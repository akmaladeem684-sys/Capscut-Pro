package com.ahstudio.editor.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.ahstudio.editor.timeline.ui.gestures.timelineGestures
import androidx.compose.runtime.withFrameNanos

@Composable
fun AhTimelineEditor(ctrl: TimelineUiController, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val metrics = TimelineMetrics(
        rowHeightPx = with(density) { TimelineTokens.RowHeight.toPx() },
        rulerHeightPx = with(density) { TimelineTokens.RulerHeight.toPx() },
        headerWidthPx = with(density) { TimelineTokens.HeaderWidth.toPx() },
        handlePx = with(density) { TimelineTokens.TrimHandle.toPx() },
        snapPx = with(density) { TimelineTokens.SnapHit.toPx() },
        edgeMarginPx = with(density) { TimelineTokens.EdgeMargin.toPx() },
        density,
    )
    LaunchedEffect(Unit) { ctrl.densityScale = density.density }
    LaunchedEffect(ctrl.isPlaying) { while (ctrl.isPlaying) { ctrl.followPlayhead(); withFrameNanos { } } }

    Column(modifier.fillMaxWidth().height(340.dp).background(TimelineTokens.PanelBg)) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.width(TimelineTokens.HeaderWidth).fillMaxHeight()) {
                Spacer(Modifier.height(TimelineTokens.RulerHeight)) // corner above headers
                TrackHeaders(ctrl, metrics, Modifier.fillMaxWidth().weight(1f))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val viewportWidth = constraints.maxWidth.toFloat()
                LaunchedEffect(viewportWidth) { ctrl.viewport.viewportWidthPx = viewportWidth }
                // §12: ONE gesture pipeline — content viewport only (ruler + tracks)
                Column(Modifier.fillMaxSize()
                    .pointerInput(ctrl, metrics) { timelineGestures(ctrl, metrics, haptics) }) {
                    Box(Modifier.fillMaxWidth().height(TimelineTokens.RulerHeight)) { TimelineRuler(ctrl, metrics) }
                    Box(Modifier.fillMaxSize().onSizeChanged { ctrl.tracksAreaHeightPx = it.height.toFloat() }) {
                        TracksArea(ctrl, metrics)
                        SnapLinesOverlay(ctrl, metrics)
                        PlayheadLine(ctrl, metrics)   // §2: the ONE Master CTI
                        ReorderIndicator(ctrl, metrics)
                    }
                }
            }
        }
        TimelineToolbar(ctrl)
        ctrl.flashMessage?.let { msg ->
            LaunchedEffect(msg) { kotlinx.coroutines.delay(1600); ctrl.flashMessage = null }
            androidx.compose.material3.Text(
                msg, color = TimelineTokens.TextDim,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
        }
    }
}
