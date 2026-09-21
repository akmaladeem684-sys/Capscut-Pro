package com.universal.engine.renderer3d

import kotlin.math.tan

data class Camera(
    val eyeX: Float = 0f, val eyeY: Float = 0f, val eyeZ: Float = 3f,
    val centerX: Float = 0f, val centerY: Float = 0f, val centerZ: Float = 0f,
    val upX: Float = 0f, val upY: Float = 1f, val upZ: Float = 0f,
    val fovDegrees: Float = 60f,
    val orthographic: Boolean = false,
    val orthoHeight: Float = 2f
) {
    private val up: FloatArray get() = floatArrayOf(upX, upY, upZ)

    fun viewMatrix(): FloatArray {
        val zx = eyeX - centerX
        val zy = eyeY - centerY
        val zz = eyeZ - centerZ
        val zl = kotlin.math.sqrt(zx*zx + zy*zy + zz*zz).coerceAtLeast(1e-8f)
        val z = floatArrayOf(zx/zl, zy/zl, zz/zl)
        var x = cross(up, z)
        val xl = len(x)
        x = floatArrayOf(x[0]/xl, x[1]/xl, x[2]/xl)
        val y = cross(z, x)
        return floatArrayOf(
            x[0], y[0], z[0], 0f,
            x[1], y[1], z[1], 0f,
            x[2], y[2], z[2], 0f,
            -(x[0]*eyeX + x[1]*eyeY + x[2]*eyeZ),
            -(y[0]*eyeX + y[1]*eyeY + y[2]*eyeZ),
            -(z[0]*eyeX + z[1]*eyeY + z[2]*eyeZ),
            1f
        )
    }

    fun projectionMatrix(aspect: Float, near: Float = 0.1f, far: Float = 100f): FloatArray {
        if (orthographic) {
            val h = orthoHeight / 2f
            val w = h * aspect
            return floatArrayOf(
                1f/w, 0f, 0f, 0f,  0f, 1f/h, 0f, 0f,  0f, 0f, -2f/(far-near), 0f,
                0f, 0f, -(far+near)/(far-near), 1f
            )
        }
        val f = 1f / tan(Math.toRadians(fovDegrees / 2.0)).toFloat()
        return floatArrayOf(
            f/aspect, 0f, 0f, 0f,  0f, f, 0f, 0f,  0f, 0f, (far+near)/(near-far), -1f,
            0f, 0f, 2f*far*near/(near-far), 0f
        )
    }

    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
    private fun len(v: FloatArray) = kotlin.math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]).coerceAtLeast(1e-8f)
}

data class Light(
    val type: Type = Type.DIRECTIONAL,
    val x: Float = 1f, val y: Float = 2f, val z: Float = 3f,
    val r: Float = 1f, val g: Float = 1f, val b: Float = 1f,
    val intensity: Float = 1f,
    val range: Float = 20f
) {
    enum class Type { DIRECTIONAL, POINT }
}
