package com.greenaltambe.vela.modules

import com.greenaltambe.vela.core.http.ApiClient
import com.greenaltambe.vela.core.http.FakeClock
import com.greenaltambe.vela.core.http.FakeTransport
import com.greenaltambe.vela.core.http.HttpResponse
import com.greenaltambe.vela.core.http.InMemoryApiCache
import com.greenaltambe.vela.core.http.ModuleLog
import com.greenaltambe.vela.core.http.TransportException
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.core.settings.TitleCasing
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.feed.FeedContext
import com.greenaltambe.vela.feed.FeedPipeline
import com.greenaltambe.vela.modules.dearrow.Branding
import com.greenaltambe.vela.modules.dearrow.DeArrow
import com.greenaltambe.vela.modules.dearrow.DeArrowStep
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.modules.dearrow.ThumbnailVote
import com.greenaltambe.vela.modules.dearrow.TitleVote
import com.greenaltambe.vela.modules.dearrow.formatTitle
import com.greenaltambe.vela.modules.dearrow.parseBrandingResponse
import com.greenaltambe.vela.modules.dearrow.pickThumbnailTime
import com.greenaltambe.vela.modules.dearrow.pickTitle
import com.greenaltambe.vela.modules.ryd.ReturnYouTubeDislike
import com.greenaltambe.vela.modules.ryd.Votes
import com.greenaltambe.vela.modules.ryd.VotesResult
import com.greenaltambe.vela.modules.ryd.compact
import com.greenaltambe.vela.modules.ryd.summary
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RydTest {
    private val transport = FakeTransport(Fixtures::server)
    private val settings = SettingsResolver(InMemorySettingsStore())
    private val log = ModuleLog()
    private val ryd = ReturnYouTubeDislike(ApiClient(transport, InMemoryApiCache(), FakeClock(), sleep = {}), settings, log)

    @Test fun parsesTheRealAnswer() = runTest {
        val v = assertIs<VotesResult.Known>(ryd.votes(Fixtures.SPONSORED_VIDEO)).votes
        assertEquals(18192, v.likes)
        assertEquals(519, v.dislikes)
        assertEquals(1_236_865L, v.viewCount)
        assertFalse(v.deleted)
        assertEquals("👍 18.1K  👎 519  ★ 4.8", v.summary())
    }

    @Test fun anUnknownVideoIsUnknownNotAnError() = runTest {
        assertEquals(VotesResult.Unknown, ryd.votes("neverseenvid"))
        assertEquals(VotesResult.Unknown, ryd.votes("neverseenvid"))
        assertEquals(1, transport.requests.size, "an unknown id is remembered")
    }

    @Test fun anAllZeroAnswerMeansTheServiceKnowsNothing() = runTest {
        transport.handler = { HttpResponse(200, """{"id":"zzz","likes":0,"dislikes":0,"rating":0.0,"viewCount":0,"deleted":false}""") }
        assertEquals(VotesResult.Unknown, ryd.votes("zzz"))
        assertNull(ryd.cached("zzz"))
    }

    @Test fun repeatedLookupsAreServedFromMemory() = runTest {
        ryd.votes(Fixtures.SPONSORED_VIDEO)
        ryd.votes(Fixtures.SPONSORED_VIDEO)
        assertEquals(1, transport.requests.size)
        assertEquals(18192, ryd.cached(Fixtures.SPONSORED_VIDEO)?.likes)
    }

    @Test fun rateLimitingAndOutagesMakeItInactiveAndAreLogged() = runTest {
        transport.handler = { HttpResponse(429, "") }
        assertIs<VotesResult.Unavailable>(ryd.votes("abc"))
        transport.handler = { throw TransportException("down") }
        assertIs<VotesResult.Unavailable>(ryd.votes("def"))
        assertFalse(log.status().getValue("ryd").ok)
        assertNull(ryd.cached("abc"))
    }

    @Test fun unreadableAnswersAreUnavailable() = runTest {
        transport.handler = { HttpResponse(200, "<html>") }
        assertIs<VotesResult.Unavailable>(ryd.votes("abc"))
    }

    @Test fun prefetchIsBatchedAndNeverExceedsTheConcurrencyLimitOrRepeatsKnownIds() = runTest {
        val inFlight = AtomicInteger(); var peak = 0
        val slow = object : com.greenaltambe.vela.core.http.HttpTransport {
            override suspend fun execute(request: com.greenaltambe.vela.core.http.HttpRequest): HttpResponse {
                val n = inFlight.incrementAndGet(); synchronized(this) { peak = maxOf(peak, n) }
                delay(10); inFlight.decrementAndGet()
                return HttpResponse(200, """{"likes":1,"dislikes":2,"rating":3.0,"viewCount":4}""")
            }
        }
        val limited = ReturnYouTubeDislike(ApiClient(slow, InMemoryApiCache(), FakeClock(), sleep = {}), settings, log, concurrency = 3)
        val ids = (1..12).map { "v$it" }
        limited.prefetch(ids + ids)
        assertTrue(peak in 2..3, "peak $peak")
        assertEquals(1, limited.cached("v7")?.likes)
        val before = limited.revision.value
        limited.prefetch(ids)
        assertEquals(before, limited.revision.value, "nothing new, nothing fetched")
    }

    @Test fun disabledModuleAsksForNothing() = runTest {
        settings.set(Settings.returnYouTubeDislikeEnabled, false)
        assertIs<VotesResult.Unavailable>(ryd.votes(Fixtures.SPONSORED_VIDEO))
        ryd.prefetch(listOf("a", "b"))
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun compactCounts() {
        assertEquals("999", compact(999)); assertEquals("1K", compact(1000)); assertEquals("1.2K", compact(1234))
        assertEquals("3.4M", compact(3_400_000)); assertEquals("2B", compact(2_000_000_000))
        assertNull(Votes.parse("{}"))
    }
}

class DeArrowTest {
    private val transport = FakeTransport(Fixtures::server)
    private val settings = SettingsResolver(InMemorySettingsStore())
    private val log = ModuleLog()
    private val dearrow = DeArrow(ApiClient(transport, InMemoryApiCache(), FakeClock(), sleep = {}), settings, log)
    private val video = Fixtures.SPONSORED_VIDEO

    // DeArrow is off by default (owner decision); these tests are about what it does when the viewer turned it on.
    init { settings.set(Settings.deArrowEnabled, true) }

    private fun summary(id: String = video, title: String = "Original CLICKBAIT title") =
        VideoSummary(id, title, "UC", "chan", thumbnailUrl = "https://i.ytimg.com/vi/$id/hq.jpg")

    // ---- parsing the real answer ----

    @Test fun isOffByDefault() = assertFalse(DeArrow(ApiClient(transport, InMemoryApiCache(), FakeClock(), sleep = {}), SettingsResolver(InMemorySettingsStore()), log).isEnabled(SettingsResolver(InMemorySettingsStore())))

    @Test fun parsesTheRealPrefixAnswerAndFindsTheVideo() {
        val all = parseBrandingResponse(Fixtures.text("dearrow_branding_efd7.json"))
        val b = all.getValue(video)
        assertEquals("iPhone 18 Pro review", b.titles.single().title)
        assertEquals(59.0, b.thumbnails.single().timestampSeconds)
        assertEquals(950.581, b.videoDurationSeconds)
        assertTrue(all.size > 1, "other videos with the same prefix come along")
    }

    @Test fun garbageIsNoBranding() {
        assertTrue(parseBrandingResponse("<html>").isEmpty())
        assertTrue(parseBrandingResponse("[]").isEmpty())
    }

    // ---- vote and lock handling ----

    private fun title(text: String, votes: Int = 0, locked: Boolean = false, original: Boolean = false) = TitleVote(text, original, votes, locked)

    @Test fun lockedAndTrustedTitlesAreUsedOthersFallBackToTheOriginal() {
        assertEquals("A", Branding(titles = listOf(title("A", votes = 0))).pickTitle(0))
        assertNull(Branding(titles = listOf(title("A", votes = -1))).pickTitle(0), "downvoted")
        assertEquals("A", Branding(titles = listOf(title("A", votes = -5, locked = true))).pickTitle(0), "locked beats votes")
        assertNull(Branding(titles = listOf(title("A", votes = 1))).pickTitle(3), "below the user's threshold")
        assertNull(Branding(titles = listOf(title("A", votes = 9, original = true))).pickTitle(0), "the community chose the original")
        assertNull(Branding().pickTitle(0))
    }

    @Test fun lockedTitlesRankFirstThenVotes() {
        val b = Branding(titles = listOf(title("many", votes = 9), title("locked", votes = 0, locked = true), title("some", votes = 3)))
        assertEquals("locked", b.pickTitle(0))
        assertEquals("many", Branding(titles = listOf(title("some", votes = 3), title("many", votes = 9))).pickTitle(0))
    }

    @Test fun thumbnailTimeFollowsTheSameRulesAndGeneratedFramesAreOptIn() {
        assertEquals(12.5, Branding(thumbnails = listOf(ThumbnailVote(12.5, false, 1, false))).pickThumbnailTime(0, false))
        assertNull(Branding(thumbnails = listOf(ThumbnailVote(null, true, 5, false))).pickThumbnailTime(0, true), "original wins")
        assertNull(Branding(thumbnails = listOf(ThumbnailVote(3.0, false, -2, false))).pickThumbnailTime(0, false))
        val onlyRandom = Branding(randomTime = 0.5, videoDurationSeconds = 200.0)
        assertNull(onlyRandom.pickThumbnailTime(0, allowGenerated = false))
        assertEquals(100.0, onlyRandom.pickThumbnailTime(0, allowGenerated = true))
    }

    // ---- casing ----

    @Test fun unformatMarkersAreRemovedAndProtectWords() {
        assertEquals("Why iPhone sales fall", formatTitle("Why >iPhone sales fall", TitleCasing.AS_SUBMITTED))
        assertEquals("Why iPhone Sales Fall", formatTitle("why >iPhone sales fall", TitleCasing.TITLE_CASE))
        assertEquals("Why iPhone sales fall", formatTitle("WHY >iPhone SALES FALL", TitleCasing.SENTENCE_CASE), "an all-caps title is lowered")
    }

    @Test fun titleCaseKeepsSmallWordsLowerExceptFirstAndLast() {
        assertEquals("The Rise and Fall of the Empire", formatTitle("the rise and fall of the empire", TitleCasing.TITLE_CASE))
    }

    @Test fun sentenceCaseLowersTheRestButKeepsAcronymsAndBrands() {
        assertEquals("Nasa and NASA test YouTube", formatTitle("Nasa And NASA Test YouTube", TitleCasing.SENTENCE_CASE))
        assertEquals("Fbi and CIA", formatTitle("fbi and CIA", TitleCasing.SENTENCE_CASE), "only words already in capitals count as acronyms")
    }

    // ---- the service and the feed step ----

    @Test fun prefetchFillsTheCacheWithOneRequestPerPrefixAndSendsNoVideoId() = runTest {
        dearrow.prefetch(listOf(video, "other-video"))
        val urls = transport.requests.map { it.url }
        assertTrue(urls.all { "/api/branding/" in it })
        assertTrue(urls.none { video in it })
        assertTrue(dearrow.cached(video)!!.titles.isNotEmpty())
        assertEquals(Branding.NONE, dearrow.cached("other-video"), "asked, nothing there: remembered as none")
        val count = transport.requests.size
        dearrow.prefetch(listOf(video, "other-video"))
        assertEquals(count, transport.requests.size, "known ids are not asked again")
    }

    @Test fun titleAndThumbnailAreReplacedOnlyOncePrefetchedAndOnlyOnEnabledSurfaces() = runTest {
        assertEquals("Original", dearrow.title(video, "Original", DeArrowSurface.FEED), "nothing known yet: the original shows at once")
        dearrow.prefetch(listOf(video))
        assertEquals("iPhone 18 Pro review", dearrow.title(video, "Original", DeArrowSurface.FEED))
        assertEquals("https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID=$video&time=59.0", dearrow.thumbnail(video, DeArrowSurface.FEED))

        settings.set(Settings.deArrowSearch, false)
        assertEquals("Original", dearrow.title(video, "Original", DeArrowSurface.SEARCH))
        assertNull(dearrow.thumbnail(video, DeArrowSurface.SEARCH))
        assertEquals("iPhone 18 Pro review", dearrow.title(video, "Original", DeArrowSurface.VIDEO_PAGE))
    }

    @Test fun feedStepSwapsInBrandingKeepsTheOriginalAndLeavesOthersAlone() = runTest {
        dearrow.prefetch(listOf(video, "plain"))
        val out = FeedPipeline(listOf(DeArrowStep(dearrow, DeArrowSurface.FEED))).run(listOf(summary(), summary("plain", "Plain")), FeedContext(settings))
        assertEquals("iPhone 18 Pro review", out[0].title)
        assertEquals("Original CLICKBAIT title", out[0].originalTitle)
        assertTrue(out[0].thumbnailUrl!!.contains("dearrow-thumb"))
        assertEquals("https://i.ytimg.com/vi/$video/hq.jpg", out[0].originalThumbnailUrl)
        assertEquals(summary("plain", "Plain"), out[1])
    }

    @Test fun feedStepDoesNothingBeforePrefetchOrWhenDisabled() = runTest {
        val before = FeedPipeline(listOf(DeArrowStep(dearrow, DeArrowSurface.FEED))).run(listOf(summary()), FeedContext(settings))
        assertEquals(summary(), before.single())
        dearrow.prefetch(listOf(video))
        settings.set(Settings.deArrowEnabled, false)
        assertEquals(summary(), FeedPipeline(listOf(DeArrowStep(dearrow, DeArrowSurface.FEED))).run(listOf(summary()), FeedContext(settings)).single())
    }

    @Test fun anOutageLeavesOriginalsAndIsVisibleInDiagnostics() = runTest {
        transport.handler = { throw TransportException("offline") }
        dearrow.prefetch(listOf(video))
        assertEquals("Original", dearrow.title(video, "Original", DeArrowSurface.FEED))
        assertFalse(log.status().getValue("dearrow").ok)
        transport.handler = Fixtures::server
        dearrow.prefetch(listOf(video))
        assertEquals("iPhone 18 Pro review", dearrow.title(video, "Original", DeArrowSurface.FEED), "recovers when the network returns")
    }

    @Test fun thumbnailSuggestionIsSentOnlyWhenCalledWithTheFrameAndDuration() = runTest {
        val result = dearrow.submitThumbnail(video, 59_000, 950_581, "u".repeat(36))
        assertIs<com.greenaltambe.vela.core.http.ApiResult.Ok>(result)
        val post = transport.requests.single()
        assertEquals("POST", post.method)
        assertTrue(post.url.endsWith("/api/branding"))
        assertTrue("\"timestamp\":59.0" in post.body!! && "\"videoID\":\"$video\"" in post.body!!, post.body)
    }

    @Test fun anOutageDuringSubmissionIsAFailureResult() = runTest {
        transport.handler = { throw TransportException("offline") }
        assertIs<com.greenaltambe.vela.core.http.ApiResult.Failed>(dearrow.submitThumbnail(video, 1_000, 100_000, "u".repeat(36)))
    }
}
