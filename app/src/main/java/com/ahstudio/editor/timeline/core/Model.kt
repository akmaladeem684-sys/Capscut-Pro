package com.ahstudio.editor.timeline.core

enum class TrackKind { VIDEO, OVERLAY, TEXT, STICKER, EFFECT, ADJUSTMENT, AUDIO, MUSIC, VOICE, SFX, CAPTION }

enum class ClipKind { VIDEO, AUDIO, TEXT, STICKER, EFFECT, ADJUSTMENT }

/** Strict routing table — extension point for flexible containers later. */
fun TrackKind.accepts(kind: ClipKind): Boolean = when (this) {
    TrackKind.VIDEO, TrackKind.OVERLAY -> kind == ClipKind.VIDEO
    TrackKind.TEXT, TrackKind.CAPTION  -> kind == ClipKind.TEXT
    TrackKind.STICKER                  -> kind == ClipKind.STICKER
    TrackKind.EFFECT                   -> kind == ClipKind.EFFECT
    TrackKind.ADJUSTMENT               -> kind == ClipKind.ADJUSTMENT
    TrackKind.AUDIO, TrackKind.MUSIC, TrackKind.VOICE, TrackKind.SFX -> kind == ClipKind.AUDIO
}

fun TrackKind.defaultClipKind(): ClipKind = when (this) {
    TrackKind.VIDEO, TrackKind.OVERLAY -> ClipKind.VIDEO
    TrackKind.TEXT, TrackKind.CAPTION  -> ClipKind.TEXT
    TrackKind.STICKER                  -> ClipKind.STICKER
    TrackKind.EFFECT                   -> ClipKind.EFFECT
    TrackKind.ADJUSTMENT               -> ClipKind.ADJUSTMENT
    else                               -> ClipKind.AUDIO
}

fun TrackKind.isAudioLike(): Boolean = this == TrackKind.AUDIO || this == TrackKind.MUSIC ||
        this == TrackKind.VOICE || this == TrackKind.SFX

enum class KeyframeInterpolation { LINEAR, HOLD, EASE }

data class Keyframe(
    val id: String,
    val property: String,          // "opacity", "volume", "scale", …
    val offsetMicros: Long,        // relative to clip start
    val value: Float,
    val interpolation: KeyframeInterpolation = KeyframeInterpolation.LINEAR,
)

data class Clip(
    val id: String,
    val trackId: String,
    val kind: ClipKind,
    val startMicros: Long,
    val durationMicros: Long,
    val sourceInMicros: Long = 0L,
    /** null = unbounded source (text/sticker/adjustment/effect). */
    val sourceDurationMicros: Long? = null,
    val speed: Float = 1f,
    val label: String = "",
    val colorArgb: Long? = null,
    val mediaUri: String? = null,
    val keyframes: List<Keyframe> = emptyList(),
    val extra: Map<String, String> = emptyMap(),
) {
    val endMicros: Long get() = startMicros + durationMicros
    /** Media position for a timeline position inside this clip (speed-aware). */
    fun mediaMicrosAt(timelineMicros: Long): Long =
        sourceInMicros + ((timelineMicros - startMicros) * speed).toLong()
}

data class Track(
    val id: String,
    val kind: TrackKind,
    val name: String,
    val visible: Boolean = true,
    val muted: Boolean = false,
    val solo: Boolean = false,
    val locked: Boolean = false,
)

enum class MarkerKind { USER, BEAT, CHAPTER }

data class Marker(
    val id: String,
    val timeMicros: Long,
    val label: String = "",
    val kind: MarkerKind = MarkerKind.USER,
    val colorArgb: Long? = null,
)

data class ProjectSettings(
    val fps: Double = 30.0,
    val width: Int = 1080,
    val height: Int = 1920,
)

object TimelineConstants {
    const val PLAYHEAD_X_FRACTION = 0.10f     // Master CTI ≈ 10% from left. Single definition.
    const val MIN_CLIP_MICROS = 50_000L       // 50 ms
}
