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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.domain.model.Timeline
import com.example.domain.model.TrackType
import com.example.domain.model.Transition
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import com.example.domain.model.TextClip
import com.example.domain.model.AudioClip
import com.example.domain.model.EffectClip
import com.example.domain.model.StickerClip
import com.example.engine.SelectedTrackElement
import com.example.ui.components.formatDurationShort
import com.example.ui.theme.*

/**
 * Principal Production-Ready Multi-Track NLE Timeline Component.
 *
 * Implements:
 * 1. Main Track Isolation (Lane 0 at top, 60dp).
 * 2. True Dynamic Infinite Sub-Tracks (Lanes 1, 2, 3... at 36dp).
 * 3. Exact 2mm (~6dp) vertical track spacing.
 * 4. Zero Dead Space layout auto-wrapping active lane count.
 * 5. Centered CTI Playhead with real-time scrubbing and instant pause on tap.
 * 6. Dynamic Left Header Indicators that auto-collapse when sub-tracks are deleted.
 * 7. 2dp crisp white selection boundary and stretch handles.
 */
@Composable
fun MultiTrackTimeline(
  timeline: Timeline,
  currentPosMs: Long,
  zoom: Float,
  selectedElement: SelectedTrackElement,
  selectedClipIds: Set<String>,
  isMultiSelectMode: Boolean,
  snapIndicatorMs: Long?,
  onSeek: (Long) -> Unit,
  onSelectElement: (SelectedTrackElement) -> Unit,
  onToggleClipSelection: (String) -> Unit,
  onZoomChange: (Float) -> Unit,
  onMoveClip: (clipId: String, deltaMs: Long) -> Unit,
  onMoveClipStart: ((clipId: String) -> Unit)? = null,
  onMoveClipEnd: ((clipId: String) -> Unit)? = null,
  onTrimClipLeft: (clipId: String, deltaMs: Long) -> Unit,
  onTrimClipLeftStart: ((clipId: String) -> Unit)? = null,
  onTrimClipLeftEnd: ((clipId: String) -> Unit)? = null,
  onTrimClipRight: (clipId: String, deltaMs: Long) -> Unit,
  onTrimClipRightStart: ((clipId: String) -> Unit)? = null,
  onTrimClipRightEnd: ((clipId: String) -> Unit)? = null,
  onToggleTrackLock: (TrackType) -> Unit,
  onToggleTrackHide: (TrackType) -> Unit,
  onToggleTrackMute: (TrackType) -> Unit,
  onToggleTrackSolo: (TrackType) -> Unit,
  onCycleTrackHeight: (TrackType) -> Unit,
  onReorderVideoClips: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
  onOpenTrimTool: (() -> Unit)? = null,
  onOpenKeyframeTool: (() -> Unit)? = null,
  onOpenTransitionsTool: (() -> Unit)? = null,
  selectedTransitionCutIndex: Int = 0,
  onSelectTransitionCut: ((Int) -> Unit)? = null,
  draggedTransitionType: TransitionType? = null,
  onDropTransition: ((cutIndex: Int, type: TransitionType) -> Unit)? = null,
  onSplitClip: (() -> Unit)? = null,
  isPlaying: Boolean = false,
  onTogglePlayPause: (() -> Unit)? = null,
  onTrimLeftToPlayhead: (() -> Unit)? = null,
  onTrimRightToPlayhead: (() -> Unit)? = null,
  onDeleteClip: (() -> Unit)? = null,
  onRippleDelete: (() -> Unit)? = null,
  onNormalDelete: (() -> Unit)? = null,
  onDuplicateClip: (() -> Unit)? = null,
  onCopyClip: (() -> Unit)? = null,
  onPasteClip: (() -> Unit)? = null,
  onToggleMultiSelect: (() -> Unit)? = null,
  onNextPeak: (() -> Unit)? = null,
  onPrevPeak: (() -> Unit)? = null,
  onNextSilence: (() -> Unit)? = null,
  onPrevSilence: (() -> Unit)? = null,
  onRemoveSilence: (() -> Unit)? = null,
  waveformStyle: WaveformStyle = WaveformStyle.MIRRORED_BARS,
  onToggleWaveformStyle: (() -> Unit)? = null,
  fps: Int = 30,
  isFrameSnapping: Boolean = false,
  onStepFrames: ((Int) -> Unit)? = null,
  onSeekToPrevCut: (() -> Unit)? = null,
  onSeekToNextCut: (() -> Unit)? = null,
  onFpsChange: ((Int) -> Unit)? = null,
  onToggleFrameSnapping: (() -> Unit)? = null,
  showTrackHeaders: Boolean = true,
  selectedKeyframeIds: Set<String> = emptySet(),
  onSelectKeyframe: ((String) -> Unit)? = null,
  onMoveKeyframe: ((String, Long) -> Unit)? = null,
  onAddAudioKeyframe: ((clipId: String, relTimeMs: Long, volume: Float) -> Unit)? = null,
  onUpdateAudioKeyframe: ((clipId: String, keyframeId: String, relTimeMs: Long, volume: Float) -> Unit)? = null,
  onDeleteAudioKeyframe: ((clipId: String, keyframeId: String) -> Unit)? = null,
  onAddMedia: (() -> Unit)? = null,
  onAddAudio: (() -> Unit)? = null,
  onAddText: (() -> Unit)? = null,
  onAddOverlay: (() -> Unit)? = null,
  onAddSticker: (() -> Unit)? = null,
  onAddEffect: (() -> Unit)? = null,
  onEditCover: (() -> Unit)? = null,
  onToggleMuteAllVideo: (() -> Unit)? = null,
  isTracksSyncEnabled: Boolean = true,
  onToggleTracksSync: (() -> Unit)? = null,
  onMoveToPlayhead: (() -> Unit)? = null,
  onSplitAllTracks: (() -> Unit)? = null,
  onScrubStart: () -> Unit = {},
  onScrubStop: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val horizontalScrollState = rememberScrollState()
  val verticalScrollState = rememberScrollState()
  val density = LocalDensity.current

  // 1. Dynamic Lane Allocation via TrackLaneManager
  val activeLanes = remember(timeline) { TrackLaneManager.computeLanes(timeline) }
  val totalDuration = timeline.totalDurationMs
  val msPerPixel = remember(zoom) { (20f / zoom).coerceIn(1.25f, 120f) }

  val maxTimelineMs = remember(timeline, totalDuration) {
    if (timeline.videoClips.isNotEmpty()) {
      (timeline.videoClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L).coerceAtLeast(100L)
    } else {
      maxOf(
        totalDuration,
        timeline.overlayClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
        timeline.audioClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
        timeline.textClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
        timeline.stickerClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
        timeline.effectClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
      ).coerceAtLeast(1000L)
    }
  }

  val trackContentWidthDp = (maxTimelineMs / msPerPixel).dp
  val computedTimelineHeightDp = remember(activeLanes) { TrackLaneManager.calculateTotalTimelineHeight(activeLanes) }

  var isMutedAll by remember { mutableStateOf(false) }

  // Touch Scrubbing & Drag gesture state
  var isTouchScrubbing by remember { mutableStateOf(false) }
  var isPinching by remember { mutableStateOf(false) }
  var scrubAccumulatorMs by remember { mutableFloatStateOf(currentPosMs.toFloat()) }

  LaunchedEffect(isPinching, zoom) {
    if (isPinching) {
      delay(1200)
      isPinching = false
    }
  }

  LaunchedEffect(currentPosMs) {
    if (!isTouchScrubbing) {
      scrubAccumulatorMs = currentPosMs.toFloat()
    }
  }

  // Keep scroll position strictly synchronized with currentPosMs (moves timeline underneath fixed center CTI)
  LaunchedEffect(currentPosMs, msPerPixel, density) {
    val safePosMs = currentPosMs.coerceAtLeast(0L)
    val targetScrollPx = with(density) { ((safePosMs / msPerPixel).dp).roundToPx() }.coerceAtLeast(0)
    if (kotlin.math.abs(horizontalScrollState.value - targetScrollPx) > 1) {
      horizontalScrollState.scrollTo(targetScrollPx)
    }
  }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .background(Color.Black)
  ) {
    val hasAnyTrack = activeLanes.any { it.hasClips } || timeline.videoClips.isNotEmpty()
    val leftColumnWidthDp = if (hasAnyTrack) 88.dp else 0.dp
    val timelineViewportWidthDp = (maxWidth - leftColumnWidthDp).coerceAtLeast(100.dp)
    val centerPaddingDp = timelineViewportWidthDp / 2
    val shift15mmDp = (15f * 160f / 25.4f).dp
    val ctiOffsetDp = (centerPaddingDp - shift15mmDp).coerceAtLeast(0.dp)
    val leftPaddingDp = ctiOffsetDp
    val rightPaddingDp = timelineViewportWidthDp - ctiOffsetDp

    Box(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize()) {
        // 1. Timecode & Ruler Bar
        TimelineRulerHeader(
          hasAnyTrack = hasAnyTrack,
          isPlaying = isPlaying,
          currentPosMs = currentPosMs,
          totalDurationMs = timeline.totalDurationMs,
          maxTimelineMs = maxTimelineMs,
          msPerPixel = msPerPixel,
          zoom = zoom,
          onZoomChange = onZoomChange,
          onPinchStart = { isPinching = true },
          onPinchEnd = { isPinching = false },
          fps = fps,
          isFrameSnapping = isFrameSnapping,
          leftPaddingDp = leftPaddingDp,
          rightPaddingDp = rightPaddingDp,
          horizontalScrollState = horizontalScrollState,
          hasRightAddButton = false,
          onTogglePlayPause = onTogglePlayPause,
          onSeek = onSeek,
          onSeekToNextCut = onSeekToNextCut,
          onScrubStart = onScrubStart,
          onScrubStop = onScrubStop,
          onAddMedia = onAddMedia,
          timeline = timeline
        )

        if (!hasAnyTrack) {
          TimelineEmptyView(
            onAddMedia = onAddMedia,
            modifier = Modifier.weight(1f).fillMaxWidth()
          )
        } else {
          // 2. Main Multi-Track Lanes Container (Zero dead space, wrapping computed height)
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
          ) {
            Row(modifier = Modifier.fillMaxSize()) {
              // Left Static Header Column (Synchronized vertically with tracks)
              DynamicLaneHeadersColumn(
                lanes = activeLanes,
                isMutedAll = isMutedAll,
                verticalScrollState = verticalScrollState,
                onToggleMuteAll = {
                  isMutedAll = !isMutedAll
                  onToggleTrackMute(TrackType.MAIN_VIDEO)
                  onToggleMuteAllVideo?.invoke()
                },
                onEditCover = onEditCover
              )

              // Right Horizontally Scrollable Multi-Lane Tracks Area
              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxHeight()
                  .pointerInput(totalDuration, msPerPixel, isFrameSnapping, fps, density) {
                    detectDragGestures(
                      onDragStart = {
                        isTouchScrubbing = true
                        scrubAccumulatorMs = currentPosMs.toFloat()
                        onScrubStart()
                      },
                      onDragEnd = {
                        isTouchScrubbing = false
                        onScrubStop()
                      },
                      onDragCancel = {
                        isTouchScrubbing = false
                        onScrubStop()
                      },
                      onDrag = { change, dragAmount ->
                        change.consume()
                        val dragAmountDp = dragAmount.x / density.density
                        val deltaMs = -dragAmountDp * msPerPixel
                        scrubAccumulatorMs = (scrubAccumulatorMs + deltaMs).coerceIn(0f, maxTimelineMs.toFloat())
                        val rawMs = scrubAccumulatorMs.toLong()
                        val targetMs = if (isFrameSnapping) {
                          val frameMs = 1000.0 / fps
                          (Math.round(rawMs / frameMs) * frameMs).toLong().coerceIn(0L, maxTimelineMs)
                        } else rawMs
                        onSeek(targetMs)
                      }
                    )
                  }
                  .pointerInput(zoom) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                      if (kotlin.math.abs(zoomChange - 1f) > 0.005f) {
                        isPinching = true
                        onZoomChange((zoom * zoomChange).coerceIn(0.25f, 8.0f))
                      }
                    }
                  }
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
                ) {
                  Column(
                    modifier = Modifier
                      .width(trackContentWidthDp + leftPaddingDp + rightPaddingDp)
                      .fillMaxHeight()
                      .verticalScroll(verticalScrollState)
                      .pointerInput(maxTimelineMs, msPerPixel, density, ctiOffsetDp) {
                        detectTapGestures { offset ->
                          if (isPlaying) {
                            onTogglePlayPause?.invoke() // Instant pause on tap
                          }
                          val ctiOffsetPx = with(density) { ctiOffsetDp.toPx() }
                          val timePx = offset.x - ctiOffsetPx
                          val timeDp = timePx / density.density
                          val clickedMs = (timeDp * msPerPixel).toLong().coerceIn(0L, maxTimelineMs)
                          onSeek(clickedMs)
                        }
                      },
                    verticalArrangement = Arrangement.spacedBy(TrackLaneManager.TRACK_VERTICAL_GAP)
                  ) {
                    // Render each active lane dynamically with strict 2mm (~6dp) gap & hierarchy
                    for (lane in activeLanes) {
                      DynamicLaneRow(
                        lane = lane,
                        currentPosMs = currentPosMs,
                        msPerPixel = msPerPixel,
                        leftPaddingDp = leftPaddingDp,
                        selectedElement = selectedElement,
                        selectedClipIds = selectedClipIds,
                        isMultiSelectMode = isMultiSelectMode,
                        isMutedAll = isMutedAll,
                        isPlaying = isPlaying,
                        onTogglePlayPause = onTogglePlayPause,
                        onSelectElement = onSelectElement,
                        onToggleClipSelection = onToggleClipSelection,
                        onMoveClip = onMoveClip,
                        onMoveClipStart = onMoveClipStart,
                        onMoveClipEnd = onMoveClipEnd,
                        onTrimClipLeft = onTrimClipLeft,
                        onTrimClipLeftStart = onTrimClipLeftStart,
                        onTrimClipLeftEnd = onTrimClipLeftEnd,
                        onTrimClipRight = onTrimClipRight,
                        onTrimClipRightStart = onTrimClipRightStart,
                        onTrimClipRightEnd = onTrimClipRightEnd,
                        onSeek = onSeek,
                        onScrubStart = onScrubStart,
                        onScrubStop = onScrubStop,
                        selectedKeyframeIds = selectedKeyframeIds,
                        onSelectKeyframe = onSelectKeyframe,
                        onMoveKeyframe = onMoveKeyframe,
                        onAddMedia = onAddMedia,
                        onAddText = onAddText,
                        onAddAudio = onAddAudio,
                        onAddOverlay = onAddOverlay,
                        onAddSticker = onAddSticker,
                        onAddEffect = onAddEffect
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // ==========================================
      // FIXED CENTER CTI OVERLAY (Stationed Center Playhead)
      // ==========================================
      if (hasAnyTrack) {
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = leftColumnWidthDp)
            .width(timelineViewportWidthDp)
            .fillMaxHeight()
        ) {
          // Vertical Center Playhead Line
          Box(
            modifier = Modifier
              .align(Alignment.Center)
              .offset(x = -shift15mmDp)
              .fillMaxHeight()
              .width(2.5.dp)
              .background(
                Brush.verticalGradient(
                  listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF007AFF),
                    Color(0xFF0052CC)
                  )
                )
              )
              .testTag("fixed_center_playhead_line")
          )

          // CTI Top Needle Cap
          Box(
            modifier = Modifier
              .align(Alignment.TopCenter)
              .offset(x = -shift15mmDp, y = 0.dp)
              .width(13.dp)
              .height(18.dp)
              .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp, topStart = 3.dp, topEnd = 3.dp))
              .background(Color(0xFF0A0D14))
              .border(
                width = 1.25.dp,
                color = Color(0xFF0088FF),
                shape = RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp, topStart = 3.dp, topEnd = 3.dp)
              )
              .testTag("fixed_center_playhead_cap"),
            contentAlignment = Alignment.Center
          ) {
            Box(
              modifier = Modifier
                .width(2.dp)
                .height(10.dp)
                .background(Color(0xFF00E5FF))
            )
          }

          // Frame preview badge on scrub
          if (isTouchScrubbing || isPinching) {
            Box(
              modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = -shift15mmDp, y = 2.dp)
            ) {
              CTIFramePreviewCard(
                timeline = timeline,
                currentPosMs = currentPosMs,
                fps = fps,
                isScrubbing = isTouchScrubbing
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Renders a single horizontal dynamic lane row.
 */
@Composable
private fun DynamicLaneRow(
  lane: TimelineLane,
  currentPosMs: Long,
  msPerPixel: Float,
  leftPaddingDp: Dp,
  selectedElement: SelectedTrackElement,
  selectedClipIds: Set<String>,
  isMultiSelectMode: Boolean,
  isMutedAll: Boolean,
  isPlaying: Boolean,
  onTogglePlayPause: (() -> Unit)?,
  onSelectElement: (SelectedTrackElement) -> Unit,
  onToggleClipSelection: (String) -> Unit,
  onMoveClip: (clipId: String, deltaMs: Long) -> Unit,
  onMoveClipStart: ((clipId: String) -> Unit)?,
  onMoveClipEnd: ((clipId: String) -> Unit)?,
  onTrimClipLeft: (clipId: String, deltaMs: Long) -> Unit,
  onTrimClipLeftStart: ((clipId: String) -> Unit)?,
  onTrimClipLeftEnd: ((clipId: String) -> Unit)?,
  onTrimClipRight: (clipId: String, deltaMs: Long) -> Unit,
  onTrimClipRightStart: ((clipId: String) -> Unit)?,
  onTrimClipRightEnd: ((clipId: String) -> Unit)?,
  onSeek: (Long) -> Unit,
  onScrubStart: () -> Unit,
  onScrubStop: () -> Unit,
  selectedKeyframeIds: Set<String>,
  onSelectKeyframe: ((String) -> Unit)?,
  onMoveKeyframe: ((String, Long) -> Unit)?,
  onAddMedia: (() -> Unit)?,
  onAddText: (() -> Unit)?,
  onAddAudio: (() -> Unit)?,
  onAddOverlay: (() -> Unit)?,
  onAddSticker: (() -> Unit)?,
  onAddEffect: (() -> Unit)?
) {
  val laneHeight = lane.heightDp

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(laneHeight)
      .testTag("timeline_lane_${lane.laneIndex}_${lane.kind.name.lowercase()}")
  ) {
    Box(
      modifier = Modifier
        .fillMaxHeight()
        .offset(x = leftPaddingDp)
    ) {
      // 1. Lane Clips Rendering
      for (clip in lane.clips) {
        val isSelected = when (lane.kind) {
          LaneKind.MAIN_VIDEO -> (selectedElement as? SelectedTrackElement.Video)?.clipId == clip.id
          LaneKind.OVERLAY -> (selectedElement as? SelectedTrackElement.Overlay)?.clipId == clip.id
          LaneKind.TEXT -> (selectedElement as? SelectedTrackElement.Text)?.clipId == clip.id
          LaneKind.AUDIO -> (selectedElement as? SelectedTrackElement.Audio)?.clipId == clip.id
          LaneKind.EFFECT -> (selectedElement as? SelectedTrackElement.Effect)?.clipId == clip.id
          LaneKind.FILTER -> false
          LaneKind.STICKER -> (selectedElement as? SelectedTrackElement.Sticker)?.clipId == clip.id
        }
        val isMulti = clip.id in selectedClipIds

        TimelineClipView(
          clipId = clip.id,
          title = clip.title,
          timelineStartMs = clip.startMs,
          durationMs = clip.durationMs,
          sourceStartMs = (clip.rawClip as? VideoClip)?.sourceStartMs ?: (clip.rawClip as? AudioClip)?.sourceStartMs ?: 0L,
          sourceEndMs = (clip.rawClip as? VideoClip)?.sourceEndMs ?: (clip.rawClip as? AudioClip)?.sourceEndMs ?: clip.durationMs,
          currentPlayheadMs = currentPosMs,
          hasAudio = (clip.rawClip as? VideoClip)?.hasAudio == true || lane.kind == LaneKind.AUDIO,
          isMuted = clip.isMuted || (lane.isMainLane && isMutedAll),
          trackColor = lane.kind.accentColor,
          heightDp = laneHeight,
          msPerPixel = msPerPixel,
          isSelected = isSelected,
          isMultiSelected = isMulti,
          isLocked = clip.isLocked,
          speed = clip.speed,
          isReversed = (clip.rawClip as? VideoClip)?.isReversed ?: false,
          isFreeze = false,
          keyframes = (clip.rawClip as? VideoClip)?.keyframes ?: (clip.rawClip as? TextClip)?.keyframes ?: (clip.rawClip as? AudioClip)?.keyframes ?: emptyList(),
          selectedKeyframeIds = selectedKeyframeIds,
          onSelectKeyframe = onSelectKeyframe,
          onMoveKeyframe = onMoveKeyframe,
          onSelect = {
            if (isPlaying) onTogglePlayPause?.invoke() // Instant pause on tap
            if (isMultiSelectMode) {
              onToggleClipSelection(clip.id)
            } else {
              val element = when (lane.kind) {
                LaneKind.MAIN_VIDEO -> SelectedTrackElement.Video(clip.id)
                LaneKind.OVERLAY -> SelectedTrackElement.Overlay(clip.id)
                LaneKind.TEXT -> SelectedTrackElement.Text(clip.id)
                LaneKind.AUDIO -> SelectedTrackElement.Audio(clip.id)
                LaneKind.EFFECT -> SelectedTrackElement.Effect(clip.id)
                LaneKind.FILTER -> SelectedTrackElement.None
                LaneKind.STICKER -> SelectedTrackElement.Sticker(clip.id)
              }
              onSelectElement(element)
            }
          },
          onSeekToPosition = { posMs -> onSeek(posMs) },
          onLongClick = { onToggleClipSelection(clip.id) },
          onMoveClip = { delta -> onMoveClip(clip.id, delta) },
          onScrub = { delta -> onSeek((currentPosMs + delta).coerceAtLeast(0L)) },
          onScrubStart = onScrubStart,
          onScrubStop = onScrubStop,
          onMoveClipStart = { onMoveClipStart?.invoke(clip.id) },
          onMoveClipEnd = { onMoveClipEnd?.invoke(clip.id) },
          onTrimLeft = { delta -> onTrimClipLeft(clip.id, delta) },
          onTrimLeftStart = { onTrimClipLeftStart?.invoke(clip.id) },
          onTrimLeftEnd = { onTrimClipLeftEnd?.invoke(clip.id) },
          onTrimRight = { delta -> onTrimClipRight(clip.id, delta) },
          onTrimRightStart = { onTrimClipRightStart?.invoke(clip.id) },
          onTrimRightEnd = { onTrimClipRightEnd?.invoke(clip.id) },
          isVideoClip = lane.isMainLane,
          uri = clip.uri,
          isVideo = (clip.rawClip as? VideoClip)?.isVideo ?: false
        )
      }

      // 2. Add Element Quick Pill at end of track sequence
      if (!lane.isMainLane && lane.clips.isNotEmpty()) {
        val maxEndMs = lane.clips.maxOfOrNull { it.endMs } ?: 0L
        val addOffset = (maxEndMs / msPerPixel).dp + 8.dp

        val onAddAction = when (lane.kind) {
          LaneKind.TEXT -> onAddText
          LaneKind.AUDIO -> onAddAudio
          LaneKind.OVERLAY -> onAddOverlay
          LaneKind.STICKER -> onAddSticker
          LaneKind.EFFECT -> onAddEffect
          else -> null
        }

        if (onAddAction != null) {
          Box(
            modifier = Modifier
              .offset(x = addOffset)
              .align(Alignment.CenterStart)
          ) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = Color(0xFF1E222D),
              border = BorderStroke(1.dp, Color(0xFF2D3344)),
              modifier = Modifier
                .width(115.dp)
                .height(30.dp)
                .clickable { onAddAction.invoke() }
                .testTag("add_lane_${lane.laneIndex}_pill_btn")
            ) {
              Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Add ${lane.kind.displayName.lowercase()}",
                  style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Medium, fontSize = 11.sp),
                  maxLines = 1
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
 * Left Static Column showing dedicated Track Header Icons that dynamically auto-collapse.
 */
@Composable
private fun DynamicLaneHeadersColumn(
  lanes: List<TimelineLane>,
  isMutedAll: Boolean,
  verticalScrollState: androidx.compose.foundation.ScrollState,
  onToggleMuteAll: () -> Unit,
  onEditCover: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .width(88.dp)
      .fillMaxHeight()
      .background(Color.Black)
      .padding(horizontal = 6.dp)
      .verticalScroll(verticalScrollState),
    verticalArrangement = Arrangement.spacedBy(TrackLaneManager.TRACK_VERTICAL_GAP)
  ) {
    for (lane in lanes) {
      if (lane.isMainLane) {
        // Main Track Header (Mute all + Cover button)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(TrackLaneManager.MAIN_LANE_HEIGHT),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(
            modifier = Modifier
              .width(36.dp)
              .clickable { onToggleMuteAll() }
              .testTag("mute_clip_btn"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = if (isMutedAll) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
              contentDescription = "Mute clip",
              tint = if (isMutedAll) RedAccent else Color.White.copy(alpha = 0.85f),
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
              text = "Mute\nclip",
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.5.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                lineHeight = 10.sp
              )
            )
          }

          Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF222630),
            border = BorderStroke(1.dp, Color(0xFF333A4A)),
            modifier = Modifier
              .width(36.dp)
              .height(46.dp)
              .clickable { onEditCover?.invoke() }
              .testTag("cover_thumbnail_btn")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Cover",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(15.dp)
              )
              Text(
                text = "Cover",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 8.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
              )
            }
          }
        }
      } else {
        // Sub-track Dynamic Header Card
        DynamicTrackHeaderCell(
          height = lane.heightDp,
          icon = lane.icon,
          label = lane.label,
          tint = lane.kind.accentColor
        )
      }
    }
  }
}

@Composable
private fun DynamicTrackHeaderCell(
  height: Dp,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  tint: Color
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(height)
      .clip(RoundedCornerShape(6.dp))
      .background(Color(0xFF1B1F2A))
      .border(0.5.dp, Color(0xFF2E3547), RoundedCornerShape(6.dp)),
    contentAlignment = Alignment.Center
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier.size(14.dp)
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = tint
        ),
        maxLines = 1
      )
    }
  }
}

@Composable
private fun TimelineRulerHeader(
  hasAnyTrack: Boolean,
  isPlaying: Boolean,
  currentPosMs: Long,
  totalDurationMs: Long,
  maxTimelineMs: Long,
  msPerPixel: Float,
  zoom: Float,
  onZoomChange: (Float) -> Unit,
  onPinchStart: () -> Unit,
  onPinchEnd: () -> Unit,
  fps: Int,
  isFrameSnapping: Boolean,
  leftPaddingDp: Dp,
  rightPaddingDp: Dp,
  horizontalScrollState: androidx.compose.foundation.ScrollState,
  hasRightAddButton: Boolean,
  onTogglePlayPause: (() -> Unit)?,
  onSeek: (Long) -> Unit,
  onSeekToNextCut: (() -> Unit)?,
  onScrubStart: () -> Unit = {},
  onScrubStop: () -> Unit = {},
  onAddMedia: (() -> Unit)? = null,
  timeline: Timeline? = null,
  modifier: Modifier = Modifier
) {
  val hasVideoClips = remember(timeline) {
    timeline?.videoClips?.isNotEmpty() == true || timeline?.overlayClips?.any { it.isVideo } == true
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(if (hasVideoClips) 54.dp else 34.dp)
      .background(Color.Black),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .width(if (hasAnyTrack) 88.dp else 72.dp)
        .fillMaxHeight()
        .padding(horizontal = 4.dp),
      contentAlignment = Alignment.CenterStart
    ) {
      Text(
        text = if (hasAnyTrack) formatDurationShort(currentPosMs) else "00:00",
        style = MaterialTheme.typography.bodySmall.copy(
          color = Color.White.copy(alpha = 0.8f),
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold
        ),
        maxLines = 1,
        modifier = Modifier.testTag("timeline_timecode_text")
      )
    }

    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .pointerInput(zoom) {
          detectTransformGestures { _, _, zoomChange, _ ->
            if (kotlin.math.abs(zoomChange - 1f) > 0.005f) {
              onPinchStart()
              onZoomChange((zoom * zoomChange).coerceIn(0.25f, 8.0f))
            }
          }
        }
        .horizontalScroll(horizontalScrollState)
    ) {
      Row(modifier = Modifier.fillMaxHeight()) {
        Spacer(modifier = Modifier.width(leftPaddingDp))
        AccurateTimecodeRuler(
          totalDurationMs = maxTimelineMs,
          currentPosMs = currentPosMs,
          msPerPixel = msPerPixel,
          fps = fps,
          isFrameSnapping = isFrameSnapping,
          onSeek = onSeek,
          onDoubleTapSnap = onSeekToNextCut,
          onScrubStart = onScrubStart,
          onScrubStop = onScrubStop,
          timeline = timeline
        )
        Spacer(modifier = Modifier.width(rightPaddingDp))
      }
    }
  }
}

@Composable
private fun TimelineEmptyView(
  onAddMedia: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .background(Color(0xFF090B10)),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.padding(24.dp)
    ) {
      Surface(
        shape = CircleShape,
        color = Color(0xFF0066FF).copy(alpha = 0.15f),
        border = BorderStroke(1.5.dp, Color(0xFF007AFF)),
        modifier = Modifier
          .size(56.dp)
          .clickable { onAddMedia?.invoke() }
          .testTag("empty_timeline_add_media_btn")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Media",
            tint = Color(0xFF0088FF),
            modifier = Modifier.size(30.dp)
          )
        }
      }
      Text(
        text = "Tap + to add your first video or photo",
        style = MaterialTheme.typography.bodyMedium.copy(
          color = Color.White.copy(alpha = 0.85f),
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium
        )
      )
    }
  }
}
