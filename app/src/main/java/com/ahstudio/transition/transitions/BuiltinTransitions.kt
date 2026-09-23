package com.ahstudio.transition.transitions

import com.ahstudio.transition.core.AlphaMode
import com.ahstudio.transition.core.ParamType
import com.ahstudio.transition.core.ParamValue
import com.ahstudio.transition.core.ShaderSource
import com.ahstudio.transition.core.TransitionDefinition
import com.ahstudio.transition.core.TransitionFamily
import com.ahstudio.transition.core.TransitionParameterDefinition
import com.ahstudio.transition.core.TransitionRenderGraphSpec
import com.ahstudio.transition.provider.TransitionProvider

object BuiltinTransitions {
    const val CROSS_DISSOLVE_ID = "com.ahstudio.transition.cross_dissolve"
    const val ZOOM_ID = "com.ahstudio.transition.zoom"
    const val ZOOM_OUT_ID = "com.ahstudio.transition.zoom_out"
    const val SLIDE_LEFT_ID = "com.ahstudio.transition.slide_left"
    const val SLIDE_RIGHT_ID = "com.ahstudio.transition.slide_right"
    const val PUSH_UP_ID = "com.ahstudio.transition.push_up"
    const val WIPE_ID = "com.ahstudio.transition.wipe"
    const val RADIAL_WIPE_ID = "com.ahstudio.transition.radial_wipe"
    const val BLUR_ID = "com.ahstudio.transition.blur"
    const val ZOOM_BLUR_ID = "com.ahstudio.transition.zoom_blur"
    const val FLASH_ID = "com.ahstudio.transition.flash"
    const val GLITCH_ID = "com.ahstudio.transition.glitch"
    const val GLITCH_WIPE_ID = "com.ahstudio.transition.glitch_wipe"
    const val SPIN_ID = "com.ahstudio.transition.spin"
    const val WHIP_PAN_ID = "com.ahstudio.transition.whip_pan"
    const val LIGHT_LEAK_ID = "com.ahstudio.transition.light_leak"

    // 1. Cross Dissolve
    private const val CROSS_DISSOLVE_FRAG = """
precision highp float;
uniform sampler2D uTextureA;
uniform sampler2D uTextureB;
uniform float uProgress;
uniform float uRawProgress;
uniform vec2 uResolution;
uniform float uTime;
uniform float uDuration;
uniform vec2 uDirection;
uniform float uOutputPremultiplied;
uniform float u_softness;   // NORMALIZED 0.0..0.5, default 0.0
in vec2 vUv;
out vec4 oColor;
void main() {
    vec4 a = texture(uTextureA, vUv);
    vec4 b = texture(uTextureB, vUv);
    float p = uProgress;
    if (u_softness > 0.0001) {
        p = smoothstep(0.5 - u_softness * 0.5, 0.5 + u_softness * 0.5, uProgress);
    }
    vec4 c = mix(a, b, clamp(p, 0.0, 1.0));
    if (uOutputPremultiplied > 0.5) { oColor = vec4(c.rgb * c.a, c.a); } else { oColor = c; }
}
"""

    // 2. Zoom In
    private const val ZOOM_FRAG = """
precision highp float;
uniform sampler2D uTextureA;
uniform sampler2D uTextureB;
uniform float uProgress;
uniform float uRawProgress;
uniform vec2 uResolution;
uniform float uTime;
uniform float uDuration;
uniform vec2 uDirection;
uniform float uOutputPremultiplied;
uniform float u_zoomAmount;    // FLOAT 1.0..3.0, default 1.6
uniform float u_edgeSoftness;  // NORMALIZED 0.0..1.0, default 0.15
in vec2 vUv;
out vec4 oColor;
void main() {
    float p = clamp(uProgress, 0.0, 1.0);
    float zoom = max(u_zoomAmount, 1.0);
    float scaleA = mix(1.0, zoom, p);
    float scaleB = mix(1.0 + (zoom - 1.0) * 0.35, 1.0, p);
    vec2 uvA = (vUv - 0.5) / scaleA + 0.5;
    vec2 uvB = (vUv - 0.5) / scaleB + 0.5;
    vec4 a = texture(uTextureA, uvA);
    vec4 b = texture(uTextureB, uvB);
    float m = p;
    if (u_edgeSoftness > 0.0001) {
        m = smoothstep(0.5 - u_edgeSoftness * 0.5, 0.5 + u_edgeSoftness * 0.5, p);
    }
    vec4 c = mix(b, a, 1.0 - m);
    if (uOutputPremultiplied > 0.5) { oColor = vec4(c.rgb * c.a, c.a); } else { oColor = c; }
}
"""

    // 3. Zoom Out
    private const val ZOOM_OUT_FRAG = """
precision highp float;
uniform sampler2D uTextureA;
uniform sampler2D uTextureB;
uniform float uProgress;
uniform float uRawProgress;
uniform vec2 uResolution;
uniform float uTime;
uniform float uDuration;
uniform vec2 uDirection;
uniform float uOutputPremultiplied;
in vec2 vUv;
out vec4 oColor;
void main() {
    float p = clamp(uProgress, 0.0, 1.0);
    float scaleA = mix(1.0, 0.5, p);
    float scaleB = mix(1.8, 1.0, p);
    vec2 uvA = (vUv - 0.5) / scaleA + 0.5;
    vec2 uvB = (vUv - 0.5) / scaleB + 0.5;
    vec4 a = (uvA.x < 0.0 || uvA.x > 1.0 || uvA.y < 0.0 || uvA.y > 1.0) ? vec4(0.0) : texture(uTextureA, uvA);
    vec4 b = texture(uTextureB, clamp(uvB, vec2(0.0), vec2(1.0)));
    vec4 c = mix(a, b, smoothstep(0.3, 0.7, p));
    if (uOutputPremultiplied > 0.5) { oColor = vec4(c.rgb * c.a, c.a); } else { oColor = c; }
}
"""

    // 4. Slide Left
    private const val SLIDE_LEFT_FRAG = """
precision highp float;
uniform sampler2D uTextureA;
uniform sampler2D uTextureB;
uniform float uProgress;
uniform float uRawProgress;
uniform vec2 uResolution;
uniform float uTime;
uniform float uDuration;
uniform vec2 uDirection;
uniform float uOutputPremultiplied;
in vec2 vUv;
out vec4 oColor;
void main() {
    float p = clamp(uProgress, 0.0, 1.0);
    vec2 offsetA = vec2(-p, 0.0);
    vec2 offsetB = vec2(1.0 - p, 0.0);
    vec2 uvA = vUv + offsetA;
    vec2 uvB = vUv - offsetB;
    vec4 a = (uvA.x >= 0.0 && uvA.x <= 1.0) ? texture(uTextureA, uvA) : vec4(0.0);
    vec4 b = (uvB.x >= 0.0 && uvB.x <= 1.0) ? texture(uTextureB, uvB) : vec4(0.0);
    vec4 c = (vUv.x < (1.0 - p)) ? texture(uTextureA, vUv + vec2(p, 0.0)) : texture(uTextureB, vUv - vec2(1.0 - p, 0.0));
    if (uOutputPremultiplied > 0.5) { oColor = vec4(c.rgb * c.a, c.a); } else { oColor = c; }
}
"""

    // 5. Slide Right
    private const val SLIDE_RIGHT_FRAG = """
precision highp float;
uniform sampler2D uTextureA;
uniform sampler2D uTextureB;
uniform float uProgress;
uniform float uRawProgress;
uniform vec2 uResolution;
uniform float uTime;
uniform float uDuration;
uniform vec2 uDirection;
uniform float uOutputPremultiplied;
in vec2 vUv;
out vec4 oColor;
void main() {
    float p = clamp(uProgress, 0.0, 1.0);
    vec4 c = (vUv.x > p) ? texture(uTextureA, vUv - vec2(p, 0.0)) : texture(uTextureB, vUv + vec2(1.0 - p, 0.0));
    if (uOutputPremultiplied > 0.5) { oColor = vec4(c.rgb * c.a, c.a); } else { oColor = c; }
}
"""

    // 6. Push Up
    private const val PUSH_UP_FRAG = """
precision highp float;
uniform sampler2D uTextureA;
uniform sampler2D uTextureB;
uniform float uProgress;
uniform float uRawProgress;
uniform vec2 uResolution;
uniform float uTime;
uniform float uDuration;
uniform vec2 uDirection;
uniform float uOutputPremultiplied;
in vec2 vUv;
out vec4 oColor;
void main() {
    float p = clamp(uProgress, 0.0, 1.0);
    vec4 c = (vUv.y < (1.0 - p)) ? texture(uTextureA, vUv + vec2(0.0, p)) : texture(uTextureB, vUv - vec2(0.0, 1.0 - p));
    if (uOutputPremultiplied > 0.5) { oColor = vec4(c.rgb * c.a, c.a); } else { oColor = c; }
}
"""

    // 7. Wipe
    private const val WIPE_FRAG = """
precision highp float;
uniform sampler2D uTextureA;
uniform sampler2D uTextureB;
uniform float uProgress;
uniform float uRawProgress;
uniform vec2 uResolution;
uniform float uTime;
uniform float uDuration;
uniform vec2 uDirection;
uniform float uOutputPremultiplied;
uniform float u_feather; // 0.0..0.2
in vec2 vUv;
out vec4 oColor;
void main() {
    float p = clamp(uProgress, 0.0, 1.0);
    float feather = max(u_feather, 0.001);
    float edge = smoothstep(p - feather, p + feather, vUv.x);
    vec4 a = texture(uTextureA, vUv);
    vec4 b = texture(uTextureB, vUv);
    vec4 c = mix(b, a, edge);
    if (uOutputPremultiplied > 0.5) { oColor = vec4(c.rgb * c.a, c.a); } else { oColor = c; }
}
"""

    // 8. Flash
    private const val FLASH_FRAG = """
precision highp float;
uniform sampler2D uTextureA;
uniform sampler2D uTextureB;
uniform float uProgress;
uniform float uRawProgress;
uniform vec2 uResolution;
uniform float uTime;
uniform float uDuration;
uniform vec2 uDirection;
uniform float uOutputPremultiplied;
in vec2 vUv;
out vec4 oColor;
void main() {
    float p = clamp(uProgress, 0.0, 1.0);
    vec4 a = texture(uTextureA, vUv);
    vec4 b = texture(uTextureB, vUv);
    float flashIntensity = 1.0 - abs(p - 0.5) * 2.0;
    vec4 base = (p < 0.5) ? a : b;
    vec4 c = mix(base, vec4(1.0, 1.0, 1.0, base.a), flashIntensity * 0.95);
    if (uOutputPremultiplied > 0.5) { oColor = vec4(c.rgb * c.a, c.a); } else { oColor = c; }
}
"""

    // 9. Glitch
    private const val GLITCH_FRAG = """
precision highp float;
uniform sampler2D uTextureA;
uniform sampler2D uTextureB;
uniform float uProgress;
uniform float uRawProgress;
uniform vec2 uResolution;
uniform float uTime;
uniform float uDuration;
uniform vec2 uDirection;
uniform float uOutputPremultiplied;
in vec2 vUv;
out vec4 oColor;

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float p = clamp(uProgress, 0.0, 1.0);
    float strength = sin(p * 3.14159);
    float block = floor(vUv.y * 24.0);
    float shift = (rand(vec2(block, floor(uTime * 20.0))) - 0.5) * 0.12 * strength;
    vec2 uvShift = vec2(vUv.x + shift, vUv.y);
    
    vec4 a = texture(uTextureA, uvShift);
    vec4 b = texture(uTextureB, uvShift);
    
    // Chromatic aberration
    float r = mix(texture(uTextureA, uvShift + vec2(0.01 * strength, 0.0)).r, texture(uTextureB, uvShift + vec2(0.01 * strength, 0.0)).r, p);
    float g = mix(a.g, b.g, p);
    float bl = mix(texture(uTextureA, uvShift - vec2(0.01 * strength, 0.0)).b, texture(uTextureB, uvShift - vec2(0.01 * strength, 0.0)).b, p);
    
    vec4 c = vec4(r, g, bl, mix(a.a, b.a, p));
    if (uOutputPremultiplied > 0.5) { oColor = vec4(c.rgb * c.a, c.a); } else { oColor = c; }
}
"""

    // 10. Blur
    private const val BLUR_FRAG = """
precision highp float;
uniform sampler2D uTextureA;
uniform sampler2D uTextureB;
uniform float uProgress;
uniform float uRawProgress;
uniform vec2 uResolution;
uniform float uTime;
uniform float uDuration;
uniform vec2 uDirection;
uniform float uOutputPremultiplied;
in vec2 vUv;
out vec4 oColor;

vec4 blurSample(sampler2D tex, vec2 uv, float radius) {
    vec4 sum = vec4(0.0);
    sum += texture(tex, uv + vec2(-radius, -radius) * 0.707) * 0.15;
    sum += texture(tex, uv + vec2(radius, -radius) * 0.707) * 0.15;
    sum += texture(tex, uv + vec2(-radius, radius) * 0.707) * 0.15;
    sum += texture(tex, uv + vec2(radius, radius) * 0.707) * 0.15;
    sum += texture(tex, uv) * 0.4;
    return sum;
}

void main() {
    float p = clamp(uProgress, 0.0, 1.0);
    float blurAmt = (1.0 - abs(p - 0.5) * 2.0) * 0.035;
    vec4 a = blurSample(uTextureA, vUv, blurAmt);
    vec4 b = blurSample(uTextureB, vUv, blurAmt);
    vec4 c = mix(a, b, smoothstep(0.2, 0.8, p));
    if (uOutputPremultiplied > 0.5) { oColor = vec4(c.rgb * c.a, c.a); } else { oColor = c; }
}
"""

    // 11. Spin
    private const val SPIN_FRAG = """
precision highp float;
uniform sampler2D uTextureA;
uniform sampler2D uTextureB;
uniform float uProgress;
uniform float uRawProgress;
uniform vec2 uResolution;
uniform float uTime;
uniform float uDuration;
uniform vec2 uDirection;
uniform float uOutputPremultiplied;
in vec2 vUv;
out vec4 oColor;
void main() {
    float p = clamp(uProgress, 0.0, 1.0);
    float angle = p * 3.14159265;
    float s = sin(angle);
    float c = cos(angle);
    vec2 center = vec2(0.5);
    vec2 uvRot = mat2(c, -s, s, c) * (vUv - center) + center;
    vec4 colA = texture(uTextureA, clamp(uvRot, vec2(0.0), vec2(1.0)));
    vec4 colB = texture(uTextureB, clamp(uvRot, vec2(0.0), vec2(1.0)));
    vec4 result = (p < 0.5) ? colA : colB;
    if (uOutputPremultiplied > 0.5) { oColor = vec4(result.rgb * result.a, result.a); } else { oColor = result; }
}
"""

    // 12. Whip Pan
    private const val WHIP_PAN_FRAG = """
precision highp float;
uniform sampler2D uTextureA;
uniform sampler2D uTextureB;
uniform float uProgress;
uniform float uRawProgress;
uniform vec2 uResolution;
uniform float uTime;
uniform float uDuration;
uniform vec2 uDirection;
uniform float uOutputPremultiplied;
in vec2 vUv;
out vec4 oColor;
void main() {
    float p = clamp(uProgress, 0.0, 1.0);
    float blurFactor = sin(p * 3.14159) * 0.05;
    vec2 offset = vec2(-p * 1.5, 0.0);
    vec4 a = texture(uTextureA, vUv + offset + vec2(blurFactor, 0.0));
    vec4 b = texture(uTextureB, vUv + offset + vec2(1.5, 0.0) - vec2(blurFactor, 0.0));
    vec4 c = mix(a, b, smoothstep(0.4, 0.6, p));
    if (uOutputPremultiplied > 0.5) { oColor = vec4(c.rgb * c.a, c.a); } else { oColor = c; }
}
"""

    // 13. Light Leak
    private const val LIGHT_LEAK_FRAG = """
precision highp float;
uniform sampler2D uTextureA;
uniform sampler2D uTextureB;
uniform float uProgress;
uniform float uRawProgress;
uniform vec2 uResolution;
uniform float uTime;
uniform float uDuration;
uniform vec2 uDirection;
uniform float uOutputPremultiplied;
in vec2 vUv;
out vec4 oColor;
void main() {
    float p = clamp(uProgress, 0.0, 1.0);
    vec4 a = texture(uTextureA, vUv);
    vec4 b = texture(uTextureB, vUv);
    vec4 base = mix(a, b, p);
    float intensity = sin(p * 3.14159);
    vec3 warmLight = vec3(1.0, 0.7, 0.3) * intensity * (1.0 - length(vUv - vec2(0.2, 0.2)) * 0.8);
    vec4 c = vec4(base.rgb + warmLight * 0.8, base.a);
    if (uOutputPremultiplied > 0.5) { oColor = vec4(c.rgb * c.a, c.a); } else { oColor = c; }
}
"""

    fun crossDissolve() = TransitionDefinition(
        id = CROSS_DISSOLVE_ID, name = "Cross Dissolve", family = TransitionFamily.DISSOLVE,
        version = 1, minEngineVersion = 1,
        parameters = listOf(TransitionParameterDefinition(
            "softness", "Softness", ParamType.NORMALIZED,
            ParamValue.NormalizedValue(0f),
            min = ParamValue.NormalizedValue(0f),
            max = ParamValue.NormalizedValue(0.5f))),
        shaders = mapOf("main" to ShaderSource("main", CROSS_DISSOLVE_FRAG)),
        graph = TransitionRenderGraphSpec.singlePass("main"),
        defaultDurationMs = 800,
        alphaMode = AlphaMode.OPAQUE)

    fun zoom() = TransitionDefinition(
        id = ZOOM_ID, name = "Zoom In", family = TransitionFamily.ZOOM,
        version = 1, minEngineVersion = 1,
        parameters = listOf(
            TransitionParameterDefinition("zoomAmount", "Zoom Amount", ParamType.FLOAT,
                ParamValue.FloatValue(1.6f),
                min = ParamValue.FloatValue(1.0f), max = ParamValue.FloatValue(3.0f),
                step = ParamValue.FloatValue(0.05f), animatable = true),
            TransitionParameterDefinition("edgeSoftness", "Edge Softness", ParamType.NORMALIZED,
                ParamValue.NormalizedValue(0.15f),
                min = ParamValue.NormalizedValue(0f), max = ParamValue.NormalizedValue(1f))),
        shaders = mapOf("main" to ShaderSource("main", ZOOM_FRAG)),
        graph = TransitionRenderGraphSpec.singlePass("main"),
        defaultDurationMs = 800,
        alphaMode = AlphaMode.OPAQUE)

    fun zoomOut() = TransitionDefinition(
        id = ZOOM_OUT_ID, name = "Zoom Out", family = TransitionFamily.ZOOM,
        version = 1, minEngineVersion = 1,
        parameters = emptyList(),
        shaders = mapOf("main" to ShaderSource("main", ZOOM_OUT_FRAG)),
        graph = TransitionRenderGraphSpec.singlePass("main"),
        defaultDurationMs = 800,
        alphaMode = AlphaMode.OPAQUE)

    fun slideLeft() = TransitionDefinition(
        id = SLIDE_LEFT_ID, name = "Slide Left", family = TransitionFamily.SLIDE,
        version = 1, minEngineVersion = 1,
        parameters = emptyList(),
        shaders = mapOf("main" to ShaderSource("main", SLIDE_LEFT_FRAG)),
        graph = TransitionRenderGraphSpec.singlePass("main"),
        defaultDurationMs = 600,
        alphaMode = AlphaMode.OPAQUE)

    fun slideRight() = TransitionDefinition(
        id = SLIDE_RIGHT_ID, name = "Slide Right", family = TransitionFamily.SLIDE,
        version = 1, minEngineVersion = 1,
        parameters = emptyList(),
        shaders = mapOf("main" to ShaderSource("main", SLIDE_RIGHT_FRAG)),
        graph = TransitionRenderGraphSpec.singlePass("main"),
        defaultDurationMs = 600,
        alphaMode = AlphaMode.OPAQUE)

    fun pushUp() = TransitionDefinition(
        id = PUSH_UP_ID, name = "Push Up", family = TransitionFamily.PUSH,
        version = 1, minEngineVersion = 1,
        parameters = emptyList(),
        shaders = mapOf("main" to ShaderSource("main", PUSH_UP_FRAG)),
        graph = TransitionRenderGraphSpec.singlePass("main"),
        defaultDurationMs = 600,
        alphaMode = AlphaMode.OPAQUE)

    fun wipe() = TransitionDefinition(
        id = WIPE_ID, name = "Wipe", family = TransitionFamily.WIPE,
        version = 1, minEngineVersion = 1,
        parameters = listOf(
            TransitionParameterDefinition("feather", "Feather", ParamType.NORMALIZED,
                ParamValue.NormalizedValue(0.05f),
                min = ParamValue.NormalizedValue(0.0f), max = ParamValue.NormalizedValue(0.2f))),
        shaders = mapOf("main" to ShaderSource("main", WIPE_FRAG)),
        graph = TransitionRenderGraphSpec.singlePass("main"),
        defaultDurationMs = 700,
        alphaMode = AlphaMode.OPAQUE)

    fun flash() = TransitionDefinition(
        id = FLASH_ID, name = "White Flash", family = TransitionFamily.LIGHT,
        version = 1, minEngineVersion = 1,
        parameters = emptyList(),
        shaders = mapOf("main" to ShaderSource("main", FLASH_FRAG)),
        graph = TransitionRenderGraphSpec.singlePass("main"),
        defaultDurationMs = 450,
        alphaMode = AlphaMode.OPAQUE)

    fun glitch() = TransitionDefinition(
        id = GLITCH_ID, name = "Glitch Cut", family = TransitionFamily.GLITCH,
        version = 1, minEngineVersion = 1,
        parameters = emptyList(),
        shaders = mapOf("main" to ShaderSource("main", GLITCH_FRAG)),
        graph = TransitionRenderGraphSpec.singlePass("main"),
        defaultDurationMs = 500,
        alphaMode = AlphaMode.OPAQUE)

    fun blur() = TransitionDefinition(
        id = BLUR_ID, name = "Blur Zoom", family = TransitionFamily.BLUR,
        version = 1, minEngineVersion = 1,
        parameters = emptyList(),
        shaders = mapOf("main" to ShaderSource("main", BLUR_FRAG)),
        graph = TransitionRenderGraphSpec.singlePass("main"),
        defaultDurationMs = 700,
        alphaMode = AlphaMode.OPAQUE)

    fun spin() = TransitionDefinition(
        id = SPIN_ID, name = "Spin 360", family = TransitionFamily.CAMERA,
        version = 1, minEngineVersion = 1,
        parameters = emptyList(),
        shaders = mapOf("main" to ShaderSource("main", SPIN_FRAG)),
        graph = TransitionRenderGraphSpec.singlePass("main"),
        defaultDurationMs = 750,
        alphaMode = AlphaMode.OPAQUE)

    fun whipPan() = TransitionDefinition(
        id = WHIP_PAN_ID, name = "Whip Pan", family = TransitionFamily.CAMERA,
        version = 1, minEngineVersion = 1,
        parameters = emptyList(),
        shaders = mapOf("main" to ShaderSource("main", WHIP_PAN_FRAG)),
        graph = TransitionRenderGraphSpec.singlePass("main"),
        defaultDurationMs = 550,
        alphaMode = AlphaMode.OPAQUE)

    fun lightLeak() = TransitionDefinition(
        id = LIGHT_LEAK_ID, name = "Light Leak", family = TransitionFamily.LIGHT,
        version = 1, minEngineVersion = 1,
        parameters = emptyList(),
        shaders = mapOf("main" to ShaderSource("main", LIGHT_LEAK_FRAG)),
        graph = TransitionRenderGraphSpec.singlePass("main"),
        defaultDurationMs = 800,
        alphaMode = AlphaMode.OPAQUE)

    fun allBuiltins(): List<TransitionDefinition> = listOf(
        crossDissolve(),
        zoom(),
        zoomOut(),
        slideLeft(),
        slideRight(),
        pushUp(),
        wipe(),
        flash(),
        glitch(),
        blur(),
        spin(),
        whipPan(),
        lightLeak()
    )

    fun builtinProvider() = object : TransitionProvider {
        override fun loadDefinitions() = allBuiltins()
    }
}
