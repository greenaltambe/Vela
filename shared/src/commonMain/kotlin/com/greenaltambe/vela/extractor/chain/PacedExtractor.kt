package com.greenaltambe.vela.extractor.chain

import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock

/**
 * Anonymous pacing: at most [maxConcurrent] calls in flight and at least [minSpacingMs] between the starts of two calls,
 * so a feed refresh or a burst of searches from one IP does not look like a scraper. Wraps a tier; the account tier has
 * its own, stricter governor instead.
 */
class PacedExtractor(
    private val inner: Extractor,
    maxConcurrent: Int = 4,
    private val minSpacingMs: Long = 120,
    private val clock: Clock = Clock.System,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) : Extractor {
    private val slots = Semaphore(maxConcurrent)
    private val gate = Mutex()
    private var lastStart = 0L

    override val capabilities get() = inner.capabilities

    private suspend fun <T> paced(call: suspend () -> T): T = slots.withPermit {
        gate.withLock {
            val wait = lastStart + minSpacingMs - clock.now().toEpochMilliseconds()
            if (wait > 0) sleeper(wait)
            lastStart = clock.now().toEpochMilliseconds()
        }
        call()
    }

    override suspend fun search(query: String, continuation: String?) = paced { inner.search(query, continuation) }
    override suspend fun videoInfo(videoId: String) = paced { inner.videoInfo(videoId) }
    override suspend fun streams(videoId: String) = paced { inner.streams(videoId) }
    override suspend fun channel(channelId: String) = paced { inner.channel(channelId) }
    override suspend fun channelVideos(channelId: String, continuation: String?) = paced { inner.channelVideos(channelId, continuation) }
    override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?) = paced { inner.comments(videoId, sort, continuation) }
    override suspend fun replies(videoId: String, commentId: String, continuation: String?) = paced { inner.replies(videoId, commentId, continuation) }
    override suspend fun trending(continuation: String?) = paced { inner.trending(continuation) }
    override suspend fun related(videoId: String) = paced { inner.related(videoId) }
    override suspend fun channelShorts(channelId: String, continuation: String?) = paced { inner.channelShorts(channelId, continuation) }
    override suspend fun recentUploads(channelId: String) = paced { inner.recentUploads(channelId) }
}
