package com.vfx.engine.core.effect

import com.vfx.engine.core.params.ParamMap
import com.vfx.engine.core.params.ParameterDescriptor

enum class EffectCategory {
  COLOR,
  BLUR,
  LIGHT,
  DISTORTION,
  STYLIZE,
  SHARPEN,
  RETOUCH,
  TRANSFORM
}

data class RenderRequirements(
  val needsMipmaps: Boolean = false,
  val needsLinearFiltering: Boolean = true,
  val needsDepthBuffer: Boolean = false,
  val needsTemporalHistory: Boolean = false,
  val inputBufferCount: Int = 1
)

data class EffectDefinition(
  val id: String,
  val displayName: String,
  val category: EffectCategory,
  val parameters: List<ParameterDescriptor>,
  val requirements: RenderRequirements = RenderRequirements()
)

data class EffectInstance(
  val instanceId: String,
  val effectId: String,
  val parameters: ParamMap = ParamMap(),
  val isEnabled: Boolean = true
)

interface Effect {
  val definition: EffectDefinition
}
