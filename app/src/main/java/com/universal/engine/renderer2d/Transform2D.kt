package com.universal.engine.renderer2d

/** Immutable 2D placement. All animation output resolves into this. */
data class Transform2D(
    val translationX: Float = 0f, val translationY: Float = 0f,
    val scaleX: Float = 1f, val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val anchorX: Float = 0.5f, val anchorY: Float = 0.5f,
    val opacity: Float = 1f,
    val cropLeft: Float = 0f, val cropTop: Float = 0f,
    val cropRight: Float = 0f, val cropBottom: Float = 0f,
    val mirrored: Boolean = false
) {
    fun matrix(): FloatArray {
        val rad = Math.toRadians(rotationDegrees.toDouble())
        val c = Math.cos(rad).toFloat()
        val s = Math.sin(rad).toFloat()
        val m = FloatArray(16)
        m[0] = c * scaleX; m[1] = s * scaleX
        m[4] = -s * scaleY; m[5] = c * scaleY
        m[10] = 1f
        m[12] = translationX
        m[13] = translationY
        m[15] = 1f
        return m
    }

    companion object { val IDENTITY = Transform2D() }
}
