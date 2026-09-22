package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.playback.*
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

private class RecordingSink : PreviewSink {
    val times = mutableListOf<Long>(); val seeks = mutableListOf<Long>()
    var started = 0; var paused = 0; var ended = 0
    override fun onPlaybackStarted() { started++ }
    override fun onPlaybackPaused() { paused++ }
    override fun onPlaybackEnded() { ended++ }
    override fun onSeek(targetMicros: Long) { seeks.add(targetMicros) }
    override fun onTimeChanged(micros: Long, playing: Boolean) { times.add(micros) }
}

class PlaybackControllerTest {

    @Test fun `frames advance clock exactly - no drift`() = runTest {
        val clock = MasterTimelineClock(); val driver = ManualFrameDriver(); val sink = RecordingSink()
        val pc = PlaybackController(clock, { s(10.0) }, driver, this.backgroundScope)
        pc.previewSink = sink; pc.play()
        repeat(60) { driver.emit(33_333_000L) } // 60 × 33.333ms
        assertEquals(s(1.99998), clock.timeMicros)
        assertEquals(60, sink.times.size)
    }

    @Test fun `end of media pauses and seeks to duration`() = runTest {
        val clock = MasterTimelineClock(); val driver = ManualFrameDriver(); val sink = RecordingSink()
        val pc = PlaybackController(clock, { s(1.0) }, driver, this.backgroundScope)
        pc.previewSink = sink; pc.play()
        driver.emit(s(0.7) * 1000L); driver.emit(s(0.7) * 1000L) // 1.4s ≥ 1s in nanos
        assertEquals(s(1.0), clock.timeMicros)
        assertFalse(pc.isPlaying); assertEquals(1, sink.ended)
    }

    @Test fun `seek clamps into project duration`() = runTest {
        val clock = MasterTimelineClock()
        val pc = PlaybackController(clock, { s(5.0) }, ManualFrameDriver(), this.backgroundScope)
        pc.seekTo(s(99.0)); assertEquals(s(5.0), clock.timeMicros)
        pc.seekTo(-1); assertEquals(0L, clock.timeMicros)
    }

    @Test fun `scrub seeks coalesce - sink receives only latest within one frame`() = runTest {
        val clock = MasterTimelineClock(); val sink = RecordingSink()
        val pc = PlaybackController(clock, { s(30.0) }, ManualFrameDriver(), this)
        pc.previewSink = sink
        pc.requestScrubSeek(s(3.0)); pc.requestScrubSeek(s(7.0))
        assertEquals(s(7.0), clock.timeMicros)      // clock IMMEDIATE
        testScheduler.advanceTimeBy(50)             // sink frame-aligned
        assertEquals(listOf(s(7.0)), sink.seeks)
    }

    @Test fun `replay after end restarts at zero`() = runTest {
        val clock = MasterTimelineClock(); val driver = ManualFrameDriver()
        val pc = PlaybackController(clock, { s(1.0) }, driver, this.backgroundScope)
        pc.play(); driver.emit(s(1.5) * 1000L); assertFalse(pc.isPlaying)
        pc.play(); assertEquals(0L, clock.timeMicros); assertTrue(pc.isPlaying)
    }
}
