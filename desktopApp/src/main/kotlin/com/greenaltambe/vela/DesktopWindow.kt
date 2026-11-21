package com.greenaltambe.vela

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import com.greenaltambe.vela.core.settings.SettingsStore
import com.greenaltambe.vela.ui.platform.LocalVolumeSlider
import com.greenaltambe.vela.ui.platform.LocalWindowControls
import com.greenaltambe.vela.ui.platform.WindowControls
import com.greenaltambe.vela.ui.theme.AppTheme
import com.greenaltambe.vela.ui.theme.LocalSystemDark
import kotlinx.coroutines.flow.debounce

/** One native window of the workspace: the tab strip over the app of the selected tab. */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun DesktopWindow(
    model: WindowModel, workspace: Workspace, services: AppServices, store: SettingsStore, systemDark: Boolean?, playerNote: @Composable () -> Unit,
    first: Boolean, icon: androidx.compose.ui.graphics.painter.Painter, iconImages: List<java.awt.Image>, blankPointer: androidx.compose.ui.input.pointer.PointerIcon,
    onNoWindowsLeft: () -> Unit,
) {
    val state = model.state
    // Video fullscreen is a real fullscreen window, like a browser's, but it is done on the AWT window directly and not through WindowState.placement:
    // Compose's placement round trip (Maximized -> Fullscreen -> back) shrinks the window and flashes it small. AWT's own fullScreenWindow leaves the
    // window's maximized state alone (measured on GNOME), so a maximized window comes back maximized; a floating one gets its bounds put back.
    val controls = remember(model) {
        object : WindowControls {
            private var floatingBounds: java.awt.Rectangle? = null
            override val isFullscreen: Boolean get() = model.videoFullscreen
            override fun toggleFullscreen() {
                val w = model.awt
                val device = w?.graphicsConfiguration?.device
                if (!model.videoFullscreen) {
                    floatingBounds = w?.bounds
                    model.videoFullscreen = true
                    if (device != null && device.isFullScreenSupported) device.fullScreenWindow = w
                } else {
                    model.videoFullscreen = false
                    if (device != null && device.fullScreenWindow === w) device.fullScreenWindow = null
                    if (w != null && state.placement == androidx.compose.ui.window.WindowPlacement.Floating) floatingBounds?.let { w.bounds = it }
                }
            }
        }
    }
    // A tab change (or a tab moved to another window) ends fullscreen first, so the screen is never left taken by a window that no longer plays.
    LaunchedEffect(model.selected) { if (controls.isFullscreen) controls.toggleFullscreen() }
    // Size, place and maximized state are kept as they change (not every frame of a drag), and the next start opens the same.
    LaunchedEffect(state) {
        snapshotFlow { if (model.videoFullscreen) null else SavedGeometry.of(state) }.debounce(600).collect { g -> if (g != null) store.saveGeometry(g) }
    }
    val title = tabTitle(model.selected.navigator, front = true).let { if (it == "Home") "Vela" else "$it - Vela" }
    Window(
        onCloseRequest = { if (workspace.closeWindow(model)) onNoWindowsLeft() },
        state = state, title = title, icon = icon,
        onPreviewKeyEvent = { e -> handleKey(e, model, workspace, onNoWindowsLeft) },
        // Keys that reached the window without a focused element taking them (the player's controls fade out with the button that had the focus): the page's shortcuts still apply.
        onKeyEvent = { e -> model.selected.navigator.keyHandler?.invoke(e) ?: false },
    ) {
        remember(window) { window.iconImages = iconImages; model.awt = window }
        // A window opened already maximized keeps its content at the size it was created with (a black strip along the bottom on GNOME) until
        // the placement changes once (minimise and restore showed this); doing that change here, as soon as the window is up, gives the same result.
        LaunchedEffect(window) {
            if (state.placement == androidx.compose.ui.window.WindowPlacement.Maximized) {
                kotlinx.coroutines.delay(400)
                if (state.placement == androidx.compose.ui.window.WindowPlacement.Maximized && !model.videoFullscreen) {
                    state.placement = androidx.compose.ui.window.WindowPlacement.Floating
                    kotlinx.coroutines.delay(120)
                    state.placement = androidx.compose.ui.window.WindowPlacement.Maximized
                }
            }
        }
        androidx.compose.runtime.DisposableEffect(window) {
            val listener = object : java.awt.event.WindowAdapter() { override fun windowActivated(e: java.awt.event.WindowEvent?) { workspace.focused = model } }
            window.addWindowListener(listener)
            onDispose { if (model.videoFullscreen) { model.videoFullscreen = false; window.graphicsConfiguration?.device?.let { d -> if (d.fullScreenWindow === window) d.fullScreenWindow = null } }; window.removeWindowListener(listener); if (model.awt === window) model.awt = null }
        }
        CompositionLocalProvider(com.greenaltambe.vela.ui.nav.LocalTabPlayer provides model.selected.player, LocalSystemDark provides systemDark, LocalWindowControls provides controls, LocalVolumeSlider provides true, com.greenaltambe.vela.ui.platform.LocalBlankPointer provides blankPointer) {
            AppTheme(services.settings) {
                Column(Modifier.fillMaxSize()) {
                    // A video in fullscreen has the whole window: no tabs then.
                    if (!controls.isFullscreen) TabStrip(model, workspace, onNoWindowsLeft)
                    playerNote()
                    // Only the selected tab is composed; the others keep their page stack (and come back to it) in their TabModel.
                    androidx.compose.runtime.key(model.selected.id) {
                        androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxSize()) { val tab = model.selected
                        App(services, tab.navigator, startupAnimation = first && !tab.splashShown && tab === model.tabs.first(), homeFeed = tab.homeFeed, onSplashDone = { tab.splashShown = true }) }
                    }
                }
            }
        }
    }
}

/** Ctrl+T new tab, Ctrl+W close, Ctrl+Tab / Ctrl+Shift+Tab next / previous, Ctrl+1..9 jump, Ctrl+Shift+N new window. Everything else goes on to the page. */
private fun handleKey(e: androidx.compose.ui.input.key.KeyEvent, w: WindowModel, ws: Workspace, onNoWindowsLeft: () -> Unit): Boolean {
    if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return false
    when (e.key) {
        Key.T -> { if (e.isShiftPressed) return false; ws.newTab(w); return true }
        Key.W -> { if (ws.closeTab(w, w.selected)) onNoWindowsLeft(); return true }
        Key.N -> { if (!e.isShiftPressed) return false; ws.open(listOf(TabModel()), w.state.size); return true }
        Key.Tab -> {
            val i = w.indexOf(w.selected); val n = w.tabs.size
            w.select(w.tabs[if (e.isShiftPressed) (i - 1 + n) % n else (i + 1) % n]); return true
        }
        Key.PageDown -> { w.select(w.tabs[(w.indexOf(w.selected) + 1) % w.tabs.size]); return true }
        Key.PageUp -> { w.select(w.tabs[(w.indexOf(w.selected) - 1 + w.tabs.size) % w.tabs.size]); return true }
    }
    val digit = (Key.One.keyCode..Key.Nine.keyCode).indexOf(e.key.keyCode)
    if (digit >= 0 && !e.isShiftPressed) { w.tabs.getOrNull(if (digit == 8) w.tabs.lastIndex else digit)?.let { w.select(it) }; return true }
    return false
}
