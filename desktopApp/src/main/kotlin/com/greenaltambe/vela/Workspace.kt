package com.greenaltambe.vela

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.greenaltambe.vela.ui.nav.Navigator

/** One tab: its own page stack (back, forward, scroll) and its own video player ([player], made by the [Workspace]); the data and settings are the app's. */
class TabModel(val navigator: Navigator = Navigator()) {
    val id: Long = nextId.getAndIncrement()
    /** This tab's player, like a browser tab's own media: created on first use, released when the tab closes. Null in tests / without a factory. */
    var player: com.greenaltambe.vela.ui.nav.TabPlayer? = null
    /** The Home feed lives with the tab, so coming back to it shows what it showed (not a reload). */
    val homeFeed = com.greenaltambe.vela.ui.screens.HomeFeedStore()
    /** The startup animation plays once, on the very first tab. */
    var splashShown = false
    private companion object { val nextId = java.util.concurrent.atomic.AtomicLong(1) }
}

/** One window: a row of tabs and the one that shows. [awt] is the native window (set when it exists), for screen positions while dragging. */
class WindowModel(val id: Int, first: List<TabModel>, val state: WindowState) {
    val tabs = mutableStateListOf<TabModel>().also { it.addAll(first) }
    var selected: TabModel by mutableStateOf(first.first())
    var awt: java.awt.Window? = null
    /** The video is fullscreen in this window (real AWT fullscreen, tab strip hidden); the window's own maximized / floating state is kept. */
    var videoFullscreen by mutableStateOf(false)
    /** Height of the tab strip in dp (AWT units), under the native title bar. */
    var stripHeight: Int = 40
    /** Width of one tab in dp, as the strip last laid them out (to find the slot under the pointer when a tab is dropped here). */
    var tabWidthDp: Float = 160f

    /** The tab strip's rectangle on the screen (AWT units), or null while the window is not showing. */
    fun stripOnScreen(): java.awt.Rectangle? = runCatching {
        val root = (awt as? javax.swing.RootPaneContainer)?.rootPane ?: return null
        val o = root.locationOnScreen
        java.awt.Rectangle(o.x, o.y, root.width, stripHeight)
    }.getOrNull()

    fun select(tab: TabModel) { if (tab in tabs) selected = tab }
    fun indexOf(tab: TabModel) = tabs.indexOf(tab)
}

/**
 * Every window and tab of this run. Tabs move between windows by being taken out of one list and put into another (the page stack and its state travel with
 * the [TabModel]); the player, the data and the settings are the app's, shared. Closing the last tab closes its window, closing the last window ends the app.
 */
class Workspace(
    private val firstSize: DpSize, private val firstPosition: WindowPosition, firstPlacement: WindowPlacement,
    /** Makes the player of a new tab (null: tabs have none, as in tests). */
    private val playerFor: (TabModel) -> com.greenaltambe.vela.ui.nav.TabPlayer? = { null },
) {
    val windows = mutableStateListOf<WindowModel>()
    private var nextWindowId = 1
    /** The window the user used last: where a link, Ctrl+T from the media keys or a second launch lands. */
    var focused: WindowModel? by mutableStateOf(null)

    init { open(listOf(TabModel()), firstSize, firstPosition, firstPlacement) }

    fun open(tabs: List<TabModel>, size: DpSize = firstSize, position: WindowPosition = WindowPosition.PlatformDefault, placement: WindowPlacement = WindowPlacement.Floating): WindowModel {
        tabs.forEach { if (it.player == null) it.player = playerFor(it) }
        val state = WindowState(placement = placement, position = position, size = size)
        return WindowModel(nextWindowId++, tabs, state).also { windows += it; focused = it }
    }

    /** Stops and frees every tab's player (the app is quitting). */
    fun releaseAllPlayers() { windows.forEach { w -> w.tabs.forEach { it.player?.release() } } }

    /** Every tab of every window. */
    fun allTabs(): List<TabModel> = windows.flatMap { it.tabs }

    fun activeNavigator(): Navigator? = (focused ?: windows.firstOrNull())?.selected?.navigator

    fun newTab(window: WindowModel, at: Int = window.tabs.size): TabModel {
        val tab = TabModel().also { it.player = playerFor(it) }
        window.tabs.add(at.coerceIn(0, window.tabs.size), tab); window.selected = tab
        return tab
    }

    /** Closes a tab; the last one of a window closes the window. Returns true when no window is left. */
    fun closeTab(window: WindowModel, tab: TabModel): Boolean {
        val i = window.indexOf(tab)
        if (i < 0) return windows.isEmpty()
        if (window.tabs.size == 1) return closeWindow(window)
        window.tabs.removeAt(i)
        tab.player?.release()
        if (window.selected === tab) window.selected = window.tabs[i.coerceAtMost(window.tabs.lastIndex)]
        return false
    }

    /** Closes a window and every tab in it, stopping their players (a tab already moved out of it, as in [moveTab], is not touched). */
    fun closeWindow(window: WindowModel): Boolean {
        window.tabs.forEach { it.player?.release() }
        windows.remove(window)
        if (focused === window) focused = windows.lastOrNull()
        return windows.isEmpty()
    }

    /** Puts [tab] of [from] into [to] at [index] (another window of the same run) and selects it there. A window left without tabs closes. */
    fun moveTab(tab: TabModel, from: WindowModel, to: WindowModel, index: Int) {
        if (from === to) return
        val i = from.indexOf(tab)
        if (i < 0) return
        from.tabs.removeAt(i)
        if (from.tabs.isEmpty()) closeWindow(from) else if (from.selected === tab) from.selected = from.tabs[i.coerceAtMost(from.tabs.lastIndex)]
        to.tabs.add(index.coerceIn(0, to.tabs.size), tab)
        to.selected = tab
        focused = to
    }

    /** Takes [tab] out of [from] into a window of its own, with its top-left near the screen point (x, y). */
    fun tearOff(tab: TabModel, from: WindowModel, x: Int, y: Int): WindowModel? {
        if (from.tabs.size < 2 || from.indexOf(tab) < 0) return null
        val i = from.indexOf(tab)
        from.tabs.removeAt(i)
        if (from.selected === tab) from.selected = from.tabs[i.coerceAtMost(from.tabs.lastIndex)]
        return open(listOf(tab), from.state.size, WindowPosition(x.dp, y.dp), WindowPlacement.Floating)
    }

    fun move(window: WindowModel, from: Int, to: Int) {
        if (from == to || from !in window.tabs.indices || to !in window.tabs.indices) return
        window.tabs.add(to, window.tabs.removeAt(from))
    }
}
