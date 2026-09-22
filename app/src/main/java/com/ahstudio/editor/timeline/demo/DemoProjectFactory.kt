package com.ahstudio.editor.timeline.demo

import com.ahstudio.editor.timeline.core.*
import com.ahstudio.editor.timeline.engine.TimelineEngine

object DemoProjectFactory {
    fun seed(engine: TimelineEngine) {
        val v = engine.addTrack(TrackKind.VIDEO, "Main Video")
        val ov = engine.addTrack(TrackKind.OVERLAY, "Overlay")
        val tx = engine.addTrack(TrackKind.TEXT, "Text")
        val st = engine.addTrack(TrackKind.STICKER, "Sticker")
        val mu = engine.addTrack(TrackKind.MUSIC, "Music")
        val vo = engine.addTrack(TrackKind.VOICE, "Voice")

        fun clip(trackId: String, kind: ClipKind, startS: Double, durS: Double, label: String,
                 media: String? = null, kf: List<Keyframe> = emptyList()) = Clip(
            id = "", trackId = trackId, kind = kind,
            startMicros = (startS * 1e6).toLong(), durationMicros = (durS * 1e6).toLong(),
            label = label, mediaUri = media, keyframes = kf)

        engine.addClip(clip(v.id, ClipKind.VIDEO, 0.0, 8.0, "Intro.mp4"))
        engine.addClip(clip(v.id, ClipKind.VIDEO, 8.0, 7.0, "Scene2.mp4"))
        engine.addClip(clip(ov.id, ClipKind.VIDEO, 2.0, 4.0, "PiP.mp4"))
        engine.addClip(clip(tx.id, ClipKind.TEXT, 1.0, 4.0, "AH STUDIO", kf = listOf(
            Keyframe("k1", "opacity", 0L, 0f), Keyframe("k2", "opacity", 500_000L, 1f))))
        engine.addClip(clip(st.id, ClipKind.STICKER, 3.0, 3.0, "🔥 Sticker"))
        engine.addClip(clip(mu.id, ClipKind.AUDIO, 0.0, 15.0, "bgm.mp3"))
        engine.addClip(clip(vo.id, ClipKind.AUDIO, 3.0, 6.0, "voice.wav"))
        engine.addMarker(0L, "Start")
        engine.addMarker(5_000_000L, "Beat 1")
        engine.addMarker(10_000_000L, "Hook")
    }
}
