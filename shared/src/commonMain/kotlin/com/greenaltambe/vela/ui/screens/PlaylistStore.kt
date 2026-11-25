package com.greenaltambe.vela.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.library.RemotePlaylist
import com.greenaltambe.vela.library.isMix
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.ui.nav.Navigator
import kotlin.time.Instant

/** The rest of a "Play all": the pages of [playlist] still to be put at the end of the queue [Navigator.playAll] made. */
class QueueFill(val playlist: RemotePlaylist, private val queue: Int, private var handled: Int) {
    /**
     * Reads page after page and queues it, for as long as the queue is still the one this belongs to. It can be run again after being cancelled
     * and carries on where it was. Returns what to tell the viewer when it had to stop short, else null.
     */
    suspend fun run(navigator: Navigator, filter: (List<VideoSummary>) -> List<VideoSummary>): String? {
        var pages = 0
        while (true) {
            if (!navigator.isQueue(queue)) return null
            if (playlist.videos.size <= handled) {
                if (!playlist.hasMore || pages++ >= RemotePlaylist.MAX_PAGES) return null
                if (!playlist.loadMore()) return "Only the first ${playlist.videos.size} videos of the playlist are queued."
                continue
            }
            val fresh = playlist.videos.drop(handled)
            val shown = filter(fresh)
            if (!navigator.extendQueue(queue, shown)) return null
            handled += fresh.size
        }
    }
}

/**
 * The last few YouTube playlists that were open, so that coming back to one (from a video, the owner's channel) shows what had been read, at the same
 * place. Held with the page stack, like [ChannelStore]. One account's reading is never shown to another.
 */
class PlaylistStore(private val max: Int = 4) {
    class Entry(val playlist: RemotePlaylist, val at: Instant, val scroll: ScrollMemory = ScrollMemory())

    private val entries = LinkedHashMap<String, Entry>()

    /** The queue still being filled, run by the shell so that it goes on while other pages are open. */
    var fill: QueueFill? by mutableStateOf(null)

    fun entryFor(services: AppServices, playlistId: String, now: Instant): Entry {
        val key = "${services.accounts.active.value.id}:$playlistId"
        val old = entries.remove(key)?.takeIf { (now - it.at).inWholeMilliseconds < MAX_AGE_MS }
        val e = old ?: Entry(RemotePlaylist(playlistId, services.extractor, services.reads), now)
        entries[key] = e
        while (entries.size > max) entries.remove(entries.keys.first())
        return e
    }

    fun clear() = entries.clear()

    /**
     * Plays what [filter] lets through of the videos read so far and keeps queueing the pages that follow. False when it lets none of them through
     * (nothing was started).
     */
    fun playAll(navigator: Navigator, playlist: RemotePlaylist, filter: (List<VideoSummary>) -> List<VideoSummary>): Boolean {
        val read = playlist.videos
        val queue = navigator.playAll(filter(read)) ?: return false
        // A mix has no end to queue.
        fill = if (playlist.hasMore && !isMix(playlist.id)) QueueFill(playlist, queue, read.size) else null
        return true
    }

    companion object { const val MAX_AGE_MS = 30 * 60 * 1000L }
}

val LocalPlaylistStore = staticCompositionLocalOf<PlaylistStore?> { null }

/** The playlist page's filter: the same feed rules and DeArrow titles the list on screen goes through. */
internal fun AppServices.playlistFilter(videos: List<VideoSummary>): List<VideoSummary> = filterFeed(videos, DeArrowSurface.FEED)
