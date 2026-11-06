@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavigationUiTest {
    @Test fun labelsAreAlwaysVisible() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        assertEquals(4, labelCount())
        repeat(3) { onNodeWithTag("home.list").performTouchInput { swipeUp(durationMillis = 150) } }; waitForIdle()
        assertEquals(4, labelCount(), "scrolling does not hide labels")
    }

    @Test fun theBottomBarHasTheFixedTabsAndNoSettings() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        for (t in listOf("HOME", "SHORTS", "SUBSCRIPTIONS", "LIBRARY")) onNodeWithTag("nav.tab.$t").assertExists()
        onNodeWithTag("nav.tab.SETTINGS").assertDoesNotExist()
        onNodeWithTag("nav.tab.SUBSCRIPTIONS").performClick(); waitForIdle()
        assertEquals(Route.Subscriptions, f.navigator.current)
    }

    @Test fun theAvatarOpensTheProfileMenuWithEveryEntryAndLocalModeOffersSignIn() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = true)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("profile.button").performClick(); waitForIdle()
        onNodeWithTag("profile.menu").assertExists(); onNodeWithTag("profile.name").assertTextEquals("Local")
        for (t in listOf("profile.signIn", "profile.settings", "profile.about")) onNodeWithTag(t).assertExists()
        onNodeWithTag("profile.signOut").assertDoesNotExist()
        onNodeWithTag("profile.settings").performClick(); waitForIdle()
        assertEquals(Route.Settings, f.navigator.current); onNodeWithTag("profile.menu").assertDoesNotExist()
        assertTrue(f.navigator.canGoBack, "Back returns to the page the menu was opened from")
    }

    @Test fun theAppMarkIsOnTheLeftAndTheProfileButtonOnTheRight() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        val logo = onNodeWithTag("topbar.logo").fetchSemanticsNode().boundsInRoot.center.x
        val profile = onNodeWithTag("profile.button").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(logo < 100 && profile > 300, "logo=$logo profile=$profile")
    }

    private fun ComposeUiTest.bar() = onNodeWithTag("component.bottom_nav").fetchSemanticsNode().boundsInRoot
    private fun ComposeUiTest.mini() = onNodeWithTag("component.mini_player").fetchSemanticsNode().boundsInRoot
    private fun ComposeUiTest.labelCount() = onAllNodesWithTag("nav.label", useUnmergedTree = true).fetchSemanticsNodes().size
    private fun Fixture.withMini() { navigator.push(Route.Video(FakeData.videos[0])); navigator.back() }

    @Test fun aDockedBarSitsBelowThePage() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        val b = bar(); val page = onNodeWithTag("home.list").fetchSemanticsNode().boundsInRoot
        assertTrue(page.bottom <= b.top + 1f, "docked: page ends ${page.bottom}, bar starts ${b.top}")
        assertTrue(b.width >= 399f, "docked is full width (${b.width})")
        for (n in onAllNodesWithTag("nav.label", useUnmergedTree = true).fetchSemanticsNodes()) assertTrue(n.boundsInRoot.top >= b.top - 1f && n.boundsInRoot.bottom <= b.bottom + 1f)
    }

    @Test fun theMiniPlayerGoesAwayOnShortsAndStaysGone() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false); f.withMini()
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("component.mini_player").assertExists()
        f.navigator.push(Route.Shorts); waitForIdle()
        onNodeWithTag("component.mini_player").assertDoesNotExist()
        f.navigator.back(); waitForIdle()
        onNodeWithTag("component.mini_player").assertDoesNotExist()
    }

    @Test fun theRegularMiniPlayerIsAsWideAsTheBarAndAttachedWhenAsked() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false); f.withMini()
        setContent { AppUnderTest(f) }; waitForIdle()
        val b = bar(); val m = mini()
        assertTrue(kotlin.math.abs(m.width - b.width) < 2f, "mini ${m.width} wide, bar ${b.width}")
        assertTrue(kotlin.math.abs(m.bottom - b.top) < 2f, "attached: mini bottom ${m.bottom}, bar top ${b.top}")
    }

    @Test fun wideWindowsGetACollapsibleRailWithTheSameTabs() = runDesktopComposeUiTest(1200, 800) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("component.side_rail").assertExists(); onNodeWithTag("component.bottom_nav").assertDoesNotExist()
        val wide = onNodeWithTag("component.side_rail").fetchSemanticsNode().size.width
        onNodeWithTag("rail.toggle").performClick(); waitForIdle()
        val narrow = onNodeWithTag("component.side_rail").fetchSemanticsNode().size.width
        assertTrue(narrow < wide, "collapsed $narrow < expanded $wide"); assertTrue(f.settings.resolve(LookSettings.railCollapsed))
        onNodeWithTag("rail.tab.LIBRARY").performClick(); waitForIdle()
        assertEquals(Route.Library, f.navigator.current)
    }
}
