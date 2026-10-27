package com.greenaltambe.vela.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import com.greenaltambe.vela.ui.components.MenuAction
import com.greenaltambe.vela.ui.components.VelaTopBar
import com.greenaltambe.vela.ui.layout.LocalWindowSize
import com.greenaltambe.vela.ui.layout.LocalWindowWidthDp
import com.greenaltambe.vela.ui.layout.WindowSizeClass
import com.greenaltambe.vela.ui.layout.windowSizeClassFor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.ui.components.BottomNav
import com.greenaltambe.vela.ui.components.MiniPlayer
import com.greenaltambe.vela.ui.components.SideRail
import com.greenaltambe.vela.ui.look.LocalLook
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.nav.TopLevelRoutes
import com.greenaltambe.vela.ui.nav.topLevel
import com.greenaltambe.vela.ui.platform.LocalInPictureInPicture
import com.greenaltambe.vela.ui.platform.LocalWindowControls
import com.greenaltambe.vela.ui.platform.PlatformBackHandler
import com.greenaltambe.vela.ui.player.applyShortcut
import com.greenaltambe.vela.ui.player.shortcutFor
import com.greenaltambe.vela.ui.screens.AccountsScreen
import com.greenaltambe.vela.ui.screens.ChannelScreen
import com.greenaltambe.vela.ui.screens.DownloadsScreen
import com.greenaltambe.vela.ui.screens.HistoryScreen
import com.greenaltambe.vela.ui.screens.HomeScreen
import com.greenaltambe.vela.ui.screens.LibraryScreen
import com.greenaltambe.vela.ui.screens.PlaylistScreen
import com.greenaltambe.vela.ui.screens.SearchScreen
import com.greenaltambe.vela.ui.screens.SettingsHost
import com.greenaltambe.vela.ui.screens.SubscriptionsScreen
import com.greenaltambe.vela.ui.screens.VideoScreen

/**
 * The frame around every screen. The window's real width picks the size class (provided to everything below): a bottom
 * bar on COMPACT, a side rail on MEDIUM and EXPANDED. Each screen gets a top bar with its title, search and an overflow
 * menu (the video page and the search view draw their own), the mini player docks above the nav while something plays
 * and its page is closed, and Back works on both platforms. System bars and cutouts are respected by the bars
 * themselves, so no screen below needs to think about insets.
 */
@Composable
fun AppShell(services: AppServices, navigator: Navigator = remember { Navigator() }) {
    val route = navigator.current
    val spacing = LocalLook.current.spacing
    val window = LocalWindowControls.current
    val inPip = LocalInPictureInPicture.current
    val focus = remember { FocusRequester() }
    val host = services.player
    val playerState = host?.controllerState?.collectAsState()?.value

    PlatformBackHandler(enabled = navigator.canGoBack) { navigator.back() }

    // Queue: when a video ends and others are queued, play the next one.
    LaunchedEffect(playerState?.status) {
        if (playerState?.status == PlaybackStatus.ENDED) {
            // "Delete after watching": a finished download is removed once it has played to the end.
            val finished = navigator.nowPlaying
            val downloads = services.downloads
            if (finished != null && downloads != null && services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.downloadsDeleteAfterWatch)) {
                downloads.repo.completedFor(finished.id)?.let { downloads.manager.remove(it.id) }
            }
            navigator.playNext()
        }
    }
    // Shortcuts need the page to hold focus; a text field the user used earlier must not keep it after navigating.
    LaunchedEffect(route) { if (route !is Route.Search) runCatching { focus.requestFocus() } }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Vela.colors.background.toColor())
            .focusRequester(focus)
            .focusable()
            // onKeyEvent (not preview): a focused text field takes its own keys first, so typing never triggers a shortcut.
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (e.key == androidx.compose.ui.input.key.Key.Escape || (e.key == androidx.compose.ui.input.key.Key.DirectionLeft && e.isAltPressed)) {
                    if (window?.isFullscreen == true) { window.toggleFullscreen(); return@onKeyEvent true }
                    return@onKeyEvent navigator.back()
                }
                val action = shortcutFor(e.key, e.isShiftPressed)
                if (host == null || action == null || navigator.nowPlaying == null) return@onKeyEvent false
                applyShortcut(action, host.session, host.controllerState.value) { window?.toggleFullscreen() }
                true
            },
    ) {
        val sizeClass = windowSizeClassFor(maxWidth.value, spacing)
        val rail = sizeClass != WindowSizeClass.COMPACT
        val fullscreenVideo = (window?.isFullscreen == true || inPip) && route is Route.Video
        val chrome = !fullscreenVideo
        CompositionLocalProvider(LocalWindowSize provides sizeClass, LocalWindowWidthDp provides maxWidth.value) {
            // One structure for every layout: only the rail, top bar and bottom bar come and go. If the page itself moved
            // between branches, crossing a size-class boundary (rotating, resizing) would rebuild it, losing its state
            // and tearing down the video surface.
            Row(Modifier.fillMaxSize().then(if (chrome) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)) else Modifier)) {
                if (chrome && rail) SideRail(route.topLevel(), onSelect = navigator::selectTab, onSearch = { navigator.push(Route.Search()) })
                Column(Modifier.weight(1f)) {
                    if (chrome && route !is Route.Video && route !is Route.Search && route != Route.Settings && route != Route.Accounts) {
                        VelaTopBar(
                            routeTitle(route), onBack = if (navigator.canGoBack) ({ navigator.back() }) else null,
                            onSearch = if (rail) null else ({ navigator.push(Route.Search()) }),
                            menu = overflowMenu(services, navigator),
                        )
                    }
                    // Keyed by the active account, so switching rebuilds every screen on the new account's data.
                    val activeAccount by services.accounts.active.collectAsState()
                    androidx.compose.runtime.key(activeAccount.id) { Destination(services, navigator, route, Modifier.weight(1f)) }
                    if (chrome) DockedMiniPlayer(services, navigator)
                    if (chrome && !rail) BottomNav(route.topLevel(), onSelect = navigator::selectTab)
                    else if (chrome) Spacer(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
                }
            }
        }
    }
}

private fun routeTitle(route: Route): String = when (route) {
    Route.Home -> "Vela"
    Route.Subscriptions -> "Subscriptions"
    Route.Library -> "Library"
    Route.Settings -> "Settings"
    is Route.Channel -> route.name.ifBlank { "Channel" }
    is Route.Playlist -> "Playlist"
    Route.History -> "History"
    Route.ManageSubscriptions -> "Subscriptions"
    Route.Diagnostics -> "Diagnostics"
    Route.Accounts -> "Accounts"
    Route.Downloads -> "Downloads"
    is Route.Search -> "Search"
    is Route.Video -> ""
}

private fun overflowMenu(services: AppServices, navigator: Navigator): List<MenuAction> = buildList {
    if (services.downloads != null) add(MenuAction("Downloads") { navigator.push(Route.Downloads) })
    add(MenuAction("History") { navigator.push(Route.History) })
    add(MenuAction("Settings") { navigator.selectTab(Route.Settings) })
    add(MenuAction("Diagnostics") { navigator.push(Route.Diagnostics) })
}

@Composable
private fun DockedMiniPlayer(services: AppServices, navigator: Navigator) {
    val playing = navigator.nowPlaying ?: return
    val host = services.player ?: return
    if (navigator.current is Route.Video) return
    val state by host.controllerState.collectAsState()
    MiniPlayer(
        title = playing.title,
        thumbnailUrl = playing.thumbnailUrl,
        playing = state.status == PlaybackStatus.PLAYING,
        progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
        onToggle = { if (state.status == PlaybackStatus.PLAYING) host.session.pause() else host.session.play() },
        onExpand = navigator::expandNowPlaying,
        onClose = { host.session.stop(); navigator.clearNowPlaying() },
    )
}

@Composable
private fun Destination(services: AppServices, navigator: Navigator, route: Route, modifier: Modifier) {
    when (route) {
        Route.Home -> HomeScreen(services, navigator, modifier)
        Route.Subscriptions -> SubscriptionsScreen(services, navigator, modifier)
        Route.Library -> LibraryScreen(services, navigator, modifier)
        Route.Settings -> SettingsHost(services, navigator, modifier)
        is Route.Search -> SearchScreen(services, navigator, route.query, modifier)
        is Route.Video -> VideoScreen(services, navigator, route.video, route.startSeconds, modifier)
        is Route.Channel -> ChannelScreen(services, navigator, route.id, route.name, modifier)
        is Route.Playlist -> PlaylistScreen(services, navigator, route.id, modifier)
        Route.History -> HistoryScreen(services, navigator, modifier)
        Route.ManageSubscriptions -> SubscriptionsScreen(services, navigator, modifier)
        Route.Diagnostics -> DiagnosticsScreen(services.extractor, services.log, services.modules.log, services.downloads, modifier)
        Route.Downloads -> DownloadsScreen(services, navigator, modifier)
        Route.Accounts -> AccountsScreen(services, navigator, modifier)
    }
}
