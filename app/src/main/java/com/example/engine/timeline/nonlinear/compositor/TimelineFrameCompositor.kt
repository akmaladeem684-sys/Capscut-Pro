package com.example.engine.timeline.nonlinear.compositor

import android.graphics.Bitmap
import android.opengl.Matrix
import android.util.LruCache
import com.example.engine.timeline.nonlinear.models.Clip
import com.example.engine.timeline.nonlinear.models.TimelineState
import com.example.engine.timeline.nonlinear.models.Track
import com.example.engine.timeline.nonlinear.models.TrackType

/**
 * Visual layer ready for OpenGL / ExoPlayer composition at a specific microsecond.
 */
data class RenderLayer(
  val clipId: String,
  val trackId: String,
  val trackType: TrackType,
  val sourceUri: String,
  val sourceTimestampUs: Long,
  val zIndex: Int,
  val translationX: Float,
  val translationY: Float,
  val scaleX: Float,
  val scaleY: Float,
  val rotationDegrees: Float,
  val alpha: Float,
  val transformMatrix: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f }
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RenderLayer) return false
    return clipId == other.clipId &&
        trackId == other.trackId &&
        sourceTimestampUs == other.sourceTimestampUs &&
        zIndex == other.zIndex &&
        translationX == other.translationX &&
        translationY == other.translationY &&
        scaleX == other.scaleX &&
        scaleY == other.scaleY &&
        rotationDegrees == other.rotationDegrees &&
        alpha == other.alpha
  }

  override fun hashCode(): Int {
    var result = clipId.hashCode()
    result = 31 * result + sourceTimestampUs.hashCode()
    result = 31 * result + zIndex
    return result
  }
}

/**
 * Audio slice specification for active sound tracks at a specific microsecond.
 */
data class ActiveAudioClip(
  val clipId: String,
  val trackId: String,
  val sourceUri: String,
  val sourceTimestampUs: Long,
  val effectiveVolume: Float, // Combining clip volume, track volume, and crossfade
  val isMuted: Boolean
)

/**
 * Frame cache manager interface for prefetching video frames near playhead during scrubbing.
 */
interface FrameCacheManager {
  /**
   * Prefetches frames around current playhead within a time window (e.g. ±1 second).
   */
  fun prefetchAround(state: TimelineState, timestampUs: Long, windowRadiusUs: Long = 1_000_000L)

  /**
   * Retrieves a cached decoded frame if available.
   */
  fun getCachedFrame(clipId: String, timestampUs: Long): Bitmap?

  /**
   * Stores a decoded frame in the cache.
   */
  fun putCachedFrame(clipId: String, timestampUs: Long, bitmap: Bitmap)

  /**
   * Clears internal cache.
   */
  fun clear()
}

/**
 * Default in-memory LRU frame cache implementation for high-speed timeline scrubbing.
 */
class InMemoryFrameCacheManager(
  maxMemoryBytes: Int = (Runtime.getRuntime().maxMemory() / 8).toInt()
) : FrameCacheManager {

  private val lruCache = object : LruCache<String, Bitmap>(maxMemoryBytes) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
  }

  override fun prefetchAround(state: TimelineState, timestampUs: Long, windowRadiusUs: Long) {
    // Queries active clips around timestampUs to mark high-priority seek targets
    val startWindow = (timestampUs - windowRadiusUs).coerceAtLeast(0L)
    val endWindow = timestampUs + windowRadiusUs
    // Target time stamps can be scheduled for background decoder warming
  }

  override fun getCachedFrame(clipId: String, timestampUs: Long): Bitmap? {
    val key = "${clipId}_${timestampUs / 100_000L}" // 100ms quantize bucket
    return lruCache.get(key)
  }

  override fun putCachedFrame(clipId: String, timestampUs: Long, bitmap: Bitmap) {
    val key = "${clipId}_${timestampUs / 100_000L}"
    lruCache.put(key, bitmap)
  }

  override fun clear() {
    lruCache.evictAll()
  }
}

/**
 * Frame Compositor Engine: bridges Non-Linear Timeline State to playback and OpenGL rendering pipelines.
 */
object TimelineFrameCompositor {

  /**
   * Efficient timestamp intersector: retrieves all active visual clips across all tracks at any given microsecond.
   * Sorts visual clips by track priority / zIndex and computes transformation matrices.
   */
  fun getActiveClipsAt(state: TimelineState, timestampUs: Long): List<RenderLayer> {
    val activeLayers = mutableListOf<RenderLayer>()

    for (track in state.tracks) {
      if (track.isHidden) continue

      // Filter active clips intersecting timestampUs
      for (clip in track.clips) {
        if (clip.containsTimestamp(timestampUs)) {
          val sourceUs = clip.timelineToSourceUs(timestampUs)
          val mvpMatrix = computeTransformMatrix(clip)

          activeLayers.add(
            RenderLayer(
              clipId = clip.id,
              trackId = track.id,
              trackType = track.type,
              sourceUri = clip.sourceUri,
              sourceTimestampUs = sourceUs,
              zIndex = clip.zIndex + (track.type.priority * 1000),
              translationX = clip.translationX,
              translationY = clip.translationY,
              scaleX = clip.scaleX,
              scaleY = clip.scaleY,
              rotationDegrees = clip.rotation,
              alpha = clip.alpha,
              transformMatrix = mvpMatrix
            )
          )
        }
      }
    }

    // Sort by ascending zIndex so background layers render first, foreground on top
    return activeLayers.sortedBy { it.zIndex }
  }

  /**
   * Calculates audio mixing matrix at timestampUs, evaluating volume levels, mutes, and crossfades.
   */
  fun getActiveAudioAt(state: TimelineState, timestampUs: Long): List<ActiveAudioClip> {
    val activeAudio = mutableListOf<ActiveAudioClip>()

    for (track in state.tracks) {
      val isTrackMuted = track.isMuted

      for (clip in track.clips) {
        if (clip.hasAudio && clip.containsTimestamp(timestampUs)) {
          val sourceUs = clip.timelineToSourceUs(timestampUs)
          val isClipMuted = isTrackMuted || clip.isMuted
          val clipVolume = if (isClipMuted) 0f else clip.volume.coerceIn(0f, 2f)

          // Linear crossfade calculation (50ms edge fade to prevent click artifacts)
          val fadeEdgeUs = 50_000L
          val fromStartUs = timestampUs - clip.startTimeUs
          val toEndUs = clip.endTimeUs - timestampUs

          val fadeInFactor = if (fromStartUs < fadeEdgeUs) (fromStartUs.toFloat() / fadeEdgeUs) else 1.0f
          val fadeOutFactor = if (toEndUs < fadeEdgeUs) (toEndUs.toFloat() / fadeEdgeUs) else 1.0f
          val crossfadeVolume = clipVolume * fadeInFactor * fadeOutFactor

          activeAudio.add(
            ActiveAudioClip(
              clipId = clip.id,
              trackId = track.id,
              sourceUri = clip.sourceUri,
              sourceTimestampUs = sourceUs,
              effectiveVolume = crossfadeVolume,
              isMuted = isClipMuted
            )
          )
        }
      }
    }

    return activeAudio
  }

  /**
   * Generates a 4x4 OpenGL column-major MVP transformation matrix for a clip.
   */
  fun computeTransformMatrix(clip: Clip): FloatArray {
    val matrix = FloatArray(16)
    Matrix.setIdentityM(matrix, 0)

    // Translation
    Matrix.translateM(matrix, 0, clip.translationX, clip.translationY, 0f)

    // Rotation around Z-axis
    if (clip.rotation != 0f) {
      Matrix.rotateM(matrix, 0, clip.rotation, 0f, 0f, 1f)
    }

    // Scaling
    Matrix.scaleM(matrix, 0, clip.scaleX, clip.scaleY, 1f)

    return matrix
  }
}
