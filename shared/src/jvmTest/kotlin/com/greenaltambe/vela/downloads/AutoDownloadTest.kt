package com.greenaltambe.vela.downloads

import com.greenaltambe.vela.core.http.FakeTransport
import com.greenaltambe.vela.core.http.HttpResponse
import kotlinx.coroutines.cancel
import com.greenaltambe.vela.core.network.FixedNetworkMonitor
import com.greenaltambe.vela.core.network.NetworkType
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.*
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.modules.createModules
import com.greenaltambe.vela.player.StreamLeases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AutoDownloadTest {
    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val clock = object : Clock { override fun now() = now }
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val storage = FakeStorage()
    private val fetcher = FakeFetcher()
    private val settings = SettingsResolver(InMemorySettingsStore())
    private val network = FixedNetworkMonitor(NetworkType.UNMETERED)
    private val base = FakeExtractor(bytesOf(3500, 1), bytesOf(1200, 2), fetcher)
    private var uploads = emptyList<VideoSummary>()
    private var listFails = false
    private val extractor = object : Extractor by base {
        override suspend fun recentUploads(channelId: String): ExtractorResult<List<VideoSummary>> =
            if (listFails) ExtractorResult.Failure(ExtractorError.Network("down")) else ExtractorResult.Success(uploads)
    }
    private val merger = FakeMerger().also { it.storage = storage }
    private val notifier = RecordingSummaryNotifier()
    private val modules = createModules(FakeTransport { HttpResponse(404, "") }, db, settings)
    private val repo = DownloadRepository(db)
    private val store = AutoDownloadStore(SqlDelightSettingsStore(db))
    private val scopes = mutableListOf<CoroutineScope>()

    private class RecordingSummaryNotifier : DownloadNotifier {
        val summaries = mutableListOf<String>()
        override fun completed(title: String) {}
        override fun failed(title: String, reason: String) {}
        override fun summary(text: String) { summaries += text }
    }

    private fun video(id: String, daysAgo: Int) = VideoSummary(id, "Title $id", "UC1", "Chan", uploadedAt = now - daysAgo.days)

    private fun TestScope.setup(): Pair<DownloadManager, AutoDownloader> {
        val m = DownloadManager(repo, StreamLeases(extractor), { storage }, fetcher, merger, OfflineCollector(extractor, modules, fetcher, { storage }, settings),
            settings, network, CoroutineScope(coroutineContext + Job()).also { scopes += it }, RecordingNotifier(), chunkBytes = 1000, maxRetries = 1, sleep = {})
        m.start(); runCurrent()
        return m to AutoDownloader(store, extractor, m, repo, { PlaylistRepository(db) }, { HistoryLibrary(db) }, network, settings, clock, notifier)
    }

    private fun test(body: suspend TestScope.() -> Unit) = runTest { try { body() } finally { scopes.forEach { it.cancel() } } }

    @Test fun onlyNewVideosAfterTheRuleWasCreatedAreQueuedOnceAndSummarisedOnce() = test {
        store.upsert(AutoDownloadRule("UC1", "Chan", sinceMs = (now - 2.days).toEpochMilliseconds()))
        uploads = listOf(video("old", 10), video("new1", 1), video("new2", 0))
        val (m, auto) = setup()
        val r = auto.run(); advanceUntilIdle()
        assertEquals(setOf("new1", "new2"), r.queued.map { it.id }.toSet(), "the backlog is not downloaded")
        assertEquals(listOf("Automatic downloads: 2 new videos queued"), notifier.summaries, "one summary, not one per video")
        assertTrue(m.items.value.all { it.state == DownloadState.COMPLETED })
        val again = auto.run(); advanceUntilIdle()
        assertTrue(again.queued.isEmpty(), "nothing is fetched twice"); assertEquals(1, notifier.summaries.size)
        m.items.value.forEach { m.remove(it.id) }; advanceUntilIdle()
        assertTrue(auto.run().queued.isEmpty(), "a download the viewer deleted does not come back")
    }

    @Test fun wifiOnlyWaitsForUnmeteredAndAFailedCheckIsReportedNotFatal() = test {
        store.upsert(AutoDownloadRule("UC1", "Chan", wifiOnly = true))
        uploads = listOf(video("a", 0))
        val (_, auto) = setup()
        network.set(NetworkType.METERED)
        val waiting = auto.run(); assertTrue(waiting.queued.isEmpty()); assertTrue(waiting.notes.any { "Wi-Fi" in it })
        network.set(NetworkType.UNMETERED); listFails = true
        assertTrue(auto.run().notes.any { "could not check" in it })
        listFails = false
        assertEquals(1, auto.run().queued.size)
    }

    @Test fun theAccountIsNeverTheSourceAndDisabledRulesAreIgnored() = test {
        store.upsert(AutoDownloadRule("UC1", "Chan", enabled = false)); uploads = listOf(video("a", 0))
        val (_, auto) = setup()
        assertTrue(auto.run().queued.isEmpty())
    }

    @Test fun watchLaterVideosAreFetchedAndTheStorageCapRemovesOldestWatchedAutomaticDownloadsFirst() = test {
        settings.set(Settings.downloadsStorageCapGb, 0.45f)         // 450 MB: room for two of the 200 MB estimates
        settings.set(Settings.autoDownloadDeleteDays, 0f)           // isolate the cap rule from the delete-after-watched rule
        val (m, auto) = setup()
        val pl = PlaylistRepository(db); pl.add(pl.watchLaterId(), listOf(video("w1", 0), video("w2", 0), video("w3", 0)), now)
        store.upsert(AutoDownloadRule(AutoDownloadRule.WATCH_LATER, "Watch later", wifiOnly = false))
        // Pretend two earlier automatic downloads are finished (200 MB each) and w1 was watched earlier than w2.
        for ((id, at) in listOf("w1" to 1L, "w2" to 2L)) {
            val row = repo.insert(DownloadRequest(video(id, 0)), StoredPlan(), "720p"); repo.setProgress(row, 200_000_000, 200_000_000); repo.setState(row, DownloadState.COMPLETED, null)
            store.markAuto(id); store.markSeen(id)
            val h = SqlDelightWatchHistoryRepository(db); val hid = h.begin("local", id, "UC1", id, Instant.fromEpochMilliseconds(at), 1, 1, 1); h.update(hid, Instant.fromEpochMilliseconds(at), 1, 1, 1)
        }
        m.reload(); runCurrent()
        val r = auto.run(); advanceUntilIdle()
        assertEquals(listOf("w3"), r.queued.map { it.id })
        val remaining = m.items.value.filter { it.state == DownloadState.COMPLETED }.map { it.videoId }
        assertFalse("w1" in remaining, "the oldest-watched automatic download made room"); assertTrue("w2" in remaining)
    }

    @Test fun watchedAutomaticDownloadsAreDeletedAfterTheDelayButManualOnesNever() = test {
        settings.set(Settings.autoDownloadDeleteDays, 3f)
        val (m, auto) = setup()
        val auto1 = repo.insert(DownloadRequest(video("auto1", 0)), StoredPlan(), "720p"); repo.setState(auto1, DownloadState.COMPLETED, null)
        val manual = repo.insert(DownloadRequest(video("manual", 0)), StoredPlan(), "720p"); repo.setState(manual, DownloadState.COMPLETED, null)
        val fresh = repo.insert(DownloadRequest(video("fresh", 0)), StoredPlan(), "720p"); repo.setState(fresh, DownloadState.COMPLETED, null)
        store.markAuto("auto1"); store.markAuto("fresh")
        val h = SqlDelightWatchHistoryRepository(db)
        for ((id, ago) in listOf("auto1" to 5, "manual" to 5, "fresh" to 1)) { val t = now - ago.days; val hid = h.begin("local", id, "UC1", id, t, 1, 1, 1); h.update(hid, t, 1, 1, 1) }
        m.reload(); runCurrent()
        val r = auto.run()
        assertEquals(1, r.describe().let { Regex("Removed (\\d+)").find(it)?.groupValues?.get(1)?.toInt() })
        val left = m.items.value.map { it.videoId }.toSet()
        assertEquals(setOf("manual", "fresh"), left, "only the old, watched, automatic one went")
    }
}
