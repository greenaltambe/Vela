package com.greenaltambe.vela.extractor.chain

import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Failures the debug menu can make the anonymous sources produce, so the failure paths can be exercised without waiting for YouTube. */
enum class SimulatedFault(val label: String) {
    NONE("No simulated failure"),
    RATE_LIMIT("HTTP 429 from every source"),
    CAPTCHA("Captcha page from every source"),
    OFFLINE("Offline (network error) from every source"),
    /** One-shot: the next stream resolve returns URLs that no longer work, then everything is normal again. */
    EXPIRED_URL("Expired stream URLs (next resolve only)"),
}

/** The switch the debug menu flips. Shared by every wrapped tier; only exists in debug builds / when VELA_DEBUG is set. */
class FaultInjector {
    private val _fault = MutableStateFlow(SimulatedFault.NONE)
    val fault: StateFlow<SimulatedFault> = _fault
    fun set(f: SimulatedFault) { _fault.value = f }
    internal fun consumeExpired(): Boolean = _fault.value == SimulatedFault.EXPIRED_URL && run { _fault.value = SimulatedFault.NONE; true }
}

/** Wraps a tier's extractor and answers with the simulated failure while one is set. Never wraps the account tier (it sends nothing anyway). */
class FaultInjectingExtractor(private val inner: Extractor, private val injector: FaultInjector) : Extractor by inner {
    private fun <T> failing(): ExtractorResult.Failure? = when (injector.fault.value) {
        SimulatedFault.RATE_LIMIT -> ExtractorResult.Failure(ExtractorError.RateLimited("simulated HTTP 429"))
        SimulatedFault.CAPTCHA -> ExtractorResult.Failure(ExtractorError.Captcha("simulated captcha page"))
        SimulatedFault.OFFLINE -> ExtractorResult.Failure(ExtractorError.Network("simulated: network is unreachable"))
        else -> null
    }

    override suspend fun streams(videoId: String): ExtractorResult<StreamSet> {
        failing<StreamSet>()?.let { return it }
        val r = inner.streams(videoId)
        if (r is ExtractorResult.Success && injector.consumeExpired()) {
            val dead = "https://invalid.invalid/expired"
            return ExtractorResult.Success(r.value.copy(videoStreams = r.value.videoStreams.map { it.copy(url = dead) }, audioStreams = r.value.audioStreams.map { it.copy(url = dead) }, hlsUrl = null))
        }
        return r
    }
    override suspend fun videoInfo(videoId: String) = failing<VideoInfo>() ?: inner.videoInfo(videoId)
    override suspend fun search(query: String, continuation: String?) = failing<Page<VideoSummary>>() ?: inner.search(query, continuation)
}

fun List<ExtractorTier>.withFaults(injector: FaultInjector?): List<ExtractorTier> =
    if (injector == null) this else map { if (it.id == TierId.AUTHENTICATED) it else ExtractorTier(it.id, FaultInjectingExtractor(it.extractor, injector), it.version) }
