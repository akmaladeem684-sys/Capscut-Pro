package com.ahstudio.editor.timeline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect

/** §2 — ONE vertical CTI line at exactly viewport.playheadXPx. Every track aligns to it. */
@Composable
fun PlayheadLine(ctrl: TimelineUiController, m: TimelineMetrics) {
    Canvas(Modifier.fillMaxSize()) {
        val x = ctrl.playheadXPx
        // High-contrast clean solid white playhead
        drawLine(
            color = Color.White,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 2.5f * density
        )
    }
}

/** §10 — snap feedback lines. */
@Composable
fun SnapLinesOverlay(ctrl: TimelineUiController, m: TimelineMetrics) {
    val lines = ctrl.snapLines
    if (lines.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        for (t in lines) {
            val x = ctrl.viewport.contentPxAtTime(t) - ctrl.scrollX
            drawLine(TimelineTokens.SnapLine, Offset(x, 0f), Offset(x, size.height), 1.5f, pathEffect = dash)
        }
    }
}

/** §13 — insertion line while reordering tracks. */
@Composable
fun ReorderIndicator(ctrl: TimelineUiController, m: TimelineMetrics) {
    val rp = ctrl.reorderPreview ?: return
    Canvas(Modifier.fillMaxSize()) {
        val y = rp.insertionIndex * m.rowHeightPx - ctrl.scrollY
        drawLine(Color(0xFF7FD1FF), Offset(0f, y), Offset(size.width, y), 3f)
    }
}
