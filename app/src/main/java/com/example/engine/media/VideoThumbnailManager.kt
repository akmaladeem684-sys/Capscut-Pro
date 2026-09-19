package com.example.engine.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * High-Performance, Multi-Tiered Video Thumbnail & Filmstrip Extraction Subsystem.
 *
 * Key Architecture Highlights:
 * - 2-Level Caching: Instant In-Memory LRU Cache (48MB) + Persistent Disk Cache on Local Storage.
 * - Hardware Decoder Concurrency Limiter (Semaphore) preventing native MediaServer starvation/crashes.
 * - In-flight job deduplication across timeline scrubbing and filmstrip tile requests.
 * - Automatic frame regeneration if cache is cleared or invalidated.
 * - Hardware rotation metadata detection with automatic orientation correction.
 * - Multi-stage fallback extraction (Closest Sync -> Closest -> First Frame -> Procedural Preview).
 */
object VideoThumbnailManager {

  private const val TAG = "VideoThumbnailManager"

  // Level 1: 48MB In-Memory Cache for instant UI tile rendering
  private val maxMemoryCacheBytes = 48 * 1024 * 1024
  private val memoryCache = object : LruCache<String, Bitmap>(maxMemoryCacheBytes) {
    override fun sizeOf(key: String, bitmap: Bitmap): Int {
      return bitmap.byteCount
    }
  }

  // Active in-flight coroutine Deferred tasks to deduplicate concurrent requests for the same key
  private val inFlightTasks = ConcurrentHashMap<String, Deferred<Bitmap?>>()

  // Concurrency limiter: At most 3 concurrent MediaMetadataRetriever decodes at once
  private val decoderSemaphore = Semaphore(3)

  // Dedicated background decoding scope with supervisor job
  private val thumbnailScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  /**
   * Generates a quantized cache key based on URI, timestamp, and target resolution.
   */
  fun makeKey(uri: String, sourceTimeMs: Long, targetWidth: Int, targetHeight: Int): String {
    val quantizedTime = (sourceTimeMs.coerceAtLeast(0L) / 200L) * 200L
    return "${uri}_${quantizedTime}_${targetWidth}x${targetHeight}"
  }

  /**
   * Retrieves a cached thumbnail synchronously from Memory Cache if present.
   */
  fun getCachedThumbnail(key: String): Bitmap? {
    val cached = memoryCache.get(key)
    if (cached != null && !cached.isRecycled) {
      return cached
    }
    return null
  }

  /**
   * Suspend function to retrieve or extract a thumbnail. Cooperates with coroutine cancellation.
   */
  suspend fun getThumbnail(
    context: Context,
    uri: String,
    sourceTimeMs: Long,
    targetWidth: Int = 120,
    targetHeight: Int = 120,
    isVideo: Boolean = true
  ): Bitmap? {
    val key = makeKey(uri, sourceTimeMs, targetWidth, targetHeight)
    val cached = memoryCache.get(key)
    if (cached != null && !cached.isRecycled) {
      return cached
    }
    return getOrExtractThumbnail(context.applicationContext, uri, sourceTimeMs, targetWidth, targetHeight, isVideo)
  }

  /**
   * Requests a thumbnail asynchronously.
   * Checks Memory Cache -> Checks Disk Cache -> Extracts from Video Decoder -> Persists to Caches.
   * Returns the Job so it can be cancelled if the caller is disposed.
   */
  fun requestThumbnail(
    context: Context,
    uri: String,
    sourceTimeMs: Long,
    targetWidth: Int = 120,
    targetHeight: Int = 120,
    isVideo: Boolean = true,
    onResult: (Bitmap) -> Unit
  ): kotlinx.coroutines.Job {
    val key = makeKey(uri, sourceTimeMs, targetWidth, targetHeight)
    val cached = memoryCache.get(key)
    if (cached != null && !cached.isRecycled) {
      onResult(cached)
      return kotlinx.coroutines.CompletableDeferred<Unit>().apply { complete(Unit) }
    }

    return thumbnailScope.launch {
      val bitmap = getOrExtractThumbnail(context.applicationContext, uri, sourceTimeMs, targetWidth, targetHeight, isVideo)
      if (bitmap != null && !bitmap.isRecycled) {
        withContext(Dispatchers.Main) {
          onResult(bitmap)
        }
      }
    }
  }

  /**
   * Synchronous or suspend extraction pipeline with multi-tier cache resolution.
   */
  suspend fun getOrExtractThumbnail(
    context: Context,
    uriString: String,
    sourceTimeMs: Long,
    targetWidth: Int,
    targetHeight: Int,
    isVideo: Boolean
  ): Bitmap? = withContext(Dispatchers.IO) {
    val key = makeKey(uriString, sourceTimeMs, targetWidth, targetHeight)

    // 1. Check Memory Cache
    val memCached = memoryCache.get(key)
    if (memCached != null && !memCached.isRecycled) {
      return@withContext memCached
    }

    // 2. Check Disk Cache
    val diskBitmap = loadFromDiskCache(context, key)
    if (diskBitmap != null && !diskBitmap.isRecycled) {
      memoryCache.put(key, diskBitmap)
      return@withContext diskBitmap
    }

    if (!currentCoroutineContext().isActive) return@withContext null

    // 3. Deduplicate in-flight extraction for the exact same key
    val existingDeferred = inFlightTasks[key]
    if (existingDeferred != null) {
      val result = existingDeferred.await()
      if (result != null && !result.isRecycled) {
        return@withContext result
      }
    }

    // 4. Launch new extraction task
    val newDeferred = async(Dispatchers.IO) {
      decoderSemaphore.withPermit {
        if (!currentCoroutineContext().isActive) return@withPermit null

        // Double-check memory cache after acquiring permit
        val doubleCheckMem = memoryCache.get(key)
        if (doubleCheckMem != null && !doubleCheckMem.isRecycled) {
          return@withPermit doubleCheckMem
        }

        val extracted = loadOrExtractRaw(context, uriString, sourceTimeMs, targetWidth, targetHeight, isVideo)
        if (extracted != null && !extracted.isRecycled) {
          memoryCache.put(key, extracted)
          // Save real media frames to persistent disk cache (avoid saving synthetic placeholders to disk)
          if (isVideo || (!uriString.startsWith("stock://") && !uriString.startsWith("sample://"))) {
            saveToDiskCache(context, key, extracted)
          }
        }
        extracted
      }
    }

    inFlightTasks[key] = newDeferred
    try {
      val result = newDeferred.await()
      result
    } finally {
      inFlightTasks.remove(key)
    }
  }

  /**
   * Raw extraction engine using MediaMetadataRetriever or BitmapFactory.
   */
  private fun loadOrExtractRaw(
    context: Context,
    uriString: String,
    sourceTimeMs: Long,
    targetWidth: Int,
    targetHeight: Int,
    isVideo: Boolean
  ): Bitmap? {
    if (uriString.isBlank()) {
      return generatePlaceholderBitmap(uriString, sourceTimeMs, targetWidth, targetHeight)
    }

    if (!isVideo) {
      return decodeImageThumbnail(context, uriString, targetWidth, targetHeight)
    }

    // Decode Video Frame
    val retriever = MediaMetadataRetriever()
    try {
      val parsedUri = try { Uri.parse(uriString) } catch (e: Exception) { null }

      if (parsedUri != null && (parsedUri.scheme == "content" || parsedUri.scheme == "android.resource")) {
        retriever.setDataSource(context, parsedUri)
      } else if (parsedUri != null && parsedUri.scheme == "file") {
        retriever.setDataSource(parsedUri.path ?: uriString)
      } else if (parsedUri != null && parsedUri.scheme == "asset") {
        val assetPath = parsedUri.path?.removePrefix("/") ?: uriString.removePrefix("asset:///")
        val afd = context.assets.openFd(assetPath)
        retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
      } else {
        val localFile = File(uriString)
        if (localFile.exists() && localFile.canRead()) {
          retriever.setDataSource(localFile.absolutePath)
        } else {
          retriever.setDataSource(uriString)
        }
      }

      val sourceTimeUs = (sourceTimeMs.coerceAtLeast(0L)) * 1000L

      // Stage 1: Try scaled keyframe extraction (fastest)
      var rawBitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        try {
          retriever.getScaledFrameAtTime(
            sourceTimeUs,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            targetWidth,
            targetHeight
          )
        } catch (e: Throwable) {
          null
        }
      } else null

      // Stage 2: Try unscaled closest sync frame
      if (rawBitmap == null) {
        try {
          rawBitmap = retriever.getFrameAtTime(sourceTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (ignored: Throwable) {}
      }

      // Stage 3: Try closest frame
      if (rawBitmap == null) {
        try {
          rawBitmap = retriever.getFrameAtTime(sourceTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (ignored: Throwable) {}
      }

      // Stage 4: Try first frame at timestamp 0
      if (rawBitmap == null) {
        try {
          rawBitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (ignored: Throwable) {}
      }

      if (rawBitmap != null) {
        val rotationStr = try {
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        } catch (e: Exception) {
          null
        }
        val rotationDegrees = rotationStr?.toIntOrNull() ?: 0

        // MediaMetadataRetriever auto-rotates on API 27+ (Android 8.1+)
        val orientedBitmap = if (rotationDegrees != 0 && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O_MR1) {
          val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
          val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
          if (rotated != rawBitmap) {
            try { rawBitmap.recycle() } catch (ignored: Exception) {}
          }
          rotated
        } else {
          rawBitmap
        }

        // Downscale if needed to target bounds
        if (orientedBitmap.width > targetWidth * 1.5 || orientedBitmap.height > targetHeight * 1.5) {
          val scaled = Bitmap.createScaledBitmap(orientedBitmap, targetWidth, targetHeight, true)
          if (scaled != orientedBitmap) {
            try { orientedBitmap.recycle() } catch (ignored: Exception) {}
          }
          return scaled
        }

        return orientedBitmap
      }
    } catch (e: Throwable) {
      Log.w(TAG, "Video thumbnail extraction attempt for $uriString at ${sourceTimeMs}ms: ${e.message}")
    } finally {
      try {
        retriever.release()
      } catch (ignored: Exception) {}
    }

    // Fallback: Generate procedural placeholder
    return generatePlaceholderBitmap(uriString, sourceTimeMs, targetWidth, targetHeight)
  }

  /**
   * Decodes an image file as a downscaled thumbnail bitmap.
   */
  private fun decodeImageThumbnail(
    context: Context,
    uriString: String,
    targetWidth: Int,
    targetHeight: Int
  ): Bitmap? {
    return try {
      val parsedUri = Uri.parse(uriString)
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

      if (parsedUri.scheme == "content" || parsedUri.scheme == "android.resource") {
        context.contentResolver.openInputStream(parsedUri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, options)
        }
      } else {
        val path = if (parsedUri.scheme == "file") parsedUri.path ?: uriString else uriString
        BitmapFactory.decodeFile(path, options)
      }

      var sampleSize = 1
      while ((options.outWidth / (sampleSize * 2)) >= targetWidth && (options.outHeight / (sampleSize * 2)) >= targetHeight) {
        sampleSize *= 2
      }

      val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
      }

      if (parsedUri.scheme == "content" || parsedUri.scheme == "android.resource") {
        context.contentResolver.openInputStream(parsedUri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
      } else {
        val path = if (parsedUri.scheme == "file") parsedUri.path ?: uriString else uriString
        BitmapFactory.decodeFile(path, decodeOptions)
      }
    } catch (e: Exception) {
      generatePlaceholderBitmap(uriString, 0L, targetWidth, targetHeight)
    }
  }

  /**
   * Loads a cached frame from the persistent disk cache.
   */
  private fun loadFromDiskCache(context: Context, key: String): Bitmap? {
    return try {
      val cacheDir = MediaPersistenceManager.getThumbnailCacheDir(context)
      val hash = MediaPersistenceManager.md5(key)
      val file = File(cacheDir, "$hash.thumb")
      if (file.exists() && file.length() > 0L) {
        val options = BitmapFactory.Options().apply {
          inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
      } else {
        null
      }
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Saves an extracted thumbnail bitmap to the persistent disk cache.
   */
  private fun saveToDiskCache(context: Context, key: String, bitmap: Bitmap) {
    try {
      val cacheDir = MediaPersistenceManager.getThumbnailCacheDir(context)
      val hash = MediaPersistenceManager.md5(key)
      val file = File(cacheDir, "$hash.thumb")
      FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.flush()
      }
    } catch (ignored: Exception) {}
  }

  /**
   * Generates a procedural cinematic thumbnail bitmap with dynamic scene color gradients.
   */
  fun generatePlaceholderBitmap(
    uriString: String,
    sourceTimeMs: Long,
    targetWidth: Int,
    targetHeight: Int
  ): Bitmap {
    val width = targetWidth.coerceIn(60, 240)
    val height = targetHeight.coerceIn(60, 240)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    val canvas = Canvas(bitmap)

    val seed = abs(uriString.hashCode() + (sourceTimeMs / 1000L).toInt() * 37)
    val color1 = Color.rgb(
      (20 + (seed * 43) % 70),
      (30 + (seed * 67) % 90),
      (60 + (seed * 89) % 120)
    )
    val color2 = Color.rgb(
      (40 + ((seed + 13) * 53) % 90),
      (15 + ((seed + 7) * 31) % 60),
      (70 + ((seed + 23) * 73) % 130)
    )

    val shader = android.graphics.LinearGradient(
      0f, 0f, width.toFloat(), height.toFloat(),
      color1, color2,
      android.graphics.Shader.TileMode.CLAMP
    )
    val paint = android.graphics.Paint().apply { this.shader = shader }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    return bitmap
  }

  /**
   * Invalidates memory-cached thumbnails for a specific media URI.
   */
  fun invalidateClip(uriString: String) {
    val snapshot = memoryCache.snapshot()
    for ((k, _) in snapshot) {
      if (k.startsWith(uriString)) {
        memoryCache.remove(k)
      }
    }
  }

  /**
   * Clears in-memory cache to release RAM if needed.
   */
  fun clearMemoryCache() {
    memoryCache.evictAll()
  }
}
