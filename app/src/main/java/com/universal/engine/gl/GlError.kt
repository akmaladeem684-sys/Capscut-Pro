package com.universal.engine.gl

import android.opengl.GLES30
import com.universal.engine.errors.EngineError

object GlError {
    fun check(context: String) {
        val e = GLES30.glGetError()
        if (e != GLES30.GL_NO_ERROR) {
            throw EngineError.RenderError("GL error 0x${e.toUInt().toString(16)} at $context")
        }
    }
}
