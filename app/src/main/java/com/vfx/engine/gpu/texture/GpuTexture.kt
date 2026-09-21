package com.vfx.engine.gpu.texture

import android.opengl.GLES30

data class TextureSpec(
  val width: Int,
  val height: Int,
  val internalFormat: Int = GLES30.GL_RGBA8,
  val format: Int = GLES30.GL_RGBA,
  val type: Int = GLES30.GL_UNSIGNED_BYTE,
  val minFilter: Int = GLES30.GL_LINEAR,
  val magFilter: Int = GLES30.GL_LINEAR
)

class GpuTexture private constructor(
  val spec: TextureSpec,
  val isOwned: Boolean
) {
  var textureId: Int = 0
    private set

  constructor(spec: TextureSpec) : this(spec, true) {
    val textures = IntArray(1)
    GLES30.glGenTextures(1, textures, 0)
    textureId = textures[0]

    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
    GLES30.glTexImage2D(
      GLES30.GL_TEXTURE_2D, 0, spec.internalFormat, spec.width, spec.height,
      0, spec.format, spec.type, null
    )
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, spec.minFilter)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, spec.magFilter)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
  }

  companion object {
    fun wrapExternal(textureId: Int, width: Int, height: Int): GpuTexture {
      val spec = TextureSpec(width = width, height = height)
      val texture = GpuTexture(spec, isOwned = false)
      texture.textureId = textureId
      return texture
    }
  }

  fun bind(unit: Int = 0) {
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
  }

  fun release() {
    if (isOwned && textureId != 0) {
      val textures = intArrayOf(textureId)
      GLES30.glDeleteTextures(1, textures, 0)
      textureId = 0
    }
  }
}
