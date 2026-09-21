package com.universal.engine.device

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.opengl.GLES30
import android.os.Build
import androidx.annotation.WorkerThread

/** Immutable snapshot of what THIS device can actually do. Never assume — always query. */
class HardwareCapabilities private constructor(
    val glesMajorVersion: Int,
    val glesMinorVersion: Int,
    val maxTextureSize: Int,
    val maxRenderBufferSize: Int,
    val maxViewport: IntArray,
    val supportsFloatFramebuffers: Boolean,
    val supportsHalfFloatFramebuffers: Boolean,
    val supportsMultisampling: Boolean,
    val supportsEglPresentationTime: Boolean,
    val supportedExtensions: Set<String>,
    val highpFloatRange: IntArray,
    val encoders: Map<VideoCodec, EncoderProfile>,
    val decoders: Map<VideoCodec, DecoderProfile>,
    val maxDecoderWidth: Int,
    val maxEncoderWidth: Int,
    val supportsHdr10: Boolean,
    val deviceModel: String
) {

    enum class VideoCodec { H264, HEVC, AV1 }

    data class EncoderProfile(val codec: VideoCodec, val mime: String, val maxWidth: Int, val maxHeight: Int, val maxBitrate: Int, val profileLevels: List<Int>)
    data class DecoderProfile(val codec: VideoCodec, val mime: String, val maxWidth: Int, val maxHeight: Int)

    fun supportsResolution(width: Int, height: Int): Boolean =
        width <= maxTextureSize && height <= maxTextureSize &&
        width <= maxDecoderWidth && width <= maxEncoderWidth &&
        width <= maxViewport[0] && height <= maxViewport[1]

    fun pickExportCodec(requested: VideoCodec): VideoCodec? {
        if (encoders.containsKey(requested)) return requested
        return when (requested) {
            VideoCodec.AV1 -> encoders[VideoCodec.HEVC]?.codec ?: encoders[VideoCodec.H264]?.codec
            VideoCodec.HEVC -> encoders[VideoCodec.H264]?.codec
            VideoCodec.H264 -> null
        }
    }

    fun encoderFor(codec: VideoCodec): EncoderProfile? = encoders[codec]

    companion object {
        @WorkerThread
        fun detect(): HardwareCapabilities {
            val glesMajor = intArrayOf(0).also { GLES30.glGetIntegerv(GLES30.GL_MAJOR_VERSION, it, 0) }[0]
            val glesMinor = intArrayOf(0).also { GLES30.glGetIntegerv(GLES30.GL_MINOR_VERSION, it, 0) }[0]
            val maxTex = intArrayOf(0).also { GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, it, 0) }[0]
            val maxRb = intArrayOf(0).also { GLES30.glGetIntegerv(GLES30.GL_MAX_RENDERBUFFER_SIZE, it, 0) }[0]
            val maxVp = IntArray(2).also { GLES30.glGetIntegerv(GLES30.GL_MAX_VIEWPORT_DIMS, it, 0) }
            val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS)?.split(' ')?.toSet() ?: emptySet()
            val range = IntArray(2)
            val precision = IntArray(1)
            GLES30.glGetShaderPrecisionFormat(GLES30.GL_FRAGMENT_SHADER, GLES30.GL_HIGH_FLOAT, range, 0, precision, 0)

            val fboExt = ext.any { it.contains("color_buffer_float") || it.contains("color_buffer_half_float") }
            val es3 = glesMajor >= 3
            val encoders = queryEncoders()
            val decoders = queryDecoders()

            return HardwareCapabilities(
                glesMajorVersion = glesMajor, glesMinorVersion = glesMinor,
                maxTextureSize = maxTex, maxRenderBufferSize = maxRb, maxViewport = maxVp,
                supportsFloatFramebuffers = es3 && ext.any { it.contains("color_buffer_float") },
                supportsHalfFloatFramebuffers = es3 && fboExt,
                supportsMultisampling = es3,
                supportsEglPresentationTime = ext.any { it.contains("EGL_ANDROID_presentation_time") },
                supportedExtensions = ext,
                highpFloatRange = range,
                encoders = encoders,
                decoders = decoders,
                maxDecoderWidth = decoders.values.maxOfOrNull { it.maxWidth } ?: 1920,
                maxEncoderWidth = encoders.values.maxOfOrNull { it.maxWidth } ?: 1920,
                supportsHdr10 = Build.VERSION.SDK_INT >= 29 && ext.any { it.contains("EXT_color_buffer_float") },
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            )
        }

        private fun queryEncoders(): Map<VideoCodec, EncoderProfile> {
            val out = mutableMapOf<VideoCodec, EncoderProfile>()
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in list.codecInfos) {
                if (!info.isEncoder) continue
                for (type in info.supportedTypes) {
                    val codec = mimeToCodec(type) ?: continue
                    if (out.containsKey(codec)) continue
                    val caps = info.getCapabilitiesForType(type)
                    val video = caps.videoCapabilities ?: continue
                    val maxBitrate = runCatching { video.bitrateRange?.upper ?: 20_000_000 }.getOrDefault(20_000_000)
                    out[codec] = EncoderProfile(
                        codec, type,
                        video.supportedWidths?.upper ?: 1920,
                        video.supportedHeights?.upper ?: 1080,
                        maxBitrate,
                        caps.profileLevels?.map { it.profile } ?: emptyList()
                    )
                }
            }
            return out
        }

        private fun queryDecoders(): Map<VideoCodec, DecoderProfile> {
            val out = mutableMapOf<VideoCodec, DecoderProfile>()
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in list.codecInfos) {
                if (info.isEncoder) continue
                for (type in info.supportedTypes) {
                    val codec = mimeToCodec(type) ?: continue
                    if (out.containsKey(codec)) continue
                    val caps = info.getCapabilitiesForType(type)
                    val video = caps.videoCapabilities ?: continue
                    out[codec] = DecoderProfile(
                        codec, type,
                        video.supportedWidths?.upper ?: 1920,
                        video.supportedHeights?.upper ?: 1080
                    )
                }
            }
            return out
        }

        private fun mimeToCodec(mime: String): VideoCodec? = when {
            mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) -> VideoCodec.H264
            mime.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) -> VideoCodec.HEVC
            Build.VERSION.SDK_INT >= 29 && mime.equals(MediaFormat.MIMETYPE_VIDEO_AV1, true) -> VideoCodec.AV1
            else -> null
        }
    }
}
