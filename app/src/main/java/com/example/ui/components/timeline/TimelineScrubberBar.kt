package com.example.ui.components.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * High-performance Playhead Scrubbing Bar for frame-by-frame timeline navigation.
 * Features:
 * 1. SMPTE Timecode (HH:MM:SS:FF) and Frame Index display.
 * 2. Tactile Jog / Shuttle Frame Scrubber canvas strip with velocity-sensitive micro-steps.
 * 3. Dedicated Frame step buttons (-5F, -1F, +1F, +5F) and Cut junction jumpers.
 * 4. FPS switcher (24, 25, 30, 60 fps) and Frame Snapping toggle.
 */
@Composable
fun TimelineScrubberBar(
  currentPosMs: Long,
  totalDurationMs: Long,
  fps: Int,
  isFrameSnapping: Boolean,
  onSeekMs: (Long) -> Unit,
  onStepFrames: (Int) -> Unit,
  onSeekToPrevCut: () -> Unit,
  onSeekToNextCut: () -> Unit,
  onFpsChange: (Int) -> Unit,
  onToggleFrameSnapping: () -> Unit,
  onScrubStart: () -> Unit = {},
  onScrubEnd: () -> Unit = {},
  isMultiTrackView: Boolean = true,
  onToggleMultiTrackView: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  var isExpandedJogWheel by remember { mutableStateOf(false) }
  var showFpsMenu by remember { mutableStateOf(false) }
  var activeScrubOffsetFrames by remember { mutableStateOf<Int?>(null) }

  val currentFrame = msToFrameIndex(currentPosMs, fps)
  val totalFrames = msToFrameIndex(totalDurationMs, fps)
  val smpteTimecode = formatSmpteTimecode(currentPosMs, fps)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(StudioDarkBg)
      .border(BorderStroke(1.dp, StudioBorder))
      .testTag("timeline_scrubber_bar")
  ) {
    // 1. Top Control Strip
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(38.dp)
        .padding(horizontal = 8.dp, vertical = 3.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left: SMPTE Timecode & Frame Counter Pill
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(4.dp),
          color = StudioSurfaceVariant,
          border = BorderStroke(1.dp, StudioBorder),
          modifier = Modifier
            .clickable { isExpandedJogWheel = !isExpandedJogWheel }
            .testTag("smpte_timecode_badge")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Box(
              modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (activeScrubOffsetFrames != null) AmberAccent else RedAccent)
            )
            Text(
              text = smpteTimecode,
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White
              )
            )
            Text(
              text = "F$currentFrame",
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                color = CyanAccent
              )
            )
          }
        }

        // FPS Selector Pill
        Box {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = StudioSurface,
            border = BorderStroke(1.dp, StudioBorder),
            modifier = Modifier
              .clickable { showFpsMenu = true }
              .testTag("fps_selector_pill")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "${fps}fps",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium,
                  color = TextSecondary
                )
              )
              Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Select FPS",
                tint = TextTertiary,
                modifier = Modifier.size(14.dp)
              )
            }
          }

          DropdownMenu(
            expanded = showFpsMenu,
            onDismissRequest = { showFpsMenu = false },
            modifier = Modifier.background(StudioSurface)
          ) {
            listOf(24, 25, 30, 60).forEach { presetFps ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = "$presetFps FPS ${if (presetFps == fps) "✓" else ""}",
                    style = MaterialTheme.typography.bodySmall.copy(
                      color = if (presetFps == fps) CyanAccent else Color.White
                    )
                  )
                },
                onClick = {
                  onFpsChange(presetFps)
                  showFpsMenu = false
                }
              )
            }
          }
        }

        // Snap-to-Frame Lock Button
        IconButton(
          onClick = onToggleFrameSnapping,
          modifier = Modifier
            .size(26.dp)
            .background(
              if (isFrameSnapping) CyanAccent.copy(alpha = 0.2f) else Color.Transparent,
              RoundedCornerShape(4.dp)
            )
            .testTag("frame_snap_toggle_btn")
        ) {
          Icon(
            imageVector = if (isFrameSnapping) Icons.Default.FilterFrames else Icons.Default.CropPortrait,
            contentDescription = "Toggle Frame Snapping",
            tint = if (isFrameSnapping) CyanAccent else TextTertiary,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      // Center: Jog / Shuttle Expansion Toggle Button & Mode Toggle
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (isExpandedJogWheel) PurpleAccent.copy(alpha = 0.3f) else StudioSurface,
          border = BorderStroke(1.dp, if (isExpandedJogWheel) PurpleAccent else StudioBorder),
          modifier = Modifier
            .clickable { isExpandedJogWheel = !isExpandedJogWheel }
            .testTag("toggle_jog_scrubber_btn")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Speed,
              contentDescription = null,
              tint = if (isExpandedJogWheel) PurpleAccent else TextSecondary,
              modifier = Modifier.size(13.dp)
            )
            Text(
              text = if (isExpandedJogWheel) "Hide Jog" else "Jog",
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isExpandedJogWheel) PurpleAccent else TextSecondary
              )
            )
          }
        }

        if (onToggleMultiTrackView != null) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isMultiTrackView) StudioSurfaceVariant else StudioDarkBg,
            border = BorderStroke(1.dp, if (isMultiTrackView) CyanAccent else StudioBorder),
            modifier = Modifier
              .clickable { onToggleMultiTrackView() }
              .testTag("timeline_mode_toggle")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = if (isMultiTrackView) Icons.Default.ViewAgenda else Icons.Default.Tune,
                contentDescription = null,
                tint = if (isMultiTrackView) CyanAccent else AudioTrackColor,
                modifier = Modifier.size(11.dp)
              )
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = if (isMultiTrackView) "Tracks" else "Wave",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 8.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
              )
            }
          }
        }
      }

      // Right: Frame-by-frame direct step buttons
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        // Prev Cut
        IconButton(
          onClick = onSeekToPrevCut,
          modifier = Modifier.size(26.dp).testTag("scrub_prev_cut_btn")
        ) {
          Icon(Icons.Default.SkipPrevious, contentDescription = "Prev Cut", tint = TextSecondary, modifier = Modifier.size(16.dp))
        }

        // -5 Frames
        Surface(
          shape = RoundedCornerShape(4.dp),
          color = StudioSurface,
          modifier = Modifier
            .height(24.dp)
            .clickable { onStepFrames(-5) }
            .testTag("scrub_minus_5f_btn")
        ) {
          Box(modifier = Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text("-5F", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary))
          }
        }

        // -1 Frame
        Surface(
          shape = RoundedCornerShape(4.dp),
          color = StudioSurfaceVariant,
          border = BorderStroke(0.5.dp, StudioBorder),
          modifier = Modifier
            .height(24.dp)
            .clickable { onStepFrames(-1) }
            .testTag("scrub_minus_1f_btn")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "-1 Frame", tint = CyanAccent, modifier = Modifier.size(11.dp))
            Text("1F", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyanAccent))
          }
        }

        // +1 Frame
        Surface(
          shape = RoundedCornerShape(4.dp),
          color = StudioSurfaceVariant,
          border = BorderStroke(0.5.dp, StudioBorder),
          modifier = Modifier
            .height(24.dp)
            .clickable { onStepFrames(1) }
            .testTag("scrub_plus_1f_btn")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("1F", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyanAccent))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "+1 Frame", tint = CyanAccent, modifier = Modifier.size(11.dp))
          }
        }

        // +5 Frames
        Surface(
          shape = RoundedCornerShape(4.dp),
          color = StudioSurface,
          modifier = Modifier
            .height(24.dp)
            .clickable { onStepFrames(5) }
            .testTag("scrub_plus_5f_btn")
        ) {
          Box(modifier = Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text("+5F", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary))
          }
        }

        // Next Cut
        IconButton(
          onClick = onSeekToNextCut,
          modifier = Modifier.size(26.dp).testTag("scrub_next_cut_btn")
        ) {
          Icon(Icons.Default.SkipNext, contentDescription = "Next Cut", tint = TextSecondary, modifier = Modifier.size(16.dp))
        }
      }
    }

    // 2. Expandable Virtual Jog Wheel / Tactile Shuttle Strip
    AnimatedVisibility(
      visible = isExpandedJogWheel,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      JogWheelFrameScrubber(
        fps = fps,
        onScrubDeltaFrames = { deltaFrames ->
          activeScrubOffsetFrames = (activeScrubOffsetFrames ?: 0) + deltaFrames
          onStepFrames(deltaFrames)
        },
        onScrubStart = onScrubStart,
        onScrubEnd = {
          activeScrubOffsetFrames = null
          onScrubEnd()
        },
        activeDeltaDisplay = activeScrubOffsetFrames
      )
    }
  }
}

/**
 * Precision Jog Wheel & Frame Shuttle Canvas Strip.
 * Translates touch drag velocity and micro-movements into precise frame steps.
 */
@Composable
fun JogWheelFrameScrubber(
  fps: Int,
  onScrubDeltaFrames: (Int) -> Unit,
  onScrubEnd: () -> Unit,
  activeDeltaDisplay: Int?,
  onScrubStart: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var accumulatedDragPx by remember { mutableFloatStateOf(0f) }
  var visualWheelOffsetPx by remember { mutableFloatStateOf(0f) }

  // 1 frame corresponds to ~8dp of drag travel for ultra-precise tactile feel
  val pxPerFrame = 22f

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(44.dp)
      .background(
        Brush.verticalGradient(
          listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0F172A))
        )
      )
      .pointerInput(fps) {
        detectDragGestures(
          onDragStart = {
            accumulatedDragPx = 0f
            onScrubStart()
          },
          onDragEnd = {
            accumulatedDragPx = 0f
            onScrubEnd()
          },
          onDragCancel = {
            accumulatedDragPx = 0f
            onScrubEnd()
          },
          onDrag = { change, dragAmount ->
            change.consume()
            accumulatedDragPx += dragAmount.x
            visualWheelOffsetPx = (visualWheelOffsetPx + dragAmount.x) % 400f

            val framesToStep = (accumulatedDragPx / pxPerFrame).toInt()
            if (framesToStep != 0) {
              onScrubDeltaFrames(framesToStep)
              accumulatedDragPx -= framesToStep * pxPerFrame
            }
          }
        )
      }
      .testTag("jog_wheel_frame_scrubber")
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val canvasWidth = size.width
      val canvasHeight = size.height
      val centerX = canvasWidth / 2f

      // 1. Background Shading / Vignette
      drawRect(
        brush = Brush.horizontalGradient(
          colors = listOf(
            Color.Black.copy(alpha = 0.8f),
            Color.Transparent,
            Color.Transparent,
            Color.Black.copy(alpha = 0.8f)
          )
        )
      )

      // 2. Draw Jog Wheel Calibrations
      val tickSpacingPx = pxPerFrame
      val totalVisibleTicks = (canvasWidth / tickSpacingPx).toInt() + 4
      val baseOffset = visualWheelOffsetPx % tickSpacingPx

      val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(180, 148, 163, 184)
        textSize = 8.sp.toPx()
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
        textAlign = android.graphics.Paint.Align.CENTER
      }

      for (i in -totalVisibleTicks / 2..totalVisibleTicks / 2) {
        val x = centerX + (i * tickSpacingPx) + baseOffset
        if (x < 0 || x > canvasWidth) continue

        val distFromCenter = abs(x - centerX) / (canvasWidth / 2f)
        val alpha = (1f - distFromCenter.coerceIn(0f, 1f))

        val isMajor = i % 5 == 0
        val isSuperMajor = i % 10 == 0

        val tickHeight = when {
          isSuperMajor -> canvasHeight * 0.7f
          isMajor -> canvasHeight * 0.45f
          else -> canvasHeight * 0.25f
        }

        val tickColor = when {
          isSuperMajor -> CyanAccent.copy(alpha = alpha * 0.9f)
          isMajor -> Color.White.copy(alpha = alpha * 0.7f)
          else -> TextTertiary.copy(alpha = alpha * 0.4f)
        }

        val yStart = (canvasHeight - tickHeight) / 2f
        val yEnd = yStart + tickHeight

        drawLine(
          color = tickColor,
          start = Offset(x, yStart),
          end = Offset(x, yEnd),
          strokeWidth = if (isSuperMajor) 2f else 1f
        )
      }

      // 3. Center Target Indicator Line (Red / Amber needle)
      val needleColor = if (activeDeltaDisplay != null) AmberAccent else RedAccent
      drawLine(
        color = needleColor,
        start = Offset(centerX, 0f),
        end = Offset(centerX, canvasHeight),
        strokeWidth = 2.5f
      )

      // Top and Bottom Diamond Caps for Center Needle
      val capSize = 4.dp.toPx()
      drawCircle(color = needleColor, radius = capSize, center = Offset(centerX, capSize))
      drawCircle(color = needleColor, radius = capSize, center = Offset(centerX, canvasHeight - capSize))
    }

    // 4. Center Overlay Text / Scrub Delta Badge
    if (activeDeltaDisplay != null) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = AmberAccent,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .offset(y = 2.dp)
      ) {
        Text(
          text = if (activeDeltaDisplay > 0) "+$activeDeltaDisplay Frames" else "$activeDeltaDisplay Frames",
          style = MaterialTheme.typography.labelSmall.copy(
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
          ),
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
      }
    } else {
      Text(
        text = "◀ DRAG HORIZONTALLY FOR FRAME-BY-FRAME JOG ▶",
        style = MaterialTheme.typography.labelSmall.copy(
          color = TextTertiary.copy(alpha = 0.6f),
          fontSize = 8.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        ),
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 2.dp)
      )
    }
  }
}

/**
 * Converts milliseconds to SMPTE timecode format: HH:MM:SS:FF
 */
fun formatSmpteTimecode(ms: Long, fps: Int = 30): String {
  val totalFrames = ((ms * fps) / 1000.0).roundToInt()
  val frame = totalFrames % fps
  val totalSeconds = totalFrames / fps
  val seconds = totalSeconds % 60
  val totalMinutes = totalSeconds / 60
  val minutes = totalMinutes % 60
  val hours = totalMinutes / 60

  return if (hours > 0) {
    String.format("%02d:%02d:%02d:%02d", hours, minutes, seconds, frame)
  } else {
    String.format("%02d:%02d:%02d", minutes, seconds, frame)
  }
}

/**
 * Converts milliseconds to absolute frame index.
 */
fun msToFrameIndex(ms: Long, fps: Int = 30): Long {
  return ((ms * fps) / 1000.0).toLong()
}

/**
 * Converts frame index to milliseconds.
 */
fun frameIndexToMs(frame: Long, fps: Int = 30): Long {
  return ((frame * 1000.0) / fps).toLong()
}
