package com.vfx.engine.gpu.texture

import android.opengl.GLES20

data class TextureSpec(
  val width: Int,
  val height: Int,
  val internalFormat: Int = GLES20.GL_RGBA,
  val minFilter: Int = GLES20.GL_LINEAR,
  val magFilter: Int = GLES20.GL_LINEAR
)

class GpuTexture(val spec: TextureSpec) {
  var textureId: Int = 0
    private set

  init {
    val textures = IntArray(1)
    GLES20.glGenTextures(1, textures, 0)
    textureId = textures[0]

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
    GLES20.glTexImage2D(
      GLES20.GL_TEXTURE_2D, 0, spec.internalFormat, spec.width, spec.height,
      0, spec.internalFormat, GLES20.GL_UNSIGNED_BYTE, null
    )
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, spec.minFilter)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, spec.magFilter)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
  }

  fun bind(unit: Int = 0) {
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
  }

  fun release() {
    if (textureId != 0) {
      val textures = intArrayOf(textureId)
      GLES20.glDeleteTextures(1, textures, 0)
      textureId = 0
    }
  }
}
