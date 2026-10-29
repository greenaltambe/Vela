package com.greenaltambe.vela.ui.nav

import com.greenaltambe.vela.core.settings.*

/** The navigation settings, read once: which tabs, how they look, which side the profile button is on. */
data class NavConfig(
    val tabs: List<NavTab> = NavTabs.default,
    val labels: NavLabels = NavLabels.ALWAYS,
    val barStyle: BarStyle = BarStyle.FLOATING,
    val railCollapsed: Boolean = false,
    val shrinkOnScroll: Boolean = false,
    val miniLook: MiniLook = MiniLook.CARD,
    val miniPlacement: MiniPlacement = MiniPlacement.ATTACHED,
    val miniGapDp: Float = 8f,
    val openOn: NavTab = NavTab.HOME,
) {
    /** The tab that shows as selected on [route], or null (Settings and its pages have none). Downloads is its own tab only when the list has it. */
    fun selected(route: Route): NavTab? {
        if (route == Route.Downloads && NavTab.DOWNLOADS in tabs) return NavTab.DOWNLOADS
        if (route is Route.Search && NavTab.SEARCH in tabs) return NavTab.SEARCH
        val tab = when (route.topLevel()) {
            Route.Home -> NavTab.HOME
            Route.Shorts -> NavTab.SHORTS
            Route.Subscriptions -> NavTab.SUBSCRIPTIONS
            Route.Library -> NavTab.LIBRARY
            else -> return null
        }
        return tab.takeIf { it in tabs }
    }

    companion object {
        fun of(s: SettingsResolver) = NavConfig(
            // The Shorts tab only exists while Shorts are on.
            tabs = NavTabs.parse(s.resolve(LookSettings.navTabs)).filter { it != NavTab.SHORTS || s.shortsEnabled() }.let { if (it.size >= 2) it else NavTabs.default.filter { t -> t != NavTab.SHORTS || s.shortsEnabled() } }, labels = s.resolve(LookSettings.navLabels),
            barStyle = s.resolve(LookSettings.barStyle), railCollapsed = s.resolve(LookSettings.railCollapsed), openOn = s.resolve(LookSettings.navOpenOn),
            shrinkOnScroll = s.resolve(LookSettings.navShrink), miniLook = s.resolve(LookSettings.miniLook), miniPlacement = s.resolve(LookSettings.miniPlacement), miniGapDp = s.resolve(LookSettings.miniGap),
        )
    }
}

/** Where a tab goes; Search has no page of its own as a tab, it opens the search view. */
fun NavTab.route(): Route = when (this) {
    NavTab.HOME -> Route.Home
    NavTab.SHORTS -> Route.Shorts
    NavTab.SUBSCRIPTIONS -> Route.Subscriptions
    NavTab.LIBRARY -> Route.Library
    NavTab.DOWNLOADS -> Route.Downloads
    NavTab.SEARCH -> Route.Search()
}

fun Navigator.openTab(tab: NavTab) { if (tab == NavTab.SEARCH) push(Route.Search()) else selectTab(tab.route()) }
