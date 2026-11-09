package com.greenaltambe.vela.library

import kotlin.math.pow

/** Colours in captions, as ARGB ints. */
object CaptionColors {
    private val names = mapOf(
        "white" to 0xFFFFFF, "black" to 0x000000, "red" to 0xFF0000, "lime" to 0x00FF00, "green" to 0x008000, "blue" to 0x0000FF, "yellow" to 0xFFFF00,
        "cyan" to 0x00FFFF, "magenta" to 0xFF00FF, "gray" to 0x808080, "grey" to 0x808080, "orange" to 0xFFA500, "pink" to 0xFFC0CB, "purple" to 0x800080,
    )

    /** A CSS colour: `rgb(r,g,b)`, `#rgb`, `#rrggbb` or a name. Null when it is none of those. */
    fun parse(css: String): Int? {
        val s = css.trim().lowercase()
        Regex("""rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})""").find(s)?.let { m ->
            val (r, g, b) = m.destructured
            return argb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
        }
        if (s.startsWith("#")) {
            val h = s.drop(1)
            val v = when (h.length) { 6 -> h.toIntOrNull(16); 3 -> h.map { "$it$it" }.joinToString("").toIntOrNull(16); else -> null } ?: return null
            return 0xFF000000.toInt() or v
        }
        return names[s]?.let { 0xFF000000.toInt() or it }
    }

    /** The colour a bare class name stands for when the file does not define it: a WebVTT colour name, or `colorRRGGBB` (YouTube's own naming). */
    fun fromClass(name: String): Int? {
        if (name.startsWith("color") && name.length == 11) return name.drop(5).toIntOrNull(16)?.let { 0xFF000000.toInt() or it }
        return names[name.lowercase()]?.takeIf { name.lowercase() in setOf("white", "black", "red", "lime", "blue", "yellow", "cyan", "magenta") }?.let { 0xFF000000.toInt() or it }
    }

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun channel(v: Int): Double { val c = v / 255.0; return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4) }
    private fun luminance(c: Int) = 0.2126 * channel((c shr 16) and 255) + 0.7152 * channel((c shr 8) and 255) + 0.0722 * channel(c and 255)

    /**
     * [argb] made light enough to read on a dark caption background: a creator's colour such as a dark brown or a pure blue (usernames in a chat
     * overlay) is mixed toward white until its contrast against near-black is at least [minContrast]. Light colours come back unchanged.
     */
    fun readableOnDark(argb: Int, minContrast: Double = 4.5): Int {
        val base = 0.02   // the darkest the background ever is: the black caption box over a dark picture
        var color = argb or (0xFF shl 24)
        var t = 0.0
        while ((luminance(color) + 0.05) / (base + 0.05) < minContrast && t < 1.0) {
            t += 0.05
            val r = (((argb shr 16) and 255) + (255 - ((argb shr 16) and 255)) * t).toInt()
            val g = (((argb shr 8) and 255) + (255 - ((argb shr 8) and 255)) * t).toInt()
            val b = ((argb and 255) + (255 - (argb and 255)) * t).toInt()
            color = argb(r.coerceAtMost(255), g.coerceAtMost(255), b.coerceAtMost(255))
        }
        return color
    }
}
