package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.core.*
import com.ahstudio.editor.timeline.engine.TimelineValidationException
import org.junit.Assert.*
import org.junit.Test

class TimelineEngineTest {
    private fun eng() = Fx.engine("V" to TrackKind.VIDEO, "T" to TrackKind.TEXT, "A" to TrackKind.MUSIC)

    @Test fun `addClip assigns id and is queryable`() {
        val e = eng(); val t = e.trackById(e.snapshot.tracks[0].id)!!
        val c = Fx.clip(e, t.id, ClipKind.VIDEO, 0.0, 3.0)
        assertTrue(c.id.isNotBlank()); assertEquals(c, e.clip(c.id))
    }

    @Test fun `track kind rejects foreign clip kinds`() {
        val e = eng(); val v = e.snapshot.tracks[0]; val a = e.snapshot.tracks[2]
        assertFails(TimelineValidationException::class.java) {
            e.addClip(Clip("", a.id, ClipKind.VIDEO, 0, s(1.0))) }
        assertFails(TimelineValidationException::class.java) {
            e.addClip(Clip("", v.id, ClipKind.AUDIO, 0, s(1.0))) }
    }

    @Test fun `negative start and too-short duration rejected`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        assertFails(TimelineValidationException::class.java) {
            e.addClip(Clip("", v.id, ClipKind.VIDEO, -1, s(1.0))) }
        assertFails(TimelineValidationException::class.java) {
            e.addClip(Clip("", v.id, ClipKind.VIDEO, 0, 100L)) }
    }

    @Test fun `trim beyond media length rejected`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val c = Fx.clip(e, v.id, ClipKind.VIDEO, 0.0, 3.0, srcDurS = 5.0)
        assertFails(TimelineValidationException::class.java) { e.trimClip(c.id, 0, s(6.0)) }
    }

    @Test fun `trim adjusts sourceIn speed-aware`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val c = e.addClip(Clip("", v.id, ClipKind.VIDEO, 0, s(4.0), sourceInMicros = s(1.0), speed = 2f))
        e.trimClip(c.id, s(1.0), s(2.0))
        assertEquals(s(3.0), e.clip(c.id)!!.sourceInMicros) // 1s + 1s×2
    }

    @Test fun `split math - duration sourceIn keyframe remap`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val c = e.addClip(Clip("", v.id, ClipKind.VIDEO, 0, s(5.0),
            keyframes = listOf(Keyframe("k", "opacity", s(3.0), 1f))))
        val right = e.splitClip(c.id, s(2.0))
        val left = e.clip(c.id)!!
        assertEquals(s(2.0), left.durationMicros); assertTrue(left.keyframes.isEmpty())
        assertEquals(s(2.0), right.startMicros); assertEquals(s(3.0), right.durationMicros)
        assertEquals(s(2.0), right.sourceInMicros)
        assertEquals(listOf(s(1.0)), right.keyframes.map { it.offsetMicros })
    }

    @Test fun `split is speed-aware on sourceIn`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val c = e.addClip(Clip("", v.id, ClipKind.VIDEO, 0, s(4.0), sourceInMicros = s(1.0), speed = 2f))
        val right = e.splitClip(c.id, s(2.0))
        assertEquals(s(5.0), right.sourceInMicros) // 1 + 2×2
    }

    @Test fun `split too close to edge rejected`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val c = Fx.clip(e, v.id, ClipKind.VIDEO, 0.0, 3.0)
        assertFails(TimelineValidationException::class.java) { e.splitClip(c.id, s(0.01)) }
    }

    @Test fun `duplicate lands after original by default`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val a = Fx.clip(e, v.id, ClipKind.VIDEO, 0.0, 3.0)
        val d = e.duplicateClip(a.id)
        assertEquals(s(3.0), d.startMicros); assertNotEquals(a.id, d.id)
    }

    @Test fun `locked track rejects add move trim`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val c = Fx.clip(e, v.id, ClipKind.VIDEO, 0.0, 3.0)
        e.updateTrack(v.id) { it.copy(locked = true) }
        assertFails(TimelineValidationException::class.java) {
            e.addClip(Clip("", v.id, ClipKind.VIDEO, s(5.0), s(1.0))) }
        assertFails(TimelineValidationException::class.java) { e.moveClip(c.id, s(4.0)) }
        assertFails(TimelineValidationException::class.java) { e.trimClip(c.id, 0, s(2.0)) }
    }

    @Test fun `removeTrack removes its clips and prunes selection`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val c = Fx.clip(e, v.id, ClipKind.VIDEO, 0.0, 3.0)
        e.setSelection(setOf(c.id))
        e.removeTrack(v.id)
        assertNull(e.clip(c.id)); assertTrue(e.selection.isEmpty())
    }

    @Test fun `moveTrack keeps all clip timestamps`() {
        val e = eng(); val v = e.snapshot.tracks[0]; val t = e.snapshot.tracks[1]; val a = e.snapshot.tracks[2]
        val cv = Fx.clip(e, v.id, ClipKind.VIDEO, 1.0, 2.0)
        val ct = Fx.clip(e, t.id, ClipKind.TEXT, 0.5, 1.0)
        e.moveTrack(t.id, 0)
        assertEquals(listOf(t.id, v.id, a.id), e.snapshot.tracks.map { it.id })
        assertEquals(s(1.0), e.clip(cv.id)!!.startMicros)
        assertEquals(s(0.5), e.clip(ct.id)!!.startMicros)
    }

    @Test fun `selection never changes timing - independent of playhead`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val c = Fx.clip(e, v.id, ClipKind.VIDEO, 1.0, 2.0)
        e.setSelection(setOf(c.id)); e.toggleSelection(c.id); e.clearSelection()
        assertEquals(s(1.0), e.clip(c.id)!!.startMicros)
        assertEquals(s(2.0), e.clip(c.id)!!.durationMicros)
    }

    @Test fun `media mapping is speed-aware`() {
        val c = Clip("x", "t", ClipKind.VIDEO, s(2.0), s(4.0), sourceInMicros = s(1.0), speed = 2f)
        assertEquals(s(3.0), c.mediaMicrosAt(s(3.0))) // 1 + 1×2
    }
}

private fun <T : Throwable> assertFails(k: Class<T>, block: () -> Unit) {
    try { block(); fail("expected ${k.simpleName}") } catch (e: Throwable) {
        if (!k.isInstance(e)) throw e
    }
}
