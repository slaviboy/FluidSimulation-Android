package com.slaviboy.fluidsimulation.fluid

/**
 * Tunable simulation/render parameters, ported from the WebGL fluid simulation's
 * `config` object. CAPTURE_RESOLUTION and the screenshot/export pipeline are
 * intentionally omitted (out of scope for this port).
 *
 * Fields are `@Volatile` since they're written from the UI thread (settings
 * screen) and read every frame from the GL thread.
 */
class FluidConfig {
    @Volatile var simResolution: Int = 128
    @Volatile var dyeResolution: Int = 1024

    @Volatile var densityDissipation: Float = 1f
    @Volatile var velocityDissipation: Float = 0.2f
    @Volatile var pressure: Float = 0.8f
    @Volatile var pressureIterations: Int = 20
    @Volatile var curl: Float = 30f
    @Volatile var splatRadius: Float = 0.25f
    @Volatile var splatForce: Float = 6000f

    @Volatile var shading: Boolean = true
    @Volatile var colorful: Boolean = true
    @Volatile var colorUpdateSpeed: Float = 10f
    @Volatile var paused: Boolean = false

    @Volatile var backColor: FloatArray = floatArrayOf(0f, 0f, 0f)
    @Volatile var transparent: Boolean = false

    @Volatile var bloom: Boolean = true
    @Volatile var bloomIterations: Int = 8
    @Volatile var bloomResolution: Int = 256
    @Volatile var bloomIntensity: Float = 0.8f
    @Volatile var bloomThreshold: Float = 0.6f
    @Volatile var bloomSoftKnee: Float = 0.7f

    @Volatile var sunrays: Boolean = true
    @Volatile var sunraysResolution: Int = 196
    @Volatile var sunraysWeight: Float = 1.0f
}
