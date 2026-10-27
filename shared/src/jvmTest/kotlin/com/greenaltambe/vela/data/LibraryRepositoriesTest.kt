package com.greenaltambe.vela.data

import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.feed.FeedEntry
import com.greenaltambe.vela.library.ImportedChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class LibraryRepositoriesTest {
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private fun at(s: Long) = Instant.fromEpochSeconds(s)
    private fun v(id: String) = VideoSummary(id, "title $id", "UC1", "Chan", durationSeconds = 100)

    @Test fun subscribeUnsubscribeAndImportDeduplicate() {
        val subs = SubscriptionRepository(db)
        subs.subscribe("a", "Alpha", null, at(1))
        assertTrue(subs.isSubscribed("a"))
        assertEquals(2, subs.import(listOf(ImportedChannel("a", "Alpha 2"), ImportedChannel("b", "Beta"), ImportedChannel("c", "Gamma")), at(2)))
        assertEquals(listOf("Alpha", "Beta", "Gamma"), subs.all().map { it.name }, "existing rows keep their name")
        subs.unsubscribe("b")
        assertFalse(subs.isSubscribed("b"))
    }

    @Test fun subscriptionsArePerAccount() {
        SubscriptionRepository(db, "one").subscribe("a", "A", null, at(1))
        assertTrue(SubscriptionRepository(db, "two").all().isEmpty())
    }

    @Test fun searchHistoryMovesRepeatsToTheTopAndClears() {
        val h = SearchHistoryRepository(db)
        h.add("cats", at(1)); h.add("dogs", at(2)); h.add("  ", at(3)); h.add("cats", at(4))
        assertEquals(listOf("cats", "dogs"), h.recent())
        h.remove("dogs")
        assertEquals(listOf("cats"), h.recent())
        h.clear()
        assertTrue(h.recent().isEmpty())
    }

    @Test fun historyListsDeletesOneVideoAndClears() {
        val repo = SqlDelightWatchHistoryRepository(db)
        repo.begin("local", "a", "UC", "A", at(1), 0, 10, 0)
        repo.begin("local", "a", "UC", "A", at(2), 0, 10, 0)
        repo.begin("local", "b", "UC", "B", at(3), 0, 10, 0)
        val lib = HistoryLibrary(db)
        assertEquals(listOf("b", "a", "a"), lib.recent().map { it.videoId })
        assertEquals(setOf("a", "b"), lib.watchedVideoIds())
        lib.deleteVideo("a")
        assertEquals(listOf("b"), lib.recent().map { it.videoId })
        lib.clear()
        assertTrue(lib.recent().isEmpty())
    }

    @Test fun watchLaterExistsFromTheStartAndCannotBeRenamedOrDeleted() {
        val p = PlaylistRepository(db)
        val watchLater = p.all().single()
        assertTrue(watchLater.isWatchLater)
        p.rename(watchLater.id, "Hacked", at(1))
        p.delete(watchLater.id)
        assertEquals("Watch later", p.all().single().name)
    }

    @Test fun playlistCreateRenameDeleteAndOrdering() {
        val p = PlaylistRepository(db)
        val id = p.create("Mine", at(10))
        p.rename(id, "Renamed", at(11))
        assertEquals(listOf("Watch later", "Renamed"), p.all().map { it.name })

        assertEquals(3, p.add(id, listOf(v("a"), v("b"), v("c"), v("a")), at(12)), "a duplicate in one call is ignored")
        assertEquals(0, p.add(id, listOf(v("b")), at(13)), "already present")
        assertEquals(listOf("a", "b", "c"), p.items(id).map { it.videoId })

        p.move(id, 2, 0)
        assertEquals(listOf("c", "a", "b"), p.items(id).map { it.videoId })
        p.move(id, 0, 5) // out of range: ignored
        assertEquals(listOf("c", "a", "b"), p.items(id).map { it.videoId })

        p.remove(id, "a")
        assertEquals(listOf("c", "b"), p.items(id).map { it.videoId })
        p.add(id, listOf(v("d")), at(14))
        assertEquals(listOf("c", "b", "d"), p.items(id).map { it.videoId }, "positions stay dense after a removal")
        assertEquals(3, p.all().first { it.id == id }.itemCount)

        p.delete(id)
        assertEquals(1, p.all().size)
        assertTrue(p.items(id).isEmpty(), "items go with the playlist")
    }

    @Test fun feedCacheRoundTripsAndTrims() {
        val repo = FeedRepository(db)
        val entries = (1..5).map { FeedEntry(v("v$it").copy(isShort = it == 1, viewCount = 10L * it), at(100L * it), at(1000)) }
        repo.replaceAll(entries, keep = 3)
        val back = repo.entries()
        assertEquals(listOf("v5", "v4", "v3"), back.map { it.video.id })
        assertEquals(50L, back.first().video.viewCount)
        repo.replaceAll(listOf(FeedEntry(v("v1").copy(isShort = true), at(50), at(1000))), keep = 10)
        assertTrue(repo.entries().first { it.video.id == "v1" }.video.isShort)
        repo.removeChannel("UC1")
        assertTrue(repo.entries().isEmpty())
    }
}
