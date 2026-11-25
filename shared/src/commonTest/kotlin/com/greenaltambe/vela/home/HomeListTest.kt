package com.greenaltambe.vela.home

import com.greenaltambe.vela.auth.FeedGroup
import com.greenaltambe.vela.auth.FeedGroupKind
import com.greenaltambe.vela.auth.HomePage
import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.test.*

class HomeListTest {
    private fun v(id: String) = VideoSummary(id, "T $id", "", "")
    private fun group(after: String?, vararg ids: String) = FeedGroup(FeedGroupKind.SUGGESTED, ids.joinToString(), null, ids.map(::v), after)
    private fun page(videos: List<String>, groups: List<FeedGroup> = emptyList(), next: String? = "n") = HomePage(videos.map(::v), next, groups = groups)
    private fun HomeList.feed() = videos.items.map { it.id }
    private fun HomeList.rows() = groups.map { g -> g.afterVideoId to g.videos.map { it.id } }

    @Test fun pagesAreAppendedAndTheTokenIsTheLastPages() {
        val list = HomeList().append(page(listOf("a", "b"), next = "n1")).append(page(listOf("b", "c"), next = "n2"))
        assertEquals(listOf("a", "b", "c"), list.feed())
        assertEquals("n2", list.videos.continuation)
    }

    @Test fun aRowKeepsItsPlaceOnItsOwnPage() {
        val list = HomeList().append(page(listOf("a", "b"), listOf(group("a", "g1", "g2"), group(null, "h1"))))
        assertEquals(listOf("a" to listOf("g1", "g2"), null to listOf("h1")), list.rows())
    }

    @Test fun aRowAtTheTopOfALaterPageFollowsThePreviousPagesLastVideo() {
        val list = HomeList().append(page(listOf("a", "b"))).append(page(listOf("c"), listOf(group(null, "g1"))))
        assertEquals(listOf("b" to listOf("g1")), list.rows())
    }

    @Test fun aRowWhoseVideoWasAlreadyListedFollowsTheNearestNewOneBeforeIt() {
        val first = HomeList().append(page(listOf("a", "b")))
        assertEquals("c", first.append(page(listOf("c", "a", "d"), listOf(group("a", "g1")))).rows().single().first)
        assertEquals("b", first.append(page(listOf("a", "d"), listOf(group("a", "g1")))).rows().single().first)
    }

    @Test fun aRowDropsVideosTheFeedOrAnEarlierRowAlreadyHas() {
        val list = HomeList().append(page(listOf("a", "b"), listOf(group("a", "b", "g1", "g2"))))
            .append(page(listOf("c"), listOf(group("c", "a", "g2", "g3"), group("c", "g1", "c"))))
        assertEquals(listOf(listOf("g1", "g2"), listOf("g3")), list.groups.map { g -> g.videos.map { it.id } })
    }

    @Test fun aPageOfRowsOnlyIsKeptAndTheListIsNotEmpty() {
        val list = HomeList().append(page(emptyList(), listOf(group(null, "g1"))))
        assertFalse(list.isEmpty); assertTrue(list.videos.items.isEmpty())
        assertTrue(HomeList().append(page(emptyList(), next = null)).isEmpty)
    }
}
