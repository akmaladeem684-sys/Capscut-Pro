package com.vfx.engine.core.registry

import com.vfx.engine.core.effect.Effect
import com.vfx.engine.core.effect.EffectDefinition
import java.util.concurrent.ConcurrentHashMap

data class EffectManifest(
  val version: Int = 1,
  val registeredEffectIds: List<String> = emptyList()
)

object EffectRegistry {
  private val factories = ConcurrentHashMap<String, () -> Effect>()
  private val definitions = ConcurrentHashMap<String, EffectDefinition>()

  fun register(definition: EffectDefinition, factory: () -> Effect) {
    definitions[definition.id] = definition
    factories[definition.id] = factory
  }

  fun getDefinition(id: String): EffectDefinition? = definitions[id]

  fun createInstance(id: String): Effect? = factories[id]?.invoke()

  fun getAllDefinitions(): List<EffectDefinition> = definitions.values.toList()

  fun generateManifest(): EffectManifest {
    return EffectManifest(version = 1, registeredEffectIds = definitions.keys.toList())
  }
}
