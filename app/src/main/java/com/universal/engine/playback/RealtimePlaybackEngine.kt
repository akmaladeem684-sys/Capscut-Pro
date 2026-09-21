package com.universal.engine.playback

import android.opengl.GLES30
import com.universal.engine.cache.FrameCache
import com.universal.engine.compositing.Compositor
import com.universal.engine.decoder.VideoDecoderAdapter
import com.universal.engine.errors.EngineError
import com.universal.engine.frame.FrameMetadata
import com.universal.engine.frame.GpuFrame
import com.universal.engine.rendergraph.Blit
import com.universal.engine.rendergraph.RenderContext
import com.universal.engine.timeline.Timeline
import com.universal.engine.timeline.TimelineEvaluator
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class RealtimePlaybackEngine(
    private val ctx: RenderContext,
    private val timeline: Timeline,
    private val displaySurface: com.universal.engine.egl.EglSurface
) {

    private val clock = MediaClock(timeline.durationUs)
    private val evaluators = TimelineEvaluator()
    private val renderer2d = com.universal.engine.renderer2d.Renderer2D(ctx)
    private val compositor = Compositor(ctx, renderer2d)
    private val frameCache = FrameCache(maxEntries = 24)

    private val renderThread = AtomicReference<Thread?>(null)
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val seekRequestUs = AtomicLong(-1L)

    private val decoders = LinkedHashMap<String, VideoDecoderAdapter>()
    private val pendingSeek = AtomicBoolean(false)
    var onError: ((EngineError) -> Unit)? = null
    var quality: RenderContext.RenderQuality
        get() = ctx.quality
        set(value) { ctx.quality = value; frameCache.invalidateAll() }

    private inner class DecoderFrameProvider : TimelineEvaluator.FrameProvider {
        override fun frameAt(mediaId: String, sourceTimestampUs: Long): GpuFrame? {
            val dec = decoders[mediaId] ?: return null
            if (pendingSeek.getAndSet(false)) {
                dec.seekTo(sourceTimestampUs, accurate = true)
                ctx.temporalBuffers.reset()
                frameCache.invalidateAll()
            } else if (!dec.hasPendingFrame() && nearestFramePts.get() != sourceTimestampUs) {
                dec.seekTo(sourceTimestampUs, accurate = false)
            }
            val consumed = dec.consumeNewestFrame() ?: run {
                ctx.diagnostics.frameDropped("no decoded frame at $sourceTimestampUs")
                return null
            }
            val meta = FrameMetadata(
                width = dec.width, height = dec.height,
                timestampUs = consumed.first, durationUs = timeline.frameDurationUs,
                frameIndex = -1L,
                pixelFormat = FrameMetadata.PixelFormat.TEXTURE_EXTERNAL_OES,
                colorInfo = com.universal.engine.frame.ColorInfo(),
                rotationDegrees = dec.rotationDegrees
            )
            nearestFramePts.set(consumed.first)
            return GpuFrame.fromExternalTexture(dec.externalTextureId, meta) { }
        }
        override fun release() {}
    }

    private val nearestFramePts = AtomicLong(-1L)
    private val providers = HashMap<String, TimelineEvaluator.FrameProvider>()

    fun addMediaSource(mediaId: String, dataSource: String) {
        check(!running.get()) { "add media sources before play()" }
        val dec = VideoDecoderAdapter(dataSource, ctx.textures) { }
        dec.initialize()
        decoders[mediaId] = dec
        providers[mediaId] = DecoderFrameProvider()
    }

    fun start() {
        if (running.getAndSet(true)) return
        ctx.bindToCurrentThread()
        ctx.egl.makeCurrent(displaySurface)
        ctx.setViewport(timeline.canvasWidth, timeline.canvasHeight)
        Thread({
            renderLoop()
        }, "ue-render").also { renderThread.set(it); it.start() }
    }

    fun play() { clock.play(); paused.set(false) }
    fun pause() { clock.pause(); paused.set(true) }
    fun seekTo(timestampUs: Long) {
        seekRequestUs.set(timestampUs.coerceIn(0, timeline.durationUs))
        pendingSeek.set(true)
    }
    fun stepFrames(count: Int) {
        clock.stepFrames(timeline.frameDurationUs, count)
        pendingSeek.set(true)
        seekRequestUs.set(clock.currentPositionUs())
    }

    private fun renderLoop() {
        try {
            while (running.get()) {
                val seekUs = seekRequestUs.getAndSet(-1L)
                if (seekUs >= 0) { clock.seekTo(seekUs); nearestFramePts.set(-1) }

                if (paused.get() || clock.state !is MediaClock.State.Playing) {
                    renderAt(clock.currentPositionUs())
                    TimeUnit.MILLISECONDS.sleep(16)
                    continue
                }

                val targetPts = nextFramePts(clock.currentPositionUs())
                val waitNanos = clock.realNanosUntil(targetPts)
                if (waitNanos > 0) {
                    var remaining = waitNanos
                    while (remaining > 2_000_000L && running.get()) {
                        TimeUnit.MILLISECONDS.sleep(remaining / 2_000_000L)
                        remaining = clock.realNanosUntil(targetPts)
                    }
                }
                if (!running.get()) break
                renderAt(targetPts)
            }
        } catch (e: EngineError) {
            onError?.invoke(e)
        } finally {
            running.set(false)
        }
    }

    private fun nextFramePts(currentPts: Long): Long =
        ((currentPts / timeline.frameDurationUs) + 1) * timeline.frameDurationUs

    private fun renderAt(ptsUs: Long) {
        ctx.currentTimestampUs = ptsUs
        val layers = evaluators.evaluate(timeline, ptsUs, providers, timeline.canvasWidth, timeline.canvasHeight)
        val target = ctx.framebuffers.acquire(timeline.canvasWidth, timeline.canvasHeight)
        compositor.composite(layers, target, timeline.backgroundR, timeline.backgroundG, timeline.backgroundB)
        present(target, ptsUs)
    }

    private fun present(target: com.universal.engine.gl.RenderTarget, ptsUs: Long) {
        ctx.egl.makeCurrent(displaySurface)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, timeline.canvasWidth, timeline.canvasHeight)
        Blit.draw(ctx, target.colorTextureId, target.width, target.height)
        ctx.egl.swapBuffers(displaySurface)
        ctx.diagnostics.renderFps.tick()
        target.recycleSelf()
    }

    fun stop() {
        running.set(false)
        renderThread.get()?.join(2000)
        decoders.values.forEach { it.release() }
        decoders.clear()
        providers.clear()
        frameCache.invalidateAll()
    }
}
