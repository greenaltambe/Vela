package com.greenaltambe.vela.feed

import com.greenaltambe.vela.core.settings.*
import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FeedStepsTest {
    private val resolver = SettingsResolver(InMemorySettingsStore())
    private val context = FeedContext(resolver)

    private fun v(id: String, title: String = "t$id", channel: String = "c", seconds: Long? = 300, short: Boolean = false) =
        VideoSummary(id, title, channel, "n", durationSeconds = seconds, isShort = short)

    private fun run(steps: List<FeedStep>, items: List<VideoSummary>) = FeedPipeline(steps).run(items, context).map { it.id }

    @Test
    fun hideWatchedOnlyWhenEnabled() {
        val watched = WatchedLookup.of(setOf("a"))
        val items = listOf(v("a"), v("b"))
        assertEquals(listOf("a", "b"), run(listOf(HideWatched(watched)), items))
        resolver.set(Settings.hideWatched, true)
        assertEquals(listOf("b"), run(listOf(HideWatched(watched)), items))
    }

    @Test
    fun hideWatchedHonoursPerChannelOverride() {
        resolver.set(Settings.hideWatched, true)
        resolver.set(Settings.hideWatched, false, SettingLayer.channel("keep"))
        val watched = WatchedLookup.of(setOf("a", "b"))
        assertEquals(listOf("b"), run(listOf(HideWatched(watched)), listOf(v("a", channel = "c"), v("b", channel = "keep"))))
    }

    @Test
    fun minLengthDropsShortOnesButKeepsUnknownDurations() {
        resolver.set(Settings.minLengthSeconds, 120f)
        val items = listOf(v("short", seconds = 60), v("long", seconds = 600), v("unknown", seconds = null), v("edge", seconds = 120))
        assertEquals(listOf("long", "unknown", "edge"), run(listOf(MinLength), items))
    }

    @Test
    fun minLengthZeroIsOff() {
        assertEquals(listOf("a"), run(listOf(MinLength), listOf(v("a", seconds = 1))))
    }

    @Test
    fun blockKeywordsAreCaseInsensitiveSubstrings() {
        resolver.set(Settings.blockKeywords, "spoiler, REACTION")
        val items = listOf(v("a", "Big Spoilers inside"), v("b", "My reaction video"), v("c", "A fine video"))
        assertEquals(listOf("c"), run(listOf(KeywordFilter), items))
    }

    @Test
    fun allowListKeepsOnlyMatchesAndBlockStillWins() {
        resolver.set(Settings.allowKeywords, "kotlin\ncompose")
        resolver.set(Settings.blockKeywords, "clickbait")
        val items = listOf(v("a", "Kotlin tips"), v("b", "Cooking"), v("c", "Compose clickbait"))
        assertEquals(listOf("a"), run(listOf(KeywordFilter), items))
    }

    @Test
    fun keywordsCanBeRegexAndBadPatternsAreIgnored() {
        resolver.set(Settings.keywordsAreRegex, true)
        resolver.set(Settings.blockKeywords, "^\\[Ad\\], (unclosed")
        val items = listOf(v("a", "[Ad] buy now"), v("b", "Normal"))
        assertEquals(listOf("b"), run(listOf(KeywordFilter), items))
    }

    @Test
    fun keywordRulesApplyPerChannel() {
        resolver.set(Settings.blockKeywords, "news", SettingLayer.channel("noisy"))
        val items = listOf(v("a", "news today", channel = "noisy"), v("b", "news today", channel = "calm"))
        assertEquals(listOf("b"), run(listOf(KeywordFilter), items))
    }

    @Test
    fun shortsFeedKeepsOnlyShortsOfChannelsThatDoNotHideThem() {
        resolver.set(Settings.shortsMode, ShortsMode.SEPARATE_TAB)
        resolver.set(Settings.shortsMode, ShortsMode.HIDE, SettingLayer.channel("nope"))
        val items = listOf(v("s1", short = true), v("s2", short = true, channel = "nope"), v("long"))
        assertEquals(listOf("s1"), FeedPipeline(listOf(OnlyShorts)).run(items, context).map { it.id })
        assertEquals(listOf("long"), standardFeedPipeline(WatchedLookup.None).run(items, context).map { it.id })
    }

    @Test
    fun mergeKeepsCacheForFailedChannelsAndRefreshesTheRest() {
        fun e(id: String, at: Long?, fetched: Long, title: String = id) =
            FeedEntry(v(id, title), at?.let { Instant.fromEpochSeconds(it) }, Instant.fromEpochSeconds(fetched))
        val cached = listOf(e("old", 100, 1), e("same", 200, 1, "before"))
        val fresh = listOf(e("same", 200, 2, "after"), e("new", 300, 2))
        val merged = mergeFeed(cached, fresh)
        assertEquals(listOf("new", "same", "old"), merged.map { it.video.id })
        assertEquals("after", merged.first { it.video.id == "same" }.video.title)
    }

    @Test
    fun mergeKeepsAKnownDateWhenTheRefreshHasNone() {
        val old = FeedEntry(v("a"), Instant.fromEpochSeconds(500), Instant.fromEpochSeconds(1))
        val fresh = FeedEntry(v("a"), null, Instant.fromEpochSeconds(2))
        assertEquals(Instant.fromEpochSeconds(500), mergeFeed(listOf(old), listOf(fresh)).single().publishedAt)
    }

    @Test
    fun undatedEntriesSortAfterDatedAndTheFeedIsBounded() {
        val dated = FeedEntry(v("d"), Instant.fromEpochSeconds(1), Instant.fromEpochSeconds(1))
        val undated = FeedEntry(v("u"), null, Instant.fromEpochSeconds(99))
        assertEquals(listOf("d", "u"), mergeFeed(emptyList(), listOf(undated, dated)).map { it.video.id })
        assertEquals(1, mergeFeed(emptyList(), listOf(undated, dated), limit = 1).size)
    }
}
