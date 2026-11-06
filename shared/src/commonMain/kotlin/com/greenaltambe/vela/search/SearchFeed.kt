package com.greenaltambe.vela.search

import com.greenaltambe.vela.extractor.ChannelRef
import com.greenaltambe.vela.extractor.VideoSummary

/** Where a block of Shorts goes in the All page: after [afterVideos] videos, showing the Shorts from [from] up to (not including) [to]. */
data class ShortsSlot(val afterVideos: Int, val from: Int, val to: Int)

/**
 * The Shorts blocks of the All page: the first after [first] videos, then one every [every] videos, each showing [perShelf] Shorts that no earlier block showed,
 * for as long as there are Shorts and videos to put them between. A block is only placed after a video that exists, so none ends the list.
 */
fun planShortsSlots(videoCount: Int, shortsCount: Int, first: Int = 3, every: Int = 8, perShelf: Int = 8): List<ShortsSlot> {
    val out = ArrayList<ShortsSlot>()
    var at = first
    var used = 0
    while (used < shortsCount && at < videoCount) {
        val to = minOf(used + perShelf, shortsCount)
        out += ShortsSlot(at, used, to)
        used = to
        at += every
    }
    return out
}

private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/**
 * The channel the query is about, or null. Strict on purpose: a wrong card is worse than none. The name must equal the query once spaces, case and
 * punctuation are ignored ("tech linked" and "TechLinked"), or one must contain the other with at least 5 characters and the shorter being 70% of the longer.
 */
fun topChannel(query: String, channels: List<ChannelRef>): ChannelRef? {
    val q = normalize(query)
    if (q.length < 3) return null
    return channels.firstOrNull { c ->
        val n = normalize(c.name)
        n.isNotEmpty() && (n == q || (minOf(n.length, q.length) >= 5 && (n.contains(q) || q.contains(n)) && minOf(n.length, q.length) * 10 >= maxOf(n.length, q.length) * 7))
    }
}

/** The channel's own videos among the results, for the "From <channel>" row; null when fewer than [min] are there. */
fun latestFrom(channel: ChannelRef, videos: List<VideoSummary>, max: Int = 8, min: Int = 2): List<VideoSummary>? {
    val n = normalize(channel.name)
    return videos.filter { normalize(it.channelName) == n }.take(max).takeIf { it.size >= min }
}
