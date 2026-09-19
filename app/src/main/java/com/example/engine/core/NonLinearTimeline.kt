package com.example.engine.core

import java.util.UUID

enum class CoreTrackType { VIDEO, AUDIO, OVERLAY_ELEMENT }

data class CoreProject(
  val id: String = UUID.randomUUID().toString(),
  val name: String = "Untitled Project",
  val width: Int = 1920,
  val height: Int = 1080,
  val frameRate: FrameRate = FrameRate(30),
  val durationFrames: Long = 0L,
  val tracks: List<CoreTrack> = emptyList(),
  val metadata: Map<String, String> = emptyMap()
) {
  init {
    require(width > 0 && height > 0)
    require(durationFrames >= 0L)
  }
}

data class CoreTrack(
  val id: String = UUID.randomUUID().toString(),
  val type: CoreTrackType,
  val order: Int,
  val locked: Boolean = false,
  val muted: Boolean = false,
  val hidden: Boolean = false,
  val clips: List<CoreClip> = emptyList()
) {
  fun sorted(): CoreTrack = copy(clips = clips.sortedWith(compareBy<CoreClip> { it.timelineStartFrame }.thenBy { it.id }))
}

data class CoreTransform(
  val x: Float = 0f,
  val y: Float = 0f,
  val scaleX: Float = 1f,
  val scaleY: Float = 1f,
  val rotation: Float = 0f,
  val opacity: Float = 1f
)

data class CoreClip(
  val id: String = UUID.randomUUID().toString(),
  val sourceMediaId: String,
  val sourceStartUs: Long,
  val sourceEndUs: Long,
  val timelineStartFrame: Long,
  val timelineEndFrame: Long,
  val speed: Float = 1f,
  val volume: Float = 1f,
  val transform: CoreTransform = CoreTransform(),
  val enabled: Boolean = true,
  val metadata: Map<String, String> = emptyMap()
) {
  init {
    require(sourceStartUs >= 0L)
    require(sourceEndUs >= sourceStartUs)
    require(timelineStartFrame >= 0L)
    require(timelineEndFrame >= timelineStartFrame)
    require(speed > 0f)
  }

  val durationFrames: Long get() = timelineEndFrame - timelineStartFrame
  val range: TimelineRange get() = TimelineRange(timelineStartFrame, timelineEndFrame)
}

data class TimelineState(
  val project: CoreProject,
  val selectedClipId: String? = null,
  val playheadFrame: Long = 0L
) {
  init { require(playheadFrame >= 0L) }

  fun replaceTrack(track: CoreTrack): TimelineState = copy(
    project = project.copy(tracks = project.tracks.map { if (it.id == track.id) track.sorted() else it })
  )

  fun track(trackId: String): CoreTrack =
    project.tracks.firstOrNull { it.id == trackId } ?: error("Unknown track: $trackId")

  fun clip(clipId: String): Pair<CoreTrack, CoreClip> {
    project.tracks.forEach { track ->
      track.clips.firstOrNull { it.id == clipId }?.let { return track to it }
    }
    error("Unknown clip: $clipId")
  }
}
