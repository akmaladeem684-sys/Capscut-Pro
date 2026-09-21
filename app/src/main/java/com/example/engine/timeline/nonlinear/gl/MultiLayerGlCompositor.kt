package com.example.engine.timeline.nonlinear.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import com.example.engine.composition.gpu.EglCore
import com.example.engine.composition.gpu.WindowSurface
import com.example.engine.timeline.nonlinear.compositor.RenderLayer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Texture source holder: either a standard 2D texture (FBO/Bitmap) or an OES External Texture (MediaCodec/Video decoder).
 */
data class LayerTextureSource(
  val textureId: Int,
  val isOes: Boolean = false,
  val texMatrix: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f }
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LayerTextureSource) return false
    return textureId == other.textureId && isOes == other.isOes
  }

  override fun hashCode(): Int = 31 * textureId + isOes.hashCode()
}

/**
 * Hardware-Accelerated Multi-Layer OpenGL ES 3.0 Compositor for Non-Linear Timeline rendering and export.
 *
 * Capabilities:
 * - Multi-Pass Framebuffer Objects (FBO) for offscreen compositing and multi-layer blending.
 * - Vertex & Fragment shaders supporting both standard 2D textures and samplerExternalOES.
 * - Hardware MVP matrix transformations for translation, scaling, rotation, and aspect ratio adaptation.
 * - Direct zero-copy rendering onto MediaCodec.createInputSurface() with eglPresentationTimeANDROID.
 */
class MultiLayerGlCompositor(
  val width: Int,
  val height: Int
) {
  companion object {
    private const val TAG = "MultiLayerGlCompositor"

    private val FULL_QUAD_COORDS = floatArrayOf(
      -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
       1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
      -1.0f,  1.0f, 0.0f, 0.0f, 1.0f,
       1.0f,  1.0f, 0.0f, 1.0f, 1.0f
    )

    private const val VERTEX_SHADER_ES3 = """#version 300 es
      layout(location = 0) in vec4 aPosition;
      layout(location = 1) in vec4 aTextureCoord;
      uniform mat4 uMvpMatrix;
      uniform mat4 uTexMatrix;
      out vec2 vTextureCoord;
      void main() {
        gl_Position = uMvpMatrix * aPosition;
        vTextureCoord = (uTexMatrix * aTextureCoord).xy;
      }
    """

    private const val FRAGMENT_SHADER_2D_ES3 = """#version 300 es
      precision mediump float;
      in vec2 vTextureCoord;
      uniform sampler2D uTexture;
      uniform float uAlpha;
      out vec4 fragColor;
      void main() {
        vec4 color = texture(uTexture, vTextureCoord);
        fragColor = vec4(color.rgb, color.a * uAlpha);
      }
    """

    private const val FRAGMENT_SHADER_OES_ES3 = """#version 300 es
      #extension GL_OES_EGL_image_external_essl3 : require
      precision mediump float;
      in vec2 vTextureCoord;
      uniform samplerExternalOES uTexture;
      uniform float uAlpha;
      out vec4 fragColor;
      void main() {
        vec4 color = texture(uTexture, vTextureCoord);
        fragColor = vec4(color.rgb, color.a * uAlpha);
      }
    """
  }

  private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(FULL_QUAD_COORDS.size * 4)
    .order(ByteOrder.nativeOrder())
    .asFloatBuffer()
    .apply {
      put(FULL_QUAD_COORDS)
      position(0)
    }

  private var program2D = 0
  private var programOes = 0

  private var fboId = 0
  private var fboTextureId = 0

  private val identityMatrix = FloatArray(16) { if (it % 5 == 0) 1f else 0f }

  init {
    initPrograms()
    initFbo()
  }

  private fun initPrograms() {
    program2D = createProgram(VERTEX_SHADER_ES3, FRAGMENT_SHADER_2D_ES3)
    programOes = createProgram(VERTEX_SHADER_ES3, FRAGMENT_SHADER_OES_ES3)
  }

  private fun initFbo() {
    val fbos = IntArray(1)
    GLES30.glGenFramebuffers(1, fbos, 0)
    fboId = fbos[0]

    val textures = IntArray(1)
    GLES30.glGenTextures(1, textures, 0)
    fboTextureId = textures[0]

    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTextureId)
    GLES30.glTexImage2D(
      GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
      width, height, 0,
      GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
    )
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
    GLES30.glFramebufferTexture2D(
      GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
      GLES30.GL_TEXTURE_2D, fboTextureId, 0
    )

    val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
    if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
      Log.e(TAG, "Framebuffer init failed with status: $status")
    }
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
  }

  /**
   * Composites multiple layers into the offscreen FBO and returns the resulting texture ID.
   */
  fun compositeToFbo(
    layers: List<RenderLayer>,
    textureSources: Map<String, LayerTextureSource>
  ): Int {
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
    GLES30.glViewport(0, 0, width, height)

    // Clear background
    GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

    renderLayersInternal(layers, textureSources)

    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    return fboTextureId
  }

  /**
   * Renders the composite layers directly onto the MediaCodec input surface and sets presentation timestamp.
   */
  fun renderToEncoderSurface(
    encoderSurface: WindowSurface,
    layers: List<RenderLayer>,
    textureSources: Map<String, LayerTextureSource>,
    presentationTimeUs: Long
  ) {
    encoderSurface.makeCurrent()
    GLES30.glViewport(0, 0, width, height)

    GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

    renderLayersInternal(layers, textureSources)

    // Send presentation time in nanoseconds to MediaCodec input surface
    encoderSurface.setPresentationTime(presentationTimeUs * 1000L)
    encoderSurface.swapBuffers()
  }

  private fun renderLayersInternal(
    layers: List<RenderLayer>,
    textureSources: Map<String, LayerTextureSource>
  ) {
    // Render in sorted zIndex order (background first, overlays/text on top)
    for ((index, layer) in layers.withIndex()) {
      val source = textureSources[layer.clipId] ?: continue
      if (index == 0) {
        GLES30.glDisable(GLES30.GL_BLEND)
      } else {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
      }
      drawLayer(layer, source)
    }

    GLES30.glDisable(GLES30.GL_BLEND)
  }

  private fun drawLayer(layer: RenderLayer, source: LayerTextureSource) {
    val program = if (source.isOes) programOes else program2D
    val target = if (source.isOes) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES30.GL_TEXTURE_2D

    GLES30.glUseProgram(program)

    val uMvpHandle = GLES30.glGetUniformLocation(program, "uMvpMatrix")
    val uTexHandle = GLES30.glGetUniformLocation(program, "uTexMatrix")
    val uAlphaHandle = GLES30.glGetUniformLocation(program, "uAlpha")
    val uSamplerHandle = GLES30.glGetUniformLocation(program, "uTexture")

    // Set MVP matrix (combines layer transforms and aspect ratio)
    GLES30.glUniformMatrix4fv(uMvpHandle, 1, false, layer.transformMatrix, 0)

    // Set texture transform matrix
    GLES30.glUniformMatrix4fv(uTexHandle, 1, false, source.texMatrix, 0)

    // Set alpha
    GLES30.glUniform1f(uAlphaHandle, layer.alpha.coerceIn(0f, 1f))

    // Bind texture
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(target, source.textureId)
    GLES30.glUniform1i(uSamplerHandle, 0)

    // Attributes
    vertexBuffer.position(0)
    GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 5 * 4, vertexBuffer)
    GLES30.glEnableVertexAttribArray(0)

    vertexBuffer.position(3)
    GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 5 * 4, vertexBuffer)
    GLES30.glEnableVertexAttribArray(1)

    // Draw full quad
    GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

    GLES30.glDisableVertexAttribArray(0)
    GLES30.glDisableVertexAttribArray(1)
    GLES30.glBindTexture(target, 0)
    GLES30.glUseProgram(0)
  }

  fun release() {
    if (fboId != 0) {
      GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
      fboId = 0
    }
    if (fboTextureId != 0) {
      GLES30.glDeleteTextures(1, intArrayOf(fboTextureId), 0)
      fboTextureId = 0
    }
    if (program2D != 0) {
      GLES30.glDeleteProgram(program2D)
      program2D = 0
    }
    if (programOes != 0) {
      GLES30.glDeleteProgram(programOes)
      programOes = 0
    }
  }

  private fun createProgram(vertexSrc: String, fragmentSrc: String): Int {
    val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
    val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
    val program = GLES30.glCreateProgram()
    GLES30.glAttachShader(program, vertexShader)
    GLES30.glAttachShader(program, fragmentShader)
    GLES30.glLinkProgram(program)

    val linkStatus = IntArray(1)
    GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
    if (linkStatus[0] != GLES30.GL_TRUE) {
      val log = GLES30.glGetProgramInfoLog(program)
      Log.e(TAG, "Error linking program: $log")
      GLES30.glDeleteProgram(program)
      return 0
    }
    return program
  }

  private fun compileShader(type: Int, src: String): Int {
    val shader = GLES30.glCreateShader(type)
    GLES30.glShaderSource(shader, src)
    GLES30.glCompileShader(shader)

    val compiled = IntArray(1)
    GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
    if (compiled[0] == 0) {
      val log = GLES30.glGetShaderInfoLog(shader)
      Log.e(TAG, "Shader compile error ($type): $log")
      GLES30.glDeleteShader(shader)
      return 0
    }
    return shader
  }
}
