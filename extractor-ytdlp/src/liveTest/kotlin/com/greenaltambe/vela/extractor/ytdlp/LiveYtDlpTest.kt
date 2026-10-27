package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.extractor.ExtractorResult
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock

/**
 * Runs the real yt-dlp against real YouTube. Skipped unless yt-dlp is installed (PATH, or `VELA_YTDLP_PATH`) and
 * www.youtube.com is reachable.
 */
class LiveYtDlpTest {
    private val binary = System.getenv("VELA_YTDLP_PATH")?.takeIf { it.isNotBlank() } ?: "yt-dlp"
    private val runner = SystemProcessRunner()
    private lateinit var extractor: YtDlpExtractor
    private val videoId = "dQw4w9WgXcQ"
    private val channelId = "UCuAXFkgsw1L7xaCfnd5JJOw"

    @Before
    fun requireYtDlp() {
        val probe = runBlocking { probeYtDlp(runner, binary) }
        assumeTrue("SKIPPED: yt-dlp is not installed ($binary); set VELA_YTDLP_PATH or put it on PATH", probe is YtDlpProbe.Ready)
        val ready = probe as YtDlpProbe.Ready
        println("yt-dlp ${ready.version}, js runtime args=${ready.jsRuntimeArgs}, note=${ready.note}")
        val reachable = runCatching { java.net.Socket().use { it.connect(java.net.InetSocketAddress("www.youtube.com", 443), 5_000) } }.isSuccess
        assumeTrue("SKIPPED: www.youtube.com is unreachable", reachable)
        extractor = YtDlpExtractor(runner, { binary }, { ready.jsRuntimeArgs })
    }

    private fun <T> ExtractorResult<T>.value(): T = when (this) {
        is ExtractorResult.Success -> value
        is ExtractorResult.Failure -> fail("${error.reason}: ${error.message}")
    }

    @Test
    fun videoInfo() = runBlocking<Unit> {
        val info = extractor.videoInfo(videoId).value()
        println("videoInfo: ${info.title}, ${info.durationSeconds}s, channel=${info.channelId}, uploaded=${info.uploadDate}")
        assertEquals(videoId, info.id)
        assertTrue("Never Gonna Give You Up" in info.title, info.title)
        assertEquals(channelId, info.channelId)
        assertTrue(info.durationSeconds in 200..230)
        assertNotNull(info.uploadDate)
    }


    @Test
    fun realStreamsAreBestFirstAndPlanAsSeparateNonDrc() = runBlocking<Unit> {
        val set = extractor.streams(videoId).value()
        assertEquals(set.videoStreams.map { it.height }, set.videoStreams.map { it.height }.sortedDescending(), "video best-first by height")
        val plan = com.greenaltambe.vela.player.StreamSelector.plan(set, com.greenaltambe.vela.player.PlaybackPrefs())
        println("plan: $plan".take(300))
        val separate = assertIs<com.greenaltambe.vela.player.PlaybackPlan.Separate>(plan)
        assertTrue(!separate.audio.isDrc, "DRC avoided")
        assertTrue(separate.video.height >= set.videoStreams.filter { !it.hasAudio }.maxOf { it.height } / 2)
    }

    @Test
    fun streams() = runBlocking<Unit> {
        val set = extractor.streams(videoId).value()
        println("streams: ${set.videoStreams.size} video, ${set.audioStreams.size} audio, hls=${set.hlsUrl != null}, expires=${set.expiresAt}")
        assertEquals("yt-dlp", set.client)
        assertTrue(set.videoStreams.isNotEmpty() && set.audioStreams.isNotEmpty())
        assertTrue(set.audioStreams.any { it.itag != null && it.contentLength != null })
        assertTrue(assertNotNull(set.expiresAt) > Clock.System.now())
    }

    @Test
    fun aResolvedAudioStreamIsActuallyFetchable() = runBlocking<Unit> {
        val set = extractor.streams(videoId).value()
        val audio = set.audioStreams.first()
        // TV-client URLs answer 403 until ~4 s after they were issued; the session waits for this too.
        set.availableAt?.let { val wait = (it - Clock.System.now()).inWholeMilliseconds; println("waiting ${wait} ms for availableAt"); if (wait > 0) Thread.sleep(wait) }
        val request = HttpRequest.newBuilder(URI(audio.url)).header("Range", "bytes=0-1023").apply {
            (set.headers + audio.headers).filterKeys { it !in restrictedHeaders }.forEach { (k, v) -> header(k, v) }
        }.GET().build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding())
        println("audio fetch: HTTP ${response.statusCode()}")
        assertEquals(206, response.statusCode(), "ranged request with our headers must be a partial response")
    }

    @Test
    fun searchPaging() = runBlocking<Unit> {
        val first = extractor.search("rick astley").value()
        println("search: ${first.items.size} results, more=${first.continuation != null}")
        assertTrue(first.items.isNotEmpty())
        val second = extractor.search("rick astley", assertNotNull(first.continuation)).value()
        assertTrue(second.items.isNotEmpty())
        // ytsearchN has no cursor, so YouTube re-ranks per N: observed 4 of 20 repeated between page 1 and 2.
        val repeated = first.items.map { it.id }.intersect(second.items.map { it.id }.toSet()).size
        println("search page overlap: $repeated of ${second.items.size}")
        assertTrue(repeated <= second.items.size / 2, "pages overlap by $repeated")
    }

    @Test
    fun channelAndPaging() = runBlocking<Unit> {
        val page = extractor.channel(channelId).value()
        println("channel: ${page.name}, subs=${page.subscriberCount}, ${page.videos.size} videos, more=${page.continuation != null}")
        assertTrue("Rick Astley" in page.name, page.name)
        assertTrue(page.videos.isNotEmpty())
        val next = extractor.channelVideos(channelId, assertNotNull(page.continuation)).value()
        assertTrue(next.items.isNotEmpty())
        assertTrue(page.videos.map { it.id }.intersect(next.items.map { it.id }.toSet()).isEmpty(), "pages overlap")
    }

    @Test
    fun theDefaultAudioTrackIsFirstOnADubbedVideo() = runBlocking<Unit> {
        val set = extractor.streams("0e3GPea1Tyg").value()
        val first = set.audioStreams.first()
        println("dubbed video: ${set.audioStreams.size} audio streams, first=${first.itag} ${first.languageCode}")
        assertEquals("en-US", first.languageCode)
    }

    @Test
    fun aShortIsFlaggedAsAShort() = runBlocking<Unit> {
        val shortId = Regex("\"id\": \"([\\w-]{11})\"").find(
            runBlocking { runner.run(listOf(binary, "--flat-playlist", "--playlist-end", "1", "-J", "--no-warnings", "https://www.youtube.com/@YouTube/shorts"), kotlin.time.Duration.parse("60s"), 4_000_000).stdout },
        )?.groupValues?.get(1) ?: fail("no short found")
        assertTrue(extractor.videoInfo(shortId).value().isShort, "short $shortId")
    }

    @Test
    fun unavailableVideoIsUnplayable() = runBlocking<Unit> {
        val error = assertIs<ExtractorResult.Failure>(extractor.videoInfo("aaaaaaaaaaa")).error
        println("unavailable video: ${error.reason} ${error.message}")
        assertIs<com.greenaltambe.vela.extractor.ExtractorError.Unplayable>(error)
        val terminated = assertIs<ExtractorResult.Failure>(extractor.streams("Tuw8hxrFBH8")).error
        println("terminated account: ${terminated.reason} ${terminated.message}")
        assertIs<com.greenaltambe.vela.extractor.ExtractorError.Unplayable>(terminated)
    }

    private val restrictedHeaders = setOf("connection", "content-length", "expect", "host", "upgrade")
}

/** The startup path of :desktopApp, against the real binary and the real PATH. */
class LiveStartupProbeTest {
    private val binary = System.getenv("VELA_YTDLP_PATH")?.takeIf { it.isNotBlank() } ?: "yt-dlp"

    @Test
    fun tierIsRegisteredAndThereIsNoNoteWhenARuntimeIsOnPath() = runBlocking<Unit> {
        val log = com.greenaltambe.vela.extractor.chain.DiagnosticsLog()
        val tier = createYtDlpTier(SystemProcessRunner(), { binary }, log)
        assumeTrue("SKIPPED: yt-dlp is not installed", tier != null)
        println("startup log: ${log.attempts.value.map { "${it.outcome}: ${it.detail}" }}")
        assertTrue(log.attempts.value.none { it.outcome == com.greenaltambe.vela.extractor.chain.AttemptOutcome.NOTE })
    }

    @Test
    fun theNoteAppearsWhenNoRuntimeIsOnPath() = runBlocking<Unit> {
        val probe = probeYtDlp(SystemProcessRunner(), binary, path = PathLookup { null })
        assumeTrue("SKIPPED: yt-dlp is not installed", probe is YtDlpProbe.Ready)
        val ready = probe as YtDlpProbe.Ready
        println("no-runtime note: ${ready.note}")
        assertNotNull(ready.note)
        assertTrue(ready.jsRuntimeArgs.isEmpty())
    }
}
