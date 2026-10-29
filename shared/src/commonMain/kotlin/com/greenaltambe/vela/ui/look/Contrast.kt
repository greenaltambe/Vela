package com.greenaltambe.vela.ui.look

import kotlin.math.pow

/** WCAG contrast maths on 0xAARRGGBB colours (alpha ignored: callers composite first). */
object Contrast {
    private fun channel(v: Int): Double { val c = v / 255.0; return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4) }

    fun luminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt(); val g = ((argb shr 8) and 0xFF).toInt(); val b = (argb and 0xFF).toInt()
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    fun ratio(a: Long, b: Long): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** [fg] over [bg] at [alpha] as an opaque colour. */
    fun over(fg: Long, bg: Long, alpha: Float): Long {
        fun mix(shift: Int): Long { val f = (fg shr shift) and 0xFF; val b = (bg shr shift) and 0xFF; return (f * alpha + b * (1 - alpha)).toLong().coerceIn(0, 255) shl shift }
        return 0xFF000000 or mix(16) or mix(8) or mix(0)
    }

    /** Black or white, whichever reads better on [bg]. */
    fun onColor(bg: Long): Long = if (ratio(0xFFFFFFFF, bg) >= ratio(0xFF000000, bg)) 0xFFFFFFFF else 0xFF000000

    /** Black or white, whichever reads better on the worst of [backgrounds] (a label over a gradient). */
    fun onAll(backgrounds: List<Long>): Long {
        val white = backgrounds.minOf { ratio(0xFFFFFFFF, it) }; val black = backgrounds.minOf { ratio(0xFF000000, it) }
        return if (white >= black) 0xFFFFFFFF else 0xFF000000
    }

    /**
     * [fg] moved towards white (on a dark [bg]) or black (on a light one) in 4 % steps until it has [min]:1 against [bg]. Returns
     * [fg] itself if it already does. The hue is kept as far as possible, which is the point of "softened" over "swap for white".
     */
    fun ensure(fg: Long, bg: Long, min: Double = 4.5): Long {
        if (ratio(fg, bg) >= min) return fg or 0xFF000000
        val target = if (luminance(bg) < 0.5) 0xFFFFFFFF else 0xFF000000
        var t = 0.04f
        while (t <= 1f) { val c = over(target, fg, t); if (ratio(c, bg) >= min) return c; t += 0.04f }
        return target
    }
}
