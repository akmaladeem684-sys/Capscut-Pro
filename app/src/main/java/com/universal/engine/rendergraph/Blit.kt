package com.universal.engine.rendergraph

import com.universal.engine.gl.FullScreenQuad
import com.universal.engine.gl.GlslLibrary
import com.universal.engine.gl.GlError

/** Shared final-output blit used by BOTH playback present and export present. */
object Blit {
    private val quad = FullScreenQuad()
    const val VERT = """
        in vec2 aPos; in vec2 aUv; out vec2 vUv;
        void main() { vUv = aUv; gl_Position = vec4(aPos, 0.0, 1.0); }
    """
    const val FRAG = """
        in vec2 vUv; out vec4 oColor;
        uniform sampler2D uTexture; uniform vec2 uTexel;
        void main() {
            vec3 c = texture(uTexture, vUv).rgb;
            c = c / (c + vec3(0.155)) * 1.019;
            oColor = vec4(clamp(c, 0.0, 1.0), 1.0);
        }
    """

    fun draw(ctx: RenderContext, sourceTexId: Int, width: Int, height: Int) {
        val p = ctx.shaders.getOrCompile("output.blit", VERT, GlslLibrary.VERSION + FRAG, mapOf(0 to "aPos", 1 to "aUv")).use()
        p.setTexture("uTexture", 0, sourceTexId, android.opengl.GLES30.GL_TEXTURE_2D)
        p.setVec2("uTexel", 1f / width, 1f / height)
        quad.bind(p); quad.draw(); quad.unbind()
        GlError.check("Blit.draw")
    }
}
