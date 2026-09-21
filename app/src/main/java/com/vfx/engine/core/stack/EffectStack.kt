package com.vfx.engine.core.stack

import com.vfx.engine.core.Microseconds
import com.vfx.engine.core.effect.EffectInstance
import com.vfx.engine.core.params.ParamMap

data class EffectStack(
  val effects: List<EffectInstance> = emptyList()
) {
  fun addEffect(effect: EffectInstance): EffectStack {
    return copy(effects = effects + effect)
  }

  fun removeEffect(instanceId: String): EffectStack {
    return copy(effects = effects.filterNot { it.instanceId == instanceId })
  }
}

object StackEvaluator {
  fun evaluateStackAtTime(
    stack: EffectStack,
    pts: Microseconds
  ): List<Pair<EffectInstance, ParamMap>> {
    return stack.effects
      .filter { it.isEnabled }
      .map { instance ->
        Pair(instance, instance.parameters)
      }
  }
}
