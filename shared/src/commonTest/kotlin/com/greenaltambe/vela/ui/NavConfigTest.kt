package com.greenaltambe.vela.ui

import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.ui.nav.NavConfig
import com.greenaltambe.vela.ui.nav.Route
import kotlin.test.*

class NavConfigTest {
    @Test fun theTabListIsParsedInOrderWithoutRepeatsOrUnknownNames() {
        assertEquals(listOf(NavTab.LIBRARY, NavTab.HOME, NavTab.DOWNLOADS), NavTabs.parse("library, HOME ,nope,home,downloads"))
        assertEquals(NavTabs.default, NavTabs.parse("home"), "fewer than two usable tabs falls back to the default")
        assertEquals(NavTabs.default, NavTabs.parse("settings,shorts"))
        assertEquals(5, NavTabs.parse("home,subscriptions,library,search,downloads,home").size)
        assertEquals(NavTabs.default, NavTabs.parse(NavTabs.encode(NavTabs.default)))
    }

    @Test fun settingsIsNeverATabAndHasNoSelectedTab() {
        assertFalse(NavTab.entries.any { it.name == "SETTINGS" })
        val nav = NavConfig()
        for (r in listOf(Route.Settings, Route.Accounts, Route.Health, Route.Backup, Route.Customize, Route.Diagnostics)) assertNull(nav.selected(r), "$r")
    }

    @Test fun pagesBelongToTheirTabAndDownloadsIsItsOwnTabOnlyWhenListed() {
        val nav = NavConfig()
        assertEquals(NavTab.HOME, nav.selected(Route.Home)); assertEquals(NavTab.SUBSCRIPTIONS, nav.selected(Route.Subscriptions))
        assertEquals(NavTab.LIBRARY, nav.selected(Route.History)); assertEquals(NavTab.LIBRARY, nav.selected(Route.Downloads), "without a Downloads tab it belongs to Library")
        val withDownloads = NavConfig(tabs = listOf(NavTab.HOME, NavTab.DOWNLOADS))
        assertEquals(NavTab.DOWNLOADS, withDownloads.selected(Route.Downloads)); assertNull(withDownloads.selected(Route.Subscriptions), "a page whose tab is hidden shows none")
    }

    @Test fun theConfigIsReadFromTheSettings() {
        val s = SettingsResolver(InMemorySettingsStore())
        s.set(LookSettings.navTabs, "downloads,home,search"); s.set(LookSettings.navLabels, NavLabels.HIDDEN); s.set(LookSettings.barStyle, BarStyle.MINIMAL); s.set(LookSettings.navShrink, true); s.set(LookSettings.miniLook, MiniLook.PIP); s.set(LookSettings.miniPlacement, MiniPlacement.SEPARATE); s.set(LookSettings.miniGap, 14f)
        val n = NavConfig.of(s)
        assertEquals(listOf(NavTab.DOWNLOADS, NavTab.HOME, NavTab.SEARCH), n.tabs); assertEquals(NavLabels.HIDDEN, n.labels); assertEquals(BarStyle.MINIMAL, n.barStyle); assertTrue(n.shrinkOnScroll); assertEquals(MiniLook.PIP, n.miniLook); assertEquals(MiniPlacement.SEPARATE, n.miniPlacement); assertEquals(14f, n.miniGapDp)
        assertEquals(NavConfig(), NavConfig.of(SettingsResolver(InMemorySettingsStore())))
    }
}
