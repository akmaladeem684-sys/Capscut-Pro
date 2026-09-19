package com.example.engine.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaMuxer
import android.media.MediaExtractor
import android.media.MediaFormat
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
import com.example.engine.composition.gpu.WindowSurface
import com.example.engine.media.MediaRelinkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** GPU handle only. Video pixels never enter this object as Bitmap/ByteBuffer data. */
data class FramePacket(
  val frameIndex: Long,
  val presentationTimeUs: Long,
  val sourceTimestampUs: Long,
  val textureId: Int,
  val textureTarget: Int,
  val transformMatrix: FloatArray,
  val width: Int,
  val height: Int,
  val clipId: String
)

data class PipelineFrame(val index: Long, val ptsUs: Long, val timelinePosMs: Long)

class FramePacketQueue<T>(capacity: Int = 3) {
  private val queue = ArrayBlockingQueue<T>(capacity.coerceIn(1, 8))
  fun put(value: T, cancelled: AtomicBoolean): Boolean {
    while (!cancelled.get()) {
      if (queue.offer(value, 50, TimeUnit.MILLISECONDS)) return true
    }
    return false
  }
  fun take(cancelled: AtomicBoolean): T? {
    while (!cancelled.get()) {
      queue.poll(50, TimeUnit.MILLISECONDS)?.let { return it }
    }
    return null
  }
  fun depth(): Int = queue.size
  fun clear() = queue.clear()
}

class AsyncFramePipelineMetrics {
  val decodedFrames = AtomicLong()
  val gpuFrames = AtomicLong()
  val encodedFrames = AtomicLong()
  val droppedFrames = AtomicLong()
  val duplicatedFrames = AtomicLong()
  val gpuToCpuCopies = AtomicLong()
  val cpuToGpuCopies = AtomicLong()
  val zeroCopyFrames = AtomicLong()
  val backpressureEvents = AtomicLong()
  val decodeTimeNs = AtomicLong()
  val gpuRenderTimeNs = AtomicLong()
  val encodeTimeNs = AtomicLong()
  val maxQueueDepth = AtomicLong()

  fun snapshot() = mapOf(
    "decodedFrames" to decodedFrames.get(),
    "gpuFrames" to gpuFrames.get(),
    "encodedFrames" to encodedFrames.get(),
    "droppedFrames" to droppedFrames.get(),
    "duplicatedFrames" to duplicatedFrames.get(),
    "gpuToCpuCopies" to gpuToCpuCopies.get(),
    "cpuToGpuCopies" to cpuToGpuCopies.get(),
    "zeroCopyFrames" to zeroCopyFrames.get(),
    "backpressureEvents" to backpressureEvents.get(),
    "maxQueueDepth" to maxQueueDepth.get()
  )
}

/**
 * MediaCodec hardware surface decoder.
 * Codec decoding runs off GL thread and renders directly to Surface/SurfaceTexture;
 * updateTexImage runs on GL thread only.
 */
private class SurfaceDecoder(
  private val context: Context,
  private val clip: VideoClip,
  private val glHandler: Handler
) : SurfaceTexture.OnFrameAvailableListener {
  private val tag = "SurfaceDecoder"
  private val frameReady = AtomicBoolean(false)
  private var codec: MediaCodec? = null
  private var extractor: MediaExtractor? = null
  private var surface: Surface? = null
  private var surfaceTexture: SurfaceTexture? = null
  private var textureId = 0
  private var width = clip.width.coerceAtLeast(1)
  private var height = clip.height.coerceAtLeast(1)
  private var lastRequestUs = Long.MIN_VALUE
  private var isInputEos = false

  fun start() {
    require(MediaRelinkManager.isRealPlayableMedia(context, clip.uri)) { "Unplayable video: ${clip.uri}" }
    val ex = MediaExtractor()
    val uri = Uri.parse(clip.uri)
    if (uri.scheme == "content" || uri.scheme == "file") {
      ex.setDataSource(context, uri, null)
    } else {
      ex.setDataSource(clip.uri)
    }
    var track = -1
    var fmt: MediaFormat? = null
    for (i in 0 until ex.trackCount) {
      val f = ex.getTrackFormat(i)
      if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) {
        track = i
        fmt = f
        break
      }
    }
    require(track >= 0 && fmt != null) { "No video track for ${clip.id}" }
    ex.selectTrack(track)
    width = fmt.getInteger(MediaFormat.KEY_WIDTH).coerceAtLeast(1)
    height = fmt.getInteger(MediaFormat.KEY_HEIGHT).coerceAtLeast(1)

    val latch = CountDownLatch(1)
    glHandler.post {
      try {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        surfaceTexture = SurfaceTexture(textureId).also {
          it.setOnFrameAvailableListener(this, glHandler)
        }
        surface = Surface(surfaceTexture)
      } finally {
        latch.countDown()
      }
    }
    check(latch.await(5, TimeUnit.SECONDS)) { "Timed out creating decoder SurfaceTexture" }

    val decoderMime = fmt.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
    codec = MediaCodec.createDecoderByType(decoderMime).also {
      it.configure(fmt, surface, null, 0)
      it.start()
    }
    extractor = ex
  }

  override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
    frameReady.set(true)
  }

  fun decode(targetUs: Long, cancelled: AtomicBoolean): FramePacket {
    val c = codec ?: return defaultPacket(targetUs)
    val ex = extractor ?: return defaultPacket(targetUs)

    // Handle backward seek or large forward jump
    if (targetUs < lastRequestUs || lastRequestUs == Long.MIN_VALUE || (targetUs - lastRequestUs > 2_000_000L)) {
      ex.seekTo(targetUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
      try {
        c.flush()
      } catch (ignored: Exception) {}
      frameReady.set(false)
      isInputEos = false
    }
    lastRequestUs = targetUs

    val info = MediaCodec.BufferInfo()
    var output = false
    var outputPts = targetUs
    var attempts = 0
    val maxAttempts = 60

    while (!output && !cancelled.get() && attempts < maxAttempts) {
      attempts++
      if (!isInputEos) {
        val inputIndex = c.dequeueInputBuffer(2_000L)
        if (inputIndex >= 0) {
          val input = c.getInputBuffer(inputIndex)
          if (input != null) {
            input.clear()
            val size = ex.readSampleData(input, 0)
            if (size < 0) {
              c.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              isInputEos = true
            } else {
              val pts = ex.sampleTime.coerceAtLeast(0L)
              c.queueInputBuffer(inputIndex, 0, size, pts, 0)
              ex.advance()
            }
          }
        }
      }

      val outIndex = c.dequeueOutputBuffer(info, 2_000L)
      if (outIndex >= 0) {
        outputPts = info.presentationTimeUs.coerceAtLeast(0L)
        // Release buffer directly to Surface (hardware GPU zero-copy)
        c.releaseOutputBuffer(outIndex, true)
        if (outputPts >= targetUs || frameReady.get() || isInputEos) {
          output = true
        }
        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
          isInputEos = true
          break
        }
      } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && isInputEos) {
        // No more frames available from decoder
        break
      }
    }
    return FramePacket(0, 0, outputPts, textureId, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, FloatArray(16), width, height, clip.id)
  }

  fun updateOnGl(): FramePacket {
    val st = surfaceTexture ?: return defaultPacket(0L)
    try {
      st.updateTexImage()
    } catch (e: Exception) {
      Log.w(tag, "updateTexImage caught: ${e.message}")
    }
    val matrix = FloatArray(16)
    st.getTransformMatrix(matrix)
    val pts = st.timestamp.coerceAtLeast(0L)
    frameReady.set(false)
    return FramePacket(0, 0, pts, textureId, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, matrix, width, height, clip.id)
  }

  private fun defaultPacket(ptsUs: Long) = FramePacket(
    0, 0, ptsUs, textureId, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, FloatArray(16), width, height, clip.id
  )

  fun release() {
    try { codec?.stop() } catch (_: Throwable) {}
    try { codec?.release() } catch (_: Throwable) {}
    try { extractor?.release() } catch (_: Throwable) {}
    try { surface?.release() } catch (_: Throwable) {}
    try { surfaceTexture?.release() } catch (_: Throwable) {}
    codec = null
    extractor = null
    surface = null
    surfaceTexture = null
    if (textureId != 0) {
      glHandler.post {
        GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        textureId = 0
      }
    }
  }
}

/**
 * Professional asynchronous hardware video export pipeline.
 * Features:
 * - MediaCodec Decoder -> Surface/SurfaceTexture/OES -> OpenGL ES Composition -> EGL Surface -> MediaCodec Encoder -> MediaMuxer -> MP4
 * - Full multi-track PCM audio mixing and AAC hardware encoding with zero drift.
 * - Hardware and image clip fallbacks.
 * - Multi-layer text, stickers, VFX, transitions, filters, and color adjustments.
 */
class AsyncFramePipelineEngine(private val context: Context) {
  private val tag = "AsyncFramePipeline"
  private val cancelled = AtomicBoolean(false)
  private val glThread = HandlerThread("AH-GPU-FramePipeline").apply { start() }
  private val glHandler = Handler(glThread.looper)
  private val composition = VideoCompositionEngine(context)
  private val audioProcessor = AudioExportProcessor(context)
  val metrics = AsyncFramePipelineMetrics()

  fun cancel() {
    cancelled.set(true)
  }

  suspend fun export(timeline: Timeline, config: ExportConfig, outputFile: File): File? = withContext(Dispatchers.IO) {
    cancelled.set(false)
    if (timeline.videoClips.isEmpty() && timeline.overlayClips.isEmpty()) return@withContext null
    val (width, height) = dimensions(config.resolution, timeline.aspectRatio)
    val fps = config.frameRate.fps.coerceAtLeast(1)
    val durationMs = timeline.totalDurationMs.coerceAtLeast(1L)
    val totalFrames = ceil(durationMs * fps / 1000.0).toLong().coerceAtLeast(1L)
    val mime = selectEncoder(config, width, height, fps) ?: return@withContext null

    var videoEncoder: MediaCodec? = null
    var audioEncoder: MediaCodec? = null
    var inputSurface: Surface? = null
    var egl: EglCore? = null
    var window: WindowSurface? = null
    var renderer: GpuCompositionRenderer? = null
    var muxer: MediaMuxer? = null
    val isMuxStarted = AtomicBoolean(false)
    val videoTrack = AtomicInteger(-1)
    val audioTrack = AtomicInteger(-1)
    val videoEos = AtomicBoolean(false)
    val audioEos = AtomicBoolean(false)
    val failure = AtomicReference<Throwable?>(null)
    val decoders = LinkedHashMap<String, SurfaceDecoder>()
    val imageBitmaps = HashMap<String, Bitmap>()
    val imageTextures = HashMap<String, Int>()

    val hasAudioSources = audioProcessor.hasActiveAudio(timeline)
    var masterPcm = ShortArray(0)
    var hasAudio = false
    val audioSampleRate = audioProcessor.sampleRate
    val audioChannels = audioProcessor.channelCount

    val firstVideoPts = AtomicLong(-1L)
    val lastVideoPts = AtomicLong(-1L)
    val firstAudioPts = AtomicLong(-1L)
    val lastAudioPts = AtomicLong(-1L)
    val audioSamplesProcessed = AtomicLong(0L)

    Log.i(tag, "[EXPORT_START] timelineDuration=${durationMs}ms, totalFrames=$totalFrames, target=${outputFile.name}")
    Log.i(tag, "[RESOLUTION] requested=${width}x${height}")
    Log.i(tag, "[FPS] requested=$fps")
    Log.i(tag, "[BITRATE] bitrate=${bitrate(config)}")
    Log.i(tag, "[AUDIO_TRACKS] count=${timeline.audioClips.size + (if (timeline.videoClips.any { it.isVideo && it.hasAudio && !it.isMuted }) 1 else 0)}")

    try {
      // 1. Mix multi-track Audio
      if (hasAudioSources) {
        try {
          masterPcm = audioProcessor.mixTimelineAudio(timeline, durationMs) { cancelled.get() }
          hasAudio = masterPcm.isNotEmpty()
        } catch (e: Exception) {
          Log.w(tag, "Audio mix error in async pipeline: ${e.message}", e)
          hasAudio = false
        }
      }

      // 2. Preload Bitmaps for image clips
      for (clip in timeline.videoClips + timeline.overlayClips) {
        if (!clip.isVideo && clip.uri.isNotBlank()) {
          try {
            val uri = Uri.parse(clip.uri)
            context.contentResolver.openInputStream(uri)?.use { stream ->
              val bmp = BitmapFactory.decodeStream(stream)
              if (bmp != null) imageBitmaps[clip.uri] = bmp
            }
          } catch (e: Exception) {
            Log.w(tag, "Failed to load image clip: ${clip.name}", e)
          }
        }
      }

      // 3. Configure Video Encoder with Surface Input
      videoEncoder = MediaCodec.createEncoderByType(mime)
      val encInfo = videoEncoder.codecInfo
      Log.i(tag, "[SELECTED_ENCODER] name=${encInfo.name} mime=$mime")
      Log.i(tag, "[HARDWARE_OR_SOFTWARE] isHardware=${hardware(encInfo)} surfaceFormat=COLOR_FormatSurface")
      val videoFormat = MediaFormat.createVideoFormat(mime, width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, bitrate(config))
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
      }
      videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      inputSurface = videoEncoder.createInputSurface()

      // 4. Initialize GL context and GPU renderer entirely on the GL HandlerThread
      val glInitLatch = CountDownLatch(1)
      glHandler.post {
        try {
          val core = EglCore(null, EglCore.FLAG_RECORDABLE)
          val winSurface = WindowSurface(core, inputSurface, false)
          winSurface.makeCurrent()
          val rend = GpuCompositionRenderer(context).also { it.initGl() }

          // Preload image textures on GL thread
          for ((uri, bmp) in imageBitmaps) {
            val texId = rend.uploadImageTexture(uri, bmp)
            imageTextures[uri] = texId
          }

          egl = core
          window = winSurface
          renderer = rend
          Log.i(tag, "[GPU_COMPOSITION_STATUS] initialized=true, nativeBridgeLoaded=${com.example.engine.composition.gpu.NativeRenderBridge.isLoaded}")
        } catch (t: Throwable) {
          failure.set(t)
        } finally {
          glInitLatch.countDown()
        }
      }
      check(glInitLatch.await(5, TimeUnit.SECONDS)) { "Timed out initializing GL Context" }
      failure.get()?.let { throw it }

      // 5. Configure Audio Encoder if Audio is Present
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

      muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

      // Initialize surface decoders for video clips
      for (clip in timeline.videoClips + timeline.overlayClips) {
        if (clip.isVideo && clip.uri.isNotBlank() && MediaRelinkManager.isRealPlayableMedia(context, clip.uri)) {
          try {
            SurfaceDecoder(context, clip, glHandler).also {
              it.start()
              decoders[clip.id] = it
              Log.i(tag, "[SELECTED_DECODER] clip=${clip.id} name=${clip.name}")
            }
          } catch (e: Exception) {
            Log.w(tag, "Hardware decoder unavailable for clip ${clip.id}", e)
          }
        }
      }

      videoEncoder.start()

      // Request sync frame on frame 0
      try {
        val syncParams = Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }
        videoEncoder.setParameters(syncParams)
      } catch (_: Exception) {}

      val drainDone = CountDownLatch(1)
      val drainExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "AH-Encode-Drain") }
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
                  videoTrack.set(muxer.addTrack(videoEncoder.outputFormat))
                  if (videoTrack.get() >= 0 && (!hasAudio || audioTrack.get() >= 0) && !isMuxStarted.get()) {
                    muxer.start()
                    isMuxStarted.set(true)
                    Log.i(tag, "[MUXER_START] videoTrack=${videoTrack.get()} audioTrack=${audioTrack.get()}")
                  }
                }
                vIndex >= 0 -> {
                  val out = videoEncoder.getOutputBuffer(vIndex)
                  if (out != null && vInfo.size > 0 && isMuxStarted.get() && (vInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    val pts = vInfo.presentationTimeUs.coerceAtLeast(0L)
                    if (firstVideoPts.get() < 0L) firstVideoPts.set(pts)
                    lastVideoPts.set(pts)
                    synchronized(muxer) {
                      muxer.writeSampleData(videoTrack.get(), out, MediaCodec.BufferInfo().apply {
                        set(vInfo.offset, vInfo.size, pts, vInfo.flags)
                      })
                    }
                    val encoded = metrics.encodedFrames.incrementAndGet()
                    if (encoded % 60 == 0L || encoded == totalFrames) {
                      Log.i(tag, "[VIDEO_FRAMES_PROCESSED] $encoded / $totalFrames (lastPts=${pts}us)")
                    }
                  }
                  val end = (vInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                  videoEncoder.releaseOutputBuffer(vIndex, false)
                  if (end) videoEos.set(true)
                }
              }
            }

            // Drain Audio
            if (hasAudio && audioEncoder != null && !audioEos.get()) {
              val aIndex = audioEncoder.dequeueOutputBuffer(aInfo, 2_000L)
              when {
                aIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                  audioTrack.set(muxer.addTrack(audioEncoder.outputFormat))
                  if (videoTrack.get() >= 0 && audioTrack.get() >= 0 && !isMuxStarted.get()) {
                    muxer.start()
                    isMuxStarted.set(true)
                    Log.i(tag, "[MUXER_START] videoTrack=${videoTrack.get()} audioTrack=${audioTrack.get()}")
                  }
                }
                aIndex >= 0 -> {
                  val out = audioEncoder.getOutputBuffer(aIndex)
                  if (out != null && aInfo.size > 0 && isMuxStarted.get() && (aInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    val pts = aInfo.presentationTimeUs.coerceAtLeast(0L)
                    if (firstAudioPts.get() < 0L) firstAudioPts.set(pts)
                    lastAudioPts.set(pts)
                    synchronized(muxer) {
                      muxer.writeSampleData(audioTrack.get(), out, MediaCodec.BufferInfo().apply {
                        set(aInfo.offset, aInfo.size, pts, aInfo.flags)
                      })
                    }
                    val samples = audioSamplesProcessed.addAndGet((aInfo.size / (audioChannels * 2)).toLong())
                    if (samples % (audioSampleRate * 2) < (aInfo.size / (audioChannels * 2))) {
                      Log.i(tag, "[AUDIO_SAMPLES_PROCESSED] $samples samples (lastPts=${pts}us)")
                    }
                  }
                  val end = (aInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                  audioEncoder.releaseOutputBuffer(aIndex, false)
                  if (end) audioEos.set(true)
                }
              }
            }
          }
        } catch (t: Throwable) {
          failure.set(t)
          cancelled.set(true)
        } finally {
          drainDone.countDown()
        }
      }

      val totalAudioFrames = if (hasAudio) masterPcm.size / audioChannels else 0
      var fedAudioFrames = 0
      val renderCompleteLatch = CountDownLatch(1)

      // Execute entire frame rendering loop directly on the dedicated GL thread
      glHandler.post {
        try {
          for (frameIndex in 0 until totalFrames) {
            if (cancelled.get()) break

            val ptsUs = frameIndex * 1_000_000L / fps
            val timelinePosMs = (ptsUs / 1000L).coerceAtMost(durationMs - 1L)
            val frame = composition.evaluateFrame(timeline, timelinePosMs)
            val activeClip = frame.activeClip

            var mainTexId = 0
            var isMainOes = false
            var mainTexMatrix: FloatArray? = null

            // 1. Decode & bind main video/image
            val mainDecoder = activeClip?.let { decoders[it.id] }
            if (mainDecoder != null) {
              val decodeStart = System.nanoTime()
              mainDecoder.decode(frame.clipSourcePosMs * 1000L, cancelled)
              val main = mainDecoder.updateOnGl()
              metrics.decodeTimeNs.addAndGet(System.nanoTime() - decodeStart)
              metrics.decodedFrames.incrementAndGet()
              metrics.zeroCopyFrames.incrementAndGet()
              mainTexId = main.textureId
              isMainOes = true
              mainTexMatrix = main.transformMatrix
            } else if (activeClip != null) {
              mainTexId = imageTextures[activeClip.uri] ?: 0
              isMainOes = false
            }

            // 2. Decode & bind overlays
            val overlayTextures = HashMap<String, Int>()
            for (overlay in frame.activeOverlays) {
              val ovDecoder = decoders[overlay.clip.id]
              if (ovDecoder != null) {
                ovDecoder.decode(overlay.sourcePosMs * 1000L, cancelled)
                val ovPacket = ovDecoder.updateOnGl()
                overlayTextures[overlay.clip.id] = ovPacket.textureId
              } else {
                val texId = imageTextures[overlay.clip.uri]
                if (texId != null && texId > 0) {
                  overlayTextures[overlay.clip.id] = texId
                }
              }
            }

            // 3. Render hardware composition directly to encoder input surface
            val startRender = System.nanoTime()
            renderer?.render(
              frame = frame,
              mainTextureId = mainTexId,
              isMainOes = isMainOes,
              mainTexMatrix = mainTexMatrix,
              overlayTextures = overlayTextures,
              viewportWidth = width,
              viewportHeight = height,
              timelineAdjustments = timeline.adjustments,
              timelineFilter = timeline.filter,
              chromaKey = timeline.chromaKey
            )
            GLES20.glFlush()
            window?.setPresentationTime(ptsUs * 1000L)
            if (window?.swapBuffers() != true) {
              throw IllegalStateException("Encoder EGL swap failed at frame $frameIndex")
            }
            metrics.gpuRenderTimeNs.addAndGet(System.nanoTime() - startRender)
            metrics.gpuFrames.incrementAndGet()

            // 4. Feed Audio Pro-rata with Microsecond Precision
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
                    val byteBuf = ByteBuffer.allocate(framesToFeed * audioChannels * 2).order(ByteOrder.LITTLE_ENDIAN)
                    val startIdx = fedAudioFrames * audioChannels
                    val endIdx = (fedAudioFrames + framesToFeed) * audioChannels
                    for (k in startIdx until endIdx) {
                      if (k < masterPcm.size) byteBuf.putShort(masterPcm[k]) else byteBuf.putShort(0)
                    }
                    byteBuf.flip()
                    inputBuffer.put(byteBuf)
                    val audioPtsUs = (fedAudioFrames.toLong() * 1_000_000L) / audioSampleRate
                    audioEncoder.queueInputBuffer(inputIndex, 0, framesToFeed * audioChannels * 2, audioPtsUs, 0)
                    fedAudioFrames += framesToFeed
                  }
                } else break
              }
            }
          }
        } catch (t: Throwable) {
          failure.set(t)
          cancelled.set(true)
        } finally {
          renderCompleteLatch.countDown()
        }
      }

      renderCompleteLatch.await()
      failure.get()?.let { throw it }

      // 6. Signal EOS to Video and Audio Encoders
      if (!cancelled.get()) {
        videoEncoder.signalEndOfInputStream()
        if (hasAudio && audioEncoder != null) {
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

      drainDone.await(60, TimeUnit.SECONDS)
      drainExecutor.shutdown()

      Log.i(tag, "[MUXER_STOP] totalEncodedFrames=${metrics.encodedFrames.get()} audioSamples=${audioSamplesProcessed.get()}")
      Log.i(tag, "[FIRST_VIDEO_PTS] ${firstVideoPts.get()}us [LAST_VIDEO_PTS] ${lastVideoPts.get()}us")
      if (hasAudio) {
        Log.i(tag, "[FIRST_AUDIO_PTS] ${firstAudioPts.get()}us [LAST_AUDIO_PTS] ${lastAudioPts.get()}us")
      }
      failure.get()?.let { throw it }
      if (cancelled.get() || !isMuxStarted.get()) {
        outputFile.delete()
        return@withContext null
      }
      outputFile.takeIf { it.exists() && it.length() > 0L }
    } catch (t: Throwable) {
      Log.e(tag, "Async hardware export pipeline error", t)
      outputFile.delete()
      null
    } finally {
      cancelled.set(true)
      decoders.values.forEach { runCatching { it.release() } }
      val cleanLatch = CountDownLatch(1)
      glHandler.post {
        runCatching { renderer?.release() }
        runCatching { window?.release() }
        runCatching { egl?.release() }
        cleanLatch.countDown()
      }
      cleanLatch.await(2, TimeUnit.SECONDS)
      runCatching { inputSurface?.release() }
      runCatching { videoEncoder?.stop() }; runCatching { videoEncoder?.release() }
      runCatching { audioEncoder?.stop() }; runCatching { audioEncoder?.release() }
      if (isMuxStarted.get()) runCatching { muxer?.stop() }
      runCatching { muxer?.release() }
    }
  }

  private fun selectEncoder(config: ExportConfig, w: Int, h: Int, fps: Int): String? {
    val mimes = when (config.codecProfile) {
      CodecProfile.H265_HEVC -> listOf(MediaFormat.MIMETYPE_VIDEO_HEVC)
      CodecProfile.H264_AVC -> listOf(MediaFormat.MIMETYPE_VIDEO_AVC)
      CodecProfile.AUTO -> listOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC)
    }
    for (mime in mimes) {
      for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
        if (!info.isEncoder || !hardware(info) || !info.supportedTypes.any { it.equals(mime, true) }) continue
        val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: continue
        val vc = caps.videoCapabilities ?: continue
        val sizeOk = runCatching { vc.isSizeSupported(w, h) }.getOrDefault(true)
        val formatOk = caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        if (formatOk && sizeOk) return mime
      }
    }
    // Universal fallback
    return MediaFormat.MIMETYPE_VIDEO_AVC
  }

  private fun hardware(info: MediaCodecInfo) = if (android.os.Build.VERSION.SDK_INT >= 29) {
    info.isHardwareAccelerated
  } else {
    val n = info.name.lowercase()
    !n.startsWith("omx.google.") && !n.startsWith("c2.android.") && !n.contains("software")
  }

  private fun bitrate(config: ExportConfig) = if (config.quality == ExportQuality.CUSTOM && config.customBitrateKbps > 0) {
    config.customBitrateKbps * 1000
  } else {
    when (config.resolution) {
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
    return when (resolution) {
      Resolution.RES_480P -> if (vertical) 480 to 854 else 854 to 480
      Resolution.RES_720P -> if (vertical) 720 to 1280 else 1280 to 720
      Resolution.RES_1080P -> if (vertical) 1080 to 1920 else 1920 to 1080
      Resolution.RES_2K -> if (vertical) 1440 to 2560 else 2560 to 1440
      Resolution.RES_VERTICAL_2K -> 1440 to 2560
      Resolution.RES_4K -> if (vertical) 2160 to 3840 else 3840 to 2160
      Resolution.RES_VERTICAL_4K -> 2160 to 3840
      Resolution.RES_SQUARE_2K -> 2048 to 2048
    }
  }
}
