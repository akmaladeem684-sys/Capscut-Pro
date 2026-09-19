package com.example.domain.model

/**
 * Timeline clip representation used by the command/undo system.
 * Clip positions are local to their containing track.
 */
data class TimelineClip(
    val id: String,
    val localStartTimeMs: Long,
    val durationMs: Long,
    val mediaUri: String
) {
    val localEndTimeMs: Long
        get() = localStartTimeMs + durationMs
}

/** A track with an independent project-timeline offset. */
data class TimelineTrack(
    val id: String,
    val type: TrackType,
    var trackOffsetMs: Long,
    var isLocked: Boolean = false,
    var clips: MutableList<TimelineClip> = mutableListOf()
) {
    enum class TrackType { VIDEO, AUDIO, OVERLAY }

    fun getAbsoluteClipStartTime(clip: TimelineClip): Long =
        trackOffsetMs + clip.localStartTimeMs

    fun getAbsoluteClipEndTime(clip: TimelineClip): Long =
        trackOffsetMs + clip.localEndTimeMs
}
