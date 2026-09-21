package com.universal.engine.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** One shared full-screen quad VAO used by every pass-through shader in the engine. */
class FullScreenQuad {
    private val vao = IntArray(1)
    private val vbo = IntArray(1)
    private val vbo2 = IntArray(1)
    private var created = false

    private val POS = floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f)
    private val UV  = floatArrayOf( 0f,1f, 1f,1f, 0f,0f, 1f,0f)

    fun bind(program: ShaderManager.ShaderProgram) {
        if (!created) {
            GLES30.glGenVertexArrays(1, vao, 0)
            GLES30.glBindVertexArray(vao[0])
            GLES30.glGenBuffers(1, vbo, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
            buffer(POS).also { GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, it.capacity() * 4, it, GLES30.GL_STATIC_DRAW) }
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
            GLES30.glGenBuffers(1, vbo2, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo2[0])
            buffer(UV).also { GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, it.capacity() * 4, it, GLES30.GL_STATIC_DRAW) }
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, 0)
            GLES30.glBindVertexArray(0)
            created = true
        }
        GLES30.glBindVertexArray(vao[0])
    }

    fun draw() = GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

    fun unbind() = GLES30.glBindVertexArray(0)

    fun release() {
        if (created) {
            GLES30.glDeleteVertexArrays(1, vao, 0)
            GLES30.glDeleteBuffers(1, vbo, 0)
            GLES30.glDeleteBuffers(1, vbo2, 0)
            created = false
        }
    }

    private fun buffer(f: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(f.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(f); position(0)
        }
}
