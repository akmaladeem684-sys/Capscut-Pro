package com.ahstudio.editor.timeline.ui.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.ahstudio.editor.timeline.ui.Hit
import com.ahstudio.editor.timeline.ui.TimelineMetrics
import com.ahstudio.editor.timeline.ui.TimelineUiController
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import kotlin.math.roundToLong

private sealed class Mode {
    data class Undecided(val hit: Hit) : Mode()
    object Scrub : Mode()
    data class Trim(val clipId: String, val side: Hit.Side, val downX: Float) : Mode()
    data class ClipDrag(val primaryId: String, val downX: Float, val downY: Float) : Mode()
    data class Scroll(
        val gestureStartTimeMicros: Long,
        val gestureStartScrollX: Float,
        val gestureStartScrollY: Float,
        val downX: Float,
        val downY: Float,
    ) : Mode()
    data class Zoom(val anchorMicros: Long, val startZoom: Float) : Mode()
}

/**
 * ONE deterministic gesture pipeline for the whole timeline surface.
 * Disambiguation rules:
 *  - ruler            → scrub
 *  - selected-clip edge → trim
 *  - clip body        → clip drag (long-press adds to multi-selection first)
 *  - empty/header zone → pan scroll (x & y) + fling
 *  - 2 pointers       → pinch zoom anchored at the Playhead
 * Any DOWN pauses playback immediately (§7).
 */
suspend fun PointerInputScope.timelineGestures(
    ctrl: TimelineUiController,
    metrics: TimelineMetrics,
    haptics: HapticFeedback,
) = coroutineScope {
    val slop = viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        ctrl.onTimelineTouchBegan()                       // §7: immediate pause
        val downPos = down.position
        val hit = ctrl.hitTest(downPos.x, downPos.y, metrics)
        var mode: Mode = Mode.Undecided(hit)
        var longPressFired = false
        var moved = false
        var extraAutoPx = 0f
        val tracker = VelocityTracker()
        var lastPos = downPos

        val lpJob: Job? = if (hit is Hit.ClipBody) {
            launch {
                delay(longPressMs)
                longPressFired = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                ctrl.onLongPressClip(hit.clipId)
            }
        } else null

        try {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Main)
                val main = ev.changes.firstOrNull() ?: break
                val pos = main.position

                // --- pinch takes over (unless actively trimming: finish drag first) ---
                if (ev.changes.size >= 2 && mode !is Mode.Zoom && mode !is Mode.Trim) {
                    lpJob?.cancel()
                    if (mode is Mode.ClipDrag) ctrl.cancelClipDrag()
                    val zoom0 = ctrl.viewport.pxPerSecond()
                    val anchor = ctrl.playheadMicros
                    mode = Mode.Zoom(anchor, zoom0)
                }

                when (val m = mode) {
                    is Mode.Undecided -> {
                        val dx = pos.x - downPos.x; val dy = pos.y - downPos.y
                        if (dx * dx + dy * dy > slop * slop) {
                            lpJob?.cancel(); moved = true
                            mode = when (hit) {
                                is Hit.Ruler -> Mode.Scrub
                                is Hit.ClipHandle -> {
                                    ctrl.beginTrim(hit.clipId, hit.side)
                                    Mode.Trim(hit.clipId, hit.side, downPos.x)
                                }
                                is Hit.ClipBody -> {
                                    if (hit.isBaseMedia && !longPressFired) {
                                        // Base media is not accidentally dragged by normal touch interaction!
                                        // Normal swipe on base media navigates the timeline!
                                        Mode.Scroll(
                                            gestureStartTimeMicros = ctrl.playheadMicros,
                                            gestureStartScrollX = ctrl.scrollX,
                                            gestureStartScrollY = ctrl.scrollY,
                                            downX = downPos.x,
                                            downY = downPos.y,
                                        )
                                    } else {
                                        // Editable overlay clip or intentionally long-pressed base media
                                        ctrl.beginClipDrag(hit.clipId)
                                        Mode.ClipDrag(hit.clipId, downPos.x, downPos.y)
                                    }
                                }
                                else -> Mode.Scroll(
                                    gestureStartTimeMicros = ctrl.playheadMicros,
                                    gestureStartScrollX = ctrl.scrollX,
                                    gestureStartScrollY = ctrl.scrollY,
                                    downX = downPos.x,
                                    downY = downPos.y,
                                )
                            }
                        }
                    }
                    is Mode.Scrub -> {
                        ctrl.edgeAutoScroll(pos.x, ctrl.viewport.viewportWidthPx)
                        ctrl.scrubTo(ctrl.timeUnderPointer(pos.x))
                    }
                    is Mode.Trim -> {
                        ctrl.edgeAutoScroll(pos.x, ctrl.viewport.viewportWidthPx)
                        val t = ctrl.timeUnderPointer(pos.x)
                        ctrl.updateTrim(m.clipId, m.side, t, metrics.snapPx)
                    }
                    is Mode.ClipDrag -> {
                        val auto = ctrl.edgeAutoScroll(pos.x, ctrl.viewport.viewportWidthPx)
                        extraAutoPx += auto
                        val deltaPx = (pos.x - m.downX) + extraAutoPx
                        val deltaMicros = (deltaPx / ctrl.viewport.pxPerMicro).toLong()
                        val rawShift = ((pos.y - m.downY) / metrics.rowHeightPx).toInt()
                        ctrl.updateClipDrag(deltaMicros, rawShift, snapEnabled = true, metrics.snapPx)
                    }
                    is Mode.Scroll -> {
                        val totalDeltaPx = (pos.x - m.downX).toDouble()
                        // Moving left (swiping left, deltaPx < 0) advances timeline forward (0s -> 6s).
                        // Moving right (swiping right, deltaPx > 0) rewinds timeline backwards (6s -> 3s).
                        val deltaMicros = (-totalDeltaPx / ctrl.viewport.pxPerMicro).roundToLong()
                        val targetMicros = (m.gestureStartTimeMicros + deltaMicros).coerceAtLeast(0L)

                        val dy = pos.y - m.downY
                        ctrl.setScrollYRaw(m.gestureStartScrollY - dy)

                        ctrl.scrollToTime(targetMicros)
                        ctrl.clock.seekTo(targetMicros)
                        ctrl.playback.requestScrubSeek(targetMicros)
                        ctrl.playheadMicros = targetMicros
                    }
                    is Mode.Zoom -> {
                        val z = ev.calculateZoom()
                        if (z != 1f && !z.isNaN()) {
                            val newPps = (m.startZoom * z).coerceIn(
                                com.ahstudio.editor.timeline.viewport.TimelineViewport.MIN_PX_PER_SECOND,
                                com.ahstudio.editor.timeline.viewport.TimelineViewport.MAX_PX_PER_SECOND)
                            ctrl.viewport.setZoomAroundTime(m.anchorMicros, newPps / m.startZoom)
                            ctrl.scrollToTime(m.anchorMicros)
                        }
                    }
                }
                if (mode !is Mode.Undecided || ev.changes.size >= 2) {
                    ev.changes.forEach { it.consume() }
                }
                tracker.addPosition(ev.changes.first().uptimeMillis, ev.changes.first().position)
                lastPos = pos
                if (ev.changes.none { it.pressed }) break
            }
        } finally { lpJob?.cancel() }

        // ---------------- finalize (P4 fix applied) ----------------
        val v = tracker.calculateVelocity()
        when (val m = mode) {
            is Mode.Undecided -> if (!longPressFired) onTap(ctrl, hit, downPos)
            is Mode.Scrub -> ctrl.scrubEnded()
            is Mode.Trim -> ctrl.commitTrim()
            is Mode.ClipDrag -> if (moved) ctrl.commitClipDrag() else onTap(ctrl, hit, downPos)
            is Mode.Scroll -> {
                if (!moved) onTap(ctrl, hit, downPos) else ctrl.fling(v.x, -v.y)
            }
            is Mode.Zoom -> Unit
        }
    }
}

private fun onTap(ctrl: TimelineUiController, hit: Hit, pos: androidx.compose.ui.geometry.Offset) {
    when (hit) {
        is Hit.Ruler -> ctrl.tapSeek(hit.timeMicros)          // §8 tap-to-seek
        is Hit.ClipBody -> ctrl.onTapClip(hit.clipId)
        is Hit.ClipHandle -> ctrl.onTapClip(hit.clipId)
        Hit.Empty, Hit.None -> {
            val t = ctrl.timeUnderPointer(pos.x)
            ctrl.tapSeek(t)
        }
    }
}
