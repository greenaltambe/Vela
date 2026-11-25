package com.greenaltambe.vela.library

import com.greenaltambe.vela.auth.AccountPlaylist
import com.greenaltambe.vela.auth.AccountReads
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorError
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.PlaylistContents
import com.greenaltambe.vela.extractor.PlaylistDetails
import com.greenaltambe.vela.extractor.StubExtractor
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.screens.PlaylistStore
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class RemotePlaylistTest {
    private fun v(id: String) = VideoSummary(id, "T $id", "UC", "C")
    private val details = PlaylistDetails("PL1", "Name", videoCount = 5)

    /** Pages by token (null = the first). A missing token is a failure. */
    private inner class Source(var pages: MutableMap<String?, PlaylistContents>, var failure: ExtractorError = ExtractorError.Network("offline")) : Extractor by StubExtractor() {
        val asked = mutableListOf<String?>()
        override suspend fun playlistPage(playlistId: String, continuation: String?): ExtractorResult<PlaylistContents> {
            asked += continuation
            return pages[continuation]?.let { ExtractorResult.Success(it) } ?: ExtractorResult.Failure(failure)
        }
    }

    private fun threePages() = Source(mutableMapOf(
        null to PlaylistContents(details, listOf(v("a"), v("b")), "2"),
        "2" to PlaylistContents(null, listOf(v("b"), v("c")), "3"),
        "3" to PlaylistContents(null, listOf(v("d"), v("e")), null),
    ))

    private class Account(val pages: Map<String?, PlaylistContents>) : AccountReads {
        val asked = mutableListOf<String?>()
        override suspend fun recommended(): List<VideoSummary>? = null
        override suspend fun playlists() = emptyList<AccountPlaylist>()
        override suspend fun playlistVideos(playlistId: String) = emptyList<VideoSummary>()
        override suspend fun playlistPage(playlistId: String, continuation: String?): PlaylistContents? { asked += continuation; return pages[continuation] }
    }

    @Test fun itReadsPageByPageAndAllOfItOnceEach() = runBlocking<Unit> {
        val source = threePages(); val account = Account(emptyMap())
        val p = RemotePlaylist("PL1", source, account)
        p.load(); p.load()
        assertEquals(listOf("a", "b"), p.videos.map { it.id })
        assertTrue(p.hasMore && !p.complete)
        assertTrue(p.loadAll() && p.complete)
        assertEquals(listOf("a", "b", "c", "d", "e"), p.videos.map { it.id })
        assertEquals(listOf(null, "2", "3"), source.asked)
        assertTrue(account.asked.isEmpty(), "the account is not used for a list that can be read without it")
    }

    @Test fun aPageThatFailsKeepsWhatWasReadAndCanBeTriedAgain() = runBlocking<Unit> {
        val source = threePages(); val last = source.pages.remove("3")!!
        val p = RemotePlaylist("PL1", source, null)
        assertFalse(p.loadAll())
        assertEquals(listOf("a", "b", "c"), p.videos.map { it.id })
        assertEquals(false, p.moreProblem?.expired)
        source.pages["3"] = last
        assertTrue(p.loadAll()); assertNull(p.moreProblem); assertEquals(5, p.videos.size)
    }

    @Test fun anExpiredListingSaysSoAndIsReadAgainFromTheStart() = runBlocking<Unit> {
        val source = threePages(); source.pages.remove("2"); source.failure = ExtractorError.TokenExpired("gone")
        val p = RemotePlaylist("PL1", source, null)
        p.load(); assertFalse(p.loadMore()); assertEquals(true, p.moreProblem?.expired)
        source.pages[null] = PlaylistContents(details, listOf(v("z")), null)
        p.reload()
        assertEquals(listOf("z"), p.videos.map { it.id }); assertNull(p.moreProblem); assertTrue(p.complete)
    }

    @Test fun aFailedRefreshKeepsTheListOnScreen() = runBlocking<Unit> {
        val source = threePages()
        val p = RemotePlaylist("PL1", source, null)
        p.load(); source.pages.clear(); p.reload()
        assertTrue(p.loaded); assertEquals(2, p.videos.size); assertNull(p.error); assertNotNull(p.moreProblem)
    }

    @Test fun aPrivateListIsReadByTheAccountForEveryPage() = runBlocking<Unit> {
        val source = Source(mutableMapOf(), ExtractorError.Unplayable("private"))
        val account = Account(mapOf(null to PlaylistContents(details, listOf(v("a")), "acc"), "acc" to PlaylistContents(null, listOf(v("b")), null)))
        val p = RemotePlaylist("PL1", source, account)
        assertTrue(p.loadAll())
        assertEquals(listOf("a", "b"), p.videos.map { it.id })
        assertEquals(listOf<String?>(null, "acc"), account.asked)
        assertEquals(listOf<String?>(null), source.asked)
    }

    @Test fun whenNobodyCanReadItTheReasonIsKept() = runBlocking<Unit> {
        val p = RemotePlaylist("PL1", Source(mutableMapOf()), Account(emptyMap()))
        assertFalse(p.loadAll()); assertEquals("offline", p.error); assertFalse(p.loaded)
    }

    @Test fun anEntryThatCouldNotBeReadMeansTheListIsNotWhole() = runBlocking<Unit> {
        val p = RemotePlaylist("PL1", Source(mutableMapOf(null to PlaylistContents(details, listOf(v("a")), null, unreadCount = 1))), null)
        assertFalse(p.loadAll()); assertEquals(1, p.unread)
    }

    @Test fun aTokenThatRepeatsWithNothingNewEndsTheReading() = runBlocking<Unit> {
        val source = Source(mutableMapOf(null to PlaylistContents(details, listOf(v("a")), "x"), "x" to PlaylistContents(null, listOf(v("a")), "x")))
        val p = RemotePlaylist("PL1", source, null)
        p.loadAll()
        assertEquals(listOf(null, "x"), source.asked); assertFalse(p.hasMore)
    }

    // ---- play all ----

    @Test fun playAllStartsAtOnceAndQueuesTheLaterPagesThroughTheFilter() = runBlocking<Unit> {
        val p = RemotePlaylist("PL1", threePages(), null); p.load()
        val navigator = Navigator(); val store = PlaylistStore()
        val filter = { list: List<VideoSummary> -> list.filterNot { it.id == "d" } }
        assertTrue(store.playAll(navigator, p, filter))
        assertEquals("a", navigator.nowPlaying?.id); assertEquals(listOf("b"), navigator.queue.map { it.id })
        assertNull(store.fill!!.run(navigator, filter))
        assertEquals(listOf("b", "c", "e"), navigator.queue.map { it.id })
    }

    @Test fun theQueueIsLeftAloneOnceItIsNoLongerThatPlaylists() = runBlocking<Unit> {
        for (change in listOf<(Navigator) -> Unit>({ it.clearQueue() }, { it.playAll(listOf(v("other"), v("next"))) }, { it.clearNowPlaying() })) {
            val source = threePages(); val p = RemotePlaylist("PL1", source, null); p.load()
            val navigator = Navigator(); val store = PlaylistStore()
            store.playAll(navigator, p) { it }
            change(navigator)
            val before = navigator.queue
            store.fill!!.run(navigator) { it }
            assertEquals(before, navigator.queue); assertEquals(listOf<String?>(null), source.asked, "nothing more is read for it")
        }
    }

    @Test fun playingOnDoesNotStopTheFillingAndNothingIsQueuedTwice() = runBlocking<Unit> {
        val p = RemotePlaylist("PL1", threePages(), null); p.load()
        val navigator = Navigator(); val store = PlaylistStore()
        store.playAll(navigator, p) { it }
        navigator.playNext()
        p.loadMore()   // a page the screen read by scrolling is queued all the same
        store.fill!!.run(navigator) { it }
        assertEquals("b", navigator.nowPlaying?.id); assertEquals(listOf("c", "d", "e"), navigator.queue.map { it.id })
    }

    @Test fun aPageThatFailsWhileFillingIsSaid() = runBlocking<Unit> {
        val source = threePages(); source.pages.remove("3")
        val p = RemotePlaylist("PL1", source, null); p.load()
        val navigator = Navigator(); val store = PlaylistStore()
        store.playAll(navigator, p) { it }
        assertEquals("Only the first 3 videos of the playlist are queued.", store.fill!!.run(navigator) { it })
    }
}
