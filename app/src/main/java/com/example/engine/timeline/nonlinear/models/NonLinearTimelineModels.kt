package com.example.engine.timeline.nonlinear.models

import java.util.UUID

/**
 * Supported track types in the non-linear multi-track timeline.
 */
enum class TrackType(val displayName: String, val priority: Int) {
  VIDEO("Video Track", 10),
  OVERLAY("Overlay Element", 20),
  TEXT("Text & Titles", 30),
  FX("Visual Effects", 40),
  AUDIO("Audio Track", 5)
}

/**
 * Which edge of a clip is being trimmed.
 */
enum class TrimEdge {
  HEAD, // Left / in-point edge
  TAIL  // Right / out-point edge
}

/**
 * Type of anchor a magnetic snap locked onto.
 */
enum class SnapType {
  NONE,
  PLAYHEAD,
  CLIP_START,
  CLIP_END,
  MARKER
}

/**
 * Result of magnetic snapping calculation.
 */
data class SnapResult(
  val originalTimeUs: Long,
  val snappedTimeUs: Long,
  val snapTargetTimeUs: Long,
  val isSnapped: Boolean,
  val snapType: SnapType = SnapType.NONE,
  val guideLinePx: Float = 0f
)

/**
 * Timeline marker placed at a specific microsecond timestamp.
 */
data class TimelineMarker(
  val id: String = UUID.randomUUID().toString(),
  val timestampUs: Long,
  val label: String = "",
  val color: Long = 0xFFFFD700 // Gold default
)

/**
 * Immutable representation of a Clip in a non-linear track.
 *
 * @param id Unique identifier
 * @param name Display label
 * @param sourceUri File or content URI of media asset
 * @param startTimeUs Timeline placement start time in microseconds
 * @param durationUs Visible duration on timeline in microseconds
 * @param sourceTrimStartUs In-point offset inside original media asset in microseconds
 * @param sourceDurationUs Total media asset duration in microseconds
 * @param zIndex Rendering stacking order
 * @param speed Playback speed multiplier (default 1.0f)
 * @param volume Audio volume multiplier (0.0f to 2.0f, default 1.0f)
 * @param transformMatrix 4x4 OpenGL column-major transformation matrix
 * @param translationX Normalized X translation (-1f to 1f)
 * @param translationY Normalized Y translation (-1f to 1f)
 * @param scaleX Horizontal scale (default 1.0f)
 * @param scaleY Vertical scale (default 1.0f)
 * @param rotation Rotation in degrees (0 to 360)
 * @param alpha Opacity / blend factor (0.0f to 1.0f)
 * @param isSelected Selection flag for UI interaction
 * @param isMuted Whether audio is suppressed
 * @param hasAudio Whether clip contains an audio stream
 * @param isVideo Whether clip contains video
 * @param thumbnailUri Optional thumbnail preview image
 * @param waveformPoints Optional pre-extracted audio waveform peak data
 */
data class Clip(
  val id: String = UUID.randomUUID().toString(),
  val name: String = "Clip",
  val sourceUri: String = "",
  val startTimeUs: Long = 0L,
  val durationUs: Long = 3_000_000L, // 3 seconds default
  val sourceTrimStartUs: Long = 0L,
  val sourceDurationUs: Long = 3_000_000L,
  val zIndex: Int = 0,
  val speed: Float = 1.0f,
  val volume: Float = 1.0f,
  val transformMatrix: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f },
  val translationX: Float = 0f,
  val translationY: Float = 0f,
  val scaleX: Float = 1.0f,
  val scaleY: Float = 1.0f,
  val rotation: Float = 0f,
  val alpha: Float = 1.0f,
  val isSelected: Boolean = false,
  val isMuted: Boolean = false,
  val hasAudio: Boolean = true,
  val isVideo: Boolean = true,
  val thumbnailUri: String? = null,
  val waveformPoints: FloatArray? = null
) {
  init {
    require(startTimeUs >= 0L) { "startTimeUs cannot be negative: $startTimeUs" }
    require(durationUs > 0L) { "durationUs must be positive: $durationUs" }
    require(sourceTrimStartUs >= 0L) { "sourceTrimStartUs cannot be negative: $sourceTrimStartUs" }
    require(sourceDurationUs >= 0L) { "sourceDurationUs cannot be negative: $sourceDurationUs" }
    require(speed > 0f) { "speed must be positive: $speed" }
  }

  val endTimeUs: Long get() = startTimeUs + durationUs

  fun containsTimestamp(timestampUs: Long): Boolean =
    timestampUs >= startTimeUs && timestampUs < endTimeUs

  fun timelineToSourceUs(timelineUs: Long): Long {
    val offset = (timelineUs - startTimeUs).coerceIn(0L, durationUs)
    val scaledOffset = (offset * speed).toLong()
    return (sourceTrimStartUs + scaledOffset).coerceIn(sourceTrimStartUs, sourceDurationUs)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Clip) return false
    return id == other.id &&
        startTimeUs == other.startTimeUs &&
        durationUs == other.durationUs &&
        sourceTrimStartUs == other.sourceTrimStartUs &&
        sourceDurationUs == other.sourceDurationUs &&
        zIndex == other.zIndex &&
        speed == other.speed &&
        volume == other.volume &&
        translationX == other.translationX &&
        translationY == other.translationY &&
        scaleX == other.scaleX &&
        scaleY == other.scaleY &&
        rotation == other.rotation &&
        alpha == other.alpha &&
        isSelected == other.isSelected &&
        isMuted == other.isMuted
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + startTimeUs.hashCode()
    result = 31 * result + durationUs.hashCode()
    result = 31 * result + zIndex
    result = 31 * result + isSelected.hashCode()
    return result
  }
}

/**
 * Immutable representation of a non-linear Track.
 * Allows multiple clips per track with blank gaps between them.
 */
data class Track(
  val id: String = UUID.randomUUID().toString(),
  val name: String = "Track",
  val type: TrackType = TrackType.VIDEO,
  val zIndex: Int = 0,
  val isMuted: Boolean = false,
  val isLocked: Boolean = false,
  val isHidden: Boolean = false,
  val clips: List<Clip> = emptyList()
) {
  val sortedClips: List<Clip> get() = clips.sortedBy { it.startTimeUs }

  val maxEndTimeUs: Long
    get() = clips.maxOfOrNull { it.endTimeUs } ?: 0L

  fun findClip(clipId: String): Clip? = clips.firstOrNull { it.id == clipId }

  fun hasCollision(startUs: Long, durationUs: Long, excludeClipId: String? = null): Boolean {
    val endUs = startUs + durationUs
    return clips.any { clip ->
      if (clip.id == excludeClipId) false
      else startUs < clip.endTimeUs && clip.startTimeUs < endUs
    }
  }
}

/**
 * Master immutable state of the non-linear multi-track timeline.
 */
data class TimelineState(
  val id: String = UUID.randomUUID().toString(),
  val name: String = "Untitled Project",
  val tracks: List<Track> = emptyList(),
  val playheadUs: Long = 0L,
  val selectedClipId: String? = null,
  val zoomLevelPxPerSec: Float = 100f,
  val scrollOffsetPx: Float = 0f,
  val markers: List<TimelineMarker> = emptyList(),
  val snapEnabled: Boolean = true,
  val snapThresholdPx: Float = 15f
) {
  init {
    require(playheadUs >= 0L) { "playheadUs cannot be negative: $playheadUs" }
    require(zoomLevelPxPerSec > 0f) { "zoomLevelPxPerSec must be positive: $zoomLevelPxPerSec" }
  }

  val totalDurationUs: Long
    get() = maxOf(1_000_000L, tracks.maxOfOrNull { it.maxEndTimeUs } ?: 1_000_000L)

  fun findClip(clipId: String): Pair<Track, Clip>? {
    for (track in tracks) {
      val clip = track.findClip(clipId)
      if (clip != null) return Pair(track, clip)
    }
    return null
  }

  fun findTrack(trackId: String): Track? = tracks.firstOrNull { it.id == trackId }

  fun selectedClip(): Clip? = selectedClipId?.let { findClip(it)?.second }

  fun recomputeZIndexes(): TimelineState {
    var z = 0
    val updatedTracks = tracks.map { track ->
      val updatedClips = track.clips.map { clip ->
        clip.copy(zIndex = z++)
      }
      track.copy(zIndex = z++, clips = updatedClips)
    }
    return copy(tracks = updatedTracks)
  }
}
