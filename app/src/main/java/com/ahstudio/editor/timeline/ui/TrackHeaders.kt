package com.ahstudio.editor.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahstudio.editor.timeline.core.Track
import com.ahstudio.editor.timeline.core.TrackKind

@Composable
fun TrackHeaders(ctrl: TimelineUiController, m: TimelineMetrics, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val tracks = ctrl.snapshot.tracks

    Box(
        modifier
            .background(TimelineTokens.HeaderBg)
            .clipToBounds()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -ctrl.scrollY }
        ) {
            tracks.forEachIndexed { index, track ->
                if (index == 1) {
                    Spacer(Modifier.height(TimelineTokens.MainToSubGap))
                } else if (index > 1) {
                    Spacer(Modifier.height(TimelineTokens.SubTrackGap))
                }
                val rowHeightPx = m.rowHeightPx(index)
                val rowDp = with(density) { rowHeightPx.toDp() }
                TrackHeaderRowItem(ctrl, index, track, rowDp, rowHeightPx, m, haptics)
            }
        }
    }
}

@Composable
private fun TrackHeaderRowItem(
    ctrl: TimelineUiController,
    index: Int,
    track: com.ahstudio.editor.timeline.core.Track,
    rowDp: Dp,
    rowHeightPx: Float,
    m: TimelineMetrics,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val isPrimaryVideo = index == 0 && track.kind == com.ahstudio.editor.timeline.core.TrackKind.VIDEO

    Box(
        Modifier
            .fillMaxWidth()
            .height(rowDp)
            .padding(vertical = 2.dp, horizontal = 4.dp)
            .pointerInput(track.id) {
                detectTapGestures(onTap = {
                    ctrl.onTimelineTouchBegan()
                    ctrl.selectTrack(track.id)
                })
            }
            .pointerInput(track.id, ctrl.snapshot.tracks.size) {
                var accY = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        ctrl.onTimelineTouchBegan()
                        ctrl.beginTrackReorder(index)
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        accY = 0f
                    },
                    onDrag = { change, amt ->
                        accY += amt.y
                        ctrl.updateTrackReorder(
                            m.trackTopPx(index) + rowHeightPx / 2f + accY + ctrl.scrollY,
                            m
                        )
                        change.consume()
                    },
                    onDragEnd = { ctrl.commitTrackReorder() },
                    onDragCancel = { ctrl.cancelTrackReorder() },
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isPrimaryVideo) {
            PrimaryVideoHeaderRow(
                track = track,
                onToggleMute = {
                    ctrl.engine.updateTrack(track.id) { it.copy(muted = !it.muted) }
                }
            )
        } else {
            SecondaryTrackIconTile(track = track)
        }
    }
}
