package com.example.engine.audio.phase3

import kotlin.math.sqrt

/**
 * Thread-confined, allocation-light PCM mixer. One output is produced for all tracks;
 * clips never own playback clocks or output devices.
 */
class AudioMixer(
  private val sampleRate: Int = 48_000,
  private val channels: Int = 2
) {
  @Volatile var masterVolume: Float = 1f
    set(value) { field = value.coerceIn(0f, 2f) }

  private var scratch = FloatArray(0)

  fun mix(
    timelineMs: Long,
    tracks: List<TimelineAudioTrack>,
    decoded: Map<String, FloatArray>,
    frames: Int
  ): AudioMixFrame {
    require(channels == 2) { "Phase 3 mixer currently expects stereo output" }
    val count = frames * channels
    if (scratch.size < count) scratch = FloatArray(count)
    java.util.Arrays.fill(scratch, 0, count, 0f)

    val soloActive = tracks.any { it.enabled && it.solo && !it.mute }
    for (track in tracks.sortedBy { it.order }) {
      if (!track.enabled || track.mute || (soloActive && !track.solo)) continue
      val trackGain = track.volume.coerceIn(0f, 4f)
      for (clip in track.clips) {
        if (!clip.contains(timelineMs) || clip.mute) continue
        val pcm = decoded[clip.id] ?: continue
        val gain = clip.gainAt(timelineMs) * trackGain * masterVolume
        val pan = clip.pan.coerceIn(-1f, 1f)
        // Constant-power stereo pan preserves perceived loudness better than linear pan.
        val angle = (pan + 1f) * (Math.PI.toFloat() / 4f)
        val left = kotlin.math.cos(angle) * gain
        val right = kotlin.math.sin(angle) * gain
        val availableFrames = minOf(frames, pcm.size / 2)
        for (i in 0 until availableFrames) {
          scratch[i * 2] += pcm[i * 2] * left
          scratch[i * 2 + 1] += pcm[i * 2 + 1] * right
        }
      }
    }

    // Soft-knee safety limiter. It only acts when the mixed signal exceeds unity;
    // intentional clip-to-clip level differences are otherwise preserved.
    for (i in 0 until count) {
      val x = scratch[i]
      scratch[i] = if (kotlin.math.abs(x) <= 1f) x else x / (1f + kotlin.math.abs(x) - 1f)
    }
    return AudioMixFrame(sampleRate, channels, scratch.copyOf(count))
  }

  fun reset() { java.util.Arrays.fill(scratch, 0f) }
}
