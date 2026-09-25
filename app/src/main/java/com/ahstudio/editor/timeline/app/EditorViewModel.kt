package com.ahstudio.editor.timeline.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.ahstudio.editor.timeline.core.Clip
import com.ahstudio.editor.timeline.core.Track
import com.ahstudio.editor.timeline.core.TrackKind
import com.ahstudio.editor.timeline.demo.DemoProjectFactory
import com.ahstudio.editor.timeline.edit.ClipPlanner
import com.ahstudio.editor.timeline.engine.TimelineEngine
import com.ahstudio.editor.timeline.io.ProjectSerializer
import com.ahstudio.editor.timeline.playback.*
import com.ahstudio.editor.timeline.snap.SnapEngine
import com.ahstudio.editor.timeline.ui.TimelineUiController
import com.ahstudio.editor.timeline.viewport.TimelineViewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.UUID

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // §1: ONE engine + ONE clock + ONE playback system + ONE viewport + ONE snap
    val engine = TimelineEngine()
    val clock = MasterTimelineClock()
    val playback = PlaybackController(clock, { engine.durationMicros() }, ChoreographerFrameDriver(), scope)
    val viewport = TimelineViewport()
    val snap = SnapEngine(engine)
    val planner = ClipPlanner(engine, snap, viewport) { clock.timeMicros }
    val controller = TimelineUiController(engine, clock, playback, viewport, snap, planner, engine.history, scope)

    private val bridge = PreviewBridge()

    // Observable states for UI
    var currentTimeMs by mutableLongStateOf(0L)
        private set

    var selectedClipId by mutableStateOf<String?>(null)
        private set

    var selectedTrackId by mutableStateOf<String?>(null)
        private set

    var selectedClipTrackId by mutableStateOf<String?>(null)
        private set

    val mainVideoTrack: Track
        get() = engine.snapshot.tracks.firstOrNull { it.kind == TrackKind.VIDEO }
            ?: Track(
                id = "main_track_video",
                kind = TrackKind.VIDEO,
                name = "Main Video",
                order = 0
            )

    val subTracks: List<Track>
        get() {
            val mainId = mainVideoTrack.id
            return engine.snapshot.tracks.filter { it.id != mainId }.sortedBy { it.order }
        }

    init {
        playback.previewSink = object : PreviewSink {
            override fun onSeek(targetMicros: Long) {
                currentTimeMs = targetMicros / 1000L
                bridge.onSeek(targetMicros)
            }
            override fun onPlaybackStarted() {
                bridge.onPlaybackStarted()
            }
            override fun onPlaybackPaused() {
                bridge.onPlaybackPaused()
            }
            override fun onPlaybackEnded() {
                bridge.onPlaybackEnded()
            }
            override fun onTimeChanged(micros: Long, playing: Boolean) {
                currentTimeMs = micros / 1000L
                bridge.onTimeChanged(micros, playing)
            }
        }
        DemoProjectFactory.seed(engine)
        clock.seekTo(0L)
        currentTimeMs = 0L

        engine.addListener { snapshot, selection ->
            val selId = selection.firstOrNull()
            selectedClipId = selId
            if (selId != null) {
                val c = snapshot.clips[selId]
                selectedClipTrackId = c?.trackId
                selectedTrackId = c?.trackId
            }
        }
    }

    fun selectClip(clipId: String) {
        selectedClipId = clipId
        engine.setSelection(setOf(clipId))
        val clip = engine.clip(clipId)
        val tId = clip?.trackId
        selectedClipTrackId = tId
        selectedTrackId = tId
    }

    fun selectTrack(trackId: String) {
        selectedTrackId = trackId
        engine.selectTrack(trackId)
    }

    fun seekTo(timeMs: Long) {
        val micros = timeMs * 1000L
        clock.seekTo(micros)
        currentTimeMs = timeMs
    }

    fun togglePlayPause() {
        if (playback.isPlaying) {
            playback.pause()
        } else {
            playback.play()
        }
    }

    /**
     * Appends a brand-new track to the track list (tracks + newTrack)
     * with a unique UUID and proper incremented order. Never overwrites existing tracks.
     */
    fun addTrack(kind: TrackKind = TrackKind.OVERLAY, name: String? = null): Track {
        val existingTracks = engine.snapshot.tracks
        val nextOrder = (existingTracks.maxOfOrNull { it.order } ?: -1) + 1
        val trackName = name ?: when (kind) {
            TrackKind.OVERLAY -> "Overlay ${existingTracks.count { it.kind == TrackKind.OVERLAY } + 1}"
            TrackKind.TEXT -> "Text ${existingTracks.count { it.kind == TrackKind.TEXT } + 1}"
            TrackKind.AUDIO, TrackKind.VOICE, TrackKind.MUSIC, TrackKind.SFX -> "Audio ${existingTracks.count { it.kind == TrackKind.AUDIO || it.kind == TrackKind.VOICE || it.kind == TrackKind.MUSIC || it.kind == TrackKind.SFX } + 1}"
            TrackKind.EFFECT -> "Effect ${existingTracks.count { it.kind == TrackKind.EFFECT } + 1}"
            TrackKind.STICKER -> "Sticker ${existingTracks.count { it.kind == TrackKind.STICKER } + 1}"
            else -> "${kind.name.lowercase().replaceFirstChar { it.uppercase() }} ${existingTracks.size + 1}"
        }
        val newTrack = Track(
            id = UUID.randomUUID().toString(),
            kind = kind,
            name = trackName,
            order = nextOrder
        )
        engine.addTrackDirect(newTrack)
        return newTrack
    }

    // ---- §18 save/load (existing projects backward-compatible) ----
    fun serializeProject(): String = ProjectSerializer.toJson(engine.snapshot, engine.selection)

    fun loadProject(json: String): Boolean = try {
        val (s, sel) = ProjectSerializer.fromJson(json)
        playback.pause()
        engine.restoreInternal(s, sel)
        clock.seekTo(0L)
        currentTimeMs = 0L
        true
    } catch (t: Throwable) {
        controller.report("Load failed: ${t.message}")
        false
    }

    override fun onCleared() {
        playback.pause()
        scope.cancel()
        super.onCleared()
    }
}

/**
 * §16 bridge — TimelineEngine → Clock → PlaybackController → Preview.
 * INTEGRATION: Ah Studio real engines connect here.
 */
class PreviewBridge : PreviewSink {
    override fun onSeek(targetMicros: Long) { /* player.seekTo(targetMicros / 1000) for active track players */ }
    override fun onPlaybackStarted() { /* play audible (non-muted/solo-respecting) players */ }
    override fun onPlaybackPaused() { /* pause all players */ }
    override fun onPlaybackEnded() { /* reset transport UI if needed */ }
    override fun onTimeChanged(micros: Long, playing: Boolean) {
        /* text/sticker/overlay/effect renderers receive exact micros */
    }
}
