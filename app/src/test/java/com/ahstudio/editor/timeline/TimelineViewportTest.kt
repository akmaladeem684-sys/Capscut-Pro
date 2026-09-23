package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.core.TimelineConstants
import com.ahstudio.editor.timeline.viewport.TimelineViewport
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class TimelineViewportTest {
    private fun vp(w: Float = 1000f) = TimelineViewport().apply { viewportWidthPx = w }

    @Test fun `playhead sits at expected fraction of viewport`() {
        val v = vp(); assertEquals(1000f * TimelineConstants.PLAYHEAD_X_FRACTION, v.playheadXPx, 0.001f)
        v.viewportWidthPx = 371f; assertEquals(371f * TimelineConstants.PLAYHEAD_X_FRACTION, v.playheadXPx, 0.001f)
    }

    @Test fun `playhead x does not move when scrolling or zooming`() {
        val v = vp()
        val before = v.playheadXPx
        v.setZoomAroundTime(s(5.0), 4f)
        assertEquals(before, v.playheadXPx, 0.0001f)
    }

    @Test fun `time px round trip error stays sub-pixel and does not accumulate`() {
        val v = vp()
        var maxErr = 0L
        var t = 0L
        repeat(10_000) {
            t += 33_333
            val back = v.timeAtContentPx(v.contentPxAtTime(t))
            maxErr = maxOf(maxErr, abs(back - t))
        }
        assertTrue("drift=${maxErr}us", maxErr <= (0.5 / v.pxPerMicro).toLong() + 1)
    }

    @Test fun `zoom anchored at playhead keeps anchor time under playhead`() {
        val v = vp()
        val anchor = s(7.3)
        v.setZoomAroundTime(anchor, 2.5f)
        assertEquals(anchor, v.timeAtScrollPx(v.scrollPxForTime(anchor)))
    }

    @Test fun `zoom clamps to bounds`() {
        val v = vp()
        v.setZoomAroundTime(0L, 1_000_000f)
        assertEquals(TimelineViewport.MAX_PX_PER_SECOND, v.pxPerSecond(), 0.01f)
        v.setZoomAroundTime(0L, 1e-9f)
        assertEquals(TimelineViewport.MIN_PX_PER_SECOND, v.pxPerSecond(), 0.01f)
    }
}
