package com.greenaltambe.vela.auth

import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.PlaylistOpRepository
import com.greenaltambe.vela.data.PlaylistRepository
import com.greenaltambe.vela.data.PlaylistSync
import com.greenaltambe.vela.data.RowState
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Task 84: one playlist model (LOCAL or SYNCED), two-way and loss-proof, with an outbox and YouTube's size limit. */
class PlaylistServiceTest {
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val accounts = AccountManager(db, CredentialStore(InMemorySecretStore()))
    private val jar = CookieJar(listOf(Cookie("SAPISID", "secret-value", ".youtube.com")))
    private fun google(channel: String) = accounts.addGoogle(GoogleIdentity("P", "$channel@example.com", null, channel), jar)
    private fun v(id: String) = VideoSummary(id, "T $id", "UC", "C")

    /** YouTube as a fake: playlists of video ids, a size limit, and levers to refuse or defer writes. */
    private class Remote(val lists: MutableMap<String, MutableList<String>> = mutableMapOf(), var cap: Int = Int.MAX_VALUE) : AccountActions, AccountReads {
        val calls = mutableListOf<String>()
        var next: ActionResult? = null           // answers the next write once
        var batchRefused = false
        var readFails = false
        var cut = false                          // the listing stops early
        var created = 0
        private fun write(call: String, then: () -> Unit): ActionResult { calls += call; next?.let { next = null; return it }; then(); return ActionResult.Ok }
        override suspend fun subscribe(channelId: String) = ActionResult.Ok
        override suspend fun unsubscribe(channelId: String) = ActionResult.Ok
        override suspend fun rate(videoId: String, rating: VideoRating) = ActionResult.Ok
        override suspend fun addToPlaylist(playlistId: String, videoId: String) = write("add $playlistId $videoId") {
            val l = lists.getOrPut(playlistId) { mutableListOf() }
            if (videoId !in l) l += videoId
        }
        override suspend fun addManyToPlaylist(playlistId: String, videoIds: List<String>): ActionResult {
            if (batchRefused && videoIds.size > 1) { calls += "batch-refused ${videoIds.size}"; return ActionResult.Failed("playlist edit answered STATUS_FAILED") }
            val l = lists.getOrPut(playlistId) { mutableListOf() }
            if (l.size + videoIds.count { it !in l } > cap) { calls += "many-full $playlistId ${videoIds.size}"; return ActionResult.Failed("This playlist is full (5000 videos).") }
            return write("many $playlistId ${videoIds.size}") { videoIds.forEach { if (it !in l) l += it } }
        }
        override suspend fun removeFromPlaylist(playlistId: String, videoId: String) = write("remove $playlistId $videoId") { lists[playlistId]?.remove(videoId) }
        override suspend fun createPlaylist(title: String): Pair<ActionResult, String?> { calls += "create $title"; next?.let { next = null; return it to null }; val id = "PLnew${++created}"; lists[id] = mutableListOf(); return ActionResult.Ok to id }
        override suspend fun renamePlaylist(playlistId: String, title: String) = write("rename $playlistId $title") {}
        override suspend fun deletePlaylist(playlistId: String) = write("delete $playlistId") { lists.remove(playlistId) }

        val titles = mutableMapOf<String, String>()
        override suspend fun recommended() = null
        override suspend fun playlists(): List<AccountPlaylist>? = if (readFails) null else lists.keys.map { AccountPlaylist(it, titles[it] ?: it, lists[it]!!.size) }
        override suspend fun playlistVideos(playlistId: String): List<VideoSummary>? = if (readFails) null else lists[playlistId]?.map { VideoSummary(it, "T $it", "UC", "C") }
        override suspend fun playlistListing(playlistId: String): PlaylistListing? =
            playlistVideos(playlistId)?.let { if (cut) PlaylistListing(it.take(it.size / 2), false) else PlaylistListing(it, true) }
    }

    private fun service(remote: Remote?, limit: Int = 5000) = PlaylistService(db, accounts, remote, remote, settings = SettingsResolver(InMemorySettingsStore()), limit = limit)
    private fun repo(id: String) = PlaylistRepository(db, id)
    private fun ids(repo: PlaylistRepository, list: Long) = repo.items(list).map { it.videoId }

    @Test fun aLocalPlaylistNeverTouchesTheNetwork() = runBlocking<Unit> {
        val g = google("UC1"); val remote = Remote(); val svc = service(remote)
        val id = svc.create("Mine", synced = false).id!!
        svc.add(id, listOf(v("a"), v("b"))); svc.remove(id, listOf("a")); svc.rename(id, "Renamed"); svc.delete(id)
        assertEquals(emptyList(), remote.calls)
        assertTrue(repo(g.id).all().none { it.name == "Renamed" })
    }

    @Test fun tickingTheBoxCreatesAPrivateCopyPushesTheVideosInOneRequestAndKeepsThemLocally() = runBlocking<Unit> {
        val g = google("UC1"); val remote = Remote(); val svc = service(remote)
        val id = svc.create("Mine", synced = false).id!!
        svc.add(id, listOf(v("a"), v("b"), v("c")))
        val r = svc.setSynced(id, true)
        assertTrue(r.ok, r.message)
        assertEquals(listOf("create Mine", "many PLnew1 3"), remote.calls, "one create, and the three videos in one request")
        assertEquals(listOf("a", "b", "c"), remote.lists["PLnew1"]?.toList())
        val p = repo(g.id).get(id)!!
        assertEquals("PLnew1", p.remoteId); assertEquals(PlaylistSync.SYNCED, p.syncState)
        assertTrue(repo(g.id).items(id).all { it.rowState == RowState.SYNCED })
        assertEquals(0, svc.waitingChanges())
    }

    @Test fun linkingAHundredAndTwentyItemsGoesInChunksAndResumesAfterTheLimitRefusesMidWay() = runBlocking<Unit> {
        val g = google("UC1"); val remote = Remote(); val svc = service(remote)
        val id = svc.create("Big", synced = false).id!!
        svc.add(id, (1..120).map { v("v$it") })
        // The first chunk is sent, the second is refused by the hourly limit: the rest waits in the outbox.
        var n = 0
        val flaky = object : AccountActions by remote {
            override suspend fun addManyToPlaylist(playlistId: String, videoIds: List<String>): ActionResult =
                if (++n == 2) ActionResult.Queued("Hourly account-action limit reached.") else remote.addManyToPlaylist(playlistId, videoIds)
        }
        val svc2 = PlaylistService(db, accounts, flaky, remote)
        val r = svc2.setSynced(id, true)
        assertTrue(r.ok && r.pending, "kept, to be sent later: ${r.message}")
        assertEquals(50, remote.lists["PLnew1"]!!.size)
        assertEquals(70, svc2.waitingChanges())
        assertEquals(PlaylistSync.LINKING, repo(g.id).get(id)!!.syncState)
        // "After a restart": a new service over the same database sends the rest, 50 then 20.
        val svc3 = service(remote)
        svc3.drain()
        assertEquals(120, remote.lists["PLnew1"]!!.size)
        assertEquals(0, svc3.waitingChanges())
        assertEquals(listOf("many PLnew1 50", "many PLnew1 50", "many PLnew1 20").size, remote.calls.count { it.startsWith("many") }, "50, 50, 20")
    }

    @Test fun ifTheAccountRefusesTheBatchTheVideosGoOneByOne() = runBlocking<Unit> {
        val g = google("UC1"); val remote = Remote().also { it.batchRefused = true }; val svc = service(remote)
        val id = svc.create("Mine", synced = false).id!!
        svc.add(id, listOf(v("a"), v("b")))
        assertTrue(svc.setSynced(id, true).ok)
        assertEquals(listOf("a", "b"), remote.lists["PLnew1"]?.toList())
        assertTrue(remote.calls.any { it.startsWith("batch-refused") } && remote.calls.count { it.startsWith("add ") } == 2)
    }

    @Test fun aSyncedPlaylistWritesToTheAccountAndARefusedWriteIsRolledBack() = runBlocking<Unit> {
        val g = google("UC1"); val remote = Remote(); val svc = service(remote)
        val id = svc.create("Mine", synced = true).id!!
        assertTrue(svc.add(id, listOf(v("a"))).ok)
        assertEquals(listOf("a"), remote.lists["PLnew1"]?.toList()); assertEquals(listOf("a"), ids(repo(g.id), id))
        remote.next = ActionResult.Failed("Account traffic is paused.")
        val r = svc.add(id, listOf(v("b")))
        assertFalse(r.ok); assertEquals("Account traffic is paused.", r.message)
        assertEquals(listOf("a"), ids(repo(g.id), id), "rolled back"); assertEquals(0, svc.waitingChanges())
        remote.next = ActionResult.Failed("nope")
        assertFalse(svc.remove(id, listOf("a")).ok)
        assertEquals(listOf("a"), ids(repo(g.id), id), "the removal is rolled back too"); assertEquals(RowState.SYNCED, repo(g.id).items(id).single().rowState)
        assertTrue(svc.remove(id, listOf("a")).ok)
        assertEquals(emptyList(), remote.lists["PLnew1"]?.toList()); assertEquals(emptyList(), ids(repo(g.id), id))
    }

    @Test fun aWriteTheLimitRefusesIsKeptAndSurvivesARestart() = runBlocking<Unit> {
        val g = google("UC1"); val remote = Remote(); val svc = service(remote)
        val id = svc.create("Mine", synced = true).id!!
        remote.next = ActionResult.Queued("Hourly account-action limit reached.")
        val r = svc.add(id, listOf(v("a")))
        assertTrue(r.ok && r.pending)
        assertEquals(listOf("a"), ids(repo(g.id), id)); assertEquals(1, svc.waitingChanges())
        assertEquals(RowState.PENDING_ADD, repo(g.id).items(id).single().rowState)
        service(remote).drain()   // a new service over the same database = a restart
        assertEquals(listOf("a"), remote.lists["PLnew1"]?.toList()); assertEquals(0, service(remote).waitingChanges())
        assertEquals(RowState.SYNCED, repo(g.id).items(id).single().rowState)
    }

    @Test fun anAddAndARemoveBeforeAnythingIsSentCancelEachOtherAndARepeatedRenameKeepsTheLast() = runBlocking<Unit> {
        val g = google("UC1"); val ops = PlaylistOpRepository(db, g.id)
        ops.enqueueAdd(7, "a", kotlin.time.Clock.System.now()); ops.enqueueRemove(7, "a", kotlin.time.Clock.System.now())
        assertEquals(0, ops.count())
        ops.enqueueRename(7, "One", kotlin.time.Clock.System.now()); ops.enqueueRename(7, "Two", kotlin.time.Clock.System.now())
        assertEquals(listOf("Two"), ops.all().map { it.payload })
    }

    @Test fun twoAccountsNeverSeeEachOthersPlaylistsOrOps() = runBlocking<Unit> {
        val a = google("UCa"); val remote = Remote(); val svc = service(remote)
        val ida = svc.create("A's", synced = true).id!!
        remote.next = ActionResult.Queued("limit"); svc.add(ida, listOf(v("x")))
        val b = google("UCb"); accounts.switchTo(b.id)
        assertEquals(emptyList(), repo(b.id).all().filter { it.name == "A's" })
        assertEquals(0, service(remote).waitingChanges(), "the other account has no outbox")
        accounts.switchTo(a.id)
        assertEquals(1, service(remote).waitingChanges())
    }

    @Test fun theSyncIsTwoWayAndNeverDropsWhatIsWaitingToBeAddedOrWhatAFailedReadCouldNotConfirm() = runBlocking<Unit> {
        val g = google("UC1"); val remote = Remote(); val svc = service(remote)
        remote.lists["PL1"] = mutableListOf("a", "b", "c"); remote.titles["PL1"] = "Remote"
        svc.syncNow()
        val id = repo(g.id).all().single { it.remoteId == "PL1" }.id
        assertEquals(listOf("a", "b", "c"), ids(repo(g.id), id), "brought in as a synced playlist")
        // Removed on YouTube and added on YouTube: both come over (the old behaviour only ever added).
        remote.lists["PL1"] = mutableListOf("c", "d", "a")
        svc.syncNow()
        assertEquals(listOf("c", "d", "a"), ids(repo(g.id), id), "b is gone, d is new, and YouTube's order wins")
        // A video waiting to be added (the limit refused it) is not removed because YouTube lacks it.
        remote.next = ActionResult.Queued("limit"); svc.add(id, listOf(v("w")))
        remote.readFails = false
        svc.syncAll()   // the drain inside sends it
        assertTrue("w" in remote.lists["PL1"]!!)
        // A failed read changes nothing.
        remote.readFails = true
        val before = ids(repo(g.id), id)
        assertNotNull(svc.syncNow().failure)
        assertEquals(before, ids(repo(g.id), id))
        // An empty answer for a playlist that holds videos is a failed read, not an emptied playlist; a cut-off listing never removes anything either.
        remote.readFails = false; val saved = remote.lists["PL1"]!!.toList(); remote.lists["PL1"] = mutableListOf()
        svc.syncNow(); assertEquals(before, ids(repo(g.id), id))
        remote.lists["PL1"] = saved.toMutableList(); remote.cut = true
        remote.lists["PL1"]!!.remove("c")
        svc.syncNow(); assertTrue("c" in ids(repo(g.id), id), "a cut-off read cannot prove c was removed")
    }

    @Test fun anAccountPlaylistIsNotBroughtBackAsACopyAfterStopSyncing() = runBlocking<Unit> {
        val g = google("UC1"); val remote = Remote(); val svc = service(remote)
        remote.lists["PL1"] = mutableListOf("a"); remote.titles["PL1"] = "Remote"
        svc.syncNow()
        val id = repo(g.id).all().single { it.remoteId == "PL1" }.id
        assertTrue(svc.setSynced(id, false).ok)
        assertEquals(null, repo(g.id).get(id)!!.remoteId); assertEquals(listOf("a"), ids(repo(g.id), id), "the videos stay")
        assertTrue(remote.calls.none { it.startsWith("delete") }, "the YouTube playlist is left alone")
        svc.syncNow()
        assertEquals(1, repo(g.id).all().count { it.name == "Remote" }, "no second copy")
    }

    @Test fun watchLaterIsOneListLocalUntilTickedAndTheMergeAddsEverythingInBothPlaces() = runBlocking<Unit> {
        val g = google("UC1"); val remote = Remote(); val svc = service(remote)
        remote.lists["WL"] = mutableListOf("r1", "r2"); remote.titles["WL"] = "Watch later"
        val wl = repo(g.id).watchLaterId()
        svc.add(wl, listOf(v("l1"), v("r1")))
        svc.syncNow()
        assertEquals(null, repo(g.id).get(wl)!!.remoteId, "a sync does not link Watch later by itself")
        assertTrue(svc.setSynced(wl, true).ok)
        assertEquals(setOf("l1", "r1", "r2"), ids(repo(g.id), wl).toSet(), "here: the union")
        assertEquals(setOf("l1", "r1", "r2"), remote.lists["WL"]!!.toSet(), "on YouTube: the union")
        assertEquals(1, repo(g.id).all().count { it.name.startsWith("Watch later") })
        assertEquals(remote.calls.filter { it.startsWith("many") || it.startsWith("add") }.single(), "many WL 1", "only what YouTube lacked was sent")
    }

    @Test fun whatDoesNotFitOnYouTubeStaysHereAndMovesToANewSyncedPlaylistOnRequest() = runBlocking<Unit> {
        val g = google("UC1"); val remote = Remote(cap = 5); val svc = service(remote, limit = 5)
        val wl = repo(g.id).watchLaterId()
        svc.add(wl, (1..8).map { v("v$it") })
        remote.titles["WL"] = "Watch later"; remote.lists["WL"] = mutableListOf()
        val r = svc.setSynced(wl, true)
        assertTrue(r.ok, r.message)
        assertEquals(5, remote.lists["WL"]!!.size, "the limit is respected")
        val p = repo(g.id).get(wl)!!
        assertEquals(3, p.overflowCount); assertEquals(8, p.itemCount, "nothing is dropped here")
        assertEquals(setOf("v6", "v7", "v8"), repo(g.id).rows(wl).filter { it.rowState == RowState.OVERFLOW }.map { it.videoId }.toSet())
        assertTrue(svc.overflowTargets(wl).isEmpty(), "no other synced playlist to offer yet")
        // "Add the rest": a new synced playlist is created and gets exactly the leftovers.
        val moved = svc.moveOverflow(wl, null)
        assertTrue(moved.ok, moved.message)
        val second = repo(g.id).all().single { it.name == "Watch later 2" }
        assertEquals("PLnew1", second.remoteId); assertEquals(setOf("v6", "v7", "v8"), remote.lists["PLnew1"]!!.toSet())
        assertEquals(0, repo(g.id).get(wl)!!.overflowCount); assertEquals(5, repo(g.id).get(wl)!!.itemCount)
    }

    @Test fun aFullAnswerFromYouTubeMakesTheVideosOverflowInsteadOfFailingOrLooping() = runBlocking<Unit> {
        val g = google("UC1"); val remote = Remote(cap = 2); val svc = service(remote)   // the service does not know the cap: only YouTube's answer says "full"
        val id = svc.create("Mine", synced = true).id!!
        svc.add(id, listOf(v("a"), v("b")))
        val r = svc.add(id, listOf(v("c")))
        assertTrue(r.ok, r.message)
        assertEquals(RowState.OVERFLOW, repo(g.id).rows(id).single { it.videoId == "c" }.rowState)
        assertEquals(0, svc.waitingChanges()); assertEquals(1, remote.calls.count { it.startsWith("many-full") }, "tried once, not retried")
        // Moving them to an existing synced playlist with room works too.
        val other = svc.create("Other", synced = true).id!!
        assertEquals(listOf(other), svc.overflowTargets(id).map { it.first.id })
        assertTrue(svc.moveOverflow(id, other).ok)
        assertEquals(listOf("c"), ids(repo(g.id), other)); assertEquals(0, repo(g.id).get(id)!!.overflowCount)
    }

    @Test fun deleteAndRenameReachTheAccountAndLikedVideosRejectsEveryWrite() = runBlocking<Unit> {
        val g = google("UC1"); val remote = Remote(); val svc = service(remote)
        remote.lists["LL"] = mutableListOf("l1"); remote.titles["LL"] = "Liked videos"
        svc.syncNow()
        val liked = repo(g.id).all().single { it.remoteId == "LL" }
        assertEquals(listOf("l1"), ids(repo(g.id), liked.id))
        assertFalse(svc.add(liked.id, listOf(v("x"))).ok); assertFalse(svc.remove(liked.id, listOf("l1")).ok)
        assertFalse(svc.rename(liked.id, "Mine").ok); assertFalse(svc.delete(liked.id).ok); assertFalse(svc.setSynced(liked.id, false).ok)
        assertTrue(remote.calls.isEmpty())
        val id = svc.create("Temp", synced = true).id!!
        assertTrue(svc.rename(id, "New name").ok); assertTrue("rename PLnew1 New name" in remote.calls)
        assertTrue(svc.delete(id).ok); assertTrue("delete PLnew1" in remote.calls); assertEquals(null, repo(g.id).get(id))
        // A refused delete changes nothing.
        val id2 = svc.create("Keep", synced = true).id!!
        remote.next = ActionResult.Failed("no"); assertFalse(svc.delete(id2).ok); assertNotNull(repo(g.id).get(id2))
    }

    @Test fun theLikeButtonUpdatesTheLocalLikedListAtOnceAndWritesNothing() = runBlocking<Unit> {
        val g = google("UC1"); val remote = Remote(); val svc = service(remote)
        svc.noteRating(v("early"), VideoRating.LIKE)   // Liked videos is not here yet: nothing happens
        assertTrue(repo(g.id).all().none { it.remoteId == "LL" })
        remote.lists["LL"] = mutableListOf("l1"); remote.titles["LL"] = "Liked videos"
        svc.syncNow()
        val liked = repo(g.id).all().single { it.remoteId == "LL" }
        svc.noteRating(v("new"), VideoRating.LIKE)
        assertEquals(listOf("new", "l1"), ids(repo(g.id), liked.id), "a like goes to the top at once")
        svc.noteRating(v("l1"), VideoRating.NONE)
        assertEquals(listOf("new"), ids(repo(g.id), liked.id), "an un-like takes it out")
        svc.noteRating(v("new"), VideoRating.DISLIKE)
        assertEquals(emptyList(), ids(repo(g.id), liked.id))
        assertTrue(remote.calls.isEmpty(), "the like button already wrote to the account; this only mirrors it")
    }

    @Test fun whenTheAccountRefusesToCreateNothingIsCreatedLocally() = runBlocking<Unit> {
        val g = google("UC1"); val remote = Remote(); val svc = service(remote)
        remote.next = ActionResult.Failed("paused")
        val r = svc.create("Refused", synced = true)
        assertFalse(r.ok); assertTrue(repo(g.id).all().none { it.name == "Refused" })
        assertNotNull(service(null).unavailableReason())
    }
}
