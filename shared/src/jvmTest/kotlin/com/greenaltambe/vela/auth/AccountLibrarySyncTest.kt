package com.greenaltambe.vela.auth

import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.PlaylistRepository
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.time.Clock

class AccountLibrarySyncTest {
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private fun v(id: String) = VideoSummary(id, "T $id", "UC", "C")
    private class Reads(var lists: List<AccountPlaylist>? , var videos: Map<String, List<VideoSummary>>) : AccountReads {
        override suspend fun recommended() = null
        override suspend fun playlists() = lists
        override suspend fun playlistVideos(playlistId: String) = videos[playlistId]
    }
    private fun sync(reads: Reads, account: String = "acc") = runBlocking { AccountLibrarySync(reads, { PlaylistRepository(db, account) }, Clock.System).sync() }

    @Test fun copiesAddsOnlyAndNeverDeletesOrDuplicates() {
        val reads = Reads(listOf(AccountPlaylist("WL", "Watch later", 2), AccountPlaylist("PL9", "Mine", 1)), mapOf("WL" to listOf(v("a"), v("b")), "PL9" to listOf(v("c"))))
        val r1 = sync(reads); assertEquals(3, r1.added)
        val repo = PlaylistRepository(db, "acc")
        val wl = repo.all().single { it.name == "Watch later (YouTube)" }
        assertTrue(wl.isMirror && !wl.isWatchLater); assertEquals(2, wl.itemCount)
        assertEquals("Mine", repo.all().single { it.isMirror && it.name == "Mine" }.name)
        // The account drops "b" and adds "d": the mirror gains d and keeps b.
        reads.videos = mapOf("WL" to listOf(v("a"), v("d")), "PL9" to listOf(v("c")))
        val r2 = sync(reads); assertEquals(1, r2.added)
        assertEquals(setOf("a", "b", "d"), repo.items(wl.id).map { it.videoId }.toSet())
        // The viewer removes "a" locally: it returns on the next sync (documented rule).
        repo.remove(wl.id, "a")
        sync(reads); assertTrue(repo.contains(wl.id, "a"))
        assertEquals(1, repo.all().count { it.name == "Watch later (YouTube)" }, "one mirror, not one per sync")
    }

    @Test fun aRenameOnTheAccountRenamesTheMirrorAndAFailedReadChangesNothing() {
        val reads = Reads(listOf(AccountPlaylist("PL9", "Old name", 0)), mapOf("PL9" to emptyList()))
        sync(reads); reads.lists = listOf(AccountPlaylist("PL9", "New name", 0)); sync(reads)
        val repo = PlaylistRepository(db, "acc")
        assertEquals(listOf("New name"), repo.all().filter { it.isMirror }.map { it.name })
        reads.lists = null
        val failed = sync(reads); assertNotNull(failed.failure)
        assertEquals(1, repo.all().count { it.isMirror })
    }

    @Test fun mirrorsBelongToTheirAccountOnly() {
        sync(Reads(listOf(AccountPlaylist("PL1", "A's list", 1)), mapOf("PL1" to listOf(v("x")))), "acc-a")
        assertEquals(emptyList(), PlaylistRepository(db, "acc-b").all().filter { it.isMirror })
        sync(Reads(listOf(AccountPlaylist("PL1", "B's list", 1)), mapOf("PL1" to listOf(v("y")))), "acc-b")
        assertEquals("A's list", PlaylistRepository(db, "acc-a").all().single { it.isMirror }.name)
    }
}
