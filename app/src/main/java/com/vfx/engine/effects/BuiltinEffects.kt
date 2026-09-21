package com.vfx.engine.effects

import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectDefinition
import com.vfx.engine.core.effect.RenderRequirements
import com.vfx.engine.core.params.ParamConstraints
import com.vfx.engine.core.params.ParamMap
import com.vfx.engine.core.params.ParameterDescriptor
import com.vfx.engine.core.registry.EffectRegistry
import com.vfx.engine.effects.base.SinglePassEffect
import com.vfx.engine.gpu.fbo.FramebufferObject

class ColorCorrectionEffect : SinglePassEffect(
  EffectDefinition(
    id = "vfx_color_correction",
    displayName = "Color Correction",
    category = EffectCategory.COLOR,
    parameters = listOf(
      ParameterDescriptor.FloatParam("exposure", 0.0f, ParamConstraints(-2f, 2f)),
      ParameterDescriptor.FloatParam("contrast", 1.0f, ParamConstraints(0f, 2f)),
      ParameterDescriptor.FloatParam("saturation", 1.0f, ParamConstraints(0f, 2f))
    )
  )
) {
  override fun render(inputFbo: FramebufferObject, outputFbo: FramebufferObject, params: ParamMap) {
    // Single pass color transformation
  }
}

class KawaseBlurEffect : SinglePassEffect(
  EffectDefinition(
    id = "vfx_kawase_blur",
    displayName = "Kawase Blur",
    category = EffectCategory.BLUR,
    parameters = listOf(
      ParameterDescriptor.FloatParam("radius", 4f, ParamConstraints(0f, 32f))
    )
  )
) {
  override fun render(inputFbo: FramebufferObject, outputFbo: FramebufferObject, params: ParamMap) {
    // Mobile dual-kawase blur pass
  }
}

class BloomEffect : SinglePassEffect(
  EffectDefinition(
    id = "vfx_bloom",
    displayName = "Bloom & Glow",
    category = EffectCategory.LIGHT,
    parameters = listOf(
      ParameterDescriptor.FloatParam("intensity", 0.5f, ParamConstraints(0f, 2f)),
      ParameterDescriptor.FloatParam("threshold", 0.65f, ParamConstraints(0f, 1f))
    )
  )
) {
  override fun render(inputFbo: FramebufferObject, outputFbo: FramebufferObject, params: ParamMap) {
    // Bloom pass
  }
}

object BuiltinEffects {
  fun registerAll() {
    val ccDef = ColorCorrectionEffect().definition
    EffectRegistry.register(ccDef) { ColorCorrectionEffect() }

    val blurDef = KawaseBlurEffect().definition
    EffectRegistry.register(blurDef) { KawaseBlurEffect() }

    val bloomDef = BloomEffect().definition
    EffectRegistry.register(bloomDef) { BloomEffect() }
  }
}
