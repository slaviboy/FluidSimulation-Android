package com.slaviboy.fluidsimulation.fluid.gl

import android.opengl.GLES30
import com.slaviboy.opengl.main.OpenGLStatic

/**
 * Compiles and links a vertex+fragment shader pair into a GL program, then
 * introspects all active uniforms into a name->location map so callers can look
 * one up by the name it has in the GLSL source instead of tracking locations by hand.
 */
class ShaderProgram(vertexSource: String, fragmentSource: String) {

    val program: Int
    val uniforms: Map<String, Int>

    init {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)

        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == GLES30.GL_FALSE) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("Could not link program: $log")
        }
        OpenGLStatic.checkGlError("glLinkProgram")

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)

        uniforms = introspectUniforms(program)
    }

    fun bind() {
        GLES30.glUseProgram(program)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == GLES30.GL_FALSE) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Could not compile shader ($type): $log\n$source")
        }
        return shader
    }

    private fun introspectUniforms(program: Int): Map<String, Int> {
        val count = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_ACTIVE_UNIFORMS, count, 0)

        val result = HashMap<String, Int>(count[0])
        val size = IntArray(1)
        val type = IntArray(1)
        for (i in 0 until count[0]) {
            val name = GLES30.glGetActiveUniform(program, i, size, 0, type, 0)
            result[name] = GLES30.glGetUniformLocation(program, name)
        }
        return result
    }
}
