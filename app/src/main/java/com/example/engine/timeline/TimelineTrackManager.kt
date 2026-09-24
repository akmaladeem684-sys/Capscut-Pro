package com.example.engine.timeline

import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.domain.model.TextClip
import com.example.domain.model.AudioClip
import com.example.domain.model.EffectClip
import com.example.domain.model.StickerClip
import com.example.ui.components.timeline.LaneClipItem
import com.example.ui.components.timeline.LaneKind
import com.example.ui.components.timeline.TimelineLane
import com.example.ui.components.timeline.TrackLaneManager

/**
 * Principal Timeline Track & Layer (Z-Index) Manager.
 *
 * Responsibilities:
 * 1. Capture and enforce Authoritative CTI Playhead insertion time (never fallback to 0s or auto-end).
 * 2. Dynamic Track Generation & Unique Z-Index Allocation for multiple simultaneous overlays & text layers.
 * 3. Prevents track collisions and maintains clear visual/layer hierarchy.
 */
object TimelineTrackManager {

    /**
     * Captures the authoritative CTI playhead position to be used as insertion start time.
     * Guaranteed to use the exact playhead timestamp without resetting to 0s or snapping to end.
     */
    fun getAuthoritativeInsertionTime(currentCtiMs: Long): Long {
        return currentCtiMs.coerceAtLeast(0L)
    }

    /**
     * Allocates a guaranteed unique Z-Index / TrackIndex for a newly created overlay.
     * Ensures each overlay lives on a dedicated layer stack (Overlay 1, 2, 3... N).
     */
    fun allocateOverlayTrackIndex(timeline: Timeline): Int {
        val existingIndices = timeline.overlayClips.map { it.trackIndex }
        val maxIndex = existingIndices.maxOrNull() ?: 0
        return maxIndex + 1
    }

    /**
     * Allocates a guaranteed unique TrackIndex for a newly created text clip.
     */
    fun allocateTextTrackIndex(timeline: Timeline): Int {
        val existingIndices = timeline.textClips.map { it.trackIndex }
        val maxIndex = existingIndices.maxOrNull() ?: 0
        return maxIndex + 1
    }

    /**
     * Allocates a guaranteed unique TrackIndex for a newly created audio clip.
     */
    fun allocateAudioTrackIndex(timeline: Timeline): Int {
        val existingIndices = timeline.audioClips.map { it.trackIndex }
        val maxIndex = existingIndices.maxOrNull() ?: 0
        return maxIndex + 1
    }

    /**
     * Re-assigns strict unique sequential Z-Indices to all overlay clips.
     * Useful when clips are reordered, deleted, or merged.
     */
    fun normalizeOverlayZIndices(overlays: List<VideoClip>): List<VideoClip> {
        return overlays.mapIndexed { index, clip ->
            clip.copy(trackIndex = index + 1)
        }
    }

    /**
     * Generates all dynamic timeline lanes via TrackLaneManager.
     */
    fun generateDynamicLanes(timeline: Timeline): List<TimelineLane> {
        return TrackLaneManager.computeLanes(timeline)
    }
}
