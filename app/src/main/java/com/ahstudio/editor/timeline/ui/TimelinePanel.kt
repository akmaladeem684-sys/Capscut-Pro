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

    Column(modifier.fillMaxWidth().height(260.dp).background(TimelineTokens.PanelBg)) {
        // 1. Top Bar: Master Timecode on Left + Undo/Redo on Right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val curTimeStr = TimeFormatter.clock(TimelineTime(ctrl.playheadMicros), ctrl.fps, withFrames = false)
            val durTimeStr = TimeFormatter.clock(TimelineTime(ctrl.engine.durationMicros()), ctrl.fps, withFrames = false)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = curTimeStr,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = " / $durTimeStr",
                    color = TimelineTokens.TextDim,
                    fontSize = 10.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(enabled = ctrl.canUndo) { ctrl.undo() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (ctrl.canUndo) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(enabled = ctrl.canRedo) { ctrl.redo() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (ctrl.canRedo) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 2. Full-Width Content Viewport (Ruler + Tracks with dynamic scrolling headers)
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
                    PlayheadLine(ctrl, metrics)   // Master authoritative CTI
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
