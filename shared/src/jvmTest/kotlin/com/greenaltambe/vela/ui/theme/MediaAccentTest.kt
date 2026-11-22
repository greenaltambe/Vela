package com.greenaltambe.vela.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.greenaltambe.vela.core.settings.ColorPalette
import kotlin.test.*

class MediaAccentTest {
    private fun contrastOnBlack(c: Color) = (c.luminance() + 0.05f) / 0.05f

    @Test fun anOnControlOverVideoReadsOnTheDarkPictureInEveryPaletteAndTheme() {
        for (p in ColorPalette.entries) for (dark in listOf(false, true)) {
            val scheme = if (dark) Palettes.dark(p) else Palettes.light(p)
            val fixed = if (p == ColorPalette.CLASSIC) ClassicColorSchemes.MediaRed else null
            val accent = mediaAccentFor(scheme, fixed)
            assertTrue(contrastOnBlack(accent) >= 4.5f, "$p dark=$dark: $accent is too dark over video (contrast ${contrastOnBlack(accent)})")
            assertNotEquals(Color.White, accent, "$p dark=$dark: the on state must differ from the white off state")
        }
    }

    @Test fun classicIsReddish() {
        val c = mediaAccentFor(Palettes.light(ColorPalette.CLASSIC), ClassicColorSchemes.MediaRed)
        assertTrue(c.red > 0.9f && c.green < 0.5f && c.blue < 0.5f)
    }
}
