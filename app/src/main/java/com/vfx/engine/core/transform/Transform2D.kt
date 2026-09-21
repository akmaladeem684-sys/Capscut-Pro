package com.vfx.engine.core.transform

import com.vfx.engine.core.math.Vec2

data class Transform2D(
  val translation: Vec2 = Vec2(0f, 0f),
  val scale: Vec2 = Vec2(1f, 1f),
  val rotationDegrees: Float = 0f,
  val anchorPoint: Vec2 = Vec2(0.5f, 0.5f),
  val skew: Vec2 = Vec2(0f, 0f)
)

object CoordinateSystem {
  fun uToNdc(u: Float): Float = (u * 2f) - 1.0f
  fun vToNdc(v: Float): Float = 1.0f - (v * 2f)
}
