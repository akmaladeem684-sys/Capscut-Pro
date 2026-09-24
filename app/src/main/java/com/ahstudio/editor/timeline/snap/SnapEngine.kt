package com.ahstudio.editor.timeline.snap

import com.ahstudio.editor.timeline.engine.TimelineEngine
import kotlin.math.abs
import kotlin.math.roundToLong

data class SnapResult(val micros: Long, val matched: List<Long>, val snapped: Boolean)

class SnapEngine(private val engine: TimelineEngine) {

    companion object {
        const val NTSC_29_97_FPS = 30000.0 / 1001.0
        const val NTSC_59_94_FPS = 60000.0 / 1001.0
        const val NTSC_23_976_FPS = 24000.0 / 1001.0

        /**
         * Resolves exact integer numerator & denominator for fractional & standard NLE frame rates.
         */
        fun getRationalFps(fps: Double): Pair<Long, Long> {
            return when {
                abs(fps - NTSC_29_97_FPS) < 0.05 || abs(fps - 29.97) < 0.05 -> Pair(30000L, 1001L)
                abs(fps - NTSC_59_94_FPS) < 0.05 || abs(fps - 59.94) < 0.05 -> Pair(60000L, 1001L)
                abs(fps - NTSC_23_976_FPS) < 0.05 || abs(fps - 23.976) < 0.05 || abs(fps - 23.98) < 0.05 -> Pair(24000L, 1001L)
                abs(fps - 24.0) < 0.05 -> Pair(24L, 1L)
                abs(fps - 25.0) < 0.05 -> Pair(25L, 1L)
                abs(fps - 30.0) < 0.05 -> Pair(30L, 1L)
                abs(fps - 50.0) < 0.05 -> Pair(50L, 1L)
                abs(fps - 60.0) < 0.05 -> Pair(60L, 1L)
                abs(fps - 120.0) < 0.05 -> Pair(120L, 1L)
                fps > 0.0 -> {
                    val rounded = Math.round(fps).coerceAtLeast(1L)
                    Pair(rounded, 1L)
                }
                else -> Pair(30L, 1L)
            }
        }
    }

    /**
     * Integer rational frame quantization for precision NTSC & standard frame alignment.
     * Prevents 1-frame micro-gaps and sub-frame rounding drift across fractional frame boundaries.
     */
    fun alignToRationalFrame(micros: Long, fps: Double = 30.0): Long {
        if (fps <= 0.0 || micros < 0L) return micros.coerceAtLeast(0L)
        val (num, den) = getRationalFps(fps)
        // Exact frame number calculation using integer rounding
        val frameNumber = (micros * num + (den * 500_000L)) / (den * 1_000_000L)
        // Exact microsecond boundary for frame number without floating-point drift
        return (frameNumber * den * 1_000_000L) / num
    }

    /**
     * Millisecond version for UI timeline snapping.
     */
    fun alignToRationalFrameMs(millis: Long, fps: Double = 30.0): Long {
        if (fps <= 0.0 || millis < 0L) return millis.coerceAtLeast(0L)
        val (num, den) = getRationalFps(fps)
        val frameNumber = (millis * num + (den * 500L)) / (den * 1_000L)
        return (frameNumber * den * 1_000L) / num
    }

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
        fps: Double? = null
    ): SnapResult {
        if (thresholdMicros <= 0) return SnapResult(targetMicros, emptyList(), false)
        val idx = engine.indexes
        var best: Long? = null
        var bestDist = Long.MAX_VALUE
        val matched = ArrayList<Long>(2)

        fun consider(cand: Long, owner: String?) {
            if (owner != null && owner in excludeClipIds) return
            val d = abs(cand - targetMicros)
            if (d <= thresholdMicros && d < bestDist) {
                bestDist = d
                best = cand
            } else if (best != null && d == bestDist && cand != best) {
                matched.add(cand)
            }
        }

        val b = idx.boundaries
        val owners = idx.boundaryOwners
        var lo = 0
        var hi = b.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (b[mid] < targetMicros) lo = mid + 1 else hi = mid - 1
        }
        var i = hi
        var steps = 0
        while (i >= 0 && steps <= 3) {
            consider(b[i], owners[i])
            i--
            steps++
        }
        i = lo
        steps = 0
        while (i < b.size && steps <= 3) {
            consider(b[i], owners[i])
            i++
            steps++
        }

        playheadMicros?.let { consider(it, null) }

        // If fps is provided and no edge was matched, snap to exact rational frame grid
        if (best == null && fps != null && fps > 0.0) {
            val aligned = alignToRationalFrame(targetMicros, fps)
            val d = abs(aligned - targetMicros)
            if (d <= thresholdMicros) {
                return SnapResult(aligned, listOf(aligned), true)
            }
        }

        return if (best != null) {
            matched.add(0, best!!)
            SnapResult(best!!, matched, true)
        } else {
            SnapResult(targetMicros, emptyList(), false)
        }
    }
}
