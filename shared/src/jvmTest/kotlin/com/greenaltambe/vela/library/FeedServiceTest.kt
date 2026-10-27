package com.greenaltambe.vela.library

import com.greenaltambe.vela.data.FeedRepository
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.SubscriptionRepository
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.feed.FeedService
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private class RecordingExtractor : Extractor by StubExtractor() {
    val inFlight = AtomicInteger()
    var maxInFlight = 0
    val failing = mutableSetOf<String>()
    var flagShort = false

    override suspend fun recentUploads(channelId: String): ExtractorResult<List<VideoSummary>> {
        val now = inFlight.incrementAndGet()
        synchronized(this) { maxInFlight = maxOf(maxInFlight, now) }
        try {
            delay(10)
            if (channelId in failing) return ExtractorResult.Failure(ExtractorError.Network("down"))
            return ExtractorResult.Success(listOf(
                VideoSummary("$channelId-1", "first", channelId, "n$channelId", uploadedAt = Instant.fromEpochSeconds(200), isShort = flagShort),
                VideoSummary("$channelId-2", "second", channelId, "n$channelId", uploadedAt = Instant.fromEpochSeconds(100), isShort = flagShort),
            ))
        } finally { inFlight.decrementAndGet() }
    }
}

class FeedServiceTest {
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val subs = SubscriptionRepository(db)
    private val cache = FeedRepository(db)
    private val extractor = RecordingExtractor()
    private fun service(concurrency: Int = 3) = FeedService(extractor, subs, cache, concurrency = concurrency)
    private fun subscribe(vararg ids: String) = ids.forEach { subs.subscribe(it, "n$it", null, Instant.fromEpochSeconds(1)) }

    @Test fun concurrencyIsLimited() = runTest {
        subscribe(*(1..12).map { "c$it" }.toTypedArray())
        service(concurrency = 3).refresh()
        assertTrue(extractor.maxInFlight in 2..3, "saw ${extractor.maxInFlight}")
    }

    @Test fun oneFailingChannelDoesNotFailTheFeedAndKeepsItsCache() = runTest {
        subscribe("a", "b")
        val first = service()
        first.refresh()
        assertEquals(4, first.entries.value.size)

        extractor.failing += "b"
        val second = service()
        assertEquals(4, second.entries.value.size, "the cache is there before any refresh")
        second.refresh()
        assertEquals(setOf("b"), second.failures.value.keys)
        assertEquals(4, second.entries.value.size, "b's entries stay")
        assertEquals(listOf("a-1", "b-1", "a-2", "b-2").sorted(), second.entries.value.map { it.video.id }.sorted())
    }

    @Test fun unsubscribedChannelsDropOutOnRefresh() = runTest {
        subscribe("a", "b")
        service().refresh()
        subs.unsubscribe("b")
        val s = service()
        s.refresh()
        assertEquals(setOf("a"), s.entries.value.map { it.video.channelId }.toSet())
    }

    @Test fun newestFirstByPublishDate() = runTest {
        subscribe("a")
        val s = service()
        s.refresh()
        assertEquals(listOf("a-1", "a-2"), s.entries.value.map { it.video.id })
    }

    @Test fun shortsFlaggedBySourceSurviveIntoTheCache() = runTest {
        subscribe("a")
        extractor.flagShort = true
        val s = service()
        s.refresh()
        assertTrue(s.entries.value.all { it.video.isShort })
        assertTrue(FeedService(extractor, subs, cache).entries.value.all { it.video.isShort }, "and the reloaded cache")
    }

    @Test fun refreshDoesNotOverlapItself() = runTest {
        subscribe("a")
        val s = service()
        coroutineScope {
            val one = launch { s.refresh() }
            val two = launch { s.refresh() }
            one.join(); two.join()
        }
        assertTrue(extractor.maxInFlight <= 1)
    }
}
