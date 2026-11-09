package com.greenaltambe.vela.ui.theme

import com.greenaltambe.vela.core.settings.*
import kotlin.test.*

class LookSettingsTest {
    @Test fun everyAppearanceSettingHasALabelAKeywordAndALookKey() {
        for (s in LookSettings.all) { assertTrue(s.label.isNotBlank() && s.keywords.isNotEmpty(), s.key); assertTrue(LookKeys.isLookKey(s.key), "${s.key} must start with look.") }
        assertTrue(LookSettings.all.all { it in Settings.all }, "registered in the schema (settings UI, search and backup read it)")
        assertFalse(LookKeys.isLookKey("account.tierOrder")); assertFalse(LookKeys.isLookKey("downloads.folder"))
    }

    @Test fun onlyTheThemeTheProgressBarAndHomeAreLeftToCustomize() {
        assertEquals(setOf("look.theme", "look.amoled", "look.progressStyle", "look.home.shelves", "look.home.density", "look.home.chips", "look.nav.railCollapsed"), LookSettings.all.map { it.key }.toSet())
    }

    @Test fun theRemovedSettingsAreKnownAsLegacySoOldBackupsRestoreQuietly() {
        for (k in listOf("look.palette", "look.font", "look.iconStyle", "look.corners", "look.glass", "look.nav.barStyle", "look.nav.tabs", "look.mini.look", "look.mini.gap", "appearance.look", "appearance.performanceMode"))
            assertTrue(LegacySettingKeys.isLegacy(k), k)
        for (k in LookSettings.all.map { it.key }) assertFalse(LegacySettingKeys.isLegacy(k), "$k is still a live setting")
    }

    @Test fun theDefaultAppearanceFollowsTheThemeAndProgressSettings() {
        val s = SettingsResolver(InMemorySettingsStore())
        assertEquals(ThemeMode.SYSTEM, DefaultAppearanceSource.resolve(s).themeMode)
        s.set(LookSettings.theme, ThemeMode.DARK); s.set(LookSettings.progressStyle, ProgressStyle.WAVE)
        val a = DefaultAppearanceSource.resolve(s)
        assertEquals(ThemeMode.DARK, a.themeMode); assertEquals(ProgressStyle.WAVE, a.progress)
        assertEquals(RedColorSchemes.Dark, a.colors(dark = true)); assertEquals(RedColorSchemes.Light, a.colors(dark = false))
    }
}
