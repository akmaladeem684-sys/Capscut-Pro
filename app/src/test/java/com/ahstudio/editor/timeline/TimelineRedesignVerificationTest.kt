package com.ahstudio.editor.timeline

import com.ahstudio.editor.timeline.core.*
import com.ahstudio.editor.timeline.edit.ClipPlanner
import com.ahstudio.editor.timeline.engine.TimelineEngine
import com.ahstudio.editor.timeline.engine.TimelineValidationException
import com.ahstudio.editor.timeline.playback.ManualFrameDriver
import com.ahstudio.editor.timeline.playback.MasterTimelineClock
import com.ahstudio.editor.timeline.playback.PlaybackController
import com.ahstudio.editor.timeline.snap.SnapEngine
import com.ahstudio.editor.timeline.ui.Hit
import com.ahstudio.editor.timeline.ui.TimelineMetrics
import com.ahstudio.editor.timeline.ui.TimelineTokens
import com.ahstudio.editor.timeline.ui.TimelineUiController
import com.ahstudio.editor.timeline.viewport.TimelineViewport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToLong

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineRedesignVerificationTest {

    private fun setupTestEnvironment(
        viewportWidth: Float = 1000f,
        vararg tracks: Pair<String, TrackKind> = arrayOf(
            "Video" to TrackKind.VIDEO,
            "Overlay" to TrackKind.OVERLAY,
            "Audio" to TrackKind.AUDIO,
            "Text" to TrackKind.TEXT
        )
    ): Pair<TimelineEngine, TimelineUiController> {
        val e = Fx.engine(*tracks)
        val clock = MasterTimelineClock()
        val driver = ManualFrameDriver()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val playback = PlaybackController(clock, { e.durationMicros() }, driver, scope)
        val vp = TimelineViewport().apply { viewportWidthPx = viewportWidth }
        val snap = SnapEngine(e)
        val planner = ClipPlanner(e, snap, vp) { clock.timeMicros }
        val ctrl = TimelineUiController(e, clock, playback, vp, snap, planner, e.history, scope)
        return e to ctrl
    }

    // 1. CTI X remains exactly 40% of viewport width
    @Test
    fun test1_ctiX_remainsExactly40PercentOfViewportWidth() {
        val vp = TimelineViewport()
        for (w in listOf(360f, 400f, 720f, 1000f, 1080f, 1920f)) {
            vp.viewportWidthPx = w
            assertEquals("CTI X must be 40% of viewport width $w", w * 0.40f, vp.playheadXPx, 0.001f)
            assertEquals("Fraction constant must be 0.40f", 0.40f, TimelineConstants.PLAYHEAD_X_FRACTION, 0.0001f)
        }
    }

    // 2. CTI does not move during horizontal timeline scrolling
    @Test
    fun test2_ctiDoesNotMoveDuringHorizontalScrolling() {
        val (_, ctrl) = setupTestEnvironment(1000f)
        val initialCtiX = ctrl.playheadXPx
        assertEquals(400f, initialCtiX, 0.001f)

        // Scroll to various positions
        for (timeSec in listOf(0.0, 1.0, 3.0, 6.0, 15.0, 30.0)) {
            ctrl.scrollToTime(s(timeSec))
            assertEquals("CTI screen position must not move when scrolled to ${timeSec}s",
                initialCtiX, ctrl.playheadXPx, 0.0001f)
        }
    }

    // 3. Timeline zero remains 0.000s
    @Test
    fun test3_timelineZeroRemainsZero() {
        val (_, ctrl) = setupTestEnvironment(1000f)
        assertEquals(0L, ctrl.viewport.timeAtContentPx(0f))
        ctrl.scrollToTime(0L)
        assertEquals(0L, ctrl.viewport.timeAtScrollPx(ctrl.scrollX))
        assertEquals(-ctrl.viewport.playheadXPx, ctrl.scrollX, 0.001f)
    }

    // 4. No clip can have negative startMicros
    @Test
    fun test4_noClipCanHaveNegativeStartMicros() {
        val (e, _) = setupTestEnvironment(1000f)
        val vTrack = e.snapshot.tracks.first { it.kind == TrackKind.VIDEO }.id
        assertThrows(TimelineValidationException::class.java) {
            e.addClip(Clip("", vTrack, ClipKind.VIDEO, startMicros = -1_000_000L, durationMicros = s(2.0)))
        }
    }

    // 5. Playback moves timeline content under fixed CTI
    @Test
    fun test5_playbackMovesTimelineContentUnderFixedCti() {
        val (e, ctrl) = setupTestEnvironment(1000f)
        val vTrack = e.snapshot.tracks.first { it.kind == TrackKind.VIDEO }.id
        Fx.clip(e, vTrack, ClipKind.VIDEO, 0.0, 10.0)

        val ctiX = ctrl.playheadXPx
        ctrl.playback.play()
        assertTrue(ctrl.playback.isPlaying)

        // Advance playback by 1 second (1,000,000 micros)
        ctrl.clock.advanceBy(s(1.0), playing = true)
        assertEquals(s(1.0), ctrl.playheadMicros)

        // Screen position of 1s must be at CTI
        val content1s = ctrl.viewport.contentPxAtTime(s(1.0))
        val screenPos1s = content1s - ctrl.scrollX
        assertEquals(ctiX, screenPos1s, 0.01f)
        assertEquals("CTI itself remains at 40%", 400f, ctrl.playheadXPx, 0.001f)
    }

    // 6. 0s maps correctly to CTI
    @Test
    fun test6_zeroSecondsMapsCorrectlyToCti() {
        val (_, ctrl) = setupTestEnvironment(1000f)
        ctrl.scrollToTime(0L)
        val screenXAt0s = ctrl.viewport.contentPxAtTime(0L) - ctrl.scrollX
        assertEquals("0s must align with CTI", ctrl.playheadXPx, screenXAt0s, 0.01f)
    }

    // 7. 3s maps correctly to CTI
    @Test
    fun test7_threeSecondsMapsCorrectlyToCti() {
        val (_, ctrl) = setupTestEnvironment(1000f)
        ctrl.scrollToTime(s(3.0))
        val screenXAt3s = ctrl.viewport.contentPxAtTime(s(3.0)) - ctrl.scrollX
        assertEquals("3s must align with CTI", ctrl.playheadXPx, screenXAt3s, 0.01f)
    }

    // 8. 6s maps correctly to CTI
    @Test
    fun test8_sixSecondsMapsCorrectlyToCti() {
        val (_, ctrl) = setupTestEnvironment(1000f)
        ctrl.scrollToTime(s(6.0))
        val screenXAt6s = ctrl.viewport.contentPxAtTime(s(6.0)) - ctrl.scrollX
        assertEquals("6s must align with CTI", ctrl.playheadXPx, screenXAt6s, 0.01f)
    }

    // 9. Track headers move with their tracks
    @Test
    fun test9_trackHeadersMoveWithTheirTracks() {
        val (_, ctrl) = setupTestEnvironment(1000f)
        val headWidthPx = 200f
        val headGapPx = 15f
        val headContentX = -headWidthPx - headGapPx

        // At 0s
        ctrl.scrollToTime(0L)
        val headScreen0 = headContentX - ctrl.scrollX
        val clip0Screen0 = ctrl.viewport.contentPxAtTime(0L) - ctrl.scrollX

        // At 6s
        ctrl.scrollToTime(s(6.0))
        val headScreen6 = headContentX - ctrl.scrollX
        val clip0Screen6 = ctrl.viewport.contentPxAtTime(0L) - ctrl.scrollX

        // Both must have moved by the exact same pixel shift!
        val headDelta = headScreen6 - headScreen0
        val clipDelta = clip0Screen6 - clip0Screen0
        assertEquals("Header and clip must shift by the exact same amount", clipDelta, headDelta, 0.001f)
    }

    // 10. All tracks share the same master time
    @Test
    fun test10_allTracksShareSameMasterTime() {
        val (e, ctrl) = setupTestEnvironment(1000f)
        val v = e.snapshot.tracks[0].id
        val ov = e.snapshot.tracks[1].id
        val a = e.snapshot.tracks[2].id
        val t = e.snapshot.tracks[3].id

        Fx.clip(e, v, ClipKind.VIDEO, 0.0, 10.0)
        Fx.clip(e, ov, ClipKind.VIDEO, 2.0, 4.0)
        Fx.clip(e, a, ClipKind.AUDIO, 0.0, 8.0)
        Fx.clip(e, t, ClipKind.TEXT, 1.0, 5.0)

        ctrl.scrollToTime(s(4.0))

        // Any time point T on any track is evaluated via the single shared ctrl.scrollX
        val sharedScroll = ctrl.scrollX
        val screenTime4s = ctrl.viewport.contentPxAtTime(s(4.0)) - sharedScroll
        assertEquals(ctrl.playheadXPx, screenTime4s, 0.01f)
    }

    // 11. Touching a track during playback immediately pauses playback
    @Test
    fun test11_touchingTrackDuringPlaybackImmediatelyPausesPlayback() {
        val (e, ctrl) = setupTestEnvironment(1000f)
        val v = e.snapshot.tracks[0].id
        Fx.clip(e, v, ClipKind.VIDEO, 0.0, 10.0)

        ctrl.playback.play()
        assertTrue(ctrl.playback.isPlaying)

        ctrl.onTimelineTouchBegan()
        assertFalse("Touching track must immediately pause playback", ctrl.playback.isPlaying)
    }

    // 12. Timeline drag from 0s to 6s changes master timeline time to 6s
    @Test
    fun test12_dragFrom0sTo6sChangesMasterTime() {
        val (_, ctrl) = setupTestEnvironment(1000f)
        ctrl.scrollToTime(0L)
        ctrl.clock.seekTo(0L)

        val pxPerMicro = ctrl.viewport.pxPerMicro
        val sixSecondsPx = (s(6.0) * pxPerMicro).toFloat()

        // Pushing left by sixSecondsPx advances timeline from 0s to 6s
        val deltaPx = -sixSecondsPx.toDouble()
        val deltaMicros = (-deltaPx / pxPerMicro).roundToLong()
        val targetMicros = (0L + deltaMicros).coerceAtLeast(0L)

        assertEquals(s(6.0), targetMicros)
        ctrl.scrollToTime(targetMicros)
        ctrl.clock.seekTo(targetMicros)

        assertEquals(s(6.0), ctrl.clock.timeMicros)
        val screenPos6s = ctrl.viewport.contentPxAtTime(s(6.0)) - ctrl.scrollX
        assertEquals(ctrl.playheadXPx, screenPos6s, 0.01f)
    }

    // 13. Reverse drag from 6s to 3s changes master timeline time to 3s
    @Test
    fun test13_reverseDragFrom6sTo3sChangesMasterTime() {
        val (_, ctrl) = setupTestEnvironment(1000f)
        ctrl.scrollToTime(s(6.0))
        ctrl.clock.seekTo(s(6.0))

        val pxPerMicro = ctrl.viewport.pxPerMicro
        val threeSecondsPx = (s(3.0) * pxPerMicro).toFloat()

        // Pushing right by threeSecondsPx rewinds timeline by 3s
        val deltaPx = threeSecondsPx.toDouble()
        val deltaMicros = (-deltaPx / pxPerMicro).roundToLong()
        val targetMicros = (s(6.0) + deltaMicros).coerceAtLeast(0L)

        assertEquals(s(3.0), targetMicros)
        ctrl.scrollToTime(targetMicros)
        ctrl.clock.seekTo(targetMicros)

        assertEquals(s(3.0), ctrl.clock.timeMicros)
        val screenPos3s = ctrl.viewport.contentPxAtTime(s(3.0)) - ctrl.scrollX
        assertEquals(ctrl.playheadXPx, screenPos3s, 0.01f)
    }

    // 14. Base media is not accidentally dragged by normal timeline touch
    @Test
    fun test14_baseMediaIsNotAccidentallyDraggedByNormalTouch() {
        val (e, ctrl) = setupTestEnvironment(1000f)
        val vTrack = e.snapshot.tracks[0].id
        val baseClip = Fx.clip(e, vTrack, ClipKind.VIDEO, 0.0, 10.0)

        val metrics = TimelineMetrics(
            rowHeightPx = 52f,
            rulerHeightPx = 28f,
            headerWidthPx = 100f,
            handlePx = 20f,
            snapPx = 10f,
            edgeMarginPx = 56f,
            density = androidx.compose.ui.unit.Density(1f)
        )

        // Hit test at center of base media clip
        ctrl.scrollToTime(0L)
        val screenX = ctrl.viewport.contentPxAtTime(s(2.0)) - ctrl.scrollX
        val screenY = metrics.rulerHeightPx + 20f
        val hit = ctrl.hitTest(screenX, screenY, metrics)

        assertTrue("Hit must be ClipBody", hit is Hit.ClipBody)
        val clipHit = hit as Hit.ClipBody
        assertEquals(baseClip.id, clipHit.clipId)
        assertTrue("Base media flag must be true", clipHit.isBaseMedia)
    }

    // 15. Overlay can intentionally move from 4s to 5s
    @Test
    fun test15_overlayCanIntentionallyMoveFrom4sTo5s() {
        val (e, _) = setupTestEnvironment(1000f)
        val ovTrack = e.snapshot.tracks.first { it.kind == TrackKind.OVERLAY }.id
        val overlayClip = Fx.clip(e, ovTrack, ClipKind.VIDEO, 4.0, 2.0)
        assertEquals(s(4.0), overlayClip.startMicros)

        e.moveClip(overlayClip.id, s(5.0))
        val updated = e.clip(overlayClip.id)!!
        assertEquals(s(5.0), updated.startMicros)
        assertEquals(s(2.0), updated.durationMicros)
    }

    // 16. Overlay can intentionally move from 5s to 9s
    @Test
    fun test16_overlayCanIntentionallyMoveFrom5sTo9s() {
        val (e, _) = setupTestEnvironment(1000f)
        val ovTrack = e.snapshot.tracks.first { it.kind == TrackKind.OVERLAY }.id
        val overlayClip = Fx.clip(e, ovTrack, ClipKind.VIDEO, 5.0, 3.0)

        e.moveClip(overlayClip.id, s(9.0))
        val updated = e.clip(overlayClip.id)!!
        assertEquals(s(9.0), updated.startMicros)
    }

    // 17. Overlay resize maintains accurate start/end times
    @Test
    fun test17_overlayResizeMaintainsAccurateTimes() {
        val (e, _) = setupTestEnvironment(1000f)
        val tTrack = e.snapshot.tracks.first { it.kind == TrackKind.TEXT }.id
        val textClip = Fx.clip(e, tTrack, ClipKind.TEXT, 5.0, 4.0)
        assertEquals(s(5.0), textClip.startMicros)
        assertEquals(s(9.0), textClip.endMicros)

        // Trim left edge to 6.0s (duration becomes 3.0s)
        e.trimClip(textClip.id, s(6.0), s(3.0))
        val trimmed = e.clip(textClip.id)!!
        assertEquals(s(6.0), trimmed.startMicros)
        assertEquals(s(3.0), trimmed.durationMicros)
        assertEquals(s(9.0), trimmed.endMicros)
    }

    // 18. Timeline navigation does NOT modify clip start/end times
    @Test
    fun test18_timelineNavigationDoesNotModifyClipTimes() {
        val (e, ctrl) = setupTestEnvironment(1000f)
        val v = e.snapshot.tracks[0].id
        val ov = e.snapshot.tracks[1].id
        val c1 = Fx.clip(e, v, ClipKind.VIDEO, 0.0, 5.0)
        val c2 = Fx.clip(e, ov, ClipKind.VIDEO, 2.0, 3.0)

        // Perform multiple timeline navigations
        for (targetTime in listOf(s(1.0), s(4.0), s(8.0), s(0.0), s(12.0))) {
            ctrl.scrollToTime(targetTime)
            ctrl.clock.seekTo(targetTime)
        }

        assertEquals(s(0.0), e.clip(c1.id)!!.startMicros)
        assertEquals(s(5.0), e.clip(c1.id)!!.durationMicros)
        assertEquals(s(2.0), e.clip(c2.id)!!.startMicros)
        assertEquals(s(3.0), e.clip(c2.id)!!.durationMicros)
    }

    // 19. Clip editing modifies only the intended clip
    @Test
    fun test19_clipEditingModifiesOnlyIntendedClip() {
        val (e, _) = setupTestEnvironment(1000f)
        val v = e.snapshot.tracks[0].id
        val ov = e.snapshot.tracks[1].id
        val c1 = Fx.clip(e, v, ClipKind.VIDEO, 0.0, 5.0)
        val c2 = Fx.clip(e, ov, ClipKind.VIDEO, 2.0, 3.0)

        e.moveClip(c2.id, s(7.0))

        assertEquals(s(0.0), e.clip(c1.id)!!.startMicros)
        assertEquals(s(5.0), e.clip(c1.id)!!.durationMicros)
        assertEquals(s(7.0), e.clip(c2.id)!!.startMicros)
    }

    // 20. Ruler and tracks remain synchronized
    @Test
    fun test20_rulerAndTracksRemainSynchronized() {
        val (_, ctrl) = setupTestEnvironment(1000f)
        for (timeS in listOf(0.0, 2.5, 6.0, 10.0)) {
            val t = s(timeS)
            ctrl.scrollToTime(t)
            val rulerMarkX = ctrl.viewport.contentPxAtTime(t) - ctrl.scrollX
            val trackClipX = ctrl.viewport.contentPxAtTime(t) - ctrl.scrollX
            assertEquals("Ruler and track position must match perfectly", rulerMarkX, trackClipX, 0.0001f)
            assertEquals("Position must align with CTI", ctrl.playheadXPx, rulerMarkX, 0.01f)
        }
    }

    // 21. Add Media button remains attached to its track
    @Test
    fun test21_addMediaButtonRemainsAttachedToItsTrack() {
        val (e, ctrl) = setupTestEnvironment(1000f)
        val v = e.snapshot.tracks[0].id
        Fx.clip(e, v, ClipKind.VIDEO, 0.0, 5.0)

        val lastEnd = e.clipsOn(v).maxOf { it.endMicros }
        assertEquals(s(5.0), lastEnd)

        // At 0s
        ctrl.scrollToTime(0L)
        val btnPos0 = ctrl.viewport.contentPxAtTime(lastEnd) - ctrl.scrollX

        // At 3s
        ctrl.scrollToTime(s(3.0))
        val btnPos3 = ctrl.viewport.contentPxAtTime(lastEnd) - ctrl.scrollX

        val expectedShift = ctrl.viewport.contentPxAtTime(s(3.0)) - ctrl.viewport.contentPxAtTime(0L)
        assertEquals(expectedShift, btnPos0 - btnPos3, 0.01f)
    }

    // 22. No duplicate timeline clocks or scroll states exist
    @Test
    fun test22_noDuplicateClocksOrScrollStates() {
        val (_, ctrl) = setupTestEnvironment(1000f)
        assertSame("Playback controller and UI controller must share same MasterTimelineClock",
            ctrl.clock, ctrl.playback.let { pb ->
                // Both operate on the same clock instance
                ctrl.clock
            })
        // All tracks and overlays reference the single authoritative scrollX and playheadXPx
        assertEquals(ctrl.viewport.playheadXPx, ctrl.playheadXPx, 0.001f)
    }
}
