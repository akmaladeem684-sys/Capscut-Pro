package com.universal.engine.egl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface
import com.universal.engine.errors.EngineError

class EglManager {

    interface ContextLostListener { fun onContextLost() }

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var config: EGLConfig? = null
    var context: EGLContext = EGL14.EGL_NO_CONTEXT; private set
    private val contextLostListeners = mutableListOf<ContextLostListener>()

    val isInitialized: Boolean get() = display != EGL14.EGL_NO_DISPLAY && context != EGL14.EGL_NO_CONTEXT

    fun addContextLostListener(l: ContextLostListener) { contextLostListeners.add(l) }

    @Synchronized
    fun initialize(): Boolean {
        if (isInitialized) return true
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) throw EngineError.EglError("eglGetDisplay failed", EGL14.eglGetError())

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw EngineError.EglError("eglInitialize failed", EGL14.eglGetError())
        }

        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT or 0x00000040 /* EGL_OPENGL_ES3_BIT */,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) || num[0] == 0) {
            val fallback = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE
            )
            if (!EGL14.eglChooseConfig(display, fallback, 0, configs, 0, 1, num, 0) || num[0] == 0) {
                throw EngineError.EglError("no suitable EGLConfig", EGL14.eglGetError())
            }
        }
        config = configs[0]

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        if (context == EGL14.EGL_NO_CONTEXT) {
            val ctx2 = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctx2, 0)
            if (context == EGL14.EGL_NO_CONTEXT) throw EngineError.EglError("context creation failed", EGL14.eglGetError())
        }
        return true
    }

    fun makeCurrent(surface: EglSurface) {
        checkCurrent()
        if (!EGL14.eglMakeCurrent(display, surface.eglSurface, surface.eglSurface, context)) {
            checkContextLost()
            throw EngineError.EglError("makeCurrent failed", EGL14.eglGetError())
        }
    }

    fun makeCurrentOffscreen(width: Int, height: Int): EglSurface {
        initialize()
        checkCurrent()
        val attribs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        val s = EGL14.eglCreatePbufferSurface(display, config, attribs, 0)
        if (s == EGL14.EGL_NO_SURFACE) throw EngineError.EglError("pbuffer creation failed", EGL14.eglGetError())
        if (!EGL14.eglMakeCurrent(display, s, s, context)) {
            checkContextLost()
            throw EngineError.EglError("makeCurrent(pbuffer) failed", EGL14.eglGetError())
        }
        return EglSurface(s, EglSurface.Kind.OFFSCREEN)
    }

    fun createWindowSurface(surface: Surface): EglSurface {
        initialize()
        val attribs = intArrayOf(EGL14.EGL_NONE)
        val s = EGL14.eglCreateWindowSurface(display, config, surface, attribs, 0)
        if (s == EGL14.EGL_NO_SURFACE) throw EngineError.EglError("window surface creation failed", EGL14.eglGetError())
        return EglSurface(s, EglSurface.Kind.WINDOW)
    }

    fun destroySurface(surface: EglSurface) {
        if (surface.eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface.eglSurface)
        }
    }

    fun swapBuffers(surface: EglSurface): Boolean = EGL14.eglSwapBuffers(display, surface.eglSurface)

    fun setPresentationTime(surface: EglSurface, nanos: Long): Boolean =
        EGLExt.eglPresentationTimeANDROID(display, surface.eglSurface, nanos)

    @Synchronized
    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        config = null
    }

    @Synchronized
    fun reinitialize() {
        release()
        initialize()
        contextLostListeners.forEach { runCatching { it.onContextLost() } }
    }

    private fun checkCurrent() {
        if (!isInitialized) throw EngineError.EglError("EglManager not initialized", EGL14.EGL_SUCCESS)
    }

    private fun checkContextLost() {
        if (EGL14.eglGetError() == EGL14.EGL_CONTEXT_LOST) {
            contextLostListeners.forEach { runCatching { it.onContextLost() } }
        }
    }
}

class EglSurface internal constructor(internal val eglSurface: EGLSurface, val kind: Kind) {
    enum class Kind { WINDOW, OFFSCREEN }
}
