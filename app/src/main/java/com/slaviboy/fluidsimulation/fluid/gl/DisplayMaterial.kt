package com.slaviboy.fluidsimulation.fluid.gl

import android.content.Context

/**
 * Ports the WebGL fluid simulation's `Material` class: the display shader is
 * compiled into one variant per active SHADING/BLOOM/SUNRAYS keyword combination
 * and cached. Kotlin's `Set<String>` already has correct equals/hashCode, so
 * (unlike the original's toy string-hash function) it's used directly as the
 * cache key.
 */
class DisplayMaterial(
    private val context: Context,
    private val vertexSource: String,
    private val fragmentResourceId: Int
) {
    private val cache = HashMap<Set<String>, ShaderProgram>()
    private var active: ShaderProgram? = null

    fun setKeywords(keywords: Set<String>) {
        active = cache.getOrPut(keywords) {
            val fragmentSource = ShaderText.load(context, fragmentResourceId, keywords.toList())
            ShaderProgram(vertexSource, fragmentSource)
        }
    }

    val uniforms: Map<String, Int> get() = active?.uniforms ?: emptyMap()

    fun bind() {
        active?.bind()
    }
}
