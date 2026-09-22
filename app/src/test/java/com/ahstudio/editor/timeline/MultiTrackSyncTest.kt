package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.core.ClipKind
import com.ahstudio.editor.timeline.core.TrackKind
import org.junit.Assert.*
import org.junit.Test

class MultiTrackSyncTest {
    @Test fun `moving clip across tracks retains time alignment`() {
        val e = Fx.engine("V1" to TrackKind.VIDEO, "V2" to TrackKind.OVERLAY)
        val v1 = e.snapshot.tracks[0].id; val v2 = e.snapshot.tracks[1].id
        val c = Fx.clip(e, v1, ClipKind.VIDEO, 3.2, 2.0)
        e.moveClip(c.id, s(3.2), v2)
        assertEquals(v2, e.clip(c.id)!!.trackId)
        assertEquals(s(3.2), e.clip(c.id)!!.startMicros)
    }

    @Test fun `solo and mute hierarchy works across tracks`() {
        val e = Fx.engine("A1" to TrackKind.MUSIC, "A2" to TrackKind.VOICE)
        val a1 = e.snapshot.tracks[0]; val a2 = e.snapshot.tracks[1]
        // Default: both audible
        assertTrue(e.isTrackAudible(a1)); assertTrue(e.isTrackAudible(a2))

        // Mute a1
        e.updateTrack(a1.id) { it.copy(muted = true) }
        assertFalse(e.isTrackAudible(e.trackById(a1.id)!!))
        assertTrue(e.isTrackAudible(e.trackById(a2.id)!!))

        // Solo a1 overrides: but a1 is muted, so a1 is NOT audible, and non-solo a2 is NOT audible
        e.updateTrack(a1.id) { it.copy(solo = true) }
        assertFalse(e.isTrackAudible(e.trackById(a1.id)!!))
        assertFalse(e.isTrackAudible(e.trackById(a2.id)!!))

        // Unmute a1: now solo a1 is audible, a2 remains silent
        e.updateTrack(a1.id) { it.copy(muted = false) }
        assertTrue(e.isTrackAudible(e.trackById(a1.id)!!))
        assertFalse(e.isTrackAudible(e.trackById(a2.id)!!))
    }
}
