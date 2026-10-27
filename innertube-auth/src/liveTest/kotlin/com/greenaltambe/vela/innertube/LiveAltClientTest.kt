package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.extractor.ExtractorResult
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tier 3 against the real service, no cookies needed. Prints the matrix that goes into the report. */
class LiveAltClientTest {
    private val videos = mapOf("normal" to "dQw4w9WgXcQ", "music" to "kJQP7kiw5Fk", "short" to "jNQXAC9IVRw", "live" to "jfKfPfyJRdk", "age-restricted(anon)" to "HtVdAasjOgU")
    private val transport = OkHttpAuthTransport()

    private fun rangeStatus(url: String, ua: String): Int =
        transport.execute("GET", url, mapOf("User-Agent" to ua, "Range" to "bytes=0-1023"), null).code

    @Test fun eachEnabledProfileMeasuredPerVideo() {
        val profiles = ClientProfile.load().filter { it.enabled }
        for (p in profiles) for ((label, id) in videos) {
            val x = AltClientExtractor(transport, { listOf(p) })
            val r = runBlocking { x.streams(id) }
            val line = when (r) {
                is ExtractorResult.Success -> {
                    val s = r.value.videoStreams.firstOrNull()?.url ?: r.value.audioStreams.first().url
                    "streams=${r.value.videoStreams.size}v/${r.value.audioStreams.size}a hls=${r.value.hlsUrl != null} GET=${rangeStatus(s, p.userAgent)}"
                }
                is ExtractorResult.Failure -> "${r.error.reason}: ${r.error.message}"
            }
            println("MATRIX ${p.name} | $label | $line")
        }
    }

    @Test fun orderedProfilesServeANormalVideoWithAPlayableStream() {
        val r = runBlocking { AltClientExtractor(transport).streams("dQw4w9WgXcQ") } as ExtractorResult.Success
        val s = r.value
        assertTrue(s.videoStreams.isNotEmpty() && s.audioStreams.isNotEmpty())
        assertEquals(206, rangeStatus(s.audioStreams.first().url, s.headers["User-Agent"]!!))
        println("MATRIX served by client=${s.client}")
    }
}
