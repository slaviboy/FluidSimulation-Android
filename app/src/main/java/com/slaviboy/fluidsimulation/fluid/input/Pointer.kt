package com.slaviboy.fluidsimulation.fluid.input

/** Mirrors the WebGL fluid simulation's `pointerPrototype`, one instance per active touch. */
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
