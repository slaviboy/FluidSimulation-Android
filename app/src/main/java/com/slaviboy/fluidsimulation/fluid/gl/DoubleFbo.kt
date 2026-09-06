package com.slaviboy.fluidsimulation.fluid.gl

/**
 * A ping-pong pair of [Fbo]s exposing `read`/`write` roles that swap each step.
 * Ports the WebGL fluid simulation's `createDoubleFBO`, used for velocity, dye
 * and pressure (fields that are iteratively updated: each pass reads the
 * previous state while writing the new one).
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
