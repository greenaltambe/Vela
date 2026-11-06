package com.greenaltambe.vela.search

import com.greenaltambe.vela.extractor.ChannelRef
import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SearchFeedTest {
    private fun ch(name: String) = ChannelRef("UC$name", name, null, null)

    @Test fun shortsBlocksStartAfterThreeVideosThenEveryEightAndNeverRepeat() {
        assertEquals(listOf(ShortsSlot(3, 0, 8), ShortsSlot(11, 8, 12)), planShortsSlots(videoCount = 20, shortsCount = 12))
    }

    @Test fun noBlockEndsTheListOrAppearsWithoutShorts() {
        assertEquals(emptyList(), planShortsSlots(videoCount = 3, shortsCount = 10))
        assertEquals(emptyList(), planShortsSlots(videoCount = 30, shortsCount = 0))
        assertEquals(listOf(ShortsSlot(3, 0, 2)), planShortsSlots(videoCount = 4, shortsCount = 2))
    }

    @Test fun theChannelCardOnlyAppearsForARealMatch() {
        assertNotNull(topChannel("tech linked", listOf(ch("Other"), ch("TechLinked"))))
        assertNotNull(topChannel("TechLinked", listOf(ch("Tech Linked"))))
        assertNull(topChannel("kotlin", listOf(ch("Kotlin by JetBrains"))))
        assertNull(topChannel("tech", listOf(ch("TechLinked"))))
        assertNull(topChannel("ab", listOf(ch("ab"))))
    }

    @Test fun theLatestRowNeedsAtLeastTwoOfTheChannelsVideos() {
        val c = ch("TechLinked")
        fun v(id: String, by: String) = VideoSummary(id, id, "", by)
        assertNull(latestFrom(c, listOf(v("1", "TechLinked"), v("2", "Someone"))))
        assertEquals(listOf("1", "3"), latestFrom(c, listOf(v("1", "TechLinked"), v("2", "Someone"), v("3", "Tech Linked")))?.map { it.id })
    }
}
