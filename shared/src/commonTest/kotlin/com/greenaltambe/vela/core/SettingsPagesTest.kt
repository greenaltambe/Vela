package com.greenaltambe.vela.core

import com.greenaltambe.vela.core.settings.*
import kotlin.test.*

class SettingsPagesTest {
    private val s = SettingsResolver(InMemorySettingsStore())

    @Test fun everySettingIsReachableFromSomePageAndNoneIsOrphaned() {
        assertEquals(emptyList(), SettingsPages.orphans(), "settings no page shows")
        val shown = SettingsPages.all.flatMap { SettingsPages.settingsOf(it) }.map { it.key }
        assertEquals(Settings.all.map { it.key }.sorted(), shown.sorted(), "each setting on exactly one page")
        assertEquals(shown.size, shown.toSet().size, "never on two pages")
    }

    @Test fun pageIdsAreUniqueAndEveryPageEitherOwnsSettingsOrLeadsSomewhere() {
        assertEquals(SettingsPages.all.size, SettingsPages.all.map { it.id }.toSet().size)
        for (p in SettingsPages.all) assertTrue(SettingsPages.settingsOf(p).isNotEmpty() || p.link != SettingsLink.NONE, "${p.id} would be an empty page")
    }

    @Test fun appearanceSettingsLiveUnderCustomizeAndSafetyKeysUnderSafety() {
        for (k in LookSettings.all) assertEquals("customize", SettingsPages.pageOf(k)?.id, k.key)
        for (k in listOf("account.tierOrder", "account.killSwitch", "playback.allowAgeRestricted", "playback.accountFallbackMode")) assertEquals("safety", SettingsPages.pageOf(Settings.all.first { it.key == k })?.id, k)
        assertEquals("playback", SettingsPages.pageOf(Settings.defaultPlaybackSpeed)?.id)
        assertEquals("extras", SettingsPages.pageOf(Settings.deArrowEnabled)?.id)
    }

    @Test fun summariesAreGeneratedFromTheCurrentValues() {
        val feed = SettingsPages.byId("feed")!!
        assertEquals("show watched", SettingsSummaries.of(feed, s))
        s.set(Settings.hideWatched, true); s.set(Settings.minLengthSeconds, 300f); s.set(Settings.blockKeywords, "asmr")
        assertEquals("hide watched · min 5 min · keyword rules", SettingsSummaries.of(feed, s))
        val extras = SettingsPages.byId("extras")!!
        assertEquals("SponsorBlock on · RYD on · DeArrow off", SettingsSummaries.of(extras, s))
        s.set(Settings.deArrowEnabled, true); assertEquals("SponsorBlock on · RYD on · DeArrow on", SettingsSummaries.of(extras, s))
        val customize = SettingsPages.byId("customize")!!
        assertEquals("System · Floating · 3 tabs", SettingsSummaries.of(customize, s))
        s.set(LookSettings.theme, ThemeMode.DARK); s.set(Settings.look, LookChoice.PURE_BLACK); s.set(LookSettings.navTabs, "home,search,library,downloads"); s.set(LookSettings.barStyle, BarStyle.DOCKED)
        assertEquals("Dark · pure black · Docked · 4 tabs", SettingsSummaries.of(customize, s))
        assertTrue(SettingsSummaries.of(SettingsPages.byId("playback")!!, s).contains("x"))
        for (p in SettingsPages.all) assertTrue(SettingsSummaries.of(p, s).isNotBlank(), "${p.id} has a summary")
    }
}
