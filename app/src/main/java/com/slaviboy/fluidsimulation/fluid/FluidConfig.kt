package com.slaviboy.fluidsimulation.fluid

/**
 * Tunable simulation/render parameters, ported from the WebGL fluid simulation's
 * `config` object. CAPTURE_RESOLUTION and the screenshot/export pipeline are
 * intentionally omitted (out of scope for this port).
 */
class FluidConfig {
    var simResolution: Int = 128
    var dyeResolution: Int = 1024

    var densityDissipation: Float = 1f
    var velocityDissipation: Float = 0.2f
    var pressure: Float = 0.8f
    var pressureIterations: Int = 20
    var curl: Float = 30f
    var splatRadius: Float = 0.25f
    var splatForce: Float = 6000f

    var shading: Boolean = true
    var colorful: Boolean = true
    var colorUpdateSpeed: Float = 10f
    var paused: Boolean = false

    var backColor: FloatArray = floatArrayOf(0f, 0f, 0f)
    var transparent: Boolean = false

    var bloom: Boolean = true
    var bloomIterations: Int = 8
    var bloomResolution: Int = 256
    var bloomIntensity: Float = 0.8f
    var bloomThreshold: Float = 0.6f
    var bloomSoftKnee: Float = 0.7f

    var sunrays: Boolean = true
    var sunraysResolution: Int = 196
    var sunraysWeight: Float = 1.0f
}
