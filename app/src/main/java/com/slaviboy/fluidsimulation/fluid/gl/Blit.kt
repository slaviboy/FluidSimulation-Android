package com.slaviboy.fluidsimulation.fluid.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Shared fullscreen-quad geometry (two triangles covering clip space) bound to
 * attribute location 0 (`aPosition`). Ports the WebGL fluid simulation's `blit`
 * closure, which every shader pass uses to draw into its target framebuffer.
 */
object Blit {
    private var vbo = 0
    private var ibo = 0

    var drawingBufferWidth: Int = 0
    var drawingBufferHeight: Int = 0

    fun init() {
        val vertices = floatArrayOf(-1f, -1f, -1f, 1f, 1f, 1f, 1f, -1f)
        val indices = shortArrayOf(0, 1, 2, 0, 2, 3)

        val vboIds = IntArray(1)
        GLES30.glGenBuffers(1, vboIds, 0)
        vbo = vboIds[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(vertices).apply { position(0) }
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vertexBuffer, GLES30.GL_STATIC_DRAW)

        val iboIds = IntArray(1)
        GLES30.glGenBuffers(1, iboIds, 0)
        ibo = iboIds[0]
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        val indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer().put(indices).apply { position(0) }
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, indexBuffer, GLES30.GL_STATIC_DRAW)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
    }

    /** target == null means draw to the default (on-screen) framebuffer. */
    fun blit(target: Fbo?, clear: Boolean = false) {
        if (target == null) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, drawingBufferWidth, drawingBufferHeight)
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer)
            GLES30.glViewport(0, 0, target.width, target.height)
        }
        if (clear) {
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_SHORT, 0)
    }
}
