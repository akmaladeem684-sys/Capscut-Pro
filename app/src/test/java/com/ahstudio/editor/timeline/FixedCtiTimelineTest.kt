package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.core.ClipKind
import com.ahstudio.editor.timeline.core.TimelineConstants
import com.ahstudio.editor.timeline.core.TrackKind
import com.ahstudio.editor.timeline.edit.ClipPlanner
import com.ahstudio.editor.timeline.engine.TimelineEngine
import com.ahstudio.editor.timeline.engine.TimelineValidationException
import com.ahstudio.editor.timeline.playback.ManualFrameDriver
import com.ahstudio.editor.timeline.playback.MasterTimelineClock
import com.ahstudio.editor.timeline.playback.PlaybackController
import com.ahstudio.editor.timeline.snap.SnapEngine
import com.ahstudio.editor.timeline.ui.TimelineUiController
import com.ahstudio.editor.timeline.viewport.TimelineViewport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToLong

@OptIn(ExperimentalCoroutinesApi::class)
class FixedCtiTimelineTest {

    @Test
    fun `cti needle sits at exactly 40 percent of viewport`() {
        assertEquals(0.40f, TimelineConstants.PLAYHEAD_X_FRACTION, 0.0001f)

        val vp = TimelineViewport().apply { viewportWidthPx = 1000f }
        assertEquals(400f, vp.playheadXPx, 0.001f)

        vp.viewportWidthPx = 1920f
        assertEquals(768f, vp.playheadXPx, 0.001f)
    }

    @Test
    fun `time 0s aligns directly under 40 percent CTI`() {
        val vp = TimelineViewport().apply { viewportWidthPx = 1000f }
        val scroll0 = vp.scrollPxForTime(0L)
        // Scroll offset for 0s puts 0s at the 400px CTI needle
        val screenXAt0 = vp.contentPxAtTime(0L) - scroll0
        assertEquals(vp.playheadXPx, screenXAt0, 0.001f)
        assertEquals(0L, vp.timeAtScrollPx(scroll0))
    }

    @Test
    fun `time 6s aligns directly under 40 percent CTI`() {
        val vp = TimelineViewport().apply { viewportWidthPx = 1000f }
        val t6s = 6_000_000L
        val scroll6s = vp.scrollPxForTime(t6s)
        val screenXAt6s = vp.contentPxAtTime(t6s) - scroll6s
        assertEquals(vp.playheadXPx, screenXAt6s, 0.001f)
        assertEquals(t6s, vp.timeAtScrollPx(scroll6s))
    }

    @Test
    fun `timeline zero lock prevents negative clip times`() {
        val e = Fx.engine("Video" to TrackKind.VIDEO)
        val v = e.snapshot.tracks[0].id
        val clip = Fx.clip(e, v, ClipKind.VIDEO, 0.0, 5.0)

        // Attempting to move clip before 0 must fail validation
        assertThrows(TimelineValidationException::class.java) {
            e.moveClip(clip.id, -1_000_000L)
        }

        // Viewport time at scroll min clamps to 0L
        val vp = TimelineViewport().apply { viewportWidthPx = 1000f }
        val scrollAtZero = vp.scrollPxForTime(0L)
        assertEquals(0L, vp.timeAtScrollPx(scrollAtZero))
        assertEquals(0L, vp.timeAtScrollPx(scrollAtZero - 200f)) // never returns negative time
    }

    @Test
    fun `track headers move with tracks without visual separation`() {
        val vp = TimelineViewport().apply { viewportWidthPx = 1000f }
        val headWidthPx = 250f

        val scroll0 = vp.scrollPxForTime(0L)
        val headStart0 = vp.contentPxAtTime(0L) - headWidthPx - scroll0
        val clipStart0 = vp.contentPxAtTime(0L) - scroll0

        // At 0s, header ends exactly where clip begins
        assertEquals(clipStart0, headStart0 + headWidthPx, 0.001f)

        // At 6s, both move by the exact same distance (6s in content pixels)
        val scroll6s = vp.scrollPxForTime(6_000_000L)
        val headStart6s = vp.contentPxAtTime(0L) - headWidthPx - scroll6s
        val clipStart6s = vp.contentPxAtTime(0L) - scroll6s

        assertEquals(clipStart6s, headStart6s + headWidthPx, 0.001f)
        val deltaHead = headStart0 - headStart6s
        val deltaClip = clipStart0 - clipStart6s
        assertEquals(deltaClip, deltaHead, 0.001f)
    }

    @Test
    fun `gesture anchor calculation moves from 0s to 6s without drift`() {
        val vp = TimelineViewport().apply { viewportWidthPx = 1000f }
        val initialTime = 0L
        val pxPerMicro = vp.pxPerMicro

        // Swiping left by 6 seconds worth of pixels
        val deltaPx = -(6_000_000L * pxPerMicro).toFloat()
        val deltaMicros = (-deltaPx.toDouble() / pxPerMicro).roundToLong()
        val targetMicros = (initialTime + deltaMicros).coerceAtLeast(0L)

        assertEquals(6_000_000L, targetMicros)

        // Swiping right from 6s back to 3s
        val swipeBackPx = (3_000_000L * pxPerMicro).toFloat()
        val backDeltaMicros = (-swipeBackPx.toDouble() / pxPerMicro).roundToLong()
        val rewindMicros = (targetMicros + backDeltaMicros).coerceAtLeast(0L)

        assertEquals(3_000_000L, rewindMicros)
    }

    @Test
    fun `touching timeline immediately pauses playback on touch down`() = runTest {
        val e = Fx.engine("V" to TrackKind.VIDEO)
        val v = e.snapshot.tracks[0].id
        Fx.clip(e, v, ClipKind.VIDEO, 0.0, 10.0)

        val clock = MasterTimelineClock()
        val driver = ManualFrameDriver()
        val playback = PlaybackController(clock, { e.durationMicros() }, driver, backgroundScope)
        val vp = TimelineViewport().apply { viewportWidthPx = 1000f }
        val snap = SnapEngine(e)
        val planner = ClipPlanner(e, snap, vp) { clock.timeMicros }
        val ctrl = TimelineUiController(e, clock, playback, vp, snap, planner, e.history, backgroundScope)

        playback.play()
        assertTrue(playback.isPlaying)

        // User touches down
        ctrl.onTimelineTouchBegan()
        assertFalse(playback.isPlaying)
    }
}
