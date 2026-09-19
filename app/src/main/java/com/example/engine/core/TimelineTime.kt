package com.example.engine.core

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Exact project frame-rate representation. */
data class FrameRate(val numerator: Int, val denominator: Int = 1) {
  init {
    require(numerator > 0) { "Frame-rate numerator must be positive" }
    require(denominator > 0) { "Frame-rate denominator must be positive" }
  }

  val fps: Double get() = numerator.toDouble() / denominator.toDouble()

  fun frameToMicros(frame: Long): Long =
    (frame.coerceAtLeast(0L).toDouble() * 1_000_000.0 * denominator / numerator).roundToLong()

  fun microsToFrame(micros: Long): Long =
    (micros.coerceAtLeast(0L).toDouble() * numerator / (1_000_000.0 * denominator)).roundToLong()

  fun timecode(frame: Long): String {
    val safe = frame.coerceAtLeast(0L)
    val nominalFramesPerSecond = fps.roundToInt().coerceAtLeast(1)
    val totalSeconds = safe / nominalFramesPerSecond
    val hh = totalSeconds / 3600
    val mm = (totalSeconds / 60) % 60
    val ss = totalSeconds % 60
    val ff = safe % nominalFramesPerSecond
    return "%02d:%02d:%02d:%02d".format(hh, mm, ss, ff)
  }
}

data class TimelineRange(val startFrame: Long, val endFrameExclusive: Long) {
  init {
    require(startFrame >= 0L)
    require(endFrameExclusive >= startFrame)
  }

  val durationFrames: Long get() = endFrameExclusive - startFrame
  fun contains(frame: Long): Boolean = frame >= startFrame && frame < endFrameExclusive
  fun overlaps(other: TimelineRange): Boolean =
    startFrame < other.endFrameExclusive && other.startFrame < endFrameExclusive
}
