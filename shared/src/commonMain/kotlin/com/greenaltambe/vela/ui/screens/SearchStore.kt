package com.greenaltambe.vela.ui.screens

import androidx.compose.runtime.staticCompositionLocalOf
import com.greenaltambe.vela.extractor.PagedList
import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.time.Instant

/**
 * The results of the last few searches with where each list was scrolled to, kept for as long as the shell lives. The search page is rebuilt
 * whenever you come back to it (after a video, say); with this it shows the results it had, at the same place, without searching again.
 */
class SearchStore(private val max: Int = 6) {
    class Entry(var videos: PagedList<VideoSummary>, var at: Instant, val scroll: ScrollMemory = ScrollMemory())

    private val entries = LinkedHashMap<String, Entry>()

    fun fresh(query: String, now: Instant): Entry? = entries[query]?.takeIf { it.videos.items.isNotEmpty() && (now - it.at).inWholeMilliseconds < MAX_AGE_MS }

    /** The scroll memory of [query]'s list (made on first ask, so the page can hand it to the list before anything is stored). */
    fun scrollFor(query: String, now: Instant): ScrollMemory = entries.getOrPut(query) { Entry(com.greenaltambe.vela.extractor.videoPagedList(), now) }.scroll

    fun put(query: String, videos: PagedList<VideoSummary>, now: Instant, refreshedAt: Boolean = false) {
        val e = entries.remove(query)?.also { it.videos = videos; if (refreshedAt) it.at = now } ?: Entry(videos, now)
        entries[query] = e
        while (entries.size > max) entries.remove(entries.keys.first())
    }

    companion object { const val MAX_AGE_MS = 30 * 60 * 1000L }
}

val LocalSearchStore = staticCompositionLocalOf<SearchStore?> { null }
