package com.universal.engine.frame

import android.opengl.GLES11Ext
import android.opengl.GLES30
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A frame that lives on the GPU. Either:
 *  - an EXTERNAL_OES texture owned by a decoder (never copied), or
 *  - a 2D RGBA texture attached to a pooled RenderTarget (render graph intermediate).
 * Disposal is refcounted: nodes may hold frames across passes without duplication.
 */
class GpuFrame private constructor(
    val textureId: Int,
    val glTextureTarget: Int,           // GL_TEXTURE_2D or GL_TEXTURE_EXTERNAL_OES
    val metadata: FrameMetadata,
    val renderTarget: Any? = null,
    private val releaser: (() -> Unit)?
) {
    private val released = AtomicBoolean(false)
    private val refCount = AtomicInteger(1)

    val width: Int get() = metadata.width
    val height: Int get() = metadata.height
    val timestampUs: Long get() = metadata.timestampUs

    fun retain(): GpuFrame { refCount.incrementAndGet(); return this }

    fun release() {
        if (refCount.decrementAndGet() == 0 && !released.getAndSet(true)) {
            releaser?.invoke()
        }
    }

    val isReleased: Boolean get() = released.get()

    companion object {
        fun fromExternalTexture(textureId: Int, metadata: FrameMetadata, onConsumed: () -> Unit): GpuFrame =
            GpuFrame(textureId, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, metadata, null) { onConsumed() }

        fun fromRenderTarget(target: com.universal.engine.gl.RenderTarget, metadata: FrameMetadata): GpuFrame =
            GpuFrame(target.colorTextureId, GLES30.GL_TEXTURE_2D, metadata, target) {
                target.manager.recycle(target)
            }
    }
}
