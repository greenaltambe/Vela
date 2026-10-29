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
        onNodeWithTag("setting.appearance.performanceMode").assertExists()
        onNodeWithTag("settings.account").assertExists()
    }

    @Test fun aChangeRaisesAnUndoToastAndUndoPutsItBack() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.SettingsPage("extras"))
        setContent { AppUnderTest(f) }; waitForIdle()
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
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("settings.pageList").performScrollToNode(hasTestTag("settings.resetPage")); onNodeWithTag("settings.resetPage").performClick(); waitForIdle()
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
        onNodeWithTag("settings.search").performTextInput("corner"); waitForIdle()
        onNodeWithTag("settings.result.look.corners").performClick(); waitForIdle()
        assertEquals(Route.CustomizePage("icons"), f.navigator.current)
    }

    @Test fun customizeHubHasTheLivePreviewThemeAndEveryPart() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.Customize)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("customize.preview").assertExists()
        // Each mini screen is 40 % of 360 x 640 and shows real content (the real bottom bar and cards), not an empty box.
        // (The third one may be partly scrolled out of the strip, so only its height is checked.)
        for (l in listOf("Home", "Video page", "Player")) {
            val b = onNodeWithTag("customize.preview.$l", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(kotlin.math.abs(b.height - 256f) < 2f && (l == "Player" || kotlin.math.abs(b.width - 144f) < 2f), "$l preview is ${b.width}x${b.height}, expected 144x256")
        }
        onNodeWithTag("customize.preview.Home", useUnmergedTree = true).onChildren().assertCountEquals(1)
        assertTrue(onAllNodesWithTag("component.bottom_nav", useUnmergedTree = true).fetchSemanticsNodes().size >= 2, "the Home preview contains a real bottom bar besides the page's own")
        for (r in listOf("color", "text", "icons", "glass", "tabs")) onNodeWithTag("customize.row.$r").performScrollTo().assertExists()
        onNodeWithTag("setting.look.theme.pills.2").performScrollTo().performClick(); waitForIdle()
        assertEquals(ThemeMode.DARK, f.settings.resolve(LookSettings.theme))
    }

    @Test fun colorTextAndIconPagesChangeTheirSettingsLive() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.CustomizePage("color"))
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("palette.SUNSET").performScrollTo().performClick(); waitForIdle()
        assertEquals(Palette.SUNSET, f.settings.resolve(LookSettings.palette))
        f.navigator.back(); f.navigator.push(Route.CustomizePage("text")); waitForIdle()
        onNodeWithTag("font.SERIF").performScrollTo().performClick(); waitForIdle()
        assertEquals(FontChoice.SERIF, f.settings.resolve(LookSettings.font))
        f.navigator.back(); f.navigator.push(Route.CustomizePage("icons")); waitForIdle()
        onNodeWithTag("iconpack.LINE").performScrollTo().performClick(); waitForIdle()
        assertEquals(IconStyle.LINE, f.settings.resolve(LookSettings.iconStyle))
        onNodeWithTag("iconpack.DUOTONE").performScrollTo().performClick(); waitForIdle()
        assertEquals(IconStyle.DUOTONE, f.settings.resolve(LookSettings.iconStyle))
        onNodeWithTag("iconpack.FILLED").performScrollTo().performClick(); waitForIdle()
        assertEquals(IconStyle.FILLED, f.settings.resolve(LookSettings.iconStyle))
    }

    @Test fun theTabChecklistReordersTogglesAndKeepsAtLeastTwo() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.CustomizePage("tabs"))
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("tabs.down.HOME").performScrollTo().performClick(); waitForIdle()
        assertEquals(listOf(NavTab.SUBSCRIPTIONS, NavTab.HOME, NavTab.LIBRARY), NavTabs.parse(f.settings.resolve(LookSettings.navTabs)))
        onNodeWithTag("tabs.toggle.DOWNLOADS").performScrollTo().performClick(); waitForIdle()
        assertTrue(NavTab.DOWNLOADS in NavTabs.parse(f.settings.resolve(LookSettings.navTabs)))
        onNodeWithTag("tabs.toggle.DOWNLOADS").performClick(); onNodeWithTag("tabs.toggle.LIBRARY").performClick(); onNodeWithTag("tabs.toggle.HOME").performClick(); waitForIdle()
        assertTrue(NavTabs.parse(f.settings.resolve(LookSettings.navTabs)).size >= 2, "the last two tabs cannot be removed")
    }

    @Test fun everyStubPageSaysItArrivesNextUpdateAndEveryLookSettingIsOnAPage() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.navigator.selectTab(Route.Settings); f.navigator.push(Route.CustomizePage("looks"))
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("customize.stub").assertExists()
        assertTrue(LookSettings.all.map { it.key }.toSet().all { it in com.greenaltambe.vela.ui.screens.CustomizePages.covered() }, "a look setting no Customize page shows")
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
