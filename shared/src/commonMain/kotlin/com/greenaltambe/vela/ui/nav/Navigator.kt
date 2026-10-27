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
    data object Subscriptions : Route
    data object Library : Route
    data object Settings : Route
    data class Search(val query: String = "") : Route
    data class Video(val video: VideoSummary, val startSeconds: Long? = null) : Route
    data class Channel(val id: String, val name: String) : Route
    data class Playlist(val id: Long) : Route
    data object History : Route
    data object ManageSubscriptions : Route
    data object Diagnostics : Route
    data object Downloads : Route
}

val TopLevelRoutes: List<Route> = listOf(Route.Home, Route.Subscriptions, Route.Library, Route.Settings)

/** The part of the stack that top-level destination [route] belongs to. */
fun Route.topLevel(): Route = when (this) {
    Route.Home, is Route.Video, is Route.Search, is Route.Channel -> Route.Home
    Route.Subscriptions, Route.ManageSubscriptions -> Route.Subscriptions
    Route.Library, is Route.Playlist, Route.History, Route.Downloads -> Route.Library
    Route.Settings, Route.Diagnostics -> Route.Settings
}

/** A back stack plus the "what is playing" state the docked mini player needs. */
@Stable
class Navigator(start: Route = Route.Home) {
    private val stack = mutableStateListOf(start)

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** The video the player is on (the mini player shows it when [current] is not its page). */
    var nowPlaying: VideoSummary? by mutableStateOf(null)
        private set

    /** Videos queued after [nowPlaying], from "play all". */
    var queue: List<VideoSummary> by mutableStateOf(emptyList())
        private set

    fun push(route: Route) {
        if (route == current) return
        if (route is Route.Video) nowPlaying = route.video
        stack.add(route)
    }

    /** Switches tab: the stack becomes just that destination. */
    fun selectTab(route: Route) {
        if (stack.size == 1 && current == route) return
        stack.clear()
        stack.add(route)
    }

    /** Pops one screen; false if already at the root of a tab (the platform may then leave the app). */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Replaces the current video page with another (related videos, queue advance) so Back does not walk through them all. */
    fun replaceWithVideo(video: VideoSummary, startSeconds: Long? = null) {
        nowPlaying = video
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

    fun clearNowPlaying() {
        nowPlaying = null
        queue = emptyList()
    }

    /** For the mini player: reopens the page of what is playing. */
    fun expandNowPlaying() {
        nowPlaying?.let { if (current !is Route.Video) push(Route.Video(it)) }
    }
}
