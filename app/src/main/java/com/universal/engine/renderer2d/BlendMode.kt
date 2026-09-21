package com.universal.engine.renderer2d

enum class BlendMode(val glValue: Int, val shaderIndex: Int) {
    NORMAL(android.opengl.GLES30.GL_FUNC_ADD, 0),
    MULTIPLY(android.opengl.GLES30.GL_FUNC_ADD, 1),
    SCREEN(android.opengl.GLES30.GL_FUNC_ADD, 2),
    OVERLAY(android.opengl.GLES30.GL_FUNC_ADD, 3),
    ADD(android.opengl.GLES30.GL_FUNC_ADD, 4)
}
