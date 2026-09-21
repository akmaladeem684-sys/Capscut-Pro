package com.ahstudio.editor.timeline.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TrackFamily { VIDEO, GRAPHIC, EFFECT, AUDIO }

@Serializable
enum class TrackKind(
    val family: TrackFamily,
    val defaultHeightDp: Int,
    val label: String,
) {
    VIDEO_MAIN(TrackFamily.VIDEO, 64, "Video"),
    VIDEO(TrackFamily.VIDEO, 56, "Video"),
    OVERLAY(TrackFamily.VIDEO, 48, "Overlay"),
    TEXT(TrackFamily.GRAPHIC, 44, "Text"),
    STICKER(TrackFamily.GRAPHIC, 44, "Sticker"),
    CAPTION(TrackFamily.GRAPHIC, 40, "Caption"),
    EFFECT(TrackFamily.EFFECT, 36, "Effects"),
    ADJUSTMENT(TrackFamily.EFFECT, 36, "Adjust"),
    AUDIO(TrackFamily.AUDIO, 48, "Audio"),
    MUSIC(TrackFamily.AUDIO, 48, "Music"),
    VOICE(TrackFamily.AUDIO, 44, "Voice"),
    SFX(TrackFamily.AUDIO, 40, "SFX");

    val isAudio: Boolean get() = family == TrackFamily.AUDIO
    fun hosts(kind: ClipKind) = family == kind.family
}

@Serializable
enum class ClipKind(val family: TrackFamily, val isMedia: Boolean) {
    VIDEO(TrackFamily.VIDEO, true),  IMAGE(TrackFamily.VIDEO, true),
    TEXT(TrackFamily.GRAPHIC, false), STICKER(TrackFamily.GRAPHIC, false),
    CAPTION(TrackFamily.GRAPHIC, false),
    EFFECT(TrackFamily.EFFECT, false), ADJUSTMENT(TrackFamily.EFFECT, false),
    AUDIO(TrackFamily.AUDIO, true),  VOICEOVER(TrackFamily.AUDIO, true),
    SFX(TrackFamily.AUDIO, true)
}

@Serializable
data class TimelineTrack(
    val id: Long,
    val kind: TrackKind,
    val name: String,
    val visible: Boolean = true,
    val muted: Boolean = false,
    val soloed: Boolean = false,
    val locked: Boolean = false,
    val heightDp: Int = kind.defaultHeightDp,
)

/** Keyframe times are RELATIVE to clip start → trim/split resilient. */
@Serializable
data class TimelineKeyframe(val timeUs: Long, val values: Map<String, Float>)

@Serializable
enum class MarkerKind { STANDARD, BEAT, CHAPTER }

@Serializable
data class TimelineMarker(
    val id: Long,
    val timeUs: Long,
    val label: String = "",
    val kind: MarkerKind = MarkerKind.STANDARD,
    val colorArgb: Long = 0xFF56C271,
)

@Serializable
sealed class ClipPayload

@Serializable @SerialName("text")
data class TextPayload(
    val text: String,
    val colorArgb: Long = 0xFFFFFFFF,
    val fontRef: String = "default"
) : ClipPayload()

@Serializable @SerialName("sticker")
data class StickerPayload(val res: String) : ClipPayload()

@Serializable @SerialName("effect")
data class EffectPayload(
    val effectId: String,
    val params: Map<String, Float> = emptyMap()
) : ClipPayload()

@Serializable @SerialName("media")
data class MediaPayload(
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 30f
) : ClipPayload()

/**
 * AUTHORITATIVE clip record. startUs/durationUs are TIMELINE time.
 * sourceInUs/sourceDurationUs are MEDIA time (trim limits). endUs is derived — never stored.
 * Non-overlap per track is an enforced invariant (see engine).
 */
@Serializable
data class TimelineClip(
    val id: Long,
    val trackId: Long,
    val kind: ClipKind,
    val startUs: Long,
    val durationUs: Long,
    val sourceInUs: Long = 0L,
    val sourceDurationUs: Long = durationUs,
    val label: String = "",
    val mediaUri: String? = null,
    val speed: Float = 1f,
    val keyframes: List<TimelineKeyframe> = emptyList(),
    val payload: ClipPayload? = null,
) {
    val endUs: Long get() = startUs + durationUs
    val sourceOutUs: Long get() = sourceInUs + sourceDurationUs
}
