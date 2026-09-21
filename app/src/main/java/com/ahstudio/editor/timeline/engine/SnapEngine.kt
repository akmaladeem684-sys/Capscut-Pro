package com.ahstudio.editor.timeline.engine

import com.ahstudio.editor.timeline.core.TimelineConstants
import com.ahstudio.editor.timeline.model.TimelineClip
import com.ahstudio.editor.timeline.model.TimelineMarker
import kotlin.math.abs

data class SnapResult(val snappedUs: Long, val snapped: Boolean, val targetUs: Long? = null)

object SnapEngine {
    fun snap(
        rawUs: Long,
        playheadUs: Long,
        clips: List<TimelineClip>,
        markers: List<TimelineMarker>,
        zoomPxPerSecond: Float,
        thresholdDp: Float = TimelineConstants.SNAP_THRESHOLD_DP,
        excludeClipId: Long? = null
    ): SnapResult {
        val thresholdUs = ((thresholdDp / 160f) * 1000f * (1000f / zoomPxPerSecond)).toLong().coerceAtLeast(5_000L)
        val candidates = ArrayList<Long>()
        candidates.add(playheadUs)
        for (m in markers) candidates.add(m.timeUs)
        for (c in clips) {
            if (c.id == excludeClipId) continue
            candidates.add(c.startUs)
            candidates.add(c.endUs)
        }

        var bestTarget: Long? = null
        var bestDist = Long.MAX_VALUE
        for (cand in candidates) {
            val dist = abs(cand - rawUs)
            if (dist < thresholdUs && dist < bestDist) {
                bestDist = dist
                bestTarget = cand
            }
        }

        return if (bestTarget != null) {
            SnapResult(bestTarget, true, bestTarget)
        } else {
            SnapResult(rawUs, false, null)
        }
    }
}
