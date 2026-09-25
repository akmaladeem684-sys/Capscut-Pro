package com.ahstudio.editor.timeline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ahstudio.editor.timeline.core.Clip
import com.ahstudio.editor.timeline.core.ClipKind
import com.ahstudio.editor.timeline.core.Track
import com.ahstudio.editor.timeline.core.TrackKind
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun TracksArea(ctrl: TimelineUiController, m: TimelineMetrics) {
    val tracks = ctrl.snapshot.tracks
    val dragged = ctrl.dragPreview
    val shift = dragged?.trackShift ?: 0

    Column(Modifier.fillMaxSize().clipToBounds()) {
        // 1. STICKY MAIN TRACK (Row 0 - Fixed at top)
        if (tracks.isNotEmpty()) {
            TrackRow(ctrl, 0, tracks, dragged, shift, m)
            if (tracks.size > 1) {
                Spacer(Modifier.height(TimelineTokens.MainToSubGap))
            }
        }

        // 2. SCROLLABLE SUB-TRACKS VIEWPORT (Row 1..N-1)
        if (tracks.size > 1) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .onSizeChanged { ctrl.subTracksViewportHeightPx = it.height.toFloat() }
            ) {
                Column(Modifier.graphicsLayer { translationY = -ctrl.scrollY }) {
                    for (row in 1 until tracks.size) {
                        if (row > 1) {
                            Spacer(Modifier.height(TimelineTokens.SubTrackGap))
                        }
                        TrackRow(ctrl, row, tracks, dragged, shift, m)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    ctrl: TimelineUiController,
    row: Int,
    tracks: List<Track>,
    dragged: DragPreview?,
    shift: Int,
    m: TimelineMetrics,
) {
    val density = LocalDensity.current
    val rowHeightPx = m.rowHeightPx(row)
    val rowDp = with(density) { rowHeightPx.toDp() }
    val fromT = ctrl.viewport.timeAtContentPx(ctrl.scrollX - 400f)
    val toT = ctrl.viewport.timeAtContentPx(ctrl.scrollX + ctrl.viewport.viewportWidthPx + 400f)
    val dragIds = dragged?.clipIds ?: emptySet()
    val track = tracks[row]

    val static = ctrl.engine.indexes.clipsByTrack[track.id].orEmpty()
        .filter { it.id !in dragIds && it.endMicros > fromT && it.startMicros < toT }
    val draggedHere = dragged?.originRow?.entries
        ?.filter { it.value + shift == row }
        ?.mapNotNull { ctrl.engine.clip(it.key) }
        .orEmpty()

    val allClips = (static + draggedHere).sortedBy { it.startMicros }

    Box(
        Modifier
            .fillMaxWidth()
            .height(rowDp)
            .background(if (row % 2 == 0) TimelineTokens.TrackBg else TimelineTokens.TrackBgAlt)
    ) {
        // Continuous lane bottom divider line
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .background(TimelineTokens.TrackLaneDivider)
        )

        // Floating Track Header item on the left (scrolling synchronously with track content before t=0)
        val headWidth = 98.dp
        val headWidthPx = with(density) { headWidth.toPx() }
        val headGapPx = with(density) { 6.dp.toPx() }
        val headContentX = -headWidthPx - headGapPx
        val headStartPx = headContentX - ctrl.scrollX

        if (headStartPx + headWidthPx > -100f && headStartPx < ctrl.viewport.viewportWidthPx + 100f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(headStartPx.roundToInt(), 0) }
                    .width(headWidth)
                    .height(rowDp)
                    .padding(vertical = 2.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (row == 0 && track.kind == TrackKind.VIDEO) {
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

        allClips.forEachIndexed { idx, clip ->
            ClipBox(ctrl, clip, track, m, rowHeightPx)

            // For primary video track, render transition marker [ | ] between adjacent clips
            if (track.kind == TrackKind.VIDEO && idx < allClips.size - 1) {
                val nextClip = allClips[idx + 1]
                if (nextClip.startMicros <= clip.endMicros + 200_000L) {
                    val splitPx = ctrl.viewport.contentPxAtTime(clip.endMicros) - ctrl.scrollX
                    TransitionButton(
                        modifier = Modifier
                            .offset { IntOffset((splitPx - 10.dp.toPx()).roundToInt(), (rowHeightPx / 2f - 11.dp.toPx()).roundToInt()) }
                    )
                }
            }
        }

        // Add Clip button [+] attached to the end of the primary video track
        if (track.kind == TrackKind.VIDEO) {
            val lastEndMicros = allClips.maxOfOrNull { it.endMicros } ?: 0L
            val addBtnGapPx = with(density) { 16.dp.toPx() }
            val addBtnContentX = ctrl.viewport.contentPxAtTime(lastEndMicros) + addBtnGapPx
            val addBtnPx = addBtnContentX - ctrl.scrollX
            if (addBtnPx in -60f..(ctrl.viewport.viewportWidthPx + 60f)) {
                AddClipButton(
                    modifier = Modifier
                        .offset { IntOffset(addBtnPx.roundToInt(), (rowHeightPx / 2f - 14.dp.toPx()).roundToInt()) }
                ) {
                    ctrl.onAddMediaToTrack(track.id)
                }
            }
        }
    }
}

@Composable
fun ClipBox(
    ctrl: TimelineUiController,
    clip: Clip,
    track: Track,
    m: TimelineMetrics,
    rowHeightPx: Float
) {
    val shown = ctrl.effectiveTrim(clip) ?: clip
    val startT = ctrl.effectiveClipStart(shown)
    val endT = startT + shown.durationMicros

    val x0 = ctrl.viewport.contentPxAtTime(startT) - ctrl.scrollX
    val x1 = ctrl.viewport.contentPxAtTime(endT) - ctrl.scrollX
    val widthPx = max(4f, x1 - x0)

    val selected = clip.id in ctrl.selection
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .offset { IntOffset(x0.roundToInt(), 0) }
            .width(with(density) { widthPx.toDp() })
            .height(with(density) { rowHeightPx.toDp() })
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        when (shown.kind) {
            ClipKind.VIDEO -> VideoFilmstripClipView(ctrl, shown, track)
            ClipKind.AUDIO -> AudioWaveformClipView(ctrl, shown, track)
            ClipKind.TEXT -> TextClipView(ctrl, shown, track)
            ClipKind.STICKER -> StickerClipView(ctrl, shown, track)
            ClipKind.EFFECT, ClipKind.ADJUSTMENT -> EffectClipView(ctrl, shown, track)
        }

        // Selection boundary & grab handles
        if (selected) {
            Canvas(Modifier.fillMaxSize()) {
                val strokeW = 2.dp.toPx()
                val handleW = 6.dp.toPx()
                val handleH = size.height * 0.72f
                val handleY = (size.height - handleH) / 2f

                // Outer selection border
                drawRoundRect(
                    color = Color.White,
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = strokeW)
                )

                // Left grab handle
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(0f, handleY),
                    size = Size(handleW, handleH),
                    cornerRadius = CornerRadius(2.5.dp.toPx())
                )
                drawLine(
                    color = Color(0xFF1E242B),
                    start = Offset(handleW / 2f, handleY + handleH * 0.25f),
                    end = Offset(handleW / 2f, handleY + handleH * 0.75f),
                    strokeWidth = 1.2.dp.toPx()
                )

                // Right grab handle
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(size.width - handleW, handleY),
                    size = Size(handleW, handleH),
                    cornerRadius = CornerRadius(2.5.dp.toPx())
                )
                drawLine(
                    color = Color(0xFF1E242B),
                    start = Offset(size.width - handleW / 2f, handleY + handleH * 0.25f),
                    end = Offset(size.width - handleW / 2f, handleY + handleH * 0.75f),
                    strokeWidth = 1.2.dp.toPx()
                )
            }
        }
    }
}

// ---------------- Primary Video Track Header (Mute Button & Cover Thumbnail) ----------------
@Composable
fun PrimaryVideoHeaderRow(
    track: Track,
    onToggleMute: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Mute Clip Audio Pill Button
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF202227))
                .clickable { onToggleMute() }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (track.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (track.muted) "Unmute clip" else "Mute clip",
                    tint = if (track.muted) Color(0xFFFF5252) else Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = if (track.muted) "Muted" else "Mute clip",
                    color = Color.White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Cover Thumbnail Card
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF202227)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CropPortrait,
                    contentDescription = "Cover",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Cover",
                    color = Color.White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------- Secondary Sub-Tracks Dynamic Header Tile ----------------
@Composable
fun SecondaryTrackIconTile(track: Track) {
    val (iconVector, labelText, iconColor) = when (track.kind) {
        TrackKind.OVERLAY -> Triple(Icons.Default.Layers, "Overlay", Color(0xFF66BB6A))
        TrackKind.AUDIO, TrackKind.VOICE, TrackKind.MUSIC, TrackKind.SFX -> Triple(Icons.Default.MusicNote, "Audio", Color(0xFF26C6DA))
        TrackKind.TEXT, TrackKind.CAPTION -> Triple(Icons.Default.Title, "Text", Color(0xFFFFA726))
        TrackKind.STICKER -> Triple(Icons.Default.AutoAwesome, "Sticker", Color(0xFFAB47BC))
        TrackKind.EFFECT -> Triple(Icons.Default.AutoFixHigh, "Effect", Color(0xFF7E57C2))
        else -> Triple(Icons.Default.Audiotrack, track.name, Color.White)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF202227))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = labelText,
                tint = iconColor,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = labelText,
            color = Color.White,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------- Add Clip Button ----------------
@Composable
fun AddClipButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(0xFFFFFFFF))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Media",
            tint = Color(0xFF141519),
            modifier = Modifier.size(18.dp)
        )
    }
}

// ---------------- Transition Cut Button ----------------
@Composable
fun TransitionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .size(20.dp, 22.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(TimelineTokens.TransitionBadgeBg)
            .border(1.dp, Color(0xFF3B404E), RoundedCornerShape(3.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(10.dp)
                .background(Color.White)
        )
    }
}

// ---------------- Video Filmstrip Clip Composable ----------------
@Composable
fun VideoFilmstripClipView(ctrl: TimelineUiController, clip: Clip, track: Track) {
    val context = LocalContext.current
    val uri = clip.mediaUri

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141923))
    ) {
        if (!uri.isNullOrBlank()) {
            Row(modifier = Modifier.fillMaxSize()) {
                val thumbCount = max(1, (clip.durationMicros / 2_000_000L).toInt().coerceAtMost(10))
                for (i in 0 until thumbCount) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        } else {
            // Cyber fallback background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF161B26), Color(0xFF1D2638))
                        )
                    )
            )
        }

        // Clip label
        Text(
            text = clip.label,
            color = Color.White,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ---------------- Audio Waveform Clip Composable ----------------
@Composable
fun AudioWaveformClipView(ctrl: TimelineUiController, clip: Clip, track: Track) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TimelineTokens.AudioColor)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = clip.label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------- Text Clip Composable ----------------
@Composable
fun TextClipView(ctrl: TimelineUiController, clip: Clip, track: Track) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TimelineTokens.TextColor)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Title,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = clip.label.ifBlank { "Text" },
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------- Sticker Clip Composable ----------------
@Composable
fun StickerClipView(ctrl: TimelineUiController, clip: Clip, track: Track) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2FA36B))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = clip.label.ifBlank { "Sticker" },
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------- Effect Clip Composable ----------------
@Composable
fun EffectClipView(ctrl: TimelineUiController, clip: Clip, track: Track) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF7A4FC9))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = clip.label.ifBlank { "Effect" },
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
