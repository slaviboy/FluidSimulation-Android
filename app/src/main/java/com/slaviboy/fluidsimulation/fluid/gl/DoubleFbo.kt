package com.slaviboy.fluidsimulation.fluid.gl

/**
 * A ping-pong pair of [Fbo]s exposing `read`/`write` roles that swap each step.
 * Used for velocity, dye and pressure — fields that are iteratively updated,
 * where each pass needs to read the previous state while writing the new one
 * (writing in place would corrupt the read as neighboring texels are sampled).
 */
class DoubleFbo(var read: Fbo, var write: Fbo) {

    val width: Int get() = read.width
    val height: Int get() = read.height
    val texelSizeX: Float get() = read.texelSizeX
    val texelSizeY: Float get() = read.texelSizeY

    fun swap() {
        val tmp = read
        read = write
        write = tmp
    }
}
