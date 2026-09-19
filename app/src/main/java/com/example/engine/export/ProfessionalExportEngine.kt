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
      val requested = videoEncoders.any { it.endsWith(":$effectiveMime") }
      ExportCapabilityReport(videoEncoders.distinct(), audioEncoders.distinct(), h264, hevc, requested, width, height, effectiveMime, if (!requested) "No compatible hardware video encoder for ${width}x${height} @ ${config.frameRate.fps}fps ($effectiveMime)." else null)
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
  fun validate(file: File, config: ExportConfig, expectedDurationMs: Long, requireAudio: Boolean = true, expectedDimensions: Pair<Int, Int>? = null): ExportValidationResult {
    if (!file.exists()) return ExportValidationResult(false, "Output file does not exist.")
    if (file.length() <= 0L) return ExportValidationResult(false, "Output file is empty.")
    val extractor = MediaExtractor()
    return try {
      extractor.setDataSource(file.absolutePath)
      var videoMime: String? = null; var audioMime: String? = null
      var width = 0; var height = 0; var fps: Int? = null; var durationMs = 0L
      var videoTrack = -1; var audioTrack = -1
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i); val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
        val trackDuration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(0L) / 1000L else 0L
        durationMs = max(durationMs, trackDuration)
        if (mime.startsWith("video/")) { if (videoTrack < 0) videoTrack = i; videoMime = mime; width = format.getInteger(MediaFormat.KEY_WIDTH); height = format.getInteger(MediaFormat.KEY_HEIGHT); if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) fps = format.getInteger(MediaFormat.KEY_FRAME_RATE) }
        else if (mime.startsWith("audio/")) { if (audioTrack < 0) audioTrack = i; audioMime = mime }
      }
      if (videoTrack < 0) return ExportValidationResult(false, "MP4 has no video track.")
      if (requireAudio && audioTrack < 0) return ExportValidationResult(false, "MP4 has no audio track.", durationMs, videoMime, audioMime, width, height, fps)
      val expectedMime = if (config.codecProfile == CodecProfile.H265_HEVC) MediaFormat.MIMETYPE_VIDEO_HEVC else null
      if (expectedMime != null && videoMime != expectedMime) return ExportValidationResult(false, "Unexpected video codec: $videoMime; expected $expectedMime.", durationMs, videoMime, audioMime, width, height, fps)
      if (expectedDimensions != null && (width != expectedDimensions.first || height != expectedDimensions.second)) return ExportValidationResult(false, "Resolution mismatch: expected ${expectedDimensions.first}x${expectedDimensions.second}, got ${width}x${height}.", durationMs, videoMime, audioMime, width, height, fps)
      if (fps != null) {
        val requestedFps = config.frameRate.fps; val tolerance = max(1, (requestedFps * 0.02f).toInt())
        if (abs(fps!! - requestedFps) > tolerance) return ExportValidationResult(false, "Frame-rate mismatch: expected about ${requestedFps}fps, got ${fps}fps.", durationMs, videoMime, audioMime, width, height, fps)
      }
      val tolerance = max(750L, expectedDurationMs / 100L)
      if (expectedDurationMs > 0L && abs(durationMs - expectedDurationMs) > tolerance) return ExportValidationResult(false, "Duration mismatch: expected ${expectedDurationMs}ms, got ${durationMs}ms.", durationMs, videoMime, audioMime, width, height, fps)
      extractor.selectTrack(videoTrack)
      val sampleSize = extractor.readSampleData(java.nio.ByteBuffer.allocate(64 * 1024), 0)
      extractor.unselectTrack(videoTrack)
      if (sampleSize <= 0) return ExportValidationResult(false, "Video track cannot be decoded.", durationMs, videoMime, audioMime, width, height, fps)
      ExportValidationResult(true, "Verified", durationMs, videoMime, audioMime, width, height, fps)
    } catch (t: Throwable) { ExportValidationResult(false, "MP4 validation failed: ${t.message ?: "unknown error"}") }
    finally { extractor.release() }
  }
}

enum class ProfessionalExportStage { PREPARING, DECODING, RENDERING, ENCODING_VIDEO, MIXING_AUDIO, MUXING, VERIFYING, COMPLETED, FAILED, CANCELLED }
data class ProfessionalExportProgress(val stage: ProfessionalExportStage = ProfessionalExportStage.PREPARING, val fraction: Float = 0f, val renderedDurationMs: Long = 0L, val estimatedRemainingMs: Long? = null, val message: String = "Preparing export")

class ProfessionalExportEngine(private val context: Context) {
  private val tag = "ProfessionalExportEngine"
  private val _progress = MutableStateFlow(ProfessionalExportProgress())
  val progress: StateFlow<ProfessionalExportProgress> = _progress.asStateFlow()
  @Volatile private var cancelled = false
  @Volatile private var activeExporter: VideoExporter? = null
  @Volatile private var activePipeline: AsyncFramePipelineEngine? = null

  fun cancel() { cancelled = true; activePipeline?.cancel(); activeExporter?.cancelExport() }

  suspend fun export(projectName: String, timeline: Timeline, config: ExportConfig, outputFile: File, requireAudio: Boolean = true): Result<File> = withContext(Dispatchers.IO) {
    cancelled = false; activeExporter = null; activePipeline = null
    try {
      _progress.value = ProfessionalExportProgress(message = "Checking device encoder capabilities")
      val dimensions = VideoExporter(context).getDimensionsForResolution(config.resolution, timeline.aspectRatio)
      val capability = ProfessionalCodecCapabilities.inspect(config, dimensions)
      if (!capability.requestedSupported) return@withContext Result.failure(IllegalStateException(capability.reason ?: "Requested export configuration is unsupported."))
      coroutineContext.ensureActive(); checkCancelled()

      val plan = ExportRenderPlanner.build(timeline, config)
      if (plan.durationMs <= 0L || plan.totalFrames <= 0L) return@withContext Result.failure(IllegalArgumentException("Timeline contains no renderable duration."))
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.PREPARING, 0.02f, message = "Prepared ${plan.totalFrames} deterministic output frames")

      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.RENDERING, 0.05f, message = "Starting asynchronous frame pipeline")
      val hasAudio = AudioExportProcessor(context).hasActiveAudio(timeline)
      // The zero-copy async surface pipeline is reserved for all-video timelines.
      // Image media is intentionally routed through the established compositor so it
      // is rendered correctly instead of failing because the surface decoder has no
      // image input path.
      //
      // IMPORTANT: the fast pipeline's GPU renderer currently applies only
      // timeline.adjustments / timeline.filter / chromaKey — it does not yet run
      // VideoEffectRenderer (the 200+ VFX catalog, body-deformation ML bridge, or
      // any per-clip EffectClip). Routing an effects-bearing timeline through it
      // would silently drop every applied effect from the exported file. Until the
      // fast pipeline gains real per-clip effect compositing, any timeline with
      // active effect clips falls back to the full VideoExporter path, which does
      // apply effects correctly (see VideoCompositionEngine -> VideoEffectRenderer).
      val hasEffectClips = timeline.effectClips.isNotEmpty()
      val asyncSafeTimeline = timeline.videoClips.all { it.isVideo } &&
        timeline.overlayClips.all { it.isVideo } &&
        !hasEffectClips
      val pipeline = if (!hasAudio && asyncSafeTimeline) AsyncFramePipelineEngine(context) else null
      activePipeline = pipeline
      val rendered = coroutineScope {
        if (pipeline != null) {
          // Fast, zero-copy GPU frame pipeline: report real encoded-frame progress
          // (CapCut-style live progress bar) instead of jumping straight to "verifying".
          val progressJob = launch(Dispatchers.Default) {
            while (isActive) {
              val encoded = pipeline.metrics.encodedFrames.get()
              val fraction = if (plan.totalFrames > 0L) (encoded.toFloat() / plan.totalFrames).coerceIn(0f, 0.92f) else 0f
              _progress.value = ProfessionalExportProgress(
                ProfessionalExportStage.ENCODING_VIDEO,
                0.05f + fraction * 0.87f,
                renderedDurationMs = ((encoded.toDouble() / max(1, plan.frameRate)) * 1000L).toLong(),
                message = "Fast GPU pipeline: encoded $encoded / ${plan.totalFrames} frames"
              )
              delay(150L)
            }
          }
          try {
            pipeline.export(timeline, config, outputFile)
          } finally {
            progressJob.cancel()
          }
        } else {
          val exporter = VideoExporter(context)
          activeExporter = exporter
          val progressJob = launch(Dispatchers.Default) {
            exporter.exportState.collectLatest { state ->
              val rendering = state as? ExportState.Rendering
              if (rendering != null) {
                _progress.value = ProfessionalExportProgress(
                  ProfessionalExportStage.ENCODING_VIDEO,
                  0.05f + rendering.progressPercent.coerceIn(0f, 1f) * 0.87f,
                  message = rendering.status
                )
              }
            }
          }
          try {
            exporter.exportProject(projectName, timeline, config)
          } finally {
            progressJob.cancel()
          }
        }
      }
      activePipeline = null; activeExporter = null
      checkCancelled()
      if (rendered == null || !rendered.exists() || rendered.length() <= 0L) return@withContext Result.failure(IllegalStateException("Render pipeline produced no output."))

      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.VERIFYING, 0.94f, plan.durationMs, message = "Verifying MP4 tracks, resolution, FPS, duration and decodability")
      val validation = ExportValidator.validate(rendered, config, plan.durationMs, requireAudio && hasAudio, dimensions)
      if (!validation.valid) { rendered.delete(); return@withContext Result.failure(IllegalStateException(validation.message)) }
      checkCancelled()
      outputFile.parentFile?.mkdirs()
      if (rendered.absolutePath != outputFile.absolutePath) rendered.copyTo(outputFile, overwrite = true)
      if (!outputFile.exists() || outputFile.length() <= 0L) { outputFile.delete(); return@withContext Result.failure(IllegalStateException("Final output could not be written.")) }
      if (rendered.absolutePath != outputFile.absolutePath) rendered.delete()
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.COMPLETED, 1f, plan.durationMs, message = "Export completed and verified")
      Result.success(outputFile)
    } catch (e: CancellationException) {
      activePipeline?.cancel(); activeExporter?.cancelExport(); activePipeline = null; activeExporter = null; outputFile.delete()
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.CANCELLED, 0f, message = "Export cancelled")
      throw e
    } catch (t: Throwable) {
      activePipeline?.cancel(); activeExporter?.cancelExport(); activePipeline = null; activeExporter = null; outputFile.delete()
      Log.e(tag, "Export failed", t)
      _progress.value = ProfessionalExportProgress(ProfessionalExportStage.FAILED, 0f, message = t.message ?: "Export failed")
      Result.failure(t)
    } finally { activePipeline = null; activeExporter = null }
  }

  private fun checkCancelled() { if (cancelled) throw CancellationException("Export cancelled") }
}
