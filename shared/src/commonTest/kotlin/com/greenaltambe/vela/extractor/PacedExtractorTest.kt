package com.greenaltambe.vela.extractor

import com.greenaltambe.vela.extractor.chain.PacedExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PacedExtractorTest {
    @Test fun limitsConcurrencyAndSpacesBurstStarts() = runTest {
        val clock = FakeClock(); val starts = mutableListOf<Long>(); var running = 0; var peak = 0
        val inner = object : Extractor by StubExtractor() {
            override suspend fun videoInfo(videoId: String): ExtractorResult<VideoInfo> {
                starts += clock.now().toEpochMilliseconds(); running++; peak = maxOf(peak, running)
                delay(10); running--
                return ExtractorResult.Success(VideoInfo(videoId, "t", "", "c", "C", 0))
            }
        }
        val p = PacedExtractor(inner, maxConcurrent = 2, minSpacingMs = 100, clock = clock, sleeper = { clock.advance(kotlin.time.Duration.parse("${it}ms")) })
        List(6) { async { p.videoInfo("v$it") } }.awaitAll()
        assertTrue(peak <= 2, "peak $peak")
        assertTrue(starts.zipWithNext().all { (a, b) -> b - a >= 100 }, "starts $starts")
    }

    @Test fun aSingleCallIsNotDelayed() = runTest {
        val clock = FakeClock(); var slept = 0L
        PacedExtractor(StubExtractor(), clock = clock, sleeper = { slept += it }).search("q")
        assertEquals(0, slept)
    }
}
