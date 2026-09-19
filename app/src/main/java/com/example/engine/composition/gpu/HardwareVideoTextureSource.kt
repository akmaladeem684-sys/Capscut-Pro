package com.example.engine.composition.gpu

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import android.util.Log
import android.view.Surface
import com.example.domain.model.VideoClip

/**
 * Manages hardware video frame decoding directly into an OpenGL OES texture via SurfaceTexture.
 * Completely avoids loading every video frame as a full-resolution CPU Bitmap.
 */
class HardwareVideoTextureSource {
  companion object {
    private const val TAG = "HwVideoTextureSource"
  }

  var oesTextureId: Int = 0
    private set
  var surfaceTexture: SurfaceTexture? = null
    private set
  var decoderSurface: Surface? = null
    private set

  private val transformMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

  fun setup() {
    if (oesTextureId == 0) {
      val textures = IntArray(1)
      GLES20.glGenTextures(1, textures, 0)
      oesTextureId = textures[0]
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

      val st = SurfaceTexture(oesTextureId)
      surfaceTexture = st
      decoderSurface = Surface(st)
    }
  }

  fun updateTexImage(): FloatArray {
    val st = surfaceTexture ?: return transformMatrix
    try {
      st.updateTexImage()
      st.getTransformMatrix(transformMatrix)
    } catch (e: Exception) {
      Log.w(TAG, "updateTexImage ignored: ${e.message}")
    }
    return transformMatrix
  }

  fun release() {
    decoderSurface?.release()
    decoderSurface = null

    surfaceTexture?.release()
    surfaceTexture = null

    if (oesTextureId != 0) {
      val textures = intArrayOf(oesTextureId)
      GLES20.glDeleteTextures(1, textures, 0)
      oesTextureId = 0
    }
    Log.d(TAG, "HardwareVideoTextureSource released")
  }
}
