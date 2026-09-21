package com.ahstudio.editor.timeline.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahstudio.editor.timeline.clock.MasterTimelineClock
import com.ahstudio.editor.timeline.clock.SeekSource
import com.ahstudio.editor.timeline.core.TimeMath
import com.ahstudio.editor.timeline.engine.*
import com.ahstudio.editor.timeline.model.*

@Composable
fun MasterTimelineView(
    engine: TimelineEngine,
    playbackController: com.ahstudio.editor.timeline.playback.PlaybackController,
    modifier: Modifier = Modifier
) {
    val state by engine.state.collectAsState()
    val posUs by engine.clock.positionUs.collectAsState()
    val isPlaying by engine.clock.isPlaying.collectAsState()

    val zoom = state.zoomPxPerSec
    val pxPerUs = zoom / 1_000_000f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121214))
    ) {
        // Timeline Header Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E22))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { playbackController.togglePlay() }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White
                    )
                }
                Text(
                    text = TimeMath.formatTimecode(posUs),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { engine.undo() }, enabled = engine.canUndo) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (engine.canUndo) Color.White else Color.Gray)
                }
                IconButton(onClick = { engine.redo() }, enabled = engine.canRedo) {
                    Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (engine.canRedo) Color.White else Color.Gray)
                }
                IconButton(onClick = { engine.setZoom(zoom * 1.25f) }) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
                }
                IconButton(onClick = { engine.setZoom(zoom / 1.25f) }) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
                }
            }
        }

        // Ruler & Tracks Canvas Container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { offset ->
                        playbackController.onTimelineTouched()
                        val clickedUs = (offset.x / pxPerUs).toLong()
                        playbackController.seekTo(clickedUs, SeekSource.USER_SCRUB)
                    })
                }
        ) {
            val viewportWidthPx = constraints.maxWidth.toFloat()
            val playheadX = viewportWidthPx * com.ahstudio.editor.timeline.core.TimelineConstants.PLAYHEAD_VIEWPORT_FRACTION

            val scrollOffsetUs = (posUs - (playheadX / pxPerUs).toLong()).coerceAtLeast(0L)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            ) {
                // Time Ruler
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .background(Color(0xFF18181C))
                        .fillMaxWidth()
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val startUs = scrollOffsetUs
                        val endUs = startUs + (size.width / pxPerUs).toLong()
                        val stepUs = 1_000_000L
                        var t = (startUs / stepUs) * stepUs
                        while (t <= endUs) {
                            val x = ((t - scrollOffsetUs) * pxPerUs)
                            drawLine(Color.Gray, Offset(x, size.height - 12f), Offset(x, size.height), 1f)
                            drawIntoCanvas { canvas ->
                                val paint = android.graphics.Paint().apply {
                                    setColor(android.graphics.Color.LTGRAY)
                                    textSize = 24f
                                }
                                canvas.nativeCanvas.drawText(
                                    TimeMath.formatTimecode(t, false),
                                    x + 4f,
                                    size.height - 16f,
                                    paint
                                )
                            }
                            t += stepUs
                        }
                    }
                }

                // Tracks
                for (track in state.tracks) {
                    Box(
                        modifier = Modifier
                            .height(track.heightDp.dp)
                            .fillMaxWidth()
                            .background(Color(0xFF1A1A1E))
                            .border(0.5.dp, Color(0xFF2C2C32))
                    ) {
                        val clips = engine.clipsForTrack(track.id)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (clip in clips) {
                                val left = (clip.startUs - scrollOffsetUs) * pxPerUs
                                val width = clip.durationUs * pxPerUs
                                val color = when (clip.kind.family) {
                                    TrackFamily.VIDEO -> Color(0xFF3B82F6)
                                    TrackFamily.AUDIO -> Color(0xFF10B981)
                                    TrackFamily.GRAPHIC -> Color(0xFFF59E0B)
                                    TrackFamily.EFFECT -> Color(0xFF8B5CF6)
                                }
                                val isSelected = state.selectedClipIds.contains(clip.id)
                                drawRect(
                                    color = if (isSelected) color.copy(alpha = 0.9f) else color.copy(alpha = 0.7f),
                                    topLeft = Offset(left, 4f),
                                    size = androidx.compose.ui.geometry.Size(width.coerceAtLeast(4f), size.height - 8f)
                                )
                                drawIntoCanvas { canvas ->
                                    val paint = android.graphics.Paint().apply {
                                        setColor(android.graphics.Color.WHITE)
                                        textSize = 28f
                                    }
                                    canvas.nativeCanvas.drawText(
                                        clip.label,
                                        left + 8f,
                                        size.height / 2f + 8f,
                                        paint
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Fixed CTI Playhead at 10%
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = Color(0xFFEF4444),
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, size.height),
                    strokeWidth = 3f
                )
                drawCircle(
                    color = Color(0xFFEF4444),
                    radius = 8f,
                    center = Offset(playheadX, 8f)
                )
            }
        }
    }
}
