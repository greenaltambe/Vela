package com.greenaltambe.vela.ui.look

import com.greenaltambe.vela.core.settings.*
import kotlin.test.*

class ContrastTest {
    private fun looks() = buildList {
        for (theme in ThemeMode.entries) for (pure in listOf(false, true)) for (palette in Palette.entries) for (sysDark in listOf(true, false))
            add("theme=$theme pureBlack=$pure palette=$palette systemDark=$sysDark" to LookBuilder.build(LookSnapshot(theme = theme, pureBlack = pure, palette = palette), sysDark))
    }

    @Test fun allTextIsAtLeast4_5To1OverEveryPalette() {
        val bad = mutableListOf<String>()
        for ((name, look) in looks()) {
            val c = look.colors
            fun check(what: String, fg: Long, bg: Long) { val r = Contrast.ratio(fg, bg); if (r < 4.5) bad += "$name: $what ${"%.2f".format(r)}" }
            for (bg in listOf(c.background, c.surface, c.surfaceVariant)) { check("onBackground/bg", c.onBackground, bg); check("onSurface/bg", c.onSurface, bg); check("onSurfaceVariant", c.onSurfaceVariant, bg.takeIf { it != c.surfaceVariant } ?: c.surface) }
            check("accent text on page", c.primary, c.background); check("accent text on surface", c.primary, c.surface); check("onPrimary on primary", c.onPrimary, c.primary)
        }
        assertTrue(bad.isEmpty(), "contrast failures (${bad.size}):\n" + bad.distinct().take(15).joinToString("\n"))
    }

    @Test fun aButtonLabelIsReadableOverEveryStopOfEveryPalette() {
        for (p in Palette.entries) {
            val l = LookBuilder.build(LookSnapshot(palette = p), true)
            val label = Contrast.onAll(l.accent.wake)
            for (stop in l.accent.wake) assertTrue(Contrast.ratio(label, stop) >= 3.0, "$p: label over ${stop.toString(16)} only ${"%.2f".format(Contrast.ratio(label, stop))}:1")
        }
    }

    @Test fun barLabelsOverAGlassBarKeepContrastAgainstTheWorstPageTextBehindIt() {
        // A bar is a blur plus a tint at least barTintAlpha opaque. Worst case behind it: page text in the opposite extreme of the background.
        for ((name, look) in looks().filter { it.first.contains("palette=VELA") }) {
            val worstBehind = if (Contrast.luminance(look.colors.background) < 0.5) 0xFFFFFFFF else 0xFF000000
            val bar = Contrast.over(look.colors.surface, worstBehind, look.effects.barTintAlpha)
            val r = Contrast.ratio(look.colors.onSurface, bar)
            assertTrue(r >= 4.5, "$name: bar label over page text only ${"%.2f".format(r)}:1 (tint alpha ${look.effects.barTintAlpha})")
        }
    }
}
