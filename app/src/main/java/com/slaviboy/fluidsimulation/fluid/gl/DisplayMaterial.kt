package com.slaviboy.fluidsimulation.fluid.gl

import android.content.Context

/**
 * The final display shader has optional SHADING/BLOOM/SUNRAYS effects, each compiled
 * in or out via a `#define` (see [ShaderText]) rather than branching at runtime, so
 * a GPU never pays for an effect that's switched off. Since each combination needs
 * its own compiled [ShaderProgram], this compiles one lazily per combination the
 * settings screen actually selects and caches it (keyed by the keyword `Set`, which
 * already has correct equals/hashCode) so toggling back to a previously-used
 * combination is instant instead of re-compiling.
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
