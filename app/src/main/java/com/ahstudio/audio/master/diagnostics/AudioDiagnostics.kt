package com.ahstudio.audio.master.diagnostics

class AudioPerformanceMonitor {
    @Volatile var totalBlocksRendered: Long = 0; private set
    @Volatile var lastBlockRenderMs: Double = 0.0; private set
    @Volatile var maxBlockRenderMs: Double = 0.0; private set

    fun onBlockRendered(renderMs: Double, frames: Int) {
        totalBlocksRendered++
        lastBlockRenderMs = renderMs
        if (renderMs > maxBlockRenderMs) maxBlockRenderMs = renderMs
    }

    fun reset() {
        totalBlocksRendered = 0
        lastBlockRenderMs = 0.0
        maxBlockRenderMs = 0.0
    }
}

class AudioGlitchDetector {
    @Volatile var totalGlitchCount: Long = 0; private set

    fun onUnderrun() {
        totalGlitchCount++
    }

    fun reset() {
        totalGlitchCount = 0
    }
}
