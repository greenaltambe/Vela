package com.greenaltambe.vela.downloads

import com.greenaltambe.vela.core.http.FakeTransport
import com.greenaltambe.vela.core.http.HttpResponse
import com.greenaltambe.vela.core.http.TransportException
import com.greenaltambe.vela.core.network.FixedNetworkMonitor
import com.greenaltambe.vela.core.network.NetworkType
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.DownloadKind
import com.greenaltambe.vela.data.DownloadState
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.ExtractorError
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.modules.createModules
import com.greenaltambe.vela.player.StreamLeases
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val CHUNK = 1000L

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadManagerTest {
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val storage = FakeStorage()
    private val fetcher = FakeFetcher()
    private val settings = SettingsResolver(InMemorySettingsStore())
    private val network = FixedNetworkMonitor()
    private val video = bytesOf(3500, 1)
    private val audio = bytesOf(1200, 2)
    private val extractor = FakeExtractor(video, audio, fetcher)
    private val merger = FakeMerger().also { it.storage = storage }
    private val notifier = RecordingNotifier()
    private val modules = createModules(FakeTransport { HttpResponse(404, "") }, db, settings)
    private val repo = DownloadRepository(db)
    private val slept = mutableListOf<Duration>()

    private fun manager(scope: CoroutineScope, maxRetries: Int = 5, mergerOverride: FakeMerger = merger) = DownloadManager(
        repo, StreamLeases(extractor), { storage }, fetcher, mergerOverride,
        OfflineCollector(extractor, modules, fetcher, { storage }, settings),
        settings, network, scope, notifier, chunkBytes = CHUNK, maxRetries = maxRetries, sleep = { slept += it },
    )

    private val scopes = mutableListOf<CoroutineScope>()

    /** A normal scope (not backgroundScope): advanceUntilIdle() only waits for foreground work. Cancelled by [test]. */
    private fun TestScope.managerScope() = CoroutineScope(coroutineContext + Job()).also { scopes += it }

    /** runTest that always cancels the managers' scopes: a leaked ticker would otherwise make a failed test spin instead of fail. */
    private fun test(body: suspend TestScope.() -> Unit) = runTest {
        try { body() } finally { scopes.forEach { it.cancel() } }
    }

    private fun TestScope.started(m: DownloadManager = manager(managerScope())): DownloadManager { m.start(); runCurrent(); return m }

    private fun DownloadManager.state(id: Long) = items.value.first { it.id == id }.state
    private fun summary(id: String) = VideoSummary(id, "Title $id", "UCc", "Chan")

    // ---- the happy path ----

    @Test fun separateStreamsAreDownloadedInRangesMergedAndPublished() = test {
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        advanceUntilIdle()

        assertEquals(DownloadState.COMPLETED, m.state(id), m.items.value.first().error)
        val job = merger.jobs.single()
        assertEquals(Container.MP4, job.container, "H.264 + AAC")
        val url = fetcher.requests.filter { it.url.startsWith("https://v/") }
        assertEquals(listOf(0L, 1000L, 2000L, 3000L), url.map { it.start }, "3500 bytes in 1000-byte ranges")
        assertEquals(listOf(999L, 1999L, 2999L, 3499L), url.map { it.end }, "the last range is clamped to the file")
        val published = storage.published.entries.single()
        assertEquals("/library/A_ video_with_bad_chars [vid1].mp4", published.key)
        assertContentEquals(video + audio, published.value, "every byte, in order, from both streams")
        assertTrue(storage.work.isEmpty(), "partial and part files are gone")
        assertEquals(1.0f, m.items.value.single().progress)
        assertEquals(listOf("A: video/with*bad?chars"), notifier.completed)
    }

    @Test fun partNamesAreUsedWhileDownloadingAndFinalNamesAfter() = test {
        fetcher.gate = CompletableDeferred()
        val m = started()
        m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        runCurrent()
        assertTrue(storage.work.keys.single().endsWith(".part"), storage.work.keys.toString())
        fetcher.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(storage.work.isEmpty())
    }

    @Test fun audioOnlyDownloadsOneStreamAndKeepsItsContainer() = test {
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, audioOnly = true))
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id))
        assertEquals(DownloadKind.AUDIO_ONLY, m.items.value.single().kind)
        assertTrue(merger.jobs.isEmpty(), "nothing to merge")
        assertTrue(fetcher.requests.none { it.url.startsWith("https://v/") }, "no video bytes requested")
        assertEquals("/library/A_ video_with_bad_chars [vid1] (audio).m4a", storage.published.keys.single())
        assertContentEquals(audio, storage.published.values.single())
    }

    // ---- plan selection ----

    @Test fun heightCapAndCodecPreferenceDecideTheStreamsAndTheContainer() = test {
        val m = started()
        m.enqueue(DownloadRequest(summary("a"), maxHeight = 720))
        advanceUntilIdle()
        // VP9 720p + AAC audio: no standard container holds both, so mkv.
        assertEquals(Container.MKV, merger.jobs.single().container)
        assertEquals(720, repo.plan(repo.find("a", DownloadKind.VIDEO)!!).streams.first { it.role == "video" }.height)

        m.enqueue(DownloadRequest(summary("b"), preferAvc = true))
        advanceUntilIdle()
        assertEquals(1080, repo.plan(repo.find("b", DownloadKind.VIDEO)!!).streams.first { it.role == "video" }.height)
        assertEquals(Container.MP4, merger.jobs.last().container)
    }

    @Test fun aMergerThatRejectsVp9WithAacGetsVp9WithOpusInWebmBeforeH264() = test {
        val picky = FakeMerger(rejects = { v, a -> v.startsWith("vp") && a.startsWith("mp4a") }).also { it.storage = storage }
        extractor.withOpus = true
        val m = started(manager(managerScope(), mergerOverride = picky))
        m.enqueue(DownloadRequest(summary("a"), maxHeight = 720)); advanceUntilIdle()
        assertEquals(Container.WEBM, picky.jobs.single().container)
        val streams = repo.plan(repo.find("a", DownloadKind.VIDEO)!!).streams
        assertTrue(streams.first { it.role == "video" }.codec.startsWith("vp")); assertTrue(streams.first { it.role == "audio" }.codec == "opus")
    }

    @Test fun withoutAnOpusTrackARejectingMergerFallsBackToH264InMp4() = test {
        val picky = FakeMerger(rejects = { v, a -> v.startsWith("vp") && a.startsWith("mp4a") }).also { it.storage = storage }
        val m = started(manager(managerScope(), mergerOverride = picky))
        m.enqueue(DownloadRequest(summary("a"), preferAvc = false)); advanceUntilIdle()
        assertEquals(Container.MP4, picky.jobs.single().container)
        assertTrue(repo.plan(repo.find("a", DownloadKind.VIDEO)!!).streams.first { it.role == "video" }.codec.startsWith("avc"))
    }

    @Test fun lowDiskFailsClearlyBeforeAnythingIsDownloadedAndRetryWorksAfterSpaceIsFreed() = test {
        storage.free = 1_000_000L // 1 MB free; the margin alone is 50 MB
        val m = started()
        val id = m.enqueue(DownloadRequest(summary("a"), maxHeight = 720)); advanceUntilIdle()
        assertEquals(DownloadState.FAILED, m.state(id))
        val reason = m.items.value.first().error
        assertTrue(reason!!.contains("Not enough free space"), reason)
        assertTrue(storage.work.isEmpty(), "nothing was written")
        storage.free = null; m.retry(id); advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id))
    }

    @Test fun defaultsComeFromSettings() = test {
        settings.set(Settings.downloadsPreferAvc, true)
        val m = started()
        m.enqueue(DownloadRequest(SomeVideo))
        advanceUntilIdle()
        assertEquals(Container.MP4, merger.jobs.single().container)
    }

    @Test fun anHlsOnlyVideoFailsClearly() = test {
        extractor.hlsOnly = true
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo))
        advanceUntilIdle()
        assertEquals(DownloadState.FAILED, m.state(id))
        assertTrue("Nothing to download" in m.items.value.single().error!!, m.items.value.single().error)
    }

    @Test fun aResolveFailureFailsTheDownloadWithTheReason() = test {
        extractor.failResolve = ExtractorError.Blocked("IP blocked")
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo))
        advanceUntilIdle()
        assertEquals(DownloadState.FAILED, m.state(id))
        assertTrue("IP blocked" in m.items.value.single().error!!)
        assertTrue(notifier.failed.single().second.contains("IP blocked"))
    }

    // ---- state machine ----

    @Test fun concurrencyIsLimitedBySettingAndTheRestWaitInTheQueue() = test {
        fetcher.gate = CompletableDeferred()
        val m = started()
        val ids = (1..4).map { m.enqueue(DownloadRequest(summary("v$it"))) }
        runCurrent()
        assertEquals(2, m.items.value.count { it.state == DownloadState.DOWNLOADING }, "default limit is 2")
        assertEquals(2, m.items.value.count { it.state == DownloadState.QUEUED })
        assertEquals(4, m.activeCount.value)

        fetcher.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(ids.all { m.state(it) == DownloadState.COMPLETED })
        assertEquals(0, m.activeCount.value)
    }

    @Test fun theLimitCanBeRaisedInSettings() = test {
        settings.set(Settings.downloadsConcurrent, 4f)
        fetcher.gate = CompletableDeferred()
        val m = started()
        repeat(4) { m.enqueue(DownloadRequest(summary("v$it"))) }
        runCurrent()
        assertEquals(4, m.items.value.count { it.state == DownloadState.DOWNLOADING })
        fetcher.gate!!.complete(Unit)
    }

    @Test fun pauseKeepsTheBytesAndResumeContinuesFromThem() = test {
        // The video stream gets two chunks, then stalls.
        fetcher.script = { r -> if (r.url.startsWith("https://v/") && r.start == 2000L) awaitCancellationThenNull() else null }
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        runCurrent()
        assertEquals(DownloadState.DOWNLOADING, m.state(id))
        m.pause(id)
        runCurrent()
        assertEquals(DownloadState.PAUSED, m.state(id))
        assertEquals(2000, storage.work.entries.single { it.key.contains("video") }.value.size, "the partial file is kept")
        assertEquals(0, m.activeCount.value, "a paused download is not active")

        fetcher.script = { null }
        fetcher.requests.clear()
        m.resume(id)
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id))
        assertEquals(listOf(2000L, 3000L), fetcher.requests.filter { it.url.startsWith("https://v/") }.map { it.start })
        assertContentEquals(video + audio, storage.published.values.single())
    }

    @Test fun removeDeletesTheFilesAndTheRowWhateverTheState() = test {
        fetcher.gate = CompletableDeferred()
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        runCurrent()
        m.remove(id)
        runCurrent()
        assertTrue(m.items.value.isEmpty())
        assertTrue(storage.work.isEmpty())

        fetcher.gate!!.complete(Unit)
        val id2 = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id2))
        m.remove(id2)
        assertTrue(storage.published.isEmpty(), "a finished download is deleted with its published file")
        assertTrue(storage.assets.isEmpty(), "and its thumbnail")
        assertNull(repo.offlineData(id2))
    }

    @Test fun enqueuingTheSameVideoTwiceGivesTheSameDownload() = test {
        val m = started()
        val a = m.enqueue(DownloadRequest(SomeVideo))
        val b = m.enqueue(DownloadRequest(SomeVideo))
        advanceUntilIdle()
        val requests = fetcher.requests.size
        val c = m.enqueue(DownloadRequest(SomeVideo))
        advanceUntilIdle()
        assertEquals(a, b); assertEquals(a, c)
        assertEquals(requests, fetcher.requests.size, "a finished download is not fetched again")
        // A different kind of the same video is a separate download.
        assertTrue(m.enqueue(DownloadRequest(SomeVideo, audioOnly = true)) != a)
    }

    @Test fun retryRequeuesAFailedDownloadAndItCompletes() = test {
        extractor.failResolve = ExtractorError.Network("offline")
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo))
        advanceUntilIdle()
        assertEquals(DownloadState.FAILED, m.state(id))
        extractor.failResolve = null
        m.retry(id)
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id))
        assertNull(m.items.value.single().error?.takeIf { !it.startsWith("Saved without") })
    }

    @Test fun enqueuingAFailedOrPausedOneRestartsIt() = test {
        extractor.failResolve = ExtractorError.Network("offline")
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo))
        advanceUntilIdle()
        extractor.failResolve = null
        assertEquals(id, m.enqueue(DownloadRequest(SomeVideo)))
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id))
    }

    // ---- surviving the process ----

    @Test fun aKilledProcessResumesFromTheBytesOnDiskNotFromZero() = test {
        // Run 1: the video stream gets two chunks, then the process "dies" (the scope is cancelled mid-request).
        var videoRequests = 0
        val deadParent = Job()
        val scopeA = CoroutineScope(coroutineContext + deadParent).also { scopes += it }
        fetcher.script = { r -> if (r.url.startsWith("https://v/") && ++videoRequests == 3) awaitCancellationThenNull() else null }
        val a = manager(scopeA)
        a.start(); runCurrent()
        val id = a.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        runCurrent()
        assertEquals(DownloadState.DOWNLOADING, repo.get(id)!!.state)
        assertEquals(2000L, storage.work.entries.single { it.key.contains("video") }.value.size.toLong())
        deadParent.cancel() // the process dies: nothing gets to write a final state
        runCurrent()
        assertEquals(DownloadState.DOWNLOADING, repo.get(id)!!.state, "the row still says it was running")

        // Run 2: a new manager on the same database and files.
        fetcher.script = { null }
        fetcher.requests.clear()
        val b = manager(managerScope())
        b.start()
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, b.state(id), b.items.value.first().error)
        val videoRanges = fetcher.requests.filter { it.url.startsWith("https://v/") }.map { it.start }
        assertEquals(listOf(2000L, 3000L), videoRanges, "continued at 2000, did not re-download the first two chunks")
        assertContentEquals(video + audio, storage.published.values.single())
        assertEquals(2, extractor.resolves, "each run resolves fresh URLs")
    }

    private suspend fun awaitCancellationThenNull(): Int? { awaitCancellation() }

    // ---- URL expiry ----

    @Test fun aDeadUrlIsResolvedAgainAndTheTransferContinuesWhereItStopped() = test {
        fetcher.script = { r -> if (r.url.startsWith("https://v/1/") && r.start >= 2000) 403 else null }
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id), m.items.value.first().error)
        assertEquals(2, extractor.resolves)
        val after403 = fetcher.requests.filter { it.url.startsWith("https://v/2/") }
        assertEquals(2000L, after403.first().start, "the fresh URL picked up at byte 2000")
        assertContentEquals(video + audio, storage.published.values.single())
    }

    private suspend fun TestScope.deadStatusIsResolvedAgain(status: Int) {
        fetcher.script = { r -> if (r.url.startsWith("https://a/1") && r.start >= 1000) status else null }
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, audioOnly = true))
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id), "HTTP $status: ${m.items.value.firstOrNull()?.error}")
        assertEquals(2, extractor.resolves, "HTTP $status must trigger exactly one fresh resolve")
        assertContentEquals(audio, storage.published.values.single())
    }

    @Test fun http401TriggersAResolve() = test { deadStatusIsResolvedAgain(401) }
    @Test fun http404TriggersAResolve() = test { deadStatusIsResolvedAgain(404) }
    @Test fun http410TriggersAResolve() = test { deadStatusIsResolvedAgain(410) }

    @Test fun transientStatusesDoNotTriggerAResolve() = test {
        var n = 0
        fetcher.script = { if (n++ == 0) 500 else null }
        val m = started()
        m.enqueue(DownloadRequest(SomeVideo, audioOnly = true))
        advanceUntilIdle()
        assertEquals(1, extractor.resolves)
        assertFalse(StreamLeases.isDeadStatus(500)); assertFalse(StreamLeases.isDeadStatus(429)); assertFalse(StreamLeases.isDeadStatus(206))
    }

    @Test fun aUrlThatKeepsBeingRejectedEndsInFailureAfterAFewFreshTries() = test {
        fetcher.script = { r -> if (r.url.startsWith("https://v/")) 403 else null }
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        advanceUntilIdle()
        assertEquals(DownloadState.FAILED, m.state(id))
        assertTrue("rejected" in m.items.value.single().error!!)
        assertEquals(4, extractor.resolves, "1 initial + 3 fresh")
    }

    // ---- transient failures ----

    @Test fun transientFailuresRetryWithBackoffAndThenSucceed() = test {
        var n = 0
        fetcher.script = { if (n++ < 2) 503 else null }
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, audioOnly = true))
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id))
        assertEquals(listOf(1.seconds, 2.seconds), slept)
    }

    @Test fun connectionErrorsAreRetriedToo() = test {
        var n = 0
        fetcher.script = { if (n++ == 0) throw TransportException("reset") else null }
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, audioOnly = true))
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id))
        assertEquals(1, slept.size)
    }

    @Test fun givingUpAfterTheRetryBudget() = test {
        fetcher.script = { 503 }
        val m = started(manager(managerScope(), maxRetries = 3))
        val id = m.enqueue(DownloadRequest(SomeVideo, audioOnly = true))
        advanceUntilIdle()
        assertEquals(DownloadState.FAILED, m.state(id))
        assertTrue("Giving up after 3 retries" in m.items.value.single().error!!)
        assertEquals(listOf(1.seconds, 2.seconds, 4.seconds), slept)
    }

    @Test fun theRetryCountResetsAfterEachGoodChunk() = test {
        // 503 before every chunk of the audio stream (2 chunks): each chunk needs one retry, never more than the budget of 1.
        var flip = true
        fetcher.script = { r -> if (r.url.startsWith("https://a/")) { flip = !flip; if (!flip) 503 else null } else null }
        val m = started(manager(managerScope(), maxRetries = 1))
        val id = m.enqueue(DownloadRequest(SomeVideo, audioOnly = true))
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id), m.items.value.first().error)
    }

    // ---- integrity and merging ----

    @Test fun aShortFileIsRejectedAndItsPartialIsDiscarded() = test {
        fetcher.reportedTotal = 5000 // the server says 5000 bytes but has only 3500 to give
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        advanceUntilIdle()
        assertEquals(DownloadState.FAILED, m.state(id))
        assertTrue("Size mismatch" in m.items.value.single().error!!)
        assertTrue(storage.work.keys.none { it.contains("video") }, "the bad partial is deleted so a retry starts clean")
    }

    @Test fun aMissingMergerFailsWithItsExplanation() = test {
        val m = started(manager(managerScope(), mergerOverride = FakeMerger(available = false)))
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        advanceUntilIdle()
        assertEquals(DownloadState.FAILED, m.state(id))
        assertTrue("ffmpeg not found" in m.items.value.single().error!!)
        // The two downloaded streams are kept, so installing ffmpeg and retrying costs no re-download.
        assertEquals(2, storage.work.size)
        fetcher.requests.clear()
    }

    @Test fun aFailedMergeIsReportedAsSuch() = test {
        merger.failWith = "codec not supported"
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        advanceUntilIdle()
        assertEquals(DownloadState.FAILED, m.state(id))
        assertTrue("Could not merge: codec not supported" in m.items.value.single().error!!)
    }

    @Test fun retryingAfterAMergeFailureDoesNotDownloadAgain() = test {
        merger.failWith = "boom"
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        advanceUntilIdle()
        merger.failWith = null
        fetcher.requests.clear()
        m.retry(id)
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id))
        val streamRequests = fetcher.requests.filter { it.url.startsWith("https://v/") || it.url.startsWith("https://a/") }
        assertTrue(streamRequests.isEmpty(), "both streams were already complete on disk: ${streamRequests.size} stream requests")
    }

    @Test fun aFailedPublishIsRetriedWithoutDownloadingAnythingAgain() = test {
        storage.failPublishes = 1
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        advanceUntilIdle()
        assertEquals(DownloadState.FAILED, m.state(id))
        assertTrue("could not write" in m.items.value.single().error!!)
        assertEquals(1, storage.work.size, "the merged file is kept in the working area")

        fetcher.requests.clear()
        val resolvesBefore = extractor.resolves
        m.retry(id)
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id))
        assertContentEquals(video + audio, storage.published.values.single())
        assertTrue(fetcher.requests.none { it.url.startsWith("https://v/") || it.url.startsWith("https://a/") }, "no stream was fetched again")
        assertEquals(resolvesBefore, extractor.resolves, "and nothing was resolved")
    }

    // ---- network ----

    @Test fun wifiOnlyKeepsDownloadsQueuedOnMeteredAndStartsThemOnWifi() = test {
        network.set(NetworkType.METERED)
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo))
        advanceUntilIdle()
        assertEquals(DownloadState.QUEUED, m.state(id))
        assertEquals("Waiting for Wi-Fi", m.waiting.value)
        assertTrue(fetcher.requests.isEmpty())
        network.set(NetworkType.UNMETERED)
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id))
        assertNull(m.waiting.value)
    }

    @Test fun theWifiOnlySettingCanBeTurnedOff() = test {
        settings.set(Settings.downloadsWifiOnly, false)
        network.set(NetworkType.METERED)
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo))
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id))
    }

    @Test fun losingWifiMidTransferStopsItAndWifiReturningContinuesIt() = test {
        val started = CompletableDeferred<Unit>()
        fetcher.script = { r -> if (r.url.startsWith("https://v/") && r.start == 2000L) { started.complete(Unit); awaitCancellationThenNull() } else null }
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        runCurrent()
        assertTrue(started.isCompleted)
        network.set(NetworkType.METERED)
        runCurrent()
        assertEquals(DownloadState.QUEUED, m.state(id))
        assertEquals(2000L, storage.work.entries.single { it.key.contains("video") }.value.size.toLong())

        fetcher.script = { null }
        network.set(NetworkType.UNMETERED)
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id))
    }

    @Test fun offlineHoldsEverything() = test {
        network.set(NetworkType.OFFLINE)
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo))
        advanceUntilIdle()
        assertEquals(DownloadState.QUEUED, m.state(id))
        assertEquals("Waiting for a connection", m.waiting.value)
    }

    // ---- offline data ----

    @Test fun theOfflineCopyOfThePageIsSavedWithTheDownload() = test {
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        advanceUntilIdle()
        val data = repo.offlineData(id)!!
        assertEquals("0:00 a\n1:00 b\n2:00 c", data.description)
        assertTrue("Title of vid1" in data.infoJson!!)
        assertTrue(data.chaptersJson!!.contains("\"a\"") && data.chaptersJson!!.contains("\"c\""), "chapters parsed from the description")
        assertTrue(data.commentsJson!!.contains("comment 1") && data.commentsJson!!.contains("comment 2"))
        assertFalse(data.commentsJson!!.contains("comment 3"), "the setting says 2 pages")
        assertEquals("/assets/vid1.jpg", data.thumbnailPath)
        assertEquals(50, storage.assets["/assets/vid1.jpg"]!!.size)
        assertNotNull(data.sponsorBlockJson)
    }

    @Test fun commentPagesFollowTheSetting() = test {
        settings.set(Settings.downloadsCommentPages, 0f)
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        advanceUntilIdle()
        assertEquals("[]", repo.offlineData(id)!!.commentsJson)
    }

    @Test fun aFailingExtraPartDoesNotFailTheDownloadButIsNoted() = test {
        val broken = object : com.greenaltambe.vela.extractor.Extractor by extractor {
            override suspend fun videoInfo(videoId: String) = com.greenaltambe.vela.extractor.ExtractorResult.Failure(ExtractorError.Network("down"))
        }
        val m = DownloadManager(
            repo, StreamLeases(extractor), { storage }, fetcher, merger,
            OfflineCollector(broken, modules, fetcher, { storage }, settings),
            settings, network, managerScope(), notifier, chunkBytes = CHUNK, sleep = { slept += it },
        )
        m.start(); runCurrent()
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        advanceUntilIdle()
        assertEquals(DownloadState.COMPLETED, m.state(id))
        assertTrue("Saved without" in m.items.value.single().error!! && "info" in m.items.value.single().error!!)
    }

    @Test fun progressIsReportedWhileTransferring() = test {
        fetcher.script = { r -> if (r.url.startsWith("https://v/") && r.start == 2000L) awaitCancellationThenNull() else null }
        val m = started()
        val id = m.enqueue(DownloadRequest(SomeVideo, preferAvc = true))
        runCurrent()
        val mid = m.items.value.single { it.id == id }
        assertEquals(DownloadState.DOWNLOADING, mid.state)
        assertEquals(2000L, mid.bytesDone)
        assertTrue(mid.progress == null || mid.progress!! in 0f..1f)

        fetcher.script = { null }
        m.pause(id); runCurrent(); m.resume(id)
        advanceUntilIdle()
        val done = m.items.value.single { it.id == id }
        assertEquals(video.size + audio.size.toLong(), done.bytesTotal)
        assertEquals(done.bytesTotal, done.bytesDone)
        assertEquals(1.0f, done.progress)
    }
}
