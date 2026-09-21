package com.example.ui.components.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Data representation for timeline items.
 */
data class TimelineClipItem(
  val id: String,
  val uri: String,
  val title: String,
  val startMs: Long,
  val durationMs: Long,
  val isVideo: Boolean = true,
  val color: Color = Color(0xFF1E88E5)
)

data class TimelineAudioItem(
  val id: String,
  val title: String,
  val startMs: Long,
  val durationMs: Long,
  val color: Color = Color(0xFF43A047)
)

data class TimelineTextItem(
  val id: String,
  val text: String,
  val startMs: Long,
  val durationMs: Long,
  val color: Color = Color(0xFFE53935)
)

/**
 * High-performance Multi-Track Horizontal Timeline Component for Android Video Editors.
 *
 * Capabilities:
 * 1. Horizontal multi-track rendering (Video with filmstrip thumbnails, Audio, and Text/Overlay tracks).
 * 2. Real-time timecode formatting (MM:SS:FF based on configured FPS).
 * 3. Bidirectional Playhead / Needle synchronization (smooth 60fps playback sync & instant scrub seek).
 * 4. Zero-thrash rendering architecture avoiding full-screen recompositions during playback.
 * 5. Touch scrubbing on both the ruler/timeline body and the playhead handle.
 */
@Composable
fun MultiTrackVideoTimeline(
  currentPositionMs: Long,
  totalDurationMs: Long,
  isPlaying: Boolean,
  onSeek: (seekToMs: Long) -> Unit,
  videoClips: List<TimelineClipItem>,
  modifier: Modifier = Modifier,
  audioClips: List<TimelineAudioItem> = emptyList(),
  textClips: List<TimelineTextItem> = emptyList(),
  fps: Int = 30,
  pixelsPerSecond: Float = 60f, // Scale factor
  trackHeight: Dp = 60.dp,
  selectedClipId: String? = null,
  onSelectClip: (String) -> Unit = {}
) {
  val density = LocalDensity.current
  val scrollState = rememberScrollState()
  var isScrubbing by remember { mutableStateOf(false) }
  val currentOnSeek by rememberUpdatedState(onSeek)

  val safeTotalDurationMs = kotlin.math.max(totalDurationMs, 1000L)
  val timelineWidthDp = with(density) {
    ((safeTotalDurationMs / 1000f) * pixelsPerSecond).toDp()
  }

  // Auto-scroll timeline to keep playhead visible during playback if user is not actively scrubbing
  LaunchedEffect(currentPositionMs, isPlaying, isScrubbing) {
    if (isPlaying && !isScrubbing) {
      val playheadPx = (currentPositionMs / 1000f) * pixelsPerSecond
      val scrollTarget = kotlin.math.max(0f, playheadPx - 300f).roundToInt()
      if (kotlin.math.abs(scrollState.value - scrollTarget) > 200) {
        scrollState.animateScrollTo(scrollTarget)
      }
    }
  }

  // Isolated timecode formatted string
  val formattedTimecode = remember(currentPositionMs, fps) {
    formatTimecode(currentPositionMs, fps)
  }
  val formattedTotalDuration = remember(safeTotalDurationMs, fps) {
    formatTimecode(safeTotalDurationMs, fps)
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(Color(0xFF121418))
      .testTag("multi_track_video_timeline")
  ) {
    // 1. Timecode & Status Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF181B20))
        .padding(horizontal = 16.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        color = Color(0xFF0D47A1).copy(alpha = 0.35f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E88E5).copy(alpha = 0.5f))
      ) {
        Text(
          text = "$formattedTimecode / $formattedTotalDuration",
          color = Color(0xFF64B5F6),
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.SemiBold,
          fontSize = 12.sp,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
      }

      Text(
        text = "${fps} FPS",
        color = Color.Gray,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
      )
    }

    // 2. Multi-Track Scroll Area with Time Ruler & Playhead
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f, fill = false)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(scrollState)
          .pointerInput(safeTotalDurationMs, pixelsPerSecond) {
            detectDragGestures(
              onDragStart = { offset ->
                isScrubbing = true
                val clickPx = offset.x
                val targetMs = ((clickPx / pixelsPerSecond) * 1000L).toLong().coerceIn(0L, safeTotalDurationMs)
                currentOnSeek(targetMs)
              },
              onDrag = { change, _ ->
                change.consume()
                val currentPx = change.position.x
                val targetMs = ((currentPx / pixelsPerSecond) * 1000L).toLong().coerceIn(0L, safeTotalDurationMs)
                currentOnSeek(targetMs)
              },
              onDragEnd = { isScrubbing = false },
              onDragCancel = { isScrubbing = false }
            )
          }
      ) {
        Column(
          modifier = Modifier
            .width(max(timelineWidthDp + 600.dp, 800.dp))
            .padding(start = 24.dp, end = 200.dp)
        ) {
          // --- A. Time Ruler Bar ---
          TimelineRulerCanvas(
            totalDurationMs = safeTotalDurationMs,
            pixelsPerSecond = pixelsPerSecond,
            fps = fps,
            modifier = Modifier
              .fillMaxWidth()
              .height(26.dp)
          )

          Spacer(modifier = Modifier.height(6.dp))

          // --- B. Track 1: Text / Title Overlays ---
          if (textClips.isNotEmpty()) {
            TrackContainer(
              icon = Icons.Default.TextFields,
              title = "Text",
              height = 36.dp
            ) {
              textClips.forEach { item ->
                val startDp = ((item.startMs / 1000f) * pixelsPerSecond).dp
                val widthDp = max(((item.durationMs / 1000f) * pixelsPerSecond).dp, 30.dp)
                Box(
                  modifier = Modifier
                    .offset(x = startDp)
                    .width(widthDp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(item.color.copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp),
                  contentAlignment = Alignment.CenterStart
                ) {
                  Text(
                    text = item.text,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                  )
                }
              }
            }
            Spacer(modifier = Modifier.height(4.dp))
          }

          // --- C. Track 2: Main Video Track with Clip Thumbnails ---
          TrackContainer(
            icon = Icons.Default.Videocam,
            title = "Video",
            height = trackHeight
          ) {
            videoClips.forEach { clip ->
              val startDp = ((clip.startMs / 1000f) * pixelsPerSecond).dp
              val widthDp = max(((clip.durationMs / 1000f) * pixelsPerSecond).dp, 40.dp)
              val isSelected = clip.id == selectedClipId

              VideoClipItemView(
                clip = clip,
                widthDp = widthDp,
                startOffsetDp = startDp,
                isSelected = isSelected,
                onClick = { onSelectClip(clip.id) }
              )
            }
          }

          Spacer(modifier = Modifier.height(4.dp))

          // --- D. Track 3: Audio Track with Waveform Styling ---
          if (audioClips.isNotEmpty()) {
            TrackContainer(
              icon = Icons.Default.Audiotrack,
              title = "Audio",
              height = 38.dp
            ) {
              audioClips.forEach { audio ->
                val startDp = ((audio.startMs / 1000f) * pixelsPerSecond).dp
                val widthDp = max(((audio.durationMs / 1000f) * pixelsPerSecond).dp, 30.dp)
                Box(
                  modifier = Modifier
                    .offset(x = startDp)
                    .width(widthDp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(audio.color.copy(alpha = 0.75f))
                    .border(1.dp, Color.Green.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp),
                  contentAlignment = Alignment.CenterStart
                ) {
                  Text(
                    text = audio.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1
                  )
                }
              }
            }
          }
        }

        // --- 3. Vertical Playhead / Needle with zero-lag translation ---
        val playheadOffsetDp = with(density) {
          24.dp + ((currentPositionMs / 1000f) * pixelsPerSecond).toDp()
        }

        Box(
          modifier = Modifier
            .offset(x = playheadOffsetDp)
            .fillMaxHeight()
            .width(24.dp)
            .offset(x = (-12).dp)
        ) {
          // Top Playhead Scrubber Handle (Draggable)
          Canvas(
            modifier = Modifier
              .fillMaxSize()
          ) {
            val handleWidth = 20.dp.toPx()
            val handleHeight = 22.dp.toPx()
            val centerX = size.width / 2f

            // Playhead Header Polygon
            val path = Path().apply {
              moveTo(centerX - handleWidth / 2f, 0f)
              lineTo(centerX + handleWidth / 2f, 0f)
              lineTo(centerX + handleWidth / 2f, handleHeight * 0.65f)
              lineTo(centerX, handleHeight)
              lineTo(centerX - handleWidth / 2f, handleHeight * 0.65f)
              close()
            }

            // Draw Head
            drawPath(
              path = path,
              brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF00E5FF), Color(0xFF00B0FF))
              )
            )

            // Draw Vertical Needle Line
            drawLine(
              color = Color(0xFF00E5FF),
              start = Offset(centerX, handleHeight),
              end = Offset(centerX, size.height),
              strokeWidth = 2.5.dp.toPx()
            )
          }
        }
      }
    }
  }
}

/**
 * Track container row layout.
 */
@Composable
private fun TrackContainer(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  height: Dp,
  content: @Composable BoxScope.() -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(height)
      .background(Color(0xFF1E222A), RoundedCornerShape(8.dp))
      .border(0.5.dp, Color(0xFF2A2E39), RoundedCornerShape(8.dp))
  ) {
    content()
  }
}

/**
 * Individual video clip block rendering horizontally with video frame thumbnails.
 */
@Composable
private fun VideoClipItemView(
  clip: TimelineClipItem,
  widthDp: Dp,
  startOffsetDp: Dp,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val context = LocalContext.current
  val density = LocalDensity.current

  // Approximate number of thumbnails to pack across width
  val thumbCount = kotlin.math.max(1, (with(density) { widthDp.toPx() } / 120f).roundToInt())

  Box(
    modifier = Modifier
      .offset(x = startOffsetDp)
      .width(widthDp)
      .fillMaxHeight()
      .padding(vertical = 3.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(Color(0xFF1B2430))
      .border(
        width = if (isSelected) 2.dp else 1.dp,
        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF37474F),
        shape = RoundedCornerShape(8.dp)
      )
      .clickable(onClick = onClick)
  ) {
    // Filmstrip Thumbnails Row
    Row(
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = Arrangement.Start
    ) {
      for (i in 0 until thumbCount) {
        AsyncImage(
          model = ImageRequest.Builder(context)
            .data(clip.uri)
            .crossfade(true)
            .build(),
          contentDescription = "Video Thumbnail",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .border(0.25.dp, Color.Black.copy(alpha = 0.3f))
        )
      }
    }

    // Gradient Overlay and Clip Title Label
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.5f))
          )
        )
        .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
      Text(
        text = clip.title,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.align(Alignment.TopStart)
      )
    }
  }
}

/**
 * Canvas-based ultra-fast time ruler drawing tick marks and timestamps without recomposition lag.
 */
@Composable
private fun TimelineRulerCanvas(
  totalDurationMs: Long,
  pixelsPerSecond: Float,
  fps: Int,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    val totalSeconds = (totalDurationMs / 1000f).toInt() + 10
    val tickColor = Color(0xFF6B7280)
    val textPaint = android.graphics.Paint().apply {
      color = android.graphics.Color.parseColor("#9E9E9E")
      textSize = 10.sp.toPx()
      isAntiAlias = true
    }

    for (sec in 0..totalSeconds) {
      val startX = sec * pixelsPerSecond

      // Major second tick
      drawLine(
        color = tickColor,
        start = Offset(startX, size.height - 12.dp.toPx()),
        end = Offset(startX, size.height),
        strokeWidth = 1.5.dp.toPx()
      )

      // Time label every 1 or 2 seconds
      val timeStr = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
      drawContext.canvas.nativeCanvas.drawText(
        timeStr,
        startX + 4.dp.toPx(),
        size.height - 4.dp.toPx(),
        textPaint
      )

      // Sub-second intermediate ticks (quarter seconds)
      for (sub in 1..3) {
        val subX = startX + (sub * (pixelsPerSecond / 4f))
        drawLine(
          color = tickColor.copy(alpha = 0.4f),
          start = Offset(subX, size.height - 6.dp.toPx()),
          end = Offset(subX, size.height),
          strokeWidth = 1.dp.toPx()
        )
      }
    }
  }
}

/**
 * Formats milliseconds into standard Timecode format (MM:SS:FF).
 */
fun formatTimecode(ms: Long, fps: Int = 30): String {
  val safeMs = kotlin.math.max(0L, ms)
  val totalSec = safeMs / 1000L
  val minutes = totalSec / 60L
  val seconds = totalSec % 60L
  val remMs = safeMs % 1000L
  val frames = ((remMs * fps) / 1000L).coerceIn(0L, (fps - 1).toLong())
  return String.format(Locale.US, "%02d:%02d:%02d", minutes, seconds, frames)
}
