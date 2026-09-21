package com.universal.engine

import com.universal.engine.animation.Easing
import com.universal.engine.animation.KeyedProperty
import com.universal.engine.animation.Keyframe
import org.junit.Assert.*
import org.junit.Test

class AnimationEvaluatorTest {

    private fun prop(vararg kf: Pair<Long, Float>) =
        KeyedProperty(kf.map { Keyframe(it.first, it.second) })

    @Test
    fun linear_interpolation_exact_at_endpoints() {
        val p = prop(0L to 0f, 1_000_000L to 10f)
        assertEquals(0f, p.evaluate(0L), 1e-5f)
        assertEquals(10f, p.evaluate(1_000_000L), 1e-5f)
        assertEquals(5f, p.evaluate(500_000L), 1e-4f)
    }

    @Test
    fun clamps_outside_range() {
        val p = prop(0L to 1f, 100L to 3f)
        assertEquals(1f, p.evaluate(-50L), 0f)
        assertEquals(3f, p.evaluate(10_000L), 0f)
    }

    @Test
    fun loop_wraps_deterministically() {
        val p = KeyedProperty(listOf(Keyframe(0L, 0f), Keyframe(1_000_000L, 8f)), KeyedProperty.LoopMode.LOOP, 1_000_000L)
        assertEquals(2f, p.evaluate(2_250_000L), 1e-3f)
    }

    @Test
    fun ping_pong_mirrors() {
        val p = KeyedProperty(listOf(Keyframe(0L, 0f), Keyframe(1_000_000L, 10f)), KeyedProperty.LoopMode.PING_PONG, 1_000_000L)
        assertEquals(10f, p.evaluate(1_000_000L), 1e-4f)
        assertEquals(0f, p.evaluate(2_000_000L), 1e-4f)
    }

    @Test
    fun bezier_easing_matches_control_shape() {
        val ease = Easing.cubicBezier(0.4f, 0f, 0.2f, 1f)
        assertTrue(ease(0.5f) in 0.4f..0.8f)
        assertEquals(0f, ease(0f), 1e-5f)
        assertEquals(1f, ease(1f), 1e-5f)
    }

    @Test
    fun timestamp_driven_same_input_same_output() {
        val p = prop(0L to 0f, 1_000_000L to 1f)
        val a = p.evaluate(333_333L)
        val b = p.evaluate(333_333L)
        assertEquals(a, b, 0f)
    }
}
