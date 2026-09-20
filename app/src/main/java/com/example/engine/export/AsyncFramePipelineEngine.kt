package com.example.engine.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.example.domain.model.*
import com.example.engine.composition.VideoCompositionEngine
import com.example.engine.composition.gpu.EglCore
import com.example.engine.composition.gpu.GpuCompositionRenderer
import com.example.engine.composition.gpu.HardwareVideoTextureSource
import com.example.engine.composition.gpu.WindowSurface
import com.example.engine.controller.DecoderManager
import com.example.engine.media.MediaRelinkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * Performance metrics for the hardware video export pipeline.
 */
class AsyncFramePipelineMetrics {
  val decodedFrames = AtomicLong()
  val gpuFrames = AtomicLong()
  val encodedFrames = AtomicLong()
  val zeroCopyFrames = AtomicLong()
  val gpuToCpuCopies = AtomicLong()
  val decodeTimeNs = AtomicLong()
  val gpuRenderTimeNs = AtomicLong()

  fun snapshot() = mapOf(
    "decodedFrames" to decodedFrames.get(),
    "gpuFrames" to gpuFrames.get(),
    "encodedFrames" to encodedFrames.get(),
    "zeroCopyFrames" to zeroCopyFrames.get(),
    "gpuToCpuCopies" to gpuToCpuCopies.get()
  )
}

/**
 * Thread-safe bounded queue for pipeline frame synchronization.
 */
class FramePacketQueue<T>(val capacity: Int) {
  private val queue = java.util.concurrent.ArrayBlockingQueue<T>(capacity)

  fun put(item: T, cancelled: AtomicBoolean): Boolean {
    while (!cancelled.get()) {
      if (queue.offer(item, 50, TimeUnit.MILLISECONDS)) return true
    }
    return false
  }

  fun take(cancelled: AtomicBoolean): T? {
    while (!cancelled.get()) {
      val item = queue.poll(50, TimeUnit.MILLISECONDS)
      if (item != null) return item
    }
    return null
  }

  fun depth(): Int = queue.size
}

/**
 * Hardware-accelerated clip decoder rendering directly to an OpenGL OES texture with software fallback.
 */
private class HardwareClipDecoder(
  private val context: Context,
  val clip: VideoClip,
  private val glHandler: Handler,
  private val decoderHandler: Handler,
  private val decoderManager: DecoderManager = DecoderManager()
) {
  private val tag = "HardwareClipDecoder"
  private val textureSource = HardwareVideoTextureSource()

  val textureId: Int get() = textureSource.oesTextureId
  val width: Int get() = textureSource.effectiveWidth
  val height: Int get() = textureSource.effectiveHeight
  val rotationDegrees: Int get() = textureSource.rotationDegrees
  val isHardwareAccelerated: Boolean get() = textureSource.isHardwareAccelerated
  val transformMatrix: FloatArray get() = textureSource.transformMatrix

  fun init(): Boolean {
    return textureSource.initialize(
      context = context,
      clip = clip,
      glHandler = glHandler,
      decoderHandler = decoderHandler,
      decoderManager = decoderManager
    )
  }

  fun decodeFrame(targetUs: Long, cancelled: AtomicBoolean): Boolean {
    return textureSource.decodeFrame(targetUs, cancelled)
  }

  fun updateTexImageOnGl() {
    textureSource.updateTexImage()
  }

  fun release() {
    textureSource.release()
  }
}

/**
 * High-performance CapCut-level hardware GPU video & audio export pipeline.
 */
class AsyncFramePipelineEngine(private val context: Context) {
  private val tag = "AsyncFramePipeline"
  private val cancelled = AtomicBoolean(false)
  private val glThread = HandlerThread("AH-GPU-Pipeline").apply { start() }
  private val glHandler = Handler(glThread.looper)
  private val decoderThread = HandlerThread("AH-Decoder-Pipeline").apply { start() }
  private val decoderHandler = Handler(decoderThread.looper)
  private val composition = VideoCompositionEngine(context)
  private val audioProcessor = AudioExportProcessor(context)
  private val decoderManager = DecoderManager()
  val metrics = AsyncFramePipelineMetrics()

  fun cancel() {
    cancelled.set(true)
  }

  private data class PendingMuxerSample(
    val isAudio: Boolean,
    val data: ByteArray,
    val offset: Int,
    val size: Int,
    val presentationTimeUs: Long,
    val flags: Int
  )

  suspend fun export(
    timeline: Timeline,
    config: ExportConfig,
    outputFile: File
  ): File? = withContext(Dispatchers.IO) {
    cancelled.set(false)
    val fps = config.frameRate.fps.coerceIn(15, 120)
    val durationMs = timeline.totalDurationMs
    val totalFrames = max(1L, ((durationMs.toDouble() / 1000.0) * fps).toLong())
    val (exportWidth, exportHeight) = dimensions(config.resolution, timeline.aspectRatio)

    Log.i(tag, "Starting Hardware GPU Export: ${exportWidth}x${exportHeight} @ ${fps}fps ($durationMs ms, $totalFrames frames)")

    // Pre-calculate / Mix Audio
    var hasAudio = audioProcessor.hasActiveAudio(timeline)
    val audioSampleRate = audioProcessor.sampleRate
    val audioChannels = audioProcessor.channelCount
    val masterPcm = if (hasAudio) {
      audioProcessor.mixTimelineAudio(timeline, durationMs) { cancelled.get() }
    } else {
      ShortArray(0)
    }
    if (masterPcm.isEmpty()) {
      hasAudio = false
    }

    var eglCore: EglCore? = null
    var windowSurface: WindowSurface? = null
    var gpuRenderer: GpuCompositionRenderer? = null
    var videoEncoder: MediaCodec? = null
    var audioEncoder: MediaCodec? = null
    var encoderInputSurface: Surface? = null
    var muxer: MediaMuxer? = null
    var muxerCoordinator: MuxerCoordinator? = null

    val isMuxStarted = AtomicBoolean(false)
    val videoTrack = AtomicInteger(-1)
    val audioTrack = AtomicInteger(-1)
    val videoEos = AtomicBoolean(false)
    val audioEos = AtomicBoolean(false)
    val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)

    val decoders = mutableMapOf<String, HardwareClipDecoder>()
    val imageBitmaps = mutableMapOf<String, Bitmap>()
    val imageTextures = mutableMapOf<String, Int>()
    val pendingSamples = ConcurrentLinkedQueue<PendingMuxerSample>()

    try {
      // 1. Pre-load Images & Fallback Bitmaps
      for (clip in timeline.videoClips + timeline.overlayClips) {
        if (!clip.isVideo && clip.uri.isNotBlank()) {
          try {
            val uri = Uri.parse(clip.uri)
            val bmp = if (uri.scheme == "content") {
              context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } else if (uri.scheme == "file") {
              BitmapFactory.decodeFile(uri.path)
            } else {
              BitmapFactory.decodeFile(clip.uri)
            }
            if (bmp != null) {
              imageBitmaps[clip.uri] = bmp
            }
          } catch (e: Exception) {
            Log.w(tag, "Failed to load image for ${clip.uri}", e)
          }
        }
      }

      // 2. Configure Video Encoder
      val videoMime = selectEncoder(config, exportWidth, exportHeight, fps) ?: MediaFormat.MIMETYPE_VIDEO_AVC
      val bitrateBps = bitrate(config)
      val videoFormat = MediaFormat.createVideoFormat(videoMime, exportWidth, exportHeight).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        try {
          setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        } catch (_: Exception) {}
      }

      videoEncoder = MediaCodec.createEncoderByType(videoMime).apply {
        configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoderInputSurface = createInputSurface()
      }

      // 3. Initialize EGL & GPU Composition on Dedicated GL Thread
      val glInitLatch = CountDownLatch(1)
      glHandler.post {
        try {
          val core = EglCore(null, EglCore.FLAG_RECORDABLE)
          val inputSurface = encoderInputSurface ?: throw IllegalStateException("Encoder input surface is null")
          val winSurface = WindowSurface(core, inputSurface, false)
          winSurface.makeCurrent()

          val rend = GpuCompositionRenderer(context)
          rend.initGl()

          // Upload image textures to GPU
          for ((uri, bmp) in imageBitmaps) {
            val texId = rend.uploadImageTexture(uri, bmp)
            imageTextures[uri] = texId
          }

          eglCore = core
          windowSurface = winSurface
          gpuRenderer = rend
        } catch (t: Throwable) {
          failure.set(t)
        } finally {
          glInitLatch.countDown()
        }
      }
      if (!glInitLatch.await(5, TimeUnit.SECONDS)) {
        throw IllegalStateException("Timed out initializing EGL GPU surface")
      }
      failure.get()?.let { throw it }

      // 4. Configure Audio Encoder if Audio is Present
      if (hasAudio) {
        try {
          val audioMime = MediaFormat.MIMETYPE_AUDIO_AAC
          val aacFormat = MediaFormat.createAudioFormat(audioMime, audioSampleRate, audioChannels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
          }
          audioEncoder = MediaCodec.createEncoderByType(audioMime).apply {
            configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
          }
        } catch (e: Exception) {
          Log.w(tag, "Audio encoder configuration failed: ${e.message}", e)
          audioEncoder = null
          hasAudio = false
        }
      }

      // 5. Initialize MediaMuxer & MuxerCoordinator
      outputFile.parentFile?.mkdirs()
      if (outputFile.exists()) outputFile.delete()
      val localMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      muxer = localMuxer
      val localCoordinator = MuxerCoordinator(localMuxer, hasAudio)
      muxerCoordinator = localCoordinator

      videoEncoder.start()

      // 6. Start Asynchronous Drain Thread
      val drainDone = CountDownLatch(1)
      val drainExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "AH-GPU-MuxDrain") }
      drainExecutor.execute {
        try {
          val vInfo = MediaCodec.BufferInfo()
          val aInfo = MediaCodec.BufferInfo()

          while (!cancelled.get() && (!videoEos.get() || (hasAudio && !audioEos.get()))) {
            // Drain Video
            if (!videoEos.get()) {
              val vIndex = videoEncoder.dequeueOutputBuffer(vInfo, 2_000L)
              when {
                vIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                  localCoordinator.setVideoFormat(videoEncoder.outputFormat)
                }
                vIndex >= 0 -> {
                  val out = videoEncoder.getOutputBuffer(vIndex)
                  if (out != null && vInfo.size > 0) {
                    localCoordinator.writeVideoSample(out, vInfo)
                    metrics.encodedFrames.incrementAndGet()
                  }
                  val isEos = (vInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                  videoEncoder.releaseOutputBuffer(vIndex, false)
                  if (isEos) videoEos.set(true)
                }
              }
            }

            // Drain Audio
            if (hasAudio && audioEncoder != null && !audioEos.get()) {
              val aIndex = audioEncoder.dequeueOutputBuffer(aInfo, 2_000L)
              when {
                aIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                  localCoordinator.setAudioFormat(audioEncoder.outputFormat)
                }
                aIndex >= 0 -> {
                  val out = audioEncoder.getOutputBuffer(aIndex)
                  if (out != null && aInfo.size > 0) {
                    localCoordinator.writeAudioSample(out, aInfo)
                  }
                  val isEos = (aInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                  audioEncoder.releaseOutputBuffer(aIndex, false)
                  if (isEos) audioEos.set(true)
                }
              }
            }
          }
        } catch (t: Throwable) {
          Log.e(tag, "Error in drain thread", t)
          failure.set(t)
          cancelled.set(true)
        } finally {
          drainDone.countDown()
        }
      }

      // 7. Video Frame Hardware Composition Loop
      val totalAudioFrames = if (hasAudio) masterPcm.size / audioChannels else 0
      var fedAudioFrames = 0
      val renderCompleteLatch = CountDownLatch(1)
      val maxConcurrentDecoders = DecoderManager.MAX_RECOMMENDED_HARDWARE_DECODERS

      fun getOrCreateDecoder(clip: VideoClip, currentActiveIds: Set<String>): HardwareClipDecoder? {
        val existing = decoders[clip.id]
        if (existing != null) return existing

        if (decoders.size >= maxConcurrentDecoders) {
          val evictCandidate = decoders.keys.firstOrNull { it !in currentActiveIds }
          if (evictCandidate != null) {
            val evicted = decoders.remove(evictCandidate)
            evicted?.release()
            Log.d(tag, "Evicted idle decoder for clip $evictCandidate to avoid codec exhaustion")
          }
        }

        val newDecoder = HardwareClipDecoder(context, clip, glHandler, decoderHandler, decoderManager)
        return if (newDecoder.init()) {
          decoders[clip.id] = newDecoder
          newDecoder
        } else {
          newDecoder.release()
          null
        }
      }

      glHandler.post {
        try {
          for (frameIndex in 0 until totalFrames) {
            if (cancelled.get()) break

            val ptsUs = (frameIndex * 1_000_000L) / fps
            val timelinePosMs = (ptsUs / 1000L).coerceAtMost(durationMs - 1L)
            val frame = composition.evaluateFrame(timeline, timelinePosMs)
            val activeClip = frame.activeClip

            val currentNeededClipIds = mutableSetOf<String>()
            if (activeClip != null && activeClip.isVideo) currentNeededClipIds.add(activeClip.id)
            for (ov in frame.activeOverlays) {
              if (ov.clip.isVideo) currentNeededClipIds.add(ov.clip.id)
            }

            var mainTexId = 0
            var isMainOes = false
            var mainTexMatrix: FloatArray? = null

            // 1. Process Main Clip
            if (activeClip != null) {
              if (activeClip.isVideo && activeClip.uri.isNotBlank()) {
                val decoder = getOrCreateDecoder(activeClip, currentNeededClipIds)
                if (decoder != null && decoder.textureId != 0) {
                  decoder.decodeFrame(frame.clipSourcePosMs * 1000L, cancelled)
                  decoder.updateTexImageOnGl()
                  mainTexId = decoder.textureId
                  isMainOes = true
                  mainTexMatrix = decoder.transformMatrix
                  metrics.decodedFrames.incrementAndGet()
                  metrics.zeroCopyFrames.incrementAndGet()
                }
              } else {
                mainTexId = imageTextures[activeClip.uri] ?: 0
                isMainOes = false
              }
            }

            // 2. Process Overlays
            val overlayTextures = HashMap<String, Int>()
            for (overlay in frame.activeOverlays) {
              if (overlay.clip.isVideo && overlay.clip.uri.isNotBlank()) {
                val ovDecoder = getOrCreateDecoder(overlay.clip, currentNeededClipIds)
                if (ovDecoder != null && ovDecoder.textureId != 0) {
                  ovDecoder.decodeFrame(overlay.sourcePosMs * 1000L, cancelled)
                  ovDecoder.updateTexImageOnGl()
                  overlayTextures[overlay.clip.id] = ovDecoder.textureId
                }
              } else {
                val texId = imageTextures[overlay.clip.uri]
                if (texId != null && texId > 0) {
                  overlayTextures[overlay.clip.id] = texId
                }
              }
            }

            // 3. Render Composition to EGL Surface
            val renderStart = System.nanoTime()
            gpuRenderer?.render(
              frame = frame,
              mainTextureId = mainTexId,
              isMainOes = isMainOes,
              mainTexMatrix = mainTexMatrix,
              overlayTextures = overlayTextures,
              viewportWidth = exportWidth,
              viewportHeight = exportHeight,
              timelineAdjustments = timeline.adjustments,
              timelineFilter = timeline.filter,
              chromaKey = timeline.chromaKey
            )
            GLES20.glFinish()
            windowSurface?.setPresentationTime(ptsUs * 1000L)
            windowSurface?.swapBuffers()
            metrics.gpuRenderTimeNs.addAndGet(System.nanoTime() - renderStart)
            metrics.gpuFrames.incrementAndGet()

            // 4. Feed Audio Pro-Rata with Exact PTS
            if (hasAudio && audioEncoder != null) {
              val targetAudioFrames = (((frameIndex + 1).toDouble() * audioSampleRate) / fps).toInt().coerceAtMost(totalAudioFrames)
              while (fedAudioFrames < targetAudioFrames && !cancelled.get()) {
                val framesToFeed = min(1024, targetAudioFrames - fedAudioFrames)
                if (framesToFeed <= 0) break
                val inputIndex = audioEncoder.dequeueInputBuffer(2_000L)
                if (inputIndex >= 0) {
                  val inputBuffer = audioEncoder.getInputBuffer(inputIndex)
                  if (inputBuffer != null) {
                    inputBuffer.clear()
                    inputBuffer.order(ByteOrder.nativeOrder())
                    val samplesToFeed = framesToFeed * audioChannels
                    val startIdx = fedAudioFrames * audioChannels
                    for (k in 0 until samplesToFeed) {
                      val idx = startIdx + k
                      val sample = if (idx < masterPcm.size) masterPcm[idx] else 0.toShort()
                      inputBuffer.putShort(sample)
                    }
                    val audioPtsUs = (fedAudioFrames.toLong() * 1_000_000L) / audioSampleRate
                    audioEncoder.queueInputBuffer(inputIndex, 0, framesToFeed * audioChannels * 2, audioPtsUs, 0)
                    fedAudioFrames += framesToFeed
                  }
                } else break
              }
            }
          }
        } catch (t: Throwable) {
          Log.e(tag, "Render loop error", t)
          failure.set(t)
          cancelled.set(true)
        } finally {
          renderCompleteLatch.countDown()
        }
      }

      renderCompleteLatch.await()
      failure.get()?.let { throw it }

      // 8. Signal EOS
      if (!cancelled.get()) {
        try {
          videoEncoder.signalEndOfInputStream()
        } catch (e: Exception) {
          Log.w(tag, "signalEndOfInputStream error", e)
        }

        if (hasAudio && audioEncoder != null) {
          // Flush any remaining audio frames before sending EOS
          var flushAttempts = 0
          while (fedAudioFrames < totalAudioFrames && !cancelled.get() && flushAttempts < 100) {
            val framesToFeed = min(1024, totalAudioFrames - fedAudioFrames)
            if (framesToFeed <= 0) break
            val inputIndex = audioEncoder.dequeueInputBuffer(10_000L)
            if (inputIndex >= 0) {
              val inputBuffer = audioEncoder.getInputBuffer(inputIndex)
              if (inputBuffer != null) {
                inputBuffer.clear()
                inputBuffer.order(ByteOrder.nativeOrder())
                val samplesToFeed = framesToFeed * audioChannels
                val startIdx = fedAudioFrames * audioChannels
                for (k in 0 until samplesToFeed) {
                  val idx = startIdx + k
                  val sample = if (idx < masterPcm.size) masterPcm[idx] else 0.toShort()
                  inputBuffer.putShort(sample)
                }
                val audioPtsUs = (fedAudioFrames.toLong() * 1_000_000L) / audioSampleRate
                audioEncoder.queueInputBuffer(inputIndex, 0, framesToFeed * audioChannels * 2, audioPtsUs, 0)
                fedAudioFrames += framesToFeed
              }
            } else {
              flushAttempts++
              Thread.sleep(5)
            }
          }

          var audioEosSent = false
          var attempts = 0
          while (!audioEosSent && attempts < 50 && !cancelled.get()) {
            val inputIndex = audioEncoder.dequeueInputBuffer(5_000L)
            if (inputIndex >= 0) {
              val audioPtsUs = (fedAudioFrames.toLong() * 1_000_000L) / audioSampleRate
              audioEncoder.queueInputBuffer(inputIndex, 0, 0, audioPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              audioEosSent = true
            } else {
              attempts++
              Thread.sleep(5)
            }
          }
        }
      }

      drainDone.await(45, TimeUnit.SECONDS)
      drainExecutor.shutdown()

      failure.get()?.let { throw it }

      if (cancelled.get()) {
        outputFile.delete()
        return@withContext null
      }

      Log.i(tag, "Hardware Export Finished: ${outputFile.absolutePath} (${outputFile.length()} bytes, ${metrics.encodedFrames.get()} frames)")
      if (outputFile.exists() && outputFile.length() > 0L) outputFile else null
    } catch (t: Throwable) {
      Log.e(tag, "Async hardware export pipeline error", t)
      outputFile.delete()
      null
    } finally {
      cancelled.set(true)
      decoders.values.forEach { runCatching { it.release() } }
      decoders.clear()

      val cleanLatch = CountDownLatch(1)
      glHandler.post {
        runCatching { gpuRenderer?.release() }
        runCatching { windowSurface?.release() }
        runCatching { eglCore?.release() }
        cleanLatch.countDown()
      }
      cleanLatch.await(2, TimeUnit.SECONDS)

      runCatching { encoderInputSurface?.release() }
      runCatching { videoEncoder?.stop() }; runCatching { videoEncoder?.release() }
      runCatching { audioEncoder?.stop() }; runCatching { audioEncoder?.release() }
      if (muxerCoordinator?.isStarted == true) runCatching { muxer?.stop() }
      runCatching { muxer?.release() }
      glThread.quitSafely()
      decoderThread.quitSafely()
    }
  }

  private fun selectEncoder(config: ExportConfig, w: Int, h: Int, fps: Int): String? {
    val mimes = when (config.codecProfile) {
      CodecProfile.H265_HEVC -> listOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC)
      CodecProfile.H264_AVC -> listOf(MediaFormat.MIMETYPE_VIDEO_AVC)
      CodecProfile.AUTO -> listOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC)
    }
    for (mime in mimes) {
      for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
        if (!info.isEncoder || !info.supportedTypes.any { it.equals(mime, true) }) continue
        val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: continue
        val vc = caps.videoCapabilities ?: continue
        val sizeOk = runCatching { vc.isSizeSupported(w, h) }.getOrDefault(true)
        val formatOk = caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        if (formatOk && sizeOk) return mime
      }
    }
    return MediaFormat.MIMETYPE_VIDEO_AVC
  }

  private fun bitrate(config: ExportConfig): Int {
    if (config.quality == ExportQuality.CUSTOM && config.customBitrateKbps > 0) {
      return config.customBitrateKbps * 1000
    }
    return when (config.resolution) {
      Resolution.RES_480P -> 2_500_000
      Resolution.RES_720P -> 5_000_000
      Resolution.RES_1080P -> 10_000_000
      Resolution.RES_2K, Resolution.RES_VERTICAL_2K -> 18_000_000
      Resolution.RES_4K, Resolution.RES_VERTICAL_4K -> 35_000_000
      Resolution.RES_SQUARE_2K -> 22_000_000
    }
  }

  private fun dimensions(resolution: Resolution, aspect: AspectRatio): Pair<Int, Int> {
    val vertical = aspect.ratio < 1f
    val (w, h) = when (resolution) {
      Resolution.RES_480P -> if (vertical) 480 to 854 else 854 to 480
      Resolution.RES_720P -> if (vertical) 720 to 1280 else 1280 to 720
      Resolution.RES_1080P -> if (vertical) 1080 to 1920 else 1920 to 1080
      Resolution.RES_2K -> if (vertical) 1440 to 2560 else 2560 to 1440
      Resolution.RES_VERTICAL_2K -> 1440 to 2560
      Resolution.RES_4K -> if (vertical) 2160 to 3840 else 3840 to 2160
      Resolution.RES_VERTICAL_4K -> 2160 to 3840
      Resolution.RES_SQUARE_2K -> 2048 to 2048
    }
    val alignedW = (w / 2) * 2
    val alignedH = (h / 2) * 2
    return alignedW to alignedH
  }
}
