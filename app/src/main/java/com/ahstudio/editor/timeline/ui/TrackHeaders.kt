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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahstudio.editor.timeline.core.Track
import com.ahstudio.editor.timeline.core.TrackKind
import com.ahstudio.editor.timeline.core.isAudioLike

@Composable
fun TrackHeaders(ctrl: TimelineUiController, m: TimelineMetrics, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val rowDp = with(LocalDensity.current) { m.rowHeightPx.toDp() }

    Box(modifier.background(TimelineTokens.HeaderBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -ctrl.scrollY }
        ) {
            ctrl.snapshot.tracks.forEachIndexed { index, track ->
                val isPrimaryVideo = index == 0 && track.kind == TrackKind.VIDEO
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
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    accY = 0f
                                },
                                onDrag = { change, amt ->
                                    accY += amt.y
                                    ctrl.updateTrackReorder(
                                        index * m.rowHeightPx + m.rowHeightPx / 2f + accY + ctrl.scrollY,
                                        m.rowHeightPx
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
        }
    }
}

@Composable
fun PrimaryVideoHeaderRow(
    track: Track,
    onToggleMute: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Mute Clip Button
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .clickable { onToggleMute() }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (track.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = "Mute clip",
                tint = if (track.muted) Color(0xFFEF4444) else TimelineTokens.TextPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Mute clip",
                color = TimelineTokens.TextDim,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                lineHeight = 10.sp
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 2. Cover Thumbnail Card
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1E2838),
                            Color(0xFF0F1520)
                        )
                    )
                )
                .clickable { /* Cover selection action */ },
            contentAlignment = Alignment.Center
        ) {
            // Edit Pencil Overlay Badge
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Cover",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }

            // Cover Text Label
            Text(
                text = "Cover",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
fun SecondaryTrackIconTile(track: Track) {
    val icon: ImageVector = when (track.kind) {
        TrackKind.OVERLAY -> Icons.Default.Layers
        TrackKind.AUDIO, TrackKind.MUSIC, TrackKind.VOICE, TrackKind.SFX -> Icons.Default.Audiotrack
        TrackKind.TEXT, TrackKind.CAPTION -> Icons.Default.Title
        TrackKind.STICKER -> Icons.Default.AutoAwesome
        TrackKind.EFFECT -> Icons.Default.AutoAwesome
        else -> Icons.Default.Layers
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF202227)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = track.name,
            tint = TimelineTokens.TextDim,
            modifier = Modifier.size(18.dp)
        )
    }
}
