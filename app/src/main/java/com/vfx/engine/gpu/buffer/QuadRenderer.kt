package com.vfx.engine.gpu.buffer

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class QuadRenderer {
  private val vertexBuffer: FloatBuffer

  companion object {
    private val QUAD_COORDS = floatArrayOf(
      -1.0f, -1.0f,
       1.0f, -1.0f,
      -1.0f,  1.0f,
       1.0f,  1.0f
    )
  }

  init {
    vertexBuffer = ByteBuffer.allocateDirect(QUAD_COORDS.size * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
      .put(QUAD_COORDS)
    vertexBuffer.position(0)
  }

  fun drawQuad(positionHandle: Int) {
    GLES20.glEnableVertexAttribArray(positionHandle)
    GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    GLES20.glDisableVertexAttribArray(positionHandle)
  }
}
