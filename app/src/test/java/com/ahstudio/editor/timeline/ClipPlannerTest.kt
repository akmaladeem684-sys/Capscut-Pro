package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.core.Clip
import com.ahstudio.editor.timeline.core.ClipKind
import com.ahstudio.editor.timeline.core.TrackKind
import com.ahstudio.editor.timeline.edit.ClipPlanner
import com.ahstudio.editor.timeline.snap.SnapEngine
import com.ahstudio.editor.timeline.viewport.TimelineViewport
import org.junit.Assert.*
import org.junit.Test

class ClipPlannerTest {
    private fun setup(): Triple<com.ahstudio.editor.timeline.engine.TimelineEngine, ClipPlanner, String> {
        val e = Fx.engine("V" to TrackKind.VIDEO)
        val snap = SnapEngine(e)
        val vp = TimelineViewport().apply { viewportWidthPx = 1000f }
        val p = ClipPlanner(e, snap, vp) { 0L }
        return Triple(e, p, e.snapshot.tracks[0].id)
    }

    @Test fun `clampToTrackGap fits into gap between clips`() {
        val (e, p, v) = setup()
        Fx.clip(e, v, ClipKind.VIDEO, 0.0, 2.0) // 0..2s
        Fx.clip(e, v, ClipKind.VIDEO, 5.0, 2.0) // 5..7s
        // gap is 2s..5s (length 3s). Try to place a 2s clip at 4s:
        val clamped = p.clampToTrackGap(v, "new", desiredStart = s(4.0), duration = s(2.0))
        assertEquals(s(3.0), clamped) // 5s - 2s = 3s max start in gap
    }

    @Test fun `clampToTrackGap jumps to tail when gap is too small`() {
        val (e, p, v) = setup()
        Fx.clip(e, v, ClipKind.VIDEO, 0.0, 2.0)
        Fx.clip(e, v, ClipKind.VIDEO, 3.0, 2.0) // gap 2..3s is 1s wide
        val clamped = p.clampToTrackGap(v, "new", desiredStart = s(2.5), duration = s(2.0))
        assertEquals(s(5.0), clamped) // placed at tail (5s)
    }

    @Test fun `trim clamps against neighbor and min duration`() {
        val (e, p, v) = setup()
        val a = Fx.clip(e, v, ClipKind.VIDEO, 0.0, 2.0)
        val b = e.addClip(Clip("", v, ClipKind.VIDEO, s(3.0), s(2.0), sourceInMicros = s(2.0)))
        // Trim b left edge past a.endMicros (2s):
        val (s, d) = p.resolveTrim(b.id, newStart = s(1.0), newEnd = s(5.0))
        assertEquals(s(2.0), s) // clamped against a's end
        assertEquals(s(3.0), d)
    }

    @Test fun `trim clamps against media length limit`() {
        val (e, p, v) = setup()
        val c = Fx.clip(e, v, ClipKind.VIDEO, 0.0, 3.0, srcDurS = 4.0)
        val (s, d) = p.resolveTrim(c.id, newStart = 0L, newEnd = s(10.0))
        assertEquals(s(4.0), d)
    }
}
