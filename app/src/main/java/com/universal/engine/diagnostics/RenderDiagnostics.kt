package com.universal.engine.diagnostics

import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Lock-free counters. Overhead in production is a handful of AtomicLong increments;
 * set [enabled]=false (default in release) to reduce it to branch cost.
 */
class RenderDiagnostics {

    @Volatile var enabled: Boolean = false

    enum class Stage { IDLE, DECODE, EVALUATE, TRANSFORM, COLOR, LAYER_2D, LAYER_3D, TEXT, EFFECTS, MASKS, COMPOSITE, GRADE, TONE_MAP, OUTPUT, ENCODE }

    @PublishedApi internal val stageRef = AtomicReference(Stage.IDLE)
    val renderFps = FpsCounter()
    val decodeFps = FpsCounter()
    val encoderFps = FpsCounter()
    val droppedFrames = AtomicLong()
    val renderedFrames = AtomicLong()
    val gpuTimeNanos = AtomicLong()
    @PublishedApi internal val cpuTimeNanos = AtomicLong()

    var glesVersion: String = "unknown"
    var gpuRenderer: String = "unknown"
    var gpuVendor: String = "unknown"
    var resolution: String = "0x0"
    var textureMemoryBytes = AtomicLong()
    var framebufferMemoryBytes = AtomicLong()
    var exportProgressPercent = AtomicLong()

    val currentStage: Stage get() = stageRef.get()

    inline fun <T> stage(s: Stage, block: () -> T): T {
        if (!enabled) return block()
        val prev = stageRef.getAndSet(s)
        val t0 = System.nanoTime()
        try {
            return block()
        } finally {
            cpuTimeNanos.addAndGet(System.nanoTime() - t0)
            stageRef.set(prev)
        }
    }

    fun frameDropped(reason: String) {
        if (enabled) Log.w(TAG, "frame dropped: $reason")
        droppedFrames.incrementAndGet()
    }

    fun dump(): String = buildString {
        appendLine("=== RenderDiagnostics ===")
        appendLine("GPU: $gpuVendor / $gpuRenderer / $glesVersion  res=$resolution")
        appendLine("render=${renderFps.fps}fps decode=${decodeFps.fps}fps encode=${encoderFps.fps}fps")
        appendLine("dropped=${droppedFrames.get()} rendered=${renderedFrames.get()}")
        appendLine("gpuTime=${gpuTimeNanos.get() / 1_000_000}ms cpuTime=${cpuTimeNanos.get() / 1_000_000}ms")
        appendLine("texMem=${textureMemoryBytes.get() / 1048576}MB fboMem=${framebufferMemoryBytes.get() / 1048576}MB")
        appendLine("stage=$currentStage exportProgress=${exportProgressPercent.get()}%")
    }

    class FpsCounter {
        private var lastNanos = System.nanoTime()
        private var count = 0L
        @Volatile var fps = 0.0; private set
        fun tick() {
            count++
            val now = System.nanoTime()
            val elapsed = now - lastNanos
            if (elapsed >= 1_000_000_000L) {
                fps = count * 1e9 / elapsed
                count = 0; lastNanos = now
            }
        }
    }

    companion object { private const val TAG = "RenderDiag" }
}
