package com.universal.engine.export

import com.universal.engine.audio.AudioPipeline
import com.universal.engine.compositing.Compositor
import com.universal.engine.device.HardwareCapabilities
import com.universal.engine.egl.EglManager
import com.universal.engine.encoder.VideoEncoderAdapter
import com.universal.engine.errors.EngineError
import com.universal.engine.errors.EngineResult
import com.universal.engine.rendergraph.Blit
import com.universal.engine.rendergraph.RenderContext
import com.universal.engine.renderer2d.Renderer2D
import com.universal.engine.timeline.Timeline
import com.universal.engine.timeline.TimelineEvaluator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hardware export engine.
 * Shares the exact same TimelineEvaluator + Compositor + RenderContext pipeline as preview.
 */
class ExportEngine(
    private val caps: HardwareCapabilities,
    private val renderContext: RenderContext,
    private val egl: EglManager
) {

    data class Request(
        val timeline: Timeline,
        val outputPath: String,
        val width: Int,
        val height: Int,
        val bitrateMbps: Float = defaultBitrateMbps(width, height),
        val codec: HardwareCapabilities.VideoCodec = HardwareCapabilities.VideoCodec.HEVC,
        val quality: RenderContext.RenderQuality = RenderContext.RenderQuality.EXPORT_HIGH,
        val speed: Float = 1f,
        val audioSources: List<String> = emptyList()
    ) {
        companion object {
            fun defaultBitrateMbps(w: Int, h: Int): Float = when {
                w * h >= 3840 * 2160 -> 45f
                w * h >= 2560 * 1440 -> 24f
                w * h >= 1920 * 1080 -> 12f
                else -> 6f
            }
        }
    }

    private val cancelled = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val pauseLatch = Semaphore(1)
    private val done = CountDownLatch(1)

    var onComplete: ((EngineResult<Unit>) -> Unit)? = null
    var progress: ExportProgress? = null; private set

    fun start(request: Request, providers: Map<String, TimelineEvaluator.FrameProvider>) {
        cancelled.set(false)
        Thread({
            val result: EngineResult<Unit> = try {
                runExport(request, providers)
                EngineResult.ok(Unit)
            } catch (e: EngineError) {
                EngineResult.err(e)
            } catch (t: Throwable) {
                EngineResult.err(EngineError.RenderError("export crashed: ${t.message}", t, EngineError.Recovery.ABORT))
            }
            done.countDown()
            onComplete?.invoke(result)
        }, "ue-export").start()
    }

    fun cancel() {
        cancelled.set(true)
        if (pauseLatch.availablePermits() == 0) pauseLatch.release()
    }

    fun pauseExport() {
        if (paused.compareAndSet(false, true)) {
            pauseLatch.drainPermits()
        }
    }

    fun resumeExport() {
        if (paused.compareAndSet(true, false)) {
            if (pauseLatch.availablePermits() == 0) pauseLatch.release()
        }
    }

    fun awaitCompletion(timeoutSeconds: Long): EngineResult<Unit>? =
        if (done.await(timeoutSeconds, TimeUnit.SECONDS)) EngineResult.ok(Unit) else null

    private fun runExport(request: Request, providers: Map<String, TimelineEvaluator.FrameProvider>) {
        val timeline = request.timeline
        val totalFrames = (timeline.durationUs * request.speed).toLong().let { d ->
            (d + timeline.frameDurationUs - 1) / timeline.frameDurationUs
        }
        val progress = ExportProgress(totalFrames, timeline.durationUs).also { this.progress = it }
        progress.stage.set(ExportProgress.Stage.PREPARING)

        renderContext.bindToCurrentThread()
        renderContext.quality = request.quality
        egl.makeCurrentOffscreen(request.width, request.height)
        renderContext.setViewport(request.width, request.height)

        val encoder = VideoEncoderAdapter(caps)
        val renderer2d = Renderer2D(renderContext)
        val compositor = Compositor(renderContext, renderer2d)
        val evaluator = TimelineEvaluator()
        val muxerAudioIndex = intArrayOf(-1)
        val audioPipelines = mutableListOf<AudioPipeline>()

        try {
            encoder.configure(request.outputPath, VideoEncoderAdapter.Config(
                width = request.width, height = request.height,
                frameRate = timeline.outputFrameRate,
                bitrateMbps = request.bitrateMbps,
                requestedCodec = request.codec
            ))

            progress.stage.set(ExportProgress.Stage.AUDIO)
            for (audioPath in request.audioSources) {
                val ap = AudioPipeline(audioPath)
                val fmt = ap.selectTrack()
                if (fmt != null) {
                    muxerAudioIndex[0] = encoder.registerAudioTrack(fmt)
                    audioPipelines.add(ap)
                } else {
                    ap.release()
                }
            }

            progress.stage.set(ExportProgress.Stage.RENDERING)
            val encoderSurface = egl.createWindowSurface(encoder.inputSurfaceHandle)
            egl.makeCurrent(encoderSurface)

            var frameIndex = 0L
            while (frameIndex < totalFrames && !cancelled.get()) {
                while (paused.get() && !cancelled.get()) {
                    TimeUnit.MILLISECONDS.sleep(50)
                }
                if (cancelled.get()) break

                val ptsUs = timeline.timestampForFrame(frameIndex)
                renderContext.currentTimestampUs = ptsUs
                renderContext.currentFrameIndex = frameIndex

                val layers = evaluator.evaluate(timeline, ptsUs, providers, timeline.canvasWidth, timeline.canvasHeight)
                val target = renderContext.framebuffers.acquire(request.width, request.height)
                compositor.composite(layers, target, timeline.backgroundR, timeline.backgroundG, timeline.backgroundB)

                egl.makeCurrent(encoderSurface)
                egl.setPresentationTime(encoderSurface, ptsUs * 1000L)
                Blit.draw(renderContext, target.colorTextureId, target.width, target.height)
                egl.swapBuffers(encoderSurface)
                target.recycleSelf()

                progress.stage.set(ExportProgress.Stage.ENCODING)
                progress.markEncodeStart()
                encoder.drain()
                progress.encodedFrames.incrementAndGet()
                progress.currentFrame.set(frameIndex + 1)
                progress.currentTimestampUs.set(ptsUs)
                renderContext.diagnostics.encoderFps.tick()
                renderContext.diagnostics.exportProgressPercent.set(progress.percent.toLong())
                frameIndex++
            }

            if (muxerAudioIndex[0] >= 0 && !cancelled.get()) {
                progress.stage.set(ExportProgress.Stage.AUDIO)
                audioPipelines.firstOrNull()?.passthroughTo(
                    muxerWriter = encoder::writeAudioSamples,
                    muxerTrackIndex = muxerAudioIndex[0],
                    timelineOffsetUs = 0L,
                    speed = request.speed,
                    cancelled = { cancelled.get() }
                )
            }

            if (!cancelled.get()) {
                progress.stage.set(ExportProgress.Stage.FINALIZING)
                encoder.signalEndOfStream()
                progress.stage.set(ExportProgress.Stage.DONE)
            } else {
                progress.stage.set(ExportProgress.Stage.CANCELLED)
                throw EngineError.CancelledError()
            }
        } finally {
            audioPipelines.forEach { it.release() }
            runCatching { encoder.release() }
            runCatching { egl.makeCurrentOffscreen(request.width, request.height) }
        }
    }
}
