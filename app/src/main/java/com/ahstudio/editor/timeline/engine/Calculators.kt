package com.ahstudio.editor.timeline.engine

import com.ahstudio.editor.timeline.core.TimelineConstants
import com.ahstudio.editor.timeline.model.TimelineClip
import kotlin.math.abs

object PlaceCalculator {
    data class Placement(val startUs: Long, val durationUs: Long)

    /** First free gap at/after [desiredStart] long enough for [duration]. */
    fun findFreePlacement(sorted: List<TimelineClip>, desiredStart: Long, duration: Long): Placement {
        var cursor = desiredStart.coerceAtLeast(0L)
        for (clip in sorted) {
            if (clip.endUs <= cursor) continue
            if (clip.startUs >= cursor + duration) break
            cursor = clip.endUs
        }
        return Placement(cursor, duration)
    }
}

object MoveCalculator {
    data class Item(val clipId: Long, val origTrackId: Long, val origStart: Long, val duration: Long)
    data class Result(val newTrackId: Long, val newStartUs: Long)

    fun calculateMove(
        item: Item,
        targetTrackId: Long,
        proposedStartUs: Long,
        otherClipsOnTarget: List<TimelineClip>,
        allowOverlap: Boolean = false
    ): Result {
        val clampedStart = proposedStartUs.coerceAtLeast(0L)
        if (allowOverlap) return Result(targetTrackId, clampedStart)

        val duration = item.duration
        var start = clampedStart
        val filtered = otherClipsOnTarget.filter { it.id != item.clipId }
        for (c in filtered.sortedBy { it.startUs }) {
            val end = c.startUs + c.durationUs
            if (c.endUs <= start) continue
            if (c.startUs >= start + duration) break
            start = end
        }
        return Result(targetTrackId, start)
    }
}

object TrimCalculator {
    fun calculateTrim(
        clip: TimelineClip,
        edge: TrimEdge,
        proposedUs: Long,
        otherClips: List<TimelineClip>
    ): TimelineClip {
        val filtered = otherClips.filter { it.id != clip.id }.sortedBy { it.startUs }
        return when (edge) {
            TrimEdge.LEFT -> {
                val maxLeft = filtered.lastOrNull { it.endUs <= clip.endUs }?.endUs ?: 0L
                val minLeft = clip.endUs - TimelineConstants.MIN_CLIP_DURATION_US
                val newStart = proposedUs.coerceIn(maxLeft, minLeft)
                val delta = newStart - clip.startUs
                val newDuration = clip.durationUs - delta
                val newSourceIn = (clip.sourceInUs + delta).coerceAtLeast(0L)
                val newSourceDur = (clip.sourceDurationUs - delta).coerceAtLeast(TimelineConstants.MIN_CLIP_DURATION_US)
                clip.copy(
                    startUs = newStart,
                    durationUs = newDuration,
                    sourceInUs = newSourceIn,
                    sourceDurationUs = newSourceDur
                )
            }
            TrimEdge.RIGHT -> {
                val minRight = clip.startUs + TimelineConstants.MIN_CLIP_DURATION_US
                val nextClip = filtered.firstOrNull { it.startUs >= clip.startUs }
                val maxRight = nextClip?.startUs ?: Long.MAX_VALUE
                val newEnd = proposedUs.coerceIn(minRight, maxRight)
                val newDuration = newEnd - clip.startUs
                val newSourceDur = newDuration.coerceAtLeast(TimelineConstants.MIN_CLIP_DURATION_US)
                clip.copy(
                    durationUs = newDuration,
                    sourceDurationUs = newSourceDur
                )
            }
        }
    }
}
