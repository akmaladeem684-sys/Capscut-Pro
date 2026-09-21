package com.ahstudio.editor.timeline.codec

import com.ahstudio.editor.timeline.model.TimelineClip
import com.ahstudio.editor.timeline.model.TimelineMarker
import com.ahstudio.editor.timeline.model.TimelineTrack
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MasterProjectData(
    val version: Int = 1,
    val tracks: List<TimelineTrack>,
    val clips: List<TimelineClip>,
    val markers: List<TimelineMarker>
)

object TimelineCodec {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun serialize(tracks: List<TimelineTrack>, clips: List<TimelineClip>, markers: List<TimelineMarker>): String {
        return json.encodeToString(MasterProjectData(tracks = tracks, clips = clips, markers = markers))
    }

    fun deserialize(jsonStr: String): MasterProjectData {
        return json.decodeFromString(MasterProjectData.serializer(), jsonStr)
    }
}
