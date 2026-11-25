@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.core.settings.ColorPalette
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.LookSettings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.core.settings.ThemeMode
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.theme.Accent
import com.greenaltambe.vela.ui.theme.AppTheme
import com.greenaltambe.vela.ui.theme.LocalSystemDark
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The colour scheme cards in Customize, and the Gold scheme's seek bar. */
class ColorSchemeUiTest {
    /** The brightness of a scheme card's own background (a point just inside its left edge, below the rounded corner). */
    private fun cardBrightness(systemDark: Boolean, mode: ThemeMode): Float {
        var result = -1f
        runDesktopComposeUiTest(500, 1200) {
            val f = buildFixture(withDownloads = false)
            f.settings.set(LookSettings.theme, mode)
            f.navigator.selectTab(Route.Settings); f.navigator.push(Route.Customize)
            setContent { CompositionLocalProvider(LocalSystemDark provides systemDark) { AppUnderTest(f) } }
            waitForIdle()
            val card = onNodeWithTag("customize.scheme.${ColorPalette.VELA_RED.name}")
            card.performScrollTo(); waitForIdle()
            val px = card.captureToImage().toPixelMap()
            result = px[6, px.height / 2].luminance()
            screenshot("color-schemes-${mode.name}-systemDark-$systemDark")
        }
        return result
    }

    @Test fun theCardsAreDarkWhenTheDesktopIsDarkAndTheThemeFollowsIt() {
        val b = cardBrightness(systemDark = true, mode = ThemeMode.SYSTEM)
        assertTrue(b < 0.2f, "the card is light in a dark app: $b")
    }

    @Test fun theCardsFollowTheChosenThemeOverTheSystem() {
        assertTrue(cardBrightness(systemDark = true, mode = ThemeMode.LIGHT) > 0.6f)
        assertTrue(cardBrightness(systemDark = false, mode = ThemeMode.DARK) < 0.2f)
    }

    @Test fun goldKeepsABrightSeekBarInTheLightThemeAndOtherSchemesTakeTheirPrimary() {
        fun seek(p: ColorPalette): Pair<Color, Color> {
            var out = Color.Unspecified to Color.Unspecified
            runDesktopComposeUiTest(100, 100) {
                val s = SettingsResolver(InMemorySettingsStore())
                s.set(LookSettings.theme, ThemeMode.LIGHT); s.set(LookSettings.colorScheme, p)
                setContent { AppTheme(s) { out = Accent.seek to androidx.compose.material3.MaterialTheme.colorScheme.primary; Box {} } }
                waitForIdle()
            }
            return out
        }
        val (gold, goldPrimary) = seek(ColorPalette.GOLD)
        assertTrue(gold.luminance() > 0.5f && gold.red > 0.9f && gold.blue < 0.2f, "not a bright yellow: $gold")
        assertTrue(goldPrimary.luminance() < 0.2f, "the light primary must stay readable: $goldPrimary")
        val (ocean, oceanPrimary) = seek(ColorPalette.OCEAN)
        assertEquals(oceanPrimary, ocean)
    }
}
