package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.core.ClipKind
import com.ahstudio.editor.timeline.core.TrackKind
import com.ahstudio.editor.timeline.edit.ClipPlanner
import com.ahstudio.editor.timeline.playback.ManualFrameDriver
import com.ahstudio.editor.timeline.playback.MasterTimelineClock
import com.ahstudio.editor.timeline.playback.PlaybackController
import com.ahstudio.editor.timeline.snap.SnapEngine
import com.ahstudio.editor.timeline.ui.TimelineUiController
import com.ahstudio.editor.timeline.viewport.TimelineViewport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TouchPauseInteractionTest {
    @Test fun `touching the timeline always pauses active playback immediately`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val e = Fx.engine("V" to TrackKind.VIDEO)
        val v = e.snapshot.tracks[0].id
        Fx.clip(e, v, ClipKind.VIDEO, 0.0, 10.0)
        val clock = MasterTimelineClock()
        val driver = ManualFrameDriver()
        val playback = PlaybackController(clock, { e.durationMicros() }, driver, this.backgroundScope)
        val vp = TimelineViewport().apply { viewportWidthPx = 1000f }
        val snap = SnapEngine(e)
        val planner = ClipPlanner(e, snap, vp) { clock.timeMicros }
        val ctrl = TimelineUiController(e, clock, playback, vp, snap, planner, e.history, this.backgroundScope)

        playback.play()
        assertTrue("Playback should be playing", playback.isPlaying)

        // Simulating user touch on timeline
        ctrl.onTimelineTouchBegan()

        assertFalse("Touching timeline must immediately pause playback", playback.isPlaying)
    }
}
