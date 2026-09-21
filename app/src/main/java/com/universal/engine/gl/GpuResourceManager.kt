package com.universal.engine.gl

import android.opengl.GLES30
import java.util.concurrent.ConcurrentHashMap

/**
 * Ownership + refcount + leak accounting for every GL object the engine creates.
 * All GL object ids flow through here so `releaseAll()` after context loss or shutdown
 * is complete and deterministic.
 */
class GpuResourceManager(
    private val memoryBudget: com.universal.engine.memory.MemoryBudgetManager
) : com.universal.engine.memory.MemoryBudgetManager.Evictable {

    sealed class Resource(val category: String, val bytes: Long) {
        abstract fun deleteGl()
        class Texture(val id: Int, bytes: Long) : Resource(CAT_TEXTURE, bytes) {
            override fun deleteGl() = GLES30.glDeleteTextures(1, intArrayOf(id), 0)
        }
        class Framebuffer(val id: Int, bytes: Long) : Resource(CAT_FBO, bytes) {
            override fun deleteGl() = GLES30.glDeleteFramebuffers(1, intArrayOf(id), 0)
        }
        class Renderbuffer(val id: Int, bytes: Long) : Resource(CAT_FBO, bytes) {
            override fun deleteGl() = GLES30.glDeleteRenderbuffers(1, intArrayOf(id), 0)
        }
        class Buffer(val id: Int, bytes: Long) : Resource(CAT_BUFFER, bytes) {
            override fun deleteGl() = GLES30.glDeleteBuffers(1, intArrayOf(id), 0)
        }
        class VertexArray(val id: Int) : Resource(CAT_BUFFER, 0L) {
            override fun deleteGl() = GLES30.glDeleteVertexArrays(1, intArrayOf(id), 0)
        }
        class ShaderProgram(val id: Int) : Resource(CAT_SHADER, 0L) {
            override fun deleteGl() = GLES30.glDeleteProgram(id)
        }
        companion object {
            const val CAT_TEXTURE = "textures"
            const val CAT_FBO = "framebuffers"
            const val CAT_BUFFER = "buffers"
            const val CAT_SHADER = "shaders"
        }
    }

    private val live = ConcurrentHashMap<Int, Resource>()

    fun register(r: Resource): Resource {
        if (!memoryBudget.allocate(r.category, r.bytes)) {
            r.deleteGl()
            throw com.universal.engine.errors.EngineError.ResourceError(
                "GPU memory budget exceeded (${memoryBudget.totalBytes()} bytes live); refusing allocation of ${r.bytes} bytes")
        }
        live[System.identityHashCode(r)] = r
        return r
    }

    fun unregister(r: Resource) {
        if (live.remove(System.identityHashCode(r)) != null) {
            memoryBudget.release(r.category, r.bytes)
            r.deleteGl()
        }
    }

    fun invalidateAllOnContextLoss() {
        live.values.forEach { memoryBudget.release(it.category, it.bytes) }
        live.clear()
    }

    fun releaseAll() {
        live.values.forEach { memoryBudget.release(it.category, it.bytes); it.deleteGl() }
        live.clear()
    }

    override fun evict(): Long = 0L
}
