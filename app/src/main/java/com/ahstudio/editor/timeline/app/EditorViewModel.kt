package com.ahstudio.editor.timeline.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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

    init {
        playback.previewSink = bridge
        DemoProjectFactory.seed(engine)
        clock.seekTo(0L)
    }

    // ---- §18 save/load (existing projects backward-compatible) ----
    fun serializeProject(): String = ProjectSerializer.toJson(engine.snapshot, engine.selection)

    fun loadProject(json: String): Boolean = try {
        val (s, sel) = ProjectSerializer.fromJson(json)
        playback.pause()
        engine.restoreInternal(s, sel)
        clock.seekTo(0L)
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
