@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.*
import com.greenaltambe.vela.core.settings.LookSettings
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.ShortsMode
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The navigation bar's own settings: Explore as a tab with a switch, names under the icons, and everything around them (Shorts, shortcuts, rails). */
class NavCustomizationUiTest {
    private fun ComposeUiTest.tabs(prefix: String = "nav.tab.") = listOf("HOME", "SHORTS", "SUBSCRIPTIONS", "EXPLORE", "LIBRARY").filter { onAllNodesWithTag("$prefix$it").fetchSemanticsNodes().isNotEmpty() }
    private fun ComposeUiTest.labelCount() = onAllNodesWithTag("nav.label", useUnmergedTree = true).fetchSemanticsNodes().size

    @Test fun fiveTabsFitANarrowPhoneEachWithItsNameInsideTheBarAndNotOverlapping() = runDesktopComposeUiTest(320, 700) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        assertEquals(listOf("HOME", "SHORTS", "SUBSCRIPTIONS", "EXPLORE", "LIBRARY"), tabs())
        val bar = onNodeWithTag("component.bottom_nav").fetchSemanticsNode().boundsInRoot
        val bounds = tabs().map { onNodeWithTag("nav.tab.$it").fetchSemanticsNode().boundsInRoot }
        for (b in bounds) assertTrue(b.left >= bar.left - 1f && b.right <= bar.right + 1f, "a tab is outside the bar: $b in $bar")
        for ((a, b) in bounds.zipWithNext()) assertTrue(a.right <= b.left + 1f, "tabs overlap: $a / $b")
        assertEquals(5, labelCount())
        for (n in onAllNodesWithTag("nav.label", useUnmergedTree = true).fetchSemanticsNodes()) assertTrue(n.boundsInRoot.right <= bar.right + 1f && n.boundsInRoot.left >= bar.left - 1f, "a name is cut by the bar edge")
    }

    @Test fun theNavigationPageShowsTheTabsAndTheSwitches() = runDesktopComposeUiTest(400, 1500) {
        val f = buildFixture(withDownloads = false); f.navigator.push(Route.CustomizePage("navigation"))
        setContent { AppUnderTest(f) }; waitForIdle()
        assertEquals(0, onAllNodesWithTag("customize.nav.preview", useUnmergedTree = true).fetchSemanticsNodes().size, "no preview any more: the real bar is the preview")
        for (t in listOf("HOME", "SHORTS", "SUBSCRIPTIONS", "EXPLORE", "LIBRARY")) onNodeWithTag("customize.nav.row.$t", useUnmergedTree = true).assertExists()
        onNodeWithTag("customize.nav.explore", useUnmergedTree = true).assertExists()
        onNodeWithTag("setting.look.nav.showLabels", useUnmergedTree = true).assertExists()
    }

    @Test fun theSwitchHidesExploreFromTheBarAtOnceAndBringsItBack() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false); f.navigator.push(Route.CustomizePage("navigation"))
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("customize.nav.explore", useUnmergedTree = true).performClick(); waitForIdle()
        assertEquals(false, f.settings.resolve(LookSettings.showExplore))
        assertEquals(listOf("HOME", "SHORTS", "SUBSCRIPTIONS", "LIBRARY"), tabs())
        onNodeWithTag("customize.nav.explore", useUnmergedTree = true).performClick(); waitForIdle()
        assertEquals(listOf("HOME", "SHORTS", "SUBSCRIPTIONS", "EXPLORE", "LIBRARY"), tabs())
    }

    @Test fun hidingTheNamesLeavesIconsOnlyAndEveryTabStillWorksAndIsAnnounced() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        f.settings.set(LookSettings.showNavLabels, false)
        setContent { AppUnderTest(f) }; waitForIdle()
        assertEquals(0, labelCount(), "no names under the icons")
        assertEquals(5, tabs().size, "the same five tabs")
        // Screen readers still hear the name: the icon carries it.
        onNodeWithTag("nav.tab.EXPLORE").assertExists()
        assertTrue(onAllNodesWithContentDescription("Explore", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "the Explore icon is described")
        onNodeWithTag("nav.tab.EXPLORE").performClick(); waitForIdle()
        assertEquals(Route.Explore, f.navigator.current)
        onNodeWithTag("nav.tab.LIBRARY").performClick(); waitForIdle()
        assertEquals(Route.Library, f.navigator.current)
    }

    @Test fun withExploreHiddenTheBrowseExploreButtonsStillOpenItAndNoTabLooksSelected() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        f.settings.set(LookSettings.showExplore, false)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("home.signin.explore", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("home.signin.explore", useUnmergedTree = true).performClick(); waitForIdle()
        assertEquals(Route.Explore, f.navigator.current)
        for (t in tabs()) onNodeWithTag("nav.tab.$t").assertIsNotSelected()
        assertTrue(f.navigator.back() || true)
    }

    @Test fun theShortsRowOpensTheShortsSettingsAndSaysWhetherShortsAreShown() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.settings.set(Settings.shortsMode, ShortsMode.HIDE); f.navigator.push(Route.CustomizePage("navigation"))
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithText("Hidden. Change it in the Shorts settings.", useUnmergedTree = true).assertExists()
        assertEquals(listOf("HOME", "SUBSCRIPTIONS", "EXPLORE", "LIBRARY"), tabs())
        onNodeWithTag("customize.nav.shorts", useUnmergedTree = true).performClick(); waitForIdle()
        assertEquals(Route.SettingsPage("shorts"), f.navigator.current)
    }

    @Test fun theCustomizeHubListsTheNavigationPageWithASummary() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false); f.navigator.push(Route.Customize)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("customize.row.navigation", useUnmergedTree = true).assertExists()
        onNodeWithText("5 tabs · names shown", useUnmergedTree = true).assertExists()
        onNodeWithTag("customize.row.navigation", useUnmergedTree = true).performClick(); waitForIdle()
        assertEquals(Route.CustomizePage("navigation"), f.navigator.current)
    }

    @Test fun onAWideWindowTheFoldedRailDropsItsNamesButTheUnfoldedOneKeepsThem() = runDesktopComposeUiTest(1200, 800) {
        val f = buildFixture(withDownloads = false)
        f.settings.set(LookSettings.showNavLabels, false)
        setContent { AppUnderTest(f) }; waitForIdle()
        assertEquals(5, tabs("rail.tab.").size)
        assertTrue(labelCount() >= 5, "unfolded: names are the content of the rail")
        onNodeWithTag("rail.toggle").performClick(); waitForIdle()
        f.settings.set(LookSettings.railCollapsed, true); waitForIdle()
        assertEquals(0, labelCount(), "folded with names off: icons only")
        onNodeWithTag("rail.tab.EXPLORE").performClick(); waitForIdle()
        assertEquals(Route.Explore, f.navigator.current)
    }

    private fun ComposeUiTest.barBounds() = onNodeWithTag("component.bottom_nav").fetchSemanticsNode().boundsInRoot

    @Test fun aSolidBarKeepsThePageAboveItAndATranslucentOneLetsThePageRunUnderIt() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false); f.navigator.selectTab(Route.Explore)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("explore.list", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val solidPage = onNodeWithTag("explore.list", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(solidPage.bottom <= barBounds().top + 1f, "solid: the page ends above the bar")
        f.settings.set(LookSettings.translucentNavBar, true); waitForIdle()
        val page = onNodeWithTag("explore.list", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(page.bottom >= barBounds().bottom - 1f, "translucent: the page runs to the bottom of the window, under the bar (page ends ${page.bottom}, bar ends ${barBounds().bottom})")
    }

    @Test fun underATranslucentBlurredBarTheLastCardCanStillBeScrolledClearOfIt() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        f.settings.set(LookSettings.translucentNavBar, true)
        f.navigator.selectTab(Route.Explore)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("card.title", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("explore.list", useUnmergedTree = true).performScrollToIndex(FakeData.videos.size - 1); waitForIdle()
        val lastBottom = onAllNodesWithTag("card.title", useUnmergedTree = true).fetchSemanticsNodes().maxOf { it.boundsInRoot.bottom }
        assertTrue(lastBottom <= barBounds().top + 2f, "the last card ends at ${lastBottom}, above the bar at ${barBounds().top}")
    }

    @Test fun aPageTheShellPadsItselfStaysClearOfATranslucentBar() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        f.settings.set(LookSettings.translucentNavBar, true)
        f.navigator.selectTab(Route.Library)
        setContent { AppUnderTest(f) }; waitForIdle()
        val bar = barBounds()
        val lowest = onAllNodes(isRoot().not() and hasText("Library", substring = true), useUnmergedTree = true).fetchSemanticsNodes().filter { it.boundsInRoot.top < bar.top }.maxOfOrNull { it.boundsInRoot.bottom } ?: 0f
        assertTrue(lowest <= bar.top + 2f, "Library's content ends above the bar")
    }

    private fun ComposeUiTest.bounds(tag: String) = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test fun theTopBarIsAlsoSeeThroughAndTheFirstCardStartsBelowIt() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false); f.navigator.selectTab(Route.Explore)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("card.title", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val solidList = bounds("explore.list"); val solidBar = bounds("topbar")
        assertTrue(solidList.top >= solidBar.bottom - 1f, "solid: the page starts below the top bar")
        f.settings.set(LookSettings.translucentNavBar, true); waitForIdle()
        val bar = bounds("topbar"); val list = bounds("explore.list")
        assertTrue(list.top <= bar.top + 1f, "translucent: the page starts at the top of the window, under the bar (list ${list.top}, bar ${bar.top})")
        val firstCard = onAllNodesWithTag("card.title", useUnmergedTree = true).fetchSemanticsNodes().minOf { it.boundsInRoot.top }
        assertTrue(firstCard >= bar.bottom - 1f, "the first card starts below the bar: card $firstCard, bar ends ${bar.bottom}")
    }

    @Test fun onHomeTheChipStripSitsUnderTheTopBarAndOverTheList() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        f.settings.set(LookSettings.translucentNavBar, true)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("home.chips", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val bar = bounds("topbar"); val chips = bounds("home.chips"); val list = bounds("home.list")
        assertTrue(chips.top >= bar.bottom - 1f, "the chips start below the top bar: chips ${chips.top}, bar ends ${bar.bottom}")
        assertTrue(list.top <= bar.top + 1f, "the list runs from the top of the window, under both")
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("home.signin", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(bounds("home.signin").top >= chips.bottom - 1f, "the first thing of the list starts below the chips, not behind them")
    }

    @Test fun solidBarsKeepHomesChipsAboveTheListAsBefore() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("home.chips", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(bounds("home.list").top >= bounds("home.chips").bottom - 1f, "solid: the list starts below the chips")
    }

    @Test fun theMiniPlayerStaysAttachedToATranslucentBar() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        f.settings.set(LookSettings.translucentNavBar, true)
        f.navigator.push(Route.Video(FakeData.videos[0])); f.navigator.back()
        setContent { AppUnderTest(f) }; waitForIdle()
        val m = onNodeWithTag("component.mini_player").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(m.bottom - barBounds().top) < 2f, "mini bottom ${m.bottom}, bar top ${barBounds().top}")
    }

    @Test fun theIconAnimationRunsToACleanEndOnEveryTapAndCanBeSwitchedOff() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        for (t in listOf("SUBSCRIPTIONS", "EXPLORE", "LIBRARY", "HOME")) { onNodeWithTag("nav.tab.$t").performClick(); waitForIdle(); onNodeWithTag("nav.tab.$t").assertIsSelected() }
        // Two quick taps: the first animation is cut short by the second.
        onNodeWithTag("nav.tab.LIBRARY").performClick(); onNodeWithTag("nav.tab.SUBSCRIPTIONS").performClick(); waitForIdle()
        onNodeWithTag("nav.tab.SUBSCRIPTIONS").assertIsSelected(); onNodeWithTag("nav.tab.LIBRARY").assertIsNotSelected()
        f.settings.set(LookSettings.animateNavIcons, false); waitForIdle()
        onNodeWithTag("nav.tab.HOME").performClick(); waitForIdle()
        onNodeWithTag("nav.tab.HOME").assertIsSelected()
    }

    @Test fun theNavigationPageOffersTheTranslucentAndAnimationSwitchesAndTheSummaryNamesTheBackground() = runDesktopComposeUiTest(400, 1000) {
        val f = buildFixture(withDownloads = false); f.navigator.push(Route.CustomizePage("navigation"))
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("setting.look.nav.translucent.switch", useUnmergedTree = true).assertExists()
        onNodeWithTag("setting.look.nav.animateIcons.switch", useUnmergedTree = true).assertExists()
        assertEquals(0, onAllNodesWithTag("setting.look.nav.barOpacity", useUnmergedTree = true).fetchSemanticsNodes().size, "no Glass choice any more")
        f.settings.set(LookSettings.translucentNavBar, true)
        f.navigator.back(); f.navigator.push(Route.Customize); waitForIdle()
        onNodeWithText("5 tabs · names shown · translucent", useUnmergedTree = true).assertExists()
    }

    @Test fun resettingThePageBringsBackExploreAndTheNames() = runDesktopComposeUiTest(400, 900) {
        val f = buildFixture(withDownloads = false)
        f.settings.set(LookSettings.showExplore, false); f.settings.set(LookSettings.showNavLabels, false)
        f.navigator.push(Route.CustomizePage("navigation"))
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("customize.menu", useUnmergedTree = true).performClick(); waitForIdle()
        onNodeWithTag("customize.resetPage", useUnmergedTree = true).performClick(); waitForIdle()
        assertEquals(true, f.settings.resolve(LookSettings.showExplore)); assertEquals(true, f.settings.resolve(LookSettings.showNavLabels))
        assertEquals(5, tabs().size); assertEquals(5, labelCount())
    }
}
