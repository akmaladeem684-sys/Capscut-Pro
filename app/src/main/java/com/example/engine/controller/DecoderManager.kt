package com.example.engine.controller

import android.media.MediaCodec
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

    /**
     * Recommended maximum concurrent hardware decoders on Android to prevent
     * MediaCodec 0xfffffc0e (NO_MEMORY / codec exhaustion) errors.
     */
    const val MAX_RECOMMENDED_HARDWARE_DECODERS = 4
  }

  private var _decoderState: DecoderState = DecoderState.UNINITIALIZED
  val decoderState: DecoderState get() = _decoderState
  val isHardwareAccelerated: Boolean get() = _decoderState == DecoderState.HARDWARE_ACCELERATED

  private var fallbackTriggered = false
  private var lastErrorMessage: String? = null

  fun forceSoftwareFallback(reason: String = "Forced software fallback") {
    triggerSoftwareFallback(reason)
  }

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
    } catch (e: Throwable) {
      Log.w(TAG, "Failed to query MediaCodecList, defaulting to software fallback: ${e.message}")
      _decoderState = DecoderState.SOFTWARE_FALLBACK
    }
  }

  fun isHardwareAccelerated(codecInfo: MediaCodecInfo): Boolean {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
      if (codecInfo.isSoftwareOnly) return false
      if (codecInfo.isHardwareAccelerated) return true
    }
    val name = codecInfo.name.lowercase()
    val isSoftware = name.startsWith("omx.google.") ||
        name.startsWith("c2.android.") ||
        name.contains(".sw.") ||
        name.contains("software") ||
        name.contains("ffmpeg") ||
        name.contains("google")
    return !isSoftware
  }

  /**
   * Discovers a software decoder name for the given MIME type from MediaCodecList.
   */
  fun findSoftwareDecoderName(mimeType: String): String? {
    return try {
      val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
      for (info in codecList.codecInfos) {
        if (info.isEncoder) continue
        for (type in info.supportedTypes) {
          if (type.equals(mimeType, ignoreCase = true)) {
            if (!isHardwareAccelerated(info)) {
              return info.name
            }
          }
        }
      }
      null
    } catch (e: Exception) {
      Log.w(TAG, "Error looking up software decoder for $mimeType", e)
      null
    }
  }

  /**
   * Discovers a hardware decoder name for the given MIME type from MediaCodecList.
   */
  fun findHardwareDecoderName(mimeType: String): String? {
    return try {
      val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
      for (info in codecList.codecInfos) {
        if (info.isEncoder) continue
        for (type in info.supportedTypes) {
          if (type.equals(mimeType, ignoreCase = true)) {
            if (isHardwareAccelerated(info)) {
              return info.name
            }
          }
        }
      }
      null
    } catch (e: Exception) {
      Log.w(TAG, "Error looking up hardware decoder for $mimeType", e)
      null
    }
  }

  /**
   * Discovers the best hardware-accelerated video encoder name for the given MIME type,
   * prioritizing native SoC hardware drivers (Qualcomm, MediaTek, Samsung Exynos, Kirin)
   * over software implementations.
   */
  fun findHardwareEncoderName(
    mimeType: String,
    width: Int = 1920,
    height: Int = 1080,
    requireSurface: Boolean = true
  ): String? {
    return try {
      val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
      var bestName: String? = null
      var bestScore = -1

      for (info in codecList.codecInfos) {
        if (!info.isEncoder) continue
        val types = info.supportedTypes
        val matchesMime = types.any { it.equals(mimeType, ignoreCase = true) }
        if (!matchesMime) continue

        if (!isHardwareAccelerated(info)) continue

        val caps = runCatching { info.getCapabilitiesForType(mimeType) }.getOrNull() ?: continue
        if (requireSurface && !caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)) {
          continue
        }
        val vc = caps.videoCapabilities
        if (vc != null && width > 0 && height > 0 && !vc.isSizeSupported(width, height)) {
          continue
        }

        // Score based on vendor hardware tier:
        // Qualcomm (qcom, qti) > Exynos/Samsung > MediaTek (mtk) > HiSilicon/Kirin > Other hardware
        val name = info.name.lowercase()
        var score = 10
        if (name.contains("qcom") || name.contains("qti")) score = 50
        else if (name.contains("exynos") || name.contains("samsung")) score = 40
        else if (name.contains("mtk") || name.contains("mediatek")) score = 35
        else if (name.contains("hisi") || name.contains("kirin")) score = 30
        else if (name.contains("intel") || name.contains("nvidia")) score = 25

        if (score > bestScore) {
          bestScore = score
          bestName = info.name
        }
      }
      bestName
    } catch (e: Exception) {
      Log.w(TAG, "Error looking up hardware encoder for $mimeType", e)
      null
    }
  }

  /**
   * Factory method to create a MediaCodec encoder, prioritizing hardware acceleration.
   * Returns Pair<MediaCodec, Boolean> (codec, isHardwareAccelerated).
   */
  fun createEncoder(
    mimeType: String,
    width: Int = 1920,
    height: Int = 1080,
    requireSurface: Boolean = true
  ): Pair<MediaCodec, Boolean> {
    val hwName = findHardwareEncoderName(mimeType, width, height, requireSurface)
    if (hwName != null) {
      try {
        val codec = MediaCodec.createByCodecName(hwName)
        Log.i(TAG, "Successfully created hardware video encoder: $hwName")
        return Pair(codec, true)
      } catch (e: Exception) {
        Log.w(TAG, "Failed creating hardware encoder by name $hwName, falling back to createEncoderByType", e)
      }
    }

    val fallbackCodec = MediaCodec.createEncoderByType(mimeType)
    val isHw = !isSoftwareCodec(fallbackCodec)
    Log.i(TAG, "Created fallback video encoder: ${fallbackCodec.name} (hardwareAccelerated=$isHw)")
    return Pair(fallbackCodec, isHw)
  }

  /**
   * Factory method to create a MediaCodec decoder with automatic fallback.
   * If hardware creation fails or if fallback is active, creates a software decoder.
   *
   * @return Pair<MediaCodec, Boolean> where boolean indicates if it is hardware-accelerated.
   */
  fun createDecoder(mimeType: String, preferHardware: Boolean = true): Pair<MediaCodec, Boolean> {
    val shouldAttemptHardware = preferHardware && !fallbackTriggered && _decoderState != DecoderState.SOFTWARE_FALLBACK

    if (shouldAttemptHardware) {
      val hwName = findHardwareDecoderName(mimeType)
      if (hwName != null) {
        try {
          val codec = MediaCodec.createByCodecName(hwName)
          return Pair(codec, true)
        } catch (e: Exception) {
          Log.w(TAG, "Failed creating hardware decoder by name $hwName, attempting generic createDecoderByType", e)
        }
      }

      try {
        val codec = MediaCodec.createDecoderByType(mimeType)
        val isHw = !isSoftwareCodec(codec)
        return Pair(codec, isHw)
      } catch (e: Exception) {
        Log.w(TAG, "Hardware decoder creation failed for $mimeType; triggering software fallback", e)
        triggerSoftwareFallback("Failed creating hardware decoder for $mimeType: ${e.message}")
      }
    }

    // Software fallback path
    val swName = findSoftwareDecoderName(mimeType)
    if (swName != null) {
      try {
        val codec = MediaCodec.createByCodecName(swName)
        return Pair(codec, false)
      } catch (e: Exception) {
        Log.w(TAG, "Failed creating software decoder by name $swName", e)
      }
    }

    // Default fallback
    val codec = MediaCodec.createDecoderByType(mimeType)
    return Pair(codec, false)
  }

  private fun isSoftwareCodec(codec: MediaCodec): Boolean {
    return try {
      val name = codec.name.lowercase()
      name.startsWith("omx.google.") ||
          name.startsWith("c2.android.") ||
          name.contains(".sw.") ||
          name.contains("software") ||
          name.contains("ffmpeg")
    } catch (_: Exception) {
      false
    }
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

  fun isFallbackActive(): Boolean = fallbackTriggered || _decoderState == DecoderState.SOFTWARE_FALLBACK

  fun isRuntimeFallbackTriggered(): Boolean = fallbackTriggered

  fun getLastErrorMessage(): String? = lastErrorMessage

  fun reset() {
    fallbackTriggered = false
    lastErrorMessage = null
    detectCapabilities()
  }
}
