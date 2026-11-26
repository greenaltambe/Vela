package com.greenaltambe.vela.auth

import com.greenaltambe.vela.backup.BackupService
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.PlaylistRepository
import com.greenaltambe.vela.data.PlaylistSync
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorError
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.PlaylistContents
import com.greenaltambe.vela.extractor.PlaylistDetails
import com.greenaltambe.vela.extractor.StubExtractor
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.library.RemotePlaylist
import com.greenaltambe.vela.sync.PlaylistsDomain
import com.greenaltambe.vela.sync.SavedPlaylistsDomain
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*
import kotlin.time.Clock

/** Saved playlists: somebody else's, kept here read-only, following the original and never written to the account. */
class SavedPlaylistTest {
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val accounts = AccountManager(db, CredentialStore(InMemorySecretStore()))
    private fun v(id: String) = VideoSummary(id, "T $id", "UC", "C")

    /** The original as YouTube serves it now: its videos in pages of two, or [failure]. */
    private inner class Original(var ids: List<String> = listOf("a", "b", "c"), var title: String = "Theirs") : Extractor by StubExtractor() {
        var failure: ExtractorError? = null
        var failFrom: Int? = null
        var reads = 0
        override suspend fun playlistPage(playlistId: String, continuation: String?): ExtractorResult<PlaylistContents> {
            val start = continuation?.toInt() ?: 0
            if (start == 0) reads++
            failure?.let { return ExtractorResult.Failure(it) }
            if (failFrom?.let { start >= it } == true) return ExtractorResult.Failure(ExtractorError.Network("offline"))
            val details = if (continuation == null) PlaylistDetails(playlistId, title, "Owner", "UCowner", coverUrl = "https://i.ytimg.com/vi/a/hqdefault.jpg?sqp=x&rs=y", videoCount = ids.size.toLong()) else null
            val next = (start + 2).takeIf { it < ids.size }?.toString()
            return ExtractorResult.Success(PlaylistContents(details, ids.drop(start).take(2).map(::v), next))
        }
    }

    /** Any account write at all fails the test. */
    private class NoWrites : AccountActions {
        private fun no(): Nothing = throw AssertionError("an account write for a saved playlist")
        override suspend fun subscribe(channelId: String) = no()
        override suspend fun unsubscribe(channelId: String) = no()
        override suspend fun rate(videoId: String, rating: VideoRating) = no()
        override suspend fun addToPlaylist(playlistId: String, videoId: String) = no()
        override suspend fun removeFromPlaylist(playlistId: String, videoId: String) = no()
        override suspend fun createPlaylist(title: String) = no()
        override suspend fun renamePlaylist(playlistId: String, title: String) = no()
        override suspend fun deletePlaylist(playlistId: String) = no()
        override suspend fun savePlaylist(playlistId: String) = no()
        override suspend fun unsavePlaylist(playlistId: String) = no()
    }

    private class AccountLists(vararg val listed: String) : AccountReads {
        override suspend fun recommended(): List<VideoSummary>? = null
        override suspend fun playlists() = listed.map { AccountPlaylist(it, "Account $it", null) }
        override suspend fun playlistVideos(playlistId: String) = listOf(VideoSummary("x", "x", "UC", "C"))
    }

    private val original = Original()
    private fun service(reads: AccountReads? = null) = PlaylistService(db, accounts, NoWrites(), reads, settings = SettingsResolver(InMemorySettingsStore()), extractor = original)
    private fun lists() = PlaylistRepository(db, accounts.active.value.id)
    private fun ids(id: Long) = lists().items(id).map { it.videoId }

    private suspend fun saved(svc: PlaylistService): Long {
        val page = RemotePlaylist("PLtheirs", original, null); page.load()
        val id = svc.save(page).id!!
        withTimeout(5_000) { while (lists().get(id)!!.lastSyncedAt == null) delay(10) }
        return id
    }

    @Test fun savingStoresWhatWasReadAtOnceAndTheRestFollows() = runBlocking<Unit> {
        val svc = service()
        val id = saved(svc)
        val p = lists().get(id)!!
        assertEquals(listOf("Theirs", "PLtheirs", "Owner", "UCowner"), listOf(p.name, p.sourceId, p.ownerName, p.ownerChannelId))
        assertEquals("https://i.ytimg.com/vi/a/hqdefault.jpg", p.coverUrl, "without the signature that changes at every read")
        assertTrue(p.isSaved && p.isReadOnly && !p.isLocal && !p.isSynced)
        assertEquals(listOf("a", "b", "c"), ids(id))
        assertEquals(1, original.reads, "the page's own reading is carried on, not started again")
        val again = RemotePlaylist("PLtheirs", original, null); again.load()
        assertEquals(id, svc.save(again).id, "saving twice keeps one copy")
        assertEquals(1, lists().all().count { it.isSaved })
    }

    @Test fun aSavedPlaylistCannotBeChangedAndRemovingItTouchesNothingElse() = runBlocking<Unit> {
        val svc = service(); val id = saved(svc)
        for (r in listOf(svc.add(id, listOf(v("z"))), svc.remove(id, listOf("a")), svc.rename(id, "Mine"), svc.link(id), svc.unlink(id))) {
            assertFalse(r.ok); assertTrue("belongs to its owner" in r.message!!, r.message)
        }
        assertEquals(listOf("a", "b", "c"), ids(id)); assertEquals("Theirs", lists().get(id)!!.name)
        assertTrue(svc.overflowTargets(-1).none { it.first.id == id })
        assertTrue(svc.delete(id).ok); assertNull(lists().get(id))
    }

    @Test fun aWholeReadMakesTheCopyTheOriginalAgain() = runBlocking<Unit> {
        val svc = service(); val id = saved(svc)
        original.ids = listOf("n", "c", "a"); original.title = "Renamed by its owner"
        assertTrue(svc.refreshSaved(id).ok)
        assertEquals(listOf("n", "c", "a"), ids(id)); assertEquals("Renamed by its owner", lists().get(id)!!.name)
    }

    @Test fun aPartReadBringsNewVideosAndTakesNoneOut() = runBlocking<Unit> {
        val svc = service(); val id = saved(svc)
        original.ids = listOf("n", "a", "c", "d"); original.failFrom = 2
        val r = svc.refreshSaved(id)
        assertFalse(r.ok)
        assertEquals(listOf("n", "a", "b", "c"), ids(id))
    }

    @Test fun anOriginalThatIsGoneIsMarkedAndKeptAndAPoorConnectionMarksNothing() = runBlocking<Unit> {
        val svc = service(); val id = saved(svc)
        original.failure = ExtractorError.Network("offline")
        assertFalse(svc.refreshSaved(id).ok); assertNull(lists().get(id)!!.unavailableSince)
        original.failure = ExtractorError.Unplayable("The playlist does not exist.")
        val r = svc.refreshSaved(id)
        assertTrue("no longer available" in r.message!!)
        assertNotNull(lists().get(id)!!.unavailableSince); assertEquals(listOf("a", "b", "c"), ids(id))
        original.failure = null
        assertTrue(svc.refreshSaved(id).ok); assertNull(lists().get(id)!!.unavailableSince, "it can be read again")
    }

    @Test fun anEmptyAnswerThatDoesNotSayItIsEmptyKeepsTheCopy() = runBlocking<Unit> {
        val svc = service(); val id = saved(svc)
        val silent = object : Extractor by StubExtractor() {
            var count: Long? = null
            override suspend fun playlistPage(playlistId: String, continuation: String?) = ExtractorResult.Success(PlaylistContents(PlaylistDetails(playlistId, "Theirs", videoCount = count), emptyList()))
        }
        svc.refreshSaved(id, RemotePlaylist("PLtheirs", silent, null)); assertEquals(3, ids(id).size)
        silent.count = 0
        assertTrue(svc.refreshSaved(id, RemotePlaylist("PLtheirs", silent, null)).ok); assertTrue(ids(id).isEmpty())
    }

    @Test fun anOlderReadingDoesNotUndoALaterOne() = runBlocking<Unit> {
        val svc = service(); val id = saved(svc)
        val old = RemotePlaylist("PLtheirs", original, null); old.loadAll()
        delay(20)
        original.ids = listOf("n"); assertTrue(svc.refreshSaved(id).ok)
        svc.refreshSaved(id, old)
        assertEquals(listOf("n"), ids(id))
    }

    @Test fun openingItReadsItAgainOnlyWhenTheCopyIsOld() = runBlocking<Unit> {
        val svc = service(); val id = saved(svc)
        assertNull(svc.refreshSavedIfStale(id)); assertEquals(1, original.reads)
        lists().markRefreshed(id, Clock.System.now() - kotlin.time.Duration.parse("1h"))
        assertNotNull(svc.refreshSavedIfStale(id)); assertEquals(2, original.reads)
    }

    @Test fun librarySyncRefreshesItAndDoesNotAlsoImportItAsOneOfYourOwn() = runBlocking<Unit> {
        accounts.addGoogle(GoogleIdentity("P", "p@example.com", null, "UC1"), CookieJar(listOf(Cookie("SAPISID", "s", ".youtube.com"))))
        val svc = service(AccountLists("PLtheirs", "PLmine")); val id = saved(svc)
        original.ids = listOf("a", "q")
        assertNull(svc.syncNow().failure)
        assertEquals(listOf("PLtheirs"), lists().all().filter { it.sourceId == "PLtheirs" || it.remoteId == "PLtheirs" }.map { it.sourceId })
        assertEquals(listOf("a", "q"), ids(id))
        assertEquals(PlaylistSync.SYNCED, lists().byRemote("PLmine")!!.syncState)
    }

    @Test fun backupAndDeviceSyncCarryWhichPlaylistItIsAndNotItsVideos() = runBlocking<Unit> {
        val svc = service(); saved(svc)
        val account = accounts.active.value.id
        lists().create("Theirs", Clock.System.now())   // a local list of the same name stays apart
        val text = BackupService(db).create()
        assertTrue("\"PLtheirs\"" in text && "T a" !in text, text.take(400))
        val fresh = createDatabase(JvmDatabaseDriverFactory())
        assertEquals(listOf("Theirs", "Theirs"), BackupService(fresh).restore(text).newPlaylists)
        BackupService(fresh).restore(text)
        val restored = PlaylistRepository(fresh).all().filter { !it.isWatchLater }
        assertEquals(1, restored.count { it.isSaved }); assertEquals(1, restored.count { it.isLocal }); assertEquals(0, restored.single { it.isSaved }.itemCount)

        val domain = SavedPlaylistsDomain(db, { Clock.System.now() }, account)
        val values = domain.read()
        assertEquals(1, values.size); assertTrue(PlaylistsDomain(db, { Clock.System.now() }, account).read().values.none { "T a" in it })
        val other = createDatabase(JvmDatabaseDriverFactory())
        val there = SavedPlaylistsDomain(other, { Clock.System.now() }, account)
        there.apply(values)
        assertEquals(values, there.read(), "it reads back as it arrived, so nothing ping-pongs")
        // Synced there as the viewer's own: it is still added, so that it does not read as removed.
        val third = createDatabase(JvmDatabaseDriverFactory())
        val repo = PlaylistRepository(third, account); repo.linked("PLtheirs", "Theirs", Clock.System.now())
        val onThird = SavedPlaylistsDomain(third, { Clock.System.now() }, account)
        onThird.apply(values); assertEquals(values, onThird.read())
        there.apply(values.mapValues { null }); assertTrue(there.read().isEmpty())
    }
}
