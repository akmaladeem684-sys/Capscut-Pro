package com.ahstudio.editor.timeline.engine

import com.ahstudio.editor.timeline.model.*

enum class TrimEdge { LEFT, RIGHT }

interface TimelineCommand {
    val name: String
    fun apply(engine: TimelineEngine)
    fun undo(engine: TimelineEngine)
}

class BatchCommand(private val commands: List<TimelineCommand>) : TimelineCommand {
    override val name get() = "Batch(${commands.size})"
    override fun apply(e: TimelineEngine) = commands.forEach { it.apply(e) }
    override fun undo(e: TimelineEngine) = commands.reversed().forEach { it.undo(e) }
}

class MoveClipsCommand(
    private val before: List<TimelineClip>,
    private val after: List<TimelineClip>,
) : TimelineCommand {
    override val name get() = "MoveClips"
    override fun apply(e: TimelineEngine) = e.replaceAllInternal(after)
    override fun undo(e: TimelineEngine) = e.replaceAllInternal(before)
}

class TrimClipCommand(
    private val before: TimelineClip,
    private val after: TimelineClip,
) : TimelineCommand {
    override val name get() = "TrimClip"
    override fun apply(e: TimelineEngine) = e.replaceAllInternal(listOf(after))
    override fun undo(e: TimelineEngine) = e.replaceAllInternal(listOf(before))
}

class SplitClipCommand(
    val original: TimelineClip,
    val left: TimelineClip,
    val right: TimelineClip,
) : TimelineCommand {
    override val name get() = "SplitClip"
    override fun apply(e: TimelineEngine) {
        e.removeClipInternal(original.id); e.insertClipInternal(left); e.insertClipInternal(right)
    }
    override fun undo(e: TimelineEngine) {
        e.removeClipInternal(left.id); e.removeClipInternal(right.id); e.insertClipInternal(original)
    }
}

class AddClipsCommand(private val clips: List<TimelineClip>) : TimelineCommand {
    override val name get() = "AddClips"
    override fun apply(e: TimelineEngine) = clips.forEach { e.insertClipInternal(it) }
    override fun undo(e: TimelineEngine) = clips.forEach { e.removeClipInternal(it.id) }
}

class DeleteClipsCommand(private val clips: List<TimelineClip>) : TimelineCommand {
    override val name get() = "DeleteClips"
    override fun apply(e: TimelineEngine) = clips.forEach { e.removeClipInternal(it.id) }
    override fun undo(e: TimelineEngine) = clips.forEach { e.insertClipInternal(it) }
}

class AddTrackCommand(private val track: TimelineTrack, private val index: Int) : TimelineCommand {
    override val name get() = "AddTrack"
    override fun apply(e: TimelineEngine) = e.insertTrackInternal(track, index)
    override fun undo(e: TimelineEngine) { e.removeTrackInternal(track.id) }
}

class RemoveTrackCommand(
    private val track: TimelineTrack,
    private val index: Int,
    private val clips: List<TimelineClip>,
) : TimelineCommand {
    override val name get() = "RemoveTrack"
    override fun apply(e: TimelineEngine) { e.removeTrackInternal(track.id) }
    override fun undo(e: TimelineEngine) {
        e.insertTrackInternal(track, index); clips.forEach { e.insertClipInternal(it) }
    }
}

class ReorderTracksCommand(private val from: Int, private val to: Int) : TimelineCommand {
    override val name get() = "ReorderTracks"
    override fun apply(e: TimelineEngine) = e.moveTrackInternal(from, to)
    override fun undo(e: TimelineEngine) = e.moveTrackInternal(to, from)
}

class SetTrackFlagsCommand(
    private val before: TimelineTrack,
    private val after: TimelineTrack,
) : TimelineCommand {
    override val name get() = "SetTrackFlags"
    override fun apply(e: TimelineEngine) = e.setTrackInternal(after)
    override fun undo(e: TimelineEngine) = e.setTrackInternal(before)
}

class AddMarkerCommand(private val marker: TimelineMarker) : TimelineCommand {
    override val name get() = "AddMarker"
    override fun apply(e: TimelineEngine) = e.insertMarkerInternal(marker)
    override fun undo(e: TimelineEngine) = e.removeMarkerInternal(marker.id)
}

class RemoveMarkerCommand(private val marker: TimelineMarker) : TimelineCommand {
    override val name get() = "RemoveMarker"
    override fun apply(e: TimelineEngine) = e.removeMarkerInternal(marker.id)
    override fun undo(e: TimelineEngine) = e.insertMarkerInternal(marker)
}

class SetKeyframesCommand(
    private val clipId: Long,
    private val before: List<TimelineKeyframe>,
    private val after: List<TimelineKeyframe>,
) : TimelineCommand {
    override val name get() = "SetKeyframes"
    override fun apply(e: TimelineEngine) {
        e.clipById(clipId)?.let { e.replaceAllInternal(listOf(it.copy(keyframes = after))) }
    }
    override fun undo(e: TimelineEngine) {
        e.clipById(clipId)?.let { e.replaceAllInternal(listOf(it.copy(keyframes = before))) }
    }
}
