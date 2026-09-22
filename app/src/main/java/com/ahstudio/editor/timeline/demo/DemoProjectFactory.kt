package com.ahstudio.editor.timeline.demo

import com.ahstudio.editor.timeline.core.*
import com.ahstudio.editor.timeline.engine.TimelineEngine

object DemoProjectFactory {
    fun seed(engine: TimelineEngine) {
        val v = engine.addTrack(TrackKind.VIDEO, "Main Video")
        val ov = engine.addTrack(TrackKind.OVERLAY, "Overlay")
        val vo = engine.addTrack(TrackKind.VOICE, "Voiceover")
        val tx = engine.addTrack(TrackKind.TEXT, "Text")

        fun clip(trackId: String, kind: ClipKind, startS: Double, durS: Double, label: String,
                 media: String? = null, kf: List<Keyframe> = emptyList()) = Clip(
            id = "", trackId = trackId, kind = kind,
            startMicros = (startS * 1e6).toLong(), durationMicros = (durS * 1e6).toLong(),
            label = label, mediaUri = media, keyframes = kf)

        // Main Video Track (Continuous Thumbnails - 40s total)
        engine.addClip(clip(v.id, ClipKind.VIDEO, 0.0, 14.5, "Cyber Matrix 1"))
        engine.addClip(clip(v.id, ClipKind.VIDEO, 14.5, 25.5, "Cyber Matrix 2"))

        // Overlay Track (PiP)
        engine.addClip(clip(ov.id, ClipKind.VIDEO, 18.0, 14.0, "PiP.mp4"))

        // Voiceover / Audio Track (Teal Waveforms)
        engine.addClip(clip(vo.id, ClipKind.AUDIO, 0.0, 10.2, "Voiceover 5"))
        engine.addClip(clip(vo.id, ClipKind.AUDIO, 10.2, 13.8, "Voiceover 2"))
        engine.addClip(clip(vo.id, ClipKind.AUDIO, 24.0, 16.0, "Energetic Tech"))

        // Text Track (Orange Pills)
        engine.addClip(clip(tx.id, ClipKind.TEXT, 0.0, 15.0, "Enter text..."))
        engine.addClip(clip(tx.id, ClipKind.TEXT, 15.0, 25.0, "Enter text..."))

        engine.addMarker(0L, "Start")
        engine.addMarker(14_500_000L, "Cut 1")
    }
}
