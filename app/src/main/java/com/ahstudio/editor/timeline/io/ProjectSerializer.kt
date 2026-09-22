package com.ahstudio.editor.timeline.io

import com.ahstudio.editor.timeline.core.*
import com.ahstudio.editor.timeline.engine.TimelineSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class TrackDto(
    val id: String, val kind: String, val name: String,
    val visible: Boolean = true, val muted: Boolean = false, val solo: Boolean = false, val locked: Boolean = false,
)
@Serializable data class KeyframeDto(
    val id: String, val property: String, val offsetMicros: Long, val value: Float, val interpolation: String = "LINEAR",
)
@Serializable data class ClipDto(
    val id: String, val trackId: String, val kind: String,
    val startMicros: Long, val durationMicros: Long, val sourceInMicros: Long = 0L,
    val sourceDurationMicros: Long? = null, val speed: Float = 1f,
    val label: String = "", val colorArgb: Long? = null, val mediaUri: String? = null,
    val keyframes: List<KeyframeDto> = emptyList(), val extra: Map<String, String> = emptyMap(),
)
@Serializable data class MarkerDto(
    val id: String, val timeMicros: Long, val label: String = "", val kind: String = "USER", val colorArgb: Long? = null,
)
@Serializable data class ProjectFileDto(
    val version: Int = 1,
    val fps: Double, val width: Int, val height: Int,
    val tracks: List<TrackDto>, val clips: List<ClipDto>, val markers: List<MarkerDto>,
    val selection: List<String> = emptyList(),
)

object ProjectSerializer {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun toJson(s: TimelineSnapshot, selection: Set<String>): String =
        json.encodeToString(ProjectFileDto(
            version = 1,
            fps = s.settings.fps, width = s.settings.width, height = s.settings.height,
            tracks = s.tracks.map { TrackDto(it.id, it.kind.name, it.name, it.visible, it.muted, it.solo, it.locked) },
            clips = s.clips.values.map { c ->
                ClipDto(c.id, c.trackId, c.kind.name, c.startMicros, c.durationMicros, c.sourceInMicros,
                    c.sourceDurationMicros, c.speed, c.label, c.colorArgb, c.mediaUri,
                    c.keyframes.map { KeyframeDto(it.id, it.property, it.offsetMicros, it.value, it.interpolation.name) },
                    c.extra)
            },
            markers = s.markers.map { MarkerDto(it.id, it.timeMicros, it.label, it.kind.name, it.colorArgb) },
            selection = selection.toList(),
        ))

    fun fromJson(text: String): Pair<TimelineSnapshot, Set<String>> {
        val f = json.decodeFromString<ProjectFileDto>(text)
        require(f.version == 1) { "Unsupported project version ${f.version}" } // migration hook
        val snap = TimelineSnapshot(
            tracks = f.tracks.map { Track(it.id, TrackKind.valueOf(it.kind), it.name, it.visible, it.muted, it.solo, it.locked) },
            clips = f.clips.associate { c ->
                c.id to Clip(c.id, c.trackId, ClipKind.valueOf(c.kind), c.startMicros, c.durationMicros,
                    c.sourceInMicros, c.sourceDurationMicros, c.speed, c.label, c.colorArgb, c.mediaUri,
                    c.keyframes.map { k ->
                        Keyframe(k.id, k.property, k.offsetMicros, k.value, KeyframeInterpolation.valueOf(k.interpolation))
                    }, c.extra)
            },
            markers = f.markers.map { Marker(it.id, it.timeMicros, it.label, MarkerKind.valueOf(it.kind), it.colorArgb) },
            settings = ProjectSettings(f.fps, f.width, f.height),
        )
        return snap to f.selection.toSet()
    }
}
