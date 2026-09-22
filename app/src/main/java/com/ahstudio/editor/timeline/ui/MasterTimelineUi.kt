package com.ahstudio.editor.timeline.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.ahstudio.editor.timeline.demo.DemoProjectFactory
import com.ahstudio.editor.timeline.edit.ClipPlanner
import com.ahstudio.editor.timeline.engine.TimelineEngine
import com.ahstudio.editor.timeline.playback.ChoreographerFrameDriver
import com.ahstudio.editor.timeline.playback.MasterTimelineClock
import com.ahstudio.editor.timeline.playback.PlaybackController
import com.ahstudio.editor.timeline.snap.SnapEngine
import com.ahstudio.editor.timeline.viewport.TimelineViewport

@Composable
fun MasterTimelineView(
    modifier: Modifier = Modifier,
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

    Box(modifier = modifier) {
        AhTimelineEditor(ctrl = activeCtrl, modifier = Modifier.fillMaxSize())
    }
}
