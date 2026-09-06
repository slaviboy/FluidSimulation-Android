package com.slaviboy.fluidsimulation.fluid.input

/** Tracked state for one active touch, from the moment it goes down until it lifts. */
data class Pointer(
    var id: Int = -1,
    var texcoordX: Float = 0f,
    var texcoordY: Float = 0f,
    var prevTexcoordX: Float = 0f,
    var prevTexcoordY: Float = 0f,
    var deltaX: Float = 0f,
    var deltaY: Float = 0f,
    var down: Boolean = false,
    var moved: Boolean = false,
    var color: FloatArray = floatArrayOf(30f, 0f, 300f)
)
