package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import kotlin.test.*

/** Hand-built from the shapes measured on 2026-11-25 (report 164); no real playlist content. */
class PlaylistPageTest {
    private val sent = mutableListOf<String>()

    /** [pages] are answered in order. */
    private fun client(vararg pages: String): InnertubeClient {
        val left = ArrayDeque(pages.toList())
        val transport = FakeTransport { _, _, _, body -> sent += body.orEmpty(); RawResponse(200, left.removeFirst(), emptyMap(), "") }
        val jar = CookieJar(listOf(Cookie("SID", "sid-v", ".google.com"), Cookie("SAPISID", "AbCdEfGhIjKlMnOp/1234567890", ".youtube.com")))
        return InnertubeClient(AuthSession(jar, AccountGovernor("a1", limits = { GovernorLimits(minSpacingMs = 0, browsePerMinute = 100) }), transport), WebClient("WEB", "2.0", "en", "US", "UA"))
    }

    private fun lockup(id: String, channel: String = "Chan") = """{"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_VIDEO","contentId":"$id",
        "contentImage":{"thumbnailViewModel":{"overlays":[{"thumbnailBadgeViewModel":{"text":"1:05"}}]}},
        "metadata":{"lockupMetadataViewModel":{"title":{"content":"T $id"},"metadata":{"contentMetadataViewModel":{"metadataRows":[
        {"metadataParts":[{"text":{"content":"$channel","commandRuns":[{"onTap":{"innertubeCommand":{"browseEndpoint":{"browseId":"UC$id"}}}}]}}]},
        {"metadataParts":[{"text":{"content":"10 views"}},{"text":{"content":"2 days ago"}}]}]}}}}}}"""
    private fun classic(id: String) = """{"playlistVideoRenderer":{"videoId":"$id","title":{"runs":[{"text":"T $id"}]},"lengthSeconds":"61",
        "shortBylineText":{"runs":[{"text":"Chan","navigationEndpoint":{"browseEndpoint":{"browseId":"UC$id"}}}]}}}"""
    private fun nextNew(token: String) = """{"continuationItemViewModel":{"continuationCommand":{"innertubeCommand":{"continuationCommand":{"token":"$token"}}}}}"""
    private fun nextClassic(token: String) = """{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"$token"}}}}"""

    private fun newHeader(count: String) = """{"pageHeaderRenderer":{"content":{"pageHeaderViewModel":{"metadata":{"contentMetadataViewModel":{"metadataRows":[
        {"metadataParts":[{"text":{"content":"Playlist"}},{"text":{"content":"$count"}},{"text":{"content":"9 views"}}]}]}}}}}}"""
    private fun oldHeader(count: String) = """{"playlistHeaderRenderer":{"numVideosText":{"runs":[{"text":"$count"}]}}}"""

    /** A first page: [sections] are the entry lists of the item sections; [after] sits beside them, as YouTube's own continuation of the page does. */
    private fun page(vararg sections: List<String>, header: String = newHeader("2 videos"), after: String? = null): String {
        val list = sections.map { """{"itemSectionRenderer":{"contents":[${it.joinToString(",")}]}}""" } + listOfNotNull(after)
        return """{"header":$header,"metadata":{"playlistMetadataRenderer":{"title":"P"}},"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
            {"sectionListRenderer":{"contents":[${list.joinToString(",")}]}}}}]}}}"""
    }
    private fun appended(vararg entries: String) = """{"contents":{},"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[${entries.joinToString(",")}]}}]}"""

    @Test fun theNewLayoutIsReadAcrossPages() {
        val (videos, complete) = client(page(listOf(lockup("a"), lockup("b", "Word of the Day"), nextNew("p2"))), appended(lockup("c"))).playlistListing("LL")
        assertEquals(listOf("a", "b", "c"), videos.map { it.videoId })
        assertTrue(complete)
        assertEquals(listOf("T b", "Word of the Day", "UCb", 65L), videos[1].let { listOf(it.title, it.channelName, it.channelId, it.durationSeconds) })
        assertTrue("\"continuation\":\"p2\"" in sent[1])
    }

    @Test fun anEntryWithoutAChannelRowTakesTheNameFromTheChannelPicture() {
        val noRow = """{"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_VIDEO","contentId":"a","metadata":{"lockupMetadataViewModel":{"title":{"content":"T"},
            "image":{"decoratedAvatarViewModel":{"a11yLabel":"Go to channel Some One"}},
            "metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"10 views"}}]}]}}}}}}"""
        assertEquals("Some One", client(page(listOf(noRow))).playlistListing("LL").first.single().channelName)
    }

    @Test fun theOldLayoutIsReadAcrossPages() {
        val old = page(listOf("""{"playlistVideoListRenderer":{"contents":[${classic("a")},${nextClassic("p2")}]}}"""), header = oldHeader("2 videos"))
        val (videos, complete) = client(old, appended(classic("b"))).playlistListing("WL")
        assertEquals(listOf("a", "b"), videos.map { it.videoId })
        assertEquals("UCa", videos[0].channelId)
        assertTrue(complete)
    }

    @Test fun suggestionsAfterThePlaylistAreNeitherReadNorFollowed() {
        val (videos, complete) = client(page(listOf(lockup("a")), listOf(lockup("suggested")), after = nextNew("section"))).playlistListing("PL1")
        assertEquals(listOf("a"), videos.map { it.videoId })
        assertTrue(complete)
        assertEquals(1, sent.size, "the page's own continuation is not part of the playlist")
    }

    @Test fun videosStatedButNoneReadIsAFailureNotAnEmptyPlaylist() {
        val unknown = """{"somethingNewViewModel":{"videoId":"a"}}"""
        assertFailsWith<AuthFailure.Unparseable> { client(page(listOf(unknown))).playlistListing("PL1") }
        assertFailsWith<AuthFailure.Unparseable> { client(page(header = oldHeader("1,204 videos"))).playlistListing("PL1") }
        assertFailsWith<AuthFailure.Unparseable> { client(page(listOf(unknown), header = "{}")).playlistListing("PL1") }
        assertFailsWith<AuthFailure.Unparseable> { client("""{"responseContext":{}}""").playlistListing("PL1") }
    }

    @Test fun aPlaylistThatSaysItIsEmptyIsReadAsEmpty() {
        for (header in listOf(oldHeader("No videos"), newHeader("0 videos"))) {
            val (videos, complete) = client(page(listOf("""{"shelfRenderer":{}}"""), header = header)).playlistListing("PL1")
            assertTrue(videos.isEmpty() && complete)
        }
        val (videos, complete) = client(page(listOf("""{"messageRenderer":{}}"""), header = "{}")).playlistListing("PL1")
        assertTrue(videos.isEmpty() && complete)
    }

    @Test fun anythingLeftUnreadMakesTheListIncomplete() {
        fun complete(vararg pages: String, maxPages: Int = 60) = client(*pages).playlistListing("PL1", maxPages).also { assertEquals("a", it.first.first().videoId) }.second
        assertFalse(complete(page(listOf(lockup("a"), """{"somethingNewViewModel":{}}"""))), "an unknown entry")
        assertFalse(complete(page(listOf(lockup("a"), """{"continuationItemViewModel":{}}"""))), "a next-page entry without a token")
        assertFalse(complete(page(listOf(lockup("a"), nextNew("p2"))), """{"responseContext":{}}"""), "a further page with no list")
        assertFalse(complete(page(listOf(lockup("a"), nextNew("p2"))), maxPages = 1), "the page limit")
    }
}
