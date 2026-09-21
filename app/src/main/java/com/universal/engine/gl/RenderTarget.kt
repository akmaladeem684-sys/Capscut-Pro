package com.universal.engine.gl

import android.opengl.GLES30

/** A complete render destination: FBO + color texture (+ optional depth/stencil). */
class RenderTarget internal constructor(
    val manager: FramebufferManager,
    val framebufferId: Int,
    val colorTexture: TextureManager.TextureResource,
    val depthRenderbufferId: Int,      // 0 = none
    val width: Int,
    val height: Int,
    val hasDepth: Boolean
) {
    val colorTextureId: Int get() = colorTexture.id
    internal var inUse = false

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebufferId)
        GLES30.glViewport(0, 0, width, height)
    }

    fun unbind() = GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

    fun recycleSelf() = manager.recycle(this)

    internal fun deleteGl() {
        GLES30.glDeleteFramebuffers(1, intArrayOf(framebufferId), 0)
        if (depthRenderbufferId != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(depthRenderbufferId), 0)
        manager.textures.destroy(colorTexture)
    }
}
