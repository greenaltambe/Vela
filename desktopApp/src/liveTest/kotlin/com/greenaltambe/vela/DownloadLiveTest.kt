@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.core.http.HttpRequest
import com.greenaltambe.vela.core.http.HttpResponse
import com.greenaltambe.vela.core.http.HttpTransport
import com.greenaltambe.vela.core.http.TransportException
import com.greenaltambe.vela.core.network.FixedNetworkMonitor
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.DownloadState
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.SqlDelightWatchHistoryRepository
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.downloads.DownloadManager
import com.greenaltambe.vela.downloads.DownloadRepository
import com.greenaltambe.vela.downloads.DownloadRequest
import com.greenaltambe.vela.downloads.Downloads
import com.greenaltambe.vela.downloads.OfflineCollector
import com.greenaltambe.vela.downloads.OfflineData
import com.greenaltambe.vela.downloads.StoredBranding
import com.greenaltambe.vela.downloads.StoredInfo
import com.greenaltambe.vela.downloads.StoredPlan
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.chain.FallbackChain
import com.greenaltambe.vela.extractor.chain.stubTiers
import com.greenaltambe.vela.extractor.newpipe.NewPipeExtractor
import com.greenaltambe.vela.modules.createModules
import com.greenaltambe.vela.modules.sponsorblock.Segment
import com.greenaltambe.vela.net.FfmpegMediaMerger
import com.greenaltambe.vela.net.FileDownloadStorage
import com.greenaltambe.vela.net.OkHttpRangeFetcher
import com.greenaltambe.vela.net.OkHttpTransport
import com.greenaltambe.vela.playback.DesktopPlayerHost
import com.greenaltambe.vela.playback.DesktopPlaybackRuntime
import com.greenaltambe.vela.player.PlaybackMetadata
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.StreamLeases
import com.greenaltambe.vela.player.mpv.MpvConfig
import com.greenaltambe.vela.player.mpv.MpvLibrary
import com.greenaltambe.vela.player.mpv.MpvPlayerController
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun exec(vararg cmd: String): String {
    val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    check(p.waitFor(120, TimeUnit.SECONDS) && p.exitValue() == 0) { "${cmd.toList()}\n$out" }
    return out
}

private fun probeStreams(path: String) = exec("ffprobe", "-v", "error", "-show_entries", "stream=codec_type,codec_name", "-of", "csv=p=0", path).trim().lines().sorted()
private fun probeDuration(path: String) = exec("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path).trim().toDouble()

class DownloadLiveTest {
    private fun networkUp() = runCatching { Socket().use { it.connect(InetSocketAddress("www.youtube.com", 443), 5_000) } }.isSuccess

    @Test
    fun realVideoAndAudioOnlyDownloadsMergePlayAndAreMeasured() {
        assumeTrue("SKIPPED: no network", networkUp())
        val merger = FfmpegMediaMerger()
        assumeTrue("SKIPPED: ffmpeg missing", merger.available)
        val backend = MpvLibrary.load().getOrNull()
        assumeTrue("SKIPPED: libmpv not loadable", backend != null)

        val settings = SettingsResolver(InMemorySettingsStore())
        val database = createDatabase(JvmDatabaseDriverFactory(null))
        val chain = FallbackChain(stubTiers(anonymous = NewPipeExtractor()), accountMode = { settings.resolve(Settings.accountFallbackMode) }, log = DiagnosticsLog())
        val modules = createModules(OkHttpTransport(), database, settings)
        val dir = Files.createTempDirectory("vela-live-dl").toFile()
        val storage = FileDownloadStorage(File(dir, "Videos"))
        val fetcher = OkHttpRangeFetcher()
        val repo = DownloadRepository(database)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = DownloadManager(repo, StreamLeases(chain), { storage }, fetcher, merger, OfflineCollector(chain, modules, fetcher, { storage }, settings), settings, FixedNetworkMonitor(), scope)
        val player = MpvPlayerController(backend!!, MpvConfig())
        try {
            manager.start()
            // A short real video: the first search hit between 20 s and 3 min, else the oldest one on YouTube.
            val hit = runBlocking { (chain.search("blender foundation short film") as? ExtractorResult.Success)?.value?.items }
                ?.firstOrNull { (it.durationSeconds ?: 0) in 20..180 } ?: VideoSummary("jNQXAC9IVRw", "Me at the zoo", "UC4QobU6STFB0P71PMvOGN5A", "jawed", durationSeconds = 19)
            println("LIVE download video: ${hit.id} '${hit.title}' (${hit.durationSeconds} s)")

            val speeds = mutableListOf<Long>()
            fun await(id: Long, what: String): com.greenaltambe.vela.downloads.DownloadItem {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(300)
                while (System.nanoTime() < deadline) {
                    val item = manager.items.value.firstOrNull { it.id == id }
                    item?.speedBps?.let { speeds += it }
                    if (item != null && (item.state == DownloadState.COMPLETED || item.state == DownloadState.FAILED)) {
                        check(item.state == DownloadState.COMPLETED) { "$what failed: ${item.error}" }
                        return item
                    }
                    Thread.sleep(200)
                }
                error("$what timed out; items=${manager.items.value}")
            }

            // ---- video + audio, merged ----
            val t0 = System.nanoTime()
            val id = manager.enqueue(DownloadRequest(hit, maxHeight = 1080))
            val done = await(id, "video download")
            if (speeds.isNotEmpty()) println("LIVE download transfer speed while downloading: avg ${"%.1f".format(speeds.average() * 8 / 1e6)} Mbit/s, peak ${"%.1f".format(speeds.max() * 8 / 1e6)} Mbit/s over ${speeds.size} samples")
            val seconds = (System.nanoTime() - t0) / 1e9
            val file = File(done.uri!!)
            val bytes = file.length()
            println("LIVE download video: ${file.name} ${bytes / 1024} KiB, '${done.qualityLabel}', ${"%.1f".format(seconds)} s total = ${"%.1f".format(bytes * 8 / seconds / 1e6)} Mbit/s (includes resolve, merge and the page extras)")
            val streams = probeStreams(file.path)
            println("LIVE download video streams: $streams  duration=${probeDuration(file.path)} s  container=${file.extension}")
            assertTrue(streams.any { it.endsWith(",video") } && streams.any { it.endsWith(",audio") }, "both streams: $streams")
            hit.durationSeconds?.let { assertEquals(it.toDouble(), probeDuration(file.path), 3.0) }
            val data = repo.offlineData(id)!!
            println("LIVE download offline data: description=${data.description?.length} chars, comments=${data.commentsJson?.length} chars, thumbnail=${data.thumbnailPath}, sponsorblock=${data.sponsorBlockJson}, note='${done.error}'")
            assertTrue(File(data.thumbnailPath!!).length() > 1000, "thumbnail saved")

            // mpv plays the file: position advances, picture decoded.
            player.load(PlaybackPlan.Local(done.uri!!, hasVideo = true), 0, true, PlaybackMetadata("offline"))
            val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (System.nanoTime() < limit && !(player.state.value.status == PlaybackStatus.PLAYING && player.state.value.positionMs > 1500)) Thread.sleep(100)
            println("LIVE download playback: ${player.state.value} stats=${player.stats.value.videoResolution} ${player.stats.value.videoCodec}/${player.stats.value.audioCodec}")
            assertTrue(player.state.value.positionMs > 1500, "mpv played the downloaded file")
            player.stop()

            // ---- audio only ----
            val t1 = System.nanoTime()
            val aid = manager.enqueue(DownloadRequest(hit, audioOnly = true))
            val adone = await(aid, "audio download")
            val aseconds = (System.nanoTime() - t1) / 1e9
            val afile = File(adone.uri!!)
            println("LIVE download audio: ${afile.name} ${afile.length() / 1024} KiB in ${"%.1f".format(aseconds)} s")
            val astreams = probeStreams(afile.path)
            println("LIVE download audio streams: $astreams  container=${afile.extension}")
            assertEquals(1, astreams.size)
            assertTrue(astreams.single().endsWith(",audio"))
            player.load(PlaybackPlan.Local(adone.uri!!, hasVideo = false), 0, true, PlaybackMetadata("offline audio"))
            val l2 = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (System.nanoTime() < l2 && !(player.state.value.status == PlaybackStatus.PLAYING && player.state.value.positionMs > 1500)) Thread.sleep(100)
            assertTrue(player.state.value.positionMs > 1500, "mpv played the audio file")

            // Deleting removes the file and the saved thumbnail.
            manager.remove(id); manager.remove(aid)
            assertTrue(!file.exists() && !afile.exists() && !File(data.thumbnailPath!!).exists())
        } finally {
            player.release()
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    @Test
    fun downloadFromTheUiThenFindItInLibraryPlayItOfflineAndDeleteIt() = runDesktopComposeUiTest(width = 500, height = 900) {
        assumeTrue("SKIPPED: no network", networkUp())
        val merger = FfmpegMediaMerger()
        assumeTrue("SKIPPED: ffmpeg missing", merger.available)
        val settings = SettingsResolver(InMemorySettingsStore())
        val database = createDatabase(JvmDatabaseDriverFactory(null))
        val log = DiagnosticsLog()
        val chain = FallbackChain(stubTiers(anonymous = NewPipeExtractor()), accountMode = { settings.resolve(Settings.accountFallbackMode) }, log = log)
        val modules = createModules(OkHttpTransport(), database, settings)
        val created = DesktopPlaybackRuntime.create(chain, settings, SqlDelightWatchHistoryRepository(database), listOf(modules.sponsorBlock))
        assumeTrue("SKIPPED: libmpv not loadable", created.isSuccess)
        val runtime = created.getOrThrow()
        val host = DesktopPlayerHost(runtime)
        val dir = Files.createTempDirectory("vela-ui-dl").toFile()
        val storage = FileDownloadStorage(File(dir, "Videos"))
        val fetcher = OkHttpRangeFetcher()
        val repo = DownloadRepository(database)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = DownloadManager(repo, StreamLeases(chain), { storage }, fetcher, merger, OfflineCollector(chain, modules, fetcher, { storage }, settings), settings, FixedNetworkMonitor(), scope)
        manager.start()
        val services = AppServices(chain, settings, log, database, host, modules = modules, downloads = Downloads(manager, repo, { storage }, merger))
        val c = runtime.controller
        try {
            val hit = runBlocking { (chain.search("blender foundation short film") as? ExtractorResult.Success)?.value?.items }
                ?.firstOrNull { (it.durationSeconds ?: 0) in 20..180 } ?: VideoSummary("jNQXAC9IVRw", "Me at the zoo", "UC4QobU6STFB0P71PMvOGN5A", "jawed", durationSeconds = 19)
            val navigator = Navigator().also { it.push(Route.Video(hit)) }
            setContent { App(services, navigator) }
            waitUntil(timeoutMillis = 60_000) { c.state.value.status == PlaybackStatus.PLAYING }

            // Download from the video page: the dialog, a quality, confirm.
            onNodeWithTag("video.list").performScrollToNode(hasTestTag("video.download"))
            onNodeWithTag("video.download").performClick()
            onNodeWithTag("download.quality.720").performClick()
            onNodeWithTag("download.confirm").performClick()
            println("UI download queued: ${manager.items.value.map { it.state to it.qualityLabel }}")
            assertEquals(1, manager.items.value.size)

            // Library > Downloads shows it, with progress, until it is downloaded.
            if (onAllNodesWithTag("player.back").fetchSemanticsNodes().isEmpty()) onNodeWithTag("player.surface").performClick()
            onNodeWithTag("player.back").performClick()
            onNodeWithText("Library").performClick()
            onNodeWithTag("library.downloads").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("downloads.row").fetchSemanticsNodes().isNotEmpty() }
            waitUntil(timeoutMillis = 240_000) { manager.items.value.singleOrNull()?.state == DownloadState.COMPLETED }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("downloads.play").fetchSemanticsNodes().isNotEmpty() }
            println("UI downloads row: ${manager.items.value.single().let { "${it.state} ${it.qualityLabel} ${it.bytesTotal} bytes -> ${it.uri}" }}")

            // Play it from there: the page now says Downloaded and the session is the offline one.
            onNodeWithTag("downloads.play").performClick()
            waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag("video.list").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("video.list").performScrollToNode(hasTestTag("video.downloaded"))
            waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag("video.downloaded").fetchSemanticsNodes().isNotEmpty() }
            waitUntil(timeoutMillis = 30_000) { c.state.value.status == PlaybackStatus.PLAYING && c.state.value.positionMs > 1_000 }
            val ready = host.session.state.value as com.greenaltambe.vela.player.SessionState.Ready
            println("UI offline playback: client=${ready.client} plan=${ready.plan::class.simpleName} ${c.stats.value.videoResolution}")
            assertEquals("offline", ready.client)
            assertTrue(ready.plan is PlaybackPlan.Local)

            // Delete it with the confirmation.
            val file = File(manager.items.value.single().uri!!)
            assertTrue(file.exists())
            if (onAllNodesWithTag("player.back").fetchSemanticsNodes().isEmpty()) onNodeWithTag("player.surface").performClick()
            onNodeWithTag("player.back").performClick() // back to the Downloads list it was opened from
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("downloads.delete").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("downloads.delete").performClick()
            onNodeWithTag("downloads.confirmDelete").performClick()
            waitUntil(timeoutMillis = 10_000) { manager.items.value.isEmpty() }
            assertTrue(!file.exists(), "the file is deleted with the download")
        } finally {
            runtime.close()
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Every call fails and is counted: proves a downloaded video's page never asks the network for anything. */
    private class ForbiddenExtractor : Extractor by StubExtractor() {
        val calls = AtomicInteger()
        private fun <T> forbidden(): ExtractorResult<T> { calls.incrementAndGet(); return ExtractorResult.Failure(ExtractorError.Network("offline")) }
        override suspend fun search(query: String, continuation: String?) = forbidden<Page<VideoSummary>>()
        override suspend fun videoInfo(videoId: String) = forbidden<VideoInfo>()
        override suspend fun streams(videoId: String) = forbidden<StreamSet>()
        override suspend fun channel(channelId: String) = forbidden<ChannelPage>()
        override suspend fun channelVideos(channelId: String, continuation: String?) = forbidden<Page<VideoSummary>>()
        override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?) = forbidden<CommentsPage>()
        override suspend fun replies(videoId: String, commentId: String, continuation: String?) = forbidden<CommentsPage>()
        override suspend fun trending(continuation: String?) = forbidden<Page<VideoSummary>>()
        override suspend fun related(videoId: String) = forbidden<List<VideoSummary>>()
        override suspend fun channelShorts(channelId: String, continuation: String?) = forbidden<Page<VideoSummary>>()
        override suspend fun recentUploads(channelId: String) = forbidden<List<VideoSummary>>()
    }

    private class ForbiddenTransport : HttpTransport {
        val requests = AtomicInteger()
        override suspend fun execute(request: HttpRequest): HttpResponse { requests.incrementAndGet(); throw TransportException("offline") }
    }

    @Test
    fun aDownloadedVideoPlaysAndShowsItsWholePageWithEveryNetworkCallForbidden() = runDesktopComposeUiTest(width = 500, height = 900) {
        val merger = FfmpegMediaMerger()
        assumeTrue("SKIPPED: ffmpeg missing", merger.available)
        val dir = Files.createTempDirectory("vela-offline").toFile()
        try {
            // A real media file with picture and sound.
            val media = File(dir, "clip.mp4")
            exec("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc=duration=20:size=320x180:rate=15", "-f", "lavfi", "-i", "sine=frequency=440:duration=20", "-c:v", "mpeg4", "-c:a", "aac", "-shortest", media.path)

            val settings = SettingsResolver(InMemorySettingsStore())
            val database = createDatabase(JvmDatabaseDriverFactory(null))
            val transport = ForbiddenTransport()
            val modules = createModules(transport, database, settings)
            val extractor = ForbiddenExtractor()
            val log = DiagnosticsLog()
            val chain = FallbackChain(stubTiers(anonymous = extractor), accountMode = { settings.resolve(Settings.accountFallbackMode) }, log = log)
            val created = DesktopPlaybackRuntime.create(chain, settings, SqlDelightWatchHistoryRepository(database), listOf(modules.sponsorBlock))
            assumeTrue("SKIPPED: libmpv not loadable", created.isSuccess)
            val runtime = created.getOrThrow()
            val host = DesktopPlayerHost(runtime)

            // Register a finished download with the page data saved the way the downloader saves it.
            val storage = FileDownloadStorage(File(dir, "Videos"))
            val repo = DownloadRepository(database)
            val summary = VideoSummary("offlineVid1", "Original title", "UCoffline", "Offline channel")
            val id = repo.insert(DownloadRequest(summary), StoredPlan(), "720p")
            repo.setPaths(id, null, null, media.path)
            repo.setState(id, DownloadState.COMPLETED)
            val json = Json { encodeDefaults = true }
            repo.saveOfflineData(
                OfflineData(
                    id, "Intro text\n0:00 Start\n0:05 Middle\n0:10 End", json.encodeToString(StoredInfo.serializer(), StoredInfo("Original title", "d", "UCoffline", "Offline channel", 20, 1234)),
                    json.encodeToString(ListSerializer(Chapter.serializer()), listOf(Chapter("Start", 0), Chapter("Middle", 5), Chapter("End", 10))),
                    json.encodeToString(ListSerializer(Comment.serializer()), listOf(Comment("c1", "alice", "saved comment one"), Comment("c2", "bob", "saved comment two"))),
                    json.encodeToString(ListSerializer(Segment.serializer()), listOf(Segment("s1", "sponsor", "skip", 2_000, 4_000))),
                    json.encodeToString(StoredBranding.serializer(), StoredBranding(title = "Clean saved title")),
                    null,
                ),
            )
            modules.segments.store("offlineVid1", listOf(Segment("s1", "sponsor", "skip", 2_000, 4_000)))

            val manager = DownloadManager(repo, StreamLeases(chain), { storage }, OkHttpRangeFetcher(), merger, OfflineCollector(chain, modules, OkHttpRangeFetcher(), { storage }, settings), settings, FixedNetworkMonitor(), CoroutineScope(SupervisorJob() + Dispatchers.IO))
            val downloads = Downloads(manager, repo, { storage }, merger)
            val services = AppServices(chain, settings, log, database, host, modules = modules, downloads = downloads)
            val navigator = Navigator().also { it.push(Route.Video(summary)) }
            val c = runtime.controller
            try {
                setContent { App(services, navigator) }
                waitUntil(timeoutMillis = 30_000) { c.state.value.status == PlaybackStatus.PLAYING && c.state.value.positionMs > 500 }
                println("offline playback: ${c.state.value} ${c.stats.value.videoResolution} ${c.stats.value.videoCodec}")

                // The page, from what was saved.
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("video.downloaded").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("video.list").performScrollToNode(hasTestTag("video.title"))
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("video.title").fetchSemanticsNodes().isNotEmpty() }
                println("offline title shown: " + onNodeWithTag("video.title").fetchSemanticsNode().config.toString())
                onNodeWithTag("video.list").performScrollToNode(hasTestTag(ComponentIds.CHAPTER_ROW))
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ComponentIds.CHAPTER_ROW).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("video.list").performScrollToNode(hasTestTag(ComponentIds.COMMENT_ITEM))
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ComponentIds.COMMENT_ITEM).fetchSemanticsNodes().size == 2 }
                waitUntil(timeoutMillis = 10_000) { modules.sponsorBlock.segments.value.size == 1 }
                println("offline page: ${onAllNodesWithTag(ComponentIds.CHAPTER_ROW).fetchSemanticsNodes().size} chapters, 2 saved comments, ${modules.sponsorBlock.segments.value.size} SponsorBlock marker")

                // SponsorBlock works offline too: seeking into the stored sponsor segment skips it.
                host.session.seekTo(2_500)
                waitUntil(timeoutMillis = 15_000) { c.state.value.positionMs >= 3_800 }

                println("network calls: extractor=${extractor.calls.get()} http=${transport.requests.get()}")
                assertEquals(0, extractor.calls.get(), "a downloaded video's page must not call the extractor at all")
                assertEquals(0, transport.requests.get(), "...nor the SponsorBlock / RYD / DeArrow servers")
            } finally {
                runtime.close()
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
