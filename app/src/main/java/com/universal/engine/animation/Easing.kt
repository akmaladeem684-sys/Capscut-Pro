package com.universal.engine.animation

import kotlin.math.pow
import kotlin.math.sin

/** All easing is pure math over normalized t ∈ [0,1]. Timestamps never enter here. */
object Easing {
    val LINEAR: (Float) -> Float = { it }
    val EASE_IN: (Float) -> Float = { it * it }
    val EASE_OUT: (Float) -> Float = { 1f - (1f - it) * (1f - it) }
    val EASE_IN_OUT: (Float) -> Float = { if (it < 0.5f) 2f * it * it else 1f - 2f * (1f - it) * (1f - it) }
    val EASE_OUT_BACK: (Float) -> Float = { t -> val c = 1.70158f; val u = t - 1f; 1f + (c + 1f) * u * u * u + c * u * u }
    val EASE_OUT_ELASTIC: (Float) -> Float = { t ->
        if (t == 0f || t == 1f) t
        else (2f.pow(-10f * t) * sin((t * 10f - 0.75f) * (2.0943951f / 3f)) + 1f)
    }

    fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float): (Float) -> Float = { x ->
        if (x <= 0f) 0f else if (x >= 1f) 1f else {
            var lo = 0.0f
            var hi = 1.0f
            var t = x
            repeat(24) {
                val tx = bezierX(t, x1, x2)
                if (tx < x) lo = t else hi = t
                t = (lo + hi) / 2f
            }
            bezierY(t, y1, y2)
        }
    }

    private fun bezierX(t: Float, x1: Float, x2: Float) = 3f * (1-t)*(1-t)*t*x1 + 3f*(1-t)*t*t*x2 + t*t*t
    private fun bezierY(t: Float, y1: Float, y2: Float) = 3f * (1-t)*(1-t)*t*y1 + 3f*(1-t)*t*t*y2 + t*t*t
}
