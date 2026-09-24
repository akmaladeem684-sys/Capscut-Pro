package com.example.engine.ai

import android.graphics.RectF

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun toRectF(viewWidth: Float, viewHeight: Float): RectF {
        return RectF(
            left * viewWidth,
            top * viewHeight,
            right * viewWidth,
            bottom * viewHeight
        )
    }
}

data class MotionKeyframe(
    val timestampUs: Long,
    val centerX: Float,
    val centerY: Float,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val rotationDeg: Float = 0.0f,
    val confidence: Float = 1.0f
)

data class TrackingResult(
    val targetId: String,
    val clipId: String,
    val startTimestampUs: Long,
    val endTimestampUs: Long,
    val keyframes: List<MotionKeyframe>
)
