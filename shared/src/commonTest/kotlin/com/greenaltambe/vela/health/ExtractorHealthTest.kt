package com.greenaltambe.vela.health

import com.greenaltambe.vela.chaos.ChaosExtractor
import com.greenaltambe.vela.chaos.Fault.*
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.extractor.chain.ExtractorTier
import com.greenaltambe.vela.extractor.chain.TierId
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** A tier that works: one search hit and one playable stream. */
private fun good(): Extractor = object : Extractor by StubExtractor() {
    override suspend fun search(query: String, continuation: String?) = ExtractorResult.Success(Page(listOf(VideoSummary("v", "T", "c", "C"))))
    override suspend fun streams(videoId: String) = ExtractorResult.Success(StreamSet(listOf(VideoStream("https://rr.example/v", "video/mp4", "avc1", 1, 640, 360, 30)), emptyList()))
}

class ExtractorHealthTest {
    private val clock = FakeClock()

    @Test fun healthyTierPassesEveryCheckAndFailingTierShowsItsReason() = runTest {
        val h = ExtractorHealth(listOf(ExtractorTier(TierId.ANONYMOUS, good()), ExtractorTier(TierId.ALT_CLIENT, ChaosExtractor(FORBIDDEN_403))), { _, _ -> 206 }, clock = clock)
        h.run()
        val (a, alt) = h.summary()
        assertTrue(a.healthy && a.lastFailure == null)
        assertFalse(alt.healthy); assertEquals("HTTP 403 page", alt.lastFailureReason)
    }

    @Test fun theAccountTierIsNeverProbed() = runTest {
        val auth = ChaosExtractor(OK)
        ExtractorHealth(listOf(ExtractorTier(TierId.ANONYMOUS, good()), ExtractorTier(TierId.AUTHENTICATED, auth)), { _, _ -> 206 }, clock = clock).run()
        assertEquals(0, auth.calls)
    }

    @Test fun aStreamUrlThatDoesNot206IsAFailureOfThatCheck() = runTest {
        val h = ExtractorHealth(listOf(ExtractorTier(TierId.ANONYMOUS, good())), { _, _ -> 403 }, clock = clock)
        h.run()
        assertTrue(h.results.value.any { it.check.startsWith("GET") && !it.ok && it.detail == "HTTP 403" })
    }

    @Test fun anEmptyStreamSetIsReportedAsPossibleSabr() = runTest {
        val noStreams = object : Extractor by good() {
            override suspend fun streams(videoId: String) = ExtractorResult.Success(StreamSet(emptyList(), emptyList()))
        }
        val h = ExtractorHealth(listOf(ExtractorTier(TierId.ANONYMOUS, noStreams)), { _, _ -> 206 }, clock = clock)
        h.run()
        assertTrue(h.results.value.any { it.detail?.contains("SABR") == true })
    }

    @Test fun staleAfterSevenDaysAndResultsPersistThroughTheCodec() = runTest {
        val store = InMemoryHealthStore()
        val h = ExtractorHealth(listOf(ExtractorTier(TierId.ANONYMOUS, good())), { _, _ -> 206 }, store, clock)
        assertTrue(h.isStale(), "never run counts as stale")
        h.run(); assertFalse(h.isStale())
        clock.advance(8.days); assertTrue(h.isStale())
        assertEquals(h.results.value, ExtractorHealth(emptyList(), { _, _ -> 0 }, store, clock).results.value)
        assertEquals(h.results.value.map { it.copy(detail = it.detail) }, HealthCodec.decode(HealthCodec.encode(h.results.value)))
    }

    @Test fun newPipeWarnsAfterSixtyDaysOnly() {
        assertNull(NewPipeVersion.warning(Instant.parse("2026-09-01T00:00:00Z")))
        assertTrue(NewPipeVersion.warning(Instant.parse("2026-10-28T00:00:00Z"))!!.contains("scripts/bump-newpipe.sh"))
        assertEquals(74L, NewPipeVersion.ageDays(Instant.parse("2026-10-28T00:00:00Z")))
    }
}
