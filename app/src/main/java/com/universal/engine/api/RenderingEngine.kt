package com.universal.engine.api

import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES30
import android.view.Surface
import com.universal.engine.device.HardwareCapabilities
import com.universal.engine.diagnostics.RenderDiagnostics
import com.universal.engine.egl.EglManager
import com.universal.engine.export.ExportEngine
import com.universal.engine.playback.RealtimePlaybackEngine
import com.universal.engine.rendergraph.RenderContext
import com.universal.engine.timeline.Timeline
import com.universal.engine.timeline.TimelineEvaluator
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Single entry point assembling the engine. */
class RenderingEngine {

    val diagnostics = RenderDiagnostics()
    val egl = EglManager()
    val capabilities: HardwareCapabilities by lazy { HardwareCapabilities.detect() }
    val renderContext: RenderContext by lazy {
        RenderContext(
            egl, capabilities, diagnostics,
            budgetSoftLimitBytes = if (capabilities.maxTextureSize >= 8192) 768L * 1048576 else 384L * 1048576,
            budgetHardLimitBytes = if (capabilities.maxTextureSize >= 8192) 1536L * 1048576 else 768L * 1048576
        )
    }

    private var playback: RealtimePlaybackEngine? = null
    private var exportEngine: ExportEngine? = null

    fun createPlayback(timeline: Timeline, previewSurface: Surface): RealtimePlaybackEngine {
        check(playback == null) { "releasePlayback() first" }
        egl.initialize()
        egl.makeCurrentOffscreen(timeline.canvasWidth, timeline.canvasHeight)
        renderContext.bindToCurrentThread()
        val s = egl.createWindowSurface(previewSurface)
        return RealtimePlaybackEngine(renderContext, timeline, s).also { playback = it }
    }

    fun releasePlayback() {
        playback?.stop()
        playback = null
    }

    fun createExportEngine(): ExportEngine =
        ExportEngine(capabilities, renderContext, egl).also { exportEngine = it }

    fun extractFrame(timeline: Timeline, timestampUs: Long, providers: Map<String, TimelineEvaluator.FrameProvider>): Bitmap? {
        egl.initialize()
        egl.makeCurrentOffscreen(timeline.canvasWidth, timeline.canvasHeight)
        renderContext.bindToCurrentThread()
        val renderer2d = com.universal.engine.renderer2d.Renderer2D(renderContext)
        val compositor = com.universal.engine.compositing.Compositor(renderContext, renderer2d)
        val layers = TimelineEvaluator().evaluate(timeline, timestampUs, providers, timeline.canvasWidth, timeline.canvasHeight)
        val target = renderContext.framebuffers.acquire(timeline.canvasWidth, timeline.canvasHeight)
        compositor.composite(layers, target, timeline.backgroundR, timeline.backgroundG, timeline.backgroundB)
        val bmp = readTargetToBitmap(target)
        target.recycleSelf()
        return bmp
    }

    fun shutdown() {
        playback?.stop()
        playback = null
        renderContext.release()
    }
}

fun readTargetToBitmap(target: com.universal.engine.gl.RenderTarget): Bitmap {
    val buf = ByteBuffer.allocateDirect(target.width * target.height * 4).order(ByteOrder.nativeOrder())
    GLES30.glReadPixels(0, 0, target.width, target.height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
    val bmp = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
    buf.rewind()
    bmp.copyPixelsFromBuffer(buf)
    val m = Matrix().apply { setScale(1f, -1f); postTranslate(0f, target.height.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, target.width, target.height, m, false)
}
