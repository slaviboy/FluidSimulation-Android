package com.slaviboy.fluidsimulation.fluid

/**
 * Every tunable simulation/render parameter, with the same defaults the simulation
 * starts up with. There's no screenshot/capture-resolution setting since there's no
 * capture feature.
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

    /** Restores every field to its default value, e.g. for the settings screen's Reset button. */
    fun reset() {
        simResolution = 128
        dyeResolution = 1024

        densityDissipation = 1f
        velocityDissipation = 0.2f
        pressure = 0.8f
        pressureIterations = 20
        curl = 30f
        splatRadius = 0.25f
        splatForce = 6000f

        shading = true
        colorful = true
        colorUpdateSpeed = 10f
        paused = false

        backColor = floatArrayOf(0f, 0f, 0f)
        transparent = false

        bloom = true
        bloomIterations = 8
        bloomResolution = 256
        bloomIntensity = 0.8f
        bloomThreshold = 0.6f
        bloomSoftKnee = 0.7f

        sunrays = true
        sunraysResolution = 196
        sunraysWeight = 1.0f
    }
}
