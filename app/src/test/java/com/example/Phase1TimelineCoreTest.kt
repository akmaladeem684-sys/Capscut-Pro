package com.example

import com.example.engine.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1TimelineCoreTest {
  private val video = CoreTrack(id = "v1", type = CoreTrackType.VIDEO, order = 0)
  private val audio = CoreTrack(id = "a1", type = CoreTrackType.AUDIO, order = 1)
  private val clip = CoreClip(
    id = "c1",
    sourceMediaId = "media1",
    sourceStartUs = 0L,
    sourceEndUs = 10_000_000L,
    timelineStartFrame = 0L,
    timelineEndFrame = 300L
  )
  private fun state() = TimelineState(CoreProject(id = "p1", tracks = listOf(video, audio)))

  @Test fun frameAndTimeConversionIsDeterministic() {
    val fps = FrameRate(30)
    assertEquals(1_000_000L, fps.frameToMicros(30))
    assertEquals(30L, fps.microsToFrame(1_000_000L))
    assertEquals("00:00:10:15", fps.timecode(315))
  }

  @Test fun editCommandsCoverAddMoveTrimSplitDuplicateDeleteAndTrackMove() {
    val history = TimelineCommandHistory()
    var s = state()
    s = history.execute(s, AddClipCommand("v1", clip))
    s = history.execute(s, MoveClipCommand("c1", 30L, "a1"))
    assertEquals("a1", s.clip("c1").first.id)
    s = history.execute(s, TrimClipCommand("c1", 60L, 270L))
    val trimmed = s.clip("c1").second
    assertEquals(60L, trimmed.timelineStartFrame)
    assertEquals(270L, trimmed.timelineEndFrame)
    assertTrue(trimmed.sourceStartUs > 0L)
    assertTrue(trimmed.sourceEndUs < 10_000_000L)
    s = history.execute(s, SplitClipCommand("c1", 165L))
    assertEquals(2, s.clipIds().size)
    val firstTwo = s.project.tracks.flatMap { it.clips }.sortedBy { it.timelineStartFrame }
    assertEquals(firstTwo[0].sourceEndUs, firstTwo[1].sourceStartUs)
    val originalSecond = firstTwo[1].id
    s = history.execute(s, DuplicateClipCommand(originalSecond, 300L))
    assertEquals(3, s.project.tracks.flatMap { it.clips }.size)
    s = history.execute(s, MoveTrackCommand("a1", -1))
    assertEquals(-1, s.track("a1").order)
    s = history.execute(s, DeleteClipCommand(originalSecond))
    assertEquals(2, s.project.tracks.flatMap { it.clips }.size)
    assertTrue(history.canUndo)
    val undone = history.undo()!!
    assertEquals(3, undone.project.tracks.flatMap { it.clips }.size)
    val redone = history.redo()!!
    assertEquals(2, redone.project.tracks.flatMap { it.clips }.size)
  }

  @Test fun serializationRoundTripKeepsTimelineState() {
    val project = CoreProject(
      id = "p",
      name = "Test",
      width = 1080,
      height = 1920,
      frameRate = FrameRate(30000, 1001),
      durationFrames = 900,
      tracks = listOf(video.copy(clips = listOf(clip)))
    )
    val restored = CoreProjectSerializer.fromJson(CoreProjectSerializer.toJson(project))
    assertEquals(project, restored)
  }

  @Test fun sourceMediaIsNeverCopiedByEdits() {
    val before = clip.sourceMediaId
    val moved = MoveClipCommand("c1", 90L).apply(AddClipCommand("v1", clip).apply(state()))
    assertEquals(before, moved.clip("c1").second.sourceMediaId)
    assertNotEquals(0L, moved.clip("c1").second.timelineStartFrame)
  }

  private fun TimelineState.clipIds(): Set<String> = project.tracks.flatMap { it.clips }.map { it.id }.toSet()
}
