package com.universal.engine.text

import android.graphics.Bitmap
import android.opengl.GLES30
import com.universal.engine.frame.FrameMetadata
import com.universal.engine.frame.GpuFrame
import com.universal.engine.renderer2d.Transform2D

/**
 * External Text Engine bridge. Typography stays OUT of the rendering engine:
 * the text engine produces laid-out glyph data; we rasterize to a texture once per
 * text-state change and composite it like any other 2D layer.
 */
class TextRenderAdapter(private val ctx: com.universal.engine.rendergraph.RenderContext) {

    data class RenderedText(
        val bitmap: Bitmap,
        val boundsWidth: Int,
        val boundsHeight: Int,
        val baselineOffsetPx: Int
    )

    data class TextMesh3D(val mesh: com.universal.engine.renderer3d.Mesh, val material: com.universal.engine.renderer3d.Material)

    fun toGpuFrame(text: RenderedText): GpuFrame {
        val bmp = text.bitmap
        val w = bmp.width
        val h = bmp.height
        val tex = ctx.textures.createTexture2D(w, h, GLES30.GL_RGBA8, linearFilter = true)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex.id)
        android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        com.universal.engine.gl.GlError.check("TextRenderAdapter.upload")
        val meta = FrameMetadata(
            w, h, 0L, 0L, -1L, FrameMetadata.PixelFormat.TEXTURE_2D_RGBA,
            com.universal.engine.frame.ColorInfo(), alphaMode = FrameMetadata.AlphaMode.PREMULTIPLIED
        )
        return GpuFrame.fromRenderTarget(
            ctx.framebuffers.acquire(w, h),
            meta
        )
    }

    fun normalizedPlacement(
        text: RenderedText,
        canvasW: Int,
        canvasH: Int,
        centerX: Float,
        centerY: Float,
        scale: Float,
        rotationDeg: Float,
        opacity: Float
    ): Transform2D {
        val sx = text.boundsWidth.toFloat() / canvasW * scale
        val sy = text.boundsHeight.toFloat() / canvasH * scale
        return Transform2D(
            translationX = centerX, translationY = centerY,
            scaleX = sx, scaleY = sy,
            rotationDegrees = rotationDeg, opacity = opacity,
            anchorX = 0.5f, anchorY = 0.5f
        )
    }
}
