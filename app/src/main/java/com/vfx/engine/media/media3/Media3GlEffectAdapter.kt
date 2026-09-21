package com.vfx.engine.media.media3

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.example.engine.effects.media3.WysiwygGlowGlEffect
import com.vfx.engine.core.effect.EffectInstance

@OptIn(UnstableApi::class)
class Media3GlEffectAdapter(
  val effectInstance: EffectInstance
) : GlEffect {

  override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
    // Bridges engine-core EffectInstance directly to Media3 GlEffect pipeline
    return WysiwygGlowGlEffect().toGlShaderProgram(context, useHdr)
  }
}

@OptIn(UnstableApi::class)
object Media3TransformerBridge {
  fun adaptEffectToMedia3(instance: EffectInstance): GlEffect {
    return Media3GlEffectAdapter(instance)
  }
}
