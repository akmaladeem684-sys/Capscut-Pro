package com.universal.engine

import com.universal.engine.timeline.Clip
import com.universal.engine.timeline.Timeline
import com.universal.engine.timeline.Track
import org.junit.Assert.*
import org.junit.Test

class TimelineMathTest {
    private val timeline = Timeline(
        tracks = listOf(Track(0, listOf(
            Clip("c1", "m1", timelineStartUs = 1_000_000L, durationUs = 2_000_000L, sourceInUs = 500_000L, speed = 2f)
        ))),
        outputFrameRate = 30
    )

    @Test
    fun frame_duration_math() {
        assertEquals(33_333L, timeline.frameDurationUs)
    }

    @Test
    fun frame_to_pts_mapping_is_monotonic_and_exact() {
        assertEquals(0L, timeline.timestampForFrame(0))
        assertEquals(33_333L, timeline.timestampForFrame(1))
        assertEquals(333_330L, timeline.timestampForFrame(10))
    }

    @Test
    fun clip_source_mapping_respects_speed() {
        val clip = timeline.tracks[0].clips[0]
        assertEquals(500_000L, clip.sourceTimestampAt(1_000_000L))
        assertEquals(2_000_000L, clip.sourceTimestampAt(1_750_000L))
    }

    @Test
    fun no_duplicate_or_missing_frames_in_schedule() {
        val pts = (0 until timeline.totalFrames().toInt()).map { timeline.timestampForFrame(it.toLong()) }
        assertEquals(pts.size, pts.toSet().size)
        assertTrue(pts.zipWithNext().all { (a, b) -> b > a })
    }
}
