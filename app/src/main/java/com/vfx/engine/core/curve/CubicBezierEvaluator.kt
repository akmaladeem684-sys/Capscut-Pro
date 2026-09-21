package com.vfx.engine.core.curve

import kotlin.math.abs

/**
 * High-precision, zero-allocation Cubic Bézier curve evaluator using Newton-Raphson iteration
 * with binary search fallback.
 *
 * Given control handles P1=(x1, y1) and P2=(x2, y2), solves B_x(t) = x for t,
 * then returns B_y(t).
 */
object CubicBezierEvaluator {

  private const val NEWTON_ITERATIONS = 8
  private const val NEWTON_MIN_SLOPE = 1e-6f
  private const val SUBDIVISION_PRECISION = 1e-7f
  private const val SUBDIVISION_MAX_ITERATIONS = 10

  fun sampleX(t: Float, x1: Float, x2: Float): Float {
    // 3*(1-t)^2*t*x1 + 3*(1-t)*t^2*x2 + t^3
    val oneMinusT = 1.0f - t
    return 3.0f * oneMinusT * oneMinusT * t * x1 +
        3.0f * oneMinusT * t * t * x2 +
        t * t * t
  }

  fun sampleY(t: Float, y1: Float, y2: Float): Float {
    // 3*(1-t)^2*t*y1 + 3*(1-t)*t^2*y2 + t^3
    val oneMinusT = 1.0f - t
    return 3.0f * oneMinusT * oneMinusT * t * y1 +
        3.0f * oneMinusT * t * t * y2 +
        t * t * t
  }

  fun sampleDerivativeX(t: Float, x1: Float, x2: Float): Float {
    // d/dt B_x(t) = 3*(1-t)^2*x1 + 6*(1-t)*t*(x2 - x1) + 3*t^2*(1 - x2)
    val oneMinusT = 1.0f - t
    return 3.0f * oneMinusT * oneMinusT * x1 +
        6.0f * oneMinusT * t * (x2 - x1) +
        3.0f * t * t * (1.0f - x2)
  }

  /**
   * Evaluates y given x in [0, 1] and Bézier control handles (x1, y1), (x2, y2).
   * Guaranteed zero-allocation loop.
   */
  fun evaluate(x1: Float, y1: Float, x2: Float, y2: Float, x: Float): Float {
    if (x <= 0.0f) return 0.0f
    if (x >= 1.0f) return 1.0f

    // Linear speed-up if control points form linear curve
    if (x1 == y1 && x2 == y2) return x

    val t = solveCurveX(x, x1, x2)
    return sampleY(t, y1, y2)
  }

  private fun solveCurveX(x: Float, x1: Float, x2: Float): Float {
    // First try Newton-Raphson iteration
    var t = x
    for (i in 0 until NEWTON_ITERATIONS) {
      val currentX = sampleX(t, x1, x2) - x
      if (abs(currentX) < SUBDIVISION_PRECISION) {
        return t
      }
      val dX = sampleDerivativeX(t, x1, x2)
      if (abs(dX) < NEWTON_MIN_SLOPE) {
        break
      }
      t -= currentX / dX
    }

    // Fallback to binary subdivision if Newton-Raphson didn't converge or hit zero slope
    var intervalStart = 0.0f
    var intervalEnd = 1.0f
    t = x

    for (i in 0 until SUBDIVISION_MAX_ITERATIONS) {
      val currentX = sampleX(t, x1, x2)
      if (abs(currentX - x) < SUBDIVISION_PRECISION) {
        return t
      }
      if (x > currentX) {
        intervalStart = t
      } else {
        intervalEnd = t
      }
      t = (intervalStart + intervalEnd) * 0.5f
    }

    return t
  }
}
