package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.core.*
import com.ahstudio.editor.timeline.io.ProjectSerializer
import org.junit.Assert.*
import org.junit.Test

class SerializerTest {
    @Test fun `full snapshot serializes and deserializes bit-exact`() {
        val e = Fx.engine("Video" to TrackKind.VIDEO, "Music" to TrackKind.MUSIC)
        val v = e.snapshot.tracks[0]; val m = e.snapshot.tracks[1]
        val cv = e.addClip(Clip("", v.id, ClipKind.VIDEO, s(1.0), s(4.0),
            keyframes = listOf(Keyframe("k1", "opacity", s(0.5), 0.8f, KeyframeInterpolation.EASE))))
        val cm = Fx.clip(e, m.id, ClipKind.AUDIO, 0.0, 10.0)
        e.addMarker(s(2.5), "Drop", MarkerKind.BEAT)
        e.setSelection(setOf(cv.id))

        val json = ProjectSerializer.toJson(e.snapshot, e.selection)
        val (restored, sel) = ProjectSerializer.fromJson(json)

        assertEquals(e.snapshot.tracks, restored.tracks)
        assertEquals(e.snapshot.clips, restored.clips)
        assertEquals(e.snapshot.markers, restored.markers)
        assertEquals(e.snapshot.settings, restored.settings)
        assertEquals(setOf(cv.id), sel)
    }

    @Test fun `unknown JSON keys are ignored gracefully`() {
        val json = """{"version":1,"fps":30.0,"width":1080,"height":1920,"tracks":[],"clips":[],"markers":[],"selection":[],"unknownFutureKey":"foo"}"""
        val (s, _) = ProjectSerializer.fromJson(json)
        assertEquals(30.0, s.settings.fps, 0.001)
    }
}
