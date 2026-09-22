package com.ahstudio.editor.timeline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.ahstudio.editor.timeline.core.TimeFormatter
import com.ahstudio.editor.timeline.core.TimelineTime
import kotlin.math.roundToLong

@Composable
fun TimelineRuler(ctrl: TimelineUiController, m: TimelineMetrics) {
    val measurer = rememberTextMeasurer()
    val snap = ctrl.snapshot

    // Ruler rendering (gestures handled deterministically by timelineGestures)
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(TimelineTokens.RulerBg)

        val pps = ctrl.viewport.pxPerSecond()
        val minPx = 64f
        val steps = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 300.0, 600.0, 1800.0, 3600.0)
        val stepSec = steps.firstOrNull { it * pps >= minPx } ?: 3600.0
        val stepMicros = (stepSec * 1_000_000).roundToLong()
        val subMicros = if ((stepMicros / 5.0) * ctrl.viewport.pxPerMicro >= 12f) stepMicros / 5L else 0L
        val frameMicros = TimelineTime.frameDurationMicros(ctrl.fps)
        val showFrames = frameMicros * ctrl.viewport.pxPerMicro >= 10f

        val scroll = ctrl.scrollX
        val fromT = ctrl.viewport.timeAtContentPx(scroll)
        val toT = ctrl.viewport.timeAtContentPx(scroll + w)
        val first = (fromT / stepMicros) * stepMicros

        var t = first
        while (t <= toT + stepMicros) {
            val x = ctrl.viewport.contentPxAtTime(t) - scroll
            if (x >= -40f && x <= w + 40f) {
                drawLine(TimelineTokens.TextDim, Offset(x, h * 0.45f), Offset(x, h), 1.2f)
                val label = TimeFormatter.clock(TimelineTime(t), ctrl.fps, withFrames = showFrames && stepSec <= 5.0)
                val tr = measurer.measure(label, TextStyle(fontSize = 9.sp, color = TimelineTokens.TextPrimary))
                val tx = x + 6f
                if (tx >= 0f && tx + tr.size.width <= w) {
                    drawText(textLayoutResult = tr, topLeft = Offset(tx, 2f))
                }
                if (subMicros > 0) {
                    var st = t + subMicros
                    while (st < t + stepMicros) {
                        val sx = ctrl.viewport.contentPxAtTime(st) - scroll
                        if (sx >= 0f && sx <= w) {
                            drawLine(TimelineTokens.TextDim.copy(alpha = 0.4f), Offset(sx, h * 0.75f), Offset(sx, h), 1f)
                        }
                        st += subMicros
                    }
                }
            }
            t += stepMicros
        }

        // Markers (§5)
        for (mk in snap.markers) {
            val x = ctrl.viewport.contentPxAtTime(mk.timeMicros) - scroll
            if (x in -20f..w + 20f) {
                val p = Path().apply {
                    moveTo(x, h * 0.35f); lineTo(x - 5f, h * 0.55f); lineTo(x, h * 0.75f); lineTo(x + 5f, h * 0.55f); close()
                }
                drawPath(p, Color(0xFF7FD1FF))
            }
        }

        // ---- Master CTI head + time bubble (fixed at 10%) ----
        val px = ctrl.viewport.playheadXPx
        drawLine(TimelineTokens.Playhead, Offset(px, h * 0.55f), Offset(px, h), 2f)
        val head = Path().apply {
            moveTo(px - 8f, h * 0.1f); lineTo(px + 8f, h * 0.1f); lineTo(px, h * 0.55f); close()
        }
        drawPath(head, TimelineTokens.Playhead)
        val label = TimeFormatter.clock(TimelineTime(ctrl.playheadMicros), ctrl.fps)
        val tr = measurer.measure(label, TextStyle(fontSize = 10.sp, color = Color(0xFF10131A)))
        val bubbleWidth = tr.size.width + 10f
        val maxBx = (w - bubbleWidth - 2f).coerceAtLeast(2f)
        val bx = (px - tr.size.width / 2f).coerceIn(2f, maxBx)
        drawRoundRect(
            color = TimelineTokens.Playhead,
            topLeft = Offset(bx, 1f),
            size = androidx.compose.ui.geometry.Size(bubbleWidth, tr.size.height + 4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )
        drawText(textLayoutResult = tr, topLeft = Offset(bx + 5f, 3f))
    }
}
