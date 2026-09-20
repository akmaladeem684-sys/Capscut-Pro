package com.example.engine.timeline.nonlinear.adapter

import com.example.domain.model.Timeline
import com.example.domain.model.TrackType as DomainTrackType
import com.example.engine.SelectedTrackElement
import com.example.engine.TimelineEngine
import com.example.engine.playback.VideoPlaybackEngine
import com.example.engine.timeline.nonlinear.models.*
import com.example.engine.timeline.nonlinear.models.TrackType as NlTrackType

/**
 * High-performance bidirectional adapter between Domain Timeline and Non-Linear Timeline models.
 * Bridges all NLE operations (moving, trimming, splitting, ripple/gap delete, muting, locking)
 * directly into the application's core TimelineEngine and PlaybackEngine.
 */
object NonLinearTimelineAdapter {

  /**
   * Converts a domain Timeline into the Non-Linear TimelineState with microsecond precision.
   */
  fun toTimelineState(
    timeline: Timeline,
    playheadMs: Long,
    selectedElement: SelectedTrackElement = SelectedTrackElement.None,
    zoomLevelPxPerSec: Float = 100f
  ): TimelineState {
    val selectedId = when (selectedElement) {
      is SelectedTrackElement.Video -> selectedElement.clipId
      is SelectedTrackElement.Overlay -> selectedElement.clipId
      is SelectedTrackElement.Audio -> selectedElement.clipId
      is SelectedTrackElement.Text -> selectedElement.clipId
      is SelectedTrackElement.Sticker -> selectedElement.clipId
      is SelectedTrackElement.Effect -> selectedElement.clipId
      else -> null
    }

    val tracks = mutableListOf<Track>()

    // 1. Main Video Track
    val videoClips = timeline.videoClips.map { v ->
      val durationMs = v.durationMs.coerceAtLeast(100L)
      val totalMediaMs = if (v.sourceTotalDurationMs > 0L) v.sourceTotalDurationMs else maxOf(v.sourceEndMs, durationMs)
      Clip(
        id = v.id,
        name = v.name.ifBlank { "Video" },
        sourceUri = v.uri,
        startTimeUs = v.timelineStartMs * 1000L,
        durationUs = durationMs * 1000L,
        sourceTrimStartUs = v.sourceStartMs * 1000L,
        sourceDurationUs = totalMediaMs * 1000L,
        zIndex = 10,
        speed = v.speed.coerceAtLeast(0.1f),
        volume = v.volume,
        rotation = v.rotationDegrees.toFloat(),
        alpha = v.opacity,
        isSelected = v.id == selectedId,
        isMuted = v.isMuted,
        hasAudio = v.hasAudio,
        isVideo = true
      )
    }.sortedBy { it.startTimeUs }

    val videoSettings = timeline.trackSettings[DomainTrackType.MAIN_VIDEO]
    tracks.add(
      Track(
        id = "track_video_main",
        name = "Video 1",
        type = NlTrackType.VIDEO,
        zIndex = 10,
        isMuted = videoSettings?.isMuted ?: false,
        isLocked = videoSettings?.isLocked ?: false,
        isHidden = videoSettings?.isHidden ?: false,
        clips = videoClips
      )
    )

    // 2. Overlay / PiP Track
    val overlayClips = timeline.overlayClips.map { o ->
      val durationMs = o.durationMs.coerceAtLeast(100L)
      val totalMediaMs = if (o.sourceTotalDurationMs > 0L) o.sourceTotalDurationMs else maxOf(o.sourceEndMs, durationMs)
      Clip(
        id = o.id,
        name = o.name.ifBlank { "Overlay" },
        sourceUri = o.uri,
        startTimeUs = o.timelineStartMs * 1000L,
        durationUs = durationMs * 1000L,
        sourceTrimStartUs = o.sourceStartMs * 1000L,
        sourceDurationUs = totalMediaMs * 1000L,
        zIndex = 20,
        speed = o.speed.coerceAtLeast(0.1f),
        volume = o.volume,
        rotation = o.rotationDegrees.toFloat(),
        alpha = o.opacity,
        isSelected = o.id == selectedId,
        isMuted = o.isMuted,
        hasAudio = o.hasAudio,
        isVideo = true
      )
    }.sortedBy { it.startTimeUs }

    val overlaySettings = timeline.trackSettings[DomainTrackType.OVERLAY]
    tracks.add(
      Track(
        id = "track_overlay",
        name = "Overlay (PiP)",
        type = NlTrackType.OVERLAY,
        zIndex = 20,
        isMuted = overlaySettings?.isMuted ?: false,
        isLocked = overlaySettings?.isLocked ?: false,
        isHidden = overlaySettings?.isHidden ?: false,
        clips = overlayClips
      )
    )

    // 3. Audio Track
    val audioClips = timeline.audioClips.map { a ->
      val durationMs = a.durationMs.coerceAtLeast(100L)
      val totalMediaMs = if (a.sourceEndMs > 0L) a.sourceEndMs else durationMs
      Clip(
        id = a.id,
        name = a.title.ifBlank { "Audio" },
        sourceUri = a.uri,
        startTimeUs = a.timelineStartMs * 1000L,
        durationUs = durationMs * 1000L,
        sourceTrimStartUs = a.sourceStartMs * 1000L,
        sourceDurationUs = totalMediaMs * 1000L,
        zIndex = 5,
        speed = a.speed.coerceAtLeast(0.1f),
        volume = a.volume,
        isSelected = a.id == selectedId,
        isMuted = a.isMuted,
        hasAudio = true,
        isVideo = false
      )
    }.sortedBy { it.startTimeUs }

    val audioSettings = timeline.trackSettings[DomainTrackType.AUDIO]
    tracks.add(
      Track(
        id = "track_audio",
        name = "Audio 1",
        type = NlTrackType.AUDIO,
        zIndex = 5,
        isMuted = audioSettings?.isMuted ?: false,
        isLocked = audioSettings?.isLocked ?: false,
        isHidden = audioSettings?.isHidden ?: false,
        clips = audioClips
      )
    )

    // 4. Text & Titles Track
    val textClips = timeline.textClips.map { t ->
      Clip(
        id = t.id,
        name = t.text.take(16).ifBlank { "Text" },
        sourceUri = "",
        startTimeUs = t.timelineStartMs * 1000L,
        durationUs = t.durationMs.coerceAtLeast(100L) * 1000L,
        sourceTrimStartUs = 0L,
        sourceDurationUs = t.durationMs * 1000L,
        zIndex = 30,
        isSelected = t.id == selectedId,
        hasAudio = false,
        isVideo = false
      )
    }.sortedBy { it.startTimeUs }

    val textSettings = timeline.trackSettings[DomainTrackType.TEXT]
    tracks.add(
      Track(
        id = "track_text",
        name = "Text & Titles",
        type = NlTrackType.TEXT,
        zIndex = 30,
        isMuted = textSettings?.isMuted ?: false,
        isLocked = textSettings?.isLocked ?: false,
        isHidden = textSettings?.isHidden ?: false,
        clips = textClips
      )
    )

    // 5. Visual Effects Track
    if (timeline.effectClips.isNotEmpty()) {
      val fxClips = timeline.effectClips.map { e ->
        Clip(
          id = e.id,
          name = e.customName.ifBlank { e.effectType.displayName },
          sourceUri = "",
          startTimeUs = e.timelineStartMs * 1000L,
          durationUs = e.durationMs.coerceAtLeast(100L) * 1000L,
          sourceTrimStartUs = 0L,
          sourceDurationUs = e.durationMs * 1000L,
          zIndex = 40,
          isSelected = e.id == selectedId,
          hasAudio = false,
          isVideo = false
        )
      }.sortedBy { it.startTimeUs }

      val fxSettings = timeline.trackSettings[DomainTrackType.EFFECT]
      tracks.add(
        Track(
          id = "track_fx",
          name = "Effects",
          type = NlTrackType.FX,
          zIndex = 40,
          isMuted = fxSettings?.isMuted ?: false,
          isLocked = fxSettings?.isLocked ?: false,
          isHidden = fxSettings?.isHidden ?: false,
          clips = fxClips
        )
      )
    }

    return TimelineState(
      tracks = tracks,
      playheadUs = (playheadMs * 1000L).coerceAtLeast(0L),
      selectedClipId = selectedId,
      zoomLevelPxPerSec = zoomLevelPxPerSec
    ).recomputeZIndexes()
  }

  /**
   * Dispatches a TimelineAction directly to the TimelineEngine & PlaybackEngine.
   */
  fun dispatchActionToTimelineEngine(
    action: TimelineAction,
    timeline: Timeline,
    timelineEngine: TimelineEngine,
    playbackEngine: VideoPlaybackEngine? = null,
    onSeekScrub: ((Long) -> Unit)? = null
  ) {
    when (action) {
      is TimelineAction.SetPlayhead -> {
        val posMs = (action.timestampUs / 1000L).coerceAtLeast(0L)
        timelineEngine.seekTo(posMs)
        playbackEngine?.seekTo(posMs)
        onSeekScrub?.invoke(posMs)
      }

      is TimelineAction.MoveClip -> {
        val newStartMs = (action.newStartTimeUs / 1000L).coerceAtLeast(0L)
        if (action.targetTrackId == "track_overlay") {
          timelineEngine.moveClipToTrackLane(action.clipId, DomainTrackType.OVERLAY, 0)
        } else if (action.targetTrackId == "track_video_main") {
          timelineEngine.moveClipToTrackLane(action.clipId, DomainTrackType.MAIN_VIDEO, 0)
        }
        timelineEngine.moveClip(action.clipId, newStartMs, snap = false)
      }

      is TimelineAction.TrimClip -> {
        val deltaMs = action.deltaUs / 1000L
        if (action.edge == TrimEdge.HEAD) {
          timelineEngine.trimClipLeftByDelta(action.clipId, deltaMs, snap = false)
        } else {
          timelineEngine.trimClipRightByDelta(action.clipId, deltaMs, snap = false)
        }
      }

      is TimelineAction.SplitClip -> {
        val splitMs = action.splitTimestampUs / 1000L
        timelineEngine.splitClipAtTime(action.clipId, splitMs)
      }

      is TimelineAction.RippleDelete -> {
        selectClipInTimeline(action.clipId, timeline, timelineEngine)
        timelineEngine.rippleDelete()
      }

      is TimelineAction.GapDelete -> {
        selectClipInTimeline(action.clipId, timeline, timelineEngine)
        timelineEngine.normalDelete()
      }

      is TimelineAction.SelectClip -> {
        val id = action.clipId
        if (id != null) {
          selectClipInTimeline(id, timeline, timelineEngine)
        } else {
          timelineEngine.selectElement(SelectedTrackElement.None)
        }
      }

      is TimelineAction.ToggleTrackMute -> {
        val trackType = mapTrackIdToDomainType(action.trackId)
        if (trackType != null) {
          timelineEngine.toggleTrackMute(trackType)
        }
      }

      is TimelineAction.ToggleTrackLock -> {
        val trackType = mapTrackIdToDomainType(action.trackId)
        if (trackType != null) {
          timelineEngine.toggleTrackLock(trackType)
        }
      }

      else -> {
        // Handled locally
      }
    }
  }

  private fun selectClipInTimeline(clipId: String, timeline: Timeline, timelineEngine: TimelineEngine) {
    when {
      timeline.videoClips.any { it.id == clipId } -> {
        timelineEngine.selectElement(SelectedTrackElement.Video(clipId))
      }
      timeline.overlayClips.any { it.id == clipId } -> {
        timelineEngine.selectElement(SelectedTrackElement.Overlay(clipId))
      }
      timeline.audioClips.any { it.id == clipId } -> {
        timelineEngine.selectElement(SelectedTrackElement.Audio(clipId))
      }
      timeline.textClips.any { it.id == clipId } -> {
        timelineEngine.selectElement(SelectedTrackElement.Text(clipId))
      }
      timeline.effectClips.any { it.id == clipId } -> {
        timelineEngine.selectElement(SelectedTrackElement.Effect(clipId))
      }
      timeline.stickerClips.any { it.id == clipId } -> {
        timelineEngine.selectElement(SelectedTrackElement.Sticker(clipId))
      }
      else -> {
        timelineEngine.selectElement(SelectedTrackElement.None)
      }
    }
  }

  private fun mapTrackIdToDomainType(trackId: String): DomainTrackType? {
    return when (trackId) {
      "track_video_main" -> DomainTrackType.MAIN_VIDEO
      "track_overlay" -> DomainTrackType.OVERLAY
      "track_audio" -> DomainTrackType.AUDIO
      "track_text" -> DomainTrackType.TEXT
      "track_fx" -> DomainTrackType.EFFECT
      else -> null
    }
  }
}
