package com.greenaltambe.vela.ui.nav

import com.greenaltambe.vela.core.settings.*

/** The navigation as the shell and the player sheet see it: the fixed tabs (Shorts only while Shorts are on) and whether the wide rail is collapsed. */
data class NavConfig(
    val tabs: List<NavTab> = NavTabs.default,
    val railCollapsed: Boolean = false,
) {
    /** The tab that shows as selected on [route], or null (Settings and its pages have none). Downloads is its own tab only when the list has it. */
    fun selected(route: Route): NavTab? {
        if (route == Route.Downloads && NavTab.DOWNLOADS in tabs) return NavTab.DOWNLOADS
        if (route is Route.Search && NavTab.SEARCH in tabs) return NavTab.SEARCH
        val tab = when (route.topLevel()) {
            Route.Home -> NavTab.HOME
            Route.Shorts -> NavTab.SHORTS
            Route.Explore -> NavTab.EXPLORE
            Route.Subscriptions -> NavTab.SUBSCRIPTIONS
            Route.Library -> NavTab.LIBRARY
            else -> return null
        }
        return tab.takeIf { it in tabs }
    }

    companion object {
        fun of(s: SettingsResolver) = NavConfig(
            tabs = NavTabs.default.filter { it != NavTab.SHORTS || s.shortsEnabled() },
            railCollapsed = s.resolve(LookSettings.railCollapsed),
        )
    }
}

/** Where a tab goes; Search has no page of its own as a tab, it opens the search view. */
fun NavTab.route(): Route = when (this) {
    NavTab.HOME -> Route.Home
    NavTab.SHORTS -> Route.Shorts
    NavTab.EXPLORE -> Route.Explore
    NavTab.SUBSCRIPTIONS -> Route.Subscriptions
    NavTab.LIBRARY -> Route.Library
    NavTab.DOWNLOADS -> Route.Downloads
    NavTab.SEARCH -> Route.Search()
}

fun Navigator.openTab(tab: NavTab) { if (tab == NavTab.SEARCH) push(Route.Search()) else selectTab(tab.route()) }
