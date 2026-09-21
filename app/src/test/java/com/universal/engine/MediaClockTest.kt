package com.universal.engine

import com.universal.engine.playback.MediaClock
import org.junit.Assert.*
import org.junit.Test

class MediaClockTest {

    @Test
    fun paused_clock_holds_position() {
        val clock = MediaClock(10_000_000L)
        clock.play()
        clock.pause()
        val p1 = clock.currentPositionUs()
        Thread.sleep(30)
        assertEquals(p1, clock.currentPositionUs())
    }

    @Test
    fun seek_clamps_to_duration() {
        val clock = MediaClock(5_000_000L)
        clock.seekTo(99_000_000L)
        assertEquals(5_000_000L, clock.currentPositionUs())
        assertTrue(clock.state is MediaClock.State.Ended)
    }

    @Test
    fun speed_multiplies_progression() {
        val clock = MediaClock(60_000_000L)
        clock.play()
        clock.setSpeed(2f)
        val before = clock.currentPositionUs()
        Thread.sleep(100)
        val deltaUs = clock.currentPositionUs() - before
        assertTrue("expected >=120ms media progress at 2x over 100ms real, got $deltaUs", deltaUs >= 120_000L)
    }

    @Test
    fun frame_stepping_is_exact() {
        val clock = MediaClock(10_000_000L)
        clock.seekTo(0)
        clock.stepFrames(33_333L)
        assertEquals(33_333L, clock.currentPositionUs())
        clock.stepFrames(33_333L, 2)
        assertEquals(99_999L, clock.currentPositionUs())
    }

    @Test
    fun ended_at_duration() {
        val clock = MediaClock(1_000_000L)
        clock.play()
        clock.seekTo(1_000_000L)
        assertTrue(clock.state is MediaClock.State.Ended)
    }
}
