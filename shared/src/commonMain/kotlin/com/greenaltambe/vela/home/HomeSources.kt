package com.greenaltambe.vela.home

import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.core.settings.HomeShelf
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** What one shelf has to show: [title] when it names something ("Because you watched ..."), and the videos. */
data class ShelfData(val title: String?, val videos: List<VideoSummary>, val topics: List<String> = emptyList(), val subtitle: String? = null)

/**
 * Where each shelf gets its videos. Every source is independent: it asks only for what it needs, anonymously, and fails alone (the caller treats a
 * failure or an empty answer as "no shelf"). None of them needs an account.
 */
class HomeSources(private val services: AppServices) {
    /**
     * The shelf's videos, or null when it has none or its source fails. A source that throws is tried once more after a short wait (a blip of the
     * network or a tier handing over); an answer that is simply empty is not retried.
     */
    suspend fun load(shelf: HomeShelf, dismissed: HomeDismissed): ShelfData? {
        suspend fun once(): Result<ShelfData?> = runCatching {
            when (shelf) {
                HomeShelf.CONTINUE -> null              // read from the watch history by the screen (it is local and instant)
                HomeShelf.SUBSCRIPTIONS -> subscriptions()
                HomeShelf.BECAUSE -> because(dismissed)
                HomeShelf.CHANNELS -> channels(dismissed)
                HomeShelf.INTERESTS -> interests(dismissed)
                HomeShelf.SHORTS -> shorts(dismissed)
            }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        val first = once()
        val data = first.getOrNull() ?: if (first.isFailure) { kotlinx.coroutines.delay(RETRY_AFTER_MS); once().getOrNull() } else null
        return data?.let { it.copy(videos = it.videos.withoutDismissed(dismissed)) }?.takeIf { it.videos.isNotEmpty() }
    }

    private companion object { const val RETRY_AFTER_MS = 1_500L }

    /** The newest uploads of the subscribed channels: what the feed already holds; an empty feed is filled once first. */
    private suspend fun subscriptions(): ShelfData? {
        if (services.subscriptions.all().isEmpty()) return null
        if (services.feed.entries.value.isEmpty()) services.feed.refreshIfStale()
        val videos = services.feed.entries.value.asSequence().filter { !it.video.isShort }
            .map { it.video.copy(uploadedAt = it.video.uploadedAt ?: it.publishedAt) }.take(14).toList()
        return ShelfData(null, videos)
    }

    /** The related lists of the last few videos watched through, merged and ranked ([becauseYouWatched]). */
    private suspend fun because(dismissed: HomeDismissed): ShelfData? {
        val records = services.history.recent(80).filter { it.videoId.isNotBlank() }.distinctBy { it.videoId }
        val finished = records.filter { it.durationMs > 0 && it.positionMs >= it.durationMs * 0.8 }
        val seeds = (finished.ifEmpty { records }).take(3)
        if (seeds.isEmpty()) return null
        val lists = coroutineScope {
            seeds.map { s -> async { (services.extractor.related(s.videoId) as? ExtractorResult.Success)?.value.orEmpty() } }.awaitAll()
        }
        val watched = services.history.watchedVideoIds()
        val videos = becauseYouWatched(lists, watched + seeds.map { it.videoId }, perChannel = 2, limit = 16)
            .filterNot { it.channelId in dismissed.channels }
        val name = seeds.first().title
        return ShelfData("Because you watched", videos, subtitle = name)
    }

    /** The channels watched a lot and not subscribed to: their newest videos, one channel after another. */
    private suspend fun channels(dismissed: HomeDismissed): ShelfData? {
        val subscribed = services.subscriptions.all().mapTo(HashSet()) { it.channelId }
        val top = channelsWatchedALot(services.history.recent(300), subscribed, hidden = dismissed.channels)
        if (top.isEmpty()) return null
        val watched = services.history.watchedVideoIds()
        val lists = coroutineScope {
            top.map { id -> async { (services.extractor.channelVideos(id) as? ExtractorResult.Success)?.value?.items.orEmpty().filter { !it.isShort && it.id !in watched }.take(5) } }.awaitAll()
        }
        return ShelfData(null, interleave(lists).take(16))
    }

    /** A search for each of the topics that come up most in the history's titles. */
    private suspend fun interests(dismissed: HomeDismissed): ShelfData? {
        val topics = topKeywords(services.history.recent(150).map { it.title }, n = 3)
        if (topics.isEmpty()) return null
        val watched = services.history.watchedVideoIds()
        val lists = coroutineScope {
            topics.take(2).map { t -> async { (services.extractor.search(t) as? ExtractorResult.Success)?.value?.items.orEmpty().filter { !it.isShort && it.id !in watched && it.channelId !in dismissed.channels }.take(8) } }.awaitAll()
        }
        return ShelfData(null, interleave(lists).distinctBy { it.id }.take(16), topics)
    }

    /** Shorts of a few of the channels the viewer subscribes to. */
    private suspend fun shorts(dismissed: HomeDismissed): ShelfData? {
        val channels = services.subscriptions.all().filter { it.channelId !in dismissed.channels }.take(4)
        if (channels.isEmpty()) return null
        val lists = coroutineScope {
            channels.map { c -> async { (services.extractor.channelShorts(c.channelId, null) as? ExtractorResult.Success)?.value?.items.orEmpty().take(4).map { it.copy(isShort = true, channelId = c.channelId, channelName = it.channelName.ifBlank { c.name }) } } }.awaitAll()
        }
        return ShelfData(null, interleave(lists).take(14))
    }

    /** The topics shown as chips: the same words the interests shelf searches. */
    fun topics(): List<String> = topKeywords(services.history.recent(150).map { it.title }, n = 3)
}
