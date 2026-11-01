package com.greenaltambe.vela.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.greenaltambe.vela.extractor.*
import kotlin.time.Instant

/** One tab's loaded items, filters, next-page token, and loading state. */
class ChannelTabFeed {
    var items by mutableStateOf<List<ChannelItem>>(emptyList())
    var shelves by mutableStateOf<List<ChannelShelf>>(emptyList())
    var filters by mutableStateOf<List<ChannelFilterGroup>>(emptyList())
    var selectedFilter by mutableStateOf<ChannelFilterOption?>(null)
    var nextToken by mutableStateOf<String?>(null)
    var state by mutableStateOf<ListState>(ListState.Loading)
    var loaded by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var refreshedNotice by mutableStateOf(false)
    val scroll = ScrollMemory()
}

/** Legacy tab feed for backwards compatibility. */
class TabFeed<T> {
    var items by mutableStateOf<List<T>>(emptyList())
    var next by mutableStateOf<String?>(null)
    var state by mutableStateOf<ListState>(ListState.Loading)
    var loaded by mutableStateOf(false)
    var loading by mutableStateOf(false)
}

/** Everything a channel page shows, so it can be rebuilt exactly as it was: the open tab, each tab's items, sorting and scroll positions. */
class ChannelState(var at: Instant) {
    var landing by mutableStateOf<ChannelLanding?>(null)
    var about by mutableStateOf<ChannelAbout?>(null)
    var selectedTabIndex by mutableStateOf(0)
    var searchQuery by mutableStateOf("")

    // Per-tab feeds keyed by tab kind or url
    private val tabFeeds = HashMap<String, ChannelTabFeed>()
    fun feedFor(tabKey: String): ChannelTabFeed = tabFeeds.getOrPut(tabKey) { ChannelTabFeed() }

    // Legacy fields preserved for compatibility
    var page: ChannelPage?
        get() = landing?.header
        set(value) {
            if (value != null) {
                val current = landing
                landing = current?.copy(header = value) ?: ChannelLanding(header = value)
            }
        }
    val videos = TabFeed<VideoSummary>()
    val shorts = TabFeed<VideoSummary>()
    val live = TabFeed<VideoSummary>()
    val playlists = TabFeed<PlaylistRef>()
    private val scrolls = HashMap<String, ScrollMemory>()
    fun scrollFor(tab: String) = scrolls.getOrPut(tab) { ScrollMemory() }
}

/**
 * The last few channel pages, kept for as long as the shell lives. A channel page is rebuilt when you come back to it (from a playlist, a video);
 * with this it shows the tab you were on with the items it had, at the same place, without loading again.
 */
class ChannelStore(private val max: Int = 6) {
    private val entries = LinkedHashMap<String, ChannelState>()

    fun stateFor(channelId: String, now: Instant): ChannelState {
        val old = entries.remove(channelId)?.takeIf { (now - it.at).inWholeMilliseconds < MAX_AGE_MS }
        val e = old ?: ChannelState(now)
        entries[channelId] = e
        while (entries.size > max) entries.remove(entries.keys.first())
        return e
    }

    companion object { const val MAX_AGE_MS = 30 * 60 * 1000L }
}

val LocalChannelStore = staticCompositionLocalOf<ChannelStore?> { null }
