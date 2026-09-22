package com.ahstudio.editor.timeline.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahstudio.editor.timeline.core.Clip
import com.ahstudio.editor.timeline.core.ClipKind
import com.ahstudio.editor.timeline.core.Track
import com.ahstudio.editor.timeline.core.TrackKind
import com.ahstudio.editor.timeline.core.isAudioLike
import com.example.engine.media.VideoThumbnailManager
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TracksArea(ctrl: TimelineUiController, m: TimelineMetrics) {
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Column(Modifier.graphicsLayer { translationY = -ctrl.scrollY }) {
            val tracks = ctrl.snapshot.tracks
            val dragged = ctrl.dragPreview
            val shift = dragged?.trackShift ?: 0
            tracks.indices.forEach { row -> TrackRow(ctrl, row, tracks, dragged, shift, m) }
        }
    }
}

@Composable
private fun TrackRow(
    ctrl: TimelineUiController, row: Int, tracks: List<Track>,
    dragged: DragPreview?, shift: Int, m: TimelineMetrics,
) {
    val rowDp = with(LocalDensity.current) { m.rowHeightPx.toDp() }
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
        // Continuous lane bottom boundary divider line
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .background(TimelineTokens.TrackLaneDivider)
        )

        // Floating/attached Track Head item at start of track (before time 0)
        // Moves dynamically along with the track as video plays or user scrubs!
        val headWidth = 98.dp
        val headWidthPx = with(LocalDensity.current) { headWidth.toPx() }
        val headStartPx = ctrl.viewport.contentPxAtTime(0L) - headWidthPx - 6.dp.value - ctrl.scrollX

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
            ClipBox(ctrl, clip, track, m)

            // For primary video track, render transition marker [ | ] between adjacent clips
            if (track.kind == TrackKind.VIDEO && idx < allClips.size - 1) {
                val nextClip = allClips[idx + 1]
                if (nextClip.startMicros <= clip.endMicros + 200_000L) {
                    val splitPx = ctrl.viewport.contentPxAtTime(clip.endMicros) - ctrl.scrollX
                    TransitionButton(
                        modifier = Modifier
                            .offset { IntOffset((splitPx - 10.dp.toPx()).roundToInt(), (m.rowHeightPx / 2f - 11.dp.toPx()).roundToInt()) }
                    )
                }
            }
        }

        // Add Clip button [+] at the end of the video track
        if (track.kind == TrackKind.VIDEO) {
            val lastEndMicros = allClips.maxOfOrNull { it.endMicros } ?: 0L
            val addBtnPx = ctrl.viewport.contentPxAtTime(lastEndMicros) - ctrl.scrollX + 16.dp.value
            AddClipButton(
                modifier = Modifier
                    .offset { IntOffset(addBtnPx.roundToInt(), (m.rowHeightPx / 2f - 13.dp.toPx()).roundToInt()) }
            ) {
                // Add media action
            }
        }
    }
}

@Composable
fun ClipBox(ctrl: TimelineUiController, clip: Clip, track: Track, m: TimelineMetrics) {
    val shown = ctrl.effectiveTrim(clip) ?: clip
    val selected = clip.id in ctrl.selection
    val dragging = ctrl.dragPreview?.clipIds?.contains(clip.id) == true
    val startPx = ctrl.viewport.contentPxAtTime(ctrl.effectiveClipStart(shown)) - ctrl.scrollX
    val widthPx = ctrl.viewport.contentPxAtTime(shown.durationMicros).coerceAtLeast(2f)
    val wI = widthPx.roundToInt().coerceAtLeast(1)

    val isVideo = track.kind == TrackKind.VIDEO || track.kind == TrackKind.OVERLAY || clip.kind == ClipKind.VIDEO
    val isAudio = track.kind.isAudioLike() || clip.kind == ClipKind.AUDIO
    val isText = track.kind == TrackKind.TEXT || clip.kind == ClipKind.TEXT || track.kind == TrackKind.CAPTION

    Box(
        Modifier
            .offset { IntOffset(startPx.roundToInt(), 0) }
            .layout { measurable, _ ->
                val p = measurable.measure(Constraints(minWidth = wI, maxWidth = wI))
                layout(wI, p.height) { p.place(0, 0) }
            }
            .fillMaxHeight()
            .padding(horizontal = 1.dp, vertical = 2.dp)
            .alpha(if (track.visible) 1f else 0.3f)
            .graphicsLayer { if (dragging) { scaleX = 1.02f; scaleY = 1.02f } }
            .clip(RoundedCornerShape(4.dp))
    ) {
        when {
            isVideo -> {
                VideoFilmstripClipView(
                    clip = shown,
                    widthPx = widthPx,
                    selected = selected,
                    m = m
                )
            }
            isAudio -> {
                AudioWaveformClipView(
                    clip = shown,
                    selected = selected,
                    ctrl = ctrl,
                    m = m
                )
            }
            isText -> {
                TextClipView(
                    clip = shown,
                    selected = selected,
                    m = m
                )
            }
            else -> {
                GenericClipView(
                    clip = shown,
                    track = track,
                    selected = selected,
                    m = m
                )
            }
        }

        // Selection border & handles
        if (selected) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = TimelineTokens.Selection,
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
                // Left grab handle
                drawRoundRect(
                    color = TimelineTokens.Selection,
                    topLeft = Offset(0f, size.height * 0.2f),
                    size = Size(4.dp.toPx(), size.height * 0.6f),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                // Right grab handle
                drawRoundRect(
                    color = TimelineTokens.Selection,
                    topLeft = Offset(size.width - 4.dp.toPx(), size.height * 0.2f),
                    size = Size(4.dp.toPx(), size.height * 0.6f),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

/**
 * Continuous video thumbnail strip for video clips.
 */
@Composable
private fun VideoFilmstripClipView(
    clip: Clip,
    widthPx: Float,
    selected: Boolean,
    m: TimelineMetrics
) {
    val context = LocalContext.current
    val tileWidthPx = 48.dp.value * m.density.density
    val tileCount = maxOf(1, ceil(widthPx / tileWidthPx).toInt())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF10141C))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            for (i in 0 until tileCount) {
                VideoThumbnailFrameTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    context = context,
                    mediaUri = clip.mediaUri,
                    frameIndex = i,
                    totalFrames = tileCount,
                    clipId = clip.id
                )
            }
        }

        // Subtle frame vertical dividers
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (tileCount > 1) {
                val step = size.width / tileCount
                for (i in 1 until tileCount) {
                    val x = i * step
                    drawLine(
                        color = Color.Black.copy(alpha = 0.5f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }

        // Clip label overlay at bottom
        if (clip.label.isNotBlank()) {
            Text(
                text = clip.label,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun VideoThumbnailFrameTile(
    modifier: Modifier,
    context: android.content.Context,
    mediaUri: String?,
    frameIndex: Int,
    totalFrames: Int,
    clipId: String
) {
    var bitmap by remember(mediaUri, frameIndex) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(mediaUri, frameIndex) {
        if (!mediaUri.isNullOrBlank()) {
            val key = VideoThumbnailManager.makeKey(mediaUri, frameIndex * 1000L, 100, 100)
            val cached = VideoThumbnailManager.getCachedThumbnail(key)
            if (cached != null) {
                bitmap = cached
            } else {
                val loaded = VideoThumbnailManager.getThumbnail(
                    context = context,
                    uri = mediaUri,
                    sourceTimeMs = frameIndex * 1000L,
                    targetWidth = 100,
                    targetHeight = 100,
                    isVideo = true
                )
                if (loaded != null && !loaded.isRecycled) {
                    bitmap = loaded
                }
            }
        }
    }

    val currentBmp = bitmap
    if (currentBmp != null && !currentBmp.isRecycled) {
        Image(
            bitmap = currentBmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        // High-tech cyber / cinematic frame thumbnail pattern (matching screenshot)
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val shift = (frameIndex.toFloat() / totalFrames.coerceAtLeast(1))

            // Cinematic room background gradient
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF0C1322),
                        Color(0xFF1B283E),
                        Color(0xFF141F32),
                        Color(0xFF080D17)
                    )
                )
            )

            // Glowing cyan/blue perspective room grid lines
            val cx = w * (0.45f + shift * 0.1f)
            val cy = h * 0.48f

            // Perspective lines
            drawLine(Color(0x334DD0E1), Offset(cx, cy), Offset(0f, 0f), 0.8f)
            drawLine(Color(0x334DD0E1), Offset(cx, cy), Offset(w, 0f), 0.8f)
            drawLine(Color(0x444DD0E1), Offset(cx, cy), Offset(0f, h), 1f)
            drawLine(Color(0x444DD0E1), Offset(cx, cy), Offset(w, h), 1f)

            // Futuristic server/monitor tech rectangles
            drawRect(
                color = Color(0x3300E5FF),
                topLeft = Offset(cx - w * 0.18f, cy - h * 0.22f),
                size = Size(w * 0.36f, h * 0.44f),
                style = Stroke(width = 0.8f)
            )

            // Neon horizontal floor glow
            drawLine(
                color = Color(0x5500B0FF),
                start = Offset(0f, h * 0.72f),
                end = Offset(w, h * 0.72f),
                strokeWidth = 1f
            )
        }
    }
}

/**
 * Audio waveform clip view with Teal background.
 */
@Composable
private fun AudioWaveformClipView(
    clip: Clip,
    selected: Boolean,
    ctrl: TimelineUiController,
    m: TimelineMetrics
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TimelineTokens.AudioColor)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        // Audio wave rendering
        Canvas(Modifier.fillMaxSize()) {
            val wf = ctrl.visualProvider?.waveformFor(clip)
            val stepX = 2.5.dp.toPx()
            val bars = (size.width / stepX).toInt().coerceAtLeast(1)
            val midY = size.height / 2f

            if (wf != null && wf.isNotEmpty()) {
                val stepWf = wf.size.toFloat() / bars
                for (i in 0 until bars) {
                    val v = wf[(i * stepWf).toInt().coerceIn(0, wf.size - 1)].coerceIn(0f, 1f)
                    val bh = (size.height * 0.65f * v).coerceAtLeast(2f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.92f),
                        start = Offset(i * stepX, midY - bh / 2f),
                        end = Offset(i * stepX, midY + bh / 2f),
                        strokeWidth = 1.2.dp.toPx()
                    )
                }
            } else {
                // Procedural crisp audio waveform
                for (i in 0 until bars) {
                    val norm = i.toFloat() / bars
                    val amp = sin(norm * 28.0f) * 0.5f + sin(norm * 8.0f) * 0.3f + 0.2f
                    val v = amp.coerceIn(0.15f, 0.95f)
                    val bh = size.height * 0.55f * v
                    drawLine(
                        color = Color.White.copy(alpha = 0.9f),
                        start = Offset(i * stepX, midY - bh / 2f),
                        end = Offset(i * stepX, midY + bh / 2f),
                        strokeWidth = 1.2.dp.toPx()
                    )
                }
            }
        }

        // Audio clip title (e.g. Voiceover 5, Energetic Tech)
        Text(
            text = clip.label.ifBlank { "Audio" },
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 2.dp, top = 1.dp)
        )
    }
}

/**
 * Text clip view with vibrant Orange background.
 */
@Composable
private fun TextClipView(
    clip: Clip,
    selected: Boolean,
    m: TimelineMetrics
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(3.dp))
            .background(TimelineTokens.TextColor)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = clip.label.ifBlank { "Enter text..." },
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Generic clip view.
 */
@Composable
private fun GenericClipView(
    clip: Clip,
    track: Track,
    selected: Boolean,
    m: TimelineMetrics
) {
    val color = clip.colorArgb?.let { Color(it) } ?: TimelineTokens.trackColor(track.kind)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(3.dp))
            .background(color)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = clip.label.ifBlank { track.name },
            color = Color.White,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Transition separator button between adjacent video clips.
 */
@Composable
private fun TransitionButton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 20.dp, height = 22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(TimelineTokens.TransitionBadgeBg)
            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(4.dp))
            .clickable { /* Transition picker action */ },
        contentAlignment = Alignment.Center
    ) {
        // Vertical transition split glyph
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(10.dp)
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(Color.White.copy(alpha = 0.5f))
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(10.dp)
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(1.dp))
            )
        }
    }
}

/**
 * Add clip button [+] at the end of the video track.
 */
@Composable
private fun AddClipButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF22252D))
            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Media",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}
