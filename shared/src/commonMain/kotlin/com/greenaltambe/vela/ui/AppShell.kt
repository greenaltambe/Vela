package com.greenaltambe.vela.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
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
 * Bottom navigation on narrow windows, a side rail on wide ones, search always in reach, Back on both platforms, and
 * the mini player docked above the nav while something plays and its page is closed.
 */
@Composable
fun AppShell(services: AppServices, navigator: Navigator = remember { Navigator() }) {
    val route = navigator.current
    val wideAt = LocalLook.current.spacing.wideLayoutMinWidthDp
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
    LaunchedEffect(route) { runCatching { focus.requestFocus() } }

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
        val wide = maxWidth.value >= wideAt
        val fullscreenVideo = window?.isFullscreen == true || inPip
        val body: @Composable (Modifier) -> Unit = { m -> Destination(services, navigator, route, m) }

        // One structure for every layout: only the rail, top bar and bottom bar come and go. If the page itself moved
        // between branches, crossing the wide/narrow breakpoint (or entering fullscreen) would rebuild it, losing its
        // state and tearing down the video surface.
        val chrome = !(fullscreenVideo && route is Route.Video)
        Row(Modifier.fillMaxSize().then(if (chrome) Modifier.safeContentPadding() else Modifier)) {
            if (chrome && wide) SideRail(route.topLevel(), onSelect = navigator::selectTab, onSearch = { navigator.push(Route.Search()) })
            Column(Modifier.weight(1f)) {
                if (chrome) TopBar(navigator)
                body(Modifier.weight(1f))
                if (chrome) DockedMiniPlayer(services, navigator)
                if (chrome && !wide) BottomNav(route.topLevel(), onSelect = navigator::selectTab)
            }
        }
    }
}

@Composable
private fun TopBar(navigator: Navigator) {
    var query by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(horizontal = Vela.md, vertical = Vela.xs), verticalAlignment = Alignment.CenterVertically) {
        if (navigator.canGoBack) Button(onClick = { navigator.back() }, modifier = Modifier.testTag("nav.back")) { Text("Back") }
        OutlinedTextField(
            query, { query = it },
            modifier = Modifier.weight(1f).padding(horizontal = Vela.sm).testTag("nav.searchField"),
            singleLine = true, placeholder = { Text("Search") },
        )
        Button(onClick = { if (query.isNotBlank()) { navigator.push(Route.Search(query.trim())) } }, enabled = query.isNotBlank(), modifier = Modifier.testTag("nav.searchButton")) { Text("Search") }
    }
}

@Composable
private fun DockedMiniPlayer(services: AppServices, navigator: Navigator) {
    val playing = navigator.nowPlaying ?: return
    val host = services.player ?: return
    if (navigator.current is Route.Video) return
    val state by host.controllerState.collectAsState()
    MiniPlayer(
        title = playing.title,
        playing = state.status == PlaybackStatus.PLAYING,
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
    }
}
