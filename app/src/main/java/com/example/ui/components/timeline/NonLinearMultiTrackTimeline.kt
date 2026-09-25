package com.example.ui.components.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState as rememberVerticalScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// =========================================================================================
// 1. DATA MODELS & NON-LINEAR ARCHITECTURE
// =========================================================================================

enum class TimelineTrackType {
  MAIN_VIDEO,
  OVERLAY_PIP,
  AUDIO,
  MUSIC,
  SFX,
  TEXT,
  CAPTION,
  EFFECT,
  STICKER,
  ADJUSTMENT,
  ELEMENT
}

@Immutable
data class TimelineClip(
  val id: String,
  val trackType: TimelineTrackType,
  val startMs: Long,
  val durationMs: Long,
  val contentUri: String? = null,
  val label: String = "",
  val color: Color? = null,
  val isMuted: Boolean = false,
  val speed: Float = 1.0f,
  val thumbnailUri: String? = null
) {
  val endMs: Long get() = startMs + durationMs
}

@Immutable
data class TrackLayer(
  val layerId: String,
  val trackType: TimelineTrackType,
  val subTrackIndex: Int,
  val clips: List<TimelineClip>,
  val isLocked: Boolean = false,
  val isMuted: Boolean = false
)

@Immutable
data class NonLinearTimelineState(
  val layers: List<TrackLayer>,
  val currentPositionMs: Long,
  val totalDurationMs: Long,
  val isPlaying: Boolean = false,
  val zoomFactor: Float = 1.0f,
  val fps: Int = 30,
  val selectedClipId: String? = null,
  val coverThumbnailUri: String? = null
)

/**
 * Professional Non-Linear Layer Stacking & Collision Resolution Algorithm:
 * Automatically distributes overlapping clips of the same track type into parallel stacked sub-tracks.
 */
object TimelineLayerResolver {
  fun resolveLayers(clips: List<TimelineClip>): List<TrackLayer> {
    val resultLayers = mutableListOf<TrackLayer>()
    val groupedByType = clips.groupBy { it.trackType }

    // Explicit rendering order: Text/Captions -> Stickers/Elements -> Effects/Adjustments -> Overlay PIP -> Main Video -> Audio/Music/SFX
    val typeOrder = listOf(
      TimelineTrackType.TEXT,
      TimelineTrackType.CAPTION,
      TimelineTrackType.STICKER,
      TimelineTrackType.ELEMENT,
      TimelineTrackType.EFFECT,
      TimelineTrackType.ADJUSTMENT,
      TimelineTrackType.OVERLAY_PIP,
      TimelineTrackType.MAIN_VIDEO,
      TimelineTrackType.AUDIO,
      TimelineTrackType.MUSIC,
      TimelineTrackType.SFX
    )
    val allTypes = (typeOrder + (groupedByType.keys - typeOrder.toSet())).distinct()

    for (type in allTypes) {
      val typeClips = (groupedByType[type] ?: emptyList()).sortedBy { it.startMs }
      if (typeClips.isEmpty() && type != TimelineTrackType.MAIN_VIDEO) continue

      // For MAIN_VIDEO, keep in single primary track; for others, stack dynamically if overlapping
      if (type == TimelineTrackType.MAIN_VIDEO) {
        resultLayers.add(
          TrackLayer(
            layerId = "layer_main_video_0",
            trackType = TimelineTrackType.MAIN_VIDEO,
            subTrackIndex = 0,
            clips = typeClips
          )
        )
      } else {
        // Sub-tracks bin-packing algorithm
        val subTracks = mutableListOf<MutableList<TimelineClip>>()
        for (clip in typeClips) {
          var placed = false
          for (track in subTracks) {
            val lastClipInTrack = track.lastOrNull()
            if (lastClipInTrack == null || clip.startMs >= lastClipInTrack.endMs) {
              track.add(clip)
              placed = true
              break
            }
          }
          if (!placed) {
            subTracks.add(mutableListOf(clip))
          }
        }

        if (subTracks.isEmpty()) {
          subTracks.add(mutableListOf())
        }

        subTracks.forEachIndexed { subIndex, trackClips ->
          resultLayers.add(
            TrackLayer(
              layerId = "layer_${type.name.lowercase()}_$subIndex",
              trackType = type,
              subTrackIndex = subIndex,
              clips = trackClips
            )
          )
        }
      }
    }

    return resultLayers
  }
}

// =========================================================================================
// 2. PRIMARY NON-LINEAR MULTI-TRACK TIMELINE COMPOSABLE
// =========================================================================================

/**
 * CapCut / Premiere Rush Style Non-Linear Multi-Track Timeline in Jetpack Compose.
 *
 * Key Architectural Highlights:
 * 1. FIXED PLAYHEAD NEEDLE: Stays static at exactly 38% viewport width; the track canvas scrolls underneath.
 * 2. COORDINATE MATH:
 *    - Playhead Screen X: `playheadAnchorPx = viewportWidth * 0.38f`
 *    - Track Content Padding: Left padding before 0ms is exactly `playheadAnchorPx`.
 *    - Dynamic Scroll Formula: `scrollOffset = (currentPositionMs * pixelsPerMs).roundToInt()`
 *    - Touch Scrub Formula: `targetMs = (scrollOffset / pixelsPerMs).toLong()`
 * 3. MOVING TRACK START HEADERS: Attached to track start (timestamp 0ms) and naturally move along with media clips.
 * 4. REAL-TIME 60 FPS SYNC: Prevents recomposition storms via derivedStateOf and Canvas draw calls.
 */
@Composable
fun NonLinearTimelineView(
  timelineState: NonLinearTimelineState,
  onSeek: (seekToMs: Long) -> Unit,
  onClipClick: (clipId: String) -> Unit,
  onSetCoverClick: () -> Unit,
  modifier: Modifier = Modifier,
  onPausePlayback: (() -> Unit)? = null,
  onSplitClip: ((clipId: String) -> Unit)? = null,
  onDeleteClip: ((clipId: String) -> Unit)? = null
) {
  val density = LocalDensity.current
  val coroutineScope = rememberCoroutineScope()
  val horizontalScrollState = rememberScrollState()
  val verticalTracksScrollState = rememberVerticalScrollState()

  var isUserScrubbing by remember { mutableStateOf(false) }
  val currentOnSeek by rememberUpdatedState(onSeek)
  val currentOnPause by rememberUpdatedState(onPausePlayback)

  // Zoom & Metric configuration
  val basePixelsPerSecond = 55f * timelineState.zoomFactor
  val pixelsPerMs = basePixelsPerSecond / 1000f
  val safeTotalDurationMs = max(timelineState.totalDurationMs, 1000L)
  val safeCurrentPositionMs = timelineState.currentPositionMs.coerceIn(0L, safeTotalDurationMs)

  // Fixed Playhead Horizontal Anchor Fraction (10% from left edge of screen)
  val playheadAnchorFraction = 0.1f

  // Timecode formatting strings
  val formattedCurrentTimecode by remember(safeCurrentPositionMs, timelineState.fps) {
    derivedStateOf { formatNleTimecode(safeCurrentPositionMs, timelineState.fps) }
  }
  val formattedTotalDuration by remember(safeTotalDurationMs, timelineState.fps) {
    derivedStateOf { formatNleTimecode(safeTotalDurationMs, timelineState.fps) }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(Color(0xFF0F1115))
      .testTag("nle_nonlinear_timeline_container")
  ) {
    // -------------------------------------------------------------------------------------
    // A. TOP TIME CONTROL & ACTION BAR (Timecode Badge & Edit Actions)
    // -------------------------------------------------------------------------------------
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF16181D))
        .padding(horizontal = 14.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // High-Contrast Primary Timecode Badge
      Surface(
        color = Color(0xFF00E5FF).copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = formattedCurrentTimecode,
            color = Color(0xFF00E5FF),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
          Text(
            text = " / $formattedTotalDuration",
            color = Color(0xFF8E99A8),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp
          )
        }
      }

      // Quick Action Badges (FPS / Split / Layer Count)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "${timelineState.fps} FPS",
          color = Color(0xFF7A8699),
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium
        )

        timelineState.selectedClipId?.let { selectedId ->
          Surface(
            color = Color(0xFF262C36),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.clickable { onSplitClip?.invoke(selectedId) }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.ContentCut,
                contentDescription = "Split Clip",
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(3.dp))
              Text("Split", color = Color.White, fontSize = 11.sp)
            }
          }
        }
      }
    }

    // -------------------------------------------------------------------------------------
    // B. MULTI-TRACK HORIZONTAL SCROLL AREA WITH STATIC FIXED PLAYHEAD
    // -------------------------------------------------------------------------------------
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      val viewportWidthPx = constraints.maxWidth.toFloat()
      val playheadAnchorPx = viewportWidthPx * playheadAnchorFraction
      val playheadAnchorDp = with(density) { playheadAnchorPx.toDp() }

      // Total physical width of media content in Dp
      val mediaContentWidthDp = with(density) { ((safeTotalDurationMs * pixelsPerMs)).toDp() }
      // Moving header width to the left of 0ms
      val headerAreaWidthDp = 56.dp
      val headerAreaWidthPx = with(density) { headerAreaWidthDp.toPx() }

      // Content width with safety padding so last millisecond can scroll under fixed needle
      val totalContentWidthDp = playheadAnchorDp + mediaContentWidthDp + with(density) { (viewportWidthPx - playheadAnchorPx + 300f).toDp() }

      // 1. Synchronize Playback -> Horizontal Scroll Offset (at 60 FPS)
      LaunchedEffect(safeCurrentPositionMs, timelineState.isPlaying, isUserScrubbing) {
        if (!isUserScrubbing) {
          val targetScrollPx = ((safeCurrentPositionMs * pixelsPerMs) - playheadAnchorPx).roundToInt()
          if (kotlin.math.abs(horizontalScrollState.value - targetScrollPx) > 1) {
            horizontalScrollState.scrollTo(targetScrollPx.coerceAtLeast(0))
          }
        }
      }

      // 2. Horizontally Scrolled Timeline Tracks Box
      Box(
        modifier = Modifier
          .fillMaxSize()
          .horizontalScroll(horizontalScrollState)
          .pointerInput(safeTotalDurationMs, pixelsPerMs, timelineState.isPlaying) {
            detectTapGestures(
              onPress = {
                currentOnPause?.invoke()
                tryAwaitRelease()
              }
            )
          }
          .pointerInput(safeTotalDurationMs, pixelsPerMs, timelineState.isPlaying) {
            detectDragGestures(
              onDragStart = { offset ->
                if (timelineState.isPlaying) {
                  currentOnPause?.invoke()
                }
                isUserScrubbing = true
                val currentScroll = horizontalScrollState.value
                val touchContentX = currentScroll + offset.x
                // Convert touch coordinate relative to 0ms point
                val targetMs = ((touchContentX - playheadAnchorPx) / pixelsPerMs).toLong().coerceIn(0L, safeTotalDurationMs)
                currentOnSeek(targetMs)
              },
              onDrag = { change, dragAmount ->
                change.consume()
                // Scrolling moves content underneath the fixed playhead
                val newScrollPx = (horizontalScrollState.value - dragAmount.x).coerceAtLeast(0f)
                val targetMs = (((newScrollPx + playheadAnchorPx) / pixelsPerMs).toLong()).coerceIn(0L, safeTotalDurationMs)
                currentOnSeek(targetMs)
              },
              onDragEnd = { isUserScrubbing = false },
              onDragCancel = { isUserScrubbing = false }
            )
          }
      ) {
        Column(
          modifier = Modifier
            .width(totalContentWidthDp)
            .fillMaxHeight()
        ) {
          // --- B1. Time Ruler (Moves horizontally with content) ---
          TimelineRulerSection(
            totalDurationMs = safeTotalDurationMs,
            pixelsPerSecond = basePixelsPerSecond,
            fps = timelineState.fps,
            leftPaddingDp = playheadAnchorDp,
            modifier = Modifier
              .fillMaxWidth()
              .height(28.dp)
          )

          Spacer(modifier = Modifier.height(6.dp))

          // --- B2. Stacked Multi-Tracks (Vertically Scrollable, Horizontally Locked) ---
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
              .verticalScroll(verticalTracksScrollState)
              .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            timelineState.layers.forEach { layer ->
              NonLinearTrackRow(
                layer = layer,
                pixelsPerMs = pixelsPerMs,
                leftPaddingDp = playheadAnchorDp,
                headerAreaWidthDp = headerAreaWidthDp,
                selectedClipId = timelineState.selectedClipId,
                coverThumbnailUri = timelineState.coverThumbnailUri,
                onClipClick = { clipId ->
                  if (timelineState.isPlaying) {
                    currentOnPause?.invoke()
                  }
                  onClipClick(clipId)
                },
                onSetCoverClick = {
                  if (timelineState.isPlaying) {
                    currentOnPause?.invoke()
                  }
                  onSetCoverClick()
                }
              )
            }
          }
        }
      }

      // -----------------------------------------------------------------------------------
      // C. STATIC FIXED PLAYHEAD NEEDLE (VIEWPORT ANCHORED)
      // -----------------------------------------------------------------------------------
      Box(
        modifier = Modifier
          .offset(x = playheadAnchorDp - 14.dp)
          .fillMaxHeight()
          .width(28.dp)
          .zIndex(100f)
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val centerX = size.width / 2f
          val handleWidth = 22.dp.toPx()
          val handleHeight = 24.dp.toPx()

          // 1. Playhead Top Polygon Badge (Pointing down directly into the track)
          val topHandlePath = Path().apply {
            moveTo(centerX - handleWidth / 2f, 0f)
            lineTo(centerX + handleWidth / 2f, 0f)
            lineTo(centerX + handleWidth / 2f, handleHeight * 0.65f)
            lineTo(centerX, handleHeight)
            lineTo(centerX - handleWidth / 2f, handleHeight * 0.65f)
            close()
          }

          // Top Badge Gradient
          drawPath(
            path = topHandlePath,
            brush = Brush.verticalGradient(
              colors = listOf(Color(0xFFFFFFFF), Color(0xFF00E5FF))
            )
          )

          // Playhead Center Core Line
          drawLine(
            color = Color(0xFF00E5FF),
            start = Offset(centerX, handleHeight),
            end = Offset(centerX, size.height),
            strokeWidth = 2.5.dp.toPx()
          )

          // Glowing Outer Stroke for Precision Contrast
          drawLine(
            color = Color(0xFF00E5FF).copy(alpha = 0.35f),
            start = Offset(centerX, handleHeight),
            end = Offset(centerX, size.height),
            strokeWidth = 6.dp.toPx()
          )
        }
      }
    }
  }
}

// =========================================================================================
// 3. NON-LINEAR TRACK ROW & ATTACHED MOVING START HEADERS
// =========================================================================================

/**
 * Individual horizontal track row with attached MOVING START INDICATORS that travel with media.
 */
@Composable
private fun NonLinearTrackRow(
  layer: TrackLayer,
  pixelsPerMs: Float,
  leftPaddingDp: Dp,
  headerAreaWidthDp: Dp,
  selectedClipId: String?,
  coverThumbnailUri: String?,
  onClipClick: (String) -> Unit,
  onSetCoverClick: () -> Unit
) {
  val trackHeight = when (layer.trackType) {
    TimelineTrackType.MAIN_VIDEO -> 68.dp
    TimelineTrackType.OVERLAY_PIP -> 52.dp
    TimelineTrackType.TEXT, TimelineTrackType.CAPTION -> 38.dp
    TimelineTrackType.AUDIO, TimelineTrackType.MUSIC, TimelineTrackType.SFX -> 42.dp
    TimelineTrackType.EFFECT, TimelineTrackType.STICKER, TimelineTrackType.ADJUSTMENT, TimelineTrackType.ELEMENT -> 34.dp
  }

  val isTrackSelected = layer.clips.any { it.id == selectedClipId }
  val trackScale by animateFloatAsState(
    targetValue = if (isTrackSelected) 1.01f else 1.0f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMediumLow
    ),
    label = "nlTrackScale"
  )
  val trackBorderColor by animateColorAsState(
    targetValue = if (isTrackSelected) Color(0xFF00E5FF).copy(alpha = 0.55f) else Color.Transparent,
    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
    label = "nlTrackBorderColor"
  )
  val trackBorderWidth by animateDpAsState(
    targetValue = if (isTrackSelected) 1.5.dp else 0.dp,
    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
    label = "nlTrackBorderWidth"
  )

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(trackHeight)
      .graphicsLayer {
        scaleX = trackScale
        scaleY = trackScale
      }
      .border(trackBorderWidth, trackBorderColor, RoundedCornerShape(4.dp))
  ) {
    // -------------------------------------------------------------------------------------
    // A. MOVING TRACK START INDICATOR (Attached to Track Start at 0ms, scrolls with media)
    // -------------------------------------------------------------------------------------
    val movingHeaderOffsetDp = leftPaddingDp - headerAreaWidthDp - 6.dp

    Box(
      modifier = Modifier
        .offset(x = movingHeaderOffsetDp)
        .width(headerAreaWidthDp)
        .fillMaxHeight()
        .padding(vertical = 2.dp)
    ) {
      when (layer.trackType) {
        TimelineTrackType.MAIN_VIDEO -> {
          // "Set Cover" Thumbnail Button (CapCut style moving cover box)
          SetCoverThumbnailButton(
            coverThumbnailUri = coverThumbnailUri ?: layer.clips.firstOrNull()?.thumbnailUri ?: layer.clips.firstOrNull()?.contentUri,
            onClick = onSetCoverClick,
            modifier = Modifier.fillMaxSize()
          )
        }
        TimelineTrackType.AUDIO, TimelineTrackType.MUSIC, TimelineTrackType.SFX -> {
          // Dynamic Audio / Speaker Badge
          val title = when (layer.trackType) {
            TimelineTrackType.MUSIC -> if (layer.subTrackIndex == 0) "Music" else "Music ${layer.subTrackIndex + 1}"
            TimelineTrackType.SFX -> if (layer.subTrackIndex == 0) "SFX" else "SFX ${layer.subTrackIndex + 1}"
            else -> if (layer.subTrackIndex == 0) "Audio" else "Audio ${layer.subTrackIndex + 1}"
          }
          TrackStartBadge(
            icon = if (layer.isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
            label = title,
            accentColor = Color(0xFF00E676),
            modifier = Modifier.fillMaxSize()
          )
        }
        TimelineTrackType.TEXT, TimelineTrackType.CAPTION -> {
          // Text / Caption Track Badge
          val title = if (layer.trackType == TimelineTrackType.CAPTION) {
            if (layer.subTrackIndex == 0) "Caption" else "Cap ${layer.subTrackIndex + 1}"
          } else {
            if (layer.subTrackIndex == 0) "Text" else "T ${layer.subTrackIndex + 1}"
          }
          TrackStartBadge(
            icon = Icons.Default.TextFields,
            label = title,
            accentColor = Color(0xFFFFD600),
            modifier = Modifier.fillMaxSize()
          )
        }
        TimelineTrackType.OVERLAY_PIP -> {
          // Overlay / PIP Badge
          TrackStartBadge(
            icon = Icons.Default.Layers,
            label = "PIP ${layer.subTrackIndex + 1}",
            accentColor = Color(0xFF7C4DFF),
            modifier = Modifier.fillMaxSize()
          )
        }
        TimelineTrackType.EFFECT, TimelineTrackType.STICKER, TimelineTrackType.ADJUSTMENT, TimelineTrackType.ELEMENT -> {
          // Effect / Sticker / Adjustment / Element Badge
          val label = when (layer.trackType) {
            TimelineTrackType.EFFECT -> "FX"
            TimelineTrackType.STICKER -> "Sticker"
            TimelineTrackType.ADJUSTMENT -> "Adj"
            TimelineTrackType.ELEMENT -> "Elem"
            else -> "Layer"
          }
          TrackStartBadge(
            icon = Icons.Default.AutoAwesome,
            label = label,
            accentColor = Color(0xFFFF4081),
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }

    // -------------------------------------------------------------------------------------
    // B. TRACK CLIPS CONTAINER (Starts strictly at 0ms / leftPaddingDp)
    // -------------------------------------------------------------------------------------
    layer.clips.forEach { clip ->
      val clipStartOffsetDp = leftPaddingDp + with(LocalDensity.current) { ((clip.startMs * pixelsPerMs)).toDp() }
      val clipWidthDp = max(with(LocalDensity.current) { ((clip.durationMs * pixelsPerMs)).toDp() }, 36.dp)
      val isSelected = clip.id == selectedClipId

      TimelineClipBlock(
        clip = clip,
        widthDp = clipWidthDp,
        offsetDp = clipStartOffsetDp,
        isSelected = isSelected,
        onClick = { onClipClick(clip.id) }
      )
    }
  }
}

/**
 * "Set Cover" Thumbnail Button (Attached to Main Video Track Start).
 */
@Composable
private fun SetCoverThumbnailButton(
  coverThumbnailUri: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  Surface(
    color = Color(0xFF1E222B),
    shape = RoundedCornerShape(8.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .testTag("track_set_cover_btn")
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      if (!coverThumbnailUri.isNullOrBlank()) {
        AsyncImage(
          model = ImageRequest.Builder(context)
            .data(coverThumbnailUri)
            .crossfade(true)
            .build(),
          contentDescription = "Cover Thumbnail",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        Icon(
          imageVector = Icons.Default.PhotoLibrary,
          contentDescription = "Cover Placeholder",
          tint = Color(0xFF00E5FF),
          modifier = Modifier
            .size(20.dp)
            .align(Alignment.Center)
        )
      }

      // "Cover" bottom gradient label
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .background(Color.Black.copy(alpha = 0.75f))
          .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "Cover",
          color = Color(0xFF00E5FF),
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

/**
 * Generic Moving Track Start Indicator Badge for Audio, Text, and Overlay tracks.
 */
@Composable
private fun TrackStartBadge(
  icon: ImageVector,
  label: String,
  accentColor: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    color = Color(0xFF191C22),
    shape = RoundedCornerShape(8.dp),
    border = androidx.compose.foundation.BorderStroke(0.75.dp, accentColor.copy(alpha = 0.4f)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(2.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = accentColor,
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = label,
        color = Color(0xFFC0C7D5),
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

// =========================================================================================
// 4. TIMELINE CLIP BLOCK COMPOSABLE
// =========================================================================================

/**
 * Individual Clip Block with filmstrip thumbnails, waveforms, or color blocks.
 */
@Composable
private fun TimelineClipBlock(
  clip: TimelineClip,
  widthDp: Dp,
  offsetDp: Dp,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val context = LocalContext.current
  val density = LocalDensity.current

  val defaultColor = when (clip.trackType) {
    TimelineTrackType.MAIN_VIDEO -> Color(0xFF1565C0)
    TimelineTrackType.OVERLAY_PIP -> Color(0xFF6A1B9A)
    TimelineTrackType.AUDIO -> Color(0xFF2E7D32)
    TimelineTrackType.MUSIC -> Color(0xFF00796B)
    TimelineTrackType.SFX -> Color(0xFF388E3C)
    TimelineTrackType.TEXT -> Color(0xFFC62828)
    TimelineTrackType.CAPTION -> Color(0xFF1976D2)
    TimelineTrackType.EFFECT -> Color(0xFFAD1457)
    TimelineTrackType.STICKER -> Color(0xFFEF6C00)
    TimelineTrackType.ADJUSTMENT -> Color(0xFF7B1FA2)
    TimelineTrackType.ELEMENT -> Color(0xFFC2185B)
  }

  val baseColor = clip.color ?: defaultColor

  Box(
    modifier = Modifier
      .offset(x = offsetDp)
      .width(widthDp)
      .fillMaxHeight()
      .padding(vertical = 2.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(baseColor.copy(alpha = 0.85f))
      .border(
        width = if (isSelected) 2.5.dp else 1.dp,
        color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.25f),
        shape = RoundedCornerShape(8.dp)
      )
      .clickable(onClick = onClick)
      .testTag("clip_item_${clip.id}")
  ) {
    // A. Main Video Filmstrip Thumbnail Strip
    if (clip.trackType == TimelineTrackType.MAIN_VIDEO && !clip.contentUri.isNullOrBlank()) {
      val thumbCount = max(1, (with(density) { widthDp.toPx() } / 110f).roundToInt())
      Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Start
      ) {
        for (i in 0 until thumbCount) {
          AsyncImage(
            model = ImageRequest.Builder(context)
              .data(clip.thumbnailUri ?: clip.contentUri)
              .crossfade(true)
              .build(),
            contentDescription = "Filmstrip Frame",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .border(0.25.dp, Color.Black.copy(alpha = 0.35f))
          )
        }
      }
    }

    // B. Audio Waveform Decorative Canvas
    if (clip.trackType == TimelineTrackType.AUDIO) {
      Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp)) {
        val barCount = (size.width / 6.dp.toPx()).toInt()
        val centerY = size.height / 2f
        for (b in 0 until barCount) {
          val barX = b * 6.dp.toPx()
          val heightFactor = ((kotlin.math.sin(b * 0.7) * 0.4 + 0.6) * centerY * 0.85).toFloat()
          drawLine(
            color = Color(0xFFB9F6CA),
            start = Offset(barX, centerY - heightFactor),
            end = Offset(barX, centerY + heightFactor),
            strokeWidth = 2.5.dp.toPx()
          )
        }
      }
    }

    // C. Clip Label Header
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent, Color.Black.copy(alpha = 0.5f))
          )
        )
        .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
      Text(
        text = if (clip.label.isNotBlank()) clip.label else clip.id,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.TopStart)
      )
    }
  }
}

// =========================================================================================
// 5. HIGH PERFORMANCE TIME RULER CANVAS
// =========================================================================================

/**
 * High-precision Time Ruler Canvas that aligns with the leftPadding offset.
 */
@Composable
private fun TimelineRulerSection(
  totalDurationMs: Long,
  pixelsPerSecond: Float,
  fps: Int,
  leftPaddingDp: Dp,
  modifier: Modifier = Modifier
) {
  val density = LocalDensity.current
  val leftPaddingPx = with(density) { leftPaddingDp.toPx() }

  Canvas(modifier = modifier) {
    val totalSeconds = (totalDurationMs / 1000f).toInt() + 15
    val tickColor = Color(0xFF636E7B)
    val textPaint = android.graphics.Paint().apply {
      color = android.graphics.Color.parseColor("#8E99A8")
      textSize = 10.sp.toPx()
      isAntiAlias = true
      typeface = android.graphics.Typeface.MONOSPACE
    }

    for (sec in 0..totalSeconds) {
      val startX = leftPaddingPx + (sec * pixelsPerSecond)

      // Major second tick mark
      drawLine(
        color = tickColor,
        start = Offset(startX, size.height - 10.dp.toPx()),
        end = Offset(startX, size.height),
        strokeWidth = 1.5.dp.toPx()
      )

      // Timecode label (00:01, 00:02, etc.)
      val timeLabel = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
      drawContext.canvas.nativeCanvas.drawText(
        timeLabel,
        startX + 4.dp.toPx(),
        size.height - 3.dp.toPx(),
        textPaint
      )

      // Quarter-second minor sub-ticks
      for (sub in 1..3) {
        val subX = startX + (sub * (pixelsPerSecond / 4f))
        drawLine(
          color = tickColor.copy(alpha = 0.35f),
          start = Offset(subX, size.height - 5.dp.toPx()),
          end = Offset(subX, size.height),
          strokeWidth = 1.dp.toPx()
        )
      }
    }
  }
}

// =========================================================================================
// 6. TIMECODE FORMATTER UTILITY
// =========================================================================================

/**
 * Formats milliseconds into standard NLE timecode format: MM:SS:FF.
 */
fun formatNleTimecode(ms: Long, fps: Int = 30): String {
  val safeMs = max(0L, ms)
  val totalSec = safeMs / 1000L
  val minutes = totalSec / 60L
  val seconds = totalSec % 60L
  val remMs = safeMs % 1000L
  val frames = ((remMs * fps) / 1000L).coerceIn(0L, (fps - 1).toLong())
  return String.format(Locale.US, "%02d:%02d:%02d", minutes, seconds, frames)
}
