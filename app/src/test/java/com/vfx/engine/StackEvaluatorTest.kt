package com.vfx.engine

import com.vfx.engine.core.Microseconds
import com.vfx.engine.core.effect.EffectInstance
import com.vfx.engine.core.keyframe.Keyframe
import com.vfx.engine.core.registry.EffectRegistry
import com.vfx.engine.core.stack.EffectStack
import com.vfx.engine.core.stack.StackEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StackEvaluatorTest {

    @Test
    fun `empty stack evaluates to empty snapshots`() {
        val stack = EffectStack()
        val snapshots = StackEvaluator.evaluateStackAtTime(stack, Microseconds(0L))
        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun `disabled effect instances are excluded from evaluation`() {
        val registry = EffectRegistry()
        val stack = EffectStack(registry)

        val e1 = EffectInstance("vfx.color.brightness", "inst1")
        e1.enabled = true
        val e2 = EffectInstance("vfx.blur.kawase", "inst2")
        e2.enabled = false

        stack.addInstance(e1)
        stack.addInstance(e2)

        val snapshots = StackEvaluator.evaluateStackAtTime(stack, Microseconds(100_000L))
        assertEquals(1, snapshots.size)
        assertEquals("vfx.color.brightness", snapshots[0].effectId)
    }

    @Test
    fun `stack evaluation preserves authoring order`() {
        val stack = EffectStack()
        val e1 = EffectInstance("vfx.color.contrast", "inst1")
        val e2 = EffectInstance("vfx.light.bloom", "inst2")
        val e3 = EffectInstance("vfx.light.vignette", "inst3")

        stack.addInstance(e1)
        stack.addInstance(e2)
        stack.addInstance(e3)

        val snapshots = StackEvaluator.evaluateStackAtTime(stack, Microseconds(500_000L))
        assertEquals(3, snapshots.size)
        assertEquals("vfx.color.contrast", snapshots[0].effectId)
        assertEquals("vfx.light.bloom", snapshots[1].effectId)
        assertEquals("vfx.light.vignette", snapshots[2].effectId)
    }
}
