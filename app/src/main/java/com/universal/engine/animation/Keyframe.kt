package com.universal.engine.animation

import com.universal.engine.renderer2d.Transform2D

/** One keyframe of a float property. Times are timeline microseconds — never wall clock. */
data class Keyframe(val timeUs: Long, val value: Float, val easing: (Float) -> Float = Easing.LINEAR)

/**
 * Evaluates a keyed float property at an arbitrary timeline timestamp.
 */
class KeyedProperty(
    val keyframes: List<Keyframe>,
    val loopMode: LoopMode = LoopMode.NONE,
    val loopDurationUs: Long = 0L,
    val staggerUs: Long = 0L
) {
    enum class LoopMode { NONE, LOOP, PING_PONG }

    private val sorted = keyframes.sortedBy { it.timeUs }
    val startUs: Long get() = (sorted.firstOrNull()?.timeUs ?: 0L) + staggerUs
    val endUs: Long get() = sorted.lastOrNull()?.timeUs ?: 0L

    fun evaluate(timestampUs: Long): Float {
        if (sorted.isEmpty()) throw IllegalArgumentException("KeyedProperty with no keyframes")
        if (sorted.size == 1) return sorted[0].value

        var t = timestampUs - staggerUs
        val span = endUs - (sorted.firstOrNull()?.timeUs ?: 0L)
        if (span <= 0) return sorted.last().value

        val baseStart = sorted.first().timeUs
        when (loopMode) {
            LoopMode.LOOP -> if (t > endUs) t = baseStart + ((t - baseStart) % span)
            LoopMode.PING_PONG -> if (t > endUs) {
                val phase = (t - baseStart) % (span * 2)
                t = if (phase <= span) baseStart + phase else baseStart + span * 2 - phase
            }
            LoopMode.NONE -> t = t.coerceIn(baseStart, endUs)
        }

        if (t <= sorted.first().timeUs) return sorted.first().value
        if (t >= sorted.last().timeUs) return sorted.last().value

        var lo = 0
        var hi = sorted.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (sorted[mid].timeUs <= t) lo = mid else hi = mid
        }
        val a = sorted[lo]
        val b = sorted[hi]
        val raw = (t - a.timeUs).toFloat() / (b.timeUs - a.timeUs)
        return a.value + (b.value - a.value) * b.easing(raw)
    }
}

/** Output slot: a fully resolved transform for one layer at one timestamp. */
data class AnimatedTransform(
    val translateX: KeyedProperty? = null, val translateY: KeyedProperty? = null,
    val scaleX: KeyedProperty? = null, val scaleY: KeyedProperty? = null,
    val rotation: KeyedProperty? = null, val opacity: KeyedProperty? = null
) {
    fun evaluate(timestampUs: Long, base: Transform2D): Transform2D = base.copy(
        translationX = translateX?.evaluate(timestampUs) ?: base.translationX,
        translationY = translateY?.evaluate(timestampUs) ?: base.translationY,
        scaleX = scaleX?.evaluate(timestampUs) ?: base.scaleX,
        scaleY = scaleY?.evaluate(timestampUs) ?: base.scaleY,
        rotationDegrees = rotation?.evaluate(timestampUs) ?: base.rotationDegrees,
        opacity = opacity?.evaluate(timestampUs) ?: base.opacity
    )
}

/** Central evaluation entry used by BOTH playback and export. Pure function of timestamp. */
class AnimationEvaluator {
    fun evaluateTransform(animated: AnimatedTransform?, timestampUs: Long, base: Transform2D): Transform2D =
        animated?.evaluate(timestampUs, base) ?: base
}
