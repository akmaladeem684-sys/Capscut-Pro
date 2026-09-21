package com.vfx.engine.core.math

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object MathUtils {
  const val PI = Math.PI.toFloat()
  const val DEG_TO_RAD = PI / 180.0f
  const val RAD_TO_DEG = 180.0f / PI

  fun clamp(value: Float, min: Float, max: Float): Float = max(min, min(max, value))

  fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

  fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f)
    return t * t * (3.0f - 2.0f * t)
  }
}

data class Vec2(val x: Float = 0f, val y: Float = 0f) {
  operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
  operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
  operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)
}

data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
  operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
  operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
  operator fun times(scalar: Float) = Vec3(x * scalar, y * scalar, z * scalar)
}

data class Vec4(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f, val w: Float = 1f) {
  operator fun plus(other: Vec4) = Vec4(x + other.x, y + other.y, z + other.z, w + other.w)
  operator fun times(scalar: Float) = Vec4(x * scalar, y * scalar, z * scalar, w * scalar)
}

class Mat3(val values: FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
class Mat4(val values: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f })

data class RectF(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f) {
  val width: Float get() = abs(right - left)
  val height: Float get() = abs(bottom - top)
}

data class Color(val r: Float = 0f, val g: Float = 0f, val b: Float = 0f, val a: Float = 1f) {
  fun toPremultiplied(): Color = Color(r * a, g * a, b * a, a)
  fun toUnpremultiplied(): Color = if (a > 0.0001f) Color(r / a, g / a, b / a, a) else Color(0f, 0f, 0f, 0f)
}
