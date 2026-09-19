package com.example.engine.composition.gpu

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log

object GlShaderUtil {
  private const val TAG = "GlShaderUtil"

  fun loadShader(shaderType: Int, source: String): Int {
    var shader = GLES20.glCreateShader(shaderType)
    checkGlError("glCreateShader type=$shaderType")
    GLES20.glShaderSource(shader, source)
    GLES20.glCompileShader(shader)
    val compiled = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
    if (compiled[0] == 0) {
      val log = GLES20.glGetShaderInfoLog(shader)
      Log.e(TAG, "Could not compile shader $shaderType: $log")
      GLES20.glDeleteShader(shader)
      shader = 0
    }
    return shader
  }

  fun createProgram(vertexSource: String, fragmentSource: String): Int {
    val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
    if (vertexShader == 0) return 0

    val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
    if (pixelShader == 0) return 0

    var program = GLES20.glCreateProgram()
    checkGlError("glCreateProgram")
    if (program == 0) {
      Log.e(TAG, "Could not create program")
      return 0
    }
    GLES20.glAttachShader(program, vertexShader)
    checkGlError("glAttachShader vertex")
    GLES20.glAttachShader(program, pixelShader)
    checkGlError("glAttachShader pixel")
    GLES20.glLinkProgram(program)
    val linkStatus = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
    if (linkStatus[0] != GLES20.GL_TRUE) {
      val log = GLES20.glGetProgramInfoLog(program)
      Log.e(TAG, "Could not link program: $log")
      GLES20.glDeleteProgram(program)
      program = 0
    }
    return program
  }

  fun createTexture(target: Int = GLES20.GL_TEXTURE_2D): Int {
    val textures = IntArray(1)
    GLES20.glGenTextures(1, textures, 0)
    val texId = textures[0]
    GLES20.glBindTexture(target, texId)
    GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glBindTexture(target, 0)
    return texId
  }

  fun createOesTexture(): Int {
    return createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
  }

  fun uploadBitmapToTexture(bitmap: Bitmap, targetTexId: Int): Int {
    if (bitmap.isRecycled) return targetTexId
    var texId = targetTexId
    if (texId == 0) {
      texId = createTexture(GLES20.GL_TEXTURE_2D)
    }
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
    try {
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to upload bitmap to OpenGL texture", e)
    }
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    return texId
  }

  fun checkGlError(op: String) {
    val error = GLES20.glGetError()
    if (error != GLES20.GL_NO_ERROR) {
      Log.e(TAG, "$op: glError 0x${Integer.toHexString(error)}")
    }
  }
}
