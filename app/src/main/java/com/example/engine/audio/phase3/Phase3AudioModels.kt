package com.example.engine.audio.phase3

import kotlin.math.pow

/** Immutable timeline audio description consumed by the centralized mixer. */
data class TimelineAudioClip(
  val id: String,
  val sourceMediaId: String,
  val sourceUri: String,
  val sourceStartMs: Long,
  val sourceEndMs: Long,
  val timelineStartMs: Long,
  val timelineEndMs: Long,
  val trackId: String,
  val volume: Float = 1f,
  val pan: Float = 0f,
  val mute: Boolean = false,
  val fadeInMs: Long = 0L,
  val fadeOutMs: Long = 0L,
  val speed: Float = 1f,
  val enabled: Boolean = true
) {
  val durationMs: Long get() = (timelineEndMs - timelineStartMs).coerceAtLeast(0L)

  fun contains(timelineMs: Long): Boolean = enabled && timelineMs >= timelineStartMs && timelineMs < timelineEndMs

  fun timelineToSourceMs(timelineMs: Long): Long {
    val local = (timelineMs - timelineStartMs).coerceIn(0L, durationMs)
    val scaledOffsetMs = (local.toDouble() * speed.coerceIn(0.01f, 16f).toDouble()).toLong()
    return (sourceStartMs + scaledOffsetMs).coerceIn(sourceStartMs, sourceEndMs)
  }

  fun gainAt(timelineMs: Long): Float {
    if (!contains(timelineMs) || mute) return 0f
    val local = timelineMs - timelineStartMs
    val remaining = timelineEndMs - timelineMs
    val fi = if (fadeInMs > 0) (local.toFloat() / fadeInMs).coerceIn(0f, 1f) else 1f
    val fo = if (fadeOutMs > 0) (remaining.toFloat() / fadeOutMs).coerceIn(0f, 1f) else 1f
    return volume.coerceIn(0f, 4f) * minOf(fi, fo)
  }
}

data class TimelineAudioTrack(
  val id: String,
  val order: Int,
  val volume: Float = 1f,
  val mute: Boolean = false,
  val solo: Boolean = false,
  val enabled: Boolean = true,
  val clips: List<TimelineAudioClip> = emptyList()
)

data class AudioMixFrame(
  val sampleRate: Int,
  val channelCount: Int,
  val samples: FloatArray
)

internal fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)
