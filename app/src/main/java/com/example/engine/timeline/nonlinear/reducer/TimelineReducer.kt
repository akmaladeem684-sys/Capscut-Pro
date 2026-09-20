package com.example.engine.timeline.nonlinear.reducer

import com.example.engine.timeline.nonlinear.math.TimelineTimeMath
import com.example.engine.timeline.nonlinear.models.*
import java.util.UUID

/**
 * Pure state-reducer function for the Non-Linear Multi-Track Timeline.
 * Implements deterministic mutations, collision handling, dynamic z-indexing,
 * ripple vs gap deletions, and time bounds clamping.
 */
object TimelineReducer {

  /**
   * Main pure reducer entry point.
   */
  fun reduce(state: TimelineState, action: TimelineAction): TimelineState {
    return when (action) {
      is TimelineAction.MoveClip -> handleMoveClip(state, action)
      is TimelineAction.TrimClip -> handleTrimClip(state, action)
      is TimelineAction.SplitClip -> handleSplitClip(state, action)
      is TimelineAction.RippleDelete -> handleRippleDelete(state, action)
      is TimelineAction.GapDelete -> handleGapDelete(state, action)
      is TimelineAction.SelectClip -> handleSelectClip(state, action)
      is TimelineAction.SetPlayhead -> state.copy(playheadUs = action.timestampUs.coerceAtLeast(0L))
      is TimelineAction.ReorderTracks -> handleReorderTracks(state, action)
      is TimelineAction.AddClip -> handleAddClip(state, action)
      is TimelineAction.AddTrack -> handleAddTrack(state, action)
      is TimelineAction.RemoveTrack -> handleRemoveTrack(state, action)
      is TimelineAction.ToggleTrackMute -> handleToggleTrackMute(state, action)
      is TimelineAction.ToggleTrackLock -> handleToggleTrackLock(state, action)
      is TimelineAction.SetZoomLevel -> state.copy(zoomLevelPxPerSec = action.zoomLevelPxPerSec.coerceIn(1f, 2000f))
      is TimelineAction.SetScrollOffset -> state.copy(scrollOffsetPx = action.scrollOffsetPx.coerceAtLeast(0f))
      is TimelineAction.AddMarker -> state.copy(markers = (state.markers + action.marker).sortedBy { it.timestampUs })
      is TimelineAction.RemoveMarker -> state.copy(markers = state.markers.filterNot { it.id == action.markerId })
    }
  }

  private fun handleMoveClip(state: TimelineState, action: TimelineAction.MoveClip): TimelineState {
    val found = state.findClip(action.clipId) ?: return state
    val (sourceTrack, clip) = found
    if (sourceTrack.isLocked) return state

    val targetTrack = state.findTrack(action.targetTrackId) ?: sourceTrack
    if (targetTrack.isLocked) return state

    val safeStartTimeUs = action.newStartTimeUs.coerceAtLeast(0L)
    val movedClip = clip.copy(
      startTimeUs = safeStartTimeUs,
      isSelected = true
    )

    val updatedTracks = state.tracks.map { track ->
      when {
        // Moving within the same track
        sourceTrack.id == targetTrack.id && track.id == sourceTrack.id -> {
          val remainingClips = track.clips.filterNot { it.id == clip.id }
          track.copy(clips = (remainingClips + movedClip).sortedBy { it.startTimeUs })
        }
        // Moving from sourceTrack to another targetTrack
        track.id == sourceTrack.id -> {
          track.copy(clips = track.clips.filterNot { it.id == clip.id })
        }
        track.id == targetTrack.id -> {
          track.copy(clips = (track.clips + movedClip).sortedBy { it.startTimeUs })
        }
        else -> track
      }
    }

    return state.copy(tracks = updatedTracks, selectedClipId = clip.id).recomputeZIndexes()
  }

  private fun handleTrimClip(state: TimelineState, action: TimelineAction.TrimClip): TimelineState {
    val found = state.findClip(action.clipId) ?: return state
    val (track, clip) = found
    if (track.isLocked) return state

    val minDurationUs = TimelineTimeMath.MIN_CLIP_DURATION_US
    val trimmedClip = when (action.edge) {
      TrimEdge.HEAD -> {
        // Delta > 0 trims right (shortens head), Delta < 0 expands left
        val proposedStartUs = clip.startTimeUs + action.deltaUs
        val clampedStartUs = proposedStartUs.coerceAtLeast(0L)
        val actualDeltaUs = clampedStartUs - clip.startTimeUs

        val proposedDurationUs = clip.durationUs - actualDeltaUs
        if (proposedDurationUs < minDurationUs) {
          // Duration clamped to minimum
          val maxStartUs = clip.endTimeUs - minDurationUs
          val finalDeltaUs = maxStartUs - clip.startTimeUs
          val sourceDeltaUs = (finalDeltaUs * clip.speed).toLong()
          val finalSourceTrimStartUs = (clip.sourceTrimStartUs + sourceDeltaUs).coerceIn(0L, clip.sourceDurationUs)
          clip.copy(
            startTimeUs = maxStartUs,
            durationUs = minDurationUs,
            sourceTrimStartUs = finalSourceTrimStartUs
          )
        } else {
          val sourceDeltaUs = (actualDeltaUs * clip.speed).toLong()
          val finalSourceTrimStartUs = (clip.sourceTrimStartUs + sourceDeltaUs).coerceIn(0L, clip.sourceDurationUs)
          clip.copy(
            startTimeUs = clampedStartUs,
            durationUs = proposedDurationUs,
            sourceTrimStartUs = finalSourceTrimStartUs
          )
        }
      }
      TrimEdge.TAIL -> {
        // Delta > 0 expands right, Delta < 0 shortens left
        val proposedDurationUs = clip.durationUs + action.deltaUs
        val maxAvailableSourceDurationUs = if (clip.sourceDurationUs > 0L) {
          ((clip.sourceDurationUs - clip.sourceTrimStartUs) / clip.speed).toLong()
        } else {
          Long.MAX_VALUE
        }
        val clampedDurationUs = proposedDurationUs.coerceIn(minDurationUs, maxAvailableSourceDurationUs)
        clip.copy(durationUs = clampedDurationUs)
      }
    }

    val updatedTracks = state.tracks.map { t ->
      if (t.id == track.id) {
        t.copy(clips = t.clips.map { if (it.id == clip.id) trimmedClip else it }.sortedBy { it.startTimeUs })
      } else t
    }

    return state.copy(tracks = updatedTracks, selectedClipId = clip.id)
  }

  private fun handleSplitClip(state: TimelineState, action: TimelineAction.SplitClip): TimelineState {
    val found = state.findClip(action.clipId) ?: return state
    val (track, clip) = found
    if (track.isLocked) return state

    val splitUs = action.splitTimestampUs
    val minDuration = TimelineTimeMath.MIN_CLIP_DURATION_US

    // Verifies split occurs cleanly within clip boundaries with minimum margins
    if (splitUs <= clip.startTimeUs + minDuration || splitUs >= clip.endTimeUs - minDuration) {
      return state // Out of valid split bounds
    }

    val headDurationUs = splitUs - clip.startTimeUs
    val tailDurationUs = clip.durationUs - headDurationUs

    val headSourceTrimStartUs = clip.sourceTrimStartUs
    val tailSourceTrimStartUs = clip.sourceTrimStartUs + (headDurationUs * clip.speed).toLong()

    val headClip = clip.copy(
      durationUs = headDurationUs
    )

    val tailClip = clip.copy(
      id = UUID.randomUUID().toString(),
      name = "${clip.name} (Part 2)",
      startTimeUs = splitUs,
      durationUs = tailDurationUs,
      sourceTrimStartUs = tailSourceTrimStartUs,
      isSelected = true
    )

    val updatedTracks = state.tracks.map { t ->
      if (t.id == track.id) {
        val remaining = t.clips.filterNot { it.id == clip.id }
        t.copy(clips = (remaining + headClip + tailClip).sortedBy { it.startTimeUs })
      } else t
    }

    return state.copy(tracks = updatedTracks, selectedClipId = tailClip.id).recomputeZIndexes()
  }

  private fun handleRippleDelete(state: TimelineState, action: TimelineAction.RippleDelete): TimelineState {
    val found = state.findClip(action.clipId) ?: return state
    val (track, clip) = found
    if (track.isLocked) return state

    val shiftAmountUs = clip.durationUs
    val deletedEndTimeUs = clip.endTimeUs

    val updatedClips = track.clips
      .filterNot { it.id == clip.id }
      .map { other ->
        if (other.startTimeUs >= deletedEndTimeUs) {
          other.copy(startTimeUs = (other.startTimeUs - shiftAmountUs).coerceAtLeast(0L))
        } else {
          other
        }
      }
      .sortedBy { it.startTimeUs }

    val updatedTracks = state.tracks.map { t ->
      if (t.id == track.id) t.copy(clips = updatedClips) else t
    }

    val newSelectedId = state.selectedClipId.takeUnless { it == clip.id }
    return state.copy(tracks = updatedTracks, selectedClipId = newSelectedId).recomputeZIndexes()
  }

  private fun handleGapDelete(state: TimelineState, action: TimelineAction.GapDelete): TimelineState {
    val found = state.findClip(action.clipId) ?: return state
    val (track, clip) = found
    if (track.isLocked) return state

    // Deletes clip, leaving blank gap
    val updatedTracks = state.tracks.map { t ->
      if (t.id == track.id) {
        t.copy(clips = t.clips.filterNot { it.id == clip.id })
      } else t
    }

    val newSelectedId = state.selectedClipId.takeUnless { it == clip.id }
    return state.copy(tracks = updatedTracks, selectedClipId = newSelectedId).recomputeZIndexes()
  }

  private fun handleSelectClip(state: TimelineState, action: TimelineAction.SelectClip): TimelineState {
    val updatedTracks = state.tracks.map { track ->
      track.copy(clips = track.clips.map { clip ->
        clip.copy(isSelected = clip.id == action.clipId)
      })
    }
    return state.copy(tracks = updatedTracks, selectedClipId = action.clipId)
  }

  private fun handleReorderTracks(state: TimelineState, action: TimelineAction.ReorderTracks): TimelineState {
    val trackMap = state.tracks.associateBy { it.id }
    val reordered = action.trackIds.mapNotNull { trackMap[it] }
    val remaining = state.tracks.filterNot { action.trackIds.contains(it.id) }
    return state.copy(tracks = reordered + remaining).recomputeZIndexes()
  }

  private fun handleAddClip(state: TimelineState, action: TimelineAction.AddClip): TimelineState {
    val targetTrack = state.findTrack(action.trackId) ?: return state
    if (targetTrack.isLocked) return state

    val newClip = action.clip.copy(isSelected = true)
    val updatedTracks = state.tracks.map { track ->
      if (track.id == targetTrack.id) {
        track.copy(clips = (track.clips + newClip).sortedBy { it.startTimeUs })
      } else {
        track.copy(clips = track.clips.map { it.copy(isSelected = false) })
      }
    }

    return state.copy(tracks = updatedTracks, selectedClipId = newClip.id).recomputeZIndexes()
  }

  private fun handleAddTrack(state: TimelineState, action: TimelineAction.AddTrack): TimelineState {
    val updatedTracks = (state.tracks + action.track)
    return state.copy(tracks = updatedTracks).recomputeZIndexes()
  }

  private fun handleRemoveTrack(state: TimelineState, action: TimelineAction.RemoveTrack): TimelineState {
    val updatedTracks = state.tracks.filterNot { it.id == action.trackId }
    return state.copy(tracks = updatedTracks).recomputeZIndexes()
  }

  private fun handleToggleTrackMute(state: TimelineState, action: TimelineAction.ToggleTrackMute): TimelineState {
    val updatedTracks = state.tracks.map { track ->
      if (track.id == action.trackId) track.copy(isMuted = !track.isMuted) else track
    }
    return state.copy(tracks = updatedTracks)
  }

  private fun handleToggleTrackLock(state: TimelineState, action: TimelineAction.ToggleTrackLock): TimelineState {
    val updatedTracks = state.tracks.map { track ->
      if (track.id == action.trackId) track.copy(isLocked = !track.isLocked) else track
    }
    return state.copy(tracks = updatedTracks)
  }
}
