@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test

/**
 * Renders every screen at the sizes the layout must work at and writes PNGs to docs/reports/12-screens/. Looking at them
 * is part of the work (see the report); the layout assertions in [LayoutInvariantsTest] check the same screens mechanically.
 */
class ScreenshotsTest {
    private fun shot(screen: Screen, name: String, pureBlack: Boolean = false, setup: (Fixture) -> Unit) = runDesktopComposeUiTest(screen.width, screen.height) {
        val f = buildFixture(pureBlack)
        setup(f)
        setContent { AppUnderTest(f) }
        waitForIdle()
        mainClock.advanceTimeBy(600)
        waitForIdle()
        screenshot("$name-${screen.name}${if (pureBlack) "-black" else ""}")
        f.dir.deleteRecursively()
    }

    private val all = Screens
    private val phoneAndDesktop = Screens.filter { it.name in setOf("320x640", "360x800", "1280x800") }

    @Test fun home() = all.forEach { shot(it, "home") { } }
    @Test fun homeBlack() = listOf(Screens[1], Screens[4]).forEach { shot(it, "home", pureBlack = true) { } }
    @Test fun search() = all.forEach { s -> shot(s, "search") { it.navigator.push(Route.Search("home server")) } }
    @Test fun searchEmpty() = phoneAndDesktop.forEach { s -> shot(s, "search-start") { it.navigator.push(Route.Search()) } }
    @Test fun video() = all.forEach { s -> shot(s, "video") { it.navigator.push(Route.Video(FakeData.videos[0])) } }
    @Test fun videoBlack() = listOf(Screens[1], Screens[4]).forEach { s -> shot(s, "video", pureBlack = true) { it.navigator.push(Route.Video(FakeData.videos[0])) } }
    @Test fun subscriptions() = all.forEach { s -> shot(s, "subscriptions") { it.navigator.selectTab(Route.Subscriptions) } }
    @Test fun library() = phoneAndDesktop.forEach { s -> shot(s, "library") { it.navigator.selectTab(Route.Library) } }
    @Test fun settings() = all.forEach { s -> shot(s, "settings") { it.navigator.selectTab(Route.Settings) } }
    @Test fun settingsBlack() = listOf(Screens[1]).forEach { s -> shot(s, "settings", pureBlack = true) { it.navigator.selectTab(Route.Settings) } }
    @Test fun downloads() = phoneAndDesktop.forEach { s -> shot(s, "downloads") { it.navigator.selectTab(Route.Library); it.navigator.push(Route.Downloads) } }
    @Test fun channel() = phoneAndDesktop.forEach { s -> shot(s, "channel") { it.navigator.push(Route.Channel("UC1", FakeData.longChannel)) } }
    @Test fun history() = phoneAndDesktop.forEach { s -> shot(s, "history") { it.navigator.selectTab(Route.Library); it.navigator.push(Route.History) } }
    @Test fun diagnostics() = listOf(Screens[1], Screens[4]).forEach { s -> shot(s, "diagnostics") { it.navigator.selectTab(Route.Settings); it.navigator.push(Route.Diagnostics) } }
}
