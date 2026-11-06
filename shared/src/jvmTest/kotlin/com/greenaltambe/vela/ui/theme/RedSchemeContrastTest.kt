package com.greenaltambe.vela.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class RedSchemeContrastTest {
    private fun lin(c: Float) = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun luminance(c: Color) = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    private fun ratio(a: Color, b: Color): Double { val (hi, lo) = luminance(a).let { x -> luminance(b).let { y -> maxOf(x, y) to minOf(x, y) } }; return (hi + 0.05) / (lo + 0.05) }

    private fun problems(name: String, c: ColorScheme): List<String> = buildList {
        fun check(what: String, fg: Color, bg: Color) { val r = ratio(fg, bg); if (r < 4.5) add("$name: $what %.2f:1".format(r)) }
        check("onPrimary/primary", c.onPrimary, c.primary)
        check("onPrimaryContainer/primaryContainer", c.onPrimaryContainer, c.primaryContainer)
        check("onSecondary/secondary", c.onSecondary, c.secondary)
        check("onSecondaryContainer/secondaryContainer", c.onSecondaryContainer, c.secondaryContainer)
        check("onTertiaryContainer/tertiaryContainer", c.onTertiaryContainer, c.tertiaryContainer)
        check("onError/error", c.onError, c.error)
        check("onErrorContainer/errorContainer", c.onErrorContainer, c.errorContainer)
        check("onBackground/background", c.onBackground, c.background)
        check("accent text on the page", c.primary, c.background)
        for ((n, bg) in listOf("surface" to c.surface, "surfaceContainerLow" to c.surfaceContainerLow, "surfaceContainer" to c.surfaceContainer, "surfaceContainerHigh" to c.surfaceContainerHigh, "surfaceContainerHighest" to c.surfaceContainerHighest)) {
            check("onSurface/$n", c.onSurface, bg); check("onSurfaceVariant/$n", c.onSurfaceVariant, bg); check("accent text on $n", c.primary, bg)
        }
        check("inverse text", c.inverseOnSurface, c.inverseSurface)
    }

    @Test fun textRolesAreAtLeast4_5To1InLightAndDark() {
        val bad = problems("light", RedColorSchemes.Light) + problems("dark", RedColorSchemes.Dark)
        assertTrue(bad.isEmpty(), "contrast failures (${bad.size}):\n" + bad.joinToString("\n"))
    }
}
