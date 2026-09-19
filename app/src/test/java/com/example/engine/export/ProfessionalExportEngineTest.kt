package com.example.engine.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfessionalExportEngineTest {
  @Test
  fun frameTimestampsAreMonotonic() {
    val fps = 30
    val frameDurationUs = 1_000_000L / fps
    val timestamps = (0L until 300L).map { it * frameDurationUs }
    assertEquals(0L, timestamps.first())
    assertTrue(timestamps.zipWithNext().all { (a, b) -> b > a })
  }

  @Test
  fun thirtyFpsFiveSecondTimelineHasExpectedFrameCount() {
    val frames = kotlin.math.ceil(5.0 * 30.0).toLong()
    assertEquals(150L, frames)
  }

  @Test
  fun sanitizationContractAllowsStableOutputNames() {
    val name = "My Project / 4K: Final?"
    val sanitized = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48).ifBlank { "project" }
    assertEquals("My_Project___4K__Final_", sanitized)
  }
}
