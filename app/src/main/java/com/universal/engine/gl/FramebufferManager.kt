package com.universal.engine.gl

import android.opengl.GLES30
import com.universal.engine.diagnostics.RenderDiagnostics
import com.universal.engine.errors.EngineError

class FramebufferManager(
    private val resources: GpuResourceManager,
    val textures: TextureManager,
    private val budget: com.universal.engine.memory.MemoryBudgetManager,
    private val caps: com.universal.engine.device.HardwareCapabilities,
    private val diag: RenderDiagnostics
) : com.universal.engine.memory.MemoryBudgetManager.Evictable {

    private data class Key(val width: Int, val height: Int, val floatFormat: Boolean, val depth: Boolean)
    private val pool = HashMap<Key, MutableList<RenderTarget>>()

    fun acquire(width: Int, height: Int, withDepth: Boolean = false, floatFormat: Boolean = false): RenderTarget {
        requireFloatCapability(floatFormat)
        val key = Key(width, height, floatFormat, withDepth)
        synchronized(pool) {
            val list = pool[key]
            if (!list.isNullOrEmpty()) { val rt = list.removeAt(list.size - 1); rt.inUse = true; return rt }
        }
        return create(width, height, withDepth, floatFormat)
    }

    fun recycle(rt: RenderTarget) {
        rt.inUse = false
        if (budget.isUnderPressure()) { destroy(rt); return }
        val key = Key(rt.width, rt.height, rt.colorTexture.internalFormat == GLES30.GL_RGBA16F, rt.hasDepth)
        synchronized(pool) {
            pool.getOrPut(key) { mutableListOf() }.add(rt)
            if (pool.getValue(key).size > MAX_POOL_PER_KEY) destroy(pool.getValue(key).removeAt(0))
        }
    }

    private fun create(width: Int, height: Int, depth: Boolean, floatFormat: Boolean): RenderTarget {
        if (width > caps.maxTextureSize || height > caps.maxTextureSize || width > caps.maxRenderBufferSize || height > caps.maxRenderBufferSize)
            throw EngineError.UnsupportedFeatureError("render target ${width}x${height}")
        val tex = textures.createTexture2D(width, height, if (floatFormat) GLES30.GL_RGBA16F else GLES30.GL_RGBA8)

        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex.id, 0)

        var rb = 0
        if (depth) {
            val rbb = IntArray(1)
            GLES30.glGenRenderbuffers(1, rbb, 0)
            rb = rbb[0]
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, rb)
            GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, width, height)
            GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, rb)
        }

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            throw EngineError.ResourceError("incomplete FBO ${width}x${height} status=0x${status.toUInt().toString(16)}")
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GlError.check("FramebufferManager.create")

        val bytes = width.toLong() * height * (if (floatFormat) 8L else 4L) + (if (depth) width.toLong() * height * 3L else 0L)
        val fboRes = resources.register(GpuResourceManager.Resource.Framebuffer(fbo[0], 0L)) as GpuResourceManager.Resource.Framebuffer
        diag.framebufferMemoryBytes.addAndGet(bytes)
        val rt = RenderTarget(this, fboRes.id, tex, rb, width, height, depth)
        rt.inUse = true
        return rt
    }

    private fun destroy(rt: RenderTarget) {
        resources.unregister(GpuResourceManager.Resource.Framebuffer(rt.framebufferId, 0L))
        rt.deleteGl()
        diag.framebufferMemoryBytes.addAndGet(-(rt.width.toLong() * rt.height * (if (rt.hasDepth) 11L else 4L)))
    }

    private fun requireFloatCapability(floatFormat: Boolean) {
        if (floatFormat && !caps.supportsFloatFramebuffers && !caps.supportsHalfFloatFramebuffers)
            throw EngineError.UnsupportedFeatureError("float framebuffer")
    }

    fun clearPools() {
        synchronized(pool) { pool.values.flatten().forEach { destroy(it) }; pool.clear() }
    }

    override fun evict(): Long {
        val before = diag.framebufferMemoryBytes.get()
        clearPools()
        return before - diag.framebufferMemoryBytes.get()
    }

    companion object { private const val MAX_POOL_PER_KEY = 6 }
}
