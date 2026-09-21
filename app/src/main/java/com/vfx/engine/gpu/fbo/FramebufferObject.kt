package com.vfx.engine.gpu.fbo

import android.opengl.GLES30
import com.vfx.engine.gpu.texture.GpuTexture
import com.vfx.engine.gpu.texture.TextureSpec

class FramebufferObject(
  val width: Int,
  val height: Int,
  val texture: GpuTexture = GpuTexture(TextureSpec(width = width, height = height))
) {
  var fboId: Int = 0
    private set

  init {
    val fbos = IntArray(1)
    GLES30.glGenFramebuffers(1, fbos, 0)
    fboId = fbos[0]

    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
    GLES30.glFramebufferTexture2D(
      GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
      GLES30.GL_TEXTURE_2D, texture.textureId, 0
    )

    val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
    if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
      throw RuntimeException("Framebuffer incomplete: 0x${Integer.toHexString(status)}")
    }
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
  }

  fun bind() {
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
    GLES30.glViewport(0, 0, width, height)
  }

  fun unbind() {
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
  }

  fun release() {
    if (fboId != 0) {
      val fbos = intArrayOf(fboId)
      GLES30.glDeleteFramebuffers(1, fbos, 0)
      fboId = 0
    }
    texture.release()
  }
}
