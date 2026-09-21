package com.ahstudio.audio.master.metering

import com.ahstudio.audio.master.core.AudioBuffer
import com.ahstudio.audio.master.core.AudioRenderContext
import com.ahstudio.audio.master.core.linToDb
import com.ahstudio.audio.master.dsp.eq.BiquadFilter
import com.ahstudio.audio.master.dsp.eq.BiquadType
import kotlin.math.log10
import kotlin.math.sqrt

/** ITU-R BS.1770-4 K-weighted LUFS meter. */
class LoudnessMeter {
    private val stage1 = BiquadFilter().apply { configure(BiquadType.HIGH_SHELF, 1500f, 4.0f, 0.7071f) }
    private val stage2 = BiquadFilter().apply { configure(BiquadType.HIGH_PASS, 38f, 0f, 0.5f) }
    private var totalSum = 0.0
    private var totalSamples = 0L

    fun process(buffer: AudioBuffer, ctx: AudioRenderContext) {
        stage1.prepare(ctx.format, ctx.frames)
        stage2.prepare(ctx.format, ctx.frames)
        stage1.process(buffer, ctx)
        stage2.process(buffer, ctx)
        val n = ctx.frames
        val chs = buffer.channels
        for (i in 0 until n) {
            var frameSq = 0.0
            for (c in 0 until chs) {
                val v = buffer.data[c][i].toDouble()
                frameSq += v * v
            }
            totalSum += frameSq
            totalSamples++
        }
    }

    fun lufs(): Double {
        if (totalSamples == 0L) return -70.0
        val meanSq = totalSum / totalSamples
        if (meanSq < 1e-12) return -70.0
        return -0.691 + 10.0 * log10(meanSq)
    }

    fun reset() {
        totalSum = 0.0
        totalSamples = 0L
        stage1.reset()
        stage2.reset()
    }
}

class AudioLevelMeter {
    @Volatile var peakDb: Float = -120f; private set
    @Volatile var rmsDb: Float = -120f; private set

    fun update(buffer: AudioBuffer, ctx: AudioRenderContext) {
        val peak = buffer.maxAbs(ctx.frames)
        peakDb = linToDb(peak)
        var sumSq = 0.0
        val count = ctx.frames * buffer.channels
        for (c in 0 until buffer.channels) {
            val d = buffer.data[c]
            for (i in 0 until ctx.frames) {
                val v = d[i].toDouble()
                sumSq += v * v
            }
        }
        val rms = if (count > 0) sqrt(sumSq / count).toFloat() else 0f
        rmsDb = linToDb(rms)
    }

    fun reset() {
        peakDb = -120f
        rmsDb = -120f
    }
}
