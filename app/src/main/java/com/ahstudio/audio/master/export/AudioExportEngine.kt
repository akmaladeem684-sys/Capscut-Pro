package com.ahstudio.audio.master.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat as MF
import com.ahstudio.audio.master.clips.AudioClipReader
import com.ahstudio.audio.master.core.AudioEngineError
import com.ahstudio.audio.master.core.AudioEngineResult
import com.ahstudio.audio.master.core.AudioFormat
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.mixer.MasterAudioMixer
import com.ahstudio.audio.master.model.MasterAudioProject
import com.ahstudio.audio.master.recording.WavWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class ExportFormat { WAV_PCM16, WAV_FLOAT32, AAC_M4A }

data class AudioExportConfig(
    val outputFile: File,
    val format: ExportFormat = ExportFormat.AAC_M4A,
    val sampleRate: Int = 48_000,
    val channels: Int = 2,
    val bitRate: Int = 192_000,
    val startSec: Double = 0.0,
    val endSec: Double = -1.0,
)

data class ExportProgress(val progress: Float, val currentSec: Double, val totalSec: Double)

class AudioExportEngine(private val context: Context, private val cache: com.ahstudio.audio.master.cache.DecodedAudioCache) {
    private val _progress = MutableStateFlow(ExportProgress(0f, 0.0, 0.0))
    val progress: StateFlow<ExportProgress> get() = _progress

    fun export(project: MasterAudioProject, cfg: AudioExportConfig): AudioEngineResult<File> {
        val totalSec = if (cfg.endSec > cfg.startSec) cfg.endSec - cfg.startSec else project.totalDurationSec() - cfg.startSec
        if (totalSec <= 0.0) return AudioEngineResult.Failure(AudioEngineError.EXPORT_FAILED, "Total export duration <= 0")

        cache.preload(project.sources.values)
        val fmt = AudioFormat(cfg.sampleRate, cfg.channels)
        val mixer = MasterAudioMixer(1024).apply { setProject(project, fmt) }
        val reader = AudioClipReader(cache)

        return try {
            when (cfg.format) {
                ExportFormat.WAV_PCM16 -> renderToWav(cfg, mixer, reader, totalSec, floatPcm = false)
                ExportFormat.WAV_FLOAT32 -> renderToWav(cfg, mixer, reader, totalSec, floatPcm = true)
                ExportFormat.AAC_M4A -> renderToAacM4a(cfg, mixer, reader, totalSec)
            }
            AudioEngineResult.Success(cfg.outputFile)
        } catch (e: Exception) {
            AudioEngineResult.Failure(AudioEngineError.EXPORT_FAILED, e.message ?: "Export error", e)
        }
    }

    private fun renderToWav(cfg: AudioExportConfig, mixer: MasterAudioMixer, reader: AudioClipReader, totalSec: Double) {
        renderToWav(cfg, mixer, reader, totalSec, floatPcm = false)
    }

    private fun renderToWav(cfg: AudioExportConfig, mixer: MasterAudioMixer, reader: AudioClipReader, totalSec: Double, floatPcm: Boolean) {
        val wav = WavWriter(cfg.outputFile, cfg.sampleRate, cfg.channels, floatPcm)
        val blockSize = 1024
        val totalFrames = (totalSec * cfg.sampleRate).toLong()
        var rendered = 0L; var blockIdx = 0L; var curSec = cfg.startSec
        val interleaved = FloatArray(blockSize * cfg.channels)
        val fmt = AudioFormat(cfg.sampleRate, cfg.channels)

        try {
            while (rendered < totalFrames) {
                val frames = minOf(blockSize.toLong(), totalFrames - rendered).toInt()
                val ctx = AudioRenderContext(fmt, curSec, frames, blockIdx, realtime = false)
                val buf = mixer.renderBlock(ctx, reader)
                buf.interleave(interleaved, frames)
                wav.write(interleaved, frames)
                rendered += frames; blockIdx++; curSec += frames.toDouble() / cfg.sampleRate
                _progress.value = ExportProgress(rendered.toFloat() / totalFrames, curSec - cfg.startSec, totalSec)
            }
        } finally { wav.close() }
    }

    private fun renderToAacM4a(cfg: AudioExportConfig, mixer: MasterAudioMixer, reader: AudioClipReader, totalSec: Double) {
        val format = MF.createAudioFormat(MF.MIMETYPE_AUDIO_AAC, cfg.sampleRate, cfg.channels)
        format.setInteger(MF.KEY_BIT_RATE, cfg.bitRate)
        format.setInteger(MF.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)

        val codec = MediaCodec.createEncoderByType(MF.MIMETYPE_AUDIO_AAC)
        val muxer = android.media.MediaMuxer(cfg.outputFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var audioTrackIdx = -1; var muxerStarted = false

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val blockSize = 1024
        val totalFrames = (totalSec * cfg.sampleRate).toLong()
        var rendered = 0L; var blockIdx = 0L; var curSec = cfg.startSec
        val interleaved = FloatArray(blockSize * cfg.channels)
        val pcm16 = ShortArray(blockSize * cfg.channels)
        val info = MediaCodec.BufferInfo()
        var inputDone = false; var outputDone = false
        val audioFormat = AudioFormat(cfg.sampleRate, cfg.channels)

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        if (rendered >= totalFrames) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val frames = minOf(blockSize.toLong(), totalFrames - rendered).toInt()
                            val ctx = AudioRenderContext(audioFormat, curSec, frames, blockIdx, realtime = false)
                            val buf = mixer.renderBlock(ctx, reader)
                            buf.interleave(interleaved, frames)
                            for (i in 0 until frames * cfg.channels) pcm16[i] = (interleaved[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                            val byteBuf = ByteBuffer.allocate(frames * cfg.channels * 2).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until frames * cfg.channels) byteBuf.putShort(pcm16[i])
                            inBuf.clear(); inBuf.put(byteBuf.array(), 0, frames * cfg.channels * 2)
                            val ptsUs = (curSec * 1_000_000.0).toLong()
                            codec.queueInputBuffer(inIdx, 0, frames * cfg.channels * 2, ptsUs, 0)
                            rendered += frames; blockIdx++; curSec += frames.toDouble() / cfg.sampleRate
                            _progress.value = ExportProgress(rendered.toFloat() / totalFrames, curSec - cfg.startSec, totalSec)
                        }
                    }
                }
                when (val outIdx = codec.dequeueOutputBuffer(info, 5000)) {
                    in 0..Int.MAX_VALUE -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxerStarted) {
                            val outBuf = codec.getOutputBuffer(outIdx)!!
                            outBuf.position(info.offset); outBuf.limit(info.offset + info.size)
                            muxer.writeSampleData(audioTrackIdx, outBuf, info)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        audioTrackIdx = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            if (muxerStarted) { try { muxer.stop() } catch (_: Exception) {}; muxer.release() }
        }
    }
}
