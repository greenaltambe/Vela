@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.*
import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsUiTest {
    @Test fun theHomeShowsGroupsWithLiveSummariesAndNoSettingsInTheBars() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings)
        setContent { AppUnderTest(f) }; waitForIdle()
        for (p in listOf("customize", "playback", "extras", "feed", "safety", "downloads", "backup", "updates", "health")) {
            onNodeWithTag("settings.list").performScrollToNode(hasTestTag("settings.page.$p")); onNodeWithTag("settings.page.$p").assertExists()
        }
        onNodeWithTag("settings.list").performScrollToNode(hasTestTag("settings.page.extras"))
        onNodeWithTag("settings.summary.extras", useUnmergedTree = true).assertTextEquals("SponsorBlock on · RYD on · DeArrow off")
        f.settings.set(Settings.deArrowEnabled, true); waitForIdle()
        onNodeWithTag("settings.summary.extras", useUnmergedTree = true).assertTextEquals("SponsorBlock on · RYD on · DeArrow on")
        onNodeWithTag("settings.account").assertExists()
    }

    @Test fun aChangeRaisesAnUndoToastAndUndoPutsItBack() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.SettingsPage("extras"))
        setContent { AppUnderTest(f) }; waitForIdle(); waitUntil(timeoutMillis = 5000) { onAllNodesWithTag("settings.placeholder").fetchSemanticsNodes().isEmpty() }
        assertTrue(f.settings.resolve(Settings.sponsorBlockEnabled))
        onNodeWithTag("setting.modules.sponsorblock.enabled.switch").performScrollTo().performClick(); waitForIdle()
        assertTrue(!f.settings.resolve(Settings.sponsorBlockEnabled))
        onNodeWithTag("undo.toast").assertExists()
        onNodeWithTag("undo.action").performClick(); waitForIdle()
        assertTrue(f.settings.resolve(Settings.sponsorBlockEnabled), "Undo restored it")
    }

    @Test fun resetThisPageRestoresDefaultsInOneStepAndCanBeUndone() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.settings.set(Settings.deArrowEnabled, true); f.settings.set(Settings.returnYouTubeDislikeEnabled, false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.SettingsPage("extras"))
        setContent { AppUnderTest(f) }; waitForIdle(); waitUntil(timeoutMillis = 5000) { onAllNodesWithTag("settings.placeholder").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("settings.pageMenu").performClick(); onNodeWithTag("settings.resetPage").performClick(); waitForIdle()
        assertTrue(!f.settings.resolve(Settings.deArrowEnabled) && f.settings.resolve(Settings.returnYouTubeDislikeEnabled))
        onNodeWithTag("undo.action").performClick(); waitForIdle()
        assertTrue(f.settings.resolve(Settings.deArrowEnabled) && !f.settings.resolve(Settings.returnYouTubeDislikeEnabled))
    }

    @Test fun searchJumpsToTheSettingsPageAndHighlightsTheRow() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("settings.search").performTextInput("tier order"); waitForIdle()
        onNodeWithTag("settings.result.account.tierOrder").performClick(); waitForIdle()
        assertEquals(Route.SettingsPage("safety", highlight = "account.tierOrder"), f.navigator.current)
        onNodeWithTag("settings.pageList").performScrollToNode(hasTestTag("setting.account.tierOrder")); onNodeWithTag("setting.account.tierOrder").assertExists()
    }

    @Test fun appearanceSearchResultsOpenTheRightCustomizePage() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("settings.search").performTextInput("progress"); waitForIdle()
        onNodeWithTag("settings.result.look.progressStyle").performClick(); waitForIdle()
        assertEquals(Route.CustomizePage("progress"), f.navigator.current)
    }

    @Test fun customizeHubHasThemeAndEveryPart() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.Customize)
        setContent { AppUnderTest(f) }; waitForIdle()
        for (r in listOf("progress", "home")) onNodeWithTag("customize.row.$r").performScrollTo().assertExists()
        onNodeWithTag("setting.look.theme.pills.2").performScrollTo().performClick(); waitForIdle()
        assertEquals(ThemeMode.DARK, f.settings.resolve(LookSettings.theme))
    }

    @Test fun theProgressBarPageChangesItsSettingLiveAndEveryLookSettingIsOnAPage() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.CustomizePage("progress"))
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("progress.RIBBON").performScrollTo().performClick(); waitForIdle()
        assertEquals(ProgressStyle.RIBBON, f.settings.resolve(LookSettings.progressStyle))
        assertTrue(LookSettings.all.map { it.key }.toSet().all { it in com.greenaltambe.vela.ui.screens.CustomizePages.covered() + "look.nav.railCollapsed" }, "a look setting no Customize page shows")
    }

    @Test fun rowsThatOwnSettingsOpenTheirPageSoNothingIsOnlyReachableBySearch() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("settings.list").performScrollToNode(hasTestTag("settings.page.health")); onNodeWithTag("settings.page.health").performClick(); waitForIdle()
        assertEquals(Route.SettingsPage("health"), f.navigator.current)
        onNodeWithTag("setting.debug.frameStats").assertExists(); onNodeWithTag("settings.openLinked").performClick(); waitForIdle()
        assertEquals(Route.Health, f.navigator.current)
    }
}
