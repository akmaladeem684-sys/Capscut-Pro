package com.vfx.engine.gpu.buffer

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Standard Fullscreen Quad setup using OpenGL ES 3.0 VAO/VBO.
 * Position coords [-1, 1] and TexCoords [0, 1] interleaved.
 * Zero allocation per frame.
 */
class QuadRenderer {
  private var vaoId: Int = 0
  private var vboId: Int = 0

  companion object {
    // Interleaved [x, y, u, v] for 4 vertices forming a triangle strip
    private val VERTEX_DATA = floatArrayOf(
      // Position   // TexCoord
      -1.0f, -1.0f,  0.0f, 0.0f,
       1.0f, -1.0f,  1.0f, 0.0f,
      -1.0f,  1.0f,  0.0f, 1.0f,
       1.0f,  1.0f,  1.0f, 1.0f
    )
    private const val FLOAT_SIZE = 4
    private const val STRIDE = 4 * FLOAT_SIZE // 4 floats per vertex
  }

  init {
    val vaos = IntArray(1)
    GLES30.glGenVertexArrays(1, vaos, 0)
    vaoId = vaos[0]

    val vbos = IntArray(1)
    GLES30.glGenBuffers(1, vbos, 0)
    vboId = vbos[0]

    val vertexBuffer = ByteBuffer.allocateDirect(VERTEX_DATA.size * FLOAT_SIZE)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
      .put(VERTEX_DATA)
    vertexBuffer.position(0)

    GLES30.glBindVertexArray(vaoId)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
    GLES30.glBufferData(
      GLES30.GL_ARRAY_BUFFER,
      VERTEX_DATA.size * FLOAT_SIZE,
      vertexBuffer,
      GLES30.GL_STATIC_DRAW
    )

    // Position attribute at location 0
    GLES30.glEnableVertexAttribArray(0)
    GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, STRIDE, 0)

    // TexCoord attribute at location 1
    GLES30.glEnableVertexAttribArray(1)
    GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, STRIDE, 2 * FLOAT_SIZE)

    GLES30.glBindVertexArray(0)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
  }

  fun drawQuad() {
    GLES30.glBindVertexArray(vaoId)
    GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    GLES30.glBindVertexArray(0)
  }

  fun drawQuad(positionHandle: Int, texCoordHandle: Int = -1) {
    if (vaoId != 0) {
      GLES30.glBindVertexArray(vaoId)
      if (positionHandle != 0 && positionHandle != -1) {
        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, STRIDE, 0)
      }
      if (texCoordHandle != -1) {
        GLES30.glEnableVertexAttribArray(texCoordHandle)
        GLES30.glVertexAttribPointer(texCoordHandle, 2, GLES30.GL_FLOAT, false, STRIDE, 2 * FLOAT_SIZE)
      }
      GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
      GLES30.glBindVertexArray(0)
    }
  }

  fun release() {
    if (vboId != 0) {
      val vbos = intArrayOf(vboId)
      GLES30.glDeleteBuffers(1, vbos, 0)
      vboId = 0
    }
    if (vaoId != 0) {
      val vaos = intArrayOf(vaoId)
      GLES30.glDeleteVertexArrays(1, vaos, 0)
      vaoId = 0
    }
  }
}
