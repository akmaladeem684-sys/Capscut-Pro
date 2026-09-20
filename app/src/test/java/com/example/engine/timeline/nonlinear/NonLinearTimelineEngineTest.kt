package com.example.engine.timeline.nonlinear

import com.example.engine.timeline.nonlinear.compositor.InMemoryFrameCacheManager
import com.example.engine.timeline.nonlinear.compositor.TimelineFrameCompositor
import com.example.engine.timeline.nonlinear.math.MagneticSnappingEngine
import com.example.engine.timeline.nonlinear.math.TimelineTimeMath
import com.example.engine.timeline.nonlinear.models.*
import com.example.engine.timeline.nonlinear.reducer.TimelineReducer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NonLinearTimelineEngineTest {

  // ==========================================
  // PROMPT 1: Non-Linear Timeline Models & Math
  // ==========================================

  @Test
  fun testTimelineTimeMath_zeroAllocationConversions() {
    val zoomLevel = 100f // 100 px per second -> 100 px per 1_000_000 us

    // 100 px = 1_000_000 us (1 second)
    val us = TimelineTimeMath.pixelsToUs(100f, zoomLevel)
    assertEquals(1_000_000L, us)

    // 1_000_000 us = 100 px
    val px = TimelineTimeMath.usToPixels(1_000_000L, zoomLevel)
    assertEquals(100f, px, 0.001f)

    // Frame conversions at 30 fps
    val frame = TimelineTimeMath.microsToFrames(1_000_000L, 30f)
    assertEquals(30L, frame)

    val frameUs = TimelineTimeMath.framesToMicros(30L, 30f)
    assertEquals(1_000_000L, frameUs)

    // SMPTE Timecode formatting
    val timecode = TimelineTimeMath.formatTimecode(3_661_000_000L, 30f) // 1 hr, 1 min, 1 sec
    assertTrue("Timecode starts with 01:01:01", timecode.startsWith("01:01:01"))
  }

  @Test
  fun testTimelineTimeMath_dynamicRulerSteps() {
    // High zoom gives frame level
    val highZoomStep = TimelineTimeMath.calculateDynamicRulerStep(800f)
    assertEquals(33_333L, highZoomStep) // 1 frame

    // Medium zoom gives 1 second
    val medZoomStep = TimelineTimeMath.calculateDynamicRulerStep(100f)
    assertEquals(1_000_000L, medZoomStep)

    // Low zoom gives 10 seconds or 1 minute
    val lowZoomStep = TimelineTimeMath.calculateDynamicRulerStep(8f)
    assertEquals(30_000_000L, lowZoomStep)
  }

  @Test
  fun testMagneticSnappingEngine_snapsToPlayheadAndClipEdges() {
    val track1 = Track(
      id = "t1",
      clips = listOf(
        Clip(id = "c1", startTimeUs = 0L, durationUs = 2_000_000L),
        Clip(id = "c2", startTimeUs = 3_000_000L, durationUs = 2_000_000L)
      )
    )

    val state = TimelineState(
      tracks = listOf(track1),
      playheadUs = 1_500_000L,
      zoomLevelPxPerSec = 100f,
      snapThresholdPx = 15f
    )

    // 15px threshold at 100px/s is 150_000 us
    // Candidate start at 1_520_000 us (20_000 us from playhead at 1_500_000 us)
    val snapPlayhead = MagneticSnappingEngine.snapClipMove(
      candidateStartUs = 1_520_000L,
      clipDurationUs = 1_000_000L,
      movingClipId = "new_clip",
      targetTrackId = "t1",
      state = state
    )
    assertTrue(snapPlayhead.isSnapped)
    assertEquals(SnapType.PLAYHEAD, snapPlayhead.snapType)
    assertEquals(1_500_000L, snapPlayhead.snappedTimeUs)

    // Candidate start at 2_050_000 us (50_000 us from c1 end at 2_000_000 us)
    val snapClipEdge = MagneticSnappingEngine.snapClipMove(
      candidateStartUs = 2_050_000L,
      clipDurationUs = 1_000_000L,
      movingClipId = "new_clip",
      targetTrackId = "t1",
      state = state
    )
    assertTrue(snapClipEdge.isSnapped)
    assertEquals(SnapType.CLIP_END, snapClipEdge.snapType)
    assertEquals(2_000_000L, snapClipEdge.snappedTimeUs)
  }

  @Test
  fun testTimelineReducer_moveAndTrim() {
    val clip = Clip(
      id = "clip_1",
      startTimeUs = 1_000_000L,
      durationUs = 3_000_000L,
      sourceTrimStartUs = 500_000L,
      sourceDurationUs = 10_000_000L
    )
    val track = Track(id = "track_1", clips = listOf(clip))
    val state = TimelineState(tracks = listOf(track))

    // 1. Move Clip
    val movedState = TimelineReducer.reduce(state, TimelineAction.MoveClip("clip_1", "track_1", 2_000_000L))
    val movedClip = movedState.findClip("clip_1")?.second
    assertNotNull(movedClip)
    assertEquals(2_000_000L, movedClip!!.startTimeUs)

    // 2. Trim Tail (expand by 1s)
    val trimmedTailState = TimelineReducer.reduce(movedState, TimelineAction.TrimClip("clip_1", TrimEdge.TAIL, 1_000_000L))
    val tailClip = trimmedTailState.findClip("clip_1")?.second
    assertEquals(4_000_000L, tailClip!!.durationUs)

    // 3. Trim Head (shorten head by 500_000 us)
    val trimmedHeadState = TimelineReducer.reduce(trimmedTailState, TimelineAction.TrimClip("clip_1", TrimEdge.HEAD, 500_000L))
    val headClip = trimmedHeadState.findClip("clip_1")?.second
    assertEquals(2_500_000L, headClip!!.startTimeUs)
    assertEquals(3_500_000L, headClip.durationUs)
    assertEquals(1_000_000L, headClip.sourceTrimStartUs) // 500_000 + 500_000
  }

  @Test
  fun testTimelineReducer_splitClip() {
    val clip = Clip(
      id = "clip_split",
      startTimeUs = 0L,
      durationUs = 4_000_000L,
      sourceTrimStartUs = 0L,
      sourceDurationUs = 10_000_000L
    )
    val track = Track(id = "track_1", clips = listOf(clip))
    val state = TimelineState(tracks = listOf(track))

    // Split at 2.5s (2_500_000 us)
    val splitState = TimelineReducer.reduce(state, TimelineAction.SplitClip("clip_split", 2_500_000L))
    val updatedTrack = splitState.findTrack("track_1")!!
    assertEquals(2, updatedTrack.clips.size)

    val first = updatedTrack.clips[0]
    val second = updatedTrack.clips[1]

    assertEquals(0L, first.startTimeUs)
    assertEquals(2_500_000L, first.durationUs)

    assertEquals(2_500_000L, second.startTimeUs)
    assertEquals(1_500_000L, second.durationUs)
    assertEquals(2_500_000L, second.sourceTrimStartUs)
  }

  @Test
  fun testTimelineReducer_rippleDeleteVsGapDelete() {
    val clip1 = Clip(id = "c1", startTimeUs = 0L, durationUs = 2_000_000L)
    val clip2 = Clip(id = "c2", startTimeUs = 3_000_000L, durationUs = 2_000_000L) // blank gap between 2s and 3s
    val clip3 = Clip(id = "c3", startTimeUs = 6_000_000L, durationUs = 1_000_000L)

    val track = Track(id = "track_main", clips = listOf(clip1, clip2, clip3))
    val state = TimelineState(tracks = listOf(track))

    // Gap Delete on c2 leaves blank gap: c3 stays at 6_000_000 us
    val gapState = TimelineReducer.reduce(state, TimelineAction.GapDelete("c2"))
    val gapTrack = gapState.findTrack("track_main")!!
    assertEquals(2, gapTrack.clips.size)
    assertEquals(6_000_000L, gapTrack.findClip("c3")!!.startTimeUs)

    // Ripple Delete on c2 shifts c3 left by c2 duration (2_000_000 us): c3 moves to 4_000_000 us!
    val rippleState = TimelineReducer.reduce(state, TimelineAction.RippleDelete("c2"))
    val rippleTrack = rippleState.findTrack("track_main")!!
    assertEquals(2, rippleTrack.clips.size)
    assertEquals(4_000_000L, rippleTrack.findClip("c3")!!.startTimeUs)
  }

  // ==========================================
  // PROMPT 3: Frame Compositor & Audio Engine
  // ==========================================

  @Test
  fun testTimelineFrameCompositor_getActiveClipsAt() {
    val bgVideo = Clip(id = "bg", startTimeUs = 0L, durationUs = 5_000_000L, zIndex = 0)
    val overlay = Clip(id = "ov", startTimeUs = 1_000_000L, durationUs = 2_000_000L, zIndex = 1, alpha = 0.8f)
    val text = Clip(id = "tx", startTimeUs = 1_500_000L, durationUs = 1_000_000L, zIndex = 2)

    val state = TimelineState(
      tracks = listOf(
        Track(id = "t_bg", type = TrackType.VIDEO, clips = listOf(bgVideo)),
        Track(id = "t_ov", type = TrackType.OVERLAY, clips = listOf(overlay)),
        Track(id = "t_tx", type = TrackType.TEXT, clips = listOf(text))
      )
    )

    // At 1_600_000 us: all three clips are active
    val layersAt16 = TimelineFrameCompositor.getActiveClipsAt(state, 1_600_000L)
    assertEquals(3, layersAt16.size)
    // Sorted by zIndex ascending (bg -> ov -> tx)
    assertEquals("bg", layersAt16[0].clipId)
    assertEquals("ov", layersAt16[1].clipId)
    assertEquals("tx", layersAt16[2].clipId)
    assertEquals(0.8f, layersAt16[1].alpha, 0.001f)

    // At 4_000_000 us: only bgVideo is active
    val layersAt40 = TimelineFrameCompositor.getActiveClipsAt(state, 4_000_000L)
    assertEquals(1, layersAt40.size)
    assertEquals("bg", layersAt40[0].clipId)
  }

  @Test
  fun testTimelineFrameCompositor_audioMixingMatrix() {
    val audioClip1 = Clip(
      id = "a1",
      startTimeUs = 0L,
      durationUs = 5_000_000L,
      hasAudio = true,
      volume = 1.0f
    )
    val audioClip2 = Clip(
      id = "a2",
      startTimeUs = 2_000_000L,
      durationUs = 3_000_000L,
      hasAudio = true,
      volume = 0.5f,
      isMuted = false
    )

    val state = TimelineState(
      tracks = listOf(
        Track(id = "audio_1", type = TrackType.AUDIO, clips = listOf(audioClip1)),
        Track(id = "audio_2", type = TrackType.AUDIO, clips = listOf(audioClip2))
      )
    )

    val activeAudio = TimelineFrameCompositor.getActiveAudioAt(state, 2_500_000L)
    assertEquals(2, activeAudio.size)
    assertFalse(activeAudio[0].isMuted)
    assertEquals(1.0f, activeAudio[0].effectiveVolume, 0.05f)
    assertEquals(0.5f, activeAudio[1].effectiveVolume, 0.05f)
  }

  @Test
  fun testInMemoryFrameCacheManager() {
    val cache = InMemoryFrameCacheManager(10 * 1024 * 1024)
    assertNull(cache.getCachedFrame("clip1", 1_000_000L))
    cache.clear()
  }

  // ==========================================
  // FULL INTEGRATION & ADAPTER VERIFICATION
  // ==========================================

  @Test
  fun testNonLinearTimelineAdapter_toTimelineState() {
    val videoClip = com.example.domain.model.VideoClip(
      id = "v1",
      name = "Main Clip",
      uri = "content://media/1",
      timelineStartMs = 1000L,
      durationMs = 4000L,
      sourceStartMs = 500L,
      sourceEndMs = 4500L,
      speed = 1.0f,
      volume = 0.9f
    )
    val overlayClip = com.example.domain.model.VideoClip(
      id = "ov1",
      name = "Picture in Picture",
      uri = "content://media/2",
      timelineStartMs = 2000L,
      durationMs = 2000L,
      sourceStartMs = 0L,
      sourceEndMs = 2000L
    )
    val audioClip = com.example.domain.model.AudioClip(
      id = "a1",
      title = "Background Track",
      uri = "content://audio/1",
      timelineStartMs = 0L,
      durationMs = 6000L,
      sourceStartMs = 0L,
      sourceEndMs = 6000L,
      volume = 0.8f
    )
    val textClip = com.example.domain.model.TextClip(
      id = "txt1",
      text = "Title Text",
      timelineStartMs = 1500L,
      durationMs = 3000L
    )

    val domainTimeline = com.example.domain.model.Timeline(
      videoClips = listOf(videoClip),
      overlayClips = listOf(overlayClip),
      audioClips = listOf(audioClip),
      textClips = listOf(textClip)
    )

    val state = com.example.engine.timeline.nonlinear.adapter.NonLinearTimelineAdapter.toTimelineState(
      timeline = domainTimeline,
      playheadMs = 2500L,
      selectedElement = com.example.engine.SelectedTrackElement.Video("v1"),
      zoomLevelPxPerSec = 120f
    )

    // Verification
    assertEquals(2_500_000L, state.playheadUs)
    assertEquals("v1", state.selectedClipId)
    assertEquals(120f, state.zoomLevelPxPerSec, 0.01f)

    // Verify Main Video Track
    val videoTrack = state.findTrack("track_video_main")
    assertNotNull(videoTrack)
    assertEquals(1, videoTrack!!.clips.size)
    val v = videoTrack.clips[0]
    assertEquals("v1", v.id)
    assertEquals(1_000_000L, v.startTimeUs)
    assertEquals(4_000_000L, v.durationUs)
    assertEquals(500_000L, v.sourceTrimStartUs)
    assertTrue(v.isSelected)

    // Verify Overlay Track
    val overlayTrack = state.findTrack("track_overlay")
    assertNotNull(overlayTrack)
    assertEquals(1, overlayTrack!!.clips.size)
    assertEquals("ov1", overlayTrack.clips[0].id)
    assertEquals(2_000_000L, overlayTrack.clips[0].startTimeUs)

    // Verify Audio Track
    val audioTrack = state.findTrack("track_audio")
    assertNotNull(audioTrack)
    assertEquals(1, audioTrack!!.clips.size)
    assertEquals("a1", audioTrack.clips[0].id)
    assertEquals(0L, audioTrack.clips[0].startTimeUs)
    assertEquals(6_000_000L, audioTrack.clips[0].durationUs)

    // Verify Text Track
    val textTrack = state.findTrack("track_text")
    assertNotNull(textTrack)
    assertEquals(1, textTrack!!.clips.size)
    assertEquals("txt1", textTrack.clips[0].id)
    assertEquals(1_500_000L, textTrack.clips[0].startTimeUs)
  }

  @Test
  fun testNonLinearTimelineAdapter_actionDispatching() {
    val videoClip = com.example.domain.model.VideoClip(
      id = "v1",
      name = "Test Clip",
      timelineStartMs = 1000L,
      durationMs = 5000L
    )
    val domainTimeline = com.example.domain.model.Timeline(
      videoClips = listOf(videoClip)
    )

    val timelineEngine = com.example.engine.TimelineEngine()
    timelineEngine.loadTimeline(domainTimeline)

    var seekReportedMs: Long? = null
    // Dispatch Seek Playhead action
    com.example.engine.timeline.nonlinear.adapter.NonLinearTimelineAdapter.dispatchActionToTimelineEngine(
      action = TimelineAction.SetPlayhead(3_500_000L),
      timeline = domainTimeline,
      timelineEngine = timelineEngine,
      onSeekScrub = { seekReportedMs = it }
    )

    assertEquals(3500L, timelineEngine.currentPositionMs.value)
    assertEquals(3500L, seekReportedMs)

    // Dispatch Move Clip action
    com.example.engine.timeline.nonlinear.adapter.NonLinearTimelineAdapter.dispatchActionToTimelineEngine(
      action = TimelineAction.MoveClip("v1", "track_video_main", 2_000_000L),
      timeline = domainTimeline,
      timelineEngine = timelineEngine
    )

    val updatedClip = timelineEngine.timeline.value.videoClips.find { it.id == "v1" }
    assertNotNull(updatedClip)
    assertEquals(2000L, updatedClip!!.timelineStartMs)
  }
}
