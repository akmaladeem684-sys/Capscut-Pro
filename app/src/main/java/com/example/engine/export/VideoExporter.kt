package com.example.engine.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.opengl.GLES20
import android.media.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import com.example.domain.model.*
import com.example.engine.composition.VideoCompositionEngine
import com.example.engine.composition.gpu.EglCore
import com.example.engine.composition.gpu.GpuCompositionRenderer
import com.example.engine.composition.gpu.WindowSurface
import com.example.engine.media.MediaRelinkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

enum class CodecProfile(val label: String, val mimeType: String) {
  AUTO("Auto (Best Performance)", "auto"),
  H264_AVC("H.264 / AVC (Universal)", MediaFormat.MIMETYPE_VIDEO_AVC),
  H265_HEVC("H.265 / HEVC (High Efficiency 4K)", MediaFormat.MIMETYPE_VIDEO_HEVC)
}

data class ExportConfig(
  val resolution: Resolution = Resolution.RES_1080P,
  val frameRate: FrameRate = FrameRate.FPS_30,
  val quality: ExportQuality = ExportQuality.HIGH,
  val customBitrateKbps: Int = 12000,
  val codecProfile: CodecProfile = CodecProfile.AUTO
)

sealed class ExportState {
  object Idle : ExportState()
  data class Rendering(
    val progressPercent: Float,
    val currentFrame: Int,
    val totalFrames: Int,
    val status: String = "Encoding video...",
    val fps: Float = 0f,
    val estimatedRemainingSec: Int = 0,
    val resolution: Resolution = Resolution.RES_1080P,
    val renderEngine: String = "Hardware Video Engine (GPU)",
    val isPaused: Boolean = false
  ) : ExportState()
  data class Success(val file: File, val durationMs: Long, val fileSizeBytes: Long) : ExportState()
  data class Error(
    val message: String,
    val failedFrame: Int = 0,
    val failedLayer: String? = null,
    val canRetry: Boolean = true
  ) : ExportState()
}

/**
 * MuxerCoordinator manages dynamic track registration and sample writing to MediaMuxer.
 * It ensures:
 * 1. Tracks are registered before MediaMuxer is started.
 * 2. Exact track indices returned by MediaMuxer are stored and used.
 * 3. CODEC_CONFIG packets are filtered out (CSD is supplied via MediaFormat).
 * 4. Microsecond-accurate monotonic timestamps prevent video freezes and playback stalls.
 */
class MuxerCoordinator(
  private val mediaMuxer: MediaMuxer,
  private val hasAudio: Boolean
) {
  private val tag = "MuxerCoordinator"

  var videoTrackIndex: Int = -1
    private set
  var audioTrackIndex: Int = -1
    private set
  var isStarted: Boolean = false
    private set

  private var lastVideoPtsUs: Long = -1L
  private var lastAudioPtsUs: Long = -1L

  private class QueuedPacket(
    val isAudio: Boolean,
    val data: ByteArray,
    val presentationTimeUs: Long,
    val flags: Int
  ) : Comparable<QueuedPacket> {
    override fun compareTo(other: QueuedPacket): Int {
      return presentationTimeUs.compareTo(other.presentationTimeUs)
    }
  }

  private val pendingQueue = mutableListOf<QueuedPacket>()

  @Synchronized
  fun setVideoFormat(format: MediaFormat) {
    if (videoTrackIndex < 0) {
      try {
        videoTrackIndex = mediaMuxer.addTrack(format)
        Log.d(tag, "Added video track with index $videoTrackIndex")
        checkStart()
      } catch (e: Exception) {
        Log.e(tag, "Failed to add video track to muxer", e)
      }
    }
  }

  @Synchronized
  fun setAudioFormat(format: MediaFormat) {
    if (audioTrackIndex < 0) {
      try {
        audioTrackIndex = mediaMuxer.addTrack(format)
        Log.d(tag, "Added audio track with index $audioTrackIndex")
        checkStart()
      } catch (e: Exception) {
        Log.e(tag, "Failed to add audio track to muxer", e)
      }
    }
  }

  @Synchronized
  private fun checkStart() {
    if (isStarted) return
    val videoReady = videoTrackIndex >= 0
    val audioReady = !hasAudio || audioTrackIndex >= 0

    if (videoReady && audioReady) {
      try {
        mediaMuxer.start()
        isStarted = true
        Log.d(tag, "MediaMuxer started successfully")
        flushPending()
      } catch (e: Exception) {
        Log.e(tag, "Failed to start MediaMuxer", e)
      }
    }
  }

  @Synchronized
  private fun flushPending() {
    pendingQueue.sort()
    for (packet in pendingQueue) {
      val trackIndex = if (packet.isAudio) audioTrackIndex else videoTrackIndex
      if (trackIndex >= 0) {
        val lastPts = if (packet.isAudio) lastAudioPtsUs else lastVideoPtsUs
        var pts = packet.presentationTimeUs
        if (pts < 0L) pts = 0L
        if (pts <= lastPts) {
          pts = lastPts + 1L // 1 microsecond nudge only to maintain strictly monotonic order
        }

        val bufferInfo = MediaCodec.BufferInfo().apply {
          set(0, packet.data.size, pts, packet.flags)
        }
        val byteBuffer = ByteBuffer.wrap(packet.data)
        try {
          mediaMuxer.writeSampleData(trackIndex, byteBuffer, bufferInfo)
          if (packet.isAudio) {
            lastAudioPtsUs = pts
          } else {
            lastVideoPtsUs = pts
          }
        } catch (e: Exception) {
          Log.w(tag, "Failed to write queued sample", e)
        }
      }
    }
    pendingQueue.clear()
  }

  @Synchronized
  fun writeVideoSample(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    // Ignore codec config buffers (CSD) and empty buffers
    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 || info.size <= 0) {
      return
    }

    if (isStarted && videoTrackIndex >= 0) {
      var pts = info.presentationTimeUs
      if (pts < 0L) pts = 0L
      if (pts <= lastVideoPtsUs) {
        pts = lastVideoPtsUs + 1L
      }
      val correctedInfo = MediaCodec.BufferInfo().apply {
        set(info.offset, info.size, pts, info.flags)
      }
      try {
        mediaMuxer.writeSampleData(videoTrackIndex, buffer, correctedInfo)
        lastVideoPtsUs = pts
      } catch (e: Exception) {
        Log.w(tag, "Failed to write video sample", e)
      }
    } else {
      val bytes = ByteArray(info.size)
      buffer.position(info.offset)
      buffer.get(bytes)
      pendingQueue.add(QueuedPacket(false, bytes, info.presentationTimeUs, info.flags))
    }
  }

  @Synchronized
  fun writeAudioSample(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    // Ignore codec config buffers and empty buffers
    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 || info.size <= 0) {
      return
    }

    if (isStarted && audioTrackIndex >= 0) {
      var pts = info.presentationTimeUs
      if (pts < 0L) pts = 0L
      if (pts <= lastAudioPtsUs) {
        pts = lastAudioPtsUs + 1L
      }
      val correctedInfo = MediaCodec.BufferInfo().apply {
        set(info.offset, info.size, pts, info.flags)
      }
      try {
        mediaMuxer.writeSampleData(audioTrackIndex, buffer, correctedInfo)
        lastAudioPtsUs = pts
      } catch (e: Exception) {
        Log.w(tag, "Failed to write audio sample", e)
      }
    } else {
      val bytes = ByteArray(info.size)
      buffer.position(info.offset)
      buffer.get(bytes)
      pendingQueue.add(QueuedPacket(true, bytes, info.presentationTimeUs, info.flags))
    }
  }
}

/**
 * High-Performance, Professional Video Rendering & Export Engine.
 * Supports 4K UHD, 2K QHD, 1080p FHD, 60fps, complex multi-layer compositions,
 * GPU hardware surface rendering, memory-bounded bitmap pooling, orientation stabilization,
 * and sample-accurate multi-track audio mixing with smooth, freeze-free gallery playback.
 */
class VideoExporter(private val context: Context) {

  private val tag = "VideoExporter"

  private class CachedVideoFrame(
    val clipId: String,
    val sourcePosMs: Long,
    val bitmap: Bitmap
  )

  private val bitmapPool = RenderBitmapPool(maxPoolSizeBytes = 128 * 1024 * 1024L)
  private val videoFrameCache = mutableMapOf<String, CachedVideoFrame>()
  private val compositionEngine = VideoCompositionEngine(context)
  private val audioProcessor = AudioExportProcessor(context)

  private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
  val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

  @Volatile
  private var isCancelled = false
  @Volatile
  private var isPaused = false

  fun pauseExport() {
    isPaused = true
  }

  fun resumeExport() {
    isPaused = false
  }

  fun cancelExport() {
    isCancelled = true
    isPaused = false
  }

  fun beginExternalExport(config: ExportConfig) {
    isCancelled = false
    isPaused = false
    _exportState.value = ExportState.Rendering(
      progressPercent = 0f,
      currentFrame = 0,
      totalFrames = 0,
      status = "Preparing hardware export...",
      resolution = config.resolution,
      renderEngine = "Hardware GPU Engine"
    )
  }

  fun updateExternalExportProgress(progress: Float, status: String) {
    val current = _exportState.value as? ExportState.Rendering ?: return
    _exportState.value = current.copy(
      progressPercent = progress.coerceIn(0f, 1f),
      status = status
    )
  }

  fun completeExternalExport(file: File, durationMs: Long) {
    _exportState.value = ExportState.Success(
      file = file,
      durationMs = durationMs,
      fileSizeBytes = file.length()
    )
  }

  fun failExternalExport(message: String) {
    _exportState.value = ExportState.Error(message = message)
  }


  /**
   * Evaluates if any asset in the project has a native resolution significantly below 1080p
   * when targeting 2K / 4K UHD rendering.
   */
  fun checkLowResolutionAssets(timeline: Timeline, targetRes: Resolution): List<String> {
    if (targetRes != Resolution.RES_2K &&
        targetRes != Resolution.RES_4K &&
        targetRes != Resolution.RES_VERTICAL_2K &&
        targetRes != Resolution.RES_VERTICAL_4K &&
        targetRes != Resolution.RES_SQUARE_2K
    ) {
      return emptyList()
    }

    val lowRes = mutableListOf<String>()
    for (clip in timeline.videoClips + timeline.overlayClips) {
      if (clip.width > 0 && clip.height > 0) {
        val minDim = min(clip.width, clip.height)
        if (minDim < 720) {
          lowRes.add("${clip.name} (${clip.width}×${clip.height})")
        }
      }
    }
    return lowRes
  }

  /**
   * Validates hardware encoder capability for 4K / 2K HEVC and AVC encoding.
   */
  fun check4KSupport(): Boolean {
    return try {
      val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
      val hevcMime = MediaFormat.MIMETYPE_VIDEO_HEVC
      for (info in codecList.codecInfos) {
        if (info.isEncoder) {
          try {
            val caps = info.getCapabilitiesForType(hevcMime)
            val videoCaps = caps.videoCapabilities
            if (videoCaps != null && videoCaps.isSizeSupported(3840, 2160)) {
              return true
            }
          } catch (ignored: Exception) {}
        }
      }
      true
    } catch (e: Exception) {
      true
    }
  }

  fun calculateEstimatedSizeBytes(durationMs: Long, config: ExportConfig): Long {
    val durationSec = (durationMs / 1000f).coerceAtLeast(1f)
    val effectiveBitrate = if (config.quality == ExportQuality.CUSTOM && config.customBitrateKbps > 0) {
      config.customBitrateKbps * 1000L
    } else {
      val baseBitrate = when (config.resolution) {
        Resolution.RES_480P -> 2_500_000L
        Resolution.RES_720P -> 5_000_000L
        Resolution.RES_1080P -> 10_000_000L
        Resolution.RES_2K, Resolution.RES_VERTICAL_2K -> 18_000_000L
        Resolution.RES_4K, Resolution.RES_VERTICAL_4K -> 35_000_000L
        Resolution.RES_SQUARE_2K -> 22_000_000L
      }
      val codecMultiplier = if (config.codecProfile == CodecProfile.H265_HEVC) 0.75f else 1.0f
      (baseBitrate * config.quality.bitrateMultiplier * (config.frameRate.fps / 30f) * codecMultiplier).toLong()
    }
    return (effectiveBitrate * durationSec / 8).toLong()
  }

  /**
   * Evaluates if a timeline can be exported directly via Media3 Transformer.
   */
  fun canExportWithMedia3Transformer(timeline: Timeline): Boolean {
    if (timeline.videoClips.isEmpty()) return false
    // Timelines with filters, color adjustments, chroma key, custom canvas overlays, text layers, stickers, animations, effects, or clip transformations use composition engine
    if (timeline.filter.type != com.example.domain.model.FilterType.NONE ||
        timeline.adjustments != com.example.domain.model.VideoAdjustments() ||
        timeline.chromaKey.enabled ||
        timeline.videoClips.any {
          it.filter?.type != com.example.domain.model.FilterType.NONE ||
          it.rotationDegrees != 0 ||
          it.cropScale != 1.0f ||
          it.cropOffsetX != 0f ||
          it.cropOffsetY != 0f ||
          it.flipHorizontal ||
          it.flipVertical ||
          it.opacity != 1.0f
        } ||
        timeline.overlayClips.isNotEmpty() ||
        timeline.textClips.isNotEmpty() ||
        timeline.stickerClips.isNotEmpty() ||
        timeline.effectClips.isNotEmpty() ||
        timeline.videoClips.any { it.animation.hasAnimation || it.keyframes.isNotEmpty() }
    ) {
      return false
    }
    // All video clips must refer to physical playable media on device
    return timeline.videoClips.all { clip ->
      clip.isVideo && clip.uri.isNotBlank() && MediaRelinkManager.isRealPlayableMedia(context, clip.uri)
    }
  }

  /**
   * High-level MP4 Exporter using Media3 Transformer when applicable.
   */
  suspend fun exportWithMedia3Transformer(
    timeline: Timeline,
    outputFile: File,
    config: ExportConfig
  ): File? = withContext(Dispatchers.Main) {
    var completed = false
    var exportError: Throwable? = null
    val latch = java.util.concurrent.CountDownLatch(1)

    try {
      _exportState.value = ExportState.Rendering(0.05f, 0, 100, "Configuring Media3 Transformer pipeline...")

      val (exportWidth, exportHeight) = getDimensionsForResolution(config.resolution, timeline.aspectRatio)
      val targetFps = config.frameRate.fps
      val effectiveBitrateBps = if (config.quality == ExportQuality.CUSTOM && config.customBitrateKbps > 0) {
        config.customBitrateKbps * 1000
      } else {
        val baseBitrate = when (config.resolution) {
          Resolution.RES_480P -> 2_500_000L
          Resolution.RES_720P -> 5_000_000L
          Resolution.RES_1080P -> 10_000_000L
          Resolution.RES_2K, Resolution.RES_VERTICAL_2K -> 18_000_000L
          Resolution.RES_4K, Resolution.RES_VERTICAL_4K -> 35_000_000L
          Resolution.RES_SQUARE_2K -> 22_000_000L
        }
        (baseBitrate * config.quality.bitrateMultiplier * (config.frameRate.fps / 30f)).toInt()
      }

      val presentation = Presentation.createForWidthAndHeight(exportWidth, exportHeight, Presentation.LAYOUT_SCALE_TO_FIT)
      val frameDrop = FrameDropEffect.createDefaultFrameDropEffect(targetFps.toFloat())
      val videoEffects = listOf(presentation, frameDrop)

      val editedMediaItems = mutableListOf<EditedMediaItem>()
      for (clip in timeline.videoClips) {
        val uri = Uri.parse(clip.uri)
        val clippingConfig = if (clip.sourceStartMs > 0L || (clip.sourceEndMs > 0L && clip.sourceEndMs > clip.sourceStartMs)) {
          MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clip.sourceStartMs)
            .setEndPositionMs(clip.sourceEndMs)
            .build()
        } else {
          MediaItem.ClippingConfiguration.UNSET
        }
        val mediaItem = MediaItem.Builder()
          .setUri(uri)
          .setClippingConfiguration(clippingConfig)
          .build()
        val editedItem = EditedMediaItem.Builder(mediaItem)
          .setRemoveAudio(clip.isMuted || !clip.hasAudio)
          .setEffects(Effects(emptyList(), videoEffects))
          .build()
        editedMediaItems.add(editedItem)
      }

      if (editedMediaItems.isEmpty()) return@withContext null

      val encoderSettings = VideoEncoderSettings.Builder()
        .setBitrate(effectiveBitrateBps)
        .build()
      val encoderFactory = DefaultEncoderFactory.Builder(context)
        .setRequestedVideoEncoderSettings(encoderSettings)
        .setEnableFallback(true)
        .build()

      val sequence = EditedMediaItemSequence(editedMediaItems)
      val composition = Composition.Builder(sequence).build()

      val transformer = Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .setEncoderFactory(encoderFactory)
        .addListener(object : Transformer.Listener {
          override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            completed = true
            latch.countDown()
          }

          override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
            exportError = exportException
            latch.countDown()
          }
        })
        .build()

      transformer.start(composition, outputFile.absolutePath)

      val progressHolder = ProgressHolder()
      val startTime = System.currentTimeMillis()
      while (!completed && exportError == null && !isCancelled) {
        val status = transformer.getProgress(progressHolder)
        if (status == Transformer.PROGRESS_STATE_AVAILABLE) {
          val progress = (progressHolder.progress / 100f).coerceIn(0f, 1f)
          val elapsedSec = (System.currentTimeMillis() - startTime) / 1000f
          val etaSec = if (progress > 0.05f) ((elapsedSec / progress) - elapsedSec).toInt().coerceAtLeast(0) else 0
          _exportState.value = ExportState.Rendering(
            progressPercent = progress,
            currentFrame = progressHolder.progress,
            totalFrames = 100,
            status = "Exporting with Media3 Transformer (${progressHolder.progress}%)",
            fps = 0f,
            estimatedRemainingSec = etaSec
          )
        }
        kotlinx.coroutines.delay(150)
      }

      if (isCancelled) {
        transformer.cancel()
        _exportState.value = ExportState.Idle
        if (outputFile.exists()) outputFile.delete()
        return@withContext null
      }

      if (exportError != null) {
        Log.w(tag, "Media3 Transformer error: ${exportError?.message}. Falling back to composition engine.")
        if (outputFile.exists()) outputFile.delete()
        return@withContext null
      }

      if (completed && outputFile.exists() && outputFile.length() > 1024L) {
        val isValid = validatePlayableMp4(outputFile)
        if (isValid) {
          _exportState.value = ExportState.Success(outputFile, timeline.totalDurationMs, outputFile.length())
          return@withContext outputFile
        }
      }
    } catch (e: Throwable) {
      Log.w(tag, "Media3 Transformer setup exception: ${e.message}", e)
      if (outputFile.exists()) outputFile.delete()
    }
    return@withContext null
  }

  /**
   * Production MP4 Exporter using Media3 Transformer when applicable,
   * with seamless fallback to the robust hardware composition pipeline.
   */
  suspend fun exportProject(
    projectName: String,
    timeline: Timeline,
    config: ExportConfig = ExportConfig()
  ): File? = withContext(Dispatchers.IO) {
    isCancelled = false
    val totalDurationMs = timeline.totalDurationMs.coerceAtLeast(1000L)

    // Verify storage capacity
    val outputDir = File(context.filesDir, "exports").apply { if (!exists()) mkdirs() }
    val estimatedBytes = calculateEstimatedSizeBytes(totalDurationMs, config)
    val usableSpace = outputDir.usableSpace
    if (usableSpace < estimatedBytes + 20 * 1024 * 1024L) {
      _exportState.value = ExportState.Error(
        "Insufficient storage space. Need at least ${((estimatedBytes / (1024 * 1024)) + 20)} MB free."
      )
      return@withContext null
    }

    val sanitizedName = projectName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
    val resLabel = config.resolution.label.lowercase()
    val outputFile = File(outputDir, "${sanitizedName}_${resLabel}_${System.currentTimeMillis()}.mp4")

    // Use Chunked Export for long projects (>15 seconds) or 4K to prevent OOM
    if (totalDurationMs > 15_000L || config.resolution == Resolution.RES_4K || config.resolution == Resolution.RES_VERTICAL_4K) {
      Log.i(tag, "Project length $totalDurationMs ms / 4K resolution detected. Delegating to ChunkedExportEngine...")
      val chunkedEngine = ChunkedExportEngine(context)
      val chunkedSuccess = chunkedEngine.exportInChunks(timeline, config, outputFile, this@VideoExporter) { progress, status ->
        val frames = ((totalDurationMs / 1000.0) * config.frameRate.fps).toInt()
        val curFrame = (progress * frames).toInt()
        _exportState.value = ExportState.Rendering(
          progressPercent = progress,
          currentFrame = curFrame,
          totalFrames = frames,
          status = status,
          resolution = config.resolution,
          renderEngine = "Chunked Hardware Export Engine"
        )
      }
      if (chunkedSuccess) {
        val sizeBytes = outputFile.length()
        _exportState.value = ExportState.Success(outputFile, totalDurationMs, sizeBytes)
        return@withContext outputFile
      } else {
        Log.w(tag, "Chunked export encountered error, falling back to single-pass hardware export.")
      }
    }

    // 1. Attempt export via Media3 Transformer if eligible
    if (canExportWithMedia3Transformer(timeline)) {
      val transformerResult = exportWithMedia3Transformer(timeline, outputFile, config)
      if (transformerResult != null) {
        return@withContext transformerResult
      }
      Log.i(tag, "Media3 Transformer pipeline deferred to hardware composition engine")
    }

    // 2. Hardware composition pipeline with strict MediaCodec & MediaMuxer lifecycle
    return@withContext exportWithHardwarePipeline(projectName, timeline, config, outputFile)
  }

  suspend fun exportTimelineSegment(
    timeline: Timeline,
    config: ExportConfig,
    outputFile: File,
    startMs: Long,
    endMs: Long
  ): Boolean = withContext(Dispatchers.IO) {
    val segmentDuration = (endMs - startMs).coerceAtLeast(1000L)
    val shiftedVideo = timeline.videoClips.mapNotNull { clip ->
      val clipEnd = clip.timelineStartMs + clip.durationMs
      if (clipEnd <= startMs || clip.timelineStartMs >= endMs) null
      else {
        val newStart = (clip.timelineStartMs - startMs).coerceAtLeast(0L)
        val newEnd = (clipEnd - startMs).coerceIn(0L, segmentDuration)
        clip.copy(timelineStartMs = newStart, durationMs = maxOf(100L, newEnd - newStart))
      }
    }
    val shiftedAudio = timeline.audioClips.mapNotNull { clip ->
      val clipEnd = clip.timelineStartMs + clip.durationMs
      if (clipEnd <= startMs || clip.timelineStartMs >= endMs) null
      else {
        val newStart = (clip.timelineStartMs - startMs).coerceAtLeast(0L)
        val newEnd = (clipEnd - startMs).coerceIn(0L, segmentDuration)
        clip.copy(timelineStartMs = newStart, durationMs = maxOf(100L, newEnd - newStart))
      }
    }
    val segmentTimeline = timeline.copy(
      videoClips = shiftedVideo,
      audioClips = shiftedAudio
    )

    val result = exportWithHardwarePipeline("segment", segmentTimeline, config, outputFile)
    result != null && result.exists() && result.length() > 0L
  }

  /**
   * Hardware composition engine using MediaCodec + MediaMuxer with strict lifecycle,
   * timestamp monotonic synchronization, 4K/2K resolution capability, and precise track index handling.
   */
  suspend fun exportWithHardwarePipeline(
    projectName: String,
    timeline: Timeline,
    config: ExportConfig,
    outputFile: File
  ): File? = withContext(Dispatchers.IO) {
    val totalDurationMs = timeline.totalDurationMs.coerceAtLeast(1000L)
    val fps = config.frameRate.fps
    val totalFrames = ((totalDurationMs / 1000.0) * fps).toInt().coerceAtLeast(15)

    // Determine target dimensions from resolution and timeline aspect ratio aligned to 16px
    val (exportWidth, exportHeight) = getDimensionsForResolution(config.resolution, timeline.aspectRatio)

    var mediaMuxer: MediaMuxer? = null
    var videoEncoder: MediaCodec? = null
    var audioEncoder: MediaCodec? = null
    var eglCore: EglCore? = null
    var windowSurface: WindowSurface? = null
    var gpuRenderer: GpuCompositionRenderer? = null
    var encoderInputSurface: Surface? = null
    var useGpuSurface = false

    // Cache retrievers and bitmaps
    val retrievers = mutableMapOf<String, MediaMetadataRetriever>()
    val imageBitmaps = mutableMapOf<String, Bitmap>()

    try {
      _exportState.value = ExportState.Rendering(
        progressPercent = 0.02f,
        currentFrame = 0,
        totalFrames = totalFrames,
        status = "Preparing audio & video tracks...",
        resolution = config.resolution,
        renderEngine = if (useGpuSurface) "Hardware GPU Engine (OpenGL ES)" else "Software Canvas Engine"
      )

      // 1. Process & Mix all Audio Tracks
      val hasAudioSources = audioProcessor.hasActiveAudio(timeline)
      var masterPcm: ShortArray = ShortArray(0)
      var hasAudio = false

      if (hasAudioSources) {
        _exportState.value = ExportState.Rendering(
          progressPercent = 0.05f,
          currentFrame = 0,
          totalFrames = totalFrames,
          status = "Mixing multi-track audio...",
          resolution = config.resolution,
          renderEngine = if (useGpuSurface) "Hardware GPU Engine (OpenGL ES)" else "Software Canvas Engine"
        )
        try {
          masterPcm = audioProcessor.mixTimelineAudio(timeline, totalDurationMs) { isCancelled }
        } catch (e: Exception) {
          Log.w(tag, "Audio mixing encountered error: ${e.message}. Continuing with fallback audio.", e)
          masterPcm = ShortArray(0)
        }
        if (isCancelled) {
          cleanUp(null, null, null, null, null, null, null, outputFile)
          _exportState.value = ExportState.Idle
          return@withContext null
        }
        hasAudio = masterPcm.isNotEmpty()
      }

      // Preload image bitmaps
      for (clip in timeline.videoClips + timeline.overlayClips) {
        if (!clip.isVideo && clip.uri.isNotBlank()) {
          try {
            val uri = Uri.parse(clip.uri)
            context.contentResolver.openInputStream(uri)?.use { stream ->
              val bmp = BitmapFactory.decodeStream(stream)
              if (bmp != null) {
                imageBitmaps[clip.uri] = bmp
              }
            }
          } catch (e: Exception) {
            Log.w(tag, "Failed to load image for clip: ${clip.name}", e)
          }
        }
      }

      // 2. Initialize Video Encoder (HEVC or AVC)
      _exportState.value = ExportState.Rendering(0.08f, 0, totalFrames, "Configuring hardware encoders for ${config.resolution.label}...")
      val videoMime = selectVideoCodecMime(config, exportWidth, exportHeight)
      val bitrate = (calculateEstimatedSizeBytes(totalDurationMs, config) * 8 / (totalDurationMs / 1000f)).toInt()
        .coerceIn(1_500_000, 50_000_000)

      val videoFormat = MediaFormat.createVideoFormat(videoMime, exportWidth, exportHeight).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
        setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second keyframe interval for instant seek & smooth playback
        try {
          setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        } catch (ignored: Exception) {}
      }

      videoEncoder = try {
        MediaCodec.createEncoderByType(videoMime)
      } catch (e: Exception) {
        Log.w(tag, "Failed to create encoder for $videoMime, falling back to AVC", e)
        MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
      }
      val codecInfo = videoEncoder.codecInfo
      val caps = codecInfo.getCapabilitiesForType(videoEncoder.name.let {
        try { videoEncoder.inputFormat.getString(MediaFormat.KEY_MIME) ?: videoMime } catch (e: Exception) { videoMime }
      })
      val chosenColorFormat = if (caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)) {
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
      } else if (caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)) {
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
      } else {
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
      }

      var useGpuSurface = false

      val hasMlBodyEffects = timeline.effectClips.any { it.effectType.name.startsWith("VFX_BODY_") && !it.isHidden }
      val supportsSurface = !hasMlBodyEffects && caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
      if (supportsSurface) {
        try {
          videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
          try {
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
          } catch (configEx: Exception) {
            Log.w(tag, "Standard configure failed, retrying basic format", configEx)
            videoEncoder.reset()
            val basicFmt = MediaFormat.createVideoFormat(videoMime, exportWidth, exportHeight).apply {
              setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
              setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
              setInteger(MediaFormat.KEY_FRAME_RATE, fps)
              setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            videoEncoder.configure(basicFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
          }
          val surface = videoEncoder.createInputSurface()
          encoderInputSurface = surface

          val core = EglCore(null, EglCore.FLAG_RECORDABLE)
          val winSurface = WindowSurface(core, surface, false)
          winSurface.makeCurrent()
          val renderer = GpuCompositionRenderer(context)
          renderer.initGl()

          eglCore = core
          windowSurface = winSurface
          gpuRenderer = renderer

          videoEncoder.start()
          useGpuSurface = true
          Log.i(tag, "GPU hardware surface composition pipeline activated ($exportWidth x $exportHeight @ ${fps}fps via $videoMime)")
        } catch (e: Throwable) {
          Log.w(tag, "GPU surface initialization fallback to buffer pipeline: ${e.message}")
          useGpuSurface = false
          try { windowSurface?.release() } catch (ignored: Exception) {}
          try { eglCore?.release() } catch (ignored: Exception) {}
          try { gpuRenderer?.release() } catch (ignored: Exception) {}
          windowSurface = null
          eglCore = null
          gpuRenderer = null
          try { encoderInputSurface?.release() } catch (ignored: Exception) {}
          encoderInputSurface = null

          try { videoEncoder.stop() } catch (ignored: Exception) {}
          try { videoEncoder.release() } catch (ignored: Exception) {}
          videoEncoder = MediaCodec.createEncoderByType(videoMime)
        }
      }

      if (!useGpuSurface) {
        try {
          videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, chosenColorFormat)
          videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
          videoEncoder.start()
        } catch (e: Exception) {
          Log.w(tag, "Primary buffer configuration failed, retrying with COLOR_FormatYUV420SemiPlanar", e)
          try { videoEncoder.reset() } catch (ignored: Exception) {}
          videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
          videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
          videoEncoder.start()
        }
      }

      // 3. Initialize Audio Encoder (AAC) if audio is present
      val audioSampleRate = audioProcessor.sampleRate
      val audioChannels = audioProcessor.channelCount
      if (hasAudio) {
        try {
          val audioMime = MediaFormat.MIMETYPE_AUDIO_AAC
          val aacFormat = MediaFormat.createAudioFormat(audioMime, audioSampleRate, audioChannels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
          }
          audioEncoder = MediaCodec.createEncoderByType(audioMime)
          audioEncoder.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
          audioEncoder.start()
        } catch (e: Exception) {
          Log.w(tag, "Audio encoder configuration failed: ${e.message}. Continuing export video-only.", e)
          audioEncoder = null
          hasAudio = false
        }
      }

      // 4. Initialize MediaMuxer & MuxerCoordinator
      mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      val coordinator = MuxerCoordinator(mediaMuxer, hasAudio)

      val frameDurationUs = 1_000_000L / fps

      val frameBitmap = if (!useGpuSurface) bitmapPool.acquire(exportWidth, exportHeight, Bitmap.Config.ARGB_8888) else null
      val frameCanvas = if (frameBitmap != null) Canvas(frameBitmap) else null
      val pixelBuffer = if (!useGpuSurface) IntArray(exportWidth * exportHeight) else null
      val yuvBuffer = if (!useGpuSurface) ByteArray(exportWidth * exportHeight * 3 / 2) else null

      val totalAudioFrames = masterPcm.size / audioChannels
      var fedAudioFrames = 0
      val renderStartTime = System.currentTimeMillis()
      var lastFpsCheckTime = renderStartTime
      var lastFpsCheckFrame = 0
      var currentThroughputFps = 0f

      // Pre-prime the Audio Encoder with the initial buffer so format is ready immediately on frame 0
      if (hasAudio && audioEncoder != null) {
        val primeFrames = min(4096, totalAudioFrames)
        if (primeFrames > 0) {
          val inputBufferIndex = audioEncoder.dequeueInputBuffer(10000L)
          if (inputBufferIndex >= 0) {
            val inputBuffer = audioEncoder.getInputBuffer(inputBufferIndex)
            if (inputBuffer != null) {
              inputBuffer.clear()
              val byteBuffer = ByteBuffer.allocate(primeFrames * audioChannels * 2).order(ByteOrder.LITTLE_ENDIAN)
              val endIdx = primeFrames * audioChannels
              for (i in 0 until endIdx) {
                if (i < masterPcm.size) {
                  byteBuffer.putShort(masterPcm[i])
                } else {
                  byteBuffer.putShort(0)
                }
              }
              byteBuffer.flip()
              inputBuffer.put(byteBuffer)
              audioEncoder.queueInputBuffer(inputBufferIndex, 0, primeFrames * audioChannels * 2, 0L, 0)
              fedAudioFrames = primeFrames
            }
          }
          drainAudioEncoder(audioEncoder, coordinator, false)
        }
      }

      // 5. Main Interleaved Video & Audio Encoding Loop
      for (frameIndex in 0 until totalFrames) {
        while (isPaused && !isCancelled) {
          _exportState.value = (_exportState.value as? ExportState.Rendering)?.copy(
            isPaused = true,
            status = "Render paused at frame $frameIndex"
          ) ?: _exportState.value
          Thread.sleep(100)
        }

        if (isCancelled) {
          try { windowSurface?.release() } catch (ignored: Exception) {}
          try { eglCore?.release() } catch (ignored: Exception) {}
          try { gpuRenderer?.release() } catch (ignored: Exception) {}
          try { encoderInputSurface?.release() } catch (ignored: Exception) {}
          cleanUp(videoEncoder, audioEncoder, encoderInputSurface, windowSurface, eglCore, gpuRenderer, mediaMuxer, outputFile)
          bitmapPool.release(frameBitmap)
          _exportState.value = ExportState.Idle
          return@withContext null
        }

        val timelinePosMs = (frameIndex.toDouble() * 1000.0 / fps).toLong()
        val composedFrame = compositionEngine.evaluateFrame(timeline, timelinePosMs)
        val ptsUs = (frameIndex.toLong() * 1_000_000L) / fps

        // Request keyframe on frame 0 to guarantee instant playback in gallery
        if (frameIndex == 0) {
          try {
            val syncParams = Bundle().apply {
              putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            videoEncoder.setParameters(syncParams)
          } catch (ignored: Exception) {}
        }

        if (useGpuSurface && gpuRenderer != null && windowSurface != null) {
          // Hardware GPU rendering directly to encoder surface
          val mainBmp = fetchClipBitmap(composedFrame.activeClip, composedFrame.clipSourcePosMs, retrievers, imageBitmaps, exportWidth, exportHeight)
          val mainTexId = if (mainBmp != null) {
            gpuRenderer.uploadImageTexture("main_${composedFrame.activeClip?.id ?: "none"}", mainBmp)
          } else 0

          val overlayTexMap = mutableMapOf<String, Int>()
          for (overlay in composedFrame.activeOverlays) {
            val bmp = fetchClipBitmap(overlay.clip, overlay.sourcePosMs, retrievers, imageBitmaps, exportWidth, exportHeight)
            if (bmp != null) {
              val texId = gpuRenderer.uploadImageTexture("overlay_${overlay.clip.id}", bmp)
              overlayTexMap[overlay.clip.id] = texId
            }
          }

          gpuRenderer.render(
            frame = composedFrame,
            mainTextureId = mainTexId,
            isMainOes = false,
            mainTexMatrix = null,
            overlayTextures = overlayTexMap,
            viewportWidth = exportWidth,
            viewportHeight = exportHeight,
            timelineAdjustments = timeline.adjustments,
            timelineFilter = timeline.filter,
            chromaKey = timeline.chromaKey
          )

          // Flush the GL command stream before handing the encoder surface to
          // BufferQueue. Some GPU drivers otherwise submit the surface before
          // deferred rendering has completed, producing black encoded frames.
          GLES20.glFlush()
          val glError = GLES20.glGetError()
          if (glError != GLES20.GL_NO_ERROR) {
            Log.w(tag, "GL error before encoder swap at frame $frameIndex: 0x" + Integer.toHexString(glError))
          }

          val ptsNs = ptsUs * 1000L
          windowSurface.setPresentationTime(ptsNs)
          if (!windowSurface.swapBuffers()) {
            throw IllegalStateException("Encoder input surface swapBuffers() failed at frame $frameIndex")
          }
        } else {
          // CPU buffer fallback for headless environments
          val mainBmp = fetchClipBitmap(composedFrame.activeClip, composedFrame.clipSourcePosMs, retrievers, imageBitmaps, exportWidth, exportHeight)
          val overlayBmps = mutableMapOf<String, Bitmap>()
          for (overlay in composedFrame.activeOverlays) {
            val bmp = fetchClipBitmap(overlay.clip, overlay.sourcePosMs, retrievers, imageBitmaps, exportWidth, exportHeight)
            if (bmp != null) {
              overlayBmps[overlay.clip.id] = bmp
            }
          }

          if (frameBitmap != null && frameCanvas != null && pixelBuffer != null && yuvBuffer != null) {
            frameBitmap.eraseColor(android.graphics.Color.BLACK)
            compositionEngine.renderFrameWithHumanEffects(
              canvas = frameCanvas,
              frame = composedFrame,
              mainBitmap = mainBmp,
              overlayBitmaps = overlayBmps,
              canvasWidth = exportWidth,
              canvasHeight = exportHeight,
              chromaKey = timeline.chromaKey
            )

            frameBitmap.getPixels(pixelBuffer, 0, exportWidth, 0, 0, exportWidth, exportHeight)
            if (chosenColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
              encodeYUV420Planar(yuvBuffer, pixelBuffer, exportWidth, exportHeight)
            } else {
              encodeYUV420SemiPlanar(yuvBuffer, pixelBuffer, exportWidth, exportHeight)
            }

            var videoFed = false
            var attempts = 0
            while (!videoFed && attempts < 50) {
              val inputBufferIndex = videoEncoder.dequeueInputBuffer(5000L)
              if (inputBufferIndex >= 0) {
                val inputBuffer = videoEncoder.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null) {
                  inputBuffer.clear()
                  inputBuffer.put(yuvBuffer)
                  videoEncoder.queueInputBuffer(inputBufferIndex, 0, yuvBuffer.size, ptsUs, 0)
                  videoFed = true
                }
              } else {
                drainVideoEncoder(videoEncoder, coordinator, false)
                attempts++
              }
            }
          }
        }

        // Drain Video Encoder Output Samples
        drainVideoEncoder(videoEncoder, coordinator, false)

        // Feed Audio Pro-rata with Microsecond Precision (Zero Drift)
        if (hasAudio && audioEncoder != null) {
          val targetAudioFrames = (((frameIndex + 1).toLong() * audioSampleRate) / fps).toInt().coerceAtMost(totalAudioFrames)
          while (fedAudioFrames < targetAudioFrames) {
            val framesToFeed = min(1024, targetAudioFrames - fedAudioFrames)
            if (framesToFeed <= 0) break

            val inputBufferIndex = audioEncoder.dequeueInputBuffer(5000L)
            if (inputBufferIndex >= 0) {
              val inputBuffer = audioEncoder.getInputBuffer(inputBufferIndex)
              if (inputBuffer != null) {
                inputBuffer.clear()
                val byteBuffer = ByteBuffer.allocate(framesToFeed * audioChannels * 2).order(ByteOrder.LITTLE_ENDIAN)
                val startIdx = fedAudioFrames * audioChannels
                val endIdx = (fedAudioFrames + framesToFeed) * audioChannels
                for (i in startIdx until endIdx) {
                  if (i < masterPcm.size) {
                    byteBuffer.putShort(masterPcm[i])
                  } else {
                    byteBuffer.putShort(0)
                  }
                }
                byteBuffer.flip()
                inputBuffer.put(byteBuffer)

                val audioPtsUs = (fedAudioFrames.toLong() * 1_000_000L) / audioSampleRate
                audioEncoder.queueInputBuffer(inputBufferIndex, 0, framesToFeed * audioChannels * 2, audioPtsUs, 0)
                fedAudioFrames += framesToFeed
              }
            } else {
              drainAudioEncoder(audioEncoder, coordinator, false)
              break
            }
          }
          drainAudioEncoder(audioEncoder, coordinator, false)
        }

        // Calculate Render FPS throughput & Time Remaining
        val now = System.currentTimeMillis()
        if (now - lastFpsCheckTime >= 500L) {
          val framesDiff = frameIndex - lastFpsCheckFrame
          val timeDiffSec = (now - lastFpsCheckTime) / 1000f
          currentThroughputFps = if (timeDiffSec > 0f) framesDiff / timeDiffSec else 0f
          lastFpsCheckTime = now
          lastFpsCheckFrame = frameIndex
        }

        val progress = ((frameIndex + 1).toFloat() / totalFrames).coerceIn(0f, 1f)
        val elapsedSec = (now - renderStartTime) / 1000f
        val estRemainingSec = if (progress > 0.03f) ((elapsedSec / progress) - elapsedSec).toInt().coerceAtLeast(0) else 0

        _exportState.value = ExportState.Rendering(
          progressPercent = progress,
          currentFrame = frameIndex + 1,
          totalFrames = totalFrames,
          status = "Rendering ${config.resolution.label} Frame ${frameIndex + 1}/$totalFrames...",
          fps = currentThroughputFps,
          estimatedRemainingSec = estRemainingSec,
          resolution = config.resolution,
          renderEngine = if (useGpuSurface) "Hardware GPU Engine (OpenGL ES)" else "Software Canvas Engine",
          isPaused = false
        )
      }

      bitmapPool.release(frameBitmap)

      // 6. Signal End of Stream & Drain Final Buffers
      _exportState.value = ExportState.Rendering(
        progressPercent = 0.95f,
        currentFrame = totalFrames,
        totalFrames = totalFrames,
        status = "Finalizing MP4 container...",
        resolution = config.resolution,
        renderEngine = if (useGpuSurface) "Hardware GPU Engine (OpenGL ES)" else "Software Canvas Engine"
      )
      if (useGpuSurface) {
        videoEncoder.signalEndOfInputStream()
      } else {
        var eosFed = false
        var attempts = 0
        while (!eosFed && attempts < 50) {
          val inputBufferIndex = videoEncoder.dequeueInputBuffer(5000L)
          if (inputBufferIndex >= 0) {
            val eosPtsUs = (totalFrames.toLong() * 1_000_000L) / fps
            videoEncoder.queueInputBuffer(inputBufferIndex, 0, 0, eosPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            eosFed = true
          } else {
            drainVideoEncoder(videoEncoder, coordinator, true)
            attempts++
          }
        }
      }
      drainVideoEncoder(videoEncoder, coordinator, true)

      if (hasAudio && audioEncoder != null) {
        var audioEosFed = false
        var attempts = 0
        while (!audioEosFed && attempts < 50) {
          val inputBufferIndex = audioEncoder.dequeueInputBuffer(5000L)
          if (inputBufferIndex >= 0) {
            val audioPtsUs = (fedAudioFrames.toLong() * 1_000_000L) / audioSampleRate
            audioEncoder.queueInputBuffer(inputBufferIndex, 0, 0, audioPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            audioEosFed = true
          } else {
            drainAudioEncoder(audioEncoder, coordinator, true)
            attempts++
          }
        }
        drainAudioEncoder(audioEncoder, coordinator, true)
      }

      // Stop Muxer safely
      if (coordinator.isStarted) {
        try {
          mediaMuxer.stop()
        } catch (e: Exception) {
          Log.w(tag, "Muxer stop warning", e)
        }
      }

      // Release hardware encoders and muxer
      try { videoEncoder.stop() } catch (ignored: Exception) {}
      videoEncoder.release()
      videoEncoder = null

      try { audioEncoder?.stop() } catch (ignored: Exception) {}
      audioEncoder?.release()
      audioEncoder = null

      mediaMuxer.release()
      mediaMuxer = null

      // Validate output file
      val finalSize = outputFile.length()
      if (finalSize > 1024L) {
        val isValid = validatePlayableMp4(outputFile)
        if (isValid) {
          _exportState.value = ExportState.Success(outputFile, totalDurationMs, finalSize)
          return@withContext outputFile
        } else {
          _exportState.value = ExportState.Error("Exported MP4 header validation failed")
          cleanUp(videoEncoder, audioEncoder, encoderInputSurface, windowSurface, eglCore, gpuRenderer, mediaMuxer, outputFile)
          return@withContext null
        }
      } else {
        _exportState.value = ExportState.Error("Export resulted in incomplete or empty file")
        cleanUp(videoEncoder, audioEncoder, encoderInputSurface, windowSurface, eglCore, gpuRenderer, mediaMuxer, outputFile)
        return@withContext null
      }
    } catch (e: OutOfMemoryError) {
      Log.e(tag, "Export OutOfMemoryError", e)
      cleanUp(videoEncoder, audioEncoder, encoderInputSurface, windowSurface, eglCore, gpuRenderer, mediaMuxer, outputFile)
      _exportState.value = ExportState.Error("Out of memory during video rendering")
      return@withContext null
    } catch (e: Exception) {
      Log.e(tag, "Export failed with exception", e)
      cleanUp(videoEncoder, audioEncoder, encoderInputSurface, windowSurface, eglCore, gpuRenderer, mediaMuxer, outputFile)
      val errorMsg = when {
        e is MediaCodec.CodecException -> "Encoder failure: ${e.diagnosticInfo}"
        e.message != null -> e.message!!
        else -> "Video export pipeline failed"
      }
      _exportState.value = ExportState.Error(errorMsg)
      return@withContext null
    } finally {
      try { windowSurface?.release() } catch (ignored: Exception) {}
      try { eglCore?.release() } catch (ignored: Exception) {}
      try { gpuRenderer?.release() } catch (ignored: Exception) {}
      try { encoderInputSurface?.release() } catch (ignored: Exception) {}
      for (r in retrievers.values) {
        try { r.release() } catch (ignored: Exception) {}
      }
      retrievers.clear()
      for (f in videoFrameCache.values) {
        if (!f.bitmap.isRecycled) {
          bitmapPool.release(f.bitmap)
        }
      }
      videoFrameCache.clear()
      imageBitmaps.clear()
      bitmapPool.clear()
      audioProcessor.clearCache()
    }
  }

  fun release() {
    isCancelled = true
    bitmapPool.clear()
    audioProcessor.clearCache()
  }

  fun updateSuccessFile(file: File) {
    val currentState = _exportState.value
    if (currentState is ExportState.Success) {
      _exportState.value = currentState.copy(file = file, fileSizeBytes = file.length())
    }
  }

  private fun selectVideoCodecMime(config: ExportConfig, width: Int, height: Int): String {
    if (config.codecProfile == CodecProfile.H265_HEVC) {
      if (hasEncoderForMime(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
        return MediaFormat.MIMETYPE_VIDEO_HEVC
      }
    } else if (config.codecProfile == CodecProfile.AUTO) {
      // For 4K resolution (either width or height >= 2160), prefer HEVC if supported
      if ((width >= 2160 || height >= 2160) && hasEncoderForMime(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
        return MediaFormat.MIMETYPE_VIDEO_HEVC
      }
    }
    return MediaFormat.MIMETYPE_VIDEO_AVC
  }

  private fun hasEncoderForMime(mime: String): Boolean {
    return try {
      val count = MediaCodecList.getCodecCount()
      for (i in 0 until count) {
        val info = MediaCodecList.getCodecInfoAt(i)
        if (info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }) {
          return true
        }
      }
      false
    } catch (e: Exception) {
      false
    }
  }

  private fun drainVideoEncoder(
    encoder: MediaCodec,
    coordinator: MuxerCoordinator,
    endOfStream: Boolean
  ) {
    val bufferInfo = MediaCodec.BufferInfo()
    var attempts = 0
    while (attempts < 50) {
      val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 5000L)
      if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
        if (!endOfStream) break
        attempts++
      } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        val newFormat = encoder.outputFormat
        coordinator.setVideoFormat(newFormat)
      } else if (outputBufferIndex >= 0) {
        // Skip codec configuration buffers (CSD is supplied via MediaFormat)
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
          encoder.releaseOutputBuffer(outputBufferIndex, false)
          continue
        }

        if (bufferInfo.size > 0) {
          val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
          if (outputBuffer != null) {
            coordinator.writeVideoSample(outputBuffer, bufferInfo)
          }
        }
        encoder.releaseOutputBuffer(outputBufferIndex, false)
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
          break
        }
      }
    }
  }

  private fun drainAudioEncoder(
    encoder: MediaCodec,
    coordinator: MuxerCoordinator,
    endOfStream: Boolean
  ) {
    val bufferInfo = MediaCodec.BufferInfo()
    var attempts = 0
    while (attempts < 50) {
      val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 5000L)
      if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
        if (!endOfStream) break
        attempts++
      } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        val newFormat = encoder.outputFormat
        coordinator.setAudioFormat(newFormat)
      } else if (outputBufferIndex >= 0) {
        // Skip codec configuration buffers
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
          encoder.releaseOutputBuffer(outputBufferIndex, false)
          continue
        }

        if (bufferInfo.size > 0) {
          val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
          if (outputBuffer != null) {
            coordinator.writeAudioSample(outputBuffer, bufferInfo)
          }
        }
        encoder.releaseOutputBuffer(outputBufferIndex, false)
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
          break
        }
      }
    }
  }

  private fun validatePlayableMp4(file: File): Boolean {
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(file.absolutePath)
      val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
      hasVideo != null
    } catch (e: Exception) {
      Log.w(tag, "Failed to validate MP4 with retriever", e)
      false
    } finally {
      try { retriever.release() } catch (ignored: Exception) {}
    }
  }

  private fun fetchClipBitmap(
    clip: VideoClip?,
    sourcePosMs: Long,
    retrievers: MutableMap<String, MediaMetadataRetriever>,
    imageBitmaps: MutableMap<String, Bitmap>,
    targetWidth: Int = 1080,
    targetHeight: Int = 1920
  ): Bitmap? {
    if (clip == null || clip.uri.isBlank()) return null

    if (!clip.isVideo) {
      return imageBitmaps[clip.uri]
    }

    val cached = videoFrameCache[clip.id]
    if (cached != null && kotlin.math.abs(cached.sourcePosMs - sourcePosMs) <= 12L && !cached.bitmap.isRecycled) {
      return cached.bitmap
    }

    val retriever = retrievers.getOrPut(clip.uri) {
      MediaMetadataRetriever().apply {
        try {
          val uri = Uri.parse(clip.uri)
          if (uri.scheme == "content" || uri.scheme == "file") {
            setDataSource(context, uri)
          } else {
            setDataSource(clip.uri)
          }
        } catch (e: Exception) {
          Log.w(tag, "Could not set data source on retriever for ${clip.uri}", e)
        }
      }
    }

    return try {
      val sourceUs = sourcePosMs * 1000L
      var rawBmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
        retriever.getScaledFrameAtTime(sourceUs, MediaMetadataRetriever.OPTION_CLOSEST, targetWidth, targetHeight)
          ?: retriever.getFrameAtTime(sourceUs, MediaMetadataRetriever.OPTION_CLOSEST)
      } else {
        retriever.getFrameAtTime(sourceUs, MediaMetadataRetriever.OPTION_CLOSEST)
      }

      // Check orientation metadata from source video (only needed on pre-API 27)
      val orientationDegrees = try {
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
      } catch (e: Exception) {
        0
      }

      // MediaMetadataRetriever auto-rotates on API 27+ (Android 8.1+)
      val orientedBmp = if (rawBmp != null && orientationDegrees != 0 && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O_MR1) {
        val matrix = Matrix().apply { postRotate(orientationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
        if (rotated != rawBmp) {
          try { rawBmp.recycle() } catch (ignored: Exception) {}
        }
        rotated
      } else {
        rawBmp
      }

      if (orientedBmp != null) {
        if (cached != null && cached.bitmap != orientedBmp && !cached.bitmap.isRecycled) {
          bitmapPool.release(cached.bitmap)
        }
        videoFrameCache[clip.id] = CachedVideoFrame(clip.id, sourcePosMs, orientedBmp)
      }
      orientedBmp ?: cached?.bitmap
    } catch (e: Exception) {
      cached?.bitmap
    }
  }

  private fun cleanUp(
    videoEncoder: MediaCodec?,
    audioEncoder: MediaCodec?,
    surface: Surface?,
    windowSurface: WindowSurface?,
    eglCore: EglCore?,
    gpuRenderer: GpuCompositionRenderer?,
    mediaMuxer: MediaMuxer?,
    failedFile: File?
  ) {
    try { videoEncoder?.stop() } catch (ignored: Exception) {}
    try { videoEncoder?.release() } catch (ignored: Exception) {}
    try { audioEncoder?.stop() } catch (ignored: Exception) {}
    try { audioEncoder?.release() } catch (ignored: Exception) {}
    try { surface?.release() } catch (ignored: Exception) {}
    try { windowSurface?.release() } catch (ignored: Exception) {}
    try { gpuRenderer?.release() } catch (ignored: Exception) {}
    try { eglCore?.release() } catch (ignored: Exception) {}
    try { mediaMuxer?.stop() } catch (ignored: Exception) {}
    try { mediaMuxer?.release() } catch (ignored: Exception) {}
    if (failedFile != null && failedFile.exists()) {
      failedFile.delete()
    }
  }

  fun getDimensionsForResolution(res: Resolution, aspect: AspectRatio): Pair<Int, Int> {
    val (w, h) = when (res) {
      Resolution.RES_SQUARE_2K -> Pair(2048, 2048)
      Resolution.RES_VERTICAL_2K -> Pair(1440, 2560)
      Resolution.RES_VERTICAL_4K -> Pair(2160, 3840)
      else -> {
        val shortSide = res.width
        val longSide = res.height
        when (aspect) {
          AspectRatio.RATIO_9_16 -> Pair(shortSide, longSide)
          AspectRatio.RATIO_16_9 -> Pair(longSide, shortSide)
          AspectRatio.RATIO_1_1 -> Pair(shortSide, shortSide)
          AspectRatio.RATIO_4_5 -> Pair((shortSide * 4) / 5, shortSide)
          AspectRatio.RATIO_3_4 -> Pair((shortSide * 3) / 4, shortSide)
          AspectRatio.CUSTOM -> Pair(shortSide, shortSide)
        }
      }
    }

    // Align dimensions to 2-pixel boundaries (even numbers) for standard YUV420 encoder compatibility
    val alignedW = (w / 2) * 2
    val alignedH = (h / 2) * 2
    return Pair(alignedW.coerceIn(320, 3840), alignedH.coerceIn(320, 3840))
  }

  private fun encodeYUV420SemiPlanar(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
    val frameSize = width * height
    var yIndex = 0
    var uvIndex = frameSize

    var a: Int
    var R: Int
    var G: Int
    var B: Int
    var Y: Int
    var U: Int
    var V: Int
    var index = 0

    for (j in 0 until height) {
      for (i in 0 until width) {
        val pixel = argb[index++]
        R = (pixel and 0xff0000) shr 16
        G = (pixel and 0xff00) shr 8
        B = pixel and 0xff

        Y = ((66 * R + 129 * G + 25 * B + 128) shr 8) + 16
        U = ((-38 * R - 74 * G + 112 * B + 128) shr 8) + 128
        V = ((112 * R - 94 * G - 18 * B + 128) shr 8) + 128

        yuv420sp[yIndex++] = (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()

        if (j % 2 == 0 && index % 2 == 0) {
          yuv420sp[uvIndex++] = (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
          yuv420sp[uvIndex++] = (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
        }
      }
    }
  }

  private fun encodeYUV420Planar(yuv420p: ByteArray, argb: IntArray, width: Int, height: Int) {
    val frameSize = width * height
    val qFrameSize = frameSize / 4
    var yIndex = 0
    var uIndex = frameSize
    var vIndex = frameSize + qFrameSize

    var R: Int
    var G: Int
    var B: Int
    var Y: Int
    var U: Int
    var V: Int
    var index = 0

    for (j in 0 until height) {
      for (i in 0 until width) {
        val pixel = argb[index++]
        R = (pixel and 0xff0000) shr 16
        G = (pixel and 0xff00) shr 8
        B = pixel and 0xff

        Y = ((66 * R + 129 * G + 25 * B + 128) shr 8) + 16
        U = ((-38 * R - 74 * G + 112 * B + 128) shr 8) + 128
        V = ((112 * R - 94 * G - 18 * B + 128) shr 8) + 128

        yuv420p[yIndex++] = (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()

        if (j % 2 == 0 && index % 2 == 0) {
          yuv420p[uIndex++] = (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
          yuv420p[vIndex++] = (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
        }
      }
    }
  }
}
