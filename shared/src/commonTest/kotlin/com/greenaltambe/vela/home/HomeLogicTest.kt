package com.greenaltambe.vela.home

import com.greenaltambe.vela.data.WatchRecord
import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeLogicTest {
    private fun v(id: String, channel: String = "c$id", title: String = "title $id", seconds: Long? = 300, live: Boolean = false, short: Boolean = false, up: Instant? = null) =
        VideoSummary(id, title, channel, "name $channel", durationSeconds = seconds, isLive = live, isShort = short, uploadedAt = up)

    private fun rec(video: String, channel: String, title: String = "t") = WatchRecord(1, "a", video, channel, title, Instant.fromEpochSeconds(0), 0, 0, 0)

    @Test fun becauseYouWatchedRanksWhatSeveralListsRecommend() {
        val out = becauseYouWatched(listOf(listOf(v("a"), v("shared")), listOf(v("shared"), v("b")), listOf(v("c"))), exclude = emptySet())
        assertEquals("shared", out.first().id)
        assertEquals(setOf("a", "b", "c", "shared"), out.map { it.id }.toSet())
    }

    @Test fun becauseYouWatchedDropsWatchedSeedsAndShortsAndCapsAChannel() {
        val lists = listOf(listOf(v("seed"), v("w"), v("s", short = true), v("x1", "same"), v("x2", "same"), v("x3", "same"), v("y")))
        val out = becauseYouWatched(lists, exclude = setOf("seed", "w"), perChannel = 2)
        assertEquals(listOf("x1", "x2", "y"), out.map { it.id })
    }

    @Test fun aListedVideoOutranksAHighPositionInOneList() {
        val out = becauseYouWatched(listOf(listOf(v("first"), v("twice")), listOf(v("other"), v("twice"))), emptySet())
        assertEquals("twice", out.first().id)
    }

    @Test fun filtersWorkOnWhatIsLoaded() {
        val watched = setOf("seen")
        assertFalse(HomeFilter.UNWATCHED.matches(v("seen"), watched)); assertTrue(HomeFilter.UNWATCHED.matches(v("new"), watched))
        assertTrue(HomeFilter.SHORT.matches(v("a", seconds = 599), emptySet())); assertFalse(HomeFilter.SHORT.matches(v("a", seconds = 600), emptySet()))
        assertFalse(HomeFilter.SHORT.matches(v("a", seconds = null), emptySet()))
        assertTrue(HomeFilter.LIVE.matches(v("a", live = true), emptySet()))
        assertTrue(HomeFilter.MUSIC.matches(v("a", title = "Artist - Song (Official Video)"), emptySet()))
        assertTrue(HomeFilter.MUSIC.matches(v("a", channel = "x").copy(channelName = "Some Artist - Topic"), emptySet()))
        assertFalse(HomeFilter.MUSIC.matches(v("a", title = "How to cook rice"), emptySet()))
        assertTrue(matchesTopic(v("a", title = "Learning Kotlin fast"), "kotlin")); assertFalse(matchesTopic(v("a"), ""))
    }

    @Test fun dismissedVideosAndChannelsAreLeftOut() {
        val list = listOf(v("a", "c1"), v("b", "c2"), v("c", "c3"))
        assertEquals(listOf("b"), list.withoutDismissed(HomeDismissed(videos = setOf("a"), channels = setOf("c3"))).map { it.id })
        assertEquals(list, list.withoutDismissed(HomeDismissed()))
    }

    @Test fun channelsWatchedALotNeedSeveralDifferentVideosAndNoSubscription() {
        val history = listOf(rec("1", "A"), rec("2", "A"), rec("2", "A"), rec("3", "B"), rec("4", "S"), rec("5", "S"), rec("6", "C"), rec("7", "C"), rec("8", "C"))
        assertEquals(listOf("C", "A"), channelsWatchedALot(history, subscribed = setOf("S")))
        assertEquals(listOf("A"), channelsWatchedALot(history, setOf("S"), hidden = setOf("C")))
    }

    @Test fun keywordsAreTheWordsThatComeUpOften() {
        val titles = List(4) { "Learning Kotlin the easy way $it" } + List(3) { "Kotlin multiplatform guide $it" } + listOf("Random cooking video")
        val words = topKeywords(titles, n = 2)
        assertEquals("kotlin", words.first())
        assertFalse("the" in words || "video" in words)
    }

    @Test fun newSinceNeedsAKnownTimeAfterTheLastVisit() {
        val at = Instant.fromEpochSeconds(1000)
        assertTrue(isNewSince(v("a", up = Instant.fromEpochSeconds(2000)), at))
        assertFalse(isNewSince(v("a", up = Instant.fromEpochSeconds(500)), at))
        assertFalse(isNewSince(v("a", up = null), at)); assertFalse(isNewSince(v("a", up = Instant.fromEpochSeconds(2000)), null))
    }

    @Test fun interleaveTakesOneFromEachListInTurn() {
        assertEquals(listOf(1, 3, 2, 4, 5), interleave(listOf(listOf(1, 2), listOf(3, 4, 5))))
    }
}
