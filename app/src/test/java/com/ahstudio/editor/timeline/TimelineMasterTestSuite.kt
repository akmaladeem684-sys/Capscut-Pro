package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.clock.MasterTimelineClock
import com.ahstudio.editor.timeline.clock.SeekSource
import com.ahstudio.editor.timeline.engine.*
import com.ahstudio.editor.timeline.model.*
import org.junit.Assert.*
import org.junit.Test

class TimelineMasterTestSuite {

    @Test
    fun testClockSeekAndBounds() {
        val clock = MasterTimelineClock()
        clock.durationUs = 10_000_000L
        clock.seekTo(3_000_000L, SeekSource.PROGRAMMATIC)
        assertEquals(3_000_000L, clock.positionUs.value)
    }

    @Test
    fun testEngineAddAndDeleteCommandRoundTrip() {
        val clock = MasterTimelineClock()
        val engine = TimelineEngine(clock)
        val clip = TimelineClip(200L, 1L, ClipKind.VIDEO, 2_000_000L, 4_000_000L, label = "Test Clip")
        
        val addCmd = AddClipsCommand(listOf(clip))
        engine.execute(addCmd)
        assertNotNull(engine.clipById(200L))
        assertTrue(engine.canUndo)

        engine.undo()
        assertNull(engine.clipById(200L))
        assertTrue(engine.canRedo)

        engine.redo()
        assertNotNull(engine.clipById(200L))
    }

    @Test
    fun testSnapEnginePrecision() {
        val clip = TimelineClip(300L, 1L, ClipKind.VIDEO, 5_000_000L, 3_000_000L)
        val snap = SnapEngine.snap(
            rawUs = 5_005_000L,
            playheadUs = 0L,
            clips = listOf(clip),
            markers = emptyList(),
            zoomPxPerSecond = 64f,
            thresholdDp = 20f
        )
        assertTrue(snap.snapped)
        assertEquals(5_000_000L, snap.snappedUs)
    }
}
