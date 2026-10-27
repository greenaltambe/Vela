package com.greenaltambe.vela.data

import com.greenaltambe.vela.data.db.VelaDatabase
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.feed.FeedEntry
import com.greenaltambe.vela.library.ImportedChannel
import kotlin.time.Instant

/** Anything the UI needs to open a channel from a list. */
data class SubscribedChannel(val channelId: String, val name: String, val avatarUrl: String?, val subscribedAt: Instant)

/** Subscriptions kept on this device under [accountId]; the account id is what later lets a real account sync them. */
class SubscriptionRepository(private val db: VelaDatabase, private val accountId: String = LOCAL_ACCOUNT) {
    private val q get() = db.subscriptionQueries

    fun all(): List<SubscribedChannel> = q.selectForAccount(accountId).executeAsList()
        .map { SubscribedChannel(it.channelId, it.channelName, it.avatarUrl, Instant.fromEpochMilliseconds(it.subscribedAt)) }

    fun isSubscribed(channelId: String): Boolean = q.isSubscribed(accountId, channelId).executeAsOne() > 0

    fun subscribe(channelId: String, name: String, avatarUrl: String?, at: Instant) {
        q.upsert(accountId, channelId, name, avatarUrl, at.toEpochMilliseconds())
    }

    fun unsubscribe(channelId: String) {
        q.delete(accountId, channelId)
    }

    /** Adds [channels] not already subscribed; returns how many were new. Existing rows keep their name and date. */
    fun import(channels: List<ImportedChannel>, at: Instant): Int = db.transactionWithResult {
        var added = 0
        for (c in channels) {
            if (!isSubscribed(c.channelId)) {
                subscribe(c.channelId, c.name, null, at)
                added++
            }
        }
        added
    }
}

/** The history screen's view of [WatchHistoryRepository]'s table: listing and deleting. */
class HistoryLibrary(private val db: VelaDatabase, private val accountId: String = LOCAL_ACCOUNT) {
    private val q get() = db.watchHistoryQueries

    fun recent(limit: Long = 500): List<WatchRecord> = q.selectAll(accountId, limit).executeAsList().map {
        WatchRecord(it.id, it.accountId, it.videoId, it.channelId, it.title, Instant.fromEpochMilliseconds(it.watchedAt), it.positionMs, it.durationMs, it.watchedMs)
    }

    fun delete(id: Long) { q.deleteById(id) }

    /** Every watch of a video, i.e. removing it from the list however many times it was watched. */
    fun deleteVideo(videoId: String) { q.deleteForVideo(accountId, videoId) }

    fun clear() { q.clearForAccount(accountId) }

    fun watchedVideoIds(): Set<String> = q.watchedVideoIds(accountId).executeAsList().toSet()

    /** How far through each recently watched video the viewer got, 0..1 (the latest watch of each). */
    fun progress(limit: Long = 500): Map<String, Float> = buildMap {
        for (r in recent(limit)) if (r.durationMs > 0 && r.videoId !in this) put(r.videoId, (r.positionMs.toFloat() / r.durationMs).coerceIn(0f, 1f))
    }
}

class SearchHistoryRepository(private val db: VelaDatabase, private val accountId: String = LOCAL_ACCOUNT) {
    private val q get() = db.searchHistoryQueries

    fun recent(limit: Long = 20): List<String> = q.selectRecent(accountId, limit).executeAsList()

    /** Repeating a query moves it to the top rather than duplicating it. */
    fun add(query: String, at: Instant) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) q.upsert(accountId, trimmed, at.toEpochMilliseconds())
    }

    fun remove(query: String) { q.delete(accountId, query) }
    fun clear() { q.clearForAccount(accountId) }
}

data class PlaylistSummary(val id: Long, val name: String, val isWatchLater: Boolean, val itemCount: Int)

data class PlaylistVideo(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
) {
    fun toSummary() = VideoSummary(videoId, title, channelId, channelName, thumbnailUrl, durationSeconds)

    companion object {
        fun from(v: VideoSummary) = PlaylistVideo(v.id, v.title, v.channelId, v.channelName, v.thumbnailUrl, v.durationSeconds)
    }
}

/** Local playlists, including the built-in Watch Later. Positions are always 0..n-1 after every change. */
class PlaylistRepository(private val db: VelaDatabase, private val accountId: String = LOCAL_ACCOUNT) {
    private val q get() = db.playlistQueries

    fun all(): List<PlaylistSummary> {
        watchLaterId()
        return q.selectForAccount(accountId).executeAsList().map { PlaylistSummary(it.id, it.name, it.systemKey == WATCH_LATER, it.itemCount.toInt()) }
    }

    /** The id of Watch Later, created on first use. */
    fun watchLaterId(at: Instant = Instant.fromEpochMilliseconds(0)): Long =
        q.selectBySystemKey(accountId, WATCH_LATER).executeAsOneOrNull()?.id ?: create("Watch later", at, WATCH_LATER)

    fun create(name: String, at: Instant, systemKey: String? = null): Long = q.transactionWithResult {
        q.insert(accountId, name.trim().ifEmpty { "Untitled" }, systemKey, at.toEpochMilliseconds(), at.toEpochMilliseconds())
        q.lastInsertRowId().executeAsOne()
    }

    /** Built-in playlists keep their name. */
    fun rename(id: Long, name: String, at: Instant) {
        if (name.isNotBlank()) q.rename(name.trim(), at.toEpochMilliseconds(), id)
    }

    /** Built-in playlists cannot be deleted. */
    fun delete(id: Long) { q.delete(id) }

    fun items(id: Long): List<PlaylistVideo> = q.selectItems(id).executeAsList().map {
        PlaylistVideo(it.videoId, it.title, it.channelId, it.channelName, it.thumbnailUrl, it.durationSeconds)
    }

    fun contains(id: Long, videoId: String): Boolean = q.selectItemVideoIds(id).executeAsList().contains(videoId)

    /** Appends [videos] that are not already in the playlist; returns how many were added. */
    fun add(id: Long, videos: List<VideoSummary>, at: Instant): Int = q.transactionWithResult {
        val existing = q.selectItemVideoIds(id).executeAsList().toHashSet()
        var next = q.maxPosition(id).executeAsOne() + 1
        var added = 0
        for (v in videos) {
            if (!existing.add(v.id)) continue
            q.insertItem(id, v.id, next++, v.title, v.channelId, v.channelName, v.thumbnailUrl, v.durationSeconds, at.toEpochMilliseconds())
            added++
        }
        q.touch(at.toEpochMilliseconds(), id)
        added
    }

    fun remove(id: Long, videoId: String) = q.transaction {
        q.deleteItem(id, videoId)
        renumber(id, q.selectItems(id).executeAsList().map { it.videoId })
    }

    /** Moves the item at [from] to index [to] (both 0-based); out-of-range moves are ignored. */
    fun move(id: Long, from: Int, to: Int) = q.transaction {
        val ids = q.selectItems(id).executeAsList().map { it.videoId }.toMutableList()
        if (from !in ids.indices || to !in ids.indices || from == to) return@transaction
        ids.add(to, ids.removeAt(from))
        renumber(id, ids)
    }

    private fun renumber(id: Long, ordered: List<String>) {
        ordered.forEachIndexed { index, videoId -> q.setItemPosition(index.toLong(), id, videoId) }
    }

    companion object {
        const val WATCH_LATER = "watch_later"
    }
}

/** The cached subscription feed: shown at once, refreshed afterwards. */
class FeedRepository(private val db: VelaDatabase, private val accountId: String = LOCAL_ACCOUNT) {
    private val q get() = db.feedItemQueries

    fun entries(limit: Long = 500): List<FeedEntry> = q.selectForAccount(accountId, limit).executeAsList().map {
        FeedEntry(
            VideoSummary(it.videoId, it.title, it.channelId, it.channelName, it.thumbnailUrl, it.durationSeconds, it.viewCount, it.isShort != 0L),
            it.publishedAt?.let(Instant::fromEpochMilliseconds),
            Instant.fromEpochMilliseconds(it.fetchedAt),
        )
    }

    fun replaceAll(entries: List<FeedEntry>, keep: Long = 500) = q.transaction {
        for (e in entries) {
            val v = e.video
            q.upsert(accountId, v.id, v.channelId, v.channelName, v.title, v.thumbnailUrl, v.durationSeconds, v.viewCount, if (v.isShort) 1 else 0, e.publishedAt?.toEpochMilliseconds(), e.fetchedAt.toEpochMilliseconds())
        }
        q.trim(accountId, accountId, keep)
    }

    fun removeChannel(channelId: String) { q.deleteForChannel(accountId, channelId) }
    fun clear() { q.clearForAccount(accountId) }
}
