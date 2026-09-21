package com.vfx.engine.gpu.fbo

import android.opengl.GLES20
import com.vfx.engine.gpu.texture.GpuTexture
import com.vfx.engine.gpu.texture.TextureSpec

class FramebufferObject(val width: Int, val height: Int) {
  var fboId: Int = 0
    private set
  val texture: GpuTexture

  init {
    val fbos = IntArray(1)
    GLES20.glGenFramebuffers(1, fbos, 0)
    fboId = fbos[0]

    texture = GpuTexture(TextureSpec(width = width, height = height))

    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
    GLES20.glFramebufferTexture2D(
      GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
      GLES20.GL_TEXTURE_2D, texture.textureId, 0
    )

    val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
    if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
      throw RuntimeException("Framebuffer incomplete: $status")
    }
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
  }

  fun bind() {
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
    GLES20.glViewport(0, 0, width, height)
  }

  fun unbind() {
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
  }

  fun release() {
    if (fboId != 0) {
      val fbos = intArrayOf(fboId)
      GLES20.glDeleteFramebuffers(1, fbos, 0)
      fboId = 0
    }
    texture.release()
  }
}
