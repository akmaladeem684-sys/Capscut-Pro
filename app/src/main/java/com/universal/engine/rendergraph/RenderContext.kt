package com.universal.engine.rendergraph

import com.universal.engine.device.HardwareCapabilities
import com.universal.engine.diagnostics.RenderDiagnostics
import com.universal.engine.egl.EglManager
import com.universal.engine.gl.FramebufferManager
import com.universal.engine.gl.GpuResourceManager
import com.universal.engine.gl.ShaderManager
import com.universal.engine.gl.TextureManager
import com.universal.engine.memory.MemoryBudgetManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class RenderContext(
    val egl: EglManager,
    val capabilities: HardwareCapabilities,
    val diagnostics: RenderDiagnostics,
    budgetSoftLimitBytes: Long,
    budgetHardLimitBytes: Long
) : EglManager.ContextLostListener {

    val memoryBudget = MemoryBudgetManager(budgetSoftLimitBytes, budgetHardLimitBytes)
    val resources = GpuResourceManager(memoryBudget)
    val textures = TextureManager(resources, memoryBudget, capabilities, diagnostics)
    val framebuffers = FramebufferManager(resources, textures, memoryBudget, capabilities, diagnostics)
    val shaders = ShaderManager(resources)
    val temporalBuffers = TemporalBufferManager(this)

    @Volatile var viewportWidth: Int = 0
    @Volatile var viewportHeight: Int = 0
    @Volatile var quality: RenderQuality = RenderQuality.BALANCED

    @Volatile var currentTimestampUs: Long = 0L
    @Volatile var currentFrameIndex: Long = -1L

    private val renderThreadRef = AtomicReference<Thread?>(null)
    private val alive = AtomicBoolean(true)

    init { egl.addContextLostListener(this) }

    fun bindToCurrentThread() {
        if (!renderThreadRef.compareAndSet(null, Thread.currentThread())) {
            check(renderThreadRef.get() === Thread.currentThread()) {
                "RenderContext used from ${Thread.currentThread().name} but owned by ${renderThreadRef.get()?.name}"
            }
        }
    }

    fun assertOnRenderThread() {
        check(Thread.currentThread() === renderThreadRef.get()) {
            "GPU access from wrong thread: ${Thread.currentThread().name}"
        }
    }

    fun setViewport(w: Int, h: Int) { viewportWidth = w; viewportHeight = h }

    override fun onContextLost() {
        resources.invalidateAllOnContextLoss()
        shaders.invalidateAll()
        diagnostics.droppedFrames.incrementAndGet()
    }

    fun release() {
        if (!alive.getAndSet(false)) return
        assertOnRenderThread()
        temporalBuffers.release()
        framebuffers.clearPools()
        textures.clearPools()
        shaders.releaseAll()
        resources.releaseAll()
        egl.release()
    }

    enum class RenderQuality { DRAFT, BALANCED, HIGH, ULTRA, EXPORT_STANDARD, EXPORT_HIGH, EXPORT_MAXIMUM }

    val effectScale: Float get() = when (quality) {
        RenderQuality.DRAFT -> 0.5f
        RenderQuality.BALANCED -> 0.75f
        else -> 1.0f
    }
    val allowTemporalEffects: Boolean get() = quality != RenderQuality.DRAFT
    val maxBlurRadius: Float get() = when (quality) {
        RenderQuality.DRAFT -> 16f
        RenderQuality.BALANCED -> 48f
        else -> 128f
    }
}
