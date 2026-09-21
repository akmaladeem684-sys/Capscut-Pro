package com.universal.engine.compositing

import android.opengl.GLES30
import com.universal.engine.frame.FrameMetadata
import com.universal.engine.frame.GpuFrame
import com.universal.engine.gl.FullScreenQuad
import com.universal.engine.gl.GlError
import com.universal.engine.gl.GlslLibrary
import com.universal.engine.rendergraph.RenderContext
import com.universal.engine.renderer2d.Renderer2D

/**
 * Composites resolved [Layer]s bottom-up into one RGBA target, honoring blend modes,
 * premultiplied alpha and per-layer masks.
 */
class Compositor(private val ctx: RenderContext, private val renderer2d: Renderer2D) {

    private val maskProgram get() = ctx.shaders.getOrCompile(
        "composite.mask",
        MASK_VERT,
        GlslLibrary.VERSION + GlslLibrary.BLENDING + MASK_FRAG,
        mapOf(0 to "aPos", 1 to "aUv")
    )
    private val quad = FullScreenQuad()

    fun composite(layers: List<Layer>, out: com.universal.engine.gl.RenderTarget, backgroundR: Float, backgroundG: Float, backgroundB: Float) {
        out.bind()
        GLES30.glClearColor(backgroundR, backgroundG, backgroundB, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        out.unbind()

        val sorted = layers.sortedBy { it.zIndex }
        for (layer in sorted) {
            val withMask = applyMaskIfPresent(layer) ?: continue
            renderer2d.drawLayer(ctx.framebuffers, out, withMask.source, layer.transform, layer.blend)
            if (withMask.source !== layer.source) withMask.source.release()
            layer.source.release()
            layer.maskFrame?.release()
        }
        GlError.check("Compositor.composite")
    }

    private fun applyMaskIfPresent(layer: Layer): Layer {
        val mask = layer.maskFrame ?: return layer
        val src = layer.source
        val target = ctx.framebuffers.acquire(src.width, src.height)
        val p = maskProgram.use()
        target.bind()
        quad.bind(p)
        p.setTexture("uSource", 0, src.textureId, src.glTextureTarget)
        p.setTexture("uMask", 1, mask.textureId, mask.glTextureTarget)
        quad.draw()
        quad.unbind()
        target.unbind()
        val maskedMeta = src.metadata.copy(alphaMode = FrameMetadata.AlphaMode.PREMULTIPLIED)
        val masked = GpuFrame.fromRenderTarget(target, maskedMeta)
        src.release()
        return layer.copy(source = masked)
    }

    private val MASK_VERT = """
        in vec2 aPos; in vec2 aUv; out vec2 vUv;
        void main() { vUv = aUv; gl_Position = vec4(aPos, 0.0, 1.0); }
    """

    private val MASK_FRAG = """
        in vec2 vUv; out vec4 oColor;
        uniform sampler2D uSource; uniform sampler2D uMask;
        void main() {
            vec4 s = texture(uSource, vUv);
            float m = texture(uMask, vUv).r;
            float a = s.a * m;
            oColor = vec4(s.rgb * a, a);
        }
    """
}
