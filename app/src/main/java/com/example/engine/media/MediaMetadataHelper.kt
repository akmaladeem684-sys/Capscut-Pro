package com.example.engine.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log

data class RealMediaMetadata(
  val durationMs: Long,
  val width: Int,
  val height: Int,
  val rotationDegrees: Int,
  val frameRate: Float,
  val mimeType: String,
  val hasAudio: Boolean,
  val isVideo: Boolean
)

object MediaMetadataHelper {
  private const val TAG = "MediaMetadataHelper"

  /**
   * Reads actual media metadata using MediaMetadataRetriever and BitmapFactory.
   * Handles content:// URIs, file:// URIs, and fallback defaults.
   */
  fun extractMetadata(context: Context, uriString: String, defaultImageDurationMs: Long = 3000L): RealMediaMetadata {
    if (uriString.isBlank()) {
      return RealMediaMetadata(
        durationMs = 4000L,
        width = 1920,
        height = 1080,
        rotationDegrees = 0,
        frameRate = 30f,
        mimeType = "video/mp4",
        hasAudio = true,
        isVideo = true
      )
    }

    val uri = try {
      Uri.parse(uriString)
    } catch (e: Exception) {
      null
    }

    val contentResolver = context.contentResolver
    val mimeType = try {
      if (uri != null && uri.scheme == "content") {
        contentResolver.getType(uri) ?: ""
      } else ""
    } catch (e: Exception) {
      ""
    }

    val isImageMime = mimeType.startsWith("image/") ||
      uriString.endsWith(".jpg", ignoreCase = true) ||
      uriString.endsWith(".jpeg", ignoreCase = true) ||
      uriString.endsWith(".png", ignoreCase = true) ||
      uriString.endsWith(".webp", ignoreCase = true)

    if (isImageMime) {
      var width = 1080
      var height = 1920
      try {
        if (uri != null) {
          contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
              width = options.outWidth
              height = options.outHeight
            }
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed reading image bounds for $uriString", e)
      }

      return RealMediaMetadata(
        durationMs = defaultImageDurationMs,
        width = width,
        height = height,
        rotationDegrees = 0,
        frameRate = 30f,
        mimeType = if (mimeType.isNotBlank()) mimeType else "image/jpeg",
        hasAudio = false,
        isVideo = false
      )
    }

    // Try MediaMetadataRetriever for video / audio
    val retriever = MediaMetadataRetriever()
    try {
      if (uri != null && (uri.scheme == "content" || uri.scheme == "file")) {
        retriever.setDataSource(context, uri)
      } else {
        retriever.setDataSource(uriString)
      }

      val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
      val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
      val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
      val hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
      val extractedMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
      val captureFpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)

      val durationMs = durationStr?.toLongOrNull()?.coerceAtLeast(300L) ?: 4000L
      var width = widthStr?.toIntOrNull() ?: 1920
      var height = heightStr?.toIntOrNull() ?: 1080
      val rotation = rotationStr?.toIntOrNull() ?: 0
      val hasAudio = hasAudioStr?.equals("yes", ignoreCase = true) ?: true
      val frameRate = captureFpsStr?.toFloatOrNull()?.takeIf { it in 10f..120f } ?: 30.0f
      val finalMime = extractedMime ?: if (mimeType.isNotBlank()) mimeType else "video/mp4"

      // Account for 90 or 270 degree rotation if width/height are unrotated
      val effectiveWidth = if (rotation == 90 || rotation == 270) height else width
      val effectiveHeight = if (rotation == 90 || rotation == 270) width else height

      return RealMediaMetadata(
        durationMs = durationMs,
        width = effectiveWidth,
        height = effectiveHeight,
        rotationDegrees = rotation,
        frameRate = frameRate,
        mimeType = finalMime,
        hasAudio = hasAudio,
        isVideo = true
      )
    } catch (e: Exception) {
      Log.w(TAG, "MediaMetadataRetriever failed for $uriString, attempting fallback image check", e)
      // Check if it's an image that didn't have mimeType set
      try {
        if (uri != null) {
          contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
              return RealMediaMetadata(
                durationMs = defaultImageDurationMs,
                width = options.outWidth,
                height = options.outHeight,
                rotationDegrees = 0,
                frameRate = 30f,
                mimeType = "image/jpeg",
                hasAudio = false,
                isVideo = false
              )
            }
          }
        }
      } catch (ignored: Exception) {}

      return RealMediaMetadata(
        durationMs = 4000L,
        width = 1920,
        height = 1080,
        rotationDegrees = 0,
        frameRate = 30f,
        mimeType = "video/mp4",
        hasAudio = true,
        isVideo = true
      )
    } finally {
      try {
        retriever.release()
      } catch (e: Exception) {
        // Ignored
      }
    }
  }
}
