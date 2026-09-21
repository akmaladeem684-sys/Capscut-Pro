package com.vfx.engine.gpu.state

import android.opengl.GLES20

object GlCapabilities {
  var maxTextureSize: Int = 4096
    private set
  var supportsFloatTextures: Boolean = true
    private set

  fun queryCapabilities() {
    val params = IntArray(1)
    GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, params, 0)
    maxTextureSize = params[0]
  }
}

object GlStateTracker {
  private var activeProgram: Int = 0
  private var activeFbo: Int = 0

  fun bindProgram(programId: Int) {
    if (activeProgram != programId) {
      activeProgram = programId
      GLES20.glUseProgram(programId)
    }
  }

  fun bindFramebuffer(fboId: Int) {
    if (activeFbo != fboId) {
      activeFbo = fboId
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
    }
  }
}
