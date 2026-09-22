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
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MasterTimelineView(
    modifier: Modifier = Modifier,
    viewModel: StudioViewModel? = null,
    controller: TimelineUiController? = null,
) {
    val scope = rememberCoroutineScope()
    val activeCtrl = controller ?: remember {
        val clock = MasterTimelineClock()
        val engine = TimelineEngine()
        DemoProjectFactory.seed(engine)
        val viewport = TimelineViewport()
        val snap = SnapEngine(engine)
        val planner = ClipPlanner(engine, snap, viewport) { clock.timeMicros }
        val playback = PlaybackController(clock, { engine.durationMicros() }, ChoreographerFrameDriver(), scope)
        TimelineUiController(engine, clock, playback, viewport, snap, planner, engine.history, scope)
    }

    // Bidirectional sync with StudioViewModel
    if (viewModel != null) {
        val timelineState by viewModel.timelineEngine.timeline.collectAsState()
        val currentPosMs by viewModel.timelineEngine.currentPositionMs.collectAsState()
        val isPlaying by viewModel.timelineEngine.isPlaying.collectAsState()

        // Sync Playback state
        LaunchedEffect(isPlaying) {
            activeCtrl.isPlaying = isPlaying
        }

        // Sync Current Position from View Model
        LaunchedEffect(currentPosMs) {
            val targetMicros = currentPosMs * 1000L
            if (kotlin.math.abs(activeCtrl.playheadMicros - targetMicros) > 15_000L) {
                activeCtrl.clock.seekTo(targetMicros)
                if (!activeCtrl.isScrubbing) {
                    activeCtrl.scrollToTime(targetMicros)
                }
            }
        }

        // Sync Clips & Tracks if VideoClips exist in StudioViewModel
        LaunchedEffect(timelineState) {
            if (timelineState.videoClips.isNotEmpty() || timelineState.audioClips.isNotEmpty()) {
                activeCtrl.engine.reset()
                val vTrack = activeCtrl.engine.addTrack(TrackKind.VIDEO, "Main Video")
                val ovTrack = activeCtrl.engine.addTrack(TrackKind.OVERLAY, "Overlay")
                val aTrack = activeCtrl.engine.addTrack(TrackKind.VOICE, "Audio")
                val tTrack = activeCtrl.engine.addTrack(TrackKind.TEXT, "Text")

                timelineState.videoClips.forEach { c ->
                    activeCtrl.engine.addClip(
                        Clip(
                            id = c.id,
                            trackId = vTrack.id,
                            kind = ClipKind.VIDEO,
                            startMicros = c.timelineStartMs * 1000L,
                            durationMicros = c.durationMs * 1000L,
                            label = c.name,
                            mediaUri = c.uri
                        )
                    )
                }

                timelineState.overlayClips.forEach { c ->
                    activeCtrl.engine.addClip(
                        Clip(
                            id = c.id,
                            trackId = ovTrack.id,
                            kind = ClipKind.VIDEO,
                            startMicros = c.timelineStartMs * 1000L,
                            durationMicros = c.durationMs * 1000L,
                            label = c.name,
                            mediaUri = c.uri
                        )
                    )
                }

                timelineState.audioClips.forEach { c ->
                    activeCtrl.engine.addClip(
                        Clip(
                            id = c.id,
                            trackId = aTrack.id,
                            kind = ClipKind.AUDIO,
                            startMicros = c.timelineStartMs * 1000L,
                            durationMicros = c.durationMs * 1000L,
                            label = c.title,
                            mediaUri = c.uri
                        )
                    )
                }

                timelineState.textClips.forEach { c ->
                    activeCtrl.engine.addClip(
                        Clip(
                            id = c.id,
                            trackId = tTrack.id,
                            kind = ClipKind.TEXT,
                            startMicros = c.timelineStartMs * 1000L,
                            durationMicros = c.durationMs * 1000L,
                            label = c.text
                        )
                    )
                }
            }
        }
    }

    Box(modifier = modifier) {
        AhTimelineEditor(ctrl = activeCtrl, modifier = Modifier.fillMaxSize())
    }
}
