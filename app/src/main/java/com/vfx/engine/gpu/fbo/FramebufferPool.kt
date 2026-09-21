package com.vfx.engine.gpu.fbo

import com.vfx.engine.gpu.texture.GpuTexture
import com.vfx.engine.gpu.texture.TexturePool
import com.vfx.engine.gpu.texture.TextureSpec
import java.util.ArrayDeque

data class FboKey(
  val width: Int,
  val height: Int,
  val internalFormat: Int = android.opengl.GLES30.GL_RGBA8
)

/**
 * High-performance, zero-allocation Framebuffer Object Pool.
 * Manages scratch FBOs for multi-pass render graphs, recycling resources instantly.
 */
class FramebufferPool(
  private val texturePool: TexturePool = TexturePool(),
  private val maxCapacityPerKey: Int = 8
) {
  private val pool = HashMap<FboKey, ArrayDeque<FramebufferObject>>()
  private val activeFbos = HashSet<FramebufferObject>()

  @Synchronized
  fun acquire(width: Int, height: Int, internalFormat: Int = android.opengl.GLES30.GL_RGBA8): FramebufferObject {
    val key = FboKey(width, height, internalFormat)
    val queue = pool.getOrPut(key) { ArrayDeque() }

    val fbo = if (queue.isNotEmpty()) {
      queue.poll()!!
    } else {
      val spec = TextureSpec(width = width, height = height, internalFormat = internalFormat)
      val texture = texturePool.acquire(spec)
      FramebufferObject(width, height, texture)
    }

    activeFbos.add(fbo)
    return fbo
  }

  @Synchronized
  fun release(fbo: FramebufferObject) {
    if (activeFbos.remove(fbo)) {
      val key = FboKey(fbo.width, fbo.height, fbo.texture.spec.internalFormat)
      val queue = pool.getOrPut(key) { ArrayDeque() }
      if (queue.size < maxCapacityPerKey) {
        queue.offer(fbo)
      } else {
        fbo.release()
      }
    }
  }

  @Synchronized
  fun clear() {
    for (queue in pool.values) {
      while (queue.isNotEmpty()) {
        queue.poll()?.release()
      }
    }
    pool.clear()
    for (active in activeFbos) {
      active.release()
    }
    activeFbos.clear()
    texturePool.clear()
  }
}
