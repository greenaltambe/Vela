package com.greenaltambe.vela.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.greenaltambe.vela.ui.components.navEntries
import com.greenaltambe.vela.ui.nav.NavConfig
import com.greenaltambe.vela.ui.nav.openTab
import com.greenaltambe.vela.core.settings.BarStyle
import com.greenaltambe.vela.ui.screens.ProfileButton
import com.greenaltambe.vela.ui.screens.ProfileMenu
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.core.settings.MiniLook
import com.greenaltambe.vela.ui.components.MiniState
import com.greenaltambe.vela.ui.nav.BottomChrome
import com.greenaltambe.vela.ui.nav.LocalChromeInset
import com.greenaltambe.vela.ui.nav.rememberScrollChrome
import androidx.compose.foundation.background
import com.greenaltambe.vela.ui.look.VelaMotion
import com.greenaltambe.vela.ui.nav.NavAction
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.drawBehind
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
import com.greenaltambe.vela.player.SessionState
import com.greenaltambe.vela.ui.platform.PlatformBackHandler
import com.greenaltambe.vela.ui.player.applyShortcut
import com.greenaltambe.vela.ui.player.shortcutFor
import com.greenaltambe.vela.ui.screens.AccountActivityScreen
import com.greenaltambe.vela.ui.screens.AccountBanner
import com.greenaltambe.vela.ui.screens.AccountsScreen
import com.greenaltambe.vela.ui.screens.HealthScreen
import com.greenaltambe.vela.ui.screens.HealthStartEffect
import com.greenaltambe.vela.ui.screens.SessionCheckEffect
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
    // Every settings change raises an undo toast from here, over whatever page is open.
    val toasts = remember { com.greenaltambe.vela.ui.look.UndoToasts() }
    com.greenaltambe.vela.ui.look.UndoHost(toasts, Modifier.fillMaxSize()) { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        AppShellContent(services, navigator)
        val rev by services.settings.revision.collectAsState()
        val on = remember(rev) { services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.debugFrameStats) }
        // 90 Hz phones: 11.1 ms is the frame budget; the report prints through the platform log.
        com.greenaltambe.vela.ui.debug.FrameStatsOverlay(on, budgetMs = 11.1, onReport = { println("VELA-FRAMES $it") }, modifier = Modifier.align(Alignment.TopEnd))
    } }
}

@Composable
private fun AppShellContent(services: AppServices, navigator: Navigator) {
    val route = navigator.current
    val spacing = LocalLook.current.spacing
    val window = LocalWindowControls.current
    val inPip = LocalInPictureInPicture.current
    val focus = remember { FocusRequester() }
    val host = services.player
    val playerState = host?.controllerState?.collectAsState()?.value

    PlatformBackHandler(enabled = navigator.canGoBack) { navigator.back() }

    // The mini player survives the process being killed: remember what it shows, and bring it back (idle) on the next start.
    var restored by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) {
        com.greenaltambe.vela.ui.nav.NowPlayingSnapshot.decodeAll(services.settings.resolve(com.greenaltambe.vela.core.settings.Settings.restoreNowPlaying))?.let { (video, picks) ->
            navigator.restore(video); host?.session?.restoreOverrides(video.id, picks)
        }
        restored = true
    }
    val picks = host?.session?.overrides?.collectAsState()?.value
    LaunchedEffect(navigator.nowPlaying, restored, picks) {
        if (!restored) return@LaunchedEffect
        runCatching { services.settings.set(com.greenaltambe.vela.core.settings.Settings.restoreNowPlaying, com.greenaltambe.vela.ui.nav.NowPlayingSnapshot.encode(navigator.nowPlaying, picks)) }
    }

    // "Open on": the tab chosen in the settings, once, when the app starts at its root.
    LaunchedEffect(Unit) {
        val openOn = NavConfig.of(services.settings).openOn
        if (!navigator.canGoBack && navigator.current == Route.Home && openOn != com.greenaltambe.vela.core.settings.NavTab.HOME) navigator.openTab(openOn)
    }

    // Switching accounts: every observer that is not a plain read of the active repositories is told here (downloads list and schedule).
    LaunchedEffect(services.accounts.active.collectAsState().value.id) { services.downloads?.manager?.reload() }

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

    // A faint glow of the palette's first and last colours in the top corners, so the whole app wears the palette without any surface being tinted.
    val wake = Vela.look.accent.wake.map { it.toColor() }
    val glowAlpha = if (Vela.look.colors.background.toColor().luminance() < 0.5f) 0.16f else 0.10f
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Vela.colors.background.toColor())
            .drawBehind {
                val radius = size.maxDimension * 0.7f
                drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(wake.first().copy(alpha = glowAlpha), androidx.compose.ui.graphics.Color.Transparent), center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = radius))
                drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(wake.last().copy(alpha = glowAlpha), androidx.compose.ui.graphics.Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width, 0f), radius = radius))
            }
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
        val revision by services.settings.revision.collectAsState()
        val nav = remember(revision) { NavConfig.of(services.settings) }
        var profileOpen by remember { mutableStateOf(false) }
        val sizeClass = windowSizeClassFor(maxWidth.value, spacing)
        val rail = sizeClass != WindowSizeClass.COMPACT
        val fullscreenVideo = (window?.isFullscreen == true || inPip) && route is Route.Video
        val chrome = !fullscreenVideo
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        val lifecycleState by lifecycle.currentStateFlow.collectAsState()
        CompositionLocalProvider(
            LocalWindowSize provides sizeClass, LocalWindowWidthDp provides maxWidth.value,
            // Looping animations (shimmer) stop while the app is not on screen.
            com.greenaltambe.vela.ui.look.LocalAnimationsActive provides lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED),
        ) {
            // One structure for every layout: only the rail, top bar and bottom bar come and go. If the page itself moved
            // between branches, crossing a size-class boundary (rotating, resizing) would rebuild it, losing its state
            // and tearing down the video surface.
            Row(Modifier.fillMaxSize().then(if (chrome) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)) else Modifier)) {
                if (chrome && rail) SideRail(
                    navEntries(nav.tabs), nav.selected(route), nav.railCollapsed,
                    onToggleCollapsed = { services.settings.set(com.greenaltambe.vela.core.settings.LookSettings.railCollapsed, !nav.railCollapsed) },
                    onSelect = navigator::openTab,
                )
                Column(Modifier.weight(1f)) {
                    if (chrome && route !is Route.Video && route !is Route.Search && route != Route.Settings && route != Route.Accounts && route != Route.AccountActivity && route != Route.Health && route != Route.Backup && route != Route.Customize && route !is Route.CustomizePage && route !is Route.SettingsPage) {
                        VelaTopBar(
                            routeTitle(route), onBack = if (navigator.canGoBack) ({ navigator.back() }) else null,
                            onSearch = ({ navigator.push(Route.Search()) }),
                            profile = { ProfileButton(services) { profileOpen = true } },
                        )
                    }
                    if (profileOpen) ProfileMenu(services, navigator) { profileOpen = false }
                    if (chrome) AccountBanner(services, navigator)
                    if (chrome) com.greenaltambe.vela.ui.screens.HealthBanner(services, navigator)
                    if (chrome) com.greenaltambe.vela.ui.screens.CaptchaBanner(services)
                    SessionCheckEffect(services)
                    HealthStartEffect(services)
                    val mini = if (chrome) rememberMiniState(services, navigator) else null
                    val scroll = rememberScrollChrome()
                    LaunchedEffect(route) { scroll.reset() }
                    val barOverlays = chrome && !rail && nav.barStyle != BarStyle.DOCKED
                    var chromeHeightPx by remember { mutableIntStateOf(0) }
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    // The page leaves room for a bar that floats over it: its full (unshrunk) height, so a shrinking bar never reflows the list.
                    val inset = if (barOverlays) with(density) { chromeHeightPx.toDp() } else 0.dp
                    // Keyed by the active account, so switching rebuilds every screen on the new account's data.
                    val activeAccount by services.accounts.active.collectAsState()
                    Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(scroll.connection)) {
                        CompositionLocalProvider(LocalChromeInset provides inset) {
                            androidx.compose.runtime.key(activeAccount.id) { AnimatedDestination(services, navigator, route, Modifier.fillMaxSize()) }
                        }
                        if (barOverlays) BottomChrome(
                            nav, navEntries(nav.tabs), nav.selected(route), scroll, mini, navigator::openTab, Modifier.align(Alignment.BottomCenter),
                            onHeight = { if (!(nav.shrinkOnScroll && scroll.shrunk)) chromeHeightPx = it },
                        )
                    }
                    if (chrome && !rail && !barOverlays) BottomChrome(nav, navEntries(nav.tabs), nav.selected(route), scroll, mini, navigator::openTab)
                    else if (chrome && rail) {
                        // Wide windows: the rail replaces the bar; the mini player sits at the bottom of the page with the same look settings.
                        if (mini != null) MiniPlayer(mini, nav.miniLook, Vela.largeShape, (if (nav.miniLook == MiniLook.PIP) Modifier.align(Alignment.End) else Modifier.fillMaxWidth()).padding(Vela.md))
                        Spacer(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
                    }
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
    Route.Customize -> "Customize Vela"
    is Route.CustomizePage -> "Customize"
    is Route.SettingsPage -> "Settings"
    is Route.Channel -> route.name.ifBlank { "Channel" }
    is Route.Playlist -> "Playlist"
    Route.History -> "History"
    Route.ManageSubscriptions -> "Subscriptions"
    Route.Diagnostics -> "Diagnostics"
    Route.Accounts -> "Accounts"
    Route.AccountActivity -> "Account activity"
    Route.Health -> "Health"
    Route.Backup -> "Backup and restore"
    Route.Downloads -> "Downloads"
    Route.DownloadRules -> "Automatic downloads"
    is Route.Search -> "Search"
    is Route.Video -> ""
}

/** What the mini player shows, or null when nothing plays or the player's own page is open. */
@Composable
private fun rememberMiniState(services: AppServices, navigator: Navigator): MiniState? {
    val playing = navigator.nowPlaying ?: return null
    val host = services.player ?: return null
    if (navigator.current is Route.Video) return null
    val state by host.controllerState.collectAsState()
    return MiniState(
        title = playing.title,
        thumbnailUrl = playing.thumbnailUrl,
        playing = state.status == PlaybackStatus.PLAYING,
        progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
        onToggle = {
            when {
                state.status == PlaybackStatus.PLAYING -> host.session.pause()
                // After a restart the mini player is restored but nothing is loaded: start it (it resumes from the saved position).
                host.session.state.value is SessionState.Idle -> host.session.start(playing.id, playing)
                else -> host.session.play()
            }
        },
        onExpand = navigator::expandNowPlaying,
        onClose = { host.session.stop(); navigator.clearNowPlaying() },
    )
}

/**
 * The page, with the app's navigation motion (all numbers from the Motion tokens): tabs crossfade with a slight slide towards the new
 * tab, a pushed page slides in over the old one, a pop slides it back out. Moving between two videos is not animated (the same page
 * just shows the next one, as before), and nothing here animates when motion is reduced beyond a short crossfade.
 */
@Composable
private fun AnimatedDestination(services: AppServices, navigator: Navigator, route: Route, modifier: Modifier) {
    val m = Vela.motion
    val action = navigator.lastAction
    androidx.compose.animation.AnimatedContent(
        targetState = route,
        modifier = modifier,
        transitionSpec = {
            val fade = VelaMotion.tween<Float>(m, VelaMotion.Level.STANDARD)
            val tabs = TopLevelRoutes
            val toward = if (m.reduced) 0 else when (action) {
                NavAction.TAB -> if (tabs.indexOf(targetState.topLevel()) >= tabs.indexOf(initialState.topLevel())) 1 else -1
                NavAction.PUSH -> 1
                NavAction.POP -> -1
                NavAction.REPLACE -> 0
            }
            val slide = if (action == NavAction.TAB) 14 else 5
            (fadeIn(fade) + slideInHorizontally(VelaMotion.tween(m, VelaMotion.Level.STANDARD)) { toward * it / slide })
                .togetherWith(fadeOut(VelaMotion.tween(m, VelaMotion.Level.QUICK)) + slideOutHorizontally(VelaMotion.tween(m, VelaMotion.Level.STANDARD)) { -toward * it / (slide * 2) })
        },
        // Two Video routes are one page: the player surface must not be composed twice.
        contentKey = { if (it is Route.Video) "video" else it },
        label = "destination",
    ) { target -> Destination(services, navigator, target, Modifier.fillMaxSize()) }
}

@Composable
private fun Destination(services: AppServices, navigator: Navigator, route: Route, modifier: Modifier) {
    when (route) {
        Route.Home -> HomeScreen(services, navigator, modifier)
        Route.Subscriptions -> SubscriptionsScreen(services, navigator, modifier)
        Route.Library -> LibraryScreen(services, navigator, modifier)
        Route.Settings -> SettingsHost(services, navigator, modifier)
        Route.Customize -> com.greenaltambe.vela.ui.screens.CustomizeScreen(services, navigator, modifier)
        is Route.CustomizePage -> com.greenaltambe.vela.ui.screens.CustomizePageScreen(services, navigator, route.page, modifier)
        is Route.SettingsPage -> com.greenaltambe.vela.ui.screens.SettingsPageScreen(services, navigator, route.pageId, route.highlight, modifier)
        is Route.Search -> SearchScreen(services, navigator, route.query, modifier)
        is Route.Video -> VideoScreen(services, navigator, route.video, route.startSeconds, modifier)
        is Route.Channel -> ChannelScreen(services, navigator, route.id, route.name, modifier)
        is Route.Playlist -> PlaylistScreen(services, navigator, route.id, modifier)
        Route.History -> HistoryScreen(services, navigator, modifier)
        Route.ManageSubscriptions -> SubscriptionsScreen(services, navigator, modifier)
        Route.Diagnostics -> DiagnosticsScreen(services.extractor, services.log, services.modules.log, services.downloads, modifier, faults = services.faults, exportLog = services.exportLog, rebuildCaches = services.rebuildCaches)
        Route.Downloads -> DownloadsScreen(services, navigator, modifier)
        Route.DownloadRules -> com.greenaltambe.vela.ui.screens.DownloadRulesScreen(services, navigator, modifier)
        Route.Accounts -> AccountsScreen(services, navigator, modifier)
        Route.AccountActivity -> AccountActivityScreen(services, navigator, modifier)
        Route.Health -> HealthScreen(services, navigator, modifier)
        Route.Backup -> com.greenaltambe.vela.ui.screens.BackupScreen(services, navigator, modifier)
    }
}
