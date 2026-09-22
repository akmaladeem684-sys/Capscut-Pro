package com.ahstudio.editor.timeline.snap

import com.ahstudio.editor.timeline.engine.TimelineEngine
import kotlin.math.abs

data class SnapResult(val micros: Long, val matched: List<Long>, val snapped: Boolean)

class SnapEngine(private val engine: TimelineEngine) {

    /**
     * Binary-search the sorted boundary index, skipping boundaries that belong to
     * excluded clips (the one being dragged never snaps to itself).
     * Candidates: clip starts/ends, keyframe absolute times, markers, t=0, playhead.
     */
    fun query(
        targetMicros: Long,
        thresholdMicros: Long,
        excludeClipIds: Set<String> = emptySet(),
        playheadMicros: Long? = null,
    ): SnapResult {
        if (thresholdMicros <= 0) return SnapResult(targetMicros, emptyList(), false)
        val idx = engine.indexes
        var best: Long? = null; var bestDist = Long.MAX_VALUE; val matched = ArrayList<Long>(2)

        fun consider(cand: Long, owner: String?) {
            if (owner != null && owner in excludeClipIds) return
            val d = abs(cand - targetMicros)
            if (d <= thresholdMicros && d < bestDist) { bestDist = d; best = cand }
            else if (best != null && d == bestDist && cand != best) matched.add(cand)
        }

        val b = idx.boundaries; val owners = idx.boundaryOwners
        var lo = 0; var hi = b.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (b[mid] < targetMicros) lo = mid + 1 else hi = mid - 1
        }
        var i = hi; var steps = 0
        while (i >= 0 && steps <= 3) { consider(b[i], owners[i]); i--; steps++ }
        i = lo; steps = 0
        while (i < b.size && steps <= 3) { consider(b[i], owners[i]); i++; steps++ }

        playheadMicros?.let { consider(it, null) }

        return if (best != null) {
            matched.add(0, best!!)
            SnapResult(best!!, matched, true)
        } else SnapResult(targetMicros, emptyList(), false)
    }
}
