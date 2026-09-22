package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.core.ClipKind
import com.ahstudio.editor.timeline.core.TrackKind
import org.junit.Assert.*
import org.junit.Test

class TimelineIndexesTest {
    private fun eng() = Fx.engine("V" to TrackKind.VIDEO)

    @Test fun `clipAtTime is exact at boundaries`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val c = Fx.clip(e, v.id, ClipKind.VIDEO, 1.0, 2.0) // [1s, 3s)
        assertNull(e.indexes.clipAtTime(v.id, s(0.999999)))
        assertEquals(c, e.indexes.clipAtTime(v.id, s(1.0)))
        assertEquals(c, e.indexes.clipAtTime(v.id, s(2.0)))
        assertEquals(c, e.indexes.clipAtTime(v.id, s(2.999999)))
        assertNull(e.indexes.clipAtTime(v.id, s(3.0))) // exclusive end
    }

    @Test fun `overlapping query handles empty, single and many clips`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val a = Fx.clip(e, v.id, ClipKind.VIDEO, 0.0, 2.0)
        val b = Fx.clip(e, v.id, ClipKind.VIDEO, 2.0, 2.0)
        val c = Fx.clip(e, v.id, ClipKind.VIDEO, 5.0, 2.0)
        assertEquals(listOf(a, b), e.indexes.clipsOverlapping(v.id, s(1.0), s(3.0)))
        assertEquals(emptyList<com.ahstudio.editor.timeline.core.Clip>(),
            e.indexes.clipsOverlapping(v.id, s(4.1), s(4.9)))
        assertEquals(listOf(a, b, c), e.indexes.clipsOverlapping(v.id, 0, s(10.0)))
    }

    @Test fun `neighbors returns correct prev and next`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val a = Fx.clip(e, v.id, ClipKind.VIDEO, 0.0, 2.0)
        val b = Fx.clip(e, v.id, ClipKind.VIDEO, 3.0, 2.0)
        val c = Fx.clip(e, v.id, ClipKind.VIDEO, 6.0, 2.0)
        val (p, n) = e.indexes.neighbors(v.id, b.id)
        assertEquals(a, p); assertEquals(c, n)
        val (pA, nA) = e.indexes.neighbors(v.id, a.id)
        assertNull(pA); assertEquals(b, nA)
        val (pC, nC) = e.indexes.neighbors(v.id, c.id)
        assertEquals(b, pC); assertNull(nC)
    }

    @Test fun `boundaries array is sorted and complete`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        Fx.clip(e, v.id, ClipKind.VIDEO, 2.0, 3.0)
        e.addMarker(s(1.0))
        val b = e.indexes.boundaries
        for (i in 0 until b.size - 1) assertTrue(b[i] <= b[i + 1])
        assertTrue(0L in b)
        assertTrue(s(1.0) in b)
        assertTrue(s(2.0) in b)
        assertTrue(s(5.0) in b)
    }
}
