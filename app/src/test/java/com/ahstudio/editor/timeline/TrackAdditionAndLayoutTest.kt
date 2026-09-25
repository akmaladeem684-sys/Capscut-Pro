package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.core.Track
import com.ahstudio.editor.timeline.core.TrackKind
import com.ahstudio.editor.timeline.engine.TimelineEngine
import com.example.domain.model.Timeline
import com.example.domain.model.TrackType
import com.example.engine.timeline.TimelineTrackManager
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TrackAdditionAndLayoutTest {

    @Test
    fun testTrackAddition_4thTrackAppendsBelow3rdTrackWithUniqueUuidAndIncrementedOrder() {
        val engine = TimelineEngine()
        engine.reset()

        // 1. Add Track 1 (Main Video)
        val t1 = engine.addTrack(TrackKind.VIDEO, "Main Video")
        // 2. Add Track 2 (Overlay 1)
        val t2 = engine.addTrack(TrackKind.OVERLAY, "Overlay 1")
        // 3. Add Track 3 (Overlay 2)
        val t3 = engine.addTrack(TrackKind.OVERLAY, "Overlay 2")

        assertEquals(3, engine.snapshot.tracks.size)
        assertEquals(t1.id, engine.snapshot.tracks[0].id)
        assertEquals(t2.id, engine.snapshot.tracks[1].id)
        assertEquals(t3.id, engine.snapshot.tracks[2].id)

        // 4. Add 4th Track (Overlay 3)
        val t4 = engine.addTrack(TrackKind.OVERLAY, "Overlay 3")

        // VERIFY: Adding 4th track appends below 3rd track, NEVER replaces 3rd track
        assertEquals(4, engine.snapshot.tracks.size)
        assertEquals(t1.id, engine.snapshot.tracks[0].id)
        assertEquals(t2.id, engine.snapshot.tracks[1].id)
        assertEquals(t3.id, engine.snapshot.tracks[2].id) // 3rd track is preserved!
        assertEquals(t4.id, engine.snapshot.tracks[3].id) // 4th track is appended below it!

        // VERIFY: Unique UUID & incremented order
        assertNotEquals(t3.id, t4.id)
        assertTrue(t4.order > t3.order)
        assertTrue(t4.id.isNotBlank())
        assertEquals(t4.id, t4.trackId)

        // 5. Add 5th Track (Audio)
        val t5 = engine.addTrack(TrackKind.AUDIO, "Audio 1")
        assertEquals(5, engine.snapshot.tracks.size)
        assertEquals(t4.id, engine.snapshot.tracks[3].id)
        assertEquals(t5.id, engine.snapshot.tracks[4].id)
        assertTrue(t5.order > t4.order)
        assertNotEquals(t4.id, t5.id)
    }

    @Test
    fun testTimelineTrackManager_AddTrack_AppendsWithoutOverwriting() {
        var timeline = Timeline()

        // Add 1st track
        val (tl1, track1) = TimelineTrackManager.addTrack(timeline, TrackType.MAIN_VIDEO, "Main Track")
        // Add 2nd track
        val (tl2, track2) = TimelineTrackManager.addTrack(tl1, TrackType.OVERLAY, "Overlay 1")
        // Add 3rd track
        val (tl3, track3) = TimelineTrackManager.addTrack(tl2, TrackType.OVERLAY, "Overlay 2")

        assertEquals(3, tl3.tracks.size)
        assertEquals(track3.trackId, tl3.tracks[2].trackId)

        // Add 4th track
        val (tl4, track4) = TimelineTrackManager.addTrack(tl3, TrackType.OVERLAY, "Overlay 3")

        // VERIFY: 4 tracks in list, 3rd track is untouched, 4th track is appended below it
        assertEquals(4, tl4.tracks.size)
        assertEquals(track1.trackId, tl4.tracks[0].trackId)
        assertEquals(track2.trackId, tl4.tracks[1].trackId)
        assertEquals(track3.trackId, tl4.tracks[2].trackId)
        assertEquals(track4.trackId, tl4.tracks[3].trackId)

        assertNotEquals(track3.trackId, track4.trackId)
        assertTrue(track4.order > track3.order)
    }

    @Test
    fun testTimelineTrackManager_FindOrCreateTrack_AppendsNewTrackProperly() {
        var timeline = Timeline()

        // Create tracks by locking each allocated track so next clip requires a new track dynamically
        val (tl1, idx1) = TimelineTrackManager.findOrCreateTrackForClip(timeline, TrackType.OVERLAY, 1000L)
        val tl1Locked = tl1.copy(tracks = tl1.tracks.map { it.copy(isLocked = true) })
        val (tl2, idx2) = TimelineTrackManager.findOrCreateTrackForClip(tl1Locked, TrackType.OVERLAY, 1000L)
        val tl2Locked = tl2.copy(tracks = tl2.tracks.map { it.copy(isLocked = true) })
        val (tl3, idx3) = TimelineTrackManager.findOrCreateTrackForClip(tl2Locked, TrackType.OVERLAY, 1000L)
        val tl3Locked = tl3.copy(tracks = tl3.tracks.map { it.copy(isLocked = true) })

        // Add 4th track
        val (tl4, idx4) = TimelineTrackManager.findOrCreateTrackForClip(tl3Locked, TrackType.OVERLAY, 1000L)

        assertTrue(tl4.tracks.size >= 4)
        val trackIds = tl4.tracks.map { it.trackId }
        assertEquals(trackIds.size, trackIds.distinct().size) // All UUIDs are unique
    }

    @Test
    fun testTrackModel_TrackIdGetter_IsStable() {
        val uuid = UUID.randomUUID().toString()
        val track = Track(id = uuid, kind = TrackKind.OVERLAY, name = "Overlay 1", order = 1)

        assertEquals(uuid, track.trackId)
        assertEquals(uuid, track.id)
    }
}
