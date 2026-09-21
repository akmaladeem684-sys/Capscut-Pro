package com.vfx.engine.gpu.texture

import java.util.ArrayDeque

/**
 * Reusable GPU texture pool to eliminate runtime VRAM allocations during render loops.
 */
class TexturePool(private val maxCapacityPerSpec: Int = 10) {

  private val pool = HashMap<TextureSpec, ArrayDeque<GpuTexture>>()
  private val activeTextures = HashSet<GpuTexture>()

  @Synchronized
  fun acquire(spec: TextureSpec): GpuTexture {
    val queue = pool.getOrPut(spec) { ArrayDeque() }
    val texture = if (queue.isNotEmpty()) {
      queue.poll()!!
    } else {
      GpuTexture(spec)
    }
    activeTextures.add(texture)
    return texture
  }

  @Synchronized
  fun release(texture: GpuTexture) {
    if (activeTextures.remove(texture)) {
      val queue = pool.getOrPut(texture.spec) { ArrayDeque() }
      if (queue.size < maxCapacityPerSpec) {
        queue.offer(texture)
      } else {
        texture.release()
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
    for (active in activeTextures) {
      active.release()
    }
    activeTextures.clear()
  }
}
