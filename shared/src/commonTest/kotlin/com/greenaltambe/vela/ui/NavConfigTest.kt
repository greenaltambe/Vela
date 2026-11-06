package com.greenaltambe.vela.ui

import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.ui.nav.NavConfig
import com.greenaltambe.vela.ui.nav.Route
import kotlin.test.*

class NavConfigTest {
    @Test fun theTabsAreFixed() {
        assertEquals(listOf(NavTab.HOME, NavTab.SHORTS, NavTab.SUBSCRIPTIONS, NavTab.LIBRARY), NavTabs.default)
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

    @Test fun theNavigationIsFixedAndOnlyTheRailStateIsRemembered() {
        val s = SettingsResolver(InMemorySettingsStore())
        val n = NavConfig.of(s)
        assertEquals(NavTabs.default, n.tabs); assertFalse(n.railCollapsed)
        s.set(LookSettings.railCollapsed, true)
        assertTrue(NavConfig.of(s).railCollapsed)
        s.set(Settings.shortsMode, ShortsMode.HIDE)
        assertFalse(NavTab.SHORTS in NavConfig.of(s).tabs, "Shorts is a tab only while Shorts are on")
    }
}
