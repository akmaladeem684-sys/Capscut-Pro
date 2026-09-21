package com.example.engine

import com.example.engine.composition.coordinates.CoordinateNormalizationService
import com.example.engine.composition.coordinates.NormalizedCoordinates
import com.example.engine.composition.coordinates.toMedia3OverlaySettings
import com.vfx.engine.core.EngineConfig
import com.vfx.engine.core.Microseconds
import com.vfx.engine.core.color.ColorEngine
import com.vfx.engine.core.color.ToneMapper
import com.vfx.engine.core.color.ToneMapperType
import com.vfx.engine.core.effect.EffectCategory
import com.vfx.engine.core.effect.EffectInstance
import com.vfx.engine.core.graph.PassResource
import com.vfx.engine.core.graph.RenderGraph
import com.vfx.engine.core.graph.RenderGraphCompiler
import com.vfx.engine.core.graph.RenderPass
import com.vfx.engine.core.keyframe.EasingPreset
import com.vfx.engine.core.keyframe.Keyframe
import com.vfx.engine.core.keyframe.KeyframeTrack
import com.vfx.engine.core.lut.CubeLutParser
import com.vfx.engine.core.math.Color
import com.vfx.engine.core.params.ParamMap
import com.vfx.engine.core.params.ParameterValue
import com.vfx.engine.core.registry.EffectRegistry
import com.vfx.engine.core.stack.EffectStack
import com.vfx.engine.core.stack.StackEvaluator
import com.vfx.engine.effects.BuiltinEffects
import com.vfx.engine.media.media3.Media3TransformerBridge
import com.vfx.engine.pipeline.EffectsEngine
import com.vfx.engine.pipeline.RenderRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

class EffectsEngineIntegrationTest {

  @Before
  fun setUp() {
    BuiltinEffects.registerAll()
  }

  @Test
  fun testBuiltinEffectsRegistry() {
    val definitions = EffectRegistry.getAllDefinitions()
    assertTrue("Builtin effects should be registered", definitions.isNotEmpty())

    val colorCorrectionDef = EffectRegistry.getDefinition("vfx_color_correction")
    assertNotNull("Color correction effect should be found", colorCorrectionDef)
    assertEquals(EffectCategory.COLOR, colorCorrectionDef?.category)

    val instance = EffectRegistry.createInstance("vfx_kawase_blur")
    assertNotNull("Effect instance should be created", instance)
  }

  @Test
  fun testStackAndParamEvaluator() {
    val params = ParamMap(mapOf("exposure" to ParameterValue.FloatVal(1.5f)))
    val instance = EffectInstance(
      instanceId = "inst_1",
      effectId = "vfx_color_correction",
      parameters = params,
      isEnabled = true
    )

    val stack = EffectStack(listOf(instance))
    val evaluated = StackEvaluator.evaluateStackAtTime(stack, Microseconds.fromMillis(500))

    assertEquals(1, evaluated.size)
    assertEquals("inst_1", evaluated[0].first.instanceId)
    assertEquals(1.5f, evaluated[0].second.getFloat("exposure"))
  }

  @Test
  fun testKeyframeTrackEvaluation() {
    val k0 = Keyframe(time = Microseconds.fromMillis(0), value = 0.0f, easing = EasingPreset.LINEAR)
    val k1 = Keyframe(time = Microseconds.fromMillis(1000), value = 10.0f, easing = EasingPreset.LINEAR)

    val track = KeyframeTrack(
      paramName = "blurRadius",
      keyframes = listOf(k0, k1),
      interpolator = { a, b, t -> a + (b - a) * t }
    )

    val valueAtMid = track.evaluateAt(Microseconds.fromMillis(500))
    assertNotNull(valueAtMid)
    assertEquals(5.0f, valueAtMid!!, 0.001f)
  }

  @Test
  fun testRenderGraphCompilationAndCulling() {
    val pass1 = RenderPass("pass1", "effect_1", listOf("res_input"), "res_mid")
    val pass2 = RenderPass("pass2", "effect_2", listOf("res_mid"), "res_final")
    val unusedPass = RenderPass("pass_unused", "effect_3", listOf("res_input"), "res_unused")

    val graph = RenderGraph(
      passes = listOf(pass1, pass2, unusedPass),
      resources = listOf(
        PassResource("res_input", 1080, 1920),
        PassResource("res_mid", 1080, 1920),
        PassResource("res_final", 1080, 1920),
        PassResource("res_unused", 1080, 1920)
      )
    )

    val compiledPasses = RenderGraphCompiler.compileAndCull(graph, "res_final")
    assertEquals(2, compiledPasses.size)
    assertEquals("pass1", compiledPasses[0].id)
    assertEquals("pass2", compiledPasses[1].id)
  }

  @Test
  fun testCoordinateNormalizationAndOverlaySettings() {
    val service = CoordinateNormalizationService(targetResolution = IntSize(1080, 1920))

    val norm = service.normalizeFromComposeDp(
      centerDpOffset = DpOffset(180.dp, 320.dp),
      overlayDpSize = DpSize(100.dp, 100.dp),
      viewportDpSize = DpSize(360.dp, 640.dp)
    )

    assertEquals(0.5f, norm.uCenterX, 0.01f)
    assertEquals(0.5f, norm.vCenterY, 0.01f)

    val overlaySettings = norm.toMedia3OverlaySettings()
    assertNotNull(overlaySettings)
  }

  @Test
  fun testColorEngineAndToneMapping() {
    val colorLinear = ColorEngine.sRgbToLinear(0.5f)
    val colorSRgb = ColorEngine.linearToSRgb(colorLinear)
    assertEquals(0.5f, colorSRgb, 0.01f)

    val hdrColor = Color(r = 2.0f, g = 1.5f, b = 0.8f, a = 1.0f)
    val toneMappedAces = ToneMapper.applyToneMap(hdrColor, ToneMapperType.ACES)

    assertTrue("Tone mapped R should be in 0..1 range", toneMappedAces.r in 0f..1f)
    assertTrue("Tone mapped G should be in 0..1 range", toneMappedAces.g in 0f..1f)
  }

  @Test
  fun testCubeLutParser() {
    val cubeContent = """
      TITLE "Test LUT"
      LUT_3D_SIZE 2
      0.0 0.0 0.0
      1.0 0.0 0.0
      0.0 1.0 0.0
      1.0 1.0 0.0
      0.0 0.0 1.0
      1.0 0.0 1.0
      0.0 1.0 1.0
      1.0 1.0 1.0
    """.trimIndent()

    val lutData = CubeLutParser.parse(cubeContent)
    assertEquals("Test LUT", lutData.title)
    assertEquals(2, lutData.size)
    assertEquals(24, lutData.tableData.size)
  }

  @Test
  fun testMedia3EffectAdapterBridge() {
    val instance = EffectInstance("inst_glow", "vfx_bloom")
    val glEffect = Media3TransformerBridge.adaptEffectToMedia3(instance)
    assertNotNull(glEffect)
  }

  @Test
  fun testEffectsEngineExecutionPlan() {
    val engine = EffectsEngine(EngineConfig())
    val request = RenderRequest(
      pts = Microseconds.fromMillis(200),
      width = 1080,
      height = 1920,
      stack = EffectStack()
    )

    val plan = engine.prepareExecutionPlan(request)
    assertNotNull(plan)
    assertEquals(0, plan.activePassesCount)
  }
}
