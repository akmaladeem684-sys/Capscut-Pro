package com.example.engine.audio.phase3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioMixerTest {
  @Test fun mixes_multiple_tracks_without_independent_players() {
    val tracks = listOf(
      TimelineAudioTrack("A1", 0, clips = listOf(TimelineAudioClip("c1", "m1", "u1", 0, 1000, 0, 1000, "A1"))),
      TimelineAudioTrack("A2", 1, clips = listOf(TimelineAudioClip("c2", "m2", "u2", 0, 1000, 0, 1000, "A2")))
    )
    val pcm = FloatArray(20) { 0.25f }
    val result = AudioMixer().mix(500, tracks, mapOf("c1" to pcm, "c2" to pcm), 10)
    assertTrue(result.samples.all { it.isFinite() })
    assertEquals(20, result.samples.size)
  }

  @Test fun mute_and_solo_are_deterministic() {
    val a = TimelineAudioClip("a", "m", "u", 0, 1000, 0, 1000, "1")
    val b = a.copy(id = "b", trackId = "2")
    val tracks = listOf(TimelineAudioTrack("1", 0, clips = listOf(a)), TimelineAudioTrack("2", 1, solo = true, clips = listOf(b)))
    val pcm = FloatArray(8) { 0.25f }
    val out = AudioMixer().mix(100, tracks, mapOf("a" to pcm, "b" to pcm), 4)
    assertTrue(out.samples.any { it != 0f })
  }

  @Test fun fades_are_non_destructive() {
    val clip = TimelineAudioClip("c", "m", "u", 0, 1000, 0, 1000, "1", fadeInMs = 500, fadeOutMs = 500)
    assertEquals(0f, clip.gainAt(0), 0.0001f)
    assertTrue(clip.gainAt(250) > 0f)
    assertTrue(clip.gainAt(500) > 0f)
  }
}
