package com.ahstudio.editor.timeline.edit

import com.ahstudio.editor.timeline.core.TimelineConstants
import com.ahstudio.editor.timeline.engine.TimelineEngine
import com.ahstudio.editor.timeline.snap.SnapEngine
import com.ahstudio.editor.timeline.viewport.TimelineViewport
import kotlin.math.abs

class ClipPlanner(
    private val engine: TimelineEngine,
    private val snap: SnapEngine,
    private val viewport: TimelineViewport,
    private val playheadProvider: () -> Long,
) {
    data class Placement(val startMicros: Long, val trackId: String, val snappedTo: List<Long>)

    fun snapThresholdMicros(px: Float): Long =
        (px / viewport.pxPerMicro).toLong().coerceAtLeast(1L)

    fun resolveMove(
        clipId: String, targetTrackId: String, desiredStartMicros: Long,
        groupIds: Set<String>, snapEnabled: Boolean, snapThresholdPx: Float = 10f,
    ): Placement {
        val clip = engine.clip(clipId)
            ?: return Placement(desiredStartMicros.coerceAtLeast(0L), targetTrackId, emptyList())
        var start = desiredStartMicros.coerceAtLeast(0L)
        var snappedTo: List<Long> = emptyList()
        if (snapEnabled) {
            val r = snap.query(start, snapThresholdMicros(snapThresholdPx), groupIds, playheadProvider())
            if (r.snapped) { start = r.micros; snappedTo = r.matched }
        }
        return Placement(clampToTrackGap(targetTrackId, clipId, start, clip.durationMicros), targetTrackId, snappedTo)
    }

    /** Magnetic: fits into nearest valid gap; clamps desired into each gap's valid range. */
    fun clampToTrackGap(trackId: String, clipId: String, desiredStart: Long, duration: Long): Long {
        val clips = engine.clipsOn(trackId).filter { it.id != clipId }
        data class Gap(val lo: Long, val hi: Long?) // hi == null → infinite tail
        val gaps = ArrayList<Gap>()
        var cursor = 0L
        for (c in clips) {
            if (c.startMicros - cursor >= duration) gaps.add(Gap(cursor, c.startMicros))
            if (c.endMicros > cursor) cursor = c.endMicros
        }
        gaps.add(Gap(cursor, null))

        var best: Long? = null; var bestDist = Long.MAX_VALUE
        for (g in gaps) {
            val hi = g.hi ?: Long.MAX_VALUE
            val maxStart = hi - duration
            if (maxStart < g.lo) continue
            val cand = desiredStart.coerceIn(g.lo, maxStart)
            val d = abs(cand - desiredStart)
            if (d < bestDist) { bestDist = d; best = cand }
        }
        return best ?: desiredStart.coerceAtLeast(0L)
    }

    /** Trim clamps: neighbors + media extent + min duration. Returns (start, duration). */
    fun resolveTrim(clipId: String, newStart: Long, newEnd: Long): Pair<Long, Long> {
        val c = engine.clip(clipId) ?: return newStart to (newEnd - newStart)
        val (prev, next) = engine.indexes.neighbors(c.trackId, clipId)
        val minD = TimelineConstants.MIN_CLIP_MICROS
        val speed = c.speed.coerceAtLeast(0.01f)
        var s = newStart; var e = newEnd

        val thr = snapThresholdMicros(10f)
        snap.query(s, thr, setOf(clipId), playheadProvider()).takeIf { it.snapped }?.let { s = it.micros }
        snap.query(e, thr, setOf(clipId), playheadProvider()).takeIf { it.snapped }?.let { e = it.micros }

        val lower = maxOf(0L, prev?.endMicros ?: 0L, c.startMicros - (c.sourceInMicros / speed).toLong())
        val upper = next?.startMicros ?: Long.MAX_VALUE

        e = e.coerceAtMost(upper)
        s = s.coerceAtLeast(lower)
        if (e - s < minD) e = s + minD // engine validation surfaces impossible media trims
        c.sourceDurationMicros?.let { limit ->
            val maxEnd = c.startMicros + ((limit - c.sourceInMicros) / speed).toLong()
            if (e > maxEnd) { e = maxEnd; s = s.coerceAtMost(e - minD) }
        }
        return s to (e - s)
    }
}
