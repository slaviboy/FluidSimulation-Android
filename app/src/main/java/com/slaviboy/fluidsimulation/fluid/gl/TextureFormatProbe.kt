package com.slaviboy.fluidsimulation.fluid.gl

import android.opengl.GLES30

/**
 * Runtime FBO-completeness probe for floating point render-target formats, ported
 * from the WebGL fluid simulation's `getSupportedFormat`/`supportRenderTextureFormat`.
 * Rather than assuming `EXT_color_buffer_float`/half-float support from an
 * extension string, this actually tries to render to a throwaway texture of each
 * candidate format and falls back R16F -> RG16F -> RGBA16F on failure, exactly
 * matching upstream behavior and degrading gracefully on devices lacking full
 * float-render support.
 */
object TextureFormatProbe {

    data class FormatSet(val internalFormat: Int, val format: Int)

    class SupportedFormats(
        val formatRGBA: FormatSet,
        val formatRG: FormatSet,
        val formatR: FormatSet,
        val halfFloatTexType: Int
    )

    fun probe(): SupportedFormats {
        val halfFloatTexType = GLES30.GL_HALF_FLOAT
        val formatRGBA = getSupportedFormat(GLES30.GL_RGBA16F, GLES30.GL_RGBA, halfFloatTexType)
        val formatRG = getSupportedFormat(GLES30.GL_RG16F, GLES30.GL_RG, halfFloatTexType)
        val formatR = getSupportedFormat(GLES30.GL_R16F, GLES30.GL_RED, halfFloatTexType)
        return SupportedFormats(formatRGBA, formatRG, formatR, halfFloatTexType)
    }

    private fun getSupportedFormat(internalFormat: Int, format: Int, type: Int): FormatSet {
        if (!supportRenderTextureFormat(internalFormat, format, type)) {
            return when (internalFormat) {
                GLES30.GL_R16F -> getSupportedFormat(GLES30.GL_RG16F, GLES30.GL_RG, type)
                GLES30.GL_RG16F -> getSupportedFormat(GLES30.GL_RGBA16F, GLES30.GL_RGBA, type)
                else -> FormatSet(GLES30.GL_RGBA16F, GLES30.GL_RGBA)
            }
        }
        return FormatSet(internalFormat, format)
    }

    private fun supportRenderTextureFormat(internalFormat: Int, format: Int, type: Int): Boolean {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        val texture = textureIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internalFormat, 4, 4, 0, format, type, null)

        val framebufferIds = IntArray(1)
        GLES30.glGenFramebuffers(1, framebufferIds, 0)
        val framebuffer = framebufferIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0)

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        val supported = status == GLES30.GL_FRAMEBUFFER_COMPLETE

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        GLES30.glDeleteTextures(1, intArrayOf(texture), 0)

        return supported
    }
}
