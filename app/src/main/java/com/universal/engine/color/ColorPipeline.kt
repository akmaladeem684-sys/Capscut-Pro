package com.universal.engine.color

import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.universal.engine.gl.FullScreenQuad
import com.universal.engine.gl.GlslLibrary
import com.universal.engine.gl.GlslLibrary.VERSION
import com.universal.engine.gl.RenderTarget
import com.universal.engine.rendergraph.RenderContext

/**
 * GPU color conversion + normalization.
 * All conversions happen in GLSL during texture sampling — no CPU color work anywhere.
 * Output of [normalizeToLinearRgba] is always linear sRGB-encoded RGBA8/RGBA16F texture.
 */
class ColorPipeline(private val ctx: RenderContext) {

    private val program by lazy {
        ctx.shaders.getOrCompile(
            "color.normalize",
            VERTEX,
            VERSION + GlslLibrary.COMMON + GlslLibrary.YUV_CONVERSION + FRAGMENT,
            mapOf(0 to "aPos", 1 to "aUv")
        )
    }

    private val quad = FullScreenQuad()

    fun normalizeToLinearRgba(input: com.universal.engine.frame.GpuFrame, target: RenderTarget) {
        val p = program.use()
        target.bind()
        quad.bind(p)
        p.setTexture("uTexture", 0, input.textureId, input.glTextureTarget)
        p.setInt("uIsOes", if (input.glTextureTarget == GLES11Ext.GL_TEXTURE_EXTERNAL_OES) 1 else 0)
        val ci = input.metadata.colorInfo
        p.setInt("uMatrix", when (ci.matrix) {
            com.universal.engine.frame.ColorInfo.MatrixCoefficients.BT601 -> 0
            com.universal.engine.frame.ColorInfo.MatrixCoefficients.BT2020 -> 2
            else -> 1
        })
        p.setInt("uFullRange", if (ci.range == com.universal.engine.frame.ColorInfo.Range.FULL) 1 else 0)
        p.setInt("uTransfer", when (ci.transfer) {
            com.universal.engine.frame.ColorInfo.Transfer.SRGB -> 0
            com.universal.engine.frame.ColorInfo.Transfer.GAMMA_2_2 -> 1
            com.universal.engine.frame.ColorInfo.Transfer.PQ -> 2
            com.universal.engine.frame.ColorInfo.Transfer.HLG -> 3
        })
        p.setVec2("uTexelSize", 1f / input.width, 1f / input.height)
        p.setMat4("uDecTransform", input.metadata.transform)
        quad.draw()
        quad.unbind()
        target.unbind()
        com.universal.engine.gl.GlError.check("ColorPipeline.normalize")
    }

    fun encodeToSrgb(inputTexId: Int, target: RenderTarget, inputIsLinear: Boolean, inputIsHdr: Boolean, maxCll: Float) {
        val p = ctx.shaders.getOrCompile(
            "color.encode",
            VERTEX,
            VERSION + GlslLibrary.COMMON + GlslLibrary.YUV_CONVERSION + ENCODE_FRAG,
            mapOf(0 to "aPos", 1 to "aUv")
        ).use()
        target.bind()
        quad.bind(p)
        p.setTexture("uTexture", 0, inputTexId, GLES30.GL_TEXTURE_2D)
        p.setInt("uInputLinear", if (inputIsLinear) 1 else 0)
        p.setInt("uInputHdr", if (inputIsHdr) 1 else 0)
        p.setFloat("uMaxCll", maxCll)
        quad.draw()
        quad.unbind()
        target.unbind()
        com.universal.engine.gl.GlError.check("ColorPipeline.encode")
    }

    private val VERTEX = """
        in vec2 aPos; in vec2 aUv; out vec2 vUv;
        void main() { vUv = aUv; gl_Position = vec4(aPos, 0.0, 1.0); }
    """

    private val FRAGMENT = """
        in vec2 vUv; out vec4 oColor;
        uniform sampler2D uTexture; uniform int uIsOes;
        uniform int uMatrix; uniform int uFullRange; uniform int uTransfer;
        uniform vec2 uTexelSize; uniform mat4 uDecTransform;
        void main() {
            vec4 sample4;
            if (uIsOes == 1) {
                vec4 t = uDecTransform * vec4(vUv, 0.0, 1.0);
                sample4 = texture(uTexture, t.xy);
            } else {
                sample4 = texture(uTexture, vUv);
            }
            vec3 rgb;
            if (uIsOes == 1) {
                vec3 yuv = sample4.rgb;
                if (uFullRange == 1) yuv = (yuv - vec3(0.0, 0.5, 0.5)) * vec3(1.0, 1.16438, 1.16438) - vec3(0.0625);
                else yuv -= vec3(0.0625, 0.5, 0.5);
                rgb = yuvToRgbLimited(yuv, uMatrix);
                rgb = srgbToLinear(rgb);
            } else {
                rgb = sample4.rgb;
            }
            switch (uTransfer) {
                case 1: rgb = applyGamma(rgb, 2.2); break;
                case 2: rgb = pqToLinear(rgb) / 10000.0 * 100.0; break;
                case 3: rgb = hlgToLinear(rgb); break;
                default: break;
            }
            oColor = vec4(rgb, sample4.a);
        }
    """

    private val ENCODE_FRAG = """
        in vec2 vUv; out vec4 oColor;
        uniform sampler2D uTexture; uniform int uInputLinear; uniform int uInputHdr; uniform float uMaxCll;
        void main() {
            vec4 c = texture(uTexture, vUv);
            vec3 rgb = c.rgb;
            if (uInputHdr == 1 && uMaxCll > 0.0) rgb = toneMapReinhard(rgb, uMaxCll);
            if (uInputLinear == 1) rgb = linearToSrgb(rgb);
            oColor = vec4(clamp(rgb, 0.0, 1.0), c.a);
        }
    """
}
