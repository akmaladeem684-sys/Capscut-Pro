package com.universal.engine.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.universal.engine.device.HardwareCapabilities
import com.universal.engine.errors.EngineError
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class VideoEncoderAdapter(private val caps: HardwareCapabilities) {

    data class Config(
        val width: Int, val height: Int,
        val frameRate: Int, val bitrateMbps: Float,
        val keyframeIntervalSeconds: Int = 2,
        val requestedCodec: HardwareCapabilities.VideoCodec = HardwareCapabilities.VideoCodec.HEVC,
        val rotationDegrees: Int = 0
    )

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false

    lateinit var config: Config; private set
    val actualCodec: HardwareCapabilities.VideoCodec get() = actualCodecField
    private lateinit var actualCodecField: HardwareCapabilities.VideoCodec
    private val eosSent = AtomicBoolean(false)

    val inputSurfaceHandle: Surface get() = inputSurface
        ?: throw EngineError.EncodeError("encoder not configured", null)

    fun configure(outputPath: String, cfg: Config) {
        if (!caps.supportsResolution(cfg.width, cfg.height)) {
            throw EngineError.UnsupportedFeatureError(
                "export ${cfg.width}x${cfg.height}",
                "device max=${caps.maxEncoderWidth} texture max=${caps.maxTextureSize}")
        }
        val selectedCodec = caps.pickExportCodec(cfg.requestedCodec)
            ?: throw EngineError.UnsupportedFeatureError("video encoder", "no supported video encoder on device")
        actualCodecField = selectedCodec
        this.config = cfg

        val mime = caps.encoderFor(selectedCodec)!!.mime
        val format = MediaFormat.createVideoFormat(mime, cfg.width, cfg.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (cfg.bitrateMbps * 1_000_000).toInt())
            setInteger(MediaFormat.KEY_FRAME_RATE, cfg.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, cfg.keyframeIntervalSeconds)
        }
        try {
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            codec = MediaCodec.createEncoderByType(mime).also {
                it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = it.createInputSurface()
                it.start()
            }
        } catch (e: Exception) {
            release()
            throw EngineError.EncodeError("configure failed: ${e.message}", mime, e, EngineError.Recovery.ABORT)
        }
    }

    fun registerAudioTrack(format: MediaFormat): Int {
        check(muxer != null && !muxerStarted)
        audioTrackIndex = muxer!!.addTrack(format)
        return audioTrackIndex
    }

    private fun startMuxerIfNeeded() {
        if (muxerStarted) return
        if (videoTrackIndex < 0) videoTrackIndex = muxer!!.addTrack(codec!!.outputFormat)
        muxer!!.start()
        muxerStarted = true
    }

    fun writeAudioSamples(muxerTrackIndex: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        startMuxerIfNeeded()
        muxer?.writeSampleData(muxerTrackIndex, buffer, info)
    }

    fun drain(maxAttempts: Int = 16): Long? {
        val c = codec ?: return null
        repeat(maxAttempts) {
            val info = MediaCodec.BufferInfo()
            val idx = c.dequeueOutputBuffer(info, 10_000L)
            when {
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (videoTrackIndex < 0) videoTrackIndex = muxer!!.addTrack(c.outputFormat)
                    startMuxerIfNeeded()
                }
                idx >= 0 -> {
                    val encoded = c.getOutputBuffer(idx) ?: run {
                        c.releaseOutputBuffer(idx, false)
                        return@repeat
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        c.releaseOutputBuffer(idx, false)
                        return@repeat
                    }
                    startMuxerIfNeeded()
                    if (info.size > 0) {
                        encoded.position(info.offset)
                        encoded.limit(info.offset + info.size)
                        muxer!!.writeSampleData(videoTrackIndex, encoded, info)
                    }
                    val pts = info.presentationTimeUs
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    c.releaseOutputBuffer(idx, false)
                    if (eos) eosSent.set(true)
                    return pts
                }
            }
        }
        return null
    }

    fun signalEndOfStream() {
        if (!eosSent.getAndSet(true)) codec?.signalEndOfInputStream()
        var guard = 0
        while (!drainUntilEos() && guard++ < 600) TimeUnit.MILLISECONDS.sleep(5)
    }

    private fun drainUntilEos(): Boolean {
        val c = codec ?: return true
        val info = MediaCodec.BufferInfo()
        val idx = c.dequeueOutputBuffer(info, 50_000L)
        if (idx >= 0) {
            val buf = c.getOutputBuffer(idx)
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && buf != null) {
                startMuxerIfNeeded()
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                muxer!!.writeSampleData(videoTrackIndex, buf, info)
            }
            val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            c.releaseOutputBuffer(idx, false)
            return eos
        }
        return false
    }

    fun release() {
        runCatching { if (!eosSent.get()) codec?.signalEndOfInputStream() }
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { inputSurface?.release() }
        inputSurface = null
        if (muxerStarted) runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        muxer = null
    }
}
