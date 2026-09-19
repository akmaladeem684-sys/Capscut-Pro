package com.example.engine.export

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.example.domain.model.Timeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

data class ExportFramePlan(val frameIndex: Long, val presentationTimeUs: Long, val timelinePositionMs: Long)
data class ExportPlan(val durationMs: Long, val totalFrames: Long, val frameRate: Int, val frames: Sequence<ExportFramePlan>)

/** Deterministic planning metadata; VideoExporter remains the authoritative fallback compositor. */
object ExportRenderPlanner {
  fun build(timeline: Timeline, config: ExportConfig): ExportPlan {
    val durationMs = timeline.totalDurationMs.coerceAtLeast(0L)
    val fps = config.frameRate.fps.coerceAtLeast(1)
    val totalFrames = if (durationMs == 0L) 0L else ceil(durationMs / 1000.0 * fps).toLong()
    val frames = sequence {
      var index = 0L
      while (index < totalFrames) {
        val ptsUs = index * 1_000_000L / fps
        val positionMs = (ptsUs / 1000L).coerceAtMost(max(0L, durationMs - 1L))
        yield(ExportFramePlan(index, ptsUs, positionMs))
        index++
      }
    }
    return ExportPlan(durationMs, totalFrames, fps, frames)
  }

  fun activeVideoClipCount(timeline: Timeline, positionMs: Long): Int =
    timeline.videoClips.count { positionMs >= it.timelineStartMs && positionMs < it.timelineStartMs + it.durationMs }

  fun activeAudioClipCount(timeline: Timeline, positionMs: Long): Int =
    timeline.audioClips.count { positionMs >= it.timelineStartMs && positionMs < it.timelineStartMs + it.durationMs }
}

data class ExportCapabilityReport(
  val videoEncoders: List<String>, val audioEncoders: List<String>, val h264Supported: Boolean,
  val hevcSupported: Boolean, val requestedSupported: Boolean, val width: Int = 0,
  val height: Int = 0, val effectiveMime: String? = null, val reason: String? = null
)

object ProfessionalCodecCapabilities {
  private const val AAC = "audio/mp4a-latm"
  fun inspect(config: ExportConfig, dimensions: Pair<Int, Int>): ExportCapabilityReport {
    val width = dimensions.first; val height = dimensions.second
    val videoEncoders = mutableListOf<String>(); val audioEncoders = mutableListOf<String>()
    var h264 = false; var hevc = false
    return try {
      for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
        if (!info.isEncoder) continue
        val types = info.supportedTypes.asList()
        if (types.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) }) h264 = true
        if (types.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) }) hevc = true
        if (types.any { it.equals(AAC, true) }) audioEncoders += info.name
        for (mime in listOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC)) {
          if (!types.any { it.equals(mime, true) }) continue
          val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: continue
          val vc = caps.videoCapabilities ?: continue
          val sizeOk = vc.isSizeSupported(width, height)
          val fpsOk = runCatching { vc.getSupportedFrameRatesFor(width, height).contains(config.frameRate.fps.toDouble()) }.getOrDefault(false)
          if (sizeOk && fpsOk && isHardware(info)) videoEncoders += "${info.name}:$mime"
        }
      }
      val effectiveMime = when (config.codecProfile) {
        CodecProfile.H265_HEVC -> MediaFormat.MIMETYPE_VIDEO_HEVC
        CodecProfile.H264_AVC -> MediaFormat.MIMETYPE_VIDEO_AVC
        CodecProfile.AUTO -> if ((width >= 2160 || height >= 2160) && hevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
      }
      val requested = videoEncoders.any { it.endsWith(":$effectiveMime") } || (effectiveMime == MediaFormat.MIMETYPE_VIDEO_AVC && h264) || (effectiveMime == MediaFormat.MIMETYPE_VIDEO_HEVC && hevc)
      ExportCapabilityReport(videoEncoders.distinct(), audioEncoders.distinct(), h264, hevc, requested, width, height, effectiveMime, if (!requested) "No compatible video encoder found for ${width}x${height} @ ${config.frameRate.fps}fps ($effectiveMime)." else null)
    } catch (t: Throwable) {
      ExportCapabilityReport(emptyList(), emptyList(), h264, hevc, false, width, height, null, "Codec capability scan failed: ${t.message ?: "unknown error"}")
    }
  }
  private fun isHardware(info: MediaCodecInfo): Boolean = if (android.os.Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else {
    val n = info.name.lowercase(); !n.startsWith("omx.google.") && !n.startsWith("c2.android.") && !n.contains("software") && !n.contains("sw.")
  }
}

data class ExportValidationResult(
  val valid: Boolean, val message: String, val durationMs: Long = 0L, val videoCodec: String? = null,
  val audioCodec: String? = null, val width: Int = 0, val height: Int = 0, val frameRate: Int? = null
)

object ExportValidator {
  fun validate(
    file: File,
    config: ExportConfig,
    expectedDurationMs: Long,
    requireAudio: Boolean = true,
    expectedDimensions: Pair<Int, Int>? = null
  ): ExportValidationResult {
    if (!file.exists()) return ExportValidationResult(false, "Output file does not exist.")
    if (file.length() <= 0L) return ExportValidationResult(false, "Output file is empty.")
    val extractor = MediaExtractor()
    return try {
      extractor.setDataSource(file.absolutePath)
      var videoMime: String? = null
      var audioMime: String? = null
      var width = 0
      var height = 0
      var fps: Int? = null
      var durationMs = 0L
      var videoTrack = -1
      var audioTrack = -1

      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
        val trackDuration = if (format.containsKey(MediaFormat.KEY_DURATION)) {
          format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(0L) / 1000L
        } else 0L
        durationMs = max(durationMs, trackDuration)

        if (mime.startsWith("video/")) {
          if (videoTrack < 0) videoTrack = i
          videoMime = mime
          if (format.containsKey(MediaFormat.KEY_WIDTH)) width = format.getInteger(MediaFormat.KEY_WIDTH)
          if (format.containsKey(MediaFormat.KEY_HEIGHT)) height = format.getInteger(MediaFormat.KEY_HEIGHT)
          if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) fps = format.getInteger(MediaFormat.KEY_FRAME_RATE)
        } else if (mime.startsWith("audio/")) {
          if (audioTrack < 0) audioTrack = i
          audioMime = mime
        }
      }

      if (videoTrack < 0) {
        return ExportValidationResult(false, "MP4 has no video track.")
      }
      if (requireAudio && audioTrack < 0) {
        return ExportValidationResult(false, "MP4 has no audio track.", durationMs, videoMime, audioMime, width, height, fps)
      }

      // Verify at least one video sample is readable
      extractor.selectTrack(videoTrack)
      val sampleBuf = java.nio.ByteBuffer.allocate(64 * 1024)
      val sampleSize = extractor.readSampleData(sampleBuf, 0)
      extractor.unselectTrack(videoTrack)
      if (sampleSize <= 0) {
        return ExportValidationResult(false, "Video track contains no decodable sample data.", durationMs, videoMime, audioMime, width, height, fps)
      }

      ExportValidationResult(true, "Verified", durationMs, videoMime, audioMime, width, height, fps)
    } catch (t: Throwable) {
      ExportValidationResult(false, "MP4 validation failed: ${t.message ?: "unknown error"}")
    } finally {
      try { extractor.release() } catch (_: Throwable) {}
    }
  }
}

enum class ProfessionalExportStage { PREPARING, DECODING, RENDERING, ENCODING_VIDEO, MIXING_AUDIO, MUXING, VERIFYING, COMPLETED, FAILED, CANCELLED }
data class ProfessionalExportProgress(val stage: ProfessionalExportStage = ProfessionalExportStage.PREPARING, val fraction: Float = 0f, val renderedDurationMs: Long = 0L, val estimatedRemainingMs: Long? = null, val message: String = "Preparing export")

class ProfessionalExportEngine(private val context: Context) {
  private val tag = "ProfessionalExportEngine"
  private val _progress = MutableStateFlow(ProfessionalExportProgress())
  val progress: StateFlow<ProfessionalExportProgress> = _progress.asStateFlow()
  @Volatile private var cancelled = false
  @Volatile private var activePipeline: AsyncFramePipelineEngine? = null

  fun cancel() {
    cancelled = true
    activePipeline?.cancel()
  }

  suspend fun export(
    projectName: String,
    timeline: Timeline,
    config: ExportConfig,
    outputFile: File,
    requireAudio: Boolean = true
  ): Result<File> = withContext(Dispatchers.IO) {
    cancelled = false
    activePipeline = null
    try {
      _progress.value = ProfessionalExportProgress(message = "Checking device encoder capabilities")
      val dimensions = VideoExporter(context).getDimensionsForResolution(config.resolution, timeline.aspectRatio)
      val capability = ProfessionalCodecCapabilities.inspect(config, dimensions)
      if (!capability.requestedSupported) {
        return@withContext Result.failure(IllegalStateException(capability.reason ?: "Requested export configuration is unsupported."))
      }
      coroutineContext.ensureActive()
      checkCancelled()

      val plan = ExportRenderPlanner.build(timeline, config)
      if (plan.durationMs <= 0L || plan.totalFrames <= 0L) {
        return@withContext Result.failure(IllegalArgumentException("Timeline contains no renderable duration."))
      }
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.PREPARING, 0.05f, message = "Prepared ${plan.totalFrames} deterministic output frames")

      val hasAudio = AudioExportProcessor(context).hasActiveAudio(timeline)

      val rendered = coroutineScope {
        var pipelineResult: File? = null
        val pipeline = AsyncFramePipelineEngine(context)
        activePipeline = pipeline

        val progressJob = launch(Dispatchers.Default) {
          while (isActive) {
            val encoded = pipeline.metrics.encodedFrames.get()
            val fraction = if (plan.totalFrames > 0L) (encoded.toFloat() / plan.totalFrames).coerceIn(0f, 1f) else 0f
            _progress.value = ProfessionalExportProgress(
              ProfessionalExportStage.ENCODING_VIDEO,
              0.05f + fraction * 0.90f,
              renderedDurationMs = ((encoded.toDouble() / max(1, plan.frameRate)) * 1000L).toLong(),
              message = "Hardware GPU pipeline: encoded $encoded / ${plan.totalFrames} frames (${(fraction * 100).toInt()}%)"
            )
            delay(100L)
          }
        }

        try {
          pipelineResult = pipeline.export(timeline, config, outputFile)
        } catch (t: Throwable) {
          Log.w(tag, "Hardware GPU pipeline failed", t)
        } finally {
          progressJob.cancel()
        }

        pipelineResult
      }
      activePipeline = null
      checkCancelled()
      if (rendered == null || !rendered.exists() || rendered.length() <= 0L) {
        return@withContext Result.failure(IllegalStateException("Hardware render pipeline produced no output."))
      }

      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.VERIFYING, 0.96f, plan.durationMs, message = "Verifying exported video integrity...")
      val validation = ExportValidator.validate(rendered, config, plan.durationMs, requireAudio && hasAudio, dimensions)
      Log.i(tag, "[VALIDATION_RESULT] valid=${validation.valid} message=${validation.message} duration=${validation.durationMs}ms videoCodec=${validation.videoCodec} audioCodec=${validation.audioCodec} res=${validation.width}x${validation.height}")
      if (!validation.valid) {
        rendered.delete()
        return@withContext Result.failure(IllegalStateException(validation.message))
      }
      checkCancelled()

      outputFile.parentFile?.mkdirs()
      if (rendered.absolutePath != outputFile.absolutePath) {
        rendered.copyTo(outputFile, overwrite = true)
        rendered.delete()
      }
      if (!outputFile.exists() || outputFile.length() <= 0L) {
        outputFile.delete()
        return@withContext Result.failure(IllegalStateException("Final output file could not be written."))
      }

      Log.i(tag, "[EXPORT_COMPLETE] path=${outputFile.absolutePath} sizeBytes=${outputFile.length()}")
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.COMPLETED, 1f, plan.durationMs, message = "Export completed successfully!")
      Result.success(outputFile)
    } catch (e: CancellationException) {
      activePipeline?.cancel()
      activePipeline = null
      outputFile.delete()
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.CANCELLED, 0f, message = "Export cancelled")
      throw e
    } catch (t: Throwable) {
      activePipeline?.cancel()
      activePipeline = null
      outputFile.delete()
      Log.e(tag, "Export failed", t)
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.FAILED, 0f, message = t.message ?: "Export failed")
      Result.failure(t)
    } finally {
      activePipeline = null
    }
  }

  private fun checkCancelled() {
    if (cancelled) throw CancellationException("Export cancelled")
  }
}
