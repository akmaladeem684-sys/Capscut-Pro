package com.ahstudio.editor.timeline.viewport

import com.ahstudio.editor.timeline.core.TimelineConstants
import kotlin.math.roundToLong

/**
 * Pure math + zoom/scroll state. The Playhead (Master CTI) is FIXED at 40% of the
 * viewport width (LEFT 40% / RIGHT 60%); ALL content scrolls underneath it. There is exactly ONE playhead X,
 * derived here and consumed by every layer (ruler, tracks, overlays).
 */
class TimelineViewport {
    companion object {
        const val MIN_PX_PER_SECOND = 15f
        const val MAX_PX_PER_SECOND = 3000f
    }

    var viewportWidthPx: Float = 0f
        set(v) { field = v.coerceAtLeast(1f) }

    var pxPerMicro: Double = 60.0 / 1_000_000.0   // 60 px per second default
        private set

    val playheadXPx: Float
        get() = viewportWidthPx * TimelineConstants.PLAYHEAD_X_FRACTION

    fun pxPerSecond(): Float = (pxPerMicro * 1_000_000.0).toFloat()

    // ---- conversions (Double math, Long micros out — no drift) ----
    fun timeAtContentPx(px: Float): Long = (px / pxPerMicro).roundToLong().coerceAtLeast(0L)
    fun contentPxAtTime(micros: Long): Float = (micros * pxPerMicro).toFloat()

    /** Content coordinate under the playhead for a given horizontal scroll offset. */
    fun timeAtScrollPx(scrollPx: Float): Long = timeAtContentPx(scrollPx + playheadXPx)

    /** Scroll offset that puts [micros] under the playhead. */
    fun scrollPxForTime(micros: Long): Float = contentPxAtTime(micros) - playheadXPx

    fun setZoomAroundTime(anchorMicros: Long, factor: Float): Float {
        val current = pxPerSecond()
        val target = (current * factor).coerceIn(MIN_PX_PER_SECOND, MAX_PX_PER_SECOND)
        pxPerMicro = target / 1_000_000.0
        return target / current  // actual applied ratio (after clamping)
    }

    fun zoomRatioBounds(factor: Float): Float = factor
}
