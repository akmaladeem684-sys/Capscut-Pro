package com.ahstudio.editor.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahstudio.editor.timeline.core.TimeFormatter
import com.ahstudio.editor.timeline.core.TimelineTime
import com.ahstudio.editor.timeline.ui.gestures.timelineGestures
import androidx.compose.runtime.withFrameNanos

@Composable
fun AhTimelineEditor(ctrl: TimelineUiController, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val metrics = TimelineMetrics(
        mainRowHeightPx = with(density) { TimelineTokens.MainRowHeight.toPx() },
        subRowHeightPx = with(density) { TimelineTokens.SubRowHeight.toPx() },
        mainToSubGapPx = with(density) { TimelineTokens.MainToSubGap.toPx() },
        subTrackGapPx = with(density) { TimelineTokens.SubTrackGap.toPx() },
        rulerHeightPx = with(density) { TimelineTokens.RulerHeight.toPx() },
        headerWidthPx = with(density) { TimelineTokens.HeaderWidth.toPx() },
        handlePx = with(density) { TimelineTokens.TrimHandle.toPx() },
        snapPx = with(density) { TimelineTokens.SnapHit.toPx() },
        edgeMarginPx = with(density) { TimelineTokens.EdgeMargin.toPx() },
        density = density,
    )
    LaunchedEffect(Unit) { ctrl.densityScale = density.density }
    LaunchedEffect(ctrl.isPlaying) { while (ctrl.isPlaying) { ctrl.followPlayhead(); withFrameNanos { } } }

    Column(
        modifier
            .fillMaxWidth()
            .background(TimelineTokens.PanelBg)
    ) {
        // Full-Width Content Viewport (Ruler + Tracks with dynamic scrolling headers)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val viewportWidth = constraints.maxWidth.toFloat()
            LaunchedEffect(viewportWidth) { ctrl.onViewportWidthChanged(viewportWidth) }

            // ONE gesture pipeline — full content viewport (ruler + tracks)
            Column(
                Modifier
                    .fillMaxSize()
                    .pointerInput(ctrl, metrics) { timelineGestures(ctrl, metrics, haptics) }
            ) {
                Box(Modifier.fillMaxWidth().height(TimelineTokens.RulerHeight)) {
                    TimelineRuler(ctrl, metrics)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { ctrl.tracksAreaHeightPx = it.height.toFloat() }
                ) {
                    TracksArea(ctrl, metrics)
                    SnapLinesOverlay(ctrl, metrics)
                    PlayheadLine(ctrl, metrics)   // Master authoritative CTI needle
                    ReorderIndicator(ctrl, metrics)
                }
            }
        }

        ctrl.flashMessage?.let { msg ->
            LaunchedEffect(msg) { kotlinx.coroutines.delay(1600); ctrl.flashMessage = null }
            Text(
                msg, color = TimelineTokens.TextDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
    }
}
