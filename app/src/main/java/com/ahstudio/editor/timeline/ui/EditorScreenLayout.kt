package com.ahstudio.editor.timeline.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahstudio.editor.timeline.app.EditorViewModel
import com.ahstudio.editor.timeline.core.Clip
import com.ahstudio.editor.timeline.core.Track
import com.ahstudio.editor.timeline.core.TrackKind
import kotlin.math.roundToInt

@Composable
fun EditorScreenLayout(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. VIDEO PREVIEW AREA (Fixed weight - will never shrink)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Keeps video screen prominent
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            VideoPreviewSurface(viewModel = viewModel)
        }

        // 2. TIMELINE WRAPPER (Fixed height allocated for editing)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp) // Timeline container ki fixed height
                .background(Color(0xFF181818))
        ) {
            // Timeline Ruler & Timecode
            TimelineRuler(
                currentTimeMs = viewModel.currentTimeMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )

            // A. PINNED MAIN VIDEO TRACK (Never scrolls vertically)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp) // Main track full standard height
                    .background(Color(0xFF222222))
            ) {
                MainVideoTrackRow(
                    track = viewModel.mainVideoTrack,
                    onClipSelected = { viewModel.selectClip(it) }
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // B. VERTICALLY SCROLLABLE SUB-TRACKS (Overlay, Text, Audio, etc.)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Fills remaining timeline space
                userScrollEnabled = true
            ) {
                items(
                    items = viewModel.subTracks,
                    key = { "${it.trackId}_${it.order}" }
                ) { subTrack ->
                    val isSelected = viewModel.selectedTrackId == subTrack.trackId ||
                            viewModel.selectedClipTrackId == subTrack.trackId

                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.02f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "subTrackScale"
                    )

                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "subTrackBorderColor"
                    )

                    val borderWidth by animateDpAsState(
                        targetValue = if (isSelected) 1.5.dp else 0.dp,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "subTrackBorderWidth"
                    )

                    // Har sub-track ki poori height (half-cut nahi hoga)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp) // Har sub track ki fixed poori height
                            .padding(vertical = 2.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
                    ) {
                        SubTrackRow(
                            track = subTrack,
                            onClipSelected = { viewModel.selectClip(it) },
                            isSelected = isSelected
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1. Video Preview Surface for EditorViewModel.
 * Prominently displays video playback canvas, center controls, and timecode info.
 */
@Composable
fun VideoPreviewSurface(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val isPlaying = viewModel.playback.isPlaying
    val currentTimeMs = viewModel.currentTimeMs
    val totalDurationMs = viewModel.engine.durationMicros() / 1000L

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("editor_video_preview_surface"),
        contentAlignment = Alignment.Center
    ) {
        // Video Viewport Canvas with subtle 16:9 / 9:16 frame container
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(8.dp)),
            color = Color(0xFF0D0D11),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262630))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Interactive Play/Pause button
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.5.dp, Color(0xFF00E5FF), CircleShape)
                        .testTag("preview_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Top Status Badges: Timecode and Project details
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF333333))
                    ) {
                        Text(
                            text = "${formatTimeShort(currentTimeMs)} / ${formatTimeShort(totalDurationMs)}",
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF00E5FF))
                    ) {
                        Text(
                            text = "1080p • 30fps",
                            color = Color(0xFF00E5FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Timeline Ruler: Displays precision time tick marks and playhead indicators.
 */
@Composable
fun TimelineRuler(
    currentTimeMs: Long,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(Color(0xFF141414))
            .testTag("timeline_ruler_canvas")
    ) {
        val w = size.width
        val h = size.height
        val tickIntervalPx = 40f
        val numTicks = (w / tickIntervalPx).toInt() + 1

        for (i in 0..numTicks) {
            val x = i * tickIntervalPx
            val isMajor = i % 5 == 0
            val tickHeight = if (isMajor) h * 0.7f else h * 0.35f
            val tickColor = if (isMajor) Color(0xFF94A3B8) else Color(0xFF475569)

            drawLine(
                color = tickColor,
                start = Offset(x, h - tickHeight),
                end = Offset(x, h),
                strokeWidth = if (isMajor) 1.5f else 1f
            )
        }

        // Draw CTI playhead marker on ruler
        val playheadX = w * 0.38f
        drawLine(
            color = Color(0xFF00E5FF),
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, h),
            strokeWidth = 2.5f
        )
    }
}

/**
 * A. Pinned Main Video Track Row (Height 64dp, never scrolls vertically).
 */
@Composable
fun MainVideoTrackRow(
    track: Track,
    onClipSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clips = track.clips

    Row(
        modifier = modifier
            .fillMaxSize()
            .testTag("main_video_track_row_${track.trackId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Header badge
        Surface(
            modifier = Modifier
                .width(84.dp)
                .fillMaxHeight()
                .padding(vertical = 4.dp, horizontal = 4.dp),
            color = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = "Main Track",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Main Video",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Main Track Clips Strip
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 4.dp, horizontal = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1E293B))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            if (clips.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Default primary clip block
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.65f)
                            .clickable { onClipSelected("main_clip_primary") },
                        color = Color(0xFF0284C7),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Video Clip 1",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    clips.forEach { clip ->
                        Surface(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(120.dp)
                                .clickable { onClipSelected(clip.id) },
                            color = Color(0xFF0284C7),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = clip.label.ifBlank { "Clip" },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * B. Vertically Scrollable Sub-Track Row (Height 52dp, full height without half cuts).
 */
@Composable
fun SubTrackRow(
    track: Track,
    onClipSelected: (String) -> Unit,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (icon, trackTint, bgTint) = when (track.kind) {
        TrackKind.OVERLAY -> Triple(Icons.Default.Layers, Color(0xFF818CF8), Color(0xFF1E1B4B))
        TrackKind.TEXT, TrackKind.CAPTION -> Triple(Icons.Default.TextFields, Color(0xFFA78BFA), Color(0xFF312E81))
        TrackKind.AUDIO, TrackKind.VOICE, TrackKind.MUSIC, TrackKind.SFX -> Triple(Icons.Default.MusicNote, Color(0xFF34D399), Color(0xFF064E3B))
        TrackKind.EFFECT -> Triple(Icons.Default.AutoAwesome, Color(0xFFC084FC), Color(0xFF4C1D95))
        TrackKind.STICKER -> Triple(Icons.Default.Face, Color(0xFFFBBF24), Color(0xFF78350F))
        else -> Triple(Icons.Default.Tune, Color(0xFF94A3B8), Color(0xFF1E293B))
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(if (isSelected) Color(0xFF24242A) else Color(0xFF1B1B20))
            .testTag("sub_track_row_${track.trackId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Header badge on the left
        Surface(
            modifier = Modifier
                .width(84.dp)
                .fillMaxHeight()
                .padding(vertical = 3.dp, horizontal = 4.dp),
            color = bgTint,
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, trackTint.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = track.name,
                    tint = trackTint,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = track.name,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Sub Track Content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 3.dp, horizontal = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF141418))
                .border(1.dp, Color(0xFF262630), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            if (track.clips.isEmpty()) {
                // Placeholder clip representation
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(100.dp)
                        .clickable { onClipSelected("clip_${track.trackId}") },
                    color = trackTint.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = track.name,
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    track.clips.forEach { clip ->
                        Surface(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(110.dp)
                                .clickable { onClipSelected(clip.id) },
                            color = trackTint.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = clip.label.ifBlank { track.name },
                                    color = Color.Black,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimeShort(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format("%02d:%02d", mins, secs)
}
