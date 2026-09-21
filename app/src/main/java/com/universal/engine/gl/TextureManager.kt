package com.universal.engine.gl

import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.universal.engine.diagnostics.RenderDiagnostics
import com.universal.engine.errors.EngineError

/**
 * Texture creation + pooling. Pools are keyed by (target, width, height, internalFormat).
 * Never allocates a texture the device cannot support (max size / float capability checks).
 */
class TextureManager(
    private val resources: GpuResourceManager,
    private val budget: com.universal.engine.memory.MemoryBudgetManager,
    private val caps: com.universal.engine.device.HardwareCapabilities,
    private val diag: RenderDiagnostics
) : com.universal.engine.memory.MemoryBudgetManager.Evictable {

    private data class PoolKey(val target: Int, val width: Int, val height: Int, val internalFormat: Int, val filterLinear: Boolean)
    private val pool = HashMap<PoolKey, MutableList<TextureResource>>()

    fun createTexture2D(width: Int, height: Int, internalFormat: Int = GLES30.GL_RGBA8, linearFilter: Boolean = true, data: java.nio.ByteBuffer? = null): TextureResource {
        validateDims(width, height, internalFormat)
        val id = IntArray(1)
        GLES30.glGenTextures(1, id, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id[0])
        val filter = if (linearFilter) GLES30.GL_LINEAR else GLES30.GL_NEAREST
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
            GLES30.GL_RGBA, if (internalFormat == GLES30.GL_RGBA16F) GLES30.GL_HALF_FLOAT else GLES30.GL_UNSIGNED_BYTE, data)
        GlError.check("TextureManager.createTexture2D ${width}x${height}")
        val bytes = bytesFor(width, height, internalFormat)
        val res = resources.register(GpuResourceManager.Resource.Texture(id[0], bytes)) as GpuResourceManager.Resource.Texture
        diag.textureMemoryBytes.addAndGet(bytes)
        return TextureResource(res, width, height, internalFormat)
    }

    fun createExternalTexture(): ExternalTextureResource {
        val id = IntArray(1)
        GLES30.glGenTextures(1, id, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id[0])
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val res = resources.register(GpuResourceManager.Resource.Texture(id[0], 0L)) as GpuResourceManager.Resource.Texture
        return ExternalTextureResource(res)
    }

    fun acquire(width: Int, height: Int, internalFormat: Int = GLES30.GL_RGBA8, linearFilter: Boolean = true): TextureResource {
        val key = PoolKey(GLES30.GL_TEXTURE_2D, width, height, internalFormat, linearFilter)
        synchronized(pool) {
            val list = pool[key]
            if (!list.isNullOrEmpty()) return list.removeAt(list.size - 1)
        }
        return createTexture2D(width, height, internalFormat, linearFilter)
    }

    fun recycle(t: TextureResource) {
        if (budget.isUnderPressure()) { destroy(t); return }
        val key = PoolKey(GLES30.GL_TEXTURE_2D, t.width, t.height, t.internalFormat, true)
        synchronized(pool) {
            pool.getOrPut(key) { mutableListOf() }.add(t)
            if (pool.getValue(key).size > MAX_POOL_PER_KEY) destroy(pool.getValue(key).removeAt(0))
        }
    }

    fun destroy(t: TextureResource) {
        resources.unregister(t.resource)
        diag.textureMemoryBytes.addAndGet(-t.bytes)
    }

    fun clearPools() {
        synchronized(pool) {
            pool.values.flatten().forEach { destroy(it) }
            pool.clear()
        }
    }

    private fun validateDims(w: Int, h: Int, fmt: Int) {
        if (w <= 0 || h <= 0) throw EngineError.ResourceError("invalid texture dims ${w}x$h")
        if (w > caps.maxTextureSize || h > caps.maxTextureSize)
            throw EngineError.UnsupportedFeatureError("texture ${w}x$h", "max=${caps.maxTextureSize}")
        if (fmt == GLES30.GL_RGBA16F && !caps.supportsFloatFramebuffers)
            throw EngineError.UnsupportedFeatureError("float texture")
    }

    private fun bytesFor(w: Int, h: Int, fmt: Int) = w.toLong() * h * if (fmt == GLES30.GL_RGBA16F) 8L else 4L

    override fun evict(): Long {
        val before = diag.textureMemoryBytes.get()
        clearPools()
        return before - diag.textureMemoryBytes.get()
    }

    companion object { private const val MAX_POOL_PER_KEY = 8 }

    class TextureResource internal constructor(val resource: GpuResourceManager.Resource.Texture, val width: Int, val height: Int, val internalFormat: Int) {
        val id: Int get() = resource.id
        val bytes: Long get() = resource.bytes
        fun bind(unit: Int) { GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id) }
    }
    class ExternalTextureResource internal constructor(val resource: GpuResourceManager.Resource.Texture) {
        val id: Int get() = resource.id
        fun bind(unit: Int) { GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit); GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id) }
    }
}
