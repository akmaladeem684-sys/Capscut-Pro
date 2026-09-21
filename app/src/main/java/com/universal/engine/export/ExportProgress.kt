package com.universal.engine.export

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Progress derived ONLY from frame counts and timestamps. */
class ExportProgress(val totalFrames: Long, val totalDurationUs: Long) {
    val currentFrame = AtomicLong(0)
    val currentTimestampUs = AtomicLong(0)
    val encodedFrames = AtomicLong(0)
    val stage = AtomicReference(Stage.EVALUATING)

    enum class Stage { PREPARING, EVALUATING, RENDERING, ENCODING, AUDIO, FINALIZING, DONE, CANCELLED, FAILED }

    val percent: Int get() = if (totalFrames <= 0) 0 else ((currentFrame.get() * 100) / totalFrames).toInt().coerceIn(0, 100)

    private var firstEncodeNanos = 0L
    val encodingFps: Double get() {
        if (firstEncodeNanos == 0L) return 0.0
        val elapsed = (System.nanoTime() - firstEncodeNanos) / 1e9
        return if (elapsed <= 0) 0.0 else encodedFrames.get() / elapsed
    }
    val estimatedRemainingSeconds: Long get() {
        val fps = encodingFps
        return if (fps <= 0) -1 else ((totalFrames - currentFrame.get()) / fps).toLong()
    }

    fun markEncodeStart() { if (firstEncodeNanos == 0L) firstEncodeNanos = System.nanoTime() }
}
