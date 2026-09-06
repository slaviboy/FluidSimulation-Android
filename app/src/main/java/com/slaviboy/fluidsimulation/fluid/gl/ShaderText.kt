package com.slaviboy.fluidsimulation.fluid.gl

import android.content.Context
import com.slaviboy.opengl.main.OpenGLStatic

/**
 * Loads GLSL ES 3.00 shader source from a `res/raw` glsl file and assembles the
 * final compiled source. `#version` must be the very first line of the source, so any
 * `#define` keyword injection (used by [DisplayMaterial] for SHADING/BLOOM/SUNRAYS
 * variants) must be spliced in between the version line and the raw file body,
 * not simply prepended to the raw text.
 */
object ShaderText {

    fun load(context: Context, resourceId: Int, defines: List<String> = emptyList()): String {
        val body = OpenGLStatic.readTextFileFromResource(context, resourceId)
        val builder = StringBuilder()
        builder.append("#version 300 es\n")
        for (define in defines) {
            builder.append("#define ").append(define).append('\n')
        }
        builder.append(body)
        return builder.toString()
    }
}
