package com.example.engine.timeline.nonlinear.math

import com.example.engine.timeline.nonlinear.models.SnapResult
import com.example.engine.timeline.nonlinear.models.SnapType
import com.example.engine.timeline.nonlinear.models.TimelineState
import kotlin.math.abs

/**
 * Magnetic Snapping Engine for non-linear multi-track timeline editing.
 * Checks proximity within a pixel threshold (default 15px) to:
 * 1. The Playhead
 * 2. Nearby clip edges (head and tail) on the same track
 * 3. Clip edges across all adjacent tracks
 * 4. Timeline markers
 * 5. The timeline zero origin
 */
object MagneticSnappingEngine {

  /**
   * Snaps a moving clip's proposed start time to the nearest magnet anchor.
   */
  fun snapClipMove(
    candidateStartUs: Long,
    clipDurationUs: Long,
    movingClipId: String,
    targetTrackId: String,
    state: TimelineState
  ): SnapResult {
    if (!state.snapEnabled) {
      return SnapResult(
        originalTimeUs = candidateStartUs,
        snappedTimeUs = candidateStartUs.coerceAtLeast(0L),
        snapTargetTimeUs = candidateStartUs,
        isSnapped = false
      )
    }

    val thresholdUs = TimelineTimeMath.pixelsToUs(state.snapThresholdPx, state.zoomLevelPxPerSec)
    val candidateEndUs = candidateStartUs + clipDurationUs

    var bestDiffUs = Long.MAX_VALUE
    var bestSnappedStartUs = candidateStartUs
    var bestTargetTimeUs = candidateStartUs
    var bestSnapType = SnapType.NONE

    fun testTarget(targetUs: Long, type: SnapType) {
      // 1. Test snapping Head (start) to target
      val headDiff = abs(candidateStartUs - targetUs)
      if (headDiff <= thresholdUs && headDiff < bestDiffUs) {
        val proposedStart = targetUs.coerceAtLeast(0L)
        bestDiffUs = headDiff
        bestSnappedStartUs = proposedStart
        bestTargetTimeUs = targetUs
        bestSnapType = type
      }

      // 2. Test snapping Tail (end) to target
      val tailDiff = abs(candidateEndUs - targetUs)
      if (tailDiff <= thresholdUs && tailDiff < bestDiffUs) {
        val proposedStart = (targetUs - clipDurationUs).coerceAtLeast(0L)
        bestDiffUs = tailDiff
        bestSnappedStartUs = proposedStart
        bestTargetTimeUs = targetUs
        bestSnapType = type
      }
    }

    // Anchor 1: Timeline Origin
    testTarget(0L, SnapType.CLIP_START)

    // Anchor 2: Playhead
    testTarget(state.playheadUs, SnapType.PLAYHEAD)

    // Anchor 3: Markers
    for (marker in state.markers) {
      testTarget(marker.timestampUs, SnapType.MARKER)
    }

    // Anchor 4: Clips on Target Track (highest priority)
    val targetTrack = state.findTrack(targetTrackId)
    if (targetTrack != null) {
      for (clip in targetTrack.clips) {
        if (clip.id == movingClipId) continue
        testTarget(clip.startTimeUs, SnapType.CLIP_START)
        testTarget(clip.endTimeUs, SnapType.CLIP_END)
      }
    }

    // Anchor 5: Clips across all other tracks
    for (track in state.tracks) {
      if (track.id == targetTrackId) continue
      for (clip in track.clips) {
        if (clip.id == movingClipId) continue
        testTarget(clip.startTimeUs, SnapType.CLIP_START)
        testTarget(clip.endTimeUs, SnapType.CLIP_END)
      }
    }

    val isSnapped = bestSnapType != SnapType.NONE && bestDiffUs <= thresholdUs
    val guidePx = if (isSnapped) TimelineTimeMath.usToPixels(bestTargetTimeUs, state.zoomLevelPxPerSec) else 0f

    return SnapResult(
      originalTimeUs = candidateStartUs,
      snappedTimeUs = if (isSnapped) bestSnappedStartUs else candidateStartUs.coerceAtLeast(0L),
      snapTargetTimeUs = if (isSnapped) bestTargetTimeUs else candidateStartUs,
      isSnapped = isSnapped,
      snapType = bestSnapType,
      guideLinePx = guidePx
    )
  }

  /**
   * Snaps a trimming edge (Head or Tail) to nearby anchors.
   */
  fun snapEdgeTrim(
    candidateEdgeTimeUs: Long,
    clipId: String,
    state: TimelineState
  ): SnapResult {
    if (!state.snapEnabled) {
      return SnapResult(
        originalTimeUs = candidateEdgeTimeUs,
        snappedTimeUs = candidateEdgeTimeUs.coerceAtLeast(0L),
        snapTargetTimeUs = candidateEdgeTimeUs,
        isSnapped = false
      )
    }

    val thresholdUs = TimelineTimeMath.pixelsToUs(state.snapThresholdPx, state.zoomLevelPxPerSec)
    var bestDiffUs = Long.MAX_VALUE
    var bestTargetTimeUs = candidateEdgeTimeUs
    var bestSnapType = SnapType.NONE

    fun testTarget(targetUs: Long, type: SnapType) {
      val diff = abs(candidateEdgeTimeUs - targetUs)
      if (diff <= thresholdUs && diff < bestDiffUs) {
        bestDiffUs = diff
        bestTargetTimeUs = targetUs
        bestSnapType = type
      }
    }

    // 1. Playhead
    testTarget(state.playheadUs, SnapType.PLAYHEAD)

    // 2. Timeline Origin
    testTarget(0L, SnapType.CLIP_START)

    // 3. Markers
    for (marker in state.markers) {
      testTarget(marker.timestampUs, SnapType.MARKER)
    }

    // 4. Clip edges across all tracks
    for (track in state.tracks) {
      for (clip in track.clips) {
        if (clip.id == clipId) continue
        testTarget(clip.startTimeUs, SnapType.CLIP_START)
        testTarget(clip.endTimeUs, SnapType.CLIP_END)
      }
    }

    val isSnapped = bestSnapType != SnapType.NONE && bestDiffUs <= thresholdUs
    val guidePx = if (isSnapped) TimelineTimeMath.usToPixels(bestTargetTimeUs, state.zoomLevelPxPerSec) else 0f

    return SnapResult(
      originalTimeUs = candidateEdgeTimeUs,
      snappedTimeUs = if (isSnapped) bestTargetTimeUs else candidateEdgeTimeUs.coerceAtLeast(0L),
      snapTargetTimeUs = if (isSnapped) bestTargetTimeUs else candidateEdgeTimeUs,
      isSnapped = isSnapped,
      snapType = bestSnapType,
      guideLinePx = guidePx
    )
  }
}
