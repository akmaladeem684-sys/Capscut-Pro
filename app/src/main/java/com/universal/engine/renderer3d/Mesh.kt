package com.universal.engine.renderer3d

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Static mesh with interleaved position/normal/uv, indexed. */
class Mesh(vertices: FloatArray, indices: ShortArray) {
    private val vao = IntArray(1)
    private val vbo = IntArray(1)
    private val ibo = IntArray(1)
    val indexCount = indices.size

    init {
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glBindVertexArray(vao[0])
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        val vb = floatBuffer(vertices)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vb.capacity() * 4, vb, GLES30.GL_STATIC_DRAW)
        val stride = 8 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 24)
        GLES30.glGenBuffers(1, ibo, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo[0])
        val ib = shortBuffer(indices)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, ib.capacity() * 2, ib, GLES30.GL_STATIC_DRAW)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vao[0])
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        GLES30.glDeleteVertexArrays(1, vao, 0)
        GLES30.glDeleteBuffers(1, vbo, 0)
        GLES30.glDeleteBuffers(1, ibo, 0)
    }

    private fun floatBuffer(f: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(f.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(f); position(0) }
    private fun shortBuffer(s: ShortArray): java.nio.ShortBuffer =
        ByteBuffer.allocateDirect(s.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(s); position(0) }

    companion object {
        fun cube(): Mesh {
            val p = floatArrayOf(
                -0.5f,-0.5f,-0.5f,  0.5f,-0.5f,-0.5f,  0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f,
                -0.5f,-0.5f, 0.5f,  0.5f,-0.5f, 0.5f,  0.5f,0.5f, 0.5f, -0.5f,0.5f, 0.5f
            )
            val verts = mutableListOf<Float>()
            val idx = mutableListOf<Short>()
            val faces = arrayOf(
                intArrayOf(0,1,2,3), intArrayOf(4,5,6,7), intArrayOf(0,1,5,4), intArrayOf(2,3,7,6), intArrayOf(1,2,6,5), intArrayOf(3,0,4,7)
            )
            val normals = arrayOf(
                floatArrayOf(0f,0f,-1f), floatArrayOf(0f,0f,1f), floatArrayOf(0f,-1f,0f),
                floatArrayOf(0f,1f,0f), floatArrayOf(1f,0f,0f), floatArrayOf(-1f,0f,0f)
            )
            var vi = 0
            faces.forEachIndexed { f, quad ->
                val n = normals[f]
                val corners = arrayOf(
                    floatArrayOf(p[quad[0]*3], p[quad[0]*3+1], p[quad[0]*3+2]),
                    floatArrayOf(p[quad[1]*3], p[quad[1]*3+1], p[quad[1]*3+2]),
                    floatArrayOf(p[quad[2]*3], p[quad[2]*3+1], p[quad[2]*3+2]),
                    floatArrayOf(p[quad[3]*3], p[quad[3]*3+1], p[quad[3]*3+2])
                )
                val uvs = arrayOf(floatArrayOf(0f,0f), floatArrayOf(1f,0f), floatArrayOf(1f,1f), floatArrayOf(0f,1f))
                for (i in 0 until 4) {
                    verts.add(corners[i][0]); verts.add(corners[i][1]); verts.add(corners[i][2])
                    verts.add(n[0]); verts.add(n[1]); verts.add(n[2])
                    verts.add(uvs[i][0]); verts.add(uvs[i][1])
                }
                idx.add(vi.toShort()); idx.add((vi+1).toShort()); idx.add((vi+2).toShort())
                idx.add(vi.toShort()); idx.add((vi+2).toShort()); idx.add((vi+3).toShort())
                vi += 4
            }
            return Mesh(verts.toFloatArray(), idx.toShortArray())
        }

        fun plane(segments: Int = 1): Mesh {
            val verts = mutableListOf<Float>()
            val idx = mutableListOf<Short>()
            for (y in 0..segments) for (x in 0..segments) {
                val u = x.toFloat() / segments
                val v = y.toFloat() / segments
                verts.add(u - 0.5f); verts.add(v - 0.5f); verts.add(0f)
                verts.add(0f); verts.add(0f); verts.add(1f)
                verts.add(u); verts.add(1f - v)
            }
            for (y in 0 until segments) for (x in 0 until segments) {
                val a = (y * (segments + 1) + x).toShort()
                val b = (a + 1).toShort()
                val c = (a + segments + 1).toShort()
                val d = (c + 1).toShort()
                idx.add(a); idx.add(b); idx.add(c)
                idx.add(b); idx.add(d); idx.add(c)
            }
            return Mesh(verts.toFloatArray(), idx.toShortArray())
        }
    }
}
