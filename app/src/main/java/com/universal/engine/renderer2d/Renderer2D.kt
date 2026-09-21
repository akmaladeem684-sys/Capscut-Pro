package com.universal.engine.renderer2d

import android.opengl.GLES30
import com.universal.engine.frame.GpuFrame
import com.universal.engine.gl.FramebufferManager
import com.universal.engine.gl.FullScreenQuad
import com.universal.engine.gl.GlError
import com.universal.engine.gl.GlslLibrary
import com.universal.engine.rendergraph.RenderContext

/**
 * GPU 2D layer renderer. One draw call per layer. Supports transforms, crop, rounded-rect clip, masks,
 * blend modes and opacity.
 */
class Renderer2D(private val ctx: RenderContext) {

    private val quad = FullScreenQuad()

    private val layerProgram get() = ctx.shaders.getOrCompile(
        "2d.layer",
        VERTEX,
        GlslLibrary.VERSION + GlslLibrary.COMMON + GlslLibrary.BLENDING + GlslLibrary.MASKING + LAYER_FRAG,
        mapOf(0 to "aPos", 1 to "aUv")
    )

    fun drawLayer(target: FramebufferManager, out: com.universal.engine.gl.RenderTarget, frame: GpuFrame, t: Transform2D, blend: BlendMode) {
        val p = layerProgram.use()
        out.bind()
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        quad.bind(p)
        p.setTexture("uTexture", 0, frame.textureId, frame.glTextureTarget)
        p.setMat4("uTransform", t.matrix())
        p.setVec4("uAnchor", t.anchorX, t.anchorY, t.cropLeft, t.cropTop)
        p.setVec4("uCrop", t.cropLeft, t.cropTop, 1f - t.cropRight, 1f - t.cropBottom)
        p.setFloat("uOpacity", t.opacity)
        p.setInt("uBlendMode", blend.shaderIndex)
        p.setVec4("uRect", t.translationX, t.translationY, t.scaleX * 0.5f, t.scaleY * 0.5f)
        p.setVec2("uRoundedRadius", 0f, 0f)
        p.setFloat("uMirror", if (t.mirrored) 1f else 0f)
        quad.draw()
        quad.unbind()
        GLES30.glDisable(GLES30.GL_BLEND)
        out.unbind()
        GlError.check("Renderer2D.drawLayer")
    }

    fun drawRoundedLayer(out: com.universal.engine.gl.RenderTarget, frame: GpuFrame, t: Transform2D, radiusNormalized: Float) {
        val p = layerProgram.use()
        out.bind()
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        quad.bind(p)
        p.setTexture("uTexture", 0, frame.textureId, frame.glTextureTarget)
        p.setMat4("uTransform", t.matrix())
        p.setVec4("uAnchor", t.anchorX, t.anchorY, 0f, 0f)
        p.setVec4("uCrop", 0f, 0f, 1f, 1f)
        p.setFloat("uOpacity", t.opacity)
        p.setInt("uBlendMode", BlendMode.NORMAL.shaderIndex)
        p.setVec4("uRect", t.translationX, t.translationY, t.scaleX * 0.5f, t.scaleY * 0.5f)
        p.setVec2("uRoundedRadius", radiusNormalized, radiusNormalized)
        p.setFloat("uMirror", if (t.mirrored) 1f else 0f)
        quad.draw()
        quad.unbind()
        GLES30.glDisable(GLES30.GL_BLEND)
        out.unbind()
        GlError.check("Renderer2D.drawRoundedLayer")
    }

    private val VERTEX = """
        in vec2 aPos; in vec2 aUv; out vec2 vUv;
        uniform mat4 uTransform;
        uniform vec4 uAnchor;
        void main() {
            vec2 centered = aPos - uAnchor.xy;
            vec4 pos = uTransform * vec4(centered, 0.0, 1.0);
            vUv = aUv;
            gl_Position = pos;
        }
    """

    private val LAYER_FRAG = """
        in vec2 vUv; out vec4 oColor;
        uniform sampler2D uTexture;
        uniform float uOpacity; uniform int uBlendMode;
        uniform vec4 uCrop;
        uniform vec4 uRect;
        uniform vec2 uRoundedRadius;
        uniform float uMirror;
        uniform vec4 uAnchor;
        void main() {
            vec2 fragNdc = gl_FragCoord.xy / vec2(textureSize(uTexture, 0)) * 2.0 - 1.0;
            vec2 local = (fragNdc - uRect.xy) / max(uRect.zw, vec2(1e-6)) + 0.5;
            if (any(lessThan(local, vec2(0.0))) || any(greaterThan(local, vec2(1.0)))) discard;
            float mask = 1.0;
            if (uRoundedRadius.x > 0.0) {
                vec2 uvr = local;
                vec2 d = abs(uvr - 0.5) * 2.0;
                vec2 q = d - (1.0 - uRoundedRadius.x * 2.0);
                float r = uRoundedRadius.x;
                float dist = min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r * 2.0;
                mask = clamp(0.5 - dist, 0.0, 1.0);
            }
            vec2 uv = mix(vec2(uCrop.x, uCrop.y), vec2(uCrop.z, uCrop.w), local);
            if (uMirror > 0.5) uv.x = uCrop.z - (uv.x - uCrop.x);
            vec4 src = texture(uTexture, uv);
            src.a *= uOpacity * mask;
            oColor = vec4(src.rgb * src.a, src.a);
        }
    """
}
