package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.core.ClipKind
import com.ahstudio.editor.timeline.core.TrackKind
import org.junit.Assert.*
import org.junit.Test

class CommandHistoryTest {
    private fun eng() = Fx.engine("V" to TrackKind.VIDEO)

    @Test fun `undo redo restores clip state and selection bit-exact`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val c = Fx.clip(e, v.id, ClipKind.VIDEO, 0.0, 3.0)
        e.setSelection(setOf(c.id))
        e.moveClip(c.id, s(5.0))
        assertEquals(s(5.0), e.clip(c.id)!!.startMicros)

        e.history.undo(e)
        assertEquals(s(0.0), e.clip(c.id)!!.startMicros)
        assertEquals(setOf(c.id), e.selection)

        e.history.redo(e)
        assertEquals(s(5.0), e.clip(c.id)!!.startMicros)
    }

    @Test fun `undo delete restores clip`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val c = Fx.clip(e, v.id, ClipKind.VIDEO, 0.0, 3.0)
        e.removeClip(c.id); assertNull(e.clip(c.id))
        e.history.undo(e); assertNotNull(e.clip(c.id))
    }

    @Test fun `undo split restores single clip`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val c = Fx.clip(e, v.id, ClipKind.VIDEO, 0.0, 4.0)
        e.splitClip(c.id, s(2.0))
        assertEquals(2, e.snapshot.clips.size)
        e.history.undo(e)
        assertEquals(1, e.snapshot.clips.size)
        assertEquals(s(4.0), e.clip(c.id)!!.durationMicros)
    }

    @Test fun `new command clears redo stack`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val c = Fx.clip(e, v.id, ClipKind.VIDEO, 0.0, 3.0)
        e.moveClip(c.id, s(1.0))
        e.history.undo(e); assertTrue(e.history.canRedo)
        e.moveClip(c.id, s(2.0))
        assertFalse(e.history.canRedo)
    }

    @Test fun `transaction merges inner ops into single undo step`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val a = Fx.clip(e, v.id, ClipKind.VIDEO, 0.0, 2.0)
        val b = Fx.clip(e, v.id, ClipKind.VIDEO, 2.0, 2.0)
        e.begin("Batch move")
        e.moveClip(a.id, s(1.0)); e.moveClip(b.id, s(3.0))
        e.commit()
        e.history.undo(e)
        assertEquals(s(0.0), e.clip(a.id)!!.startMicros)
        assertEquals(s(2.0), e.clip(b.id)!!.startMicros)
    }

    @Test fun `cancel transaction rolls back state without history entry`() {
        val e = eng(); val v = e.snapshot.tracks[0]
        val a = Fx.clip(e, v.id, ClipKind.VIDEO, 0.0, 2.0)
        val beforeSnap = e.snapshot
        e.begin("Aborted")
        e.moveClip(a.id, s(5.0))
        e.cancel()
        assertEquals(beforeSnap, e.snapshot)
        assertFalse(e.history.canRedo)
    }

    @Test fun `undo boundary handles empty history safely`() {
        val e = eng()
        e.history.clear()
        assertFalse(e.history.undo(e))
        assertFalse(e.history.redo(e))
    }
}
