package com.universal.engine.gl

import android.opengl.GLES30
import com.universal.engine.errors.EngineError

class ShaderManager(private val resources: GpuResourceManager) {

    private val cache = HashMap<String, ShaderProgram>()

    fun getOrCompile(key: String, vertexSrc: String, fragmentSrc: String, attributes: Map<Int, String> = emptyMap()): ShaderProgram {
        cache[key]?.let { return it }
        return compile(key, vertexSrc, fragmentSrc, attributes).also { cache[key] = it }
    }

    fun compile(key: String, vertexSrc: String, fragmentSrc: String, attributes: Map<Int, String> = emptyMap()): ShaderProgram {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc, "$key.vsh")
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc, "$key.fsh")
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        attributes.forEach { (loc, name) -> GLES30.glBindAttribLocation(program, loc, name) }
        GLES30.glLinkProgram(program)
        val linked = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (linked[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw EngineError.ShaderError("link failed for $key", "$vertexSrc\n$fragmentSrc", log)
        }
        val res = resources.register(GpuResourceManager.Resource.ShaderProgram(program)) as GpuResourceManager.Resource.ShaderProgram
        return ShaderProgram(res, this)
    }

    private fun compileShader(type: Int, src: String, name: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw EngineError.ShaderError("compile failed: $name", src, log)
        }
        return shader
    }

    fun invalidateAll() {
        synchronized(cache) { cache.clear() }
    }

    fun releaseAll() {
        synchronized(cache) {
            cache.values.forEach { resources.unregister(it.resource) }
            cache.clear()
        }
    }

    class ShaderProgram internal constructor(val resource: GpuResourceManager.Resource.ShaderProgram, private val mgr: ShaderManager) {
        val id: Int get() = resource.id
        private val uniformCache = HashMap<String, Int>()
        private val currentUniforms = HashMap<String, Any>()

        fun use(): ShaderProgram {
            GLES30.glUseProgram(id)
            return this
        }

        fun uniformLocation(name: String): Int =
            uniformCache.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

        fun setFloat(name: String, v: Float) { setIfChanged(name, v) { GLES30.glUniform1f(uniformLocation(name), v) } }
        fun setInt(name: String, v: Int) { setIfChanged(name, v) { GLES30.glUniform1i(uniformLocation(name), v) } }
        fun setVec2(name: String, a: Float, b: Float) { setIfChanged(name, floatArrayOf(a, b)) { GLES30.glUniform2f(uniformLocation(name), a, b) } }
        fun setVec3f(name: String, x: Float, y: Float, z: Float) { setIfChanged(name, floatArrayOf(x, y, z)) { GLES30.glUniform3f(uniformLocation(name), x, y, z) } }
        fun setVec4(name: String, a: Float, b: Float, c: Float, d: Float) { setIfChanged(name, floatArrayOf(a, b, c, d)) { GLES30.glUniform4f(uniformLocation(name), a, b, c, d) } }
        fun setVec4(name: String, v: FloatArray) { setIfChanged(name, v) { GLES30.glUniform4fv(uniformLocation(name), 1, v, 0) } }
        fun setMat4(name: String, m: FloatArray) { setIfChanged(name, m) { GLES30.glUniformMatrix4fv(uniformLocation(name), 1, false, m, 0) } }

        fun setTexture(name: String, unit: Int, textureId: Int, target: Int) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
            GLES30.glBindTexture(target, textureId)
            setInt(name, unit)
        }

        @Suppress("UNCHECKED_CAST")
        private inline fun <T> setIfChanged(name: String, value: T, setter: () -> Unit) {
            val prev = currentUniforms[name]
            if (prev != null && prev == value) return
            setter()
            currentUniforms[name] = value as Any
        }

        fun invalidateUniformCache() { currentUniforms.clear() }
    }
}
