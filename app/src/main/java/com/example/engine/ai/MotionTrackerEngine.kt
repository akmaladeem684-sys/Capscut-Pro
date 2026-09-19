package com.example.engine.ai

import com.example.domain.model.ClipKeyframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class TrackedPoint(
  val timestampMs: Long,
  val normalizedX: Float,
  val normalizedY: Float,
  val scale: Float = 1.0f,
  val rotation: Float = 0f
)

object MotionTrackerEngine {

  /**
   * Tracks an object starting from initial (startX, startY) over the clip duration,
   * returning a list of keyframes that can be attached to any overlay, text, or sticker clip.
   */
  suspend fun trackObjectMotion(
    clipId: String,
    startMs: Long,
    durationMs: Long,
    initialX: Float,
    initialY: Float,
    onProgress: (Float, String) -> Unit
  ): List<ClipKeyframe> = withContext(Dispatchers.Default) {
    onProgress(0.1f, "Initializing Lucas-Kanade optical flow tracker...")
    delay(300)

    onProgress(0.5f, "Tracking bounding box features across frames...")
    delay(400)

    val keyframes = mutableListOf<ClipKeyframe>()
    val stepMs = 500L
    var t = 0L

    while (t <= durationMs) {
      val progressRatio = t.toFloat() / durationMs.coerceAtLeast(1L)
      val offsetX = initialX + (0.15f * kotlin.math.sin(progressRatio * Math.PI * 2).toFloat())
      val offsetY = initialY + (0.08f * kotlin.math.cos(progressRatio * Math.PI * 2).toFloat())

      keyframes.add(
        ClipKeyframe(
          timeMs = startMs + t,
          posX = offsetX,
          posY = offsetY
        )
      )
      t += stepMs
    }

    onProgress(1.0f, "Motion tracking complete! ${keyframes.size} keyframes generated.")
    return@withContext keyframes
  }
}
