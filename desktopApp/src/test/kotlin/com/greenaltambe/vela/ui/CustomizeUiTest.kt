@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.*
import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomizeUiTest {
    @Test fun theHubShowsThemeSummariesAndTheRailToggleAndEveryLookSettingHasARow() = runDesktopComposeUiTest(400, 1000) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.Customize)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("setting.look.theme.pills.1").assertExists()
        onNodeWithTag("customize.row.progress").assertExists(); onNodeWithTag("customize.row.home").assertExists()
        onNodeWithTag("customize.list").performScrollToNode(hasTestTag("setting.look.nav.railCollapsed"))
        onNodeWithTag("setting.look.nav.railCollapsed.switch").performClick(); waitForIdle()
        assertTrue(f.settings.resolve(LookSettings.railCollapsed))
    }

    @Test fun shelvesSwitchAndMoveWithoutShiftingTheOthers() = runDesktopComposeUiTest(400, 1400) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.CustomizePage("home"))
        setContent { AppUnderTest(f) }; waitForIdle()
        val first = HomeShelves.ordered(HomeShelves.parse(f.settings.resolve(LookSettings.homeShelves))).first()
        val second = HomeShelves.ordered(HomeShelves.parse(f.settings.resolve(LookSettings.homeShelves)))[1]
        onNodeWithTag("home.shelfDown.${first.name}").performClick(); waitForIdle()
        val now = HomeShelves.parse(f.settings.resolve(LookSettings.homeShelves))
        assertEquals(listOf(second, first), now.take(2), "moving the first shelf down swaps it with the second")
        mainClock.advanceTimeBy(6_000)
        onNodeWithTag("home.shelfToggle.${second.name}").performClick(); waitForIdle()
        assertTrue(second !in HomeShelves.parse(f.settings.resolve(LookSettings.homeShelves)))
    }

    @Test fun theProgressPageIsARadioListAndTheMenuResetsIt() = runDesktopComposeUiTest(400, 1000) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.CustomizePage("progress"))
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("progress.WAVE").performClick(); waitForIdle()
        assertEquals(ProgressStyle.WAVE, f.settings.resolve(LookSettings.progressStyle))
        mainClock.advanceTimeBy(6_000)
        onNodeWithTag("customize.menu").performClick(); onNodeWithTag("customize.resetPage").performClick(); waitForIdle()
        assertEquals(LookSettings.progressStyle.default, f.settings.resolve(LookSettings.progressStyle))
    }
}
