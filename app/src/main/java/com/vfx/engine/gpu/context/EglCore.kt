package com.vfx.engine.gpu.context

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface

class EglCore(sharedContext: EGLContext? = null) {
  var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private set
  var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private set
  private var eglConfig: EGLConfig? = null

  init {
    eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
      throw RuntimeException("EGLDisplay failed")
    }

    val version = IntArray(2)
    if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
      throw RuntimeException("EGL initialize failed")
    }

    val attribList = intArrayOf(
      EGL14.EGL_RED_SIZE, 8,
      EGL14.EGL_GREEN_SIZE, 8,
      EGL14.EGL_BLUE_SIZE, 8,
      EGL14.EGL_ALPHA_SIZE, 8,
      EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT or 0x0040, // ES3
      EGL14.EGL_NONE
    )

    val configs = arrayOfNulls<EGLConfig>(1)
    val numConfigs = IntArray(1)
    EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
    eglConfig = configs[0]

    val contextAttribs = intArrayOf(
      EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
      EGL14.EGL_NONE
    )

    val parent = sharedContext ?: EGL14.EGL_NO_CONTEXT
    eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, parent, contextAttribs, 0)
  }

  fun createWindowSurface(surface: Surface): EGLSurface {
    val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
    return EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0)
  }

  fun makeCurrent(surface: EGLSurface) {
    EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)
  }

  fun swapBuffers(surface: EGLSurface): Boolean {
    return EGL14.eglSwapBuffers(eglDisplay, surface)
  }

  fun release() {
    if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
      EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
      EGL14.eglDestroyContext(eglDisplay, eglContext)
      EGL14.eglReleaseThread()
      EGL14.eglTerminate(eglDisplay)
    }
  }
}
