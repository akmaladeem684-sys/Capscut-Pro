package com.universal.engine.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.universal.engine.errors.EngineError
import java.nio.ByteBuffer

/**
 * Audio path: passthrough-first. When the source audio codec is compatible with the output
 * container and speed == 1.0, encoded access units are copied 1:1 into the muxer.
 */
class AudioPipeline(private val dataSource: String) {

    data class Result(val trackIndex: Int, val format: MediaFormat, val samplesWritten: Int)

    private var extractor: MediaExtractor? = null
    private var trackIndex = -1
    private var format: MediaFormat? = null

    fun selectTrack(): MediaFormat? {
        val ex = MediaExtractor().also { extractor = it }
        ex.setDataSource(dataSource)
        for (i in 0 until ex.trackCount) {
            val fmt = ex.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = fmt
                ex.selectTrack(i)
                return fmt
            }
        }
        ex.release()
        extractor = null
        return null
    }

    fun passthroughTo(
        muxerWriter: (Int, ByteBuffer, MediaCodec.BufferInfo) -> Unit,
        muxerTrackIndex: Int,
        timelineOffsetUs: Long,
        speed: Float,
        cancelled: () -> Boolean
    ): Result {
        if (speed != 1.0f) throw EngineError.UnsupportedFeatureError(
            "audio passthrough at speed $speed", "re-encode required"
        )
        val ex = extractor ?: throw EngineError.AudioError("no audio track selected")
        ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val info = MediaCodec.BufferInfo()
        var written = 0
        val buf = ByteBuffer.allocateDirect(1 shl 16)
        while (!cancelled()) {
            buf.clear()
            val size = ex.readSampleData(buf, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = timelineOffsetUs + ex.sampleTime
            info.flags = when (ex.sampleFlags) {
                MediaExtractor.SAMPLE_FLAG_SYNC -> MediaCodec.BUFFER_FLAG_KEY_FRAME
                else -> 0
            }
            muxerWriter(muxerTrackIndex, buf, info)
            written++
            ex.advance()
        }
        return Result(muxerTrackIndex, format!!, written)
    }

    fun release() {
        runCatching { extractor?.release() }
        extractor = null
    }
}
