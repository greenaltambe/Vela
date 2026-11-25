@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
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
        assertEquals(5, labelCount())
        repeat(3) { onNodeWithTag("home.list").performTouchInput { swipeUp(durationMillis = 150) } }; waitForIdle()
        assertEquals(5, labelCount(), "scrolling does not hide labels")
    }

    @Test fun theBottomBarHasTheFixedTabsAndNoSettings() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        for (t in listOf("HOME", "SHORTS", "SUBSCRIPTIONS", "EXPLORE", "LIBRARY")) onNodeWithTag("nav.tab.$t").assertExists()
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

    @Test fun theChosenRegionStandsBesideTheNameOnHomeAndFollowsTheSetting() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("topbar.region", useUnmergedTree = true).assertTextEquals("US")
        f.services.settings.set(com.greenaltambe.vela.core.settings.Settings.contentRegion, "GB"); waitForIdle()
        onNodeWithTag("topbar.region", useUnmergedTree = true).assertTextEquals("GB")
        val region = onNodeWithTag("topbar.region", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = onNodeWithText("Vela", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(region.left >= title.right && region.center.y < title.center.y, "raised, after the name: $region against $title")
        screenshot("region-phone")
        f.navigator.selectTab(Route.Library); waitForIdle()
        onNodeWithTag("topbar.region", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun theDesktopBarShowsTheRegionOnEveryPage() = runDesktopComposeUiTest(1280, 800) {
        val f = buildFixture(withDownloads = false)
        f.services.settings.set(com.greenaltambe.vela.core.settings.Settings.contentRegion, "IN")
        setContent { androidx.compose.runtime.CompositionLocalProvider(com.greenaltambe.vela.ui.platform.LocalVolumeSlider provides true) { AppUnderTest(f) } }; waitForIdle()
        onNodeWithTag("topbar.region", useUnmergedTree = true).assertTextEquals("IN")
        screenshot("region-desktop")
        f.navigator.selectTab(Route.Library); waitForIdle()
        onNodeWithTag("topbar.region", useUnmergedTree = true).assertTextEquals("IN")
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

    // ---- the floating window (the viewer's per-device choice of how the video looks minimised) ----

    private fun Fixture.floating() { settings.set(Settings.miniPlayerStyle, MiniPlayerStyle.FLOATING) }

    @Test fun theFloatingMiniPlayerIsASmallSixteenByNineWindowInTheCornerAndTakesNoRoom() = runDesktopComposeUiTest(1000, 700) {
        val f = buildFixture(withDownloads = false); f.floating(); f.withMini()
        setContent { AppUnderTest(f) }; waitForIdle()
        val m = mini()
        assertTrue(m.width in 239f..721f && kotlin.math.abs(m.width / m.height - 16f / 9f) < 0.02f, "window ${m.width} x ${m.height}")
        assertTrue(m.height > 100f, "a window, not a 64 dp bar (${m.height})")
        assertTrue(m.right > 950f && m.bottom > 650f, "bottom right: ${m.right}, ${m.bottom}")
        val page = onNodeWithTag("home.list").fetchSemanticsNode().boundsInRoot
        assertTrue(page.bottom >= m.bottom, "the page runs under the window: page ends ${page.bottom}, window ends ${m.bottom}")
    }

    @Test fun draggingTheFloatingWindowSettlesItInTheNearestCorner() = runDesktopComposeUiTest(1000, 700) {
        val f = buildFixture(withDownloads = false); f.floating(); f.withMini()
        setContent { AppUnderTest(f) }; waitForIdle()
        val before = mini()
        onNodeWithTag("component.mini_player").performTouchInput { swipe(center, Offset(0f, 0f) + Offset(-before.center.x + 150f, -before.center.y + 220f), durationMillis = 300) }
        mainClock.advanceTimeBy(1500); waitForIdle()
        val m = mini()
        assertTrue(m.left < before.left - 100f && m.top < 100f, "top left of the page area now: ${m.left}, ${m.top} (was ${before.left})")
        assertEquals(PipCornerNameForTest, f.navigator.playerSheetState?.pip?.corner?.name)
    }

    @Test fun aTapOnTheFloatingWindowOpensTheVideoPage() = runDesktopComposeUiTest(1000, 700) {
        val f = buildFixture(withDownloads = false); f.floating(); f.withMini()
        setContent { AppUnderTest(f) }; waitForIdle()
        // Not the middle: that is the play button. The lower left corner is plain picture.
        onNodeWithTag("pip.surface", useUnmergedTree = true).performTouchInput { click(Offset(24f, height - 24f)) }
        mainClock.advanceTimeBy(1500); waitForIdle()
        assertTrue(f.navigator.playerSheetState?.isExpanded == true, "the page is open (progress ${f.navigator.playerSheetState?.progress}, slide ${f.navigator.playerSheetState?.slideY})")
    }

    @Test fun draggingTheGripResizesTheWindowAndItStaysInItsCorner() = runDesktopComposeUiTest(1000, 700) {
        val f = buildFixture(withDownloads = false); f.floating(); f.withMini()
        setContent { AppUnderTest(f) }; waitForIdle()
        val before = mini()
        // The window rests bottom right, so its grip is the top left corner: pulling that outwards grows it.
        onNodeWithTag("component.mini_player").performTouchInput { swipe(Offset(8f, 8f), Offset(-92f, -56f), durationMillis = 300) }
        mainClock.advanceTimeBy(1000); waitForIdle()
        val m = mini()
        assertTrue(m.width > before.width + 60f, "grew from ${before.width} to ${m.width}")
        assertTrue(kotlin.math.abs(m.width / m.height - 16f / 9f) < 0.02f, "still sixteen by nine")
        assertTrue(kotlin.math.abs(m.right - before.right) < 2f && kotlin.math.abs(m.bottom - before.bottom) < 2f, "the corner it rests in stays: ${m.right}, ${m.bottom} vs ${before.right}, ${before.bottom}")
    }

    @Test fun theBarStaysTheChoiceWhenAskedFor() = runDesktopComposeUiTest(1000, 700) {
        val f = buildFixture(withDownloads = false); f.settings.set(Settings.miniPlayerStyle, MiniPlayerStyle.BAR); f.withMini()
        setContent { AppUnderTest(f) }; waitForIdle()
        assertTrue(mini().height < 80f, "a bar (${mini().height})")
        onNodeWithTag("pip.surface", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun theChoiceIsNeverSyncedOrBackedUp() {
        assertTrue(!com.greenaltambe.vela.sync.SettingsDomain.syncable(Settings.miniPlayerStyle.key), "not synced")
        assertTrue(!com.greenaltambe.vela.backup.BackupService.backedUp(Settings.miniPlayerStyle.key), "not backed up")
    }

    private companion object { const val PipCornerNameForTest = "TOP_LEFT" }
}
