package com.ahstudio.editor.timeline.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.ahstudio.editor.timeline.core.*
import com.ahstudio.editor.timeline.demo.DemoProjectFactory
import com.ahstudio.editor.timeline.edit.ClipPlanner
import com.ahstudio.editor.timeline.engine.TimelineEngine
import com.ahstudio.editor.timeline.playback.ChoreographerFrameDriver
import com.ahstudio.editor.timeline.playback.MasterTimelineClock
import com.ahstudio.editor.timeline.playback.PlaybackController
import com.ahstudio.editor.timeline.snap.SnapEngine
import com.ahstudio.editor.timeline.viewport.TimelineViewport
import com.example.ui.StudioViewModel
import kotlin.math.abs

@Composable
fun MasterTimelineView(
    modifier: Modifier = Modifier,
    viewModel: StudioViewModel? = null,
    controller: TimelineUiController? = null,
    onAddMedia: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val activeCtrl = controller ?: remember {
        val clock = MasterTimelineClock()
        val engine = TimelineEngine()
        if (viewModel == null) {
            DemoProjectFactory.seed(engine)
        } else {
            engine.addTrack(TrackKind.VIDEO, "Main Video")
        }
        val viewport = TimelineViewport()
        val snap = SnapEngine(engine)
        val planner = ClipPlanner(engine, snap, viewport) { clock.timeMicros }
        val playback = PlaybackController(clock, { engine.durationMicros() }, ChoreographerFrameDriver(), scope)
        TimelineUiController(engine, clock, playback, viewport, snap, planner, engine.history, scope)
    }

    LaunchedEffect(onAddMedia) {
        if (onAddMedia != null) {
            activeCtrl.onAddMediaHandler = { onAddMedia() }
        }
    }

    // Bidirectional sync with StudioViewModel
    if (viewModel != null) {
        val timelineState by viewModel.timelineEngine.timeline.collectAsState()
        val currentPosMs by viewModel.timelineEngine.currentPositionMs.collectAsState()
        val isPlaying by viewModel.timelineEngine.isPlaying.collectAsState()
        val selectedClipIds by viewModel.timelineEngine.selectedClipIds.collectAsState()

        // Wire controller callbacks to StudioViewModel
        DisposableEffect(viewModel) {
            activeCtrl.onTimelinePauseRequested = {
                viewModel.timelineEngine.pause()
                viewModel.playbackEngine.pause()
            }
            activeCtrl.onPlayheadChanged = { posMs ->
                viewModel.timelineEngine.setPosition(posMs, snap = false)
                viewModel.playbackEngine.seekTo(posMs)
            }
            activeCtrl.onClipSelected = { clipId ->
                if (clipId != null) {
                    viewModel.timelineEngine.selectClip(clipId)
                } else {
                    viewModel.timelineEngine.clearSelection()
                }
            }
            activeCtrl.onClipTrimCommitted = { clipId, startMs, durMs ->
                viewModel.timelineEngine.trimClip(clipId, startMs, durMs)
            }
            activeCtrl.onClipMoveCommitted = { clipId, startMs ->
                viewModel.timelineEngine.moveClip(clipId, startMs, snap = false)
            }
            onDispose {
                activeCtrl.onTimelinePauseRequested = null
                activeCtrl.onPlayheadChanged = null
                activeCtrl.onClipSelected = null
                activeCtrl.onClipTrimCommitted = null
                activeCtrl.onClipMoveCommitted = null
            }
        }

        // Sync Playback state
        LaunchedEffect(isPlaying) {
            activeCtrl.isPlaying = isPlaying
        }

        // Sync Current Position from ViewModel
        LaunchedEffect(currentPosMs) {
            val targetMicros = currentPosMs * 1000L
            if (abs(activeCtrl.playheadMicros - targetMicros) > 15_000L) {
                activeCtrl.clock.seekTo(targetMicros)
                if (!activeCtrl.isScrubbing) {
                    activeCtrl.scrollToTime(targetMicros)
                }
            }
        }

        // Sync Selection state from ViewModel
        LaunchedEffect(selectedClipIds) {
            if (activeCtrl.selection != selectedClipIds) {
                activeCtrl.engine.setSelection(selectedClipIds)
            }
        }

        // Dynamic Tracks & Clips Synchronization (Multi-Track Dynamic Lane Allocation)
        LaunchedEffect(timelineState) {
            val previousTrackCount = activeCtrl.snapshot.tracks.size
            activeCtrl.engine.reset()
            val lanes = com.example.ui.components.timeline.TrackLaneManager.computeLanes(timelineState)

            for (lane in lanes) {
                val trackKind = when (lane.kind) {
                    com.example.ui.components.timeline.LaneKind.MAIN_VIDEO -> TrackKind.VIDEO
                    com.example.ui.components.timeline.LaneKind.OVERLAY -> TrackKind.OVERLAY
                    com.example.ui.components.timeline.LaneKind.TEXT, com.example.ui.components.timeline.LaneKind.CAPTION -> TrackKind.TEXT
                    com.example.ui.components.timeline.LaneKind.AUDIO, com.example.ui.components.timeline.LaneKind.MUSIC, com.example.ui.components.timeline.LaneKind.SFX -> TrackKind.VOICE
                    com.example.ui.components.timeline.LaneKind.STICKER -> TrackKind.STICKER
                    com.example.ui.components.timeline.LaneKind.EFFECT, com.example.ui.components.timeline.LaneKind.FILTER, com.example.ui.components.timeline.LaneKind.ADJUSTMENT, com.example.ui.components.timeline.LaneKind.ELEMENT -> TrackKind.EFFECT
                }

                val track = activeCtrl.engine.addTrack(trackKind, lane.label)

                for (clip in lane.clips) {
                    val clipKind = when (lane.kind) {
                        com.example.ui.components.timeline.LaneKind.MAIN_VIDEO, com.example.ui.components.timeline.LaneKind.OVERLAY, com.example.ui.components.timeline.LaneKind.ELEMENT -> ClipKind.VIDEO
                        com.example.ui.components.timeline.LaneKind.TEXT, com.example.ui.components.timeline.LaneKind.CAPTION -> ClipKind.TEXT
                        com.example.ui.components.timeline.LaneKind.AUDIO, com.example.ui.components.timeline.LaneKind.MUSIC, com.example.ui.components.timeline.LaneKind.SFX -> ClipKind.AUDIO
                        com.example.ui.components.timeline.LaneKind.STICKER -> ClipKind.STICKER
                        com.example.ui.components.timeline.LaneKind.EFFECT, com.example.ui.components.timeline.LaneKind.FILTER, com.example.ui.components.timeline.LaneKind.ADJUSTMENT -> ClipKind.EFFECT
                    }

                    activeCtrl.engine.addClip(
                        Clip(
                            id = clip.id,
                            trackId = track.id,
                            kind = clipKind,
                            startMicros = clip.startMs * 1000L,
                            durationMicros = clip.durationMs * 1000L,
                            label = clip.title,
                            mediaUri = clip.uri
                        )
                    )
                }
            }

            // Restore selection if any
            if (selectedClipIds.isNotEmpty()) {
                activeCtrl.engine.setSelection(selectedClipIds)
            }

            // Auto-scroll to reveal newly added track if outside visible viewport
            val newTrackCount = activeCtrl.snapshot.tracks.size
            if (newTrackCount > previousTrackCount && newTrackCount > 1) {
                val metrics = com.ahstudio.editor.timeline.ui.TimelineMetrics(
                    mainRowHeightPx = 58f * activeCtrl.densityScale,
                    subRowHeightPx = 36f * activeCtrl.densityScale,
                    mainToSubGapPx = 8f * activeCtrl.densityScale,
                    subTrackGapPx = 4f * activeCtrl.densityScale
                )
                val lastTrackBottom = metrics.totalTracksHeightPx(newTrackCount)
                val targetScrollY = maxOf(0f, lastTrackBottom - activeCtrl.tracksAreaHeightPx)
                if (targetScrollY > activeCtrl.scrollY) {
                    activeCtrl.setScrollYRaw(targetScrollY)
                }
            } else {
                activeCtrl.setScrollYRaw(activeCtrl.scrollY)
            }
        }
    }

    Box(modifier = modifier) {
        AhTimelineEditor(ctrl = activeCtrl, modifier = Modifier.fillMaxSize())
    }
}
