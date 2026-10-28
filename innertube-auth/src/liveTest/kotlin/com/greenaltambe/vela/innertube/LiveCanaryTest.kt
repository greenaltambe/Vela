package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.extractor.chain.ExtractorTier
import com.greenaltambe.vela.extractor.chain.TierId
import com.greenaltambe.vela.health.ExtractorHealth
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Regression canary: the alternate-client tier must keep serving streams that answer HTTP 206 WITHOUT a PO token. The day
 * this fails, YouTube started requiring tokens (or SABR) for these clients and tier 3 needs a PoTokenProvider.
 */
class LiveCanaryTest {
    @Test fun altClientStillReturns206WithoutAToken() {
        val health = ExtractorHealth(listOf(ExtractorTier(TierId.ALT_CLIENT, AltClientExtractor())), OkHttpStreamProbe())
        runBlocking { health.run() }
        health.results.value.forEach { println("CANARY ${it.check} ok=${it.ok} ${it.millis}ms ${it.detail ?: ""}") }
        val gets = health.results.value.filter { it.check.startsWith("GET") }
        assertTrue(gets.isNotEmpty() && gets.all { it.ok }, "alt client no longer yields playable (206) URLs without a token: ${gets.filter { !it.ok }}")
    }
}
