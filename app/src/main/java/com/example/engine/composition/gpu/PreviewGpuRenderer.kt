package com.example.engine.composition.gpu

/**
 * Phase 2 GPU preview facade. Reuses the existing native renderer and bridge rather than
 * introducing a second rendering stack or changing existing UI design.
 */
class PreviewGpuRenderer {
  private var initialized = false

  @Synchronized
  fun initialize(width: Int, height: Int): Boolean {
    initialized = NativeRenderBridge.init(width.coerceAtLeast(1), height.coerceAtLeast(1))
    return initialized
  }

  @Synchronized
  fun resize(width: Int, height: Int) {
    if (initialized) NativeRenderBridge.resize(width.coerceAtLeast(1), height.coerceAtLeast(1))
  }

  @Synchronized
  fun render(layers: List<NativeLayer>) {
    if (initialized && layers.isNotEmpty()) NativeRenderBridge.renderFrame(layers)
  }

  @Synchronized
  fun onContextLost() {
    if (initialized) {
      NativeRenderBridge.onContextLost()
      initialized = false
    }
  }

  @Synchronized
  fun release() {
    if (initialized) {
      NativeRenderBridge.release()
      initialized = false
    }
  }

  fun isInitialized(): Boolean = initialized
}
