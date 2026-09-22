package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.core.Clip
import com.ahstudio.editor.timeline.core.ClipKind
import com.ahstudio.editor.timeline.core.ProjectSettings
import com.ahstudio.editor.timeline.engine.TimelineSnapshot
import com.ahstudio.editor.timeline.engine.TimelineEngine

fun s(d: Double): Long = (d * 1_000_000).toLong()

object Fx {
    fun engine(vararg tracks: Pair<String, com.ahstudio.editor.timeline.core.TrackKind>,
               fps: Double = 25.0): TimelineEngine {
        val e = TimelineEngine(TimelineSnapshot(settings = ProjectSettings(fps = fps)))
        tracks.forEach { (n, k) -> e.addTrack(k, n) }
        return e
    }
    fun clip(e: TimelineEngine, trackId: String, kind: ClipKind, startS: Double, durS: Double,
             srcDurS: Double? = null, speed: Float = 1f): Clip =
        e.addClip(Clip("", trackId, kind, s(startS), s(durS),
            sourceDurationMicros = srcDurS?.let { s(it) }, speed = speed))
}
