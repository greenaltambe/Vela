package com.greenaltambe.vela.auth

import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.PlaylistOpRepository
import com.greenaltambe.vela.data.PlaylistRepository
import com.greenaltambe.vela.data.PlaylistSync
import com.greenaltambe.vela.data.RowState
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.PlaylistContents
import com.greenaltambe.vela.extractor.PlaylistDetails
import com.greenaltambe.vela.extractor.StubExtractor
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.library.RemotePlaylist
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.time.Clock

/** Saved playlists and the account's YouTube library: saving there, removing there, and telling other people's lists from the viewer's own. */
class SavedPlaylistAccountTest {
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val accounts = AccountManager(db, CredentialStore(InMemorySecretStore()))
    private val me = accounts.addGoogle(GoogleIdentity("P", "p@example.com", null, "UCme"), CookieJar(listOf(Cookie("SAPISID", "s", ".youtube.com"))))
    private fun v(id: String) = VideoSummary(id, "T $id", "UC", "C")

    /** One list of the account's YouTube library. [saved] is what its page says about being saved; [owner] whose it is. */
    private data class Theirs(val videos: List<String>, val owner: String = "UCother", val canBeSaved: Boolean? = true, var saved: Boolean? = true)

    private class YouTube : AccountActions, AccountReads {
        val writes = mutableListOf<String>()
        var answer: ActionResult = ActionResult.Ok
        /** What the library lists. */
        val library = linkedMapOf<String, Theirs>()
        /** Pages that can be read although the library does not list them. */
        val elsewhere = mutableMapOf<String, Theirs>()
        val unreadable = mutableSetOf<String>()
        private fun write(call: String, then: () -> Unit = {}): ActionResult { writes += call; if (answer is ActionResult.Ok) then(); return answer }
        override suspend fun subscribe(channelId: String) = ActionResult.Ok
        override suspend fun unsubscribe(channelId: String) = ActionResult.Ok
        override suspend fun rate(videoId: String, rating: VideoRating) = ActionResult.Ok
        override suspend fun addToPlaylist(playlistId: String, videoId: String) = write("add $playlistId $videoId")
        override suspend fun removeFromPlaylist(playlistId: String, videoId: String) = write("remove $playlistId $videoId")
        override suspend fun createPlaylist(title: String): Pair<ActionResult, String?> = write("create $title") to "PLnew"
        override suspend fun renamePlaylist(playlistId: String, title: String) = write("rename $playlistId")
        override suspend fun deletePlaylist(playlistId: String) = write("delete $playlistId")
        override suspend fun savePlaylist(playlistId: String) = write("save $playlistId") { elsewhere.remove(playlistId)?.let { it.saved = true; library[playlistId] = it } }
        override suspend fun unsavePlaylist(playlistId: String) = write("unsave $playlistId") { library.remove(playlistId)?.let { it.saved = false; elsewhere[playlistId] = it } }

        override suspend fun recommended(): List<VideoSummary>? = null
        override suspend fun playlists() = library.keys.map { AccountPlaylist(it, "List $it", null) }
        private fun page(id: String) = if (id in unreadable) null else library[id] ?: elsewhere[id]
        private fun details(id: String, p: Theirs) = PlaylistDetails(id, "List $id", "Owner", p.owner, videoCount = p.videos.size.toLong(), canBeSaved = p.canBeSaved, savedOnAccount = p.saved)
        override suspend fun playlistVideos(playlistId: String) = page(playlistId)?.videos?.map { VideoSummary(it, "T $it", "UC", "C") }
        override suspend fun playlistListing(playlistId: String) = page(playlistId)?.let { PlaylistListing(it.videos.map { v -> VideoSummary(v, "T $v", "UC", "C") }, true, details(playlistId, it)) }
        override suspend fun playlistPage(playlistId: String, continuation: String?) = page(playlistId)?.let { PlaylistContents(details(playlistId, it), it.videos.map { v -> VideoSummary(v, "T $v", "UC", "C") }) }
    }

    private val youtube = YouTube()
    /** The original read without the account, as the saved copy's refresh does. */
    private val anonymous = object : Extractor by StubExtractor() {
        override suspend fun playlistPage(playlistId: String, continuation: String?): ExtractorResult<PlaylistContents> =
            ExtractorResult.Success(PlaylistContents(PlaylistDetails(playlistId, "List $playlistId", "Owner", "UCother", videoCount = 2), listOf(v("a"), v("b"))))
    }
    private val service = PlaylistService(db, accounts, youtube, youtube, settings = SettingsResolver(InMemorySettingsStore()), extractor = anonymous)
    private val lists get() = PlaylistRepository(db, me.id)
    private val ops get() = PlaylistOpRepository(db, me.id)
    private suspend fun page(id: String) = RemotePlaylist(id, anonymous, null).also { it.load() }
    private fun saved(id: String) = lists.bySource(id)

    // ---- saving and removing ----

    @Test fun savingWithTheBoxTickedPutsItInTheAccountsLibraryAndWithoutItDoesNot() = runBlocking<Unit> {
        youtube.elsewhere["PLa"] = Theirs(listOf("a", "b"), saved = false); youtube.elsewhere["PLb"] = Theirs(listOf("a"), saved = false)
        assertTrue(service.save(page("PLa"), onAccount = true).ok)
        assertTrue(service.save(page("PLb"), onAccount = false).ok)
        assertEquals(listOf("save PLa"), youtube.writes)
        assertEquals(true to false, saved("PLa")!!.onAccount to saved("PLb")!!.onAccount)
    }

    @Test fun whenYouTubeRefusesNothingIsSaved() = runBlocking<Unit> {
        youtube.answer = ActionResult.Failed("Account traffic is paused.")
        val r = service.save(page("PLa"), onAccount = true)
        assertFalse(r.ok); assertTrue("Account traffic is paused." in r.message!!)
        assertNull(saved("PLa")); assertEquals(0, ops.count())
        // Already on this device: a refusal leaves it as it was.
        youtube.answer = ActionResult.Ok; service.save(page("PLa"))
        youtube.answer = ActionResult.Failed("no")
        assertFalse(service.setSynced(saved("PLa")!!.id, true).ok)
        assertEquals(false, saved("PLa")!!.onAccount); assertEquals(0, ops.count())
    }

    @Test fun aSaveTheHourlyLimitHeldBackIsSentLater() = runBlocking<Unit> {
        youtube.elsewhere["PLa"] = Theirs(listOf("a"), saved = false)
        youtube.answer = ActionResult.Queued("after 5:00")
        val r = service.save(page("PLa"), onAccount = true)
        assertTrue(r.ok && r.pending); assertEquals(true, saved("PLa")!!.onAccount); assertEquals(1, ops.count())
        // A sync while it waits does not take the playlist away, although YouTube does not list it yet.
        service.syncNow(); assertNotNull(saved("PLa"))
        youtube.answer = ActionResult.Ok; service.drain()
        assertEquals(0, ops.count()); assertTrue("PLa" in youtube.library)
    }

    @Test fun uncheckingTheBoxLeavesYouTubeAloneAndCheckingItSavesThere() = runBlocking<Unit> {
        youtube.library["PLa"] = Theirs(listOf("a"))
        service.syncNow()
        val id = saved("PLa")!!.id
        assertTrue(service.setSynced(id, false).ok)
        assertEquals(emptyList(), youtube.writes); assertEquals(false, saved("PLa")!!.onAccount)
        assertTrue(service.setSynced(id, true).ok)
        assertEquals(listOf("save PLa"), youtube.writes); assertEquals(true, saved("PLa")!!.onAccount)
    }

    @Test fun removingFromTheAccountTooSendsItAndARefusalKeepsThePlaylist() = runBlocking<Unit> {
        youtube.library["PLa"] = Theirs(listOf("a"))
        service.syncNow(); val id = saved("PLa")!!.id
        youtube.answer = ActionResult.Failed("no")
        assertFalse(service.delete(id, fromAccount = true).ok); assertNotNull(saved("PLa"))
        youtube.answer = ActionResult.Ok
        assertTrue(service.delete(id, fromAccount = true).ok)
        assertNull(saved("PLa")); assertTrue("PLa" !in youtube.library)
        // Saved again on youtube.com later: it comes back.
        youtube.savePlaylist("PLa"); service.syncNow(); assertNotNull(saved("PLa"))
    }

    @Test fun aRemovalThatWaitsIsNotUndoneByASync() = runBlocking<Unit> {
        youtube.library["PLa"] = Theirs(listOf("a"))
        service.syncNow()
        youtube.answer = ActionResult.Queued("later")
        assertTrue(service.delete(saved("PLa")!!.id, fromAccount = true).pending)
        service.syncNow(); assertNull(saved("PLa"))
        youtube.answer = ActionResult.Ok; service.drain()
        assertEquals(0, ops.count()); assertTrue("PLa" !in youtube.library)
    }

    @Test fun removingItHereOnlyWritesNothingAndASyncDoesNotBringItBack() = runBlocking<Unit> {
        youtube.library["PLa"] = Theirs(listOf("a")); youtube.library["PLb"] = Theirs(listOf("a"))
        service.syncNow()
        assertTrue(service.delete(saved("PLa")!!.id).ok)
        // PLb: kept on this device only, seen in the library, then removed.
        service.setSynced(saved("PLb")!!.id, false); service.syncNow(); service.delete(saved("PLb")!!.id)
        service.syncNow()
        assertNull(saved("PLa")); assertNull(saved("PLb")); assertEquals(emptyList(), youtube.writes)
    }

    // ---- library sync ----

    @Test fun otherPeoplesListsComeInAsSavedAndYourOwnAsSynced() = runBlocking<Unit> {
        youtube.library["PLother"] = Theirs(listOf("a", "b"))
        youtube.library["PLmine"] = Theirs(listOf("c"), owner = "UCme", canBeSaved = false, saved = null)
        youtube.library["PLmineButOffersSave"] = Theirs(listOf("d"), owner = "UCme")
        youtube.library["PLunknown"] = Theirs(listOf("e"), canBeSaved = null, saved = null)
        youtube.library["PLbroken"] = Theirs(listOf("f")); youtube.unreadable += "PLbroken"
        youtube.library["PLjustRemoved"] = Theirs(listOf("g"), saved = false)
        assertNull(service.syncNow().failure)
        val theirs = saved("PLother")!!
        assertEquals(listOf(true, null, "Owner"), listOf(theirs.onAccount, theirs.remoteId, theirs.ownerName))
        assertEquals(listOf("a", "b"), lists.items(theirs.id).map { it.videoId })
        for (own in listOf("PLmine", "PLmineButOffersSave", "PLunknown")) assertEquals(PlaylistSync.SYNCED, lists.byRemote(own)!!.syncState, own)
        assertTrue(lists.all().none { it.sourceId in listOf("PLbroken", "PLjustRemoved") || it.remoteId in listOf("PLbroken", "PLjustRemoved") })
        assertEquals(emptyList(), youtube.writes)
    }

    @Test fun aListTakenForYourOwnEarlierBecomesASavedOneWithoutAnyWrite() = runBlocking<Unit> {
        youtube.library["PLa"] = Theirs(listOf("a", "b"))
        val id = lists.linked("PLa", "List PLa", Clock.System.now())
        lists.add(id, listOf(v("a")), Clock.System.now(), RowState.SYNCED)
        // The viewer had tried to add a video, and the write still waits.
        lists.add(id, listOf(v("mine")), Clock.System.now(), RowState.PENDING_ADD); ops.enqueueAdd(id, "mine", Clock.System.now())
        youtube.answer = ActionResult.Failed("not yours")
        service.syncNow()
        val now = lists.get(id)!!
        assertEquals(listOf("PLa", null, true), listOf(now.sourceId, now.remoteId, now.onAccount))
        assertTrue(now.isSaved && now.isReadOnly)
        assertEquals(listOf("a", "b"), lists.items(id).map { it.videoId })
        assertEquals(0, ops.count())
        val kept = lists.all().single { it.name == "List PLa (your additions)" }
        assertEquals(listOf("mine"), lists.items(kept.id).map { it.videoId }); assertTrue(kept.isLocal)
    }

    @Test fun aListSavedHereAndAlsoTakenForYourOwnBecomesOne() = runBlocking<Unit> {
        youtube.library["PLa"] = Theirs(listOf("a"))
        val synced = lists.linked("PLa", "List PLa", Clock.System.now())
        val copy = lists.createSaved(com.greenaltambe.vela.data.SavedSource("PLa", "List PLa"), Clock.System.now())
        assertNull(service.syncNow().failure)
        assertNull(lists.get(synced)); assertEquals(true, lists.get(copy)!!.onAccount)
        assertEquals(1, lists.all().count { it.sourceId == "PLa" || it.remoteId == "PLa" })
        assertEquals(emptyList(), youtube.writes)
    }

    @Test fun takenOutOfTheLibraryOnYouTubeItLeavesOnlyWhenItsOwnPageSaysSo() = runBlocking<Unit> {
        youtube.library["PLa"] = Theirs(listOf("a")); youtube.library["PLb"] = Theirs(listOf("a")); youtube.library["PLc"] = Theirs(listOf("a"))
        service.syncNow()
        youtube.elsewhere["PLdevice"] = Theirs(listOf("a"), saved = false); service.save(page("PLdevice"))
        // All three drop out of the listing: A's page says not saved, B's cannot be read, C's still says saved.
        youtube.unsavePlaylist("PLa"); youtube.writes.clear()
        youtube.library.remove("PLb"); youtube.unreadable += "PLb"
        youtube.elsewhere["PLc"] = youtube.library.remove("PLc")!!
        service.syncNow()
        assertNull(saved("PLa")); assertNotNull(saved("PLb")); assertNotNull(saved("PLc"))
        assertNotNull(saved("PLdevice"), "kept on this device whatever the account says")
        assertEquals(emptyList(), youtube.writes)
    }
}
