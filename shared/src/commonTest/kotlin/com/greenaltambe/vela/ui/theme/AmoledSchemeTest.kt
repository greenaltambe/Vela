package com.greenaltambe.vela.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AmoledSchemeTest {
    @Test fun darkWithAmoledIsBlackButKeepsTextAndAccent() {
        val c = Appearance(amoled = true).colors(dark = true)
        assertEquals(Color.Black, c.background); assertEquals(Color.Black, c.surface)
        assertEquals(RedColorSchemes.Dark.primary, c.primary); assertEquals(RedColorSchemes.Dark.onBackground, c.onBackground)
    }
    @Test fun containersStayLifterThanThePageSoCardsStillShow() {
        val c = Appearance(amoled = true).colors(dark = true)
        assertNotEquals(c.background, c.surfaceContainer); assertNotEquals(c.background, c.surfaceContainerHigh)
    }
    @Test fun lightThemeAndPlainDarkAreUntouched() {
        assertEquals(RedColorSchemes.Light, Appearance(amoled = true).colors(dark = false))
        assertEquals(RedColorSchemes.Dark, Appearance(amoled = false).colors(dark = true))
    }
}
