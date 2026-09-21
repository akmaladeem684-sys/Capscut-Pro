package com.universal.engine.decoder

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.universal.engine.errors.EngineError
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class VideoDecoderAdapter(
    private val dataSource: String,
    private val textureManager: com.universal.engine.gl.TextureManager,
    private val onFrameAvailable: () -> Unit
) : SurfaceTexture.OnFrameAvailableListener {

    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var externalTexture: com.universal.engine.gl.TextureManager.ExternalTextureResource? = null
    private var decoderThread: HandlerThread? = null
    private var decoderHandler: Handler? = null

    var trackFormat: MediaFormat? = null; private set
    var durationUs: Long = 0L; private set
    var width: Int = 0; private set
    var height: Int = 0; private set
    var rotationDegrees: Int = 0; private set

    val externalTextureId: Int get() = externalTexture?.id ?: 0

    private val pendingFrameCount = AtomicInteger(0)
    private val released = AtomicBoolean(false)
    private var started = false

    var onFrameDecoded: ((Long, Long) -> Unit)? = null

    fun initialize() {
        check(!released.get())
        extractor = MediaExtractor().also { it.setDataSource(dataSource) }
        var trackIndex = -1
        for (i in 0 until extractor!!.trackCount) {
            val fmt = extractor!!.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                trackIndex = i; trackFormat = fmt; break
            }
        }
        if (trackIndex < 0) throw EngineError.DecodeError("no video track found", null, null, EngineError.Recovery.ABORT)

        val fmt = trackFormat!!
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        extractor!!.selectTrack(trackIndex)
        durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        width = fmt.getInteger(MediaFormat.KEY_WIDTH)
        height = fmt.getInteger(MediaFormat.KEY_HEIGHT)
        rotationDegrees = when {
            fmt.containsKey("rotation-degrees") -> fmt.getInteger("rotation-degrees")
            fmt.containsKey(MediaFormat.KEY_ROTATION) -> fmt.getInteger(MediaFormat.KEY_ROTATION)
            else -> 0
        }

        externalTexture = textureManager.createExternalTexture()
        surfaceTexture = SurfaceTexture(externalTexture!!.id).apply {
            setDefaultBufferSize(width, height)
            setOnFrameAvailableListener(this@VideoDecoderAdapter)
        }
        val surface = Surface(surfaceTexture)

        decoderThread = HandlerThread("ue-decoder").also { it.start() }
        decoderHandler = Handler(decoderThread!!.looper)
        try {
            codec = MediaCodec.createDecoderByType(mime).also {
                it.configure(fmt, surface, null, 0)
                it.setCallback(CodecCallback(), decoderHandler)
                it.start()
                started = true
            }
        } catch (e: Exception) {
            surface.release()
            throw EngineError.DecodeError("decoder init failed: ${e.message}", mime, e, EngineError.Recovery.ABORT)
        }
    }

    override fun onFrameAvailable(st: SurfaceTexture) {
        pendingFrameCount.incrementAndGet()
        onFrameAvailable()
    }

    fun consumeNewestFrame(): Pair<Long, Long>? {
        val st = surfaceTexture ?: return null
        if (pendingFrameCount.get() <= 0) return null
        pendingFrameCount.set(0)
        st.updateTexImage()
        val ts = st.timestamp / 1000L
        onFrameDecoded?.invoke(ts, 0L)
        return Pair(ts, 0L)
    }

    fun hasPendingFrame(): Boolean = pendingFrameCount.get() > 0

    fun seekTo(timestampUs: Long, accurate: Boolean) {
        val ex = extractor ?: return
        synchronized(ex) {
            codec?.flush()
            ex.seekTo(timestampUs, if (accurate) MediaExtractor.SEEK_TO_CLOSEST_SYNC else MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            pendingFrameCount.set(0)
        }
        if (accurate) {
            val target = timestampUs
            var guard = 0
            while (guard++ < 240) {
                val pts = decodeNextRaw() ?: break
                if (pts >= target) break
            }
            pendingFrameCount.set(0)
        }
    }

    private fun decodeNextRaw(): Long? {
        val c = codec ?: return null
        var timeoutUs = 10_000L
        repeat(3) {
            val info = MediaCodec.BufferInfo()
            val outIndex = c.dequeueOutputBuffer(info, timeoutUs)
            when {
                outIndex >= 0 -> {
                    c.releaseOutputBuffer(outIndex, true)
                    val pts = info.presentationTimeUs
                    val deadline = System.nanoTime() + 50_000_000L
                    while (pendingFrameCount.get() == 0 && System.nanoTime() < deadline) Thread.yield()
                    surfaceTexture?.updateTexImage()
                    pendingFrameCount.set(0)
                    return pts
                }
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> { timeoutUs *= 2 }
                else -> {}
            }
        }
        return null
    }

    fun flush() {
        codec?.flush()
        extractor?.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        pendingFrameCount.set(0)
    }

    fun release() {
        if (!released.getAndSet(true)) {
            runCatching { if (started) codec?.stop() }
            runCatching { codec?.release() }
            codec = null
            surfaceTexture?.setOnFrameAvailableListener(null)
            surfaceTexture?.release()
            surfaceTexture = null
            runCatching { extractor?.release() }
            extractor = null
            decoderThread?.quitSafely()
            decoderThread = null
        }
    }

    private inner class CodecCallback : MediaCodec.Callback() {
        override fun onInputBufferAvailable(mc: MediaCodec, index: Int) {
            val ex = extractor ?: return
            synchronized(ex) {
                if (released.get()) return
                val buf = mc.getInputBuffer(index) ?: return
                val size = ex.readSampleData(buf, 0)
                if (size < 0) {
                    mc.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else {
                    mc.queueInputBuffer(index, 0, size, ex.sampleTime, 0)
                    ex.advance()
                }
            }
        }
        override fun onOutputBufferAvailable(mc: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            mc.releaseOutputBuffer(index, true)
        }
        override fun onError(mc: MediaCodec, e: MediaCodec.CodecException) {
            throw EngineError.DecodeError(e.message ?: "codec error", mc.name, e, EngineError.Recovery.RECONFIGURE_DECODER)
        }
        override fun onOutputFormatChanged(mc: MediaCodec, format: MediaFormat) {}
    }
}
