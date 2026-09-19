package com.example.engine.core

import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.roundToLong

interface TimelineCommand { val name: String; fun apply(state: TimelineState): TimelineState }

data class AddClipCommand(private val trackId: String, private val clip: CoreClip) : TimelineCommand {
  override val name = "Add Clip"
  override fun apply(state: TimelineState): TimelineState {
    val track = state.track(trackId)
    require(!track.locked) { "Track is locked: $trackId" }
    require(track.clips.none { it.id == clip.id }) { "Clip already exists: ${clip.id}" }
    return state.replaceTrack(track.copy(clips = track.clips + clip).sorted()).copy(selectedClipId = clip.id)
  }
}

data class DeleteClipCommand(private val clipId: String) : TimelineCommand {
  override val name = "Delete Clip"
  override fun apply(state: TimelineState): TimelineState {
    val (track, _) = state.clip(clipId)
    require(!track.locked) { "Track is locked: ${track.id}" }
    return state.replaceTrack(track.copy(clips = track.clips.filterNot { it.id == clipId })).copy(selectedClipId = state.selectedClipId.takeUnless { it == clipId })
  }
}

data class MoveClipCommand(private val clipId: String, private val newStartFrame: Long, private val targetTrackId: String? = null) : TimelineCommand {
  override val name = "Move Clip"
  override fun apply(state: TimelineState): TimelineState {
    require(newStartFrame >= 0L)
    val (sourceTrack, clip) = state.clip(clipId)
    require(!sourceTrack.locked) { "Track is locked: ${sourceTrack.id}" }
    val target = targetTrackId?.let(state::track) ?: sourceTrack
    require(!target.locked) { "Track is locked: ${target.id}" }
    val moved = clip.copy(timelineStartFrame = newStartFrame, timelineEndFrame = newStartFrame + clip.durationFrames)
    val tracks = state.project.tracks.map { track ->
      when {
        sourceTrack.id == target.id && track.id == sourceTrack.id -> track.copy(clips = track.clips.filterNot { it.id == clipId } + moved).sorted()
        track.id == sourceTrack.id -> track.copy(clips = track.clips.filterNot { it.id == clipId }).sorted()
        track.id == target.id -> track.copy(clips = track.clips.filterNot { it.id == clipId } + moved).sorted()
        else -> track
      }
    }
    return state.copy(project = state.project.copy(tracks = tracks), selectedClipId = clipId)
  }
}

data class TrimClipCommand(private val clipId: String, private val newStartFrame: Long? = null, private val newEndFrame: Long? = null) : TimelineCommand {
  override val name = "Trim Clip"
  override fun apply(state: TimelineState): TimelineState {
    val (track, clip) = state.clip(clipId)
    require(!track.locked) { "Track is locked: ${track.id}" }
    val start = newStartFrame ?: clip.timelineStartFrame
    val end = newEndFrame ?: clip.timelineEndFrame
    require(start >= clip.timelineStartFrame && end <= clip.timelineEndFrame && end > start)
    val oldDuration = clip.durationFrames.coerceAtLeast(1L)
    val sourceDuration = clip.sourceEndUs - clip.sourceStartUs
    val sourceStart = clip.sourceStartUs + ((start - clip.timelineStartFrame).toDouble() / oldDuration * sourceDuration).roundToLong()
    val sourceEnd = clip.sourceStartUs + ((end - clip.timelineStartFrame).toDouble() / oldDuration * sourceDuration).roundToLong()
    val trimmed = clip.copy(timelineStartFrame = start, timelineEndFrame = end, sourceStartUs = sourceStart, sourceEndUs = sourceEnd)
    return state.replaceTrack(track.copy(clips = track.clips.map { if (it.id == clipId) trimmed else it }).sorted()).copy(selectedClipId = clipId)
  }
}

data class SplitClipCommand(private val clipId: String, private val splitFrame: Long) : TimelineCommand {
  override val name = "Split Clip"
  override fun apply(state: TimelineState): TimelineState {
    val (track, clip) = state.clip(clipId)
    require(!track.locked) { "Track is locked: ${track.id}" }
    require(splitFrame > clip.timelineStartFrame && splitFrame < clip.timelineEndFrame)
    val ratio = (splitFrame - clip.timelineStartFrame).toDouble() / clip.durationFrames.toDouble()
    val splitSource = clip.sourceStartUs + ((clip.sourceEndUs - clip.sourceStartUs) * ratio).roundToLong()
    val left = clip.copy(sourceEndUs = splitSource, timelineEndFrame = splitFrame)
    val right = clip.copy(id = UUID.randomUUID().toString(), sourceStartUs = splitSource, timelineStartFrame = splitFrame)
    return state.replaceTrack(track.copy(clips = track.clips.filterNot { it.id == clipId } + left + right).sorted()).copy(selectedClipId = right.id)
  }
}

data class DuplicateClipCommand(private val clipId: String, private val targetStartFrame: Long? = null) : TimelineCommand {
  override val name = "Duplicate Clip"
  override fun apply(state: TimelineState): TimelineState {
    val (track, clip) = state.clip(clipId)
    require(!track.locked) { "Track is locked: ${track.id}" }
    val start = targetStartFrame ?: clip.timelineEndFrame
    val duplicate = clip.copy(id = UUID.randomUUID().toString(), timelineStartFrame = start, timelineEndFrame = start + clip.durationFrames)
    return state.replaceTrack(track.copy(clips = track.clips + duplicate).sorted()).copy(selectedClipId = duplicate.id)
  }
}

data class MoveTrackCommand(private val trackId: String, private val newOrder: Int) : TimelineCommand {
  override val name = "Move Track"
  override fun apply(state: TimelineState): TimelineState = state.copy(project = state.project.copy(tracks = state.project.tracks.map { if (it.id == trackId) it.copy(order = newOrder) else it }.sortedBy { it.order }))
}

class TimelineCommandHistory(private val maxSize: Int = 100) {
  private data class Entry(val before: TimelineState, val after: TimelineState, val name: String)
  private val undo = ArrayDeque<Entry>()
  private val redo = ArrayDeque<Entry>()
  init { require(maxSize > 0) }
  val canUndo: Boolean get() = undo.isNotEmpty()
  val canRedo: Boolean get() = redo.isNotEmpty()
  fun execute(state: TimelineState, command: TimelineCommand): TimelineState {
    val after = command.apply(state)
    if (after == state) return state
    undo.addLast(Entry(state, after, command.name))
    while (undo.size > maxSize) undo.removeFirst()
    redo.clear()
    return after
  }
  fun undo(): TimelineState? = if (undo.isEmpty()) null else undo.removeLast().also { redo.addLast(it) }.before
  fun redo(): TimelineState? = if (redo.isEmpty()) null else redo.removeLast().also { undo.addLast(it) }.after
  fun clear() { undo.clear(); redo.clear() }
}
