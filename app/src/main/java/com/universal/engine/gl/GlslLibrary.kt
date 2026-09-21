package com.universal.engine.gl

/** Shared GLSL ES 3.00 library — concatenated into program variants, never recompiled per frame. */
object GlslLibrary {

    const val VERSION = "#version 300 es\nprecision highp float;\nprecision highp sampler2D;\n"

    const val COMMON =
        """
        float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
        float srgbToLinear(float c) { return (c <= 0.04045) ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
        vec3 srgbToLinear(vec3 c) { return vec3(srgbToLinear(c.r), srgbToLinear(c.g), srgbToLinear(c.b)); }
        float linearToSrgb(float c) { return (c <= 0.0031308) ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
        vec3 linearToSrgb(vec3 c) { return vec3(linearToSrgb(c.r), linearToSrgb(c.g), linearToSrgb(c.b)); }
        vec3 applyGamma(vec3 c, float g) { return pow(max(c, 0.0), vec3(1.0 / g)); }
        float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }
        float valueNoise(vec2 p) {
            vec2 i = floor(p), f = fract(p);
            vec2 u = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + vec2(1,0)), u.x), mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), u.x), u.y);
        }
        float fbm(vec2 p) {
            float v = 0.0, a = 0.5;
            for (int i = 0; i < 4; i++) { v += a * valueNoise(p); p *= 2.0; a *= 0.5; }
            return v;
        }
        vec2 rotateUv(vec2 uv, float radians, vec2 center) {
            float s = sin(radians), c = cos(radians);
            vec2 p = uv - center;
            return center + vec2(c * p.x - s * p.y, s * p.x + c * p.y);
        }
        """

    const val BLENDING =
        """
        vec4 blendNormal(vec4 src, vec4 dst) { return src * src.a + dst * (1.0 - src.a); }
        vec4 blendMultiply(vec4 s, vec4 d) { return vec4(s.rgb * d.rgb, s.a + d.a * (1.0 - s.a)); }
        vec4 blendScreen(vec4 s, vec4 d) { return vec4(1.0 - (1.0 - s.rgb) * (1.0 - d.rgb), s.a + d.a * (1.0 - s.a)); }
        vec4 blendOverlay(vec4 s, vec4 d) {
            vec3 r = mix(2.0 * s.rgb * d.rgb, 1.0 - 2.0 * (1.0 - s.rgb) * (1.0 - d.rgb), step(0.5, d.rgb));
            return vec4(r, s.a + d.a * (1.0 - s.a));
        }
        vec4 blendAdd(vec4 s, vec4 d) { return vec4(min(s.rgb + d.rgb, 1.0), s.a + d.a * (1.0 - s.a)); }
        vec4 applyBlend(int mode, vec4 src, vec4 dst) {
            if (mode == 0) return blendNormal(src, dst);
            if (mode == 1) return blendMultiply(src, dst);
            if (mode == 2) return blendScreen(src, dst);
            if (mode == 3) return blendOverlay(src, dst);
            return blendAdd(src, dst);
        }
        """

    const val BLUR =
        """
        vec3 blur9(sampler2D tex, vec2 uv, vec2 texelSize, float radius) {
            vec3 sum = vec3(0.0); float wsum = 0.0;
            for (int y = -1; y <= 1; y++)
            for (int x = -1; x <= 1; x++) {
                vec2 off = vec2(float(x), float(y)) * texelSize * radius;
                float w = exp(-float(x * x + y * y) * 0.5);
                sum += texture(tex, uv + off).rgb * w; wsum += w;
            }
            return sum / wsum;
        }
        """

    const val MASKING =
        """
        float roundedRectMask(vec2 uv, vec2 halfSize, vec2 halfClip) {
            vec2 d = abs(uv - vec2(0.5)) * 2.0 * halfSize;
            vec2 q = d - (halfSize * 2.0 - halfClip * 2.0);
            return 1.0 - clamp(min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) / min(halfClip.x, halfClip.y), 0.0, 1.0);
        }
        """

    const val YUV_CONVERSION =
        """
        const mat3 BT709_LIMITED = mat3(
            1.16438, 1.16438, 1.16438,
            0.0, -0.21325, 2.11240,
            1.79274, -0.53291, 0.0);
        const mat3 BT601_LIMITED = mat3(
            1.16438, 1.16438, 1.16438,
            0.0, -0.39176, 2.01723,
            1.59603, -0.81297, 0.0);
        const mat3 BT2020_LIMITED = mat3(
            1.16438, 1.16438, 1.16438,
            0.0, -0.18733, 2.14177,
            1.67867, -0.65042, 0.0);
        vec3 yuvToRgbLimited(vec3 yuv, int matrixSel) {
            vec3 rgb = matrixSel == 0 ? BT601_LIMITED * yuv
                     : matrixSel == 2 ? BT2020_LIMITED * yuv
                     : BT709_LIMITED * yuv;
            return max(rgb, 0.0);
        }
        vec3 pqToLinear(vec3 pq) {
            const float m1 = 0.1593017578125, m2 = 78.84375;
            const float c1 = 0.8359375, c2 = 18.8515625, c3 = 18.6875;
            vec3 p = pow(pq, vec3(1.0 / m2));
            vec3 d = max(c1 - c2 * p, 1e-9);
            return pow(max((p - c1) / max(c3 - c2 * p, 1e-9), 0.0), vec3(1.0 / m1)) * 10000.0;
        }
        vec3 hlgToLinear(vec3 hlg) {
            const float a = 0.17883277, b = 0.28466892, c = 0.55991073;
            return hlg <= vec3(0.5) ? hlg * hlg * 3.0 : (exp((hlg - c) / a) + b) / 12.0;
        }
        vec3 toneMapReinhard(vec3 linear, float maxL) {
            float lw = 100.0;
            vec3 t = linear / max(lw, 1.0);
            return t / (1.0 + t);
        }
        """
}
