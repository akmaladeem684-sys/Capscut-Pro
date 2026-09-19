package com.example.ui.components.timeline

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.engine.media.VideoThumbnailManager
import com.example.ui.theme.CyanAccent
import kotlin.math.ceil

/**
 * Renders a continuous, high-performance video thumbnail filmstrip for a timeline clip.
 * Automatically samples source frames from [sourceStartMs] to [sourceEndMs],
 * recalculates density upon zoom (msPerPixel changes), and displays frames asynchronously.
 */
@Composable
fun VideoFilmstripView(
  clipId: String,
  uri: String,
  timelineStartMs: Long,
  durationMs: Long,
  sourceStartMs: Long = 0L,
  sourceEndMs: Long = durationMs,
  speed: Float = 1.0f,
  isReversed: Boolean = false,
  isVideo: Boolean = true,
  clipWidthDp: Dp,
  clipHeightDp: Dp,
  currentPlayheadMs: Long? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  // Compute adaptive thumbnail tile width based on clip height (~50-60dp wide per frame)
  val tileWidthDp = remember(clipHeightDp) {
    (clipHeightDp * 0.82f).coerceIn(42.dp, 68.dp)
  }

  val tileCount = remember(clipWidthDp, tileWidthDp) {
    maxOf(1, ceil(clipWidthDp.value / tileWidthDp.value).toInt())
  }

  // Calculate the exact source timestamp in milliseconds for each thumbnail tile
  val frameTimestampsMs = remember(
    clipId, uri, durationMs, sourceStartMs, sourceEndMs, speed, isReversed, tileCount
  ) {
    List(tileCount) { index ->
      val progress = if (tileCount == 1) 0.5f else (index.toFloat() / (tileCount - 1).coerceAtLeast(1))
      val offsetMs = (progress * durationMs).toLong()
      val effectiveSourceStart = sourceStartMs.coerceAtLeast(0L)
      val effectiveSourceEnd = if (sourceEndMs > sourceStartMs) sourceEndMs else (effectiveSourceStart + durationMs)

      if (isReversed) {
        (effectiveSourceEnd - (offsetMs * speed).toLong()).coerceIn(effectiveSourceStart, effectiveSourceEnd)
      } else {
        (effectiveSourceStart + (offsetMs * speed).toLong()).coerceIn(effectiveSourceStart, effectiveSourceEnd)
      }
    }
  }

  // Active playhead progress within clip (0..1) if playhead is currently positioned on this clip
  val playheadProgress = remember(currentPlayheadMs, timelineStartMs, durationMs) {
    if (currentPlayheadMs != null && currentPlayheadMs in timelineStartMs..(timelineStartMs + durationMs) && durationMs > 0L) {
      (currentPlayheadMs - timelineStartMs).toFloat() / durationMs.toFloat()
    } else null
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .clip(RoundedCornerShape(6.dp))
      .background(Color(0xFF0F131A))
  ) {
    // 1. Continuous Row of Video Frame Thumbnails
    Row(
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = Arrangement.Start
    ) {
      frameTimestampsMs.forEachIndexed { index, sourceTimeMs ->
        val isFirst = index == 0
        val isLast = index == tileCount - 1

        ThumbnailTile(
          context = context,
          uri = uri,
          sourceTimeMs = sourceTimeMs,
          isVideo = isVideo,
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .testTag("thumbnail_tile_${clipId}_$index")
        )
      }
    }

    // 2. Filmstrip Frame Separator Grid Overlay (subtle frame boundaries)
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      val count = tileCount
      if (count > 1) {
        val step = w / count
        for (i in 1 until count) {
          val x = i * step
          // Frame separator line
          drawLine(
            color = Color.Black.copy(alpha = 0.4f),
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = 0.8.dp.toPx()
          )
        }
      }
    }
  }
}

@Composable
private fun ThumbnailTile(
  context: android.content.Context,
  uri: String,
  sourceTimeMs: Long,
  isVideo: Boolean,
  modifier: Modifier = Modifier
) {
  val quantizedTimeMs = remember(sourceTimeMs) {
    (sourceTimeMs.coerceAtLeast(0L) / 200L) * 200L
  }

  var bitmap by remember(uri, quantizedTimeMs, isVideo) {
    val key = VideoThumbnailManager.makeKey(uri, quantizedTimeMs, 120, 120)
    mutableStateOf(VideoThumbnailManager.getCachedThumbnail(key))
  }

  LaunchedEffect(uri, quantizedTimeMs, isVideo) {
    if (bitmap == null) {
      val result = VideoThumbnailManager.getThumbnail(
        context = context,
        uri = uri,
        sourceTimeMs = quantizedTimeMs,
        targetWidth = 120,
        targetHeight = 120,
        isVideo = isVideo
      )
      if (result != null && !result.isRecycled) {
        bitmap = result
      }
    }
  }

  Box(
    modifier = modifier
      .background(Color(0xFF141923))
  ) {
    val currentBmp = bitmap
    if (currentBmp != null && !currentBmp.isRecycled) {
      Image(
        bitmap = currentBmp.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      // Shimmer / Placeholder while decoding
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.linearGradient(
              listOf(
                Color(0xFF161C28),
                Color(0xFF222B3D),
                Color(0xFF161C28)
              )
            )
          )
      )
    }
  }
}
