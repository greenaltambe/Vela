package com.greenaltambe.vela.feed

import com.greenaltambe.vela.data.FeedRepository
import com.greenaltambe.vela.data.SubscriptionRepository
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock

/**
 * The subscription feed: [entries] starts as whatever is cached (so the screen has content at once), [refresh] then
 * fetches each subscribed channel's recent uploads, at most [concurrency] at a time. A channel that fails only
 * leaves its own entries stale; it is listed in [failures] and never fails the refresh.
 */
class FeedService(
    private val extractor: Extractor,
    private val subscriptions: SubscriptionRepository,
    private val cache: FeedRepository,
    private val clock: Clock = Clock.System,
    private val concurrency: Int = 6,
    /** Where new uploads of subscribed channels are recorded for the notification feed (null: not recorded). */
    private val notifications: com.greenaltambe.vela.data.NotificationRepository? = null,
    /** Called with the new uploads of channels whose level is All, for a system notification. */
    private val onNewUploads: (List<com.greenaltambe.vela.extractor.VideoSummary>) -> Unit = {},
    /** Uploads this says are blocked (the viewer's blocklist) leave no notification, in the app or on the system. */
    private val isBlocked: (com.greenaltambe.vela.extractor.VideoSummary) -> Boolean = { false },
) {
    private val _entries = MutableStateFlow(cache.entries())
    val entries: StateFlow<List<FeedEntry>> = _entries

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** channelId -> why its last fetch failed; cleared on each refresh. */
    private val _failures = MutableStateFlow<Map<String, String>>(emptyMap())
    val failures: StateFlow<Map<String, String>> = _failures

    private var lastRefreshAt: kotlin.time.Instant? = null

    /** Refreshes only if the last refresh is older than [maxAgeMs] (or there was none): coming back to the page must not refetch every channel. */
    suspend fun refreshIfStale(maxAgeMs: Long = 15 * 60_000L) {
        val last = lastRefreshAt
        if (last != null && (clock.now() - last).inWholeMilliseconds < maxAgeMs) return
        refresh()
    }

    /** Reads the cache again, e.g. after an unsubscribe or an import changed what should show. */
    fun reload() { _entries.value = cache.entries() }

    /** Everything below runs on [Dispatchers.Default]: the screens call this from the main thread, and 300 channels' worth of fetching, merging and database writes must not. */
    suspend fun refresh() {
        if (!_refreshing.compareAndSet(expect = false, update = true)) return
        try {
          withContext(Dispatchers.Default) {
            val channels = subscriptions.all()
            val subscribed = channels.mapTo(HashSet()) { it.channelId }
            val now = clock.now()
            val failures = HashMap<String, String>()
            val fresh = coroutineScope {
                val gate = Semaphore(concurrency)
                channels.map { channel ->
                    async {
                        gate.withPermit {
                            when (val result = extractor.recentUploads(channel.channelId)) {
                                is ExtractorResult.Success -> result.value.map { FeedEntry(it, it.uploadedAt, now) }
                                is ExtractorResult.Failure -> { synchronized(failures) { failures[channel.channelId] = result.error.message }; emptyList() }
                            }
                        }
                    }
                }.awaitAll().flatten()
            }
            val cached = cache.entries().filter { it.video.channelId in subscribed }
            // A first fill (nothing cached yet) only seeds the cache: it must not announce a channel's whole back catalogue.
            if (cached.isNotEmpty() && notifications != null) {
                val known = cached.mapTo(HashSet()) { it.video.id }
                val loud = ArrayList<com.greenaltambe.vela.extractor.VideoSummary>()
                val levels = subscriptions.notifyLevels()
                // At most the newest few per refresh: one burst of uploads must not become hundreds of rows (or database calls).
                // Only what is really new: a video published in the last few days, from a channel that was already in the cache. A channel that was just subscribed (or imported,
                // or synced from the account) brings its whole recent list, and a video that only now reached the feed (the cache was trimmed, the source reshuffled) can be
                // weeks old: neither is "a new upload", and saying "19 minutes ago" about it would be wrong.
                val cachedChannels = cached.mapTo(HashSet()) { it.video.channelId }
                fresh.filter { e ->
                    val published = e.video.uploadedAt
                    e.video.id !in known && e.video.channelId in cachedChannels && published != null && now - published <= NEW_UPLOAD_MAX_AGE && !isBlocked(e.video)
                }.sortedByDescending { it.video.uploadedAt }.take(MAX_NEW_PER_REFRESH).forEach { e ->
                    val level = levels[e.video.channelId] ?: com.greenaltambe.vela.data.NotifyLevel.PERSONALIZED
                    if (level != com.greenaltambe.vela.data.NotifyLevel.NONE) {
                        notifications.add(com.greenaltambe.vela.data.NotificationKind.UPLOAD, e.video.id, e.video.title, now, channelId = e.video.channelId, channelName = e.video.channelName, videoId = e.video.id, thumbnailUrl = e.video.thumbnailUrl)
                        if (level == com.greenaltambe.vela.data.NotifyLevel.ALL) loud += e.video
                    }
                }
                if (loud.isNotEmpty()) onNewUploads(loud)
            }
            val merged = withDurations(mergeFeed(cached, fresh))
            cache.replaceAll(merged)
            _entries.value = merged
            _failures.value = failures
            lastRefreshAt = clock.now()
          }
        } finally {
            _refreshing.value = false
        }
    }

    /** The RSS feed carries no durations: look up the newest entries that lack one (a few at a time), so the cards can show it. */
    private suspend fun withDurations(entries: List<FeedEntry>): List<FeedEntry> {
        val missing = entries.filter { it.video.durationSeconds == null && !it.video.isLive }.take(MAX_DURATION_LOOKUPS).map { it.video.id }
        if (missing.isEmpty()) return entries
        val found = HashMap<String, Long>()
        coroutineScope {
            val gate = Semaphore(concurrency)
            missing.map { id ->
                async {
                    gate.withPermit {
                        val info = (extractor.videoInfo(id) as? ExtractorResult.Success)?.value
                        if (info != null && info.durationSeconds > 0) synchronized(found) { found[id] = info.durationSeconds }
                    }
                }
            }.awaitAll()
        }
        if (found.isEmpty()) return entries
        return entries.map { e -> found[e.video.id]?.let { e.copy(video = e.video.copy(durationSeconds = it)) } ?: e }
    }

    companion object {
        const val MAX_DURATION_LOOKUPS = 30
        const val MAX_NEW_PER_REFRESH = 20
        /** An upload older than this is not announced as new. */
        val NEW_UPLOAD_MAX_AGE = kotlin.time.Duration.parse("3d")
    }
}
