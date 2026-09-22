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
        // Timeline Header & Add Clip Toolbar
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

            // Add Real Clips Toolbar (starts empty as requested)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { engine.addClipToTrack(1L, ClipKind.VIDEO, 6_000_000L, "VID_20250921_001.mp4") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Video", fontSize = 12.sp)
                }

                Button(
                    onClick = { engine.addClipToTrack(2L, ClipKind.VIDEO, 4_000_000L, "Overlay 1") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Overlay", fontSize = 12.sp)
                }

                Button(
                    onClick = { engine.addClipToTrack(3L, ClipKind.TEXT, 3_000_000L, "Ah Studio Text") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Title, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Text", fontSize = 12.sp)
                }
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

        // Ruler & Tracks Canvas Container with Touch Support
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
                // Time Ruler (Professional styling matching screenshot)
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .background(Color(0xFF18181C))
                        .fillMaxWidth()
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val startUs = scrollOffsetUs
                        val endUs = startUs + (size.width / pxPerUs).toLong()
                        val stepUs = 5_000_000L // 5 seconds per major tick
                        var t = (startUs / stepUs) * stepUs
                        while (t <= endUs) {
                            val x = ((t - scrollOffsetUs) * pxPerUs)
                            drawLine(Color(0xFF6B7280), Offset(x, size.height - 14f), Offset(x, size.height), 1.5f)
                            drawIntoCanvas { canvas ->
                                val paint = android.graphics.Paint().apply {
                                    setColor(android.graphics.Color.parseColor("#9CA3AF"))
                                    textSize = 22f
                                    isAntiAlias = true
                                }
                                canvas.nativeCanvas.drawText(
                                    TimeMath.formatTimecode(t, false),
                                    x + 6f,
                                    size.height - 16f,
                                    paint
                                )
                            }
                            t += stepUs
                        }
                    }
                }

                // Professional Tracks (Video 1, Overlay 1, Audio & Text)
                for (track in state.tracks) {
                    Box(
                        modifier = Modifier
                            .height(64.dp)
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
                                
                                // Draw Rounded Card Clip Box
                                drawRect(
                                    color = if (isSelected) color.copy(alpha = 0.95f) else color.copy(alpha = 0.85f),
                                    topLeft = Offset(left, 6f),
                                    size = androidx.compose.ui.geometry.Size(width.coerceAtLeast(8f), size.height - 12f)
                                )

                                drawIntoCanvas { canvas ->
                                    val paint = android.graphics.Paint().apply {
                                        setColor(android.graphics.Color.WHITE)
                                        textSize = 26f
                                        isAntiAlias = true
                                    }
                                    canvas.nativeCanvas.drawText(
                                        clip.label,
                                        left + 12f,
                                        size.height / 2f + 8f,
                                        paint
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Fixed CTI Playhead with Purple Triangular Indicator Handle
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Vertical purple playhead line matching screenshot
                drawLine(
                    color = Color(0xFFA855F7),
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, size.height),
                    strokeWidth = 3f
                )
                // Triangular Handle at Top
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(playheadX - 10f, 0f)
                    lineTo(playheadX + 10f, 0f)
                    lineTo(playheadX, 16f)
                    close()
                }
                drawPath(path, color = Color(0xFFA855F7))
            }
        }
    }
}
