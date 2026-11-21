package com.greenaltambe.vela

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorkspaceTest {
    private fun workspace() = Workspace(DpSize(1000.dp, 700.dp), WindowPosition.PlatformDefault, WindowPlacement.Floating)

    @Test fun videoFullscreenIsPerWindowAndLeavesPlacementAlone() {
        val ws = Workspace(DpSize(800.dp, 600.dp), WindowPosition.PlatformDefault, WindowPlacement.Maximized)
        val a = ws.windows.first(); val b = ws.open(listOf(TabModel()))
        assertFalse(a.videoFullscreen)
        a.videoFullscreen = true
        assertTrue(a.videoFullscreen); assertFalse(b.videoFullscreen)
        assertEquals(WindowPlacement.Maximized, a.state.placement)
    }

    @Test fun startsWithOneWindowAndOneTab() {
        val ws = workspace()
        assertEquals(1, ws.windows.size); assertEquals(1, ws.windows[0].tabs.size)
    }

    @Test fun newTabIsSelectedAndClosingSelectsANeighbour() {
        val ws = workspace(); val w = ws.windows[0]
        val a = w.tabs[0]; val b = ws.newTab(w); val c = ws.newTab(w)
        assertSame(c, w.selected)
        assertFalse(ws.closeTab(w, c)); assertSame(b, w.selected)
        assertFalse(ws.closeTab(w, a)); assertSame(b, w.selected); assertEquals(1, w.tabs.size)
    }

    @Test fun closingTheLastTabClosesTheWindowAndTheLastWindowEndsTheApp() {
        val ws = workspace(); val w = ws.windows[0]
        assertTrue(ws.closeTab(w, w.tabs[0]), "no window left")
        assertTrue(ws.windows.isEmpty())
    }

    @Test fun reorderMovesATabWithinItsWindow() {
        val ws = workspace(); val w = ws.windows[0]
        val a = w.tabs[0]; val b = ws.newTab(w); val c = ws.newTab(w)
        ws.move(w, 0, 2)
        assertEquals(listOf(b, c, a), w.tabs.toList())
    }

    @Test fun tearOffMakesANewWindowAndNeedsAtLeastTwoTabs() {
        val ws = workspace(); val w = ws.windows[0]
        assertNull(ws.tearOff(w.tabs[0], w, 10, 10), "a window's only tab cannot be torn off")
        val a = w.tabs[0]; val b = ws.newTab(w)
        val n = ws.tearOff(b, w, 100, 50)!!
        assertEquals(2, ws.windows.size); assertEquals(listOf(a), w.tabs.toList()); assertEquals(listOf(b), n.tabs.toList()); assertSame(a, w.selected)
        assertSame(b.navigator, n.selected.navigator, "the page stack travels with the tab")
    }

    @Test fun movingTheOnlyTabOfAWindowIntoAnotherClosesTheEmptyOne() {
        val ws = workspace(); val w1 = ws.windows[0]
        val b = ws.newTab(w1)
        val w2 = ws.tearOff(b, w1, 0, 0)!!
        val a = w1.tabs[0]
        ws.moveTab(a, w1, w2, 0)
        assertEquals(listOf(w2), ws.windows.toList()); assertEquals(listOf(a, b), w2.tabs.toList()); assertSame(a, w2.selected)
    }

    @Test fun geometryRoundTripsAndRejectsNonsense() {
        val g = SavedGeometry(1200, 800, 40, 60, true)
        assertEquals(g, SavedGeometry.decode(g.encode()))
        assertEquals(SavedGeometry(900, 700, null, null, false), SavedGeometry.decode(SavedGeometry(900, 700, null, null, false).encode()))
        assertNull(SavedGeometry.decode("10,10,0,0,0")); assertNull(SavedGeometry.decode("x")); assertNull(SavedGeometry.decode(null))
    }

    @Test fun libmpvHintNamesTheRightPackageManager() {
        assertTrue("dnf" in libmpvInstallHint("ID=fedora\n", "Linux")); assertTrue("apt" in libmpvInstallHint("ID=ubuntu\nID_LIKE=debian\n", "Linux"))
        assertTrue("pacman" in libmpvInstallHint("ID=arch\n", "Linux")); assertTrue("libmpv" in libmpvInstallHint("ID=gentoo\n", "Linux"))
    }
}
