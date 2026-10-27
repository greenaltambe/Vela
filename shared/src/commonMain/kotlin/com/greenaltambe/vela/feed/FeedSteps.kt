package com.greenaltambe.vela.feed

import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.ShortsMode
import com.greenaltambe.vela.extractor.VideoSummary

/** Videos the viewer has watched, for [HideWatched]. A video counts as watched once it was played at all. */
fun interface WatchedLookup {
    fun isWatched(videoId: String): Boolean

    companion object {
        val None = WatchedLookup { false }
        fun of(ids: Set<String>) = WatchedLookup { it in ids }
    }
}

/** Drops videos already in the local history, unless that channel's setting says to keep them. */
class HideWatched(private val watched: WatchedLookup) : FeedStep {
    override fun apply(items: List<VideoSummary>, context: FeedContext): List<VideoSummary> =
        items.filter { !(context.settings.resolve(Settings.hideWatched, channelId = it.channelId) && watched.isWatched(it.id)) }
}

/**
 * Drops videos shorter than the channel's minimum length. A video whose length the source did not report is kept:
 * the RSS feed carries no duration, and hiding everything from it would be worse than showing a short one.
 */
object MinLength : FeedStep {
    override fun apply(items: List<VideoSummary>, context: FeedContext): List<VideoSummary> =
        items.filter { v ->
            val min = context.settings.resolve(Settings.minLengthSeconds, channelId = v.channelId).toLong()
            min <= 0 || v.durationSeconds == null || v.durationSeconds >= min
        }
}

/** Block and allow words, split on commas and new lines. */
data class KeywordRules(val block: List<String>, val allow: List<String>, val regex: Boolean) {
    private val blockMatchers = block.mapNotNull(::matcher)
    private val allowMatchers = allow.mapNotNull(::matcher)

    private fun matcher(word: String): ((String) -> Boolean)? =
        if (regex) runCatching { Regex(word, RegexOption.IGNORE_CASE) }.getOrNull()?.let { r -> { t: String -> r.containsMatchIn(t) } }
        else { t: String -> t.contains(word, ignoreCase = true) }

    /** Blocked wins over allowed; with an allow list, only matching titles pass. */
    fun accepts(title: String): Boolean {
        if (blockMatchers.any { it(title) }) return false
        return allowMatchers.isEmpty() || allowMatchers.any { it(title) }
    }

    companion object {
        fun split(raw: String): List<String> = raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/** Block / allow by title, per channel, case-insensitive; plain substrings by default, regular expressions by setting. */
object KeywordFilter : FeedStep {
    override fun apply(items: List<VideoSummary>, context: FeedContext): List<VideoSummary> {
        val regex = context.settings.resolve(Settings.keywordsAreRegex)
        val cache = HashMap<Pair<String, String>, KeywordRules>()
        return items.filter { v ->
            val block = context.settings.resolve(Settings.blockKeywords, channelId = v.channelId)
            val allow = context.settings.resolve(Settings.allowKeywords, channelId = v.channelId)
            if (block.isEmpty() && allow.isEmpty()) return@filter true
            cache.getOrPut(block to allow) { KeywordRules(KeywordRules.split(block), KeywordRules.split(allow), regex) }.accepts(v.title)
        }
    }
}

/** Keeps only Shorts: the content of the Shorts tab when [ShortsMode.SEPARATE_TAB] is set for the channel. */
object OnlyShorts : FeedStep {
    override fun apply(items: List<VideoSummary>, context: FeedContext): List<VideoSummary> =
        items.filter { it.isShort && context.settings.resolve(Settings.shortsMode, channelId = it.channelId) != ShortsMode.HIDE }
}

/** The steps of the main subscription / home feed, in order. */
fun standardFeedPipeline(watched: WatchedLookup): FeedPipeline =
    FeedPipeline(listOf(HideShorts, HideWatched(watched), MinLength, KeywordFilter))

/** The Shorts tab inside Subscriptions: Shorts of channels whose mode is not HIDE, still through the other filters. */
fun shortsFeedPipeline(watched: WatchedLookup): FeedPipeline =
    FeedPipeline(listOf(OnlyShorts, HideWatched(watched), KeywordFilter))
