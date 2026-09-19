package com.example.engine.composition.gpu

import android.content.Context
import android.graphics.*
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.example.domain.model.*
import com.example.engine.KeyframeInterpolator
import com.example.engine.composition.ComposedFrame
import com.example.engine.composition.ComposedOverlay
import com.example.engine.composition.ComposedSticker
import com.example.engine.composition.ComposedText
import com.example.engine.composition.StickerLayerRenderer
import com.example.engine.text.TextLayerRenderer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * Production-Grade GPU Composition Renderer powered by Native C++ OpenGL ES 3.0 Engine.
 * Supports 1, 3, 10, 20, and 25+ simultaneous layers (Base Video, PIP Videos, Image Stickers,
 * Text Layers, Visual Effects) with 60 FPS performance, deterministic Z-ordering,
 * texture recycling, and premultiplied alpha blending.
 */
class GpuCompositionRenderer(private val context: Context) {
  companion object {
    private const val TAG = "GpuCompositionRenderer"
    private const val FLOAT_SIZE_BYTES = 4
    private const val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 4 * FLOAT_SIZE_BYTES
    private const val POSITION_DATA_OFFSET = 0
    private const val TEXTURE_DATA_OFFSET = 2
    private const val MAX_UNTOUCHED_CACHE_FRAMES = 60
  }

  // Full-screen quad geometry: (x, y, u, v)
  private val quadVertices = floatArrayOf(
    -1.0f, -1.0f,  0.0f, 1.0f,
     1.0f, -1.0f,  1.0f, 1.0f,
    -1.0f,  1.0f,  0.0f, 0.0f,
     1.0f,  1.0f,  1.0f, 0.0f
  )

  private val vertexBuffer: FloatBuffer = ByteBuffer
    .allocateDirect(quadVertices.size * FLOAT_SIZE_BYTES)
    .order(ByteOrder.nativeOrder())
    .asFloatBuffer()
    .apply {
      put(quadVertices)
      position(0)
    }

  // OpenGL Programs for OES conversion & post-process effects
  private var program2D = 0
  private var programOes = 0
  private var programTransition = 0
  private var programEffect = 0

  // Framebuffers for OES conversion & multi-pass effect rendering
  private val fboMain2D = GlFramebuffer()
  private val fboOverlayMap = mutableMapOf<String, GlFramebuffer>()
  private val fboA = GlFramebuffer()
  private val fboB = GlFramebuffer()

  // Cached Text & Sticker textures with frame-access tracking
  private data class CachedTexture(
    val texId: Int,
    val width: Int,
    val height: Int,
    val hash: Int,
    var lastFrameUsed: Long = 0L
  )

  private val textTextureCache = mutableMapOf<String, CachedTexture>()
  private val stickerTextureCache = mutableMapOf<String, CachedTexture>()
  private val imageTextureCache = mutableMapOf<String, CachedTexture>()

  private var currentFrameCounter = 0L

  // Reusable Matrix buffers
  private val mvpMatrix = FloatArray(16)
  private val texMatrix = FloatArray(16)

  private var isInitialized = false
  private var currentViewportWidth = 0
  private var currentViewportHeight = 0

  fun initGl() {
    if (isInitialized) return

    program2D = GlShaderUtil.createProgram(GpuShaders.VERTEX_SHADER, GpuShaders.buildFragmentShader(isOes = false))
    programOes = GlShaderUtil.createProgram(GpuShaders.VERTEX_SHADER, GpuShaders.buildFragmentShader(isOes = true))
    programTransition = GlShaderUtil.createProgram(GpuShaders.VERTEX_SHADER, GpuShaders.TRANSITION_FRAGMENT_SHADER)
    programEffect = GlShaderUtil.createProgram(GpuShaders.VERTEX_SHADER, GpuShaders.EFFECT_FRAGMENT_SHADER)

    isInitialized = true
  }

  /**
   * Main GPU composition entry point:
   * Converts OES video frames, prepares text/sticker/overlay textures, converts layer models into
   * native C++ layer representations, and renders the composed frame via NativeRenderBridge.
   */
  fun render(
    frame: ComposedFrame,
    mainTextureId: Int,
    isMainOes: Boolean,
    mainTexMatrix: FloatArray? = null,
    overlayTextures: Map<String, Int> = emptyMap(),
    viewportWidth: Int,
    viewportHeight: Int,
    timelineAdjustments: VideoAdjustments = VideoAdjustments(),
    timelineFilter: FilterSettings = FilterSettings(),
    chromaKey: ChromaKeySettings = ChromaKeySettings()
  ) {
    if (viewportWidth <= 0 || viewportHeight <= 0) return

    if (!isInitialized) {
      initGl()
    }

    currentFrameCounter++

    // Initialize or resize native C++ OpenGL ES 3.0 renderer
    if (viewportWidth != currentViewportWidth || viewportHeight != currentViewportHeight) {
      currentViewportWidth = viewportWidth
      currentViewportHeight = viewportHeight
      NativeRenderBridge.init(viewportWidth, viewportHeight)
    }

    val nativeLayers = mutableListOf<NativeLayer>()

    // 1. Process Main Base Video Clip
    if (mainTextureId > 0) {
      val main2dTexId = processMainVideoTo2D(
        frame = frame,
        textureId = mainTextureId,
        isOes = isMainOes,
        customTexMatrix = mainTexMatrix,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        adjustments = timelineAdjustments,
        filter = timelineFilter,
        chromaKey = chromaKey
      )

      if (main2dTexId > 0) {
        val baseLayer = NativeLayer(
          id = frame.activeClip?.id?.hashCode()?.toLong() ?: 1L,
          textureId = main2dTexId,
          type = NativeLayerType.BASE_VIDEO,
          isVisible = true,
          zOrder = 0,
          opacity = 1.0f,
          blendMode = NativeBlendMode.NORMAL,
          useCustomMatrix = false
        )
        nativeLayers.add(baseLayer)
      }
    }

    // 2. Process PIP Overlays (Deterministically ordered)
    for (i in frame.activeOverlays.indices) {
      val overlay = frame.activeOverlays[i]
      val overlayTexId = overlayTextures[overlay.clip.id]
      if (overlayTexId != null && overlayTexId > 0) {
        val ov2dTexId = processOverlayVideoTo2D(
          overlay = overlay,
          textureId = overlayTexId,
          viewportWidth = viewportWidth,
          viewportHeight = viewportHeight,
          chromaKey = chromaKey
        )

        if (ov2dTexId > 0) {
          val ovW = if (overlay.clip.width > 0) overlay.clip.width else viewportWidth
          val ovH = if (overlay.clip.height > 0) overlay.clip.height else viewportHeight
          val ovAspect = ovW.toFloat() / max(1, ovH)
          val vpAspect = viewportWidth.toFloat() / max(1, viewportHeight)

          val baseScaleY = (overlay.scaleY * 0.5f).coerceAtLeast(0.01f)
          val baseScaleX = (baseScaleY * (ovAspect / vpAspect)).coerceAtLeast(0.01f)

          val ovMatrix = FloatArray(16)
          Matrix.setIdentityM(ovMatrix, 0)
          Matrix.translateM(ovMatrix, 0, overlay.posX, -overlay.posY, 0f)
          Matrix.rotateM(ovMatrix, 0, -overlay.rotation, 0f, 0f, 1f)
          Matrix.scaleM(ovMatrix, 0, baseScaleX, baseScaleY, 1f)

          val calculatedZ = 100 + (i * 10)
          val overlayLayer = NativeLayer(
            id = overlay.clip.id.hashCode().toLong(),
            textureId = ov2dTexId,
            type = NativeLayerType.VIDEO,
            isVisible = true,
            zOrder = calculatedZ,
            opacity = overlay.opacity.coerceIn(0f, 1f),
            blendMode = mapBlendMode(overlay.blendMode),
            useCustomMatrix = true,
            transformMatrix = ovMatrix
          )
          nativeLayers.add(overlayLayer)
        }
      }
    }

    // 3. Process Sticker Layers (Deterministically ordered)
    for (i in frame.activeStickers.indices) {
      val sticker = frame.activeStickers[i]
      val cached = getOrCreateStickerTexture(sticker.clip, viewportWidth, viewportHeight)
      if (cached != null && cached.texId > 0) {
        cached.lastFrameUsed = currentFrameCounter
        val aspect = viewportWidth.toFloat() / max(1, viewportHeight)
        val stickerAspect = cached.width.toFloat() / max(1, cached.height)
        val scaleY = ((cached.height.toFloat() / viewportHeight) * 2f * sticker.scale).coerceAtLeast(0.01f)
        val scaleX = (scaleY * stickerAspect / aspect).coerceAtLeast(0.01f)

        val stkMatrix = FloatArray(16)
        Matrix.setIdentityM(stkMatrix, 0)
        Matrix.translateM(stkMatrix, 0, sticker.posX, -sticker.posY, 0f)
        Matrix.rotateM(stkMatrix, 0, -sticker.rotation, 0f, 0f, 1f)
        Matrix.scaleM(stkMatrix, 0, scaleX, scaleY, 1f)

        val calculatedZ = 500 + (i * 10)
        val stickerLayer = NativeLayer(
          id = sticker.clip.id.hashCode().toLong(),
          textureId = cached.texId,
          type = NativeLayerType.IMAGE_STICKER,
          isVisible = true,
          zOrder = calculatedZ,
          opacity = sticker.opacity.coerceIn(0f, 1f),
          vScale = -1.0f,
          vOffset = 1.0f,
          blendMode = NativeBlendMode.PREMULTIPLIED,
          useCustomMatrix = true,
          transformMatrix = stkMatrix
        )
        nativeLayers.add(stickerLayer)
      }
    }

    // 4. Process Text Layers (Deterministically ordered with highest Z-Order to guarantee visibility)
    for (i in frame.activeTexts.indices) {
      val text = frame.activeTexts[i]
      val cached = getOrCreateTextTexture(text.clip, text.currentPosMs, viewportWidth, viewportHeight)
      if (cached != null && cached.texId > 0) {
        cached.lastFrameUsed = currentFrameCounter
        val aspect = viewportWidth.toFloat() / max(1, viewportHeight)
        val txtAspect = cached.width.toFloat() / max(1, cached.height)
        val scaleY = ((cached.height.toFloat() / viewportHeight) * 2f * text.scale).coerceAtLeast(0.01f)
        val scaleX = (scaleY * txtAspect / aspect).coerceAtLeast(0.01f)

        val txtMatrix = FloatArray(16)
        Matrix.setIdentityM(txtMatrix, 0)
        Matrix.translateM(txtMatrix, 0, text.posX, -text.posY, 0f)
        Matrix.rotateM(txtMatrix, 0, -text.rotation, 0f, 0f, 1f)
        Matrix.scaleM(txtMatrix, 0, scaleX, scaleY, 1f)

        val calculatedZ = 1000 + (text.clip.trackIndex * 10) + i
        val textLayer = NativeLayer(
          id = text.clip.id.hashCode().toLong(),
          textureId = cached.texId,
          type = NativeLayerType.TEXT,
          isVisible = true,
          zOrder = calculatedZ,
          opacity = text.opacity.coerceIn(0f, 1f),
          vScale = -1.0f,
          vOffset = 1.0f,
          blendMode = NativeBlendMode.PREMULTIPLIED,
          useCustomMatrix = true,
          transformMatrix = txtMatrix
        )
        nativeLayers.add(textLayer)
      }
    }

    // 5. Clean up stale textures periodically
    if (currentFrameCounter % 30L == 0L) {
      cleanStaleTextureCaches()
    }

    // 6. Render via Native C++ OpenGL ES 3.0 Engine
    val hasEffects = frame.activeEffects.isNotEmpty()
    if (hasEffects) {
      NativeRenderBridge.beginOffscreen()
    } else {
      GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
      if (chromaKey.enabled && chromaKey.backgroundType == "Transparent") {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
      } else {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
      }
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    NativeRenderBridge.renderFrame(nativeLayers)

    // 7. Apply Active Visual Effects (Multi-pass ping-ponging)
    if (hasEffects) {
      val offscreenTex = NativeRenderBridge.endOffscreen()
      if (offscreenTex > 0) {
        fboA.setup(viewportWidth, viewportHeight)
        fboB.setup(viewportWidth, viewportHeight)

        var currentInputTex = offscreenTex
        var currentOutputFbo = fboB

        for (i in frame.activeEffects.indices) {
          val effect = frame.activeEffects[i]
          val isLast = (i == frame.activeEffects.size - 1)

          if (isLast) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
            applyEffect(
              effectType = effect.effectType,
              intensity = effect.intensity,
              timeSec = effect.timeInEffectMs / 1000f,
              inputTexId = currentInputTex,
              viewportWidth = viewportWidth,
              viewportHeight = viewportHeight
            )
          } else {
            currentOutputFbo.bind()
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            applyEffect(
              effectType = effect.effectType,
              intensity = effect.intensity,
              timeSec = effect.timeInEffectMs / 1000f,
              inputTexId = currentInputTex,
              viewportWidth = viewportWidth,
              viewportHeight = viewportHeight
            )
            currentOutputFbo.unbind()
            currentInputTex = currentOutputFbo.getTextureId()
            currentOutputFbo = if (currentOutputFbo == fboB) fboA else fboB
          }
        }
      }
    }
  }

  private fun processMainVideoTo2D(
    frame: ComposedFrame,
    textureId: Int,
    isOes: Boolean,
    customTexMatrix: FloatArray?,
    viewportWidth: Int,
    viewportHeight: Int,
    adjustments: VideoAdjustments,
    filter: FilterSettings,
    chromaKey: ChromaKeySettings
  ): Int {
    fboMain2D.setup(viewportWidth, viewportHeight)
    fboMain2D.bind()

    GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

    val program = if (isOes) programOes else program2D
    GLES20.glUseProgram(program)

    Matrix.setIdentityM(mvpMatrix, 0)
    val clip = frame.activeClip
    var keyframeBlur = 0f
    var keyframeEffectParam = 0f
    var finalAdjustments = adjustments
    var finalOpacity = 1.0f

    if (clip != null) {
      val kf = frame.activeClipTransform ?: KeyframeInterpolator.interpolate(clip, frame.timelinePosMs - clip.timelineStartMs)

      val cachedMain = imageTextureCache.values.find { it.texId == textureId }
      val texW = cachedMain?.width ?: if (clip.width > 0) clip.width else viewportWidth
      val texH = cachedMain?.height ?: if (clip.height > 0) clip.height else viewportHeight
      val texAspect = texW.toFloat() / max(1, texH)
      val vpAspect = viewportWidth.toFloat() / max(1, viewportHeight)

      val baseScaleX: Float
      val baseScaleY: Float
      if (texAspect > vpAspect) {
        baseScaleX = 1.0f
        baseScaleY = vpAspect / texAspect
      } else {
        baseScaleX = texAspect / vpAspect
        baseScaleY = 1.0f
      }

      val scaleX = baseScaleX * (if (clip.flipHorizontal) -clip.cropScale else clip.cropScale) * kf.scaleX
      val scaleY = baseScaleY * (if (clip.flipVertical) -clip.cropScale else clip.cropScale) * kf.scaleY

      Matrix.translateM(mvpMatrix, 0, clip.cropOffsetX + kf.posX, -(clip.cropOffsetY + kf.posY), 0f)
      Matrix.rotateM(mvpMatrix, 0, -((clip.rotationDegrees.toFloat() + kf.rotation) % 360f), 0f, 0f, 1f)
      Matrix.scaleM(mvpMatrix, 0, scaleX, scaleY, 1f)

      finalOpacity *= kf.opacity
      keyframeBlur = kf.blur
      keyframeEffectParam = kf.effectParam
      finalAdjustments = adjustments.copy(
        brightness = (adjustments.brightness + kf.brightness).coerceIn(-1f, 1f),
        contrast = (adjustments.contrast * kf.contrast).coerceAtLeast(0f),
        saturation = (adjustments.saturation * kf.saturation).coerceAtLeast(0f)
      )
    }

    if (customTexMatrix != null) {
      System.arraycopy(customTexMatrix, 0, texMatrix, 0, 16)
    } else {
      Matrix.setIdentityM(texMatrix, 0)
    }

    if (frame.activeTransition != null) {
      val tr = frame.activeTransition
      when (tr.type) {
        TransitionType.FADE -> {
          finalOpacity = (finalOpacity * (1.0f - tr.progress)).coerceIn(0f, 1f)
        }
        TransitionType.SLIDE_LEFT -> {
          Matrix.translateM(mvpMatrix, 0, -tr.progress * 2.0f, 0f, 0f)
        }
        TransitionType.ZOOM_IN -> {
          val zoom = 1.0f + tr.progress * 0.5f
          Matrix.scaleM(mvpMatrix, 0, zoom, zoom, 1f)
        }
        else -> {}
      }
    }

    val effectiveFilter = clip?.filter ?: FilterSettings()
    bindCommonUniforms(
      program = program,
      textureId = textureId,
      isOes = isOes,
      opacity = finalOpacity,
      adjustments = finalAdjustments,
      filter = effectiveFilter,
      chromaKey = chromaKey,
      viewportWidth = viewportWidth,
      viewportHeight = viewportHeight,
      blur = keyframeBlur,
      effectParam = keyframeEffectParam
    )

    drawQuad(program)
    fboMain2D.unbind()

    return fboMain2D.getTextureId()
  }

  private fun processOverlayVideoTo2D(
    overlay: ComposedOverlay,
    textureId: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    chromaKey: ChromaKeySettings
  ): Int {
    val fbo = fboOverlayMap.getOrPut(overlay.clip.id) { GlFramebuffer() }
    fbo.setup(viewportWidth, viewportHeight)
    fbo.bind()

    GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

    val program = program2D
    GLES20.glUseProgram(program)

    Matrix.setIdentityM(mvpMatrix, 0)
    Matrix.setIdentityM(texMatrix, 0)

    val overlayAdj = VideoAdjustments(
      brightness = overlay.brightness,
      contrast = overlay.contrast,
      saturation = overlay.saturation
    )

    bindCommonUniforms(
      program = program,
      textureId = textureId,
      isOes = false,
      opacity = 1.0f,
      adjustments = overlayAdj,
      filter = overlay.clip.filter ?: FilterSettings(),
      chromaKey = chromaKey,
      viewportWidth = viewportWidth,
      viewportHeight = viewportHeight,
      blur = overlay.blur,
      effectParam = overlay.effectParam
    )

    drawQuad(program)
    fbo.unbind()

    return fbo.getTextureId()
  }

  private fun getOrCreateTextTexture(
    clip: TextClip,
    currentPosMs: Long,
    viewportWidth: Int,
    viewportHeight: Int
  ): CachedTexture? {
    val hasAnim = clip.animationType != "None" || clip.animation3D != "None"
    val animTimeStep = if (hasAnim) (currentPosMs / 33L).toInt() else 0

    val hash = clip.text.hashCode() xor
        clip.textColor.toInt() xor
        clip.fontSizeSp.toInt() xor
        clip.fontWeight.hashCode() xor
        clip.backgroundColor.toInt() xor
        clip.strokeColor.toInt() xor
        clip.strokeWidth.toInt() xor
        clip.fontFamily.hashCode() xor
        clip.animationType.hashCode() xor
        clip.animation3D.hashCode() xor
        clip.effectStyle.hashCode() xor
        clip.depth3D.toInt() xor
        clip.bevelAngle3D.toInt() xor
        clip.is3D.hashCode() xor
        clip.hasGradient.hashCode() xor
        clip.gradientColorStart.toInt() xor
        clip.hasGlow.hashCode() xor
        clip.hasShadow.hashCode() xor
        clip.opacity.hashCode() xor
        clip.scale.hashCode() xor
        animTimeStep xor
        viewportWidth

    val cached = textTextureCache[clip.id]
    if (cached != null && cached.hash == hash && cached.texId > 0) {
      return cached
    }

    val bitmap = TextLayerRenderer.renderToBitmap(
      clip = clip,
      currentPosMs = currentPosMs,
      width = viewportWidth,
      height = viewportHeight,
      context = context
    ) ?: return null

    val oldTexId = if (cached != null && cached.hash != hash) cached.texId else 0
    val texId = GlShaderUtil.uploadBitmapToTexture(bitmap, oldTexId)
    bitmap.recycle()

    if (texId == 0) return null
    val entry = CachedTexture(texId, bitmap.width, bitmap.height, hash, currentFrameCounter)
    textTextureCache[clip.id] = entry
    return entry
  }

  private fun getOrCreateStickerTexture(
    clip: StickerClip,
    viewportWidth: Int,
    viewportHeight: Int
  ): CachedTexture? {
    val hash = clip.emojiOrAsset.hashCode() xor clip.badgeType.hashCode() xor viewportWidth
    val cached = stickerTextureCache[clip.id]
    if (cached != null && cached.hash == hash && cached.texId > 0) {
      return cached
    }

    val isBadge = clip.badgeType != null
    val targetWidth = if (isBadge) {
      (160f * (viewportWidth.toFloat() / 600f)).toInt().coerceIn(128, 384)
    } else {
      (80f * (viewportWidth.toFloat() / 600f)).toInt().coerceIn(64, 256)
    }
    val targetHeight = if (isBadge) {
      (targetWidth * 0.42f).toInt().coerceIn(54, 160)
    } else {
      targetWidth
    }

    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    StickerLayerRenderer.draw(
      canvas = canvas,
      clip = clip.copy(posX = 0f, posY = 0f, scale = 1f, rotation = 0f, opacity = 1f),
      currentPosMs = clip.timelineStartMs,
      width = targetWidth,
      height = targetHeight
    )

    val oldTexId = cached?.texId ?: 0
    val texId = GlShaderUtil.uploadBitmapToTexture(bitmap, oldTexId)
    bitmap.recycle()

    if (texId == 0) return null
    val entry = CachedTexture(texId, targetWidth, targetHeight, hash, currentFrameCounter)
    stickerTextureCache[clip.id] = entry
    return entry
  }

  fun uploadImageTexture(id: String, bitmap: Bitmap): Int {
    val existing = imageTextureCache[id]
    if (existing != null && existing.hash == bitmap.generationId && existing.width == bitmap.width && existing.height == bitmap.height) {
      existing.lastFrameUsed = currentFrameCounter
      return existing.texId
    }
    val texId = GlShaderUtil.uploadBitmapToTexture(bitmap, existing?.texId ?: 0)
    val entry = CachedTexture(texId, bitmap.width, bitmap.height, bitmap.generationId, currentFrameCounter)
    imageTextureCache[id] = entry
    return texId
  }

  private fun cleanStaleTextureCaches() {
    val textToDel = textTextureCache.filter { currentFrameCounter - it.value.lastFrameUsed > MAX_UNTOUCHED_CACHE_FRAMES }
    for ((id, tex) in textToDel) {
      if (tex.texId > 0) {
        GLES20.glDeleteTextures(1, intArrayOf(tex.texId), 0)
      }
      textTextureCache.remove(id)
    }

    val stickerToDel = stickerTextureCache.filter { currentFrameCounter - it.value.lastFrameUsed > MAX_UNTOUCHED_CACHE_FRAMES }
    for ((id, tex) in stickerToDel) {
      if (tex.texId > 0) {
        GLES20.glDeleteTextures(1, intArrayOf(tex.texId), 0)
      }
      stickerTextureCache.remove(id)
    }
  }

  private fun mapBlendMode(modeStr: String): NativeBlendMode {
    return when (modeStr.lowercase().trim()) {
      "screen" -> NativeBlendMode.SCREEN
      "multiply" -> NativeBlendMode.MULTIPLY
      "add", "additive" -> NativeBlendMode.ADDITIVE
      "premultiplied" -> NativeBlendMode.PREMULTIPLIED
      else -> NativeBlendMode.NORMAL
    }
  }

  private fun bindCommonUniforms(
    program: Int,
    textureId: Int,
    isOes: Boolean,
    opacity: Float,
    adjustments: VideoAdjustments,
    filter: FilterSettings,
    chromaKey: ChromaKeySettings,
    viewportWidth: Int,
    viewportHeight: Int,
    blur: Float = 0f,
    effectParam: Float = 0f
  ) {
    val uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    val uTexMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
    val uTextureHandle = GLES20.glGetUniformLocation(program, "uTexture")
    val uOpacityHandle = GLES20.glGetUniformLocation(program, "uOpacity")
    val uBlurHandle = GLES20.glGetUniformLocation(program, "uBlur")
    val uEffectParamHandle = GLES20.glGetUniformLocation(program, "uEffectParam")

    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
    GLES20.glUniformMatrix4fv(uTexMatrixHandle, 1, false, texMatrix, 0)
    GLES20.glUniform1f(uOpacityHandle, opacity)
    if (uBlurHandle >= 0) GLES20.glUniform1f(uBlurHandle, blur)
    if (uEffectParamHandle >= 0) GLES20.glUniform1f(uEffectParamHandle, effectParam)

    val target = if (isOes) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(target, textureId)
    GLES20.glUniform1i(uTextureHandle, 0)

    val uBrightnessHandle = GLES20.glGetUniformLocation(program, "uBrightness")
    val uContrastHandle = GLES20.glGetUniformLocation(program, "uContrast")
    val uSaturationHandle = GLES20.glGetUniformLocation(program, "uSaturation")
    val uExposureHandle = GLES20.glGetUniformLocation(program, "uExposure")
    val uTemperatureHandle = GLES20.glGetUniformLocation(program, "uTemperature")
    val uTintHandle = GLES20.glGetUniformLocation(program, "uTint")
    val uHighlightsHandle = GLES20.glGetUniformLocation(program, "uHighlights")
    val uShadowsHandle = GLES20.glGetUniformLocation(program, "uShadows")
    val uVignetteHandle = GLES20.glGetUniformLocation(program, "uVignette")
    val uGrainHandle = GLES20.glGetUniformLocation(program, "uGrain")
    val uSharpnessHandle = GLES20.glGetUniformLocation(program, "uSharpness")
    val uTexelSizeHandle = GLES20.glGetUniformLocation(program, "uTexelSize")

    if (uBrightnessHandle >= 0) GLES20.glUniform1f(uBrightnessHandle, adjustments.brightness)
    if (uContrastHandle >= 0) GLES20.glUniform1f(uContrastHandle, adjustments.contrast)
    if (uSaturationHandle >= 0) GLES20.glUniform1f(uSaturationHandle, adjustments.saturation)
    if (uExposureHandle >= 0) GLES20.glUniform1f(uExposureHandle, adjustments.exposure)
    if (uTemperatureHandle >= 0) GLES20.glUniform1f(uTemperatureHandle, adjustments.temperature)
    if (uTintHandle >= 0) GLES20.glUniform1f(uTintHandle, adjustments.tint)
    if (uHighlightsHandle >= 0) GLES20.glUniform1f(uHighlightsHandle, adjustments.highlights)
    if (uShadowsHandle >= 0) GLES20.glUniform1f(uShadowsHandle, adjustments.shadows)
    if (uVignetteHandle >= 0) GLES20.glUniform1f(uVignetteHandle, adjustments.vignette)
    if (uGrainHandle >= 0) GLES20.glUniform1f(uGrainHandle, adjustments.grain)
    if (uSharpnessHandle >= 0) GLES20.glUniform1f(uSharpnessHandle, adjustments.sharpness)
    if (uTexelSizeHandle >= 0) GLES20.glUniform2f(uTexelSizeHandle, 1.0f / max(1, viewportWidth), 1.0f / max(1, viewportHeight))

    val uChromaEnabledHandle = GLES20.glGetUniformLocation(program, "uChromaEnabled")
    if (uChromaEnabledHandle >= 0) {
      if (chromaKey.enabled) {
        val color = chromaKey.targetColor.toInt()
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f

        GLES20.glUniform1i(uChromaEnabledHandle, 1)
        val keyLoc = GLES20.glGetUniformLocation(program, "uChromaKeyColor").let { if (it >= 0) it else GLES20.glGetUniformLocation(program, "uKeyColor") }
        if (keyLoc >= 0) {
          GLES20.glUniform3f(keyLoc, r, g, b)
        }
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uChromaSimilarity"), chromaKey.similarity)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uChromaSmoothness"), max(0.001f, chromaKey.smoothness))
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uChromaSpill"), chromaKey.spillSuppression)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uChromaEdge"), chromaKey.edgeControl)

        val bgType = if (chromaKey.backgroundType == "SolidColor") 1 else 0
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uChromaBgType"), bgType)
        val bgColor = chromaKey.backgroundColor.toInt()
        val bgR = Color.red(bgColor) / 255f
        val bgG = Color.green(bgColor) / 255f
        val bgB = Color.blue(bgColor) / 255f
        val bgA = Color.alpha(bgColor) / 255f
        GLES20.glUniform4f(GLES20.glGetUniformLocation(program, "uChromaBgColor"), bgR, bgG, bgB, bgA)
      } else {
        GLES20.glUniform1i(uChromaEnabledHandle, 0)
      }
    }

    val uColorMatrixHandle = GLES20.glGetUniformLocation(program, "uColorMatrix")
    val uColorOffsetHandle = GLES20.glGetUniformLocation(program, "uColorOffset")
    val uUseColorMatrixHandle = GLES20.glGetUniformLocation(program, "uUseColorMatrix")

    val filterMatrix = com.example.engine.composition.ColorFilterGenerator.getFilterMatrix(filter.type, filter.intensity)
    if (filterMatrix != null && uUseColorMatrixHandle >= 0) {
      val arr = filterMatrix.array
      val glMat = floatArrayOf(
        arr[0], arr[5], arr[10], arr[15],
        arr[1], arr[6], arr[11], arr[16],
        arr[2], arr[7], arr[12], arr[17],
        arr[3], arr[8], arr[13], arr[18]
      )
      val glOffset = floatArrayOf(
        arr[4] / 255.0f,
        arr[9] / 255.0f,
        arr[14] / 255.0f,
        arr[19] / 255.0f
      )
      if (uColorMatrixHandle >= 0) GLES20.glUniformMatrix4fv(uColorMatrixHandle, 1, false, glMat, 0)
      if (uColorOffsetHandle >= 0) GLES20.glUniform4fv(uColorOffsetHandle, 1, glOffset, 0)
      GLES20.glUniform1i(uUseColorMatrixHandle, 1)
    } else if (uUseColorMatrixHandle >= 0) {
      GLES20.glUniform1i(uUseColorMatrixHandle, 0)
    }
  }

  private fun drawQuad(program: Int) {
    val aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
    val aTextureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")

    vertexBuffer.position(POSITION_DATA_OFFSET)
    GLES20.glVertexAttribPointer(
      aPositionHandle, 2, GLES20.GL_FLOAT, false,
      TRIANGLE_VERTICES_DATA_STRIDE_BYTES, vertexBuffer
    )
    GLES20.glEnableVertexAttribArray(aPositionHandle)

    vertexBuffer.position(TEXTURE_DATA_OFFSET)
    GLES20.glVertexAttribPointer(
      aTextureCoordHandle, 2, GLES20.GL_FLOAT, false,
      TRIANGLE_VERTICES_DATA_STRIDE_BYTES, vertexBuffer
    )
    GLES20.glEnableVertexAttribArray(aTextureCoordHandle)

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

    GLES20.glDisableVertexAttribArray(aPositionHandle)
    GLES20.glDisableVertexAttribArray(aTextureCoordHandle)
  }

  fun onContextLost() {
    isInitialized = false
    textTextureCache.clear()
    stickerTextureCache.clear()
    imageTextureCache.clear()
    fboOverlayMap.clear()
    NativeRenderBridge.onContextLost()
  }

  fun release() {
    fboMain2D.release()
    fboA.release()
    fboB.release()

    for (fbo in fboOverlayMap.values) {
      fbo.release()
    }
    fboOverlayMap.clear()

    val texturesToDelete = mutableListOf<Int>()
    for (t in textTextureCache.values) texturesToDelete.add(t.texId)
    for (s in stickerTextureCache.values) texturesToDelete.add(s.texId)
    for (i in imageTextureCache.values) texturesToDelete.add(i.texId)

    if (texturesToDelete.isNotEmpty()) {
      GLES20.glDeleteTextures(texturesToDelete.size, texturesToDelete.toIntArray(), 0)
    }
    textTextureCache.clear()
    stickerTextureCache.clear()
    imageTextureCache.clear()

    if (program2D != 0) {
      GLES20.glDeleteProgram(program2D)
      program2D = 0
    }
    if (programOes != 0) {
      GLES20.glDeleteProgram(programOes)
      programOes = 0
    }
    if (programTransition != 0) {
      GLES20.glDeleteProgram(programTransition)
      programTransition = 0
    }
    if (programEffect != 0) {
      GLES20.glDeleteProgram(programEffect)
      programEffect = 0
    }

    isInitialized = false
    NativeRenderBridge.release()
    Log.d(TAG, "GpuCompositionRenderer & Native Engine cleanly released")
  }

  private fun applyEffect(
    effectType: EffectType,
    intensity: Float,
    timeSec: Float,
    inputTexId: Int,
    viewportWidth: Int,
    viewportHeight: Int
  ) {
    if (programEffect == 0) return
    GLES20.glUseProgram(programEffect)

    val uTextureHandle = GLES20.glGetUniformLocation(programEffect, "uTexture")
    val uEffectTypeHandle = GLES20.glGetUniformLocation(programEffect, "uEffectType")
    val uIntensityHandle = GLES20.glGetUniformLocation(programEffect, "uIntensity")
    val uTimeHandle = GLES20.glGetUniformLocation(programEffect, "uTime")
    val uTexelSizeHandle = GLES20.glGetUniformLocation(programEffect, "uTexelSize")

    val glEffectType = when (effectType) {
      EffectType.BLUR -> GpuShaders.EFFECT_BLUR
      EffectType.GLOW -> GpuShaders.EFFECT_GLOW
      EffectType.MOTION_BLUR -> GpuShaders.EFFECT_MOTION_BLUR
      EffectType.SHAKE -> GpuShaders.EFFECT_SHAKE
      EffectType.ZOOM -> GpuShaders.EFFECT_ZOOM
      EffectType.SPIN -> GpuShaders.EFFECT_SPIN
      EffectType.FLASH -> GpuShaders.EFFECT_FLASH
      EffectType.GLITCH -> GpuShaders.EFFECT_GLITCH
      EffectType.RGB_SPLIT -> GpuShaders.EFFECT_RGB_SPLIT
      EffectType.DISTORTION, EffectType.WAVE, EffectType.RIPPLE,
      EffectType.VFX_GLITCH_4, EffectType.VFX_GLITCH_5, EffectType.VFX_GLITCH_15,
      EffectType.VFX_VIRAL_8 -> GpuShaders.EFFECT_DISTORTION
      EffectType.LENS_FLARE, EffectType.VFX_LIGHT_2 -> GpuShaders.EFFECT_LENS_FLARE
      EffectType.LIGHT_LEAK, EffectType.VFX_LIGHT_1, EffectType.VFX_LIGHT_3,
      EffectType.VFX_LIGHT_5, EffectType.VFX_VIRAL_24 -> GpuShaders.EFFECT_LIGHT_LEAK
      EffectType.VFX_VIRAL_5, EffectType.VFX_VIRAL_29 -> GpuShaders.EFFECT_RGB_SPLIT
      EffectType.VFX_VIRAL_6, EffectType.VFX_VIRAL_15, EffectType.VFX_GLITCH_14 -> GpuShaders.EFFECT_SHAKE
      EffectType.VFX_VIRAL_11, EffectType.VFX_VIRAL_36 -> GpuShaders.EFFECT_FLASH
      EffectType.VFX_VIRAL_14, EffectType.VFX_BLUR_2, EffectType.VFX_BLUR_5 -> GpuShaders.EFFECT_MOTION_BLUR
      EffectType.VFX_VIRAL_16, EffectType.VFX_VIRAL_17, EffectType.VFX_VIRAL_35 -> GpuShaders.EFFECT_ZOOM
      EffectType.VFX_VIRAL_21, EffectType.VFX_VIRAL_22, EffectType.VFX_LIGHT_7,
      EffectType.VFX_LIGHT_14, EffectType.VFX_LIGHT_19 -> GpuShaders.EFFECT_GLOW
      EffectType.VFX_BLUR_1, EffectType.VFX_BLUR_3, EffectType.VFX_BLUR_8,
      EffectType.VFX_BLUR_9, EffectType.VFX_BLUR_11, EffectType.VFX_BLUR_12,
      EffectType.VFX_BLUR_13, EffectType.VFX_BLUR_15 -> GpuShaders.EFFECT_BLUR
      else -> GpuShaders.EFFECT_BLUR
    }

    if (uEffectTypeHandle >= 0) GLES20.glUniform1i(uEffectTypeHandle, glEffectType)
    if (uIntensityHandle >= 0) GLES20.glUniform1f(uIntensityHandle, intensity)
    if (uTimeHandle >= 0) GLES20.glUniform1f(uTimeHandle, timeSec)
    if (uTexelSizeHandle >= 0) GLES20.glUniform2f(uTexelSizeHandle, 1.0f / max(1, viewportWidth), 1.0f / max(1, viewportHeight))

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexId)
    if (uTextureHandle >= 0) GLES20.glUniform1i(uTextureHandle, 0)

    drawQuad(programEffect)
  }

  fun invalidateClip(clipId: String) {
    textTextureCache.remove(clipId)?.let {
      if (it.texId > 0) {
        GLES20.glDeleteTextures(1, intArrayOf(it.texId), 0)
      }
    }
    stickerTextureCache.remove(clipId)?.let {
      if (it.texId > 0) {
        GLES20.glDeleteTextures(1, intArrayOf(it.texId), 0)
      }
    }
    imageTextureCache.remove(clipId)?.let {
      if (it.texId > 0) {
        GLES20.glDeleteTextures(1, intArrayOf(it.texId), 0)
      }
    }
  }

  fun invalidateAll() {
    for ((_, item) in textTextureCache) {
      if (item.texId > 0) GLES20.glDeleteTextures(1, intArrayOf(item.texId), 0)
    }
    textTextureCache.clear()
    for ((_, item) in stickerTextureCache) {
      if (item.texId > 0) GLES20.glDeleteTextures(1, intArrayOf(item.texId), 0)
    }
    stickerTextureCache.clear()
    for ((_, item) in imageTextureCache) {
      if (item.texId > 0) GLES20.glDeleteTextures(1, intArrayOf(item.texId), 0)
    }
    imageTextureCache.clear()
  }
}
