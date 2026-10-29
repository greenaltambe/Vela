package com.greenaltambe.vela.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.greenaltambe.vela.extractor.VideoSummary

/** Every place the app can show. Top-level destinations are the four in the bottom bar / side rail. */
sealed interface Route {
    data object Home : Route
    data object Shorts : Route
    data object Subscriptions : Route
    data object Library : Route
    data object Settings : Route
    data class Search(val query: String = "") : Route
    /** [asVideoPage]: show a Short on the ordinary video page too (the Shorts player's "Open"), instead of in the Shorts player. */
    data class Video(val video: VideoSummary, val startSeconds: Long? = null, val asVideoPage: Boolean = false) : Route
    /** A Short opened from a card elsewhere: the Shorts player, starting on it. */
    data class ShortsPlayer(val video: VideoSummary) : Route
    /** [viaVideoId]: when the id is not known (a listing that carries none), the channel is found from this video once the page is open, behind a loader. */
    /** A YouTube playlist from a channel's Playlists tab (not one of the app's own, which are [Playlist]). */
    data class ChannelPlaylist(val id: String, val title: String) : Route
    data class Channel(val id: String, val name: String, val viaVideoId: String = "") : Route
    data class Playlist(val id: Long) : Route
    data object History : Route
    data object ManageSubscriptions : Route
    data object Diagnostics : Route
    data object Accounts : Route
    data object AccountActivity : Route
    data object Health : Route
    data object Backup : Route
    data object DownloadRules : Route
    data object Downloads : Route
    data object Customize : Route
    data class SettingsPage(val pageId: String, val highlight: String? = null) : Route
    data class CustomizePage(val page: String) : Route
}

/** Pages that can be tabs (Settings never is: it lives in the profile menu). Used for the direction of the tab transition. */
val TopLevelRoutes: List<Route> = listOf(Route.Home, Route.Shorts, Route.Subscriptions, Route.Library, Route.Downloads)

/** The part of the stack that top-level destination [route] belongs to. */
fun Route.topLevel(): Route = when (this) {
    Route.Home, is Route.Video, is Route.ShortsPlayer, is Route.Search, is Route.Channel, is Route.ChannelPlaylist -> Route.Home
    Route.Shorts -> Route.Shorts
    Route.Subscriptions, Route.ManageSubscriptions -> Route.Subscriptions
    Route.Library, is Route.Playlist, Route.History, Route.Downloads, Route.DownloadRules -> Route.Library
    Route.Settings, Route.Diagnostics, Route.Accounts, Route.AccountActivity, Route.Health, Route.Backup, Route.Customize, is Route.CustomizePage, is Route.SettingsPage -> Route.Settings
}

/** What the last navigation did, so the shell can animate it: tabs crossfade and slide a little, pushes slide in, pops slide out. */
enum class NavAction { TAB, PUSH, POP, REPLACE }

/** A back stack plus the "what is playing" state the docked mini player needs. */
@Stable
class Navigator(start: Route = Route.Home) {
    private val stack = mutableStateListOf(start)

    var lastAction: NavAction by mutableStateOf(NavAction.TAB)
        private set

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** The video the player is on (the mini player shows it when [current] is not its page). */
    var nowPlaying: VideoSummary? by mutableStateOf(null)
        private set

    /** Videos queued after [nowPlaying], from "play all". */
    var queue: List<VideoSummary> by mutableStateOf(emptyList())
        private set

    /** Swaps the current screen for [route] (a search view re-searching), so Back does not step through every query. */
    fun replace(route: Route) {
        if (route is Route.Video) nowPlaying = route.video
        lastAction = NavAction.REPLACE
        stack[stack.lastIndex] = route
    }

    /** Set by the shell from the Shorts setting: while true, a Short that would open on the video page opens in the Shorts player instead. */
    var shortsInPlayer: Boolean = false

    fun push(route: Route) {
        if (route is Route.Video && route.video.isShort && shortsInPlayer && !route.asVideoPage) return push(Route.ShortsPlayer(route.video))
        if (route == current) return
        if (route is Route.Video) nowPlaying = route.video
        lastAction = NavAction.PUSH
        stack.add(route)
    }

    /** Switches tab: the stack becomes just that destination. */
    fun selectTab(route: Route) {
        if (stack.size == 1 && current == route) return
        lastAction = NavAction.TAB
        stack.clear()
        stack.add(route)
    }

    /** Pops one screen; false if already at the root of a tab (the platform may then leave the app). */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        lastAction = NavAction.POP
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Replaces the current video page with another (related videos, queue advance) so Back does not walk through them all. */
    fun replaceWithVideo(video: VideoSummary, startSeconds: Long? = null) {
        nowPlaying = video
        lastAction = if (current is Route.Video) NavAction.REPLACE else NavAction.PUSH
        if (current is Route.Video) stack[stack.lastIndex] = Route.Video(video, startSeconds) else stack.add(Route.Video(video, startSeconds))
    }

    fun playAll(videos: List<VideoSummary>) {
        val first = videos.firstOrNull() ?: return
        queue = videos.drop(1)
        replaceWithVideo(first)
    }

    /** Next in the queue, or null. Advances the page to it. */
    fun playNext(): VideoSummary? {
        val next = queue.firstOrNull() ?: return null
        queue = queue.drop(1)
        replaceWithVideo(next)
        return next
    }

    /** Puts a video back in the mini player after a restart; it does nothing if something is already there. The player stays idle until the viewer presses play. */
    fun restore(video: VideoSummary) { if (nowPlaying == null) nowPlaying = video }

    fun clearNowPlaying() {
        nowPlaying = null
        queue = emptyList()
    }

    /** For the mini player: reopens the page of what is playing. */
    fun expandNowPlaying() {
        nowPlaying?.let { if (current !is Route.Video) push(Route.Video(it)) }
    }
}


/** The mini player's video as one settings value (tab-separated), so it survives the process being killed. */
object NowPlayingSnapshot {
    private fun clean(s: String?) = s.orEmpty().replace('\t', ' ').replace('\n', ' ')
    /** [overrides]: the viewer's speed / quality picks, kept as two more fields so they survive the process being killed. */
    fun encode(v: VideoSummary?, overrides: com.greenaltambe.vela.player.PlaybackOverrides? = null): String =
        if (v == null) "" else listOf(v.id, v.title, v.channelId, v.channelName, v.thumbnailUrl, overrides?.speed?.toString(), overrides?.maxHeight?.toString()).joinToString("\t") { clean(it) }
    fun decode(raw: String): VideoSummary? = decodeAll(raw)?.first
    fun decodeAll(raw: String): Pair<VideoSummary, com.greenaltambe.vela.player.PlaybackOverrides>? {
        val p = raw.split('\t'); if (p.size < 5 || p[0].isBlank()) return null
        return VideoSummary(p[0], p[1], p[2], p[3], p[4].ifBlank { null }) to
            com.greenaltambe.vela.player.PlaybackOverrides(p.getOrNull(5)?.toFloatOrNull(), p.getOrNull(6)?.toIntOrNull())
    }
}
