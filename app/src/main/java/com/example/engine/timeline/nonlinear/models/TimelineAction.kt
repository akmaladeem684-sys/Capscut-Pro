package com.example.engine.timeline.nonlinear.models

/**
 * Sealed interface representing all pure state mutations on the non-linear timeline.
 */
sealed interface TimelineAction {

  /**
   * Moves a clip to a new start time and optionally across tracks.
   */
  data class MoveClip(
    val clipId: String,
    val targetTrackId: String,
    val newStartTimeUs: Long
  ) : TimelineAction

  /**
   * Trims the Head (in-point) or Tail (out-point) of a clip by a microsecond delta.
   */
  data class TrimClip(
    val clipId: String,
    val edge: TrimEdge,
    val deltaUs: Long
  ) : TimelineAction

  /**
   * Splits a clip into two independent non-linear clips at the specified split timestamp.
   */
  data class SplitClip(
    val clipId: String,
    val splitTimestampUs: Long
  ) : TimelineAction

  /**
   * Deletes a clip and shifts all subsequent clips on the same track to close the gap.
   */
  data class RippleDelete(
    val clipId: String
  ) : TimelineAction

  /**
   * Deletes a clip while preserving the blank gap in the non-linear track.
   */
  data class GapDelete(
    val clipId: String
  ) : TimelineAction

  /**
   * Updates clip selection state.
   */
  data class SelectClip(
    val clipId: String?
  ) : TimelineAction

  /**
   * Moves the current playhead needle position.
   */
  data class SetPlayhead(
    val timestampUs: Long
  ) : TimelineAction

  /**
   * Reorders tracks vertically, dynamically updating track priorities and z-indexes.
   */
  data class ReorderTracks(
    val trackIds: List<String>
  ) : TimelineAction

  /**
   * Inserts a new clip onto a specific track.
   */
  data class AddClip(
    val trackId: String,
    val clip: Clip
  ) : TimelineAction

  /**
   * Appends a new track to the timeline.
   */
  data class AddTrack(
    val track: Track
  ) : TimelineAction

  /**
   * Removes a track and all its clips.
   */
  data class RemoveTrack(
    val trackId: String
  ) : TimelineAction

  /**
   * Toggles mute state for a track.
   */
  data class ToggleTrackMute(
    val trackId: String
  ) : TimelineAction

  /**
   * Toggles lock state for a track.
   */
  data class ToggleTrackLock(
    val trackId: String
  ) : TimelineAction

  /**
   * Adjusts horizontal zoom scale in pixels-per-second.
   */
  data class SetZoomLevel(
    val zoomLevelPxPerSec: Float
  ) : TimelineAction

  /**
   * Adjusts horizontal scroll offset in pixels.
   */
  data class SetScrollOffset(
    val scrollOffsetPx: Float
  ) : TimelineAction

  /**
   * Adds a marker at a timeline position.
   */
  data class AddMarker(
    val marker: TimelineMarker
  ) : TimelineAction

  /**
   * Removes a marker by ID.
   */
  data class RemoveMarker(
    val markerId: String
  ) : TimelineAction
}
