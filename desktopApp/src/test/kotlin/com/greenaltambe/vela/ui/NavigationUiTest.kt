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
    @Test fun theBottomBarHasTheConfiguredTabsAndNoSettings() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        for (t in listOf("HOME", "SUBSCRIPTIONS", "LIBRARY")) onNodeWithTag("nav.tab.$t").assertExists()
        onNodeWithTag("nav.tab.SETTINGS").assertDoesNotExist()
        onNodeWithTag("nav.tab.SUBSCRIPTIONS").performClick(); waitForIdle()
        assertEquals(Route.Subscriptions, f.navigator.current)
    }

    @Test fun theTabListLabelsAndBarStyleFollowTheSettings() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        f.settings.set(LookSettings.navTabs, "downloads,home,search,library"); f.settings.set(LookSettings.navLabels, NavLabels.HIDDEN)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("nav.tab.DOWNLOADS").assertExists(); onNodeWithTag("nav.tab.SEARCH").assertExists(); onNodeWithTag("nav.tab.SUBSCRIPTIONS").assertDoesNotExist()
        assertEquals(0, onAllNodesWithTag("nav.label", useUnmergedTree = true).fetchSemanticsNodes().size, "labels: hidden")
        onNodeWithTag("nav.tab.SEARCH").performClick(); waitForIdle()
        assertTrue(f.navigator.current is Route.Search)
    }

    @Test fun theAvatarOpensTheProfileMenuWithEveryEntryAndLocalModeOffersSignIn() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = true)
        setContent { AppUnderTest(f) }; waitForIdle()
        onNodeWithTag("profile.button").performClick(); waitForIdle()
        onNodeWithTag("profile.menu").assertExists(); onNodeWithTag("profile.name").assertTextEquals("Local")
        for (t in listOf("profile.signIn", "profile.settings", "profile.customize", "profile.downloads", "profile.history", "profile.backup", "profile.health", "profile.about")) onNodeWithTag(t).assertExists()
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

    @Test fun aFloatingBarRunsOverThePageAndThePageLeavesRoomForItAtTheEnd() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        val b = bar(); val page = onNodeWithTag("home.list").fetchSemanticsNode().boundsInRoot
        assertTrue(page.bottom > b.top + 1f, "floating: the page runs under the bar (page ends ${page.bottom}, bar starts ${b.top})")
        onNodeWithTag("home.list").performScrollToIndex(12); waitForIdle()
        val lastCard = onAllNodesWithTag("component.video_card").fetchSemanticsNodes().maxOf { it.boundsInRoot.bottom }
        assertTrue(lastCard <= b.top + 1f, "the last card ($lastCard) is not hidden by the bar (${b.top})")
    }

    @Test fun aDockedBarSitsBelowThePage() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false); f.settings.set(LookSettings.barStyle, BarStyle.DOCKED)
        setContent { AppUnderTest(f) }; waitForIdle()
        val b = bar(); val page = onNodeWithTag("home.list").fetchSemanticsNode().boundsInRoot
        assertTrue(page.bottom <= b.top + 1f, "docked: page ends ${page.bottom}, bar starts ${b.top}")
        assertTrue(b.width >= 399f, "docked is full width (${b.width})")
        for (n in onAllNodesWithTag("nav.label", useUnmergedTree = true).fetchSemanticsNodes()) assertTrue(n.boundsInRoot.top >= b.top - 1f && n.boundsInRoot.bottom <= b.bottom + 1f)
    }

    @Test fun aMinimalBarIsASmallIconPillThatFloats() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false); f.settings.set(LookSettings.barStyle, BarStyle.MINIMAL)
        setContent { AppUnderTest(f) }; waitForIdle()
        val b = bar()
        assertTrue(b.width < 300f && labelCount() == 0, "minimal: ${b.width} wide, ${labelCount()} labels")
        assertTrue(onNodeWithTag("home.list").fetchSemanticsNode().boundsInRoot.bottom > b.top, "it floats over the page")
    }

    @Test fun labelsAreEitherHiddenOrAlwaysVisible() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false)
        setContent { AppUnderTest(f) }; waitForIdle()
        assertEquals(3, labelCount(), "always visible (default)")
        f.settings.set(LookSettings.navLabels, NavLabels.HIDDEN); waitForIdle(); assertEquals(0, labelCount())
        assertEquals(listOf(NavLabels.HIDDEN, NavLabels.ALWAYS), NavLabels.entries, "no hide-while-scrolling mode any more")
        // Scrolling alone never touches the labels (only the shrink option does).
        f.settings.set(LookSettings.navLabels, NavLabels.ALWAYS); waitForIdle()
        repeat(3) { onNodeWithTag("home.list").performTouchInput { swipeUp(durationMillis = 150) } }; waitForIdle()
        assertEquals(3, labelCount(), "scrolling does not hide labels when shrink is off")
    }

    @Test fun theBarShrinksWhileScrollingDownHidesItsLabelsAndComesBackScrollingUp() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false); f.settings.set(LookSettings.navShrink, true)
        setContent { AppUnderTest(f) }; waitForIdle()
        val full = bar(); assertEquals(3, labelCount())
        repeat(3) { onNodeWithTag("home.list").performTouchInput { swipeUp(durationMillis = 150) } }; waitForIdle(); mainClock.advanceTimeBy(800)
        val small = bar()
        assertTrue(small.width < full.width * 0.7f && small.height < full.height && labelCount() == 0, "shrunk: ${small.width}x${small.height} from ${full.width}x${full.height}, labels ${labelCount()}")
        repeat(3) { onNodeWithTag("home.list").performTouchInput { swipeDown(durationMillis = 150) } }; waitForIdle(); mainClock.advanceTimeBy(800)
        assertTrue(bar().width > full.width * 0.95f && labelCount() == 3, "back to the full bar with its labels")
    }

    @Test fun theContractionIsOneSmoothMotionWithTheIconsMovingTogether() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false); f.withMini()
        setContent { AppUnderTest(f) }; waitForIdle()
        fun iconCentres() = listOf("HOME", "SUBSCRIPTIONS", "LIBRARY").map { onNodeWithTag("nav.tab.$it").fetchSemanticsNode().boundsInRoot.center.x }
        val fullBar = bar(); val fullMiniTop = mini().top; val fullCentres = iconCentres()
        mainClock.autoAdvance = false
        // Start the contraction (the same animation a scroll starts, without a gesture moving the clock), then sample it every 30 ms.
        f.settings.set(LookSettings.barStyle, BarStyle.MINIMAL)
        val widths = mutableListOf<Float>(); val miniTops = mutableListOf<Float>(); val gaps = mutableListOf<Float>(); val labels = mutableListOf<Int>()
        repeat(30) { mainClock.advanceTimeBy(30); widths += bar().width; miniTops += mini().top; gaps += iconCentres().let { c -> c[1] - c[0] }; labels += labelCount() }
        mainClock.advanceTimeBy(1500)
        val small = bar(); val smallCentres = iconCentres()
        // Monotonic: the bar only ever contracts, the icons only ever move together, the mini player only ever moves down.
        assertTrue(widths.zipWithNext().all { (a, b) -> b <= a + 0.5f }, "width never grows while contracting: $widths")
        assertTrue(gaps.zipWithNext().all { (a, b) -> b <= a + 0.5f }, "the gap between icons only shrinks: $gaps")
        assertTrue(miniTops.zipWithNext().all { (a, b) -> b >= a - 0.5f }, "the mini player only moves down: $miniTops")
        // Smooth: it passes through in-between sizes, and no single 30 ms step covers more than half of the whole change.
        val total = fullBar.width - small.width
        assertTrue(widths.any { it < fullBar.width - total * 0.2f && it > small.width + total * 0.2f }, "has in-between widths: $widths")
        assertTrue((listOf(fullBar.width) + widths).zipWithNext().all { (a, b) -> a - b <= total * 0.5f }, "no jump: $widths")
        // The icons gather toward the middle (the outer ones move inwards, the middle one stays put).
        assertTrue(smallCentres[0] > fullCentres[0] + 20f && smallCentres[2] < fullCentres[2] - 20f && kotlin.math.abs(smallCentres[1] - fullCentres[1]) < 2f, "icons $fullCentres -> $smallCentres")
        // Labels fade out with the contraction: present at the start of it, gone at the end, never reappearing in between.
        assertTrue(labels.zipWithNext().all { (a, b) -> b <= a }, "labels only go away: $labels"); assertEquals(0, labels.last())
        assertTrue(mini().top > fullMiniTop + 4f, "the mini player followed the bar down")
    }

    @Test fun theRegularMiniPlayerIsAsWideAsTheBarAndAttachedWhenAsked() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false); f.withMini()
        setContent { AppUnderTest(f) }; waitForIdle()
        val b = bar(); val m = mini()
        assertTrue(kotlin.math.abs(m.width - b.width) < 2f, "mini ${m.width} wide, bar ${b.width}")
        assertTrue(kotlin.math.abs(m.bottom - b.top) < 2f, "attached: mini bottom ${m.bottom}, bar top ${b.top}")
    }

    @Test fun apartFromTheBarTheMiniPlayerKeepsTheChosenDistance() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false); f.withMini()
        f.settings.set(LookSettings.miniPlacement, MiniPlacement.SEPARATE); f.settings.set(LookSettings.miniGap, 14f)
        setContent { AppUnderTest(f) }; waitForIdle()
        val gap = bar().top - mini().bottom
        assertTrue(kotlin.math.abs(gap - 14f) < 2f, "gap $gap, wanted 14")
        f.settings.set(LookSettings.miniGap, 0f); waitForIdle()
        assertTrue(bar().top - mini().bottom < 2f, "distance 0 touches the bar")
    }

    @Test fun thePictureInPictureLookIsASmallCornerCard() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false); f.withMini(); f.settings.set(LookSettings.miniLook, MiniLook.PIP)
        setContent { AppUnderTest(f) }; waitForIdle()
        val b = bar(); val m = mini()
        assertTrue(m.width < b.width * 0.6f && kotlin.math.abs(m.width / m.height - 16f / 9f) < 0.05f, "pip ${m.width}x${m.height}")
        assertTrue(kotlin.math.abs(m.right - b.right) < 2f, "right edge lines up with the bar (${m.right} vs ${b.right})")
        assertTrue(m.bottom <= b.top, "above the bar")
    }

    @Test fun theMiniPlayerMovesWithTheBarWhenItShrinks() = runDesktopComposeUiTest(400, 800) {
        val f = buildFixture(withDownloads = false); f.withMini(); f.settings.set(LookSettings.navShrink, true)
        setContent { AppUnderTest(f) }; waitForIdle()
        val before = mini().top
        repeat(3) { onNodeWithTag("home.list").performTouchInput { swipeUp(durationMillis = 150) } }; waitForIdle(); mainClock.advanceTimeBy(800)
        val after = mini().top
        assertTrue(after > before + 4f, "the bar got shorter, so the mini player moved down with it ($before -> $after)")
        assertTrue(bar().top >= mini().bottom - 2f)
    }

    @Test fun cornersFollowTheRadiusSettingAndTheJoinedCardComesApartAsTheBarContracts() {
        val density = androidx.compose.ui.unit.Density(1f)
        fun r(s: androidx.compose.ui.graphics.Shape, pick: (androidx.compose.foundation.shape.RoundedCornerShape) -> androidx.compose.foundation.shape.CornerSize) =
            pick(s as androidx.compose.foundation.shape.RoundedCornerShape).toPx(androidx.compose.ui.geometry.Size(100f, 50f), density)
        val (mini24, bar24) = com.greenaltambe.vela.ui.nav.chromeShapes(24.dp, 32.dp, joined = true, docked = false, f = 0f)
        assertEquals(24f, r(mini24) { it.topStart }); assertEquals(0f, r(mini24) { it.bottomStart }, "joined: square where it meets the bar")
        assertEquals(24f, r(bar24) { it.bottomEnd }); assertEquals(0f, r(bar24) { it.topEnd })
        val (miniHalf, barHalf) = com.greenaltambe.vela.ui.nav.chromeShapes(24.dp, 32.dp, joined = true, docked = false, f = 0.5f)
        assertEquals(12f, r(miniHalf) { it.bottomStart }); assertEquals(16f, r(barHalf) { it.topStart }, "corners are halfway at halfway")
        val (miniDone, barDone) = com.greenaltambe.vela.ui.nav.chromeShapes(24.dp, 32.dp, joined = true, docked = false, f = 1f)
        assertEquals(24f, r(miniDone) { it.bottomStart }); assertEquals(25f, r(barDone) { it.topStart }.coerceAtMost(25f), "a pill once contracted (clamped to half the height)")
        val (mini0, _) = com.greenaltambe.vela.ui.nav.chromeShapes(0.dp, 32.dp, joined = false, docked = false, f = 0f)
        assertEquals(0f, r(mini0) { it.topStart }, "corner radius 0 gives a square mini player")
        val (apart, pill) = com.greenaltambe.vela.ui.nav.chromeShapes(12.dp, 32.dp, joined = false, docked = false, f = 0f)
        assertEquals(12f, r(apart) { it.bottomEnd }); assertTrue(r(pill) { it.topStart } >= 25f, "a floating bar on its own stays a pill")
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
