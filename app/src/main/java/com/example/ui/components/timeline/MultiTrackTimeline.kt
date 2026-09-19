package com.example.ui.components.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.example.domain.model.Timeline
import com.example.domain.model.TrackHeight
import com.example.domain.model.TrackSettings
import com.example.domain.model.TrackType
import com.example.domain.model.Transition
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import com.example.domain.model.TextClip
import com.example.engine.SelectedTrackElement
import com.example.ui.components.formatDurationShort
import com.example.ui.theme.*

private fun <T> getOrderedClipTracks(
  clips: List<T>,
  timeSelector: (T) -> Pair<Long, Long>
): List<List<T>> {
  if (clips.isEmpty()) return emptyList()
  val trackMap = mutableMapOf<Int, MutableList<T>>()
  val sortedClips = clips.sortedBy { timeSelector(it).first }

  for (clip in sortedClips) {
    val (start, duration) = timeSelector(clip)
    val end = start + duration
    var assignedTrack = 0
    while (trackMap[assignedTrack]?.any { existing ->
        val (eStart, eDuration) = timeSelector(existing)
        val eEnd = eStart + eDuration
        eStart < end && start < eEnd
      } == true) {
      assignedTrack++
    }
    trackMap.getOrPut(assignedTrack) { mutableListOf() }.add(clip)
  }

  return trackMap.entries.sortedBy { it.key }.map { it.value }
}

private fun getOrderedTextTracks(textClips: List<TextClip>): List<List<TextClip>> {
  return getOrderedClipTracks(textClips) { it.timelineStartMs to it.durationMs }
}

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
  val textTracks = remember(timeline.textClips) { getOrderedTextTracks(timeline.textClips) }
  val overlayTracks = remember(timeline.overlayClips) { getOrderedClipTracks(timeline.overlayClips) { it.timelineStartMs to it.durationMs } }
  val audioTracks = remember(timeline.audioClips) { getOrderedClipTracks(timeline.audioClips) { it.timelineStartMs to it.durationMs } }

  // Reorder dragging state on the Video track
  var draggedVideoIndex by remember { mutableStateOf<Int?>(null) }
  var dragAccumulatorPx by remember { mutableFloatStateOf(0f) }
  var dropTargetIndex by remember { mutableStateOf<Int?>(null) }

  var isMutedAll by remember { mutableStateOf(false) }

  // Touch Scrubbing & Drag gesture state
  var isTouchScrubbing by remember { mutableStateOf(false) }
  var isPinching by remember { mutableStateOf(false) }
  var scrubAccumulatorMs by remember { mutableFloatStateOf(currentPosMs.toFloat()) }

  // Auto-hide pinch zoom indicator after 1.2s of inactivity
  LaunchedEffect(isPinching, zoom) {
    if (isPinching) {
      delay(1200)
      isPinching = false
    }
  }

  // Sync scrubAccumulatorMs when currentPosMs changes externally
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
    val hasAnyTrack = timeline.videoClips.isNotEmpty() ||
      timeline.overlayClips.isNotEmpty() ||
      timeline.audioClips.isNotEmpty() ||
      timeline.textClips.isNotEmpty() ||
      timeline.stickerClips.isNotEmpty() ||
      timeline.effectClips.isNotEmpty()

    val hasRightAddButton = false
    val rightColumnWidthDp = 0.dp
    val leftColumnWidthDp = if (hasAnyTrack) 88.dp else 0.dp

    val timelineViewportWidthDp = (maxWidth - leftColumnWidthDp - rightColumnWidthDp).coerceAtLeast(100.dp)
    val centerPaddingDp = timelineViewportWidthDp / 2
    val shift15mmDp = (15f * 160f / 25.4f).dp
    val ctiOffsetDp = (centerPaddingDp - shift15mmDp).coerceAtLeast(0.dp)
    val leftPaddingDp = ctiOffsetDp
    val rightPaddingDp = timelineViewportWidthDp - ctiOffsetDp

    Box(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize()) {
        // 1. Timecode & Ruler Bar (Left: "00:00 / 00:03", Right: dots timeline ruler)
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
          hasRightAddButton = hasRightAddButton,
          onTogglePlayPause = onTogglePlayPause,
          onSeek = onSeek,
          onSeekToNextCut = onSeekToNextCut,
          onScrubStart = onScrubStart,
          onScrubStop = onScrubStop,
          onAddMedia = onAddMedia,
          timeline = timeline
        )

        if (!hasAnyTrack) {
          // Clean empty timeline view when project has no tracks yet
          TimelineEmptyView(
            onAddMedia = onAddMedia,
            modifier = Modifier.weight(1f).fillMaxWidth()
          )
        } else {
          // 2. Main Timeline Tracks View (Fixed Left Utility Column + Scrollable Multi-Track Lanes + Fixed Right Add Media Column)
          Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxSize()) {
              // Left Static Column (Track Icons & Controls aligned vertically with track lanes)
              TimelineLeftUtilityColumn(
                timeline = timeline,
                isMutedAll = isMutedAll,
                verticalScrollState = verticalScrollState,
                onToggleMuteAll = {
                  isMutedAll = !isMutedAll
                  onToggleTrackMute(TrackType.MAIN_VIDEO)
                  onToggleMuteAllVideo?.invoke()
                },
                onEditCover = onEditCover
              )

            // Right Horizontally Scrollable Tracks Area with Touch Scrubbing
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
                        val ctiOffsetPx = with(density) { ctiOffsetDp.toPx() }
                        val timePx = offset.x - ctiOffsetPx
                        val timeDp = timePx / density.density
                        val clickedMs = (timeDp * msPerPixel).toLong().coerceIn(0L, maxTimelineMs)
                        onSeek(clickedMs)
                      }
                    },
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  // ==========================================
                  // 1. MAIN VIDEO TRACK (Filmstrip)
                  // ==========================================
                  val videoTrackHeight = 56.dp
                  val maxVideoEndMs = timeline.videoClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
                  val videoSequenceWidthDp = (maxVideoEndMs / msPerPixel).dp

                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(videoTrackHeight)
                      .testTag("main_video_track")
                  ) {
                    Box(
                      modifier = Modifier
                        .fillMaxHeight()
                        .offset(x = leftPaddingDp)
                    ) {
                      // Continuous Glowing Outer Border Frame around Video Clips Sequence
                      if (timeline.videoClips.isNotEmpty()) {
                        Box(
                          modifier = Modifier
                            .width(videoSequenceWidthDp)
                            .height(videoTrackHeight)
                            .background(Color(0xFF080F1D), RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.5.dp, Color(0xFF0091FF)), RoundedCornerShape(8.dp))
                        )
                      }

                      // Video Track Boundary Markers (Locked Start/End)
                      Box(
                        modifier = Modifier
                          .offset(x = 0.dp)
                          .width(2.dp)
                          .height(videoTrackHeight)
                          .background(Color(0xFF0091FF).copy(alpha = 0.6f))
                          .testTag("video_track_start_lock_line")
                      )

                      if (timeline.videoClips.isNotEmpty()) {
                        Box(
                          modifier = Modifier
                            .offset(x = videoSequenceWidthDp)
                            .width(2.dp)
                            .height(videoTrackHeight)
                            .background(Color(0xFF0091FF).copy(alpha = 0.6f))
                            .testTag("video_track_end_lock_line")
                        )
                      }

                      // Video Clips positioned absolutely by timelineStartMs
                      for ((index, clip) in timeline.videoClips.withIndex()) {
                        val isSelected = (selectedElement as? SelectedTrackElement.Video)?.clipId == clip.id
                        val isMulti = clip.id in selectedClipIds
                        val isBeingReordered = (draggedVideoIndex == index)

                        TimelineClipView(
                          clipId = clip.id,
                          title = clip.name,
                          timelineStartMs = clip.timelineStartMs,
                          durationMs = clip.durationMs,
                          sourceStartMs = clip.sourceStartMs,
                          sourceEndMs = clip.sourceEndMs,
                          currentPlayheadMs = currentPosMs,
                          hasAudio = clip.hasAudio && clip.isVideo,
                          isMuted = clip.isMuted || isMutedAll,
                          trackColor = VideoTrackColor,
                          heightDp = videoTrackHeight,
                          msPerPixel = msPerPixel,
                          isSelected = isSelected,
                          isMultiSelected = isMulti,
                          isLocked = false,
                          speed = clip.speed,
                          isReversed = clip.isReversed,
                          isFreeze = !clip.isVideo,
                          filterName = if (clip.filter?.type != null && clip.filter.type != com.example.domain.model.FilterType.NONE) clip.filter.type.displayName else null,
                          keyframes = clip.keyframes,
                          selectedKeyframeIds = selectedKeyframeIds,
                          onSelectKeyframe = onSelectKeyframe,
                          onMoveKeyframe = onMoveKeyframe,
                          clipIndex = index,
                          totalClipsInTrack = timeline.videoClips.size,
                          isVideoClip = true,
                          uri = clip.uri,
                          isVideo = clip.isVideo,
                          isBeingReordered = isBeingReordered,
                          onSelect = {
                            if (isMultiSelectMode) onToggleClipSelection(clip.id)
                            else {
                              // Selection must not move the master playhead.
                              onSelectElement(SelectedTrackElement.Video(clip.id))
                            }
                          },
                          onLongClick = { onToggleClipSelection(clip.id) },
                          onMoveClip = { delta -> onMoveClip(clip.id, delta) },
                               onScrub = { delta -> onSeek((currentPosMs + delta).coerceIn(0L, maxTimelineMs)) },
                               onScrubStart = onScrubStart,
                               onScrubStop = onScrubStop,
                          onMoveClipStart = { onMoveClipStart?.invoke(clip.id) },
                          onMoveClipEnd = { onMoveClipEnd?.invoke(clip.id) },
                          onTrimLeft = { delta -> onTrimClipLeft(clip.id, delta) },
                          onTrimLeftStart = { onTrimClipLeftStart?.invoke(clip.id) },
                          onTrimLeftEnd = { onTrimClipLeftEnd?.invoke(clip.id) },
                          onTrimRight = { delta -> onTrimClipRight(clip.id, delta) },
                          onTrimRightStart = { onTrimClipRightStart?.invoke(clip.id) },
                          onTrimRightEnd = { onTrimClipRightEnd?.invoke(clip.id) }
                        )
                      }

                      // Applied Transition Badges between adjacent video clips (only when a transition is configured)
                      if (timeline.videoClips.size > 1) {
                        for (i in 0 until timeline.videoClips.size - 1) {
                          val clipA = timeline.videoClips[i]
                          val cutPosMs = clipA.timelineStartMs + clipA.durationMs
                          val cutX = (cutPosMs / msPerPixel).dp - 8.dp
                          val existingTransition = timeline.transitions.find { it.clipIndexBefore == i }
                          val isSelectedCut = i == selectedTransitionCutIndex

                          if (existingTransition != null) {
                            Box(
                              modifier = Modifier
                                .offset(x = cutX, y = (videoTrackHeight - 16.dp) / 2)
                                .size(16.dp)
                                .rotate(45f)
                                .background(PurpleAccent, RoundedCornerShape(2.dp))
                                .border(1.dp, if (isSelectedCut) CyanAccent else Color.White.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                                .clickable {
                                  onSelectTransitionCut?.invoke(i)
                                  onOpenTransitionsTool?.invoke()
                                }
                                .testTag("transition_badge_$i"),
                              contentAlignment = Alignment.Center
                            ) {
                              Icon(
                                imageVector = Icons.Default.Transform,
                                contentDescription = "Transition",
                                tint = Color.White,
                                modifier = Modifier.size(9.dp).rotate(-45f)
                              )
                            }
                          }
                        }
                      }

                      // Add Media button on video track (White plus, Blue color)
                      if (timeline.videoClips.isEmpty()) {
                        Surface(
                          shape = RoundedCornerShape(8.dp),
                          color = Color(0xFF0D121F),
                          border = BorderStroke(1.5.dp, Color(0xFF0080FF)),
                          modifier = Modifier
                            .width(64.dp)
                            .height(videoTrackHeight)
                            .clickable { onAddMedia?.invoke() }
                            .testTag("add_first_media_btn")
                        ) {
                          Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                          ) {
                            Box(
                              modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0080FF)),
                              contentAlignment = Alignment.Center
                            ) {
                              Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Media",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                              )
                            }
                          }
                        }
                      } else {
                        // CapCut style: Add Media '+' button snapped immediately after last video clip
                        Surface(
                          shape = RoundedCornerShape(8.dp),
                          color = Color(0xFF0D121F),
                          border = BorderStroke(1.dp, Color(0xFF1E283E)),
                          modifier = Modifier
                            .offset(x = videoSequenceWidthDp + 6.dp)
                            .width(56.dp)
                            .height(videoTrackHeight)
                            .clickable { onAddMedia?.invoke() }
                            .testTag("add_next_media_btn")
                        ) {
                          Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                          ) {
                            Box(
                              modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0080FF)),
                              contentAlignment = Alignment.Center
                            ) {
                              Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Media",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                              )
                            }
                          }
                        }
                      }
                    }
                  }

                  // ==========================================
                  // 2. OVERLAY / PIP TRACKS (Multi-track lanes)
                  // ==========================================
                  if (overlayTracks.isNotEmpty()) {
                    val overlayTrackHeight = 36.dp
                    val maxOverlayEndMs = timeline.overlayClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
                    val addOverlayOffset = (maxOverlayEndMs / msPerPixel).dp + 8.dp

                    for ((trackIdx, trackClips) in overlayTracks.withIndex()) {
                      Box(
                        modifier = Modifier
                          .fillMaxWidth()
                          .height(overlayTrackHeight)
                          .testTag(if (trackIdx == 0) "overlay_track_lane" else "overlay_track_lane_$trackIdx")
                      ) {
                        Box(
                          modifier = Modifier
                            .fillMaxHeight()
                            .offset(x = leftPaddingDp)
                        ) {
                          for (clip in trackClips) {
                            val isSelected = (selectedElement as? SelectedTrackElement.Overlay)?.clipId == clip.id
                            val isMulti = clip.id in selectedClipIds

                            TimelineClipView(
                              clipId = clip.id,
                              title = clip.name.ifBlank { "Overlay" },
                              timelineStartMs = clip.timelineStartMs,
                              durationMs = clip.durationMs,
                              sourceStartMs = clip.sourceStartMs,
                              sourceEndMs = clip.sourceEndMs,
                              currentPlayheadMs = currentPosMs,
                              hasAudio = clip.hasAudio,
                              trackColor = OverlayTrackColor,
                              heightDp = overlayTrackHeight,
                              msPerPixel = msPerPixel,
                              isSelected = isSelected,
                              isMultiSelected = isMulti,
                              isLocked = false,
                              speed = clip.speed,
                              filterName = if (clip.filter?.type != null && clip.filter.type != com.example.domain.model.FilterType.NONE) clip.filter.type.displayName else null,
                              isVideoClip = clip.isVideo,
                              uri = clip.uri,
                              isVideo = clip.isVideo,
                              onSelect = {
                                if (isMultiSelectMode) onToggleClipSelection(clip.id)
                                else {
                                  // Selection must not move the master playhead.
                                  onSelectElement(SelectedTrackElement.Overlay(clip.id))
                                }
                              },
                              onLongClick = { onToggleClipSelection(clip.id) },
                              onMoveClip = { delta -> onMoveClip(clip.id, delta) },
                               onScrub = { delta -> onSeek((currentPosMs + delta).coerceIn(0L, maxTimelineMs)) },
                               onScrubStart = onScrubStart,
                               onScrubStop = onScrubStop,
                              onMoveClipStart = { onMoveClipStart?.invoke(clip.id) },
                              onMoveClipEnd = { onMoveClipEnd?.invoke(clip.id) },
                              onTrimLeft = { delta -> onTrimClipLeft(clip.id, delta) },
                              onTrimLeftStart = { onTrimClipLeftStart?.invoke(clip.id) },
                              onTrimLeftEnd = { onTrimClipLeftEnd?.invoke(clip.id) },
                              onTrimRight = { delta -> onTrimClipRight(clip.id, delta) },
                              onTrimRightStart = { onTrimClipRightStart?.invoke(clip.id) },
                              onTrimRightEnd = { onTrimClipRightEnd?.invoke(clip.id) }
                            )
                          }

                          if (trackIdx == overlayTracks.lastIndex) {
                            Box(
                              modifier = Modifier
                                .offset(x = addOverlayOffset)
                                .align(Alignment.CenterStart)
                            ) {
                              Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E222D),
                                border = BorderStroke(1.dp, Color(0xFF2D3344)),
                                modifier = Modifier
                                  .width(115.dp)
                                  .height(32.dp)
                                  .clickable { onAddOverlay?.invoke() }
                                  .testTag("add_overlay_pill_btn")
                              ) {
                                Row(
                                  modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                  verticalAlignment = Alignment.CenterVertically
                                ) {
                                  Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                                  Spacer(modifier = Modifier.width(4.dp))
                                  Text("Add overlay", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Medium))
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }

                  // ==========================================
                  // 3. AUDIO TRACKS (Multi-track lanes)
                  // ==========================================
                  if (audioTracks.isNotEmpty()) {
                    val audioTrackHeight = 36.dp
                    val maxAudioEndMs = timeline.audioClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
                    val addAudioOffset = (maxAudioEndMs / msPerPixel).dp + 8.dp

                    for ((trackIdx, trackClips) in audioTracks.withIndex()) {
                      Box(
                        modifier = Modifier
                          .fillMaxWidth()
                          .height(audioTrackHeight)
                          .testTag(if (trackIdx == 0) "audio_track_lane" else "audio_track_lane_$trackIdx")
                      ) {
                        Box(
                          modifier = Modifier
                            .fillMaxHeight()
                            .offset(x = leftPaddingDp)
                        ) {
                          for (clip in trackClips) {
                            val isSelected = (selectedElement as? SelectedTrackElement.Audio)?.clipId == clip.id
                            val isMulti = clip.id in selectedClipIds

                            TimelineClipView(
                              clipId = clip.id,
                              title = clip.title.ifBlank { "Audio" },
                              timelineStartMs = clip.timelineStartMs,
                              durationMs = clip.durationMs,
                              trackColor = AudioTrackColor,
                              heightDp = audioTrackHeight,
                              msPerPixel = msPerPixel,
                              isSelected = isSelected,
                              isMultiSelected = isMulti,
                              isLocked = false,
                              waveformData = clip.waveformData,
                              waveformStyle = waveformStyle,
                              keyframes = clip.keyframes,
                              selectedKeyframeIds = selectedKeyframeIds,
                              baseVolume = clip.volume,
                              fadeInMs = clip.fadeInMs,
                              fadeOutMs = clip.fadeOutMs,
                              showVolumeEnvelope = true,
                              onSelectKeyframe = onSelectKeyframe,
                              onMoveKeyframe = onMoveKeyframe,
                              onAddVolumeKeyframe = { relTime, vol ->
                                onAddAudioKeyframe?.invoke(clip.id, relTime, vol)
                              },
                              onUpdateVolumeKeyframe = { kfId, relTime, vol ->
                                onUpdateAudioKeyframe?.invoke(clip.id, kfId, relTime, vol)
                              },
                              onDeleteVolumeKeyframe = { kfId ->
                                onDeleteAudioKeyframe?.invoke(clip.id, kfId)
                              },
                              onSelect = {
                                if (isMultiSelectMode) onToggleClipSelection(clip.id)
                                else onSelectElement(SelectedTrackElement.Audio(clip.id))
                              },
                              onLongClick = { onToggleClipSelection(clip.id) },
                              onMoveClip = { delta -> onMoveClip(clip.id, delta) },
                               onScrub = { delta -> onSeek((currentPosMs + delta).coerceIn(0L, maxTimelineMs)) },
                               onScrubStart = onScrubStart,
                               onScrubStop = onScrubStop,
                              onMoveClipStart = { onMoveClipStart?.invoke(clip.id) },
                              onMoveClipEnd = { onMoveClipEnd?.invoke(clip.id) },
                              onTrimLeft = { delta -> onTrimClipLeft(clip.id, delta) },
                              onTrimLeftStart = { onTrimClipLeftStart?.invoke(clip.id) },
                              onTrimLeftEnd = { onTrimClipLeftEnd?.invoke(clip.id) },
                              onTrimRight = { delta -> onTrimClipRight(clip.id, delta) },
                              onTrimRightStart = { onTrimClipRightStart?.invoke(clip.id) },
                              onTrimRightEnd = { onTrimClipRightEnd?.invoke(clip.id) }
                            )
                          }

                          if (trackIdx == audioTracks.lastIndex) {
                            Box(
                              modifier = Modifier
                                .offset(x = addAudioOffset)
                                .align(Alignment.CenterStart)
                            ) {
                              Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E222D),
                                border = BorderStroke(1.dp, Color(0xFF2D3344)),
                                modifier = Modifier
                                  .width(120.dp)
                                  .height(34.dp)
                                  .clickable { onAddAudio?.invoke() }
                                  .testTag("add_audio_pill_btn")
                              ) {
                                Row(
                                  modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                  verticalAlignment = Alignment.CenterVertically
                                ) {
                                  Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                                  Spacer(modifier = Modifier.width(6.dp))
                                  Text("Add audio", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Medium, fontSize = 11.5.sp))
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }

                  // ==========================================
                  // 4. TEXT TRACKS (Separate track lane for each text track)
                  // ==========================================
                  if (textTracks.isNotEmpty()) {
                    val textTrackHeight = 36.dp
                    val maxTextEndMs = timeline.textClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
                    val addTextOffset = (maxTextEndMs / msPerPixel).dp + 8.dp

                    for ((trackIdx, trackClips) in textTracks.withIndex()) {
                      Box(
                        modifier = Modifier
                          .fillMaxWidth()
                          .height(textTrackHeight)
                          .testTag(if (trackIdx == 0) "text_track_lane" else "text_track_lane_$trackIdx")
                      ) {
                        Box(
                          modifier = Modifier
                            .fillMaxHeight()
                            .offset(x = leftPaddingDp)
                        ) {
                          for (clip in trackClips) {
                            val isSelected = (selectedElement as? SelectedTrackElement.Text)?.clipId == clip.id
                            val isMulti = clip.id in selectedClipIds

                            TimelineClipView(
                              clipId = clip.id,
                              title = clip.text.ifBlank { "Text" },
                              timelineStartMs = clip.timelineStartMs,
                              durationMs = clip.durationMs,
                              trackColor = TextTrackColor,
                              heightDp = textTrackHeight,
                              msPerPixel = msPerPixel,
                              isSelected = isSelected,
                              isMultiSelected = isMulti,
                              isLocked = false,
                              onSelect = {
                                if (isMultiSelectMode) onToggleClipSelection(clip.id)
                                else onSelectElement(SelectedTrackElement.Text(clip.id))
                              },
                              onLongClick = { onToggleClipSelection(clip.id) },
                              onMoveClip = { delta -> onMoveClip(clip.id, delta) },
                               onScrub = { delta -> onSeek((currentPosMs + delta).coerceIn(0L, maxTimelineMs)) },
                               onScrubStart = onScrubStart,
                               onScrubStop = onScrubStop,
                              onMoveClipStart = { onMoveClipStart?.invoke(clip.id) },
                              onMoveClipEnd = { onMoveClipEnd?.invoke(clip.id) },
                              onTrimLeft = { delta -> onTrimClipLeft(clip.id, delta) },
                              onTrimLeftStart = { onTrimClipLeftStart?.invoke(clip.id) },
                              onTrimLeftEnd = { onTrimClipLeftEnd?.invoke(clip.id) },
                              onTrimRight = { delta -> onTrimClipRight(clip.id, delta) },
                              onTrimRightStart = { onTrimClipRightStart?.invoke(clip.id) },
                              onTrimRightEnd = { onTrimClipRightEnd?.invoke(clip.id) }
                            )
                          }

                          if (trackIdx == textTracks.lastIndex) {
                            Box(
                              modifier = Modifier
                                .offset(x = addTextOffset)
                                .align(Alignment.CenterStart)
                            ) {
                              Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E222D),
                                border = BorderStroke(1.dp, Color(0xFF2D3344)),
                                modifier = Modifier
                                  .width(120.dp)
                                  .height(34.dp)
                                  .clickable { onAddText?.invoke() }
                                  .testTag("add_text_pill_btn")
                              ) {
                                Row(
                                  modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                  verticalAlignment = Alignment.CenterVertically
                                ) {
                                  Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                                  Spacer(modifier = Modifier.width(6.dp))
                                  Text("Add text", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Medium, fontSize = 11.5.sp))
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }

                  // ==========================================
                  // 5. STICKER TRACK (36.dp)
                  // ==========================================
                  if (timeline.stickerClips.isNotEmpty()) {
                    val stickerTrackHeight = 36.dp
                    Box(
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(stickerTrackHeight)
                        .testTag("sticker_track_lane")
                    ) {
                      Box(
                        modifier = Modifier
                          .fillMaxHeight()
                          .offset(x = leftPaddingDp)
                      ) {
                        for (clip in timeline.stickerClips) {
                          val isSelected = (selectedElement as? SelectedTrackElement.Sticker)?.clipId == clip.id
                          val isMulti = clip.id in selectedClipIds

                          TimelineClipView(
                            clipId = clip.id,
                            title = clip.emojiOrAsset.ifBlank { "Sticker" },
                            timelineStartMs = clip.timelineStartMs,
                            durationMs = clip.durationMs,
                            trackColor = StickerTrackColor,
                            heightDp = stickerTrackHeight,
                            msPerPixel = msPerPixel,
                            isSelected = isSelected,
                            isMultiSelected = isMulti,
                            isLocked = false,
                            keyframes = clip.keyframes,
                            selectedKeyframeIds = selectedKeyframeIds,
                            onSelectKeyframe = onSelectKeyframe,
                            onMoveKeyframe = onMoveKeyframe,
                            onSelect = {
                              if (isMultiSelectMode) onToggleClipSelection(clip.id)
                              else onSelectElement(SelectedTrackElement.Sticker(clip.id))
                            },
                            onLongClick = { onToggleClipSelection(clip.id) },
                            onMoveClip = { delta -> onMoveClip(clip.id, delta) },
                               onScrub = { delta -> onSeek((currentPosMs + delta).coerceIn(0L, maxTimelineMs)) },
                               onScrubStart = onScrubStart,
                               onScrubStop = onScrubStop,
                            onMoveClipStart = { onMoveClipStart?.invoke(clip.id) },
                            onMoveClipEnd = { onMoveClipEnd?.invoke(clip.id) },
                            onTrimLeft = { delta -> onTrimClipLeft(clip.id, delta) },
                            onTrimLeftStart = { onTrimClipLeftStart?.invoke(clip.id) },
                            onTrimLeftEnd = { onTrimClipLeftEnd?.invoke(clip.id) },
                            onTrimRight = { delta -> onTrimClipRight(clip.id, delta) },
                            onTrimRightStart = { onTrimClipRightStart?.invoke(clip.id) },
                            onTrimRightEnd = { onTrimClipRightEnd?.invoke(clip.id) }
                          )
                        }

                        val maxStickerEndMs = timeline.stickerClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
                        val addStickerOffset = (maxStickerEndMs / msPerPixel).dp + 8.dp

                        Box(
                          modifier = Modifier
                            .offset(x = addStickerOffset)
                            .align(Alignment.CenterStart)
                        ) {
                          Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E222D),
                            border = BorderStroke(1.dp, Color(0xFF2D3344)),
                            modifier = Modifier
                              .width(120.dp)
                              .height(34.dp)
                              .clickable { onAddSticker?.invoke() }
                              .testTag("add_sticker_pill_btn")
                          ) {
                            Row(
                              modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                              verticalAlignment = Alignment.CenterVertically
                            ) {
                              Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                              Spacer(modifier = Modifier.width(6.dp))
                              Text("Add sticker", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Medium, fontSize = 11.5.sp))
                            }
                          }
                        }
                      }
                    }
                  }

                  // ==========================================
                  // 6. EFFECT TRACK (36.dp)
                  // ==========================================
                  if (timeline.effectClips.isNotEmpty()) {
                    val effectTrackHeight = 36.dp
                    Box(
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(effectTrackHeight)
                        .testTag("effect_track_lane")
                    ) {
                      Box(
                        modifier = Modifier
                          .fillMaxHeight()
                          .offset(x = leftPaddingDp)
                      ) {
                        for (clip in timeline.effectClips) {
                          val isSelected = (selectedElement as? SelectedTrackElement.Effect)?.clipId == clip.id
                          val isMulti = clip.id in selectedClipIds

                          TimelineClipView(
                            clipId = clip.id,
                            title = clip.effectType.displayName,
                            timelineStartMs = clip.timelineStartMs,
                            durationMs = clip.durationMs,
                            trackColor = EffectTrackColor,
                            heightDp = effectTrackHeight,
                            msPerPixel = msPerPixel,
                            isSelected = isSelected,
                            isMultiSelected = isMulti,
                            isLocked = false,
                            onSelect = {
                              if (isMultiSelectMode) onToggleClipSelection(clip.id)
                              else onSelectElement(SelectedTrackElement.Effect(clip.id))
                            },
                            onLongClick = { onToggleClipSelection(clip.id) },
                            onMoveClip = { delta -> onMoveClip(clip.id, delta) },
                               onScrub = { delta -> onSeek((currentPosMs + delta).coerceIn(0L, maxTimelineMs)) },
                               onScrubStart = onScrubStart,
                               onScrubStop = onScrubStop,
                            onMoveClipStart = { onMoveClipStart?.invoke(clip.id) },
                            onMoveClipEnd = { onMoveClipEnd?.invoke(clip.id) },
                            onTrimLeft = { delta -> onTrimClipLeft(clip.id, delta) },
                            onTrimLeftStart = { onTrimClipLeftStart?.invoke(clip.id) },
                            onTrimLeftEnd = { onTrimClipLeftEnd?.invoke(clip.id) },
                            onTrimRight = { delta -> onTrimClipRight(clip.id, delta) },
                            onTrimRightStart = { onTrimClipRightStart?.invoke(clip.id) },
                            onTrimRightEnd = { onTrimClipRightEnd?.invoke(clip.id) }
                          )
                        }

                        val maxEffectEndMs = timeline.effectClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
                        val addEffectOffset = (maxEffectEndMs / msPerPixel).dp + 8.dp

                        Box(
                          modifier = Modifier
                            .offset(x = addEffectOffset)
                            .align(Alignment.CenterStart)
                        ) {
                          Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E222D),
                            border = BorderStroke(1.dp, Color(0xFF2D3344)),
                            modifier = Modifier
                              .width(120.dp)
                              .height(34.dp)
                              .clickable { onAddEffect?.invoke() }
                              .testTag("add_effect_pill_btn")
                          ) {
                            Row(
                              modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                              verticalAlignment = Alignment.CenterVertically
                            ) {
                              Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                              Spacer(modifier = Modifier.width(6.dp))
                              Text("Add effect", style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Medium, fontSize = 11.5.sp))
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }

            // Right Static Column: Add Media '+' Button (Aligned with main video track, matching CapCut style)
            if (hasRightAddButton) {
              TimelineRightAddMediaColumn(
                timeline = timeline,
                verticalScrollState = verticalScrollState,
                onAddMedia = onAddMedia
              )
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
          // Vertical Center Playhead Line (Professional Electric Blue - Shifted 15mm Left)
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

          // CTI Top Needle Cap Badge on Ruler (Subtle Dark Black handle with Blue Accent - Shifted 15mm Left)
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

          // Dynamic Frame Preview Card on CTI Needle during Scrubbing & Playhead Movement
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

@Composable
fun VideoClipSequencerStrip(
  videoClips: List<VideoClip>,
  currentPosMs: Long,
  selectedClipId: String?,
  onSelectClip: (VideoClip) -> Unit,
  onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  Row(
    modifier = modifier
      .horizontalScroll(scrollState)
      .testTag("video_clip_sequencer_strip"),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    videoClips.forEachIndexed { index, clip ->
      val isSelected = clip.id == selectedClipId
      val isPlaying = currentPosMs in clip.timelineStartMs until (clip.timelineStartMs + clip.durationMs)

      Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) StudioSurfaceVariant else StudioSurface,
        border = BorderStroke(
          width = if (isSelected) 1.5.dp else 1.dp,
          color = when {
            isSelected -> CyanAccent
            isPlaying -> AmberAccent
            else -> StudioBorder
          }
        ),
        modifier = Modifier
          .fillMaxHeight()
          .widthIn(min = 130.dp, max = 200.dp)
          .clickable { onSelectClip(clip) }
          .testTag("sequencer_card_$index")
      ) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          // Left: Index Badge + Title/Duration
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(20.dp)
                .background(if (isSelected) CyanAccent else VideoTrackColor, RoundedCornerShape(4.dp)),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isSelected) Color.Black else Color.White
                )
              )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = clip.name,
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = formatDurationShort(clip.durationMs),
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 8.5.sp,
                  color = if (isPlaying) AmberAccent else TextSecondary
                )
              )
            }
          }

          // Right: Swap Left / Right Nudge buttons
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            if (index > 0) {
              Box(
                modifier = Modifier
                  .size(22.dp)
                  .clickable { onReorder(index, index - 1) }
                  .testTag("sequencer_move_left_$index"),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.ArrowBack,
                  contentDescription = "Move Left",
                  tint = CyanAccent,
                  modifier = Modifier.size(13.dp)
                )
              }
            }

            if (index < videoClips.size - 1) {
              Box(
                modifier = Modifier
                  .size(22.dp)
                  .clickable { onReorder(index, index + 1) }
                  .testTag("sequencer_move_right_$index"),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.ArrowForward,
                  contentDescription = "Move Right",
                  tint = CyanAccent,
                  modifier = Modifier.size(13.dp)
                )
              }
            }
          }
        }
      }
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
  onPinchStart: () -> Unit = {},
  onPinchEnd: () -> Unit = {},
  fps: Int,
  isFrameSnapping: Boolean,
  leftPaddingDp: androidx.compose.ui.unit.Dp,
  rightPaddingDp: androidx.compose.ui.unit.Dp,
  horizontalScrollState: androidx.compose.foundation.ScrollState,
  hasRightAddButton: Boolean = false,
  onTogglePlayPause: (() -> Unit)?,
  onSeek: (Long) -> Unit,
  onSeekToNextCut: (() -> Unit)?,
  onScrubStart: () -> Unit = {},
  onScrubStop: () -> Unit = {},
  onAddMedia: (() -> Unit)? = null,
  timeline: com.example.domain.model.Timeline? = null,
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
    // Left header block aligned with track utility sidebar below
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

    // Timeline Ruler with Time Markers & Dots (Scrolls smoothly under fixed center CTI)
    // Supports pinch-to-zoom directly on ruler
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

@Composable
private fun TimelineLeftUtilityColumn(
  timeline: com.example.domain.model.Timeline,
  isMutedAll: Boolean,
  verticalScrollState: androidx.compose.foundation.ScrollState,
  onToggleMuteAll: () -> Unit,
  onEditCover: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  val textTracks = remember(timeline.textClips) { getOrderedTextTracks(timeline.textClips) }
  val overlayTracks = remember(timeline.overlayClips) {
    getOrderedClipTracks(timeline.overlayClips) { it.timelineStartMs to it.durationMs }
  }
  val audioTracks = remember(timeline.audioClips) {
    getOrderedClipTracks(timeline.audioClips) { it.timelineStartMs to it.durationMs }
  }

  Column(
    modifier = modifier
      .width(88.dp)
      .fillMaxHeight()
      .background(Color.Black)
      .padding(horizontal = 6.dp)
      .verticalScroll(verticalScrollState),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    if (timeline.videoClips.isNotEmpty()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp),
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
    }

    repeat(overlayTracks.size) {
      TrackHeaderCell(36.dp, Icons.Default.Layers, "Overlay", OverlayTrackColor)
    }

    repeat(audioTracks.size) {
      TrackHeaderCell(36.dp, Icons.Default.MusicNote, "Audio", AudioTrackColor)
    }

    repeat(textTracks.size) { index ->
      TrackHeaderCell(36.dp, null, if (textTracks.size > 1) "T${index + 1}" else "T", TextTrackColor)
    }

    if (timeline.stickerClips.isNotEmpty()) {
      TrackHeaderCell(
        36.dp,
        if (timeline.stickerClips.any { it.elementId != null }) Icons.Default.Category else Icons.Default.EmojiEmotions,
        if (timeline.stickerClips.any { it.elementId != null }) "Elements" else "Sticker",
        StickerTrackColor
      )
    }

    if (timeline.effectClips.isNotEmpty()) {
      TrackHeaderCell(36.dp, Icons.Default.AutoAwesome, "Effects", EffectTrackColor)
    }
  }
}

@Composable
private fun TrackHeaderCell(
  height: androidx.compose.ui.unit.Dp,
  icon: androidx.compose.ui.graphics.vector.ImageVector?,
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
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier.size(16.dp)
      )
    } else {
      Text(
        text = label,
        style = MaterialTheme.typography.titleMedium.copy(
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = tint
        )
      )
    }
  }
}

@Composable
private fun TimelineRightAddMediaColumn(
  timeline: com.example.domain.model.Timeline,
  verticalScrollState: androidx.compose.foundation.ScrollState,
  onAddMedia: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .width(60.dp)
      .fillMaxHeight()
      .background(Color.Black)
      .padding(horizontal = 4.dp)
      .verticalScroll(verticalScrollState),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // Row 1: Aligned with Main Video Track (64.dp)
    if (onAddMedia != null) {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0D121F),
        border = BorderStroke(1.dp, Color(0xFF1E283E)),
        modifier = Modifier
          .fillMaxWidth()
          .height(64.dp)
          .clickable { onAddMedia.invoke() }
          .testTag("timeline_right_add_media_btn")
      ) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(Color(0xFF0080FF)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = "Add Media",
              tint = Color.White,
              modifier = Modifier.size(24.dp)
            )
          }
        }
      }
    }
  }
}

