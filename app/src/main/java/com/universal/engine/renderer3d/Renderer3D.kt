package com.universal.engine.renderer3d

import android.opengl.GLES30
import com.universal.engine.gl.GlError
import com.universal.engine.gl.GlslLibrary
import com.universal.engine.rendergraph.RenderContext

/**
 * Scene-graph 3D renderer. Transparent objects are sorted back-to-front and drawn after
 * opaque ones.
 */
class Renderer3D(private val ctx: RenderContext) {

    data class SceneObject(
        val mesh: Mesh,
        val material: Material,
        val modelMatrix: FloatArray,
        val opacity: Float = 1f
    )

    data class Scene(val camera: Camera, val lights: List<Light>, val objects: List<SceneObject>)

    private val pbrProgram get() = ctx.shaders.getOrCompile(
        "3d.pbr",
        VERT,
        GlslLibrary.VERSION + GlslLibrary.COMMON + FRAG,
        mapOf(0 to "aPos", 1 to "aNormal", 2 to "aUv")
    )

    fun render(target: com.universal.engine.gl.RenderTarget, scene: Scene, clearR: Float, clearG: Float, clearB: Float, clearA: Float) {
        val p = pbrProgram.use()
        target.bind()
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glClearColor(clearR, clearG, clearB, clearA)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val aspect = target.width.toFloat() / target.height
        val view = scene.camera.viewMatrix()
        val proj = scene.camera.projectionMatrix(aspect)
        p.setMat4("uView", view)
        p.setMat4("uProj", proj)
        p.setVec3f("uCamPos", scene.camera.eyeX, scene.camera.eyeY, scene.camera.eyeZ)
        p.setInt("uLightCount", scene.lights.size.coerceAtMost(8))
        scene.lights.take(8).forEachIndexed { i, l ->
            p.setVec3f("uLightPos[$i]", l.x, l.y, l.z)
            p.setVec3f("uLightColor[$i]", l.r * l.intensity, l.g * l.intensity, l.b * l.intensity)
            p.setInt("uLightType[$i]", l.type.ordinal)
        }

        val (opaque, transparent) = scene.objects.partition { it.material.opacity >= 0.999f && it.opacity >= 0.999f }
        opaque.forEach { drawObject(p, it) }
        transparent.sortedByDescending { objDepth(it, scene.camera) }.forEach { drawObject(p, it) }

        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        target.unbind()
        GlError.check("Renderer3D.render")
    }

    private fun objDepth(o: SceneObject, cam: Camera): Float {
        val m = o.modelMatrix
        return (m[12] - cam.eyeX) * (m[12] - cam.eyeX) + (m[13] - cam.eyeY) * (m[13] - cam.eyeY) + (m[14] - cam.eyeZ) * (m[14] - cam.eyeZ)
    }

    private fun drawObject(p: com.universal.engine.gl.ShaderManager.ShaderProgram, o: SceneObject) {
        p.setMat4("uModel", o.modelMatrix)
        val m = o.material
        p.setVec3f("uBaseColor", m.baseColorR, m.baseColorG, m.baseColorB)
        p.setFloat("uMetallic", m.metallic)
        p.setFloat("uRoughness", m.roughness)
        p.setFloat("uOpacity", m.opacity * o.opacity)
        if (m.baseColorTexture != null) { m.baseColorTexture.bind(0); p.setInt("uAlbedo", 0); p.setInt("uHasAlbedo", 1) }
        else p.setInt("uHasAlbedo", 0)
        if (m.normalMap != null) { m.normalMap.bind(1); p.setInt("uNormal", 1); p.setInt("uHasNormal", 1) }
        else p.setInt("uHasNormal", 0)
        if (m.doubleSided) GLES30.glDisable(GLES30.GL_CULL_FACE) else { GLES30.glEnable(GLES30.GL_CULL_FACE); GLES30.glCullFace(GLES30.GL_BACK) }
        o.mesh.draw()
    }

    private val VERT = """
        in vec3 aPos; in vec3 aNormal; in vec2 aUv;
        out vec3 vWorld; out vec3 vNormal; out vec2 vUv;
        uniform mat4 uModel; uniform mat4 uView; uniform mat4 uProj;
        void main() {
            vec4 world = uModel * vec4(aPos, 1.0);
            vWorld = world.xyz;
            vNormal = mat3(uModel) * aNormal;
            vUv = aUv;
            gl_Position = uProj * uView * world;
        }
    """

    private val FRAG = """
        in vec3 vWorld; in vec3 vNormal; in vec2 vUv;
        out vec4 oColor;
        uniform vec3 uBaseColor; uniform float uMetallic; uniform float uRoughness; uniform float uOpacity;
        uniform sampler2D uAlbedo; uniform sampler2D uNormal;
        uniform int uHasAlbedo; uniform int uHasNormal;
        uniform vec3 uCamPos;
        uniform int uLightCount;
        uniform vec3 uLightPos[8]; uniform vec3 uLightColor[8]; uniform int uLightType[8];
        void main() {
            vec3 albedo = uHasAlbedo == 1 ? uBaseColor * texture(uAlbedo, vUv).rgb : uBaseColor;
            vec3 N = normalize(vNormal);
            if (uHasNormal == 1) {
                vec3 nm = texture(uNormal, vUv).xyz * 2.0 - 1.0;
                N = normalize(N + nm * 0.5);
            }
            vec3 V = normalize(uCamPos - vWorld);
            vec3 f0 = mix(vec3(0.04), albedo, uMetallic);
            vec3 out3 = vec3(0.0);
            for (int i = 0; i < uLightCount; i++) {
                vec3 L; vec3 radiance;
                if (uLightType[i] == 0) {
                    L = normalize(-uLightPos[i]); radiance = uLightColor[i];
                } else {
                    vec3 d = uLightPos[i] - vWorld;
                    float dist = length(d); L = d / dist;
                    radiance = uLightColor[i] / max(dist * dist, 0.01);
                }
                vec3 H = normalize(V + L);
                float ndf = pow(max(dot(N, H), 0.0), mix(2.0, 256.0, 1.0 - uRoughness));
                float ndl = max(dot(N, L), 0.0);
                vec3 spec = f0 * ndf / max(4.0 * max(dot(N,V),0.0) * ndl + 0.001, 0.001) * ndl;
                vec3 diff = albedo * (1.0 - uMetallic) * ndl;
                out3 += (diff + spec * (1.0 - uRoughness)) * radiance;
            }
            out3 += albedo * 0.03;
            oColor = vec4(out3 * uOpacity, uOpacity);
        }
    """
}
