package com.example.engine.controller

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log

/**
 * Manages MediaCodec hardware capability detection, profiling for 1080p/2K/4K media,
 * and handles graceful software decoder fallback upon hardware decode failures.
 */
class DecoderManager {

  companion object {
    private const val TAG = "DecoderManager"
    const val MAX_SUPPORTED_4K_WIDTH = 3840
    const val MAX_SUPPORTED_4K_HEIGHT = 2160
    const val MAX_SUPPORTED_2K_WIDTH = 2560
    const val MAX_SUPPORTED_2K_HEIGHT = 1440
    const val MAX_SUPPORTED_1080P_WIDTH = 1920
    const val MAX_SUPPORTED_1080P_HEIGHT = 1080
  }

  private var _decoderState: DecoderState = DecoderState.UNINITIALIZED
  val decoderState: DecoderState get() = _decoderState

  private var fallbackTriggered = false
  private var lastErrorMessage: String? = null

  init {
    detectCapabilities()
  }

  fun detectCapabilities() {
    try {
      val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
      val codecInfos = codecList.codecInfos
      var hasHardwareH264 = false
      var hasHardwareHEVC = false

      for (info in codecInfos) {
        if (info.isEncoder) continue
        val types = info.supportedTypes
        for (type in types) {
          if (type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
            if (isHardwareAccelerated(info)) hasHardwareH264 = true
          }
          if (type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)) {
            if (isHardwareAccelerated(info)) hasHardwareHEVC = true
          }
        }
      }

      _decoderState = if (hasHardwareH264 || hasHardwareHEVC) {
        DecoderState.HARDWARE_ACCELERATED
      } else {
        DecoderState.SOFTWARE_FALLBACK
      }
      Log.d(TAG, "Decoder capabilities detected: state=$_decoderState (H264_HW=$hasHardwareH264, HEVC_HW=$hasHardwareHEVC)")
    } catch (e: Exception) {
      Log.w(TAG, "Failed to query MediaCodecList, defaulting to software fallback", e)
      _decoderState = DecoderState.SOFTWARE_FALLBACK
    }
  }

  private fun isHardwareAccelerated(codecInfo: MediaCodecInfo): Boolean {
    val name = codecInfo.name.lowercase()
    val isSoftware = name.startsWith("omx.google.") ||
        name.startsWith("c2.android.") ||
        name.contains(".sw.") ||
        name.contains("software") ||
        name.contains("ffmpeg")
    return !isSoftware
  }

  fun checkResolutionSupport(mimeType: String, width: Int, height: Int): Boolean {
    return try {
      val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
      for (info in codecList.codecInfos) {
        if (info.isEncoder) continue
        for (type in info.supportedTypes) {
          if (type.equals(mimeType, ignoreCase = true)) {
            val caps = info.getCapabilitiesForType(type)
            val videoCaps = caps.videoCapabilities
            if (videoCaps != null && videoCaps.isSizeSupported(width, height)) {
              return true
            }
          }
        }
      }
      // If regular search fails, accept standard sizes up to 4K
      width <= MAX_SUPPORTED_4K_WIDTH && height <= MAX_SUPPORTED_4K_HEIGHT
    } catch (e: Exception) {
      Log.w(TAG, "Resolution check failed for $width x $height ($mimeType)", e)
      true
    }
  }

  fun triggerSoftwareFallback(reason: String) {
    Log.w(TAG, "Hardware decoder failure encountered: $reason. Triggering software decoder fallback.")
    fallbackTriggered = true
    _decoderState = DecoderState.SOFTWARE_FALLBACK
    lastErrorMessage = reason
  }

  fun handleCodecError(error: Throwable): Boolean {
    Log.e(TAG, "Codec error reported: ${error.message}", error)
    triggerSoftwareFallback(error.message ?: "Unknown codec exception")
    return true // successfully handled with fallback
  }

  fun reset() {
    fallbackTriggered = false
    lastErrorMessage = null
    detectCapabilities()
  }
}
