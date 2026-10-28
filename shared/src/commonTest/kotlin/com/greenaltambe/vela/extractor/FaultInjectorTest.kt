package com.greenaltambe.vela.extractor

import com.greenaltambe.vela.core.settings.AccountFallbackMode
import com.greenaltambe.vela.extractor.chain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class FaultInjectorTest {
    private val ok = object : Extractor by StubExtractor() {
        override suspend fun streams(videoId: String) = ExtractorResult.Success(StreamSet(listOf(VideoStream("https://good/v", "video/mp4", "avc1", 1, 640, 360, 30)), listOf(AudioStream("https://good/a", "audio/mp4", "aac", 1)), client = "stub"))
    }
    private val injector = FaultInjector()
    private fun chain() = FallbackChain(listOf(ExtractorTier(TierId.ANONYMOUS, ok)).withFaults(injector), { AccountFallbackMode.ONLY_ON_FAILURE }, DiagnosticsLog())

    @Test fun eachFaultProducesItsOwnError() = runTest {
        for ((fault, reason) in listOf(SimulatedFault.RATE_LIMIT to FailureReason.RATE_LIMITED, SimulatedFault.CAPTCHA to FailureReason.CAPTCHA, SimulatedFault.OFFLINE to FailureReason.NETWORK)) {
            injector.set(fault)
            assertEquals(reason, assertIs<ExtractorResult.Failure>(chain().streams("x")).error.reason, fault.name)
        }
        injector.set(SimulatedFault.NONE)
        assertIs<ExtractorResult.Success<*>>(chain().streams("x"))
    }

    @Test fun expiredUrlsAreOneShot() = runTest {
        injector.set(SimulatedFault.EXPIRED_URL)
        val first = assertIs<ExtractorResult.Success<StreamSet>>(chain().streams("x")).value
        assertEquals("https://invalid.invalid/expired", first.videoStreams.single().url)
        assertEquals(SimulatedFault.NONE, injector.fault.value)
        assertEquals("https://good/v", assertIs<ExtractorResult.Success<StreamSet>>(chain().streams("x")).value.videoStreams.single().url)
    }

    @Test fun withoutAnInjectorTheTiersAreUntouched() {
        val tiers = listOf(ExtractorTier(TierId.ANONYMOUS, ok))
        assertSame(tiers, tiers.withFaults(null))
    }

    @Test fun theAccountTierIsNeverWrapped() {
        val auth = ExtractorTier(TierId.AUTHENTICATED, ok)
        assertSame(auth.extractor, listOf(auth).withFaults(injector).single().extractor)
    }
}
