package com.greenaltambe.vela.library

import com.greenaltambe.vela.data.*
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.feed.FeedService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class NewUploadNotificationTest {
    private val now = Instant.fromEpochSeconds(10_000_000)
    private val clock = object : Clock { override fun now() = now }
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val subs = SubscriptionRepository(db)
    private val notes = NotificationRepository(db, "local")
    private var listing = mapOf<String, List<VideoSummary>>()
    private val extractor = object : Extractor by StubExtractor() {
        override suspend fun recentUploads(channelId: String) = ExtractorResult.Success(listing[channelId].orEmpty())
    }
    private fun v(id: String, ch: String, age: kotlin.time.Duration) = VideoSummary(id, id, ch, ch, uploadedAt = now - age)
    private fun service() = FeedService(extractor, subs, FeedRepository(db), clock, notifications = notes)

    @Test fun onlyRecentUploadsOfAlreadyKnownChannelsAreAnnounced() = runTest {
        subs.subscribe("a", "a", null, now)
        listing = mapOf("a" to listOf(v("a-old1", "a", 20.days)))
        service().refresh() // first fill: seeds the cache, announces nothing
        subs.subscribe("b", "b", null, now) // a channel subscribed after that: its recent list is not "new"
        listing = mapOf(
            "a" to listOf(v("a-old1", "a", 20.days), v("a-fresh", "a", 1.hours), v("a-weeks", "a", 30.days)),
            "b" to listOf(v("b-1", "b", 2.hours)),
        )
        service().refresh()
        assertEquals(listOf("a-fresh"), notes.recent().map { it.videoId })
    }
}
