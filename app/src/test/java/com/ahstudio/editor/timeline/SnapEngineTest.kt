package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.core.ClipKind
import com.ahstudio.editor.timeline.core.Keyframe
import com.ahstudio.editor.timeline.core.TrackKind
import com.ahstudio.editor.timeline.snap.SnapEngine
import org.junit.Assert.*
import org.junit.Test

class SnapEngineTest {
    private fun eng() = Fx.engine("V" to TrackKind.VIDEO, "A" to TrackKind.MUSIC)

    @Test fun `snaps to clip start and end across tracks`() {
        val e = eng(); val v = e.snapshot.tracks[0]; val a = e.snapshot.tracks[1]
        Fx.clip(e, v.id, ClipKind.VIDEO, 2.0, 3.0) // [2s, 5s)
        val snap = SnapEngine(e)
        val r1 = snap.query(s(2.05), thresholdMicros = s(0.1))
        assertTrue(r1.snapped); assertEquals(s(2.0), r1.micros)

        val r2 = snap.query(s(4.96), thresholdMicros = s(0.1))
        assertTrue(r2.snapped); assertEquals(s(5.0), r2.micros)
    }

    @Test fun `snaps to playhead and markers`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        e.addMarker(s(3.5))
        val snap = SnapEngine(e)
        val rPlayhead = snap.query(s(1.02), thresholdMicros = s(0.1), playheadMicros = s(1.0))
        assertTrue(rPlayhead.snapped); assertEquals(s(1.0), rPlayhead.micros)

        val rMarker = snap.query(s(3.48), thresholdMicros = s(0.1))
        assertTrue(rMarker.snapped); assertEquals(s(3.5), rMarker.micros)
    }

    @Test fun `does not snap to excluded clip (self)`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val c = Fx.clip(e, v.id, ClipKind.VIDEO, 2.0, 3.0)
        val snap = SnapEngine(e)
        val r = snap.query(s(2.02), thresholdMicros = s(0.1), excludeClipIds = setOf(c.id))
        assertFalse(r.snapped)
    }

    @Test fun `snaps to keyframe within clip`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        e.addClip(com.ahstudio.editor.timeline.core.Clip(
            "", v.id, ClipKind.VIDEO, s(1.0), s(4.0),
            keyframes = listOf(Keyframe("k", "opacity", s(1.5), 1f)))) // 2.5s absolute
        val snap = SnapEngine(e)
        val r = snap.query(s(2.51), thresholdMicros = s(0.1))
        assertTrue(r.snapped); assertEquals(s(2.5), r.micros)
    }

    @Test fun `picks closest boundary when two are within threshold`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        Fx.clip(e, v.id, ClipKind.VIDEO, 1.0, 1.0) // 1s..2s
        Fx.clip(e, v.id, ClipKind.VIDEO, 2.1, 1.0) // 2.1s..3.1s
        val snap = SnapEngine(e)
        val r = snap.query(s(2.03), thresholdMicros = s(0.15))
        assertEquals(s(2.0), r.micros) // closer to 2.0 than 2.1
    }
}
