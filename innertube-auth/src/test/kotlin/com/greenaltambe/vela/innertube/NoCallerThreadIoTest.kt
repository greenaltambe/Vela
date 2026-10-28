package com.greenaltambe.vela.innertube

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.test.*

/**
 * Structural guard for the Android NetworkOnMainThreadException bug: whatever thread calls an entry point (a single
 * thread named "pretend-main" stands in for Android's main thread), the blocking transport must run elsewhere.
 */
class NoCallerThreadIoTest {
    private val main = Executors.newSingleThreadExecutor { Thread(it, "pretend-main") }
    @AfterTest fun stop() = main.shutdownNow().let {}

    private val ok = """{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"v","title":"T"},"streamingData":{"formats":[{"itag":18,"url":"https://rr.example/v","mimeType":"video/mp4; codecs=\"avc1\"","bitrate":1,"width":1,"height":1}]}}"""

    private fun profile() = listOf(ClientProfile("P", 1, "UA", kotlinx.serialization.json.buildJsonObject { put("clientName", kotlinx.serialization.json.JsonPrimitive("P")) }, true))

    @Test fun altClientNeverRunsTheTransportOnTheCallersThread() {
        val threads = mutableListOf<String>()
        val t = FakeTransport { _, _, _, _ -> threads += Thread.currentThread().name; RawResponse(200, ok, emptyMap(), "") }
        val alt = AltClientExtractor(t, { profile() })
        runBlocking(main.asCoroutineDispatcher()) { alt.streams("v"); alt.videoInfo("v") }
        assertEquals(2, threads.size)
        assertTrue(threads.none { it == "pretend-main" }, "transport ran on $threads")
    }

    @Test fun streamProbeNeverRunsOnTheCallersThread() {
        var thread = ""
        val probe = OkHttpStreamProbe(FakeTransport { _, _, _, _ -> thread = Thread.currentThread().name; RawResponse(206, "", emptyMap(), "") })
        runBlocking(main.asCoroutineDispatcher()) { probe.status("https://rr.example/v", emptyMap()) }
        assertNotEquals("pretend-main", thread)
    }
}
