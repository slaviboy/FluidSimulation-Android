package com.slaviboy.fluidsimulation.fluid

import kotlin.math.floor
import kotlin.random.Random

/** HSV/RGB color helpers used for dye splat colors and the background color picker. */
object ColorUtil {

    fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
        val i = floor(h * 6f).toInt()
        val f = h * 6f - i
        val p = v * (1f - s)
        val q = v * (1f - f * s)
        val t = v * (1f - (1f - f) * s)

        return when (((i % 6) + 6) % 6) {
            0 -> floatArrayOf(v, t, p)
            1 -> floatArrayOf(q, v, p)
            2 -> floatArrayOf(p, v, t)
            3 -> floatArrayOf(p, q, v)
            4 -> floatArrayOf(t, p, v)
            else -> floatArrayOf(v, p, q)
        }
    }

    /** Random hue at full saturation/value, dimmed to 15% brightness for dye color. */
    fun generateColor(): FloatArray {
        val c = hsvToRgb(Random.nextFloat(), 1.0f, 1.0f)
        c[0] *= 0.15f
        c[1] *= 0.15f
        c[2] *= 0.15f
        return c
    }

    /** Keeps `value` inside [min, max) by wrapping around, e.g. the color-cycle timer in [PointerManager]. */
    fun wrap(value: Float, min: Float, max: Float): Float {
        val range = max - min
        if (range == 0f) return min
        return (value - min).mod(range) + min
    }
}
