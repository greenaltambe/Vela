package com.greenaltambe.vela.auth

import com.greenaltambe.vela.core.settings.AccountHistorySync
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.AccountWatchedRepository
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Task 83, phase 3: the account's watch history is read into AccountWatched, incrementally, per account, and a failure keeps what is stored. */
class AccountHistoryServiceTest {
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val accounts = AccountManager(db, CredentialStore(InMemorySecretStore()))
    private val settings = SettingsResolver(InMemorySettingsStore())
    private val jar = CookieJar(listOf(Cookie("SAPISID", "secret-value", ".youtube.com")))
    private fun google(channel: String) = accounts.addGoogle(GoogleIdentity("P", "$channel@example.com", null, channel), jar)
    private fun v(id: String, percent: Int? = null) = VideoSummary(id, "T $id", "UC", "C", watchedPercent = percent)

    private class Pages(var pages: List<List<VideoSummary>>?) : AccountReads {
        var requests = 0
        override suspend fun recommended() = null
        override suspend fun playlists() = null
        override suspend fun playlistVideos(playlistId: String) = null
        override suspend fun historyPage(continuation: String?): HistoryPage? {
            requests++
            val all = pages ?: return null
            val i = continuation?.toInt() ?: 0
            return HistoryPage(all[i], if (i + 1 < all.size) (i + 1).toString() else null)
        }
    }

    private class Ticking(var now: Instant) : Clock { override fun now() = now }

    private fun service(reads: Pages?, clock: Clock = Clock.System, max: Int = 200) = AccountHistoryService(reads, db, accounts, settings, clock, maxEntries = max)

    @Test fun readsPagesIntoTheTableWithPercentsAndKeepsTheOrder() = runBlocking {
        val g = google("UC1")
        val reads = Pages(listOf(listOf(v("a", 100), v("b", 40)), listOf(v("c"))))
        val s = service(reads).sync()
        assertEquals(HistorySyncStatus.Done(3, 3), s)
        val rows = AccountWatchedRepository(db, g.id).recent()
        assertEquals(listOf("a", "b", "c"), rows.map { it.videoId })
        assertEquals(listOf(100, 40, null), rows.map { it.percent })
    }

    @Test fun anIncrementalRunStopsAtThePageThatReachesAStoredEntry() = runBlocking {
        google("UC1")
        val first = Pages(listOf(listOf(v("a"), v("b")), listOf(v("c"), v("d"))))
        service(first).sync()
        // Later the account has one new video on top; page 1 already contains stored entries, so page 2 is never requested.
        val later = Pages(listOf(listOf(v("new"), v("a")), listOf(v("c"), v("d")), listOf(v("zzz"))))
        val s = service(later).sync()
        assertEquals(1, later.requests, "stopped after the first page")
        assertEquals(HistorySyncStatus.Done(1, 2), s)
    }

    @Test fun readsAtMostMaxEntries() = runBlocking {
        val g = google("UC1")
        val reads = Pages(listOf((1..5).map { v("p1-$it") }, (1..5).map { v("p2-$it") }, (1..5).map { v("p3-$it") }))
        service(reads, max = 7).sync()
        assertEquals(7, AccountWatchedRepository(db, g.id).recent().size)
        assertEquals(2, reads.requests)
    }

    @Test fun aSecondAccountDoesNotSeeTheFirstOnesRowsAndRemovingDeletesThem() = runBlocking {
        val g1 = google("UC1")
        service(Pages(listOf(listOf(v("a"), v("b"))))).sync()
        val g2 = google("UC2")
        assertEquals(g2.id, accounts.active.value.id)
        assertTrue(AccountWatchedRepository(db, g2.id).recent().isEmpty())
        assertEquals(2, AccountWatchedRepository(db, g1.id).recent().size)
        accounts.remove(g1.id)
        assertTrue(AccountWatchedRepository(db, g1.id).recent().isEmpty(), "deleted with the account")
    }

    @Test fun aFailedReadKeepsTheStoredDataAndSaysFailed() = runBlocking {
        val g = google("UC1")
        service(Pages(listOf(listOf(v("a"))))).sync()
        val failing = Pages(null)
        val svc = service(failing)
        assertEquals(HistorySyncStatus.Failed, svc.sync())
        assertEquals(HistorySyncStatus.Failed, svc.status.value)
        assertEquals(listOf("a"), AccountWatchedRepository(db, g.id).recent().map { it.videoId })
    }

    @Test fun settingOffMakesNoRequestAndNeitherDoesTheLocalAccount() = runBlocking {
        val reads = Pages(listOf(listOf(v("a"))))
        settings.set(Settings.historyAccountSync, AccountHistorySync.OFF)
        google("UC1")
        service(reads).sync()
        assertEquals(0, reads.requests, "setting off")
        settings.set(Settings.historyAccountSync, AccountHistorySync.MANUAL)
        accounts.switchTo(com.greenaltambe.vela.data.LOCAL_ACCOUNT)
        service(reads).sync()
        assertEquals(0, reads.requests, "nobody signed in")
    }

    @Test fun scheduledRunsFollowTheSettingAndTheTimeOfTheLastRead() = runBlocking {
        google("UC1")
        val clock = Ticking(Instant.fromEpochSeconds(1_700_000_000))
        val reads = Pages(listOf(listOf(v("a"))))
        val svc = service(reads, clock)
        // MANUAL (the default): only an explicit read.
        assertNull(svc.syncIfDue(HistorySyncTrigger.START)); assertNull(svc.syncIfDue(HistorySyncTrigger.PERIODIC)); assertEquals(0, reads.requests)
        settings.set(Settings.historyAccountSync, AccountHistorySync.EVERY_6H)
        assertNotNull(svc.syncIfDue(HistorySyncTrigger.PERIODIC)); assertEquals(1, reads.requests)
        clock.now += 5.hours
        assertNull(svc.syncIfDue(HistorySyncTrigger.PERIODIC), "not yet six hours"); assertEquals(1, reads.requests)
        clock.now += 2.hours
        assertNotNull(svc.syncIfDue(HistorySyncTrigger.PERIODIC)); assertEquals(2, reads.requests)
    }

    @Test fun onStartRunsOncePerProcess() = runBlocking {
        google("UC1")
        settings.set(Settings.historyAccountSync, AccountHistorySync.ON_START)
        val reads = Pages(listOf(listOf(v("a"))))
        val svc = service(reads)
        assertNotNull(svc.syncIfDue(HistorySyncTrigger.START))
        assertNull(svc.syncIfDue(HistorySyncTrigger.START))
        assertNull(svc.syncIfDue(HistorySyncTrigger.PERIODIC), "ON_START has no timer")
        assertEquals(1, reads.requests)
    }

    @Test fun localOnlyHistoryDoesNotStopReading() = runBlocking {
        val g = google("UC1")
        settings.set(Settings.localOnlyHistory, true)
        service(Pages(listOf(listOf(v("a"))))).sync()
        assertEquals(1, AccountWatchedRepository(db, g.id).recent().size)
    }
}
