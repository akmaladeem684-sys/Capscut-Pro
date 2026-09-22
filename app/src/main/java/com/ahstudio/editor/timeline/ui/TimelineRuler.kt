package com.ahstudio.editor.timeline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahstudio.editor.timeline.core.TimeFormatter
import com.ahstudio.editor.timeline.core.TimelineTime
import kotlin.math.roundToLong

@Composable
fun TimelineRuler(ctrl: TimelineUiController, m: TimelineMetrics) {
    val measurer = rememberTextMeasurer()
    val snap = ctrl.snapshot

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(TimelineTokens.RulerBg)

        val pps = ctrl.viewport.pxPerSecond()
        val minPx = 72f
        val steps = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 300.0, 600.0, 1800.0, 3600.0)
        val stepSec = steps.firstOrNull { it * pps >= minPx } ?: 3600.0
        val stepMicros = (stepSec * 1_000_000).roundToLong()
        val subMicros = if (stepMicros >= 1_000_000L) stepMicros / 2L else 0L

        val scroll = ctrl.scrollX
        val fromT = ctrl.viewport.timeAtContentPx(scroll)
        val toT = ctrl.viewport.timeAtContentPx(scroll + w)
        val first = (fromT / stepMicros) * stepMicros

        var t = first
        while (t <= toT + stepMicros) {
            val x = ctrl.viewport.contentPxAtTime(t) - scroll
            if (x >= -40f && x <= w + 40f) {
                // Secondary time label
                val label = TimeFormatter.clock(TimelineTime(t), ctrl.fps, withFrames = false)
                val tr = measurer.measure(
                    label,
                    TextStyle(
                        fontSize = 9.5.sp,
                        color = TimelineTokens.TextDim,
                        fontWeight = FontWeight.Medium
                    )
                )
                val tx = x - tr.size.width / 2f
                if (tx + tr.size.width >= 0f && tx <= w) {
                    drawText(textLayoutResult = tr, topLeft = Offset(tx, (h - tr.size.height) / 2f))
                }

                // Dot separator between time marks
                if (subMicros > 0) {
                    val st = t + subMicros
                    val sx = ctrl.viewport.contentPxAtTime(st) - scroll
                    if (sx >= 0f && sx <= w) {
                        drawCircle(
                            color = TimelineTokens.TextDim.copy(alpha = 0.5f),
                            radius = 2.dp.toPx(),
                            center = Offset(sx, h / 2f)
                        )
                    }
                }
            }
            t += stepMicros
        }

        // Markers
        for (mk in snap.markers) {
            val x = ctrl.viewport.contentPxAtTime(mk.timeMicros) - scroll
            if (x in -20f..w + 20f) {
                val p = Path().apply {
                    moveTo(x, h * 0.2f); lineTo(x - 4f, h * 0.5f); lineTo(x, h * 0.8f); lineTo(x + 4f, h * 0.5f); close()
                }
                drawPath(p, Color(0xFF7FD1FF))
            }
        }

        // Master CTI playhead notch
        val px = ctrl.viewport.playheadXPx
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(px - 1.25f * density, 0f),
            size = Size(2.5f * density, h),
            cornerRadius = CornerRadius(0f)
        )
    }
}
