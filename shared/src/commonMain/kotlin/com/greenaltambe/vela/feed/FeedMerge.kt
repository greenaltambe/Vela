package com.greenaltambe.vela.feed

import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.time.Instant

/** One video in the subscription feed. [publishedAt] orders the feed; the RSS source gives it, the channel-tab source may not. */
data class FeedEntry(val video: VideoSummary, val publishedAt: Instant?, val fetchedAt: Instant)

/**
 * Combines what the cache holds with a refresh. A refreshed entry replaces the cached one for the same video (fresher
 * title / view count) but keeps a known [FeedEntry.publishedAt] when the new source had none. Entries of channels
 * that failed to refresh stay as they were. Newest first; entries with no date go after dated ones, in fetch order.
 */
fun mergeFeed(cached: List<FeedEntry>, refreshed: List<FeedEntry>, limit: Int = 500): List<FeedEntry> {
    val byId = LinkedHashMap<String, FeedEntry>()
    cached.forEach { byId[it.video.id] = it }
    refreshed.forEach { fresh ->
        val old = byId[fresh.video.id]
        byId[fresh.video.id] = if (old != null && fresh.publishedAt == null) fresh.copy(publishedAt = old.publishedAt) else fresh
    }
    return byId.values
        .sortedWith(compareByDescending<FeedEntry> { it.publishedAt != null }.thenByDescending { it.publishedAt }.thenByDescending { it.fetchedAt })
        .take(limit)
}
