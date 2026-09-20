package com.example.engine.effects.media3

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import com.example.domain.model.FilterSettings
import com.example.domain.model.FilterType
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.domain.model.VideoAdjustments
import com.example.engine.composition.ColorFilterGenerator

/**
 * High-level coordinator that integrates custom OpenGL-based shaders
 * (Color Grading, LUTs, and creative shaders) into the AndroidX Media3 Effect pipeline.
 *
 * It generates an optimized sequence of [Effect] instances applied directly
 * to video frames in the Media3 [DefaultVideoFrameProcessor] before frames reach the encoder.
 */
@OptIn(UnstableApi::class)
object Media3EffectPipeline {

  /**
   * Constructs the full sequence of Media3 effects for an export timeline:
   * 1. Resolution / Aspect Ratio layout ([Presentation])
   * 2. Frame rate control ([FrameDropEffect])
   * 3. OpenGL Color Grading ([ColorGradingGlEffect])
   * 4. OpenGL 3D/2D LUT ([LutGlEffect]) if provided
   * 5. Active custom shader effects (Glow, Glitch, RGB Split, etc.)
   */
  fun buildVideoEffects(
    context: Context,
    timeline: Timeline,
    exportWidth: Int,
    exportHeight: Int,
    targetFps: Float,
    customLut: Bitmap? = null,
    customLutIntensity: Float = 1.0f
  ): List<Effect> {
    val effects = mutableListOf<Effect>()

    // 1. Presentation: Scale to fit target dimensions
    val presentation = Presentation.createForWidthAndHeight(
      exportWidth,
      exportHeight,
      Presentation.LAYOUT_SCALE_TO_FIT
    )
    effects.add(presentation)

    // 2. Framerate control
    val frameDrop = FrameDropEffect.createDefaultFrameDropEffect(targetFps)
    effects.add(frameDrop)

    // 3. OpenGL Color Grading Shader (Brightness, Contrast, Saturation, Temp, Tint, Matrix Filter)
    val colorGrading = createColorGradingEffect(timeline.adjustments, timeline.filter)
    if (colorGrading != null && !colorGrading.isNoOp(exportWidth, exportHeight)) {
      effects.add(colorGrading)
    }

    // 4. Custom 3D / 2D LUT Shader
    if (customLut != null && customLutIntensity > 0.01f) {
      val lutEffect = LutGlEffect(
        lutBitmap = customLut,
        intensity = customLutIntensity
      )
      // Prefer Media3 native SingleColorLut if format is exact, otherwise use our flexible LutGlEffect
      val media3Lut = lutEffect.toMedia3SingleColorLut()
      if (media3Lut != null) {
        effects.add(media3Lut)
      } else {
        effects.add(lutEffect)
      }
    }

    return effects
  }

  /**
   * Builds the effect sequence for an individual clip, combining clip-specific adjustments
   * and project-level timeline adjustments.
   */
  fun buildClipEffects(
    context: Context,
    clip: VideoClip,
    timeline: Timeline,
    exportWidth: Int,
    exportHeight: Int,
    targetFps: Float
  ): List<Effect> {
    val effects = mutableListOf<Effect>()

    // 1. Presentation
    effects.add(Presentation.createForWidthAndHeight(exportWidth, exportHeight, Presentation.LAYOUT_SCALE_TO_FIT))

    // 2. Framerate
    effects.add(FrameDropEffect.createDefaultFrameDropEffect(targetFps))

    // 3. Combined Color Grading: Clip filter or Timeline filter + adjustments
    val effectiveFilter = clip.filter ?: timeline.filter
    val colorGrading = createColorGradingEffect(timeline.adjustments, effectiveFilter)
    if (colorGrading != null && !colorGrading.isNoOp(exportWidth, exportHeight)) {
      effects.add(colorGrading)
    }

    return effects
  }

  /**
   * Creates an OpenGL [ColorGradingGlEffect] from adjustments and filter settings.
   */
  fun createColorGradingEffect(
    adjustments: VideoAdjustments,
    filterSettings: FilterSettings
  ): ColorGradingGlEffect? {
    val effect = ColorGradingGlEffect.fromTimeline(adjustments, filterSettings)
    return if (effect.isNoOp(1920, 1080)) null else effect
  }

  /**
   * Creates an OpenGL [LutGlEffect] from a 3D/2D LUT Bitmap.
   */
  fun createLutEffect(
    lutBitmap: Bitmap,
    intensity: Float = 1.0f
  ): LutGlEffect {
    return LutGlEffect(lutBitmap = lutBitmap, intensity = intensity)
  }

  /**
   * Creates a custom RGB Split (chromatic aberration) shader effect using [CustomShaderGlEffect].
   */
  fun createRgbSplitEffect(intensity: Float = 0.5f): CustomShaderGlEffect {
    val fragmentShader = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uIntensity;
varying vec2 vTexSamplingCoord;

void main() {
  vec2 uv = clamp(vTexSamplingCoord, 0.0, 1.0);
  vec2 offset = vec2(uIntensity * 0.02, 0.0);
  float r = texture2D(uTexSampler, clamp(uv + offset, 0.0, 1.0)).r;
  float g = texture2D(uTexSampler, uv).g;
  float b = texture2D(uTexSampler, clamp(uv - offset, 0.0, 1.0)).b;
  float a = texture2D(uTexSampler, uv).a;
  gl_FragColor = vec4(r, g, b, a);
}
"""
    return CustomShaderGlEffect(
      name = "RgbSplitEffect",
      fragmentShader = fragmentShader,
      uniformBinder = { program: GlProgram, _, _, _ ->
        program.setFloatUniform("uIntensity", intensity)
      }
    )
  }

  /**
   * Creates a custom Glitch shader effect using [CustomShaderGlEffect].
   */
  fun createGlitchEffect(intensity: Float = 0.5f): CustomShaderGlEffect {
    val fragmentShader = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uIntensity;
uniform float uTime;
varying vec2 vTexSamplingCoord;

float rand(vec2 co) {
  return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
  vec2 uv = clamp(vTexSamplingCoord, 0.0, 1.0);
  float sliceY = floor(uv.y * 32.0);
  float sliceNoise = fract(sin(dot(vec2(sliceY, floor(uTime * 14.0)), vec2(12.9898, 78.233))) * 43758.5453);
  float glitchShift = 0.0;
  if (sliceNoise > 0.62) {
    glitchShift = (sliceNoise - 0.62) * 0.18 * uIntensity;
  }
  vec2 uvR = clamp(uv + vec2(glitchShift + 0.015 * uIntensity, 0.0), 0.0, 1.0);
  vec2 uvG = clamp(uv + vec2(glitchShift, 0.0), 0.0, 1.0);
  vec2 uvB = clamp(uv + vec2(glitchShift - 0.015 * uIntensity, 0.0), 0.0, 1.0);
  float r = texture2D(uTexSampler, uvR).r;
  float g = texture2D(uTexSampler, uvG).g;
  float b = texture2D(uTexSampler, uvB).b;
  float a = texture2D(uTexSampler, uvG).a;
  gl_FragColor = vec4(r, g, b, a);
}
"""
    return CustomShaderGlEffect(
      name = "GlitchEffect",
      fragmentShader = fragmentShader,
      uniformBinder = { program: GlProgram, presentationTimeUs: Long, _, _ ->
        program.setFloatUniform("uIntensity", intensity)
        program.setFloatUniform("uTime", (presentationTimeUs / 1_000_000.0).toFloat())
      }
    )
  }
}
