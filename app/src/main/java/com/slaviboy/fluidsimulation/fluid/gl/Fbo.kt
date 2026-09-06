package com.slaviboy.fluidsimulation.fluid.gl

import android.opengl.GLES30
import com.slaviboy.opengl.main.OpenGLStatic

/**
 * A single render-target: a texture attached to a framebuffer object. Ports the
 * WebGL fluid simulation's `createFBO`/FBO-object pattern.
 */
class Fbo(
    val texture: Int,
    val framebuffer: Int,
    val width: Int,
    val height: Int
) {
    val texelSizeX: Float = 1f / width
    val texelSizeY: Float = 1f / height

    /** Binds this FBO's texture to texture unit [unit] and returns the unit, for direct use as a uniform value. */
    fun attach(unit: Int): Int {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        return unit
    }

    fun delete() {
        GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
    }

    companion object {
        fun create(width: Int, height: Int, internalFormat: Int, format: Int, type: Int, filterParam: Int): Fbo {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

            val textureIds = IntArray(1)
            GLES30.glGenTextures(1, textureIds, 0)
            val texture = textureIds[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filterParam)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filterParam)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, null)

            val framebufferIds = IntArray(1)
            GLES30.glGenFramebuffers(1, framebufferIds, 0)
            val framebuffer = framebufferIds[0]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0)
            GLES30.glViewport(0, 0, width, height)
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            OpenGLStatic.checkGlError("Fbo.create")

            return Fbo(texture, framebuffer, width, height)
        }
    }
}
