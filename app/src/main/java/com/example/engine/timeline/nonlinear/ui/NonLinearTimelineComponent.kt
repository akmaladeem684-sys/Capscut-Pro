package com.example.engine.timeline.nonlinear.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.timeline.nonlinear.math.MagneticSnappingEngine
import com.example.engine.timeline.nonlinear.math.TimelineTimeMath
import com.example.engine.timeline.nonlinear.models.*
import com.example.engine.timeline.nonlinear.reducer.TimelineReducer
import kotlin.math.roundToLong

/**
 * High-Performance Jetpack Compose UI component for Non-Linear Multi-Track Timeline.
 * Features:
 * - Synchronized Coordinate System: Top Time-Ruler & multi-track rows with unified horizontal scroll & zoom.
 * - Draggable Playhead decoupled from recomposition using derivedStateOf.
 * - Gestures on clips: free drag along time & tracks, left/right trim handles with duration clamping, pinch-to-zoom.
 * - Custom Canvas rendering for clip waveforms and visual borders.
 * - Magnetic snapping highlight guide lines.
 */
@Composable
fun NonLinearTimelineComponent(
  state: TimelineState,
  onAction: (TimelineAction) -> Unit,
  modifier: Modifier = Modifier,
  isPlaying: Boolean = false,
  onTogglePlayPause: (() -> Unit)? = null,
  onAddMedia: (() -> Unit)? = null,
  onAddAudio: (() -> Unit)? = null,
  onAddText: (() -> Unit)? = null
) {
  val horizontalScrollState = rememberScrollState()
  var zoomLevel by remember { mutableFloatStateOf(state.zoomLevelPxPerSec) }
  var activeSnapGuidePx by remember { mutableStateOf<Float?>(null) }

  // Total canvas width in pixels
  val totalWidthPx by remember(state.totalDurationUs, zoomLevel) {
    derivedStateOf {
      TimelineTimeMath.usToPixels(state.totalDurationUs + 3_000_000L, zoomLevel).coerceAtLeast(1200f)
    }
  }

  // Playhead position in pixels
  val playheadPx by remember(state.playheadUs, zoomLevel) {
    derivedStateOf {
      TimelineTimeMath.usToPixels(state.playheadUs, zoomLevel)
    }
  }

  // Dark studio theme palette
  val surfaceColor = Color(0xFF121316)
  val rulerColor = Color(0xFF1A1C23)
  val trackLaneBg = Color(0xFF181A20)
  val playheadColor = Color(0xFFFF5252)
  val snapGuideColor = Color(0xFF00E5FF)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(surfaceColor)
      .pointerInput(Unit) {
        // Pinch-to-zoom horizontally around playhead pivot
        detectTransformGestures { centroid, _, zoomChange, _ ->
          if (zoomChange != 1.0f) {
            val newZoom = (zoomLevel * zoomChange).coerceIn(10f, 1000f)
            zoomLevel = newZoom
            onAction(TimelineAction.SetZoomLevel(newZoom))
          }
        }
      }
      .testTag("nonlinear_timeline_container")
  ) {
    // 1. Top Time-Ruler aligned with track lanes
    Row(modifier = Modifier.fillMaxWidth()) {
      Box(
        modifier = Modifier
          .width(96.dp)
          .height(36.dp)
          .background(Color(0xFF16181E)),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "TRACKS",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF8B949E),
          letterSpacing = 1.sp
        )
      }

      TimeRulerView(
        scrollState = horizontalScrollState,
        totalWidthPx = totalWidthPx,
        zoomLevel = zoomLevel,
        playheadPx = playheadPx,
        playheadUs = state.playheadUs,
        markers = state.markers,
        rulerBg = rulerColor,
        onSeek = { us -> onAction(TimelineAction.SetPlayhead(us)) },
        modifier = Modifier.weight(1f)
      )
    }

    HorizontalDivider(color = Color(0xFF282C37), thickness = 1.dp)

    // 2. Tracks and Playhead needle
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f, fill = false)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth()
      ) {
        // Track Header Controls column
        Column(
          modifier = Modifier
            .width(96.dp)
            .background(Color(0xFF16181E))
        ) {
          state.tracks.forEach { track ->
            TrackHeaderControlRow(
              track = track,
              onToggleMute = { onAction(TimelineAction.ToggleTrackMute(track.id)) },
              onToggleLock = { onAction(TimelineAction.ToggleTrackLock(track.id)) }
            )
          }
        }

        // Scrollable multi-track lanes
        Box(
          modifier = Modifier
            .weight(1f)
            .horizontalScroll(horizontalScrollState)
        ) {
          Column(
            modifier = Modifier.width(totalWidthPx.dp)
          ) {
            state.tracks.forEach { track ->
              TrackLaneRow(
                track = track,
                state = state,
                zoomLevel = zoomLevel,
                laneBg = trackLaneBg,
                onAction = onAction,
                onSnapGuide = { guidePx -> activeSnapGuidePx = guidePx }
              )
            }
          }

          // Magnetic Snapping Vertical Highlight Guide
          activeSnapGuidePx?.let { guideX ->
            Box(
              modifier = Modifier
                .offset(x = guideX.dp)
                .fillMaxHeight()
                .width(1.5.dp)
                .background(snapGuideColor)
            )
          }

          // Draggable Playhead Needle
          PlayheadNeedle(
            playheadPx = playheadPx,
            playheadUs = state.playheadUs,
            zoomLevel = zoomLevel,
            needleColor = playheadColor,
            onScrub = { us -> onAction(TimelineAction.SetPlayhead(us)) }
          )
        }
      }
    }

    // 3. Bottom Timeline Action Quick Bar
    TimelineBottomActionBar(
      state = state,
      onAction = onAction,
      isPlaying = isPlaying,
      onTogglePlayPause = onTogglePlayPause,
      zoomLevel = zoomLevel,
      onZoomChange = { newZoom ->
        zoomLevel = newZoom
        onAction(TimelineAction.SetZoomLevel(newZoom))
      },
      onAddMedia = onAddMedia,
      onAddAudio = onAddAudio,
      onAddText = onAddText
    )
  }
}

/**
 * Top Time-Ruler rendering dynamic intervals based on zoom level (frames, seconds, minutes).
 */
@Composable
fun TimeRulerView(
  scrollState: androidx.compose.foundation.ScrollState,
  totalWidthPx: Float,
  zoomLevel: Float,
  playheadPx: Float,
  playheadUs: Long,
  markers: List<TimelineMarker>,
  rulerBg: Color,
  onSeek: (Long) -> Unit,
  modifier: Modifier = Modifier
) {
  val stepUs = remember(zoomLevel) {
    TimelineTimeMath.calculateDynamicRulerStep(zoomLevel)
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(36.dp)
      .background(rulerBg)
      .horizontalScroll(scrollState)
      .pointerInput(zoomLevel) {
        detectDragGestures(
          onDragStart = { offset ->
            val seekUs = TimelineTimeMath.pixelsToUs(offset.x, zoomLevel)
            onSeek(seekUs)
          },
          onDrag = { change, _ ->
            val seekUs = TimelineTimeMath.pixelsToUs(change.position.x, zoomLevel)
            onSeek(seekUs)
          }
        )
      }
  ) {
    Canvas(
      modifier = Modifier
        .width(totalWidthPx.dp)
        .fillMaxHeight()
    ) {
      val canvasWidth = size.width
      val stepPx = TimelineTimeMath.usToPixels(stepUs, zoomLevel)

      var currentUs = 0L
      while (currentUs < 10 * 3600 * 1_000_000L) {
        val x = TimelineTimeMath.usToPixels(currentUs, zoomLevel)
        if (x > canvasWidth) break

        val isMajor = (currentUs % (stepUs * 2)) == 0L
        val tickHeight = if (isMajor) size.height * 0.5f else size.height * 0.25f

        drawLine(
          color = if (isMajor) Color(0xFF9E9E9E) else Color(0xFF616161),
          start = Offset(x, size.height - tickHeight),
          end = Offset(x, size.height),
          strokeWidth = if (isMajor) 1.5f else 1.0f
        )

        currentUs += stepUs
      }

      // Draw Markers on Ruler
      markers.forEach { marker ->
        val mx = TimelineTimeMath.usToPixels(marker.timestampUs, zoomLevel)
        drawCircle(
          color = Color(marker.color),
          radius = 4.dp.toPx(),
          center = Offset(mx, size.height * 0.35f)
        )
      }
    }

    // Playhead head marker on ruler
    Box(
      modifier = Modifier
        .offset(x = (playheadPx - 6).dp)
        .size(12.dp)
        .clip(CircleShape)
        .background(Color(0xFFFF5252))
        .align(Alignment.BottomStart)
    )
  }
}

/**
 * Track Header control column showing track type icon, name, mute and lock buttons.
 */
@Composable
fun TrackHeaderControlRow(
  track: Track,
  onToggleMute: () -> Unit,
  onToggleLock: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(60.dp)
      .padding(horizontal = 6.dp, vertical = 4.dp)
      .background(Color(0xFF1E212A), RoundedCornerShape(4.dp)),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = track.name,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
      )
      Text(
        text = track.type.name,
        color = Color(0xFF8B949E),
        fontSize = 9.sp
      )
    }

    Row {
      IconButton(
        onClick = onToggleMute,
        modifier = Modifier.size(24.dp)
      ) {
        Icon(
          imageVector = if (track.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
          contentDescription = "Mute Track",
          tint = if (track.isMuted) Color(0xFFFF5252) else Color(0xFFB0B8C4),
          modifier = Modifier.size(16.dp)
        )
      }
      IconButton(
        onClick = onToggleLock,
        modifier = Modifier.size(24.dp)
      ) {
        Icon(
          imageVector = if (track.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
          contentDescription = "Lock Track",
          tint = if (track.isLocked) Color(0xFFFFB74D) else Color(0xFFB0B8C4),
          modifier = Modifier.size(16.dp)
        )
      }
    }
  }
}

/**
 * Track Lane Row rendering non-linear clips with blank gaps, trim handles, and waveform visualization.
 */
@Composable
fun TrackLaneRow(
  track: Track,
  state: TimelineState,
  zoomLevel: Float,
  laneBg: Color,
  onAction: (TimelineAction) -> Unit,
  onSnapGuide: (Float?) -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(60.dp)
      .padding(vertical = 3.dp)
      .background(laneBg, RoundedCornerShape(4.dp))
  ) {
    track.clips.forEach { clip ->
      ClipItemView(
        clip = clip,
        track = track,
        state = state,
        zoomLevel = zoomLevel,
        onAction = onAction,
        onSnapGuide = onSnapGuide
      )
    }
  }
}

/**
 * Individual Clip View inside a track lane with custom gestures and waveform/filmstrip canvas.
 */
@Composable
fun ClipItemView(
  clip: Clip,
  track: Track,
  state: TimelineState,
  zoomLevel: Float,
  onAction: (TimelineAction) -> Unit,
  onSnapGuide: (Float?) -> Unit
) {
  val startX = TimelineTimeMath.usToPixels(clip.startTimeUs, zoomLevel)
  val widthPx = TimelineTimeMath.usToPixels(clip.durationUs, zoomLevel).coerceAtLeast(30f)

  // Distinct track type colors
  val clipColor = when (track.type) {
    TrackType.VIDEO -> Color(0xFF2E7D32)
    TrackType.AUDIO -> Color(0xFF1565C0)
    TrackType.OVERLAY -> Color(0xFF7B1FA2)
    TrackType.TEXT -> Color(0xFFE65100)
    TrackType.FX -> Color(0xFFC2185B)
  }

  val isSelected = clip.isSelected

  Box(
    modifier = Modifier
      .offset(x = startX.dp)
      .width(widthPx.dp)
      .fillMaxHeight()
      .clip(RoundedCornerShape(6.dp))
      .background(clipColor)
      .then(
        if (isSelected) Modifier.border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(6.dp))
        else Modifier.border(0.5.dp, Color(0x44FFFFFF), RoundedCornerShape(6.dp))
      )
      .clickable {
        onAction(TimelineAction.SelectClip(clip.id))
      }
      .pointerInput(clip.id, zoomLevel) {
        // Free drag horizontally along time, and snapping
        detectDragGesturesAfterLongPress(
          onDragStart = {
            onAction(TimelineAction.SelectClip(clip.id))
          },
          onDrag = { change, dragAmount ->
            change.consume()
            val deltaUs = TimelineTimeMath.pixelsToUs(dragAmount.x, zoomLevel)
            val candidateStartUs = (clip.startTimeUs + deltaUs).coerceAtLeast(0L)

            val snap = MagneticSnappingEngine.snapClipMove(
              candidateStartUs = candidateStartUs,
              clipDurationUs = clip.durationUs,
              movingClipId = clip.id,
              targetTrackId = track.id,
              state = state
            )

            onSnapGuide(if (snap.isSnapped) snap.guideLinePx else null)
            onAction(TimelineAction.MoveClip(clip.id, track.id, snap.snappedTimeUs))
          },
          onDragEnd = { onSnapGuide(null) },
          onDragCancel = { onSnapGuide(null) }
        )
      }
  ) {
    // Custom Canvas for Audio Waveform / Filmstrip representation
    Canvas(modifier = Modifier.fillMaxSize()) {
      if (track.type == TrackType.AUDIO && clip.waveformPoints != null) {
        val points = clip.waveformPoints
        val step = size.width / points.size
        val midY = size.height / 2f

        for (i in points.indices) {
          val amp = points[i].coerceIn(0f, 1f) * (size.height * 0.4f)
          drawLine(
            color = Color(0xAAFFFFFF),
            start = Offset(i * step, midY - amp),
            end = Offset(i * step, midY + amp),
            strokeWidth = 1.5f
          )
        }
      } else {
        // Decorative filmstrip notches
        val notchSpacing = 20.dp.toPx()
        var nx = 4.dp.toPx()
        while (nx < size.width - 4.dp.toPx()) {
          drawCircle(Color(0x33000000), radius = 2.dp.toPx(), center = Offset(nx, 6.dp.toPx()))
          drawCircle(Color(0x33000000), radius = 2.dp.toPx(), center = Offset(nx, size.height - 6.dp.toPx()))
          nx += notchSpacing
        }
      }
    }

    // Clip Label
    Text(
      text = clip.name,
      color = Color.White,
      fontSize = 11.sp,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      modifier = Modifier
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .align(Alignment.TopStart)
    )

    // Left Edge Trim Handle (Head)
    if (isSelected && !track.isLocked) {
      Box(
        modifier = Modifier
          .align(Alignment.CenterStart)
          .width(16.dp)
          .fillMaxHeight()
          .background(Color(0xFF00E5FF), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
          .pointerInput(clip.id) {
            detectDragGestures(
              onDrag = { change, dragAmount ->
                change.consume()
                val deltaUs = TimelineTimeMath.pixelsToUs(dragAmount.x, zoomLevel)
                val candidateStartUs = clip.startTimeUs + deltaUs
                val snap = MagneticSnappingEngine.snapEdgeTrim(candidateStartUs, clip.id, state)
                onSnapGuide(if (snap.isSnapped) snap.guideLinePx else null)
                val actualDeltaUs = snap.snappedTimeUs - clip.startTimeUs
                onAction(TimelineAction.TrimClip(clip.id, TrimEdge.HEAD, actualDeltaUs))
              },
              onDragEnd = { onSnapGuide(null) },
              onDragCancel = { onSnapGuide(null) }
            )
          }
      )
    }

    // Right Edge Trim Handle (Tail)
    if (isSelected && !track.isLocked) {
      Box(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .width(16.dp)
          .fillMaxHeight()
          .background(Color(0xFF00E5FF), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
          .pointerInput(clip.id) {
            detectDragGestures(
              onDrag = { change, dragAmount ->
                change.consume()
                val deltaUs = TimelineTimeMath.pixelsToUs(dragAmount.x, zoomLevel)
                val candidateEndUs = clip.endTimeUs + deltaUs
                val snap = MagneticSnappingEngine.snapEdgeTrim(candidateEndUs, clip.id, state)
                onSnapGuide(if (snap.isSnapped) snap.guideLinePx else null)
                val actualDeltaUs = snap.snappedTimeUs - clip.endTimeUs
                onAction(TimelineAction.TrimClip(clip.id, TrimEdge.TAIL, actualDeltaUs))
              },
              onDragEnd = { onSnapGuide(null) },
              onDragCancel = { onSnapGuide(null) }
            )
          }
      )
    }
  }
}

/**
 * High-precision Draggable Playhead Needle with scannable timecode readout.
 */
@Composable
fun PlayheadNeedle(
  playheadPx: Float,
  playheadUs: Long,
  zoomLevel: Float,
  needleColor: Color,
  onScrub: (Long) -> Unit
) {
  Box(
    modifier = Modifier
      .offset(x = (playheadPx - 1).dp)
      .fillMaxHeight()
      .width(2.dp)
      .background(needleColor)
  ) {
    // Draggable thumb on top of the needle
    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .offset(y = (-8).dp)
        .size(16.dp)
        .clip(CircleShape)
        .background(needleColor)
        .pointerInput(zoomLevel) {
          detectDragGestures(
            onDrag = { change, dragAmount ->
              change.consume()
              val deltaUs = TimelineTimeMath.pixelsToUs(dragAmount.x, zoomLevel)
              onScrub((playheadUs + deltaUs).coerceAtLeast(0L))
            }
          )
        }
    )
  }
}

/**
 * Bottom quick action toolbar for Play/Pause, Split, Ripple Delete, Gap Delete, Media additions, and Zoom.
 */
@Composable
fun TimelineBottomActionBar(
  state: TimelineState,
  onAction: (TimelineAction) -> Unit,
  isPlaying: Boolean = false,
  onTogglePlayPause: (() -> Unit)? = null,
  zoomLevel: Float = 100f,
  onZoomChange: (Float) -> Unit = {},
  onAddMedia: (() -> Unit)? = null,
  onAddAudio: (() -> Unit)? = null,
  onAddText: (() -> Unit)? = null
) {
  val selectedClip = state.selectedClip()

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(52.dp)
      .background(Color(0xFF1A1C23))
      .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    // Left: Play/Pause and Timecode readout
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      if (onTogglePlayPause != null) {
        IconButton(
          onClick = onTogglePlayPause,
          modifier = Modifier.size(34.dp).testTag("timeline_play_pause_button")
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color(0xFF00E5FF),
            modifier = Modifier.size(22.dp)
          )
        }
      }

      Text(
        text = TimelineTimeMath.formatTimecode(state.playheadUs),
        color = Color(0xFF00E5FF),
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold
      )
    }

    // Center: Quick Add Media / Audio / Text buttons
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      if (onAddMedia != null) {
        IconButton(
          onClick = onAddMedia,
          modifier = Modifier.size(32.dp).testTag("timeline_add_media_button")
        ) {
          Icon(
            imageVector = Icons.Default.VideoCall,
            contentDescription = "Add Media",
            tint = Color(0xFF81C784),
            modifier = Modifier.size(18.dp)
          )
        }
      }

      if (onAddAudio != null) {
        IconButton(
          onClick = onAddAudio,
          modifier = Modifier.size(32.dp).testTag("timeline_add_audio_button")
        ) {
          Icon(
            imageVector = Icons.Default.Audiotrack,
            contentDescription = "Add Audio",
            tint = Color(0xFF64B5F6),
            modifier = Modifier.size(18.dp)
          )
        }
      }

      if (onAddText != null) {
        IconButton(
          onClick = onAddText,
          modifier = Modifier.size(32.dp).testTag("timeline_add_text_button")
        ) {
          Icon(
            imageVector = Icons.Default.TextFields,
            contentDescription = "Add Text",
            tint = Color(0xFFFFB74D),
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    // Right: Split, Ripple Delete, Gap Delete & Zoom
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      // Split Button
      IconButton(
        onClick = {
          if (selectedClip != null) {
            onAction(TimelineAction.SplitClip(selectedClip.id, state.playheadUs))
          }
        },
        enabled = selectedClip != null && selectedClip.containsTimestamp(state.playheadUs),
        modifier = Modifier.size(32.dp).testTag("timeline_split_button")
      ) {
        Icon(
          imageVector = Icons.Default.ContentCut,
          contentDescription = "Split Clip",
          tint = if (selectedClip != null && selectedClip.containsTimestamp(state.playheadUs)) Color.White else Color(0xFF616161),
          modifier = Modifier.size(18.dp)
        )
      }

      // Ripple Delete Button
      IconButton(
        onClick = {
          if (selectedClip != null) {
            onAction(TimelineAction.RippleDelete(selectedClip.id))
          }
        },
        enabled = selectedClip != null,
        modifier = Modifier.size(32.dp).testTag("timeline_ripple_delete_button")
      ) {
        Icon(
          imageVector = Icons.Default.DeleteSweep,
          contentDescription = "Ripple Delete",
          tint = if (selectedClip != null) Color(0xFFFF5252) else Color(0xFF616161),
          modifier = Modifier.size(18.dp)
        )
      }

      // Gap Delete Button
      IconButton(
        onClick = {
          if (selectedClip != null) {
            onAction(TimelineAction.GapDelete(selectedClip.id))
          }
        },
        enabled = selectedClip != null,
        modifier = Modifier.size(32.dp).testTag("timeline_gap_delete_button")
      ) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "Gap Delete",
          tint = if (selectedClip != null) Color(0xFFFFB74D) else Color(0xFF616161),
          modifier = Modifier.size(18.dp)
        )
      }

      // Zoom Out
      IconButton(
        onClick = { onZoomChange((zoomLevel * 0.8f).coerceAtLeast(10f)) },
        modifier = Modifier.size(28.dp).testTag("timeline_zoom_out_button")
      ) {
        Icon(
          imageVector = Icons.Default.ZoomOut,
          contentDescription = "Zoom Out",
          tint = Color(0xFFB0B8C4),
          modifier = Modifier.size(16.dp)
        )
      }

      // Zoom In
      IconButton(
        onClick = { onZoomChange((zoomLevel * 1.25f).coerceAtMost(1000f)) },
        modifier = Modifier.size(28.dp).testTag("timeline_zoom_in_button")
      ) {
        Icon(
          imageVector = Icons.Default.ZoomIn,
          contentDescription = "Zoom In",
          tint = Color(0xFFB0B8C4),
          modifier = Modifier.size(16.dp)
        )
      }
    }
  }
}
