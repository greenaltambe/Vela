package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class YtDlpExtractorTest {
    private fun <T> ExtractorResult<T>.ok(): T = assertIs<ExtractorResult.Success<T>>(this).value
    private fun ExtractorResult<*>.err(): ExtractorError = assertIs<ExtractorResult.Failure>(this).error

    private fun extractor(runner: ProcessRunner, binary: () -> String = { "yt-dlp" }, extra: () -> List<String> = { emptyList() }) =
        YtDlpExtractor(runner, binary, extra)

    /** A flat playlist with [count] distinct videos. */
    private fun flat(count: Int) = """{"entries":[${(1..count).joinToString(",") { """{"_type":"url","id":"v$it","url":"https://www.youtube.com/watch?v=v$it","title":"T$it"}""" }}]}"""

    // ---- commands ----

    @Test fun streamsCommand() = runTest {
        val runner = FakeRunner.ok(Fixtures.text("video.json"))
        extractor(runner, binary = { "/opt/yt-dlp" }, extra = { listOf("--js-runtimes", "node:/usr/bin/node") }).streams("dQw4w9WgXcQ")
        assertEquals(
            listOf("/opt/yt-dlp", "--js-runtimes", "node:/usr/bin/node", "--dump-single-json", "--no-warnings", "--no-playlist",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
            runner.commands.single(),
        )
    }

    @Test fun binaryIsResolvedOnEveryCall() = runTest {
        var path = "yt-dlp"
        val runner = FakeRunner.ok(Fixtures.text("video.json"))
        val e = extractor(runner, binary = { path })
        e.videoInfo("a"); path = "/new/yt-dlp"; e.videoInfo("a")
        assertEquals(listOf("yt-dlp", "/new/yt-dlp"), runner.commands.map { it.first() })
    }

    @Test fun parsesStreamsAndInfo() = runTest {
        val e = extractor(FakeRunner.ok(Fixtures.text("video.json")))
        assertEquals("yt-dlp", e.streams("x").ok().client)
        assertEquals("dQw4w9WgXcQ", e.videoInfo("x").ok().id)
    }

    @Test fun searchCommandAndShortPage() = runTest {
        val runner = FakeRunner.ok(Fixtures.text("flat_search.json"))
        val page = extractor(runner).search("rick astley").ok()
        val cmd = runner.commands.single()
        assertEquals(listOf("--flat-playlist", "--playlist-start", "1", "--playlist-end", "20", "ytsearch20:rick astley"), cmd.takeLast(6))
        assertTrue("--dump-single-json" in cmd && "--no-warnings" in cmd)
        assertFalse("--no-playlist" in cmd)
        assertEquals(4, page.items.size, "5 entries, one of which is a channel")
        assertNull(page.continuation, "a short page is the last one")
    }

    @Test fun searchPagingFullPageGivesTokenAndNextCommandStartsAfterIt() = runTest {
        val runner = FakeRunner.ok(flat(20))
        val e = extractor(runner)
        val first = e.search("q").ok()
        assertEquals("ytdlp:21", first.continuation)

        e.search("q", first.continuation).ok()
        assertEquals(listOf("--playlist-start", "21", "--playlist-end", "40", "ytsearch40:q"), runner.commands.last().takeLast(5))
    }

    // Found live: a 20-entry page for "rick astley" contains a channel, so only 19 are videos.
    @Test fun aFullPageWithANonVideoEntryStillOffersTheNextPage() = runTest {
        val twenty = flat(19).dropLast(2) + """,{"_type":"url","id":"UCabc","url":"https://www.youtube.com/channel/UCabc"}]}"""
        val page = extractor(FakeRunner.ok(twenty)).search("q").ok()
        assertEquals(19, page.items.size)
        assertEquals("ytdlp:21", page.continuation)
    }

    @Test fun searchStopsOfferingMoreAtTheCap() = runTest {
        val runner = FakeRunner.ok(flat(20))
        val last = extractor(runner).search("q", "ytdlp:81").ok() // entries 81..100
        assertNull(last.continuation)
    }

    @Test fun channelCommandAndHeader() = runTest {
        val runner = FakeRunner.ok(Fixtures.text("flat_channel.json"))
        val page = extractor(runner).channel("UCuAXFkgsw1L7xaCfnd5JJOw").ok()
        assertEquals(
            listOf("--flat-playlist", "--playlist-start", "1", "--playlist-end", "30", "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw/videos"),
            runner.commands.single().takeLast(6),
        )
        assertEquals("Rick Astley", page.name)
        assertNull(page.continuation)
    }

    @Test fun channelFullPageOffersContinuationAndChannelVideosRedeemsIt() = runTest {
        val runner = FakeRunner.ok(flat(30))
        val e = extractor(runner)
        val token = assertNotNull(e.channel("UC1").ok().continuation)
        assertEquals("ytdlp:31", token)

        val next = e.channelVideos("UC1", token).ok()
        assertEquals(listOf("--playlist-start", "31", "--playlist-end", "60"), runner.commands.last().takeLast(5).take(4))
        assertEquals("ytdlp:61", next.continuation)
        assertEquals(30, next.items.size)
        assertEquals(1, extractor(FakeRunner.ok(flat(30))).channelVideos("UC1").ok().items.first().id.drop(1).toInt())
    }

    // ---- continuations ----

    @Test fun foreignOrDamagedTokensFailCleanlyWithoutRunningAnything() = runTest {
        val runner = FakeRunner.ok("{}")
        val e = extractor(runner)
        listOf("newpipe:abc", "ytdlp:abc", "ytdlp:0", "ytdlp:-5", "garbage").forEach { token ->
            listOf(e.search("q", token), e.channelVideos("UC1", token)).forEach { r ->
                val error = r.err()
                assertIs<ExtractorError.Unplayable>(error, token)
                assertEquals(ContinuationToken.FOREIGN_MESSAGE, error.message)
            }
        }
        assertTrue(runner.commands.isEmpty())
    }

    // ---- unsupported ----

    @Test fun commentsAndRepliesAreNotSupported() = runTest {
        val e = extractor(FakeRunner.ok("{}"))
        listOf(e.comments("v"), e.replies("v", "c")).forEach {
            val error = it.err()
            assertIs<ExtractorError.Unplayable>(error)
            assertEquals("not supported by yt-dlp tier", error.message)
        }
        assertFalse(e.capabilities.supportsCommentSort)
    }

    // ---- failures ----

    @Test fun nonZeroExitIsMappedFromStderr() = runTest {
        assertIs<ExtractorError.SignInRequired>(extractor(FakeRunner.failing(1, "ERROR: [youtube] x: Sign in to confirm you’re not a bot")).streams("x").err())
        assertIs<ExtractorError.RateLimited>(extractor(FakeRunner.failing(1, "ERROR: HTTP Error 429: Too Many Requests")).streams("x").err())
        assertIs<ExtractorError.Unplayable>(extractor(FakeRunner.failing(1, "ERROR: [youtube] x: Video unavailable")).videoInfo("x").err())
    }

    @Test fun missingBinaryIsTierUnavailable() = runTest {
        val e = extractor(FakeRunner { throw ProcessStartException("Cannot start yt-dlp: No such file") })
        val error = e.streams("x").err()
        assertIs<ExtractorError.TierUnavailable>(error)
        assertIs<ProcessStartException>(error.cause)
    }

    @Test fun timeoutIsANetworkFailure() = runTest {
        val error = extractor(FakeRunner { throw ProcessTimeoutException(60.seconds) }).streams("x").err()
        assertIs<ExtractorError.Network>(error)
        assertContains(error.message, "timed out")
    }

    @Test fun oversizedOutputIsAParseFailure() = runTest {
        assertIs<ExtractorError.ParseFailure>(extractor(FakeRunner { throw OutputTooLargeException(10) }).streams("x").err())
    }

    @Test fun garbageJsonIsAParseFailure() = runTest {
        assertIs<ExtractorError.ParseFailure>(extractor(FakeRunner.ok("not json")).streams("x").err())
        assertIs<ExtractorError.ParseFailure>(extractor(FakeRunner.ok("[1,2]")).streams("x").err())
        assertIs<ExtractorError.ParseFailure>(extractor(FakeRunner.ok("")).streams("x").err())
    }

    @Test fun cancellationIsRethrownNotMapped() = runTest {
        val e = extractor(FakeRunner { throw kotlinx.coroutines.CancellationException("cancelled") })
        assertFailsWith<kotlinx.coroutines.CancellationException> { e.streams("x") }
        Unit
    }
}
