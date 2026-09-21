package com.ahstudio.editor.timeline.core

import java.util.Locale
import kotlin.math.roundToLong

object TimelineConstants {
    const val PLAYHEAD_VIEWPORT_FRACTION = 0.10f   // CTI fixed at 10% from left
    const val MIN_CLIP_DURATION_US = 20_000L       // 20 ms
    const val GRAPHIC_INFINITE_SOURCE_US = 4L * 60 * 60 * 1_000_000L
    const val MAX_TIME_US = 24L * 60 * 60 * 1_000_000L
    const val UNDO_LIMIT = 100
    const val SNAP_THRESHOLD_DP = 12f
    const val TRIM_HANDLE_WIDTH_DP = 14f
    const val ZOOM_MIN_PX_PER_SECOND = 4f
    const val ZOOM_MAX_PX_PER_SECOND = 480f
    const val ZOOM_DEFAULT_PX_PER_SECOND = 64f
    const val KEYFRAME_DRAW_PX_PER_SECOND = 120f
}

/** Frame math — derived from the single frame-rate on the clock. */
object FrameMath {
    fun usToFrame(us: Long, fps: Float): Long = (us / 1_000_000.0 * fps).roundToLong()
    fun frameToUs(frame: Long, fps: Float): Long = (frame / fps * 1_000_000.0).roundToLong()
    fun snapToFrame(us: Long, fps: Float): Long = frameToUs(usToFrame(us, fps), fps)
}

object TimeMath {
    fun formatTimecode(us: Long, showMillis: Boolean = true): String {
        val totalMs = us / 1000
        val h = totalMs / 3_600_000
        val m = (totalMs / 60_000) % 60
        val s = (totalMs / 1000) % 60
        val ms = totalMs % 1000
        val base = if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                   else String.format(Locale.US, "%02d:%02d", m, s)
        return if (showMillis) "$base.${String.format(Locale.US, "%03d", ms)}" else base
    }
}
