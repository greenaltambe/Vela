package com.greenaltambe.vela.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
    data object Explore : Route
    data object Settings : Route
    data class Search(val query: String = "") : Route
    /** [asVideoPage]: show a Short on the ordinary video page too (the Shorts player's "Open"), instead of in the Shorts player. */
    data class Video(val video: VideoSummary, val startSeconds: Long? = null, val asVideoPage: Boolean = false) : Route
    /** A Short opened from a card elsewhere: the Shorts player, starting on it. */
    /**
     * [following]: the Shorts that come after [video] in the list it was opened from (a channel's Shorts tab, a row), played in that order next.
     * [stayOnChannel]: after those, carry on with the rest of the same channel's Shorts before mixing in other channels.
     */
    data class ShortsPlayer(val video: VideoSummary, val following: List<VideoSummary> = emptyList(), val stayOnChannel: Boolean = false) : Route
    /** [viaVideoId]: when the id is not known (a listing that carries none), the channel is found from this video once the page is open, behind a loader. */
    /** A YouTube playlist from a channel's Playlists tab (not one of the app's own, which are [Playlist]). */
    data class ChannelPlaylist(val id: String, val title: String) : Route
    data class Channel(val id: String, val name: String, val viaVideoId: String = "") : Route
    data class Playlist(val id: Long) : Route
    data object History : Route
    data object Notifications : Route
    /** The whole list of one Home shelf ([shelfId] is a `HomeShelf` name), opened from its "See all". */
    data class HomeShelf(val shelfId: String) : Route
    data object ManageSubscriptions : Route
    data object Diagnostics : Route
    data object Accounts : Route
    data object AccountActivity : Route
    data object Health : Route
    data object Backup : Route
    data object DownloadRules : Route
    data object Blocklist : Route
    data object Downloads : Route
    data object Customize : Route
    data class SettingsPage(val pageId: String, val highlight: String? = null) : Route
    data class CustomizePage(val page: String) : Route
}

/** Pages that can be tabs (Settings never is: it lives in the profile menu). Used for the direction of the tab transition. */
val TopLevelRoutes: List<Route> = listOf(Route.Home, Route.Explore, Route.Shorts, Route.Subscriptions, Route.Library, Route.Downloads)

/** The part of the stack that top-level destination [route] belongs to. */
fun Route.topLevel(): Route = when (this) {
    Route.Home, is Route.Video, is Route.ShortsPlayer, is Route.Search, is Route.Channel, is Route.ChannelPlaylist, is Route.HomeShelf, Route.Notifications -> Route.Home
    Route.Shorts -> Route.Shorts
    Route.Explore -> Route.Explore
    Route.Subscriptions, Route.ManageSubscriptions -> Route.Subscriptions
    Route.Library, is Route.Playlist, Route.History, Route.Downloads, Route.DownloadRules -> Route.Library
    Route.Settings, Route.Blocklist, Route.Diagnostics, Route.Accounts, Route.AccountActivity, Route.Health, Route.Backup, Route.Customize, is Route.CustomizePage, is Route.SettingsPage -> Route.Settings
}

/** What the last navigation did, so the shell can animate it: tabs crossfade and slide a little, pushes slide in, pops slide out. */
enum class NavAction { TAB, PUSH, POP, REPLACE }

/** A back stack plus the "what is playing" state the docked mini player needs. */
@Stable
class Navigator(start: Route = Route.Home) {
    private val stack = mutableStateListOf(start)

    /** An open asked for before the sheet existed (before the first composition: a link at start-up): it opens as soon as the sheet is attached. */
    private var pendingOpen: Pair<VideoSummary, Long?>? = null
    /** The page was asked for and left again before the sheet existed: the video goes to the mini player, idle. */
    private var pendingMini: VideoSummary? = null
    var playerSheetState: com.greenaltambe.vela.ui.player.PlayerSheetState? = null
        set(value) {
            field = value
            val pending = pendingOpen
            if (value != null && pending != null) { pendingOpen = null; value.open(pending.first, pending.second) }
            else if (value != null) pendingMini?.let { pendingMini = null; value.restore(it) }
        }
    private fun openInSheet(video: VideoSummary, start: Long?) {
        val sheet = playerSheetState
        if (sheet != null) sheet.open(video, start) else pendingOpen = video to start
    }

    /** Set when the video page opens from the mini player: the page then grows out of the mini player's picture (read once by the page). */
    var growFromMini = false
    /** Set when the video page is minimized back into the mini player, so the layer exit transition does not animate. */
    var shrinkToMini by mutableStateOf(false)
    /** Where the mini player's picture last was (window coordinates): the page's picture grows from and shrinks back to it. */
    var miniPicture by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    /** While a finger drags the mini player up: how far the page has grown out of it (0..1); -1 when nobody is dragging. The page follows it. */
    var expandDrive by mutableFloatStateOf(-1f)
        private set

    /** The mini player is being dragged up to [progress] (0..1): the first call opens the page in its pulled-down state, the next ones grow it. */
    fun driveExpand(progress: Float) {
        playerSheetState?.let {
            it.expand()
            return
        }
        if (expandDrive < 0f) {
            val playing = nowPlaying ?: return
            if (current is Route.Video) return
            growFromMini = true; expandDrive = progress; push(Route.Video(playing))
        } else expandDrive = progress
    }

    /** The drag ended: [open] keeps the page, otherwise it goes back to the mini player. */
    fun finishExpand(open: Boolean) {
        if (expandDrive < 0f) return
        expandDrive = -1f
        if (!open) back()
    }
    var lastAction: NavAction by mutableStateOf(NavAction.TAB)
        private set

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /**
     * The screen the app shows UNDER the video page: [current] itself, or, while the video page is on top, the route beneath it. The shell keeps this
     * screen composed the whole time, so closing the video reveals it as it was instead of rebuilding it.
     */
    /** Whether the screen under the video page has somewhere to go Back to (its own top bar shows Back or the app mark). */
    val baseCanGoBack: Boolean get() = if (current is Route.Video) stack.dropLast(1).size > 1 else canGoBack

    val baseRoute: Route get() = if (current is Route.Video) stack.dropLast(1).lastOrNull { it !is Route.Video } ?: Route.Home else current

    /** The video the player is on (the mini player shows it when [current] is not its page). */
    var nowPlaying: VideoSummary? by mutableStateOf(null)
        internal set

    fun setNowPlayingDirect(v: VideoSummary) {
        remember(v)
        nowPlaying = v
    }

    /** Videos queued after [nowPlaying], from "play all". */
    var queue: List<VideoSummary> by mutableStateOf(emptyList())
        private set

    /** Videos played before [nowPlaying], most recent last, for the player's Previous button. */
    var played: List<VideoSummary> by mutableStateOf(emptyList())
        private set

    /** Play this video again when it ends (the player's "Repeat" switch). It belongs to the video it was set on: another video starts without it. */
    var repeatOne: Boolean by mutableStateOf(false)

    private fun remember(next: VideoSummary) {
        val now = nowPlaying
        if (now != null && now.id != next.id) { played = (played + now).takeLast(30); repeatOne = false }
    }

    /** Swaps the current screen for [route] (a search view re-searching), so Back does not step through every query. */
    fun replace(route: Route) {
        if (route is Route.Video) {
            remember(route.video)
            nowPlaying = route.video
            openInSheet(route.video, route.startSeconds)
            return
        }
        lastAction = NavAction.REPLACE
        stack[stack.lastIndex] = route
    }

    /** Set by the shell from the Shorts setting: while true, a Short that would open on the video page opens in the Shorts player instead. */
    var shortsInPlayer: Boolean = false

    fun push(route: Route) {
        if (route is Route.Video && route.video.isShort && shortsInPlayer && !route.asVideoPage) return push(Route.ShortsPlayer(route.video))
        if (route is Route.Video) {
            remember(route.video)
            nowPlaying = route.video
            openInSheet(route.video, route.startSeconds)
            return
        }
        if (route == current) return
        playerSheetState?.let { sheet ->
            if (sheet.isExpanded || sheet.progress > 0.5f) {
                sheet.collapse()
            }
        }
        lastAction = NavAction.PUSH
        stack.add(route)
    }

    /** Counts taps on the tab that is already open: the list on screen scrolls back to its top. */
    var tabReselected by mutableIntStateOf(0)
        private set

    /** Switches tab: the stack becomes just that destination. Choosing the tab you are on is a request to go back to its top. */
    fun selectTab(route: Route) {
        if (stack.size == 1 && current == route) { tabReselected++; return }
        playerSheetState?.let { sheet ->
            if (sheet.isExpanded || sheet.progress > 0.5f) {
                sheet.collapse()
            }
        }
        lastAction = NavAction.TAB
        stack.clear()
        stack.add(route)
    }

    /** Pops one screen; false if already at the root of a tab (the platform may then leave the app). */
    fun back(): Boolean {
        pendingOpen?.let { pendingOpen = null; pendingMini = it.first; return true }
        if (stack.size <= 1) return false
        lastAction = NavAction.POP
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Replaces the current video page with another (related videos, queue advance) so Back does not walk through them all. */
    fun replaceWithVideo(video: VideoSummary, startSeconds: Long? = null) {
        remember(video)
        nowPlaying = video
        openInSheet(video, startSeconds)
    }

    fun playAll(videos: List<VideoSummary>) {
        val first = videos.firstOrNull() ?: return
        queue = videos.drop(1)
        replaceWithVideo(first)
    }

    /** What [addToQueue] / [playNextUp] did: queued behind the current video, started at once (nothing was playing), or refused (it is the one playing). */
    enum class QueueResult { QUEUED, STARTED, ALREADY_PLAYING }

    /** Adds [video] to the end of the queue (moving it there if it was queued already). With nothing playing it starts playing instead. */
    fun addToQueue(video: VideoSummary): QueueResult = enqueue(video, atFront = false)

    /** Puts [video] first in the queue, so it plays right after the current video. With nothing playing it starts playing instead. */
    fun playNextUp(video: VideoSummary): QueueResult = enqueue(video, atFront = true)

    private fun enqueue(video: VideoSummary, atFront: Boolean): QueueResult {
        val now = nowPlaying
        if (now == null) { replaceWithVideo(video); return QueueResult.STARTED }
        if (now.id == video.id) return QueueResult.ALREADY_PLAYING
        val rest = queue.filter { it.id != video.id }
        queue = if (atFront) listOf(video) + rest else rest + video
        return QueueResult.QUEUED
    }

    fun removeFromQueue(id: String) { queue = queue.filter { it.id != id } }

    fun clearQueue() { queue = emptyList() }

    /** Puts the queue in a random order ([random] is a parameter so a test can fix it). */
    fun shuffleQueue(random: kotlin.random.Random = kotlin.random.Random) { if (queue.size > 1) queue = queue.shuffled(random) }

    /** Moves the queued video at [from] so it sits at [to] (both are queue positions); anything out of range does nothing. */
    fun moveInQueue(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val list = queue.toMutableList()
        list.add(to, list.removeAt(from))
        queue = list
    }

    /** Puts the queue back as it was (the Undo of a removal or of Clear). */
    fun restoreQueue(previous: List<VideoSummary>) { queue = previous }

    /** Plays the queued video at [index] now; the ones before it are skipped, the ones after it stay queued. */
    fun playFromQueue(index: Int): VideoSummary? {
        val v = queue.getOrNull(index) ?: return null
        queue = queue.drop(index + 1)
        replaceWithVideo(v)
        return v
    }

    /** Next in the queue, or null. Advances the page to it. */
    fun playNext(): VideoSummary? {
        val next = queue.firstOrNull() ?: return null
        queue = queue.drop(1)
        replaceWithVideo(next)
        return next
    }

    /** The video played before this one, or null. Goes back to it without adding the current one to the history again. */
    fun playPrevious(): VideoSummary? {
        val prev = played.lastOrNull() ?: return null
        played = played.dropLast(1)
        nowPlaying = prev
        playerSheetState?.open(prev) ?: run {
            lastAction = if (current is Route.Video) NavAction.REPLACE else NavAction.PUSH
            if (current is Route.Video) stack[stack.lastIndex] = Route.Video(prev) else stack.add(Route.Video(prev))
        }
        return prev
    }

    /** Puts a video back in the mini player after a restart; it does nothing if something is already there. The player stays idle until the viewer presses play. */
    fun restore(video: VideoSummary) {
        if (nowPlaying == null) {
            nowPlaying = video
            playerSheetState?.restore(video)
        }
    }

    fun clearNowPlaying() {
        nowPlaying = null
        queue = emptyList()
        played = emptyList()
    }

    /** Opens what a YouTube address points at (a video, a playlist, a channel); false when [text] holds no such address. */
    fun openYoutubeLink(text: String): Boolean {
        val link = com.greenaltambe.vela.library.YoutubeLinks.find(text) ?: return false
        when (link) {
            // Only the id is known: the page loads the title and the rest itself.
            is com.greenaltambe.vela.library.YoutubeLink.Video -> push(Route.Video(com.greenaltambe.vela.extractor.VideoSummary(link.id, "", "", ""), link.startSeconds))
            is com.greenaltambe.vela.library.YoutubeLink.Playlist -> push(Route.ChannelPlaylist(link.id, "Playlist"))
            is com.greenaltambe.vela.library.YoutubeLink.Channel -> push(Route.Channel(link.id, ""))
        }
        return true
    }

    /** For the mini player: reopens the page of what is playing. */
    fun expandNowPlaying() {
        playerSheetState?.let {
            it.expand()
            return
        }
        nowPlaying?.let { if (current !is Route.Video) { growFromMini = true; push(Route.Video(it)) } }
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

/** The app's one [Navigator], for places that only need to hand it a video (a card's menu adding to the queue). Null outside the app shell (previews, tests). */
val LocalNavigator = androidx.compose.runtime.staticCompositionLocalOf<Navigator?> { null }
