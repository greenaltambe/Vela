package com.greenaltambe.vela.core

import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.extractor.FailureReason
import com.greenaltambe.vela.extractor.FakeClock
import com.greenaltambe.vela.extractor.chain.TierId
import com.greenaltambe.vela.extractor.chain.TierMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

class ConcurrencyTest {
    private val workers = 64
    private val perWorker = 500

    @Test
    fun settingsStoreKeepsEveryConcurrentWrite() = runBlocking {
        val store = InMemorySettingsStore()
        (0 until workers).map { w ->
            async(Dispatchers.Default) {
                repeat(perWorker) { i -> store.put("k$w-$i", SettingLayer.Global, "$w:$i") }
            }
        }.awaitAll()
        for (w in 0 until workers) for (i in 0 until perWorker) {
            assertEquals("$w:$i", store.get("k$w-$i", SettingLayer.Global))
        }
    }

    @Test
    fun settingsStoreRemovesUnderContention() = runBlocking {
        val store = InMemorySettingsStore()
        repeat(perWorker) { store.put("k$it", SettingLayer.video("v"), "x") }
        (0 until workers).map { w ->
            async(Dispatchers.Default) {
                repeat(perWorker) { i ->
                    if (i % workers == w) store.remove("k$i", SettingLayer.video("v"))
                    else store.put("other$w-$i", SettingLayer.Global, "y")
                }
            }
        }.awaitAll()
        repeat(perWorker) { assertEquals(null, store.get("k$it", SettingLayer.video("v"))) }
        for (w in 0 until workers) for (i in 0 until perWorker) {
            if (i % workers != w) assertEquals("y", store.get("other$w-$i", SettingLayer.Global))
        }
    }

    @Test
    fun tierMemoryHandlesConcurrentRememberAndLookup() = runBlocking {
        val memory = TierMemory(FakeClock(), 10.minutes)
        val reasons = FailureReason.entries
        val available = TierId.entries.toSet()
        (0 until workers).map { w ->
            async(Dispatchers.Default) {
                repeat(perWorker) { i ->
                    memory.remember(TierId.ANONYMOUS, reasons[(w + i) % reasons.size], TierId.AUTHENTICATED)
                    memory.routeAround(TierId.ANONYMOUS, available)
                    if (i % 50 == 0) memory.forgetWorked(TierId.YT_DLP)
                }
            }
        }.awaitAll()
        // Every remember targeted AUTHENTICATED and forgetWorked(YT_DLP) never touches it, so no write may be lost.
        assertNotNull(memory.routeAround(TierId.ANONYMOUS, available))
        memory.forgetWorked(TierId.AUTHENTICATED)
        assertEquals(null, memory.routeAround(TierId.ANONYMOUS, available))
    }
}
