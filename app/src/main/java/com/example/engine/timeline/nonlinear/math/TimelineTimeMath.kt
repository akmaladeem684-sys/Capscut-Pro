package com.example.engine.timeline.nonlinear.math

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * High-performance, zero-allocation math utility for coordinate and timestamp conversions
 * across screen pixels, microseconds, frames, and dynamic zoom scales.
 */
object TimelineTimeMath {

  const val MICROS_PER_SECOND: Double = 1_000_000.0
  const val DEFAULT_FPS: Float = 30.0f
  const val MIN_CLIP_DURATION_US: Long = 100_000L // 0.1 second minimum threshold

  /**
   * Converts horizontal screen pixels into microseconds at the given zoom level.
   * Zero-allocation.
   */
  @JvmStatic
  fun pixelsToUs(pixels: Float, zoomLevelPxPerSec: Float): Long {
    val safeZoom = if (zoomLevelPxPerSec > 0.001f) zoomLevelPxPerSec.toDouble() else 100.0
    val us = (pixels.toDouble() / safeZoom) * MICROS_PER_SECOND
    return us.roundToLong().coerceAtLeast(0L)
  }

  /**
   * Converts a microsecond timestamp into horizontal screen pixels at the given zoom level.
   * Zero-allocation.
   */
  @JvmStatic
  fun usToPixels(timestampUs: Long, zoomLevelPxPerSec: Float): Float {
    val safeZoom = if (zoomLevelPxPerSec > 0.001f) zoomLevelPxPerSec else 100.0f
    return ((timestampUs.toDouble() / MICROS_PER_SECOND) * safeZoom).toFloat().coerceAtLeast(0f)
  }

  /**
   * Converts microseconds to video frame index at the given framerate.
   */
  @JvmStatic
  fun microsToFrames(timestampUs: Long, fps: Float = DEFAULT_FPS): Long {
    val safeFps = if (fps > 0.1f) fps.toDouble() else 30.0
    return ((timestampUs.coerceAtLeast(0L).toDouble() * safeFps) / MICROS_PER_SECOND).roundToLong()
  }

  /**
   * Converts a frame index to microsecond timestamp.
   */
  @JvmStatic
  fun framesToMicros(frame: Long, fps: Float = DEFAULT_FPS): Long {
    val safeFps = if (fps > 0.1f) fps.toDouble() else 30.0
    return ((frame.coerceAtLeast(0L).toDouble() / safeFps) * MICROS_PER_SECOND).roundToLong()
  }

  /**
   * Exact frame-boundary quantization to prevent 1-frame gaps due to floating point drift.
   */
  @JvmStatic
  fun snapToFrameBoundary(timestampUs: Long, fps: Float = DEFAULT_FPS): Long {
    val frame = microsToFrames(timestampUs, fps)
    return framesToMicros(frame, fps)
  }

  /**
   * Formats a microsecond timestamp into standard SMPTE timecode (HH:MM:SS:FF).
   */
  fun formatTimecode(timestampUs: Long, fps: Float = DEFAULT_FPS): String {
    val safeUs = timestampUs.coerceAtLeast(0L)
    val nominalFps = fps.roundToInt().coerceAtLeast(1)
    val totalSeconds = safeUs / 1_000_000L
    val hh = totalSeconds / 3600
    val mm = (totalSeconds / 60) % 60
    val ss = totalSeconds % 60
    val remainderUs = safeUs % 1_000_000L
    val frame = ((remainderUs.toDouble() / MICROS_PER_SECOND) * nominalFps).toInt().coerceIn(0, nominalFps - 1)

    return String.format(java.util.Locale.US, "%02d:%02d:%02d:%02d", hh, mm, ss, frame)
  }

  /**
   * Formats a microsecond timestamp into scannable MM:SS.S short notation for compact UI badges.
   */
  fun formatShortTime(timestampUs: Long): String {
    val safeUs = timestampUs.coerceAtLeast(0L)
    val totalSeconds = safeUs / 1_000_000L
    val mm = totalSeconds / 60
    val ss = totalSeconds % 60
    val tenths = ((safeUs % 1_000_000L) / 100_000L).toInt()
    return String.format(java.util.Locale.US, "%02d:%02d.%01d", mm, ss, tenths)
  }

  /**
   * Dynamically determines ruler major tick interval (in microseconds) based on horizontal zoom level.
   */
  fun calculateDynamicRulerStep(zoomLevelPxPerSec: Float): Long {
    return when {
      zoomLevelPxPerSec >= 600f -> 33_333L    // 1 frame at 30fps
      zoomLevelPxPerSec >= 300f -> 166_667L   // 5 frames
      zoomLevelPxPerSec >= 180f -> 500_000L   // 0.5s
      zoomLevelPxPerSec >= 80f -> 1_000_000L  // 1s
      zoomLevelPxPerSec >= 40f -> 2_000_000L  // 2s
      zoomLevelPxPerSec >= 20f -> 5_000_000L  // 5s
      zoomLevelPxPerSec >= 10f -> 10_000_000L // 10s
      zoomLevelPxPerSec >= 4f -> 30_000_000L  // 30s
      zoomLevelPxPerSec >= 1.5f -> 60_000_000L// 1m
      else -> 300_000_000L                    // 5m
    }
  }

  /**
   * Formats a ruler tick mark label according to the current interval step.
   */
  fun formatRulerLabel(timestampUs: Long, stepUs: Long, fps: Float = DEFAULT_FPS): String {
    val safeUs = timestampUs.coerceAtLeast(0L)
    if (stepUs <= 166_667L) {
      // Sub-second frame notation: SS:FF or 00s 12f
      val ss = (safeUs / 1_000_000L) % 60
      val remainderUs = safeUs % 1_000_000L
      val frame = ((remainderUs.toDouble() / MICROS_PER_SECOND) * fps).toInt()
      return String.format(java.util.Locale.US, "%02ds%02df", ss, frame)
    }

    val totalSec = safeUs / 1_000_000L
    val mm = totalSec / 60
    val ss = totalSec % 60
    val remainderUs = safeUs % 1_000_000L

    return if (stepUs < 1_000_000L) {
      val tenths = (remainderUs / 100_000L).toInt()
      String.format(java.util.Locale.US, "%02d:%02d.%d", mm, ss, tenths)
    } else {
      String.format(java.util.Locale.US, "%02d:%02d", mm, ss)
    }
  }
}
