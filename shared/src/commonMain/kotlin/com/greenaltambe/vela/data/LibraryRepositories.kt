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

    fun notifyLevel(channelId: String): NotifyLevel =
        q.notifyLevel(accountId, channelId).executeAsOneOrNull()?.let { raw -> NotifyLevel.entries.firstOrNull { it.name == raw } } ?: NotifyLevel.PERSONALIZED

    /** Every channel's level in one query. */
    fun notifyLevels(): Map<String, NotifyLevel> = q.notifyLevels(accountId).executeAsList().associate { r -> r.channelId to (NotifyLevel.entries.firstOrNull { it.name == r.notifyLevel } ?: NotifyLevel.PERSONALIZED) }

    /** One level for every channel at once. */
    fun setAllNotifyLevels(level: NotifyLevel) { q.setAllNotifyLevels(level.name, accountId) }

    fun setNotifyLevel(channelId: String, level: NotifyLevel) { q.setNotifyLevel(level.name, accountId, channelId) }

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
    private val dq get() = db.continueDismissedQueries

    /** Gives the rows of [videoId] that were saved without a title their title (and channel where blank). */
    fun healMissing(videoId: String, title: String, channelId: String) { if (title.isNotBlank()) q.healMissing(title, channelId, accountId, videoId) }

    fun recent(limit: Long = 500): List<WatchRecord> = q.selectAll(accountId, limit).executeAsList().map {
        WatchRecord(it.id, it.accountId, it.videoId, it.channelId, it.title, Instant.fromEpochMilliseconds(it.watchedAt), it.positionMs, it.durationMs, it.watchedMs)
    }

    /**
     * Removes one watch. When it is the latest watch of its video the whole video goes: with older watches left behind, the video would reappear in the list
     * (and Sync would hand the newer watch back from another device).
     */
    fun delete(id: Long) {
        val row = recent(100_000).firstOrNull { it.id == id } ?: return
        val latest = q.selectLastForVideo(accountId, row.videoId).executeAsOneOrNull()
        if (latest == null || latest.id == id) { q.deleteForVideo(accountId, row.videoId); dq.deleteForVideo(accountId, row.videoId) } else q.deleteById(id)
    }

    /** Every watch of a video, i.e. removing it from the list however many times it was watched. */
    fun deleteVideo(videoId: String) { q.deleteForVideo(accountId, videoId); dq.deleteForVideo(accountId, videoId) }

    fun clear() { q.clearForAccount(accountId); dq.clearForAccount(accountId) }

    /**
     * Takes [videoId] off Continue watching without touching the history. It stays off until it is watched again (a watch newer than the latest one now),
     * then it shows up again like any started video, until it is removed again. Returns false when the video has no watch.
     */
    fun dismissContinue(videoId: String): Boolean {
        val latest = q.selectLastForVideo(accountId, videoId).executeAsOneOrNull() ?: return false
        dq.upsert(accountId, videoId, latest.watchedAt)
        return true
    }

    /** Undo of [dismissContinue]. */
    fun restoreContinue(videoId: String) { dq.deleteForVideo(accountId, videoId) }

    fun watchedVideoIds(): Set<String> = q.watchedVideoIds(accountId).executeAsList().toSet()

    /**
     * How far (0..100) each video in the history was watched: the larger of the furthest position and the time watched, over the duration.
     * A video whose duration was never recorded counts as 100 (played at all, as before the threshold existed).
     */
    fun watchedPercents(): Map<String, Int> = q.watchedProgress(accountId).executeAsList().associate { r ->
        val dur = r.maxDurationMs ?: 0L
        val reached = maxOf(r.maxPositionMs ?: 0L, r.maxWatchedMs ?: 0L)
        r.videoId to if (dur > 0) (reached * 100 / dur).toInt().coerceIn(0, 100) else 100
    }

    /** How far through each recently watched video the viewer got, 0..1 (the latest watch of each). */
    fun progress(limit: Long = 500): Map<String, Float> = buildMap {
        for (r in recent(limit)) if (r.durationMs > 0 && r.videoId !in this) put(r.videoId, (r.positionMs.toFloat() / r.durationMs).coerceIn(0f, 1f))
    }

    /**
     * Videos that were started but not watched completely, newest first.
     * Filtered to videos with a recorded position (at least 3 seconds or >= 2% watched)
     * and less than 90% watched. Videos the viewer removed ([dismissContinue]) are left out until watched again.
     */
    fun continueWatching(limit: Int = 10): List<WatchRecord> {
        val records = recent(100).distinctBy { it.videoId }
        val dismissed = dq.selectForAccount(accountId).executeAsList().associate { it.videoId to it.dismissedAt }
        return records.filter { r ->
            // Removed by the viewer and not watched since: the latest watch is not newer than the one that was current at removal.
            if (dismissed[r.videoId]?.let { r.watchedAt.toEpochMilliseconds() <= it } == true) return@filter false
            if (r.durationMs > 0) {
                val fraction = r.positionMs.toFloat() / r.durationMs
                (fraction >= 0.02f || r.positionMs >= 3000L) && fraction < 0.90f
            } else {
                r.positionMs >= 3000L
            }
        }.take(limit)
    }
}

/** One video of the account's own watch history. [percent] is null when YouTube listed it without a progress figure. */
data class AccountWatchedEntry(val videoId: String, val title: String, val channelId: String?, val percent: Int?, val seenAt: Instant)

/**
 * The signed-in account's watch history as last read (task 83), per account, stored apart from the local [HistoryLibrary]. Re-fetchable, so it is
 * deleted with its account and left alone by "clear cache" and by backups.
 */
class AccountWatchedRepository(private val db: VelaDatabase, private val accountId: String) {
    private val q get() = db.accountWatchedQueries

    fun recent(limit: Long = 500): List<AccountWatchedEntry> = q.selectRecent(accountId, limit).executeAsList()
        .map { AccountWatchedEntry(it.videoId, it.title, it.channelId, it.percent?.toInt(), Instant.fromEpochMilliseconds(it.seenAt)) }

    fun contains(videoId: String): Boolean = q.exists(accountId, videoId).executeAsOne() > 0

    /** Every stored video with its percent (null = unknown), for the watched lookup. */
    fun percents(): Map<String, Int?> = q.selectIdsAndPercent(accountId).executeAsList().associate { it.videoId to it.percent?.toInt() }

    fun upsert(e: AccountWatchedEntry) { q.upsert(accountId, e.videoId, e.title, e.channelId, e.percent?.toLong(), e.seenAt.toEpochMilliseconds()) }

    fun clear() { q.clearForAccount(accountId) }
}

class SearchHistoryRepository(private val db: VelaDatabase, private val accountId: String = LOCAL_ACCOUNT) {
    private val q get() = db.searchHistoryQueries

    fun recent(limit: Long = 20): List<String> = q.selectRecent(accountId, limit).executeAsList()

    /** Each recent query with when it was searched (epoch ms), for Sync. */
    fun recentWithTime(limit: Long = 1000): List<Pair<String, Long>> = q.selectRecentWithTime(accountId, limit).executeAsList().map { it.query to it.searchedAt }

    /** Repeating a query moves it to the top rather than duplicating it. */
    fun add(query: String, at: Instant) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) q.upsert(accountId, trimmed, at.toEpochMilliseconds())
    }

    fun remove(query: String) { q.delete(accountId, query) }
    fun clear() { q.clearForAccount(accountId) }
}

/** How a playlist relates to the viewer's YouTube account. */
enum class PlaylistSync { LOCAL, SYNCED, LINKING, ERROR }

/** The state of one saved video: [PENDING_ADD] is not on YouTube yet, [PENDING_REMOVE] a hidden tombstone for one still there, [OVERFLOW] did not fit (YouTube's playlist is full). */
enum class RowState { SYNCED, PENDING_ADD, PENDING_REMOVE, OVERFLOW }

/** YouTube's limit on the videos in one playlist (Watch later included). */
const val YOUTUBE_PLAYLIST_LIMIT = 5000

data class PlaylistSummary(
    val id: Long, val name: String, val isWatchLater: Boolean, val itemCount: Int,
    /** The YouTube playlist this one is synced with ("WL" Watch later, "LL" Liked videos); null = LOCAL. */
    val remoteId: String? = null,
    val syncState: PlaylistSync = if (remoteId == null) PlaylistSync.LOCAL else PlaylistSync.SYNCED,
    val pendingCount: Int = 0, val overflowCount: Int = 0, val lastSyncedAt: Long? = null, val syncError: String? = null,
) {
    /** Linked with YouTube (the old "mirror"). */
    val isMirror: Boolean get() = remoteId != null
    val isSynced: Boolean get() = remoteId != null
    /** Liked videos belongs to the like button: it can be opened and played, nothing else. */
    val isReadOnly: Boolean get() = remoteId == "LL"
}

data class PlaylistVideo(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val rowState: RowState = RowState.SYNCED,
) {
    fun toSummary() = VideoSummary(videoId, title, channelId, channelName, thumbnailUrl, durationSeconds)

    companion object {
        fun from(v: VideoSummary) = PlaylistVideo(v.id, v.title, v.channelId, v.channelName, v.thumbnailUrl, v.durationSeconds)
    }
}

/** Playlists, including the built-in Watch later. Positions are always 0..n-1 after every change made here (a sync may leave gaps; only the order matters). */
class PlaylistRepository(private val db: VelaDatabase, private val accountId: String = LOCAL_ACCOUNT) {
    private val q get() = db.playlistQueries

    private fun state(s: String) = runCatching { PlaylistSync.valueOf(s) }.getOrDefault(PlaylistSync.LOCAL)
    private fun rowState(s: String) = runCatching { RowState.valueOf(s) }.getOrDefault(RowState.SYNCED)

    fun all(): List<PlaylistSummary> {
        watchLaterId()
        return q.selectForAccount(accountId).executeAsList().map {
            PlaylistSummary(it.id, it.name, it.systemKey == WATCH_LATER, it.itemCount.toInt(), it.remoteId, state(it.syncState), it.pendingCount.toInt(), it.overflowCount.toInt(), it.lastSyncedAt, it.syncError)
        }
    }

    fun get(id: Long): PlaylistSummary? = all().firstOrNull { it.id == id }
    fun byRemote(remoteId: String): PlaylistSummary? = all().firstOrNull { it.remoteId == remoteId }

    /**
     * The playlist synced with an account playlist ([remoteId] "WL", "LL" or a playlist id), created on first use and renamed when the account renames it.
     * Watch later is the one local Watch later, linked to "WL".
     */
    fun linked(remoteId: String, title: String, at: Instant): Long {
        if (remoteId == "WL") {
            val id = watchLaterId(at)
            val cur = q.selectById(id).executeAsOne()
            if (cur.remoteId == null) q.setRemote("WL", PlaylistSync.SYNCED.name, null, null, id)
            return id
        }
        val existing = q.selectByRemote(accountId, remoteId).executeAsList().firstOrNull()
        if (existing == null) {
            val id = create(if (remoteId == "LL") "Liked videos" else title, at)
            q.setRemote(remoteId, PlaylistSync.SYNCED.name, null, null, id)
            return id
        }
        if (existing.name != title.trim() && title.isNotBlank() && remoteId != "LL" && existing.systemKey == null) q.renameFromRemote(title.trim(), at.toEpochMilliseconds(), existing.id)
        return existing.id
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

    /** Links [id] to the YouTube playlist [remoteId] (LINKING until its items are pushed) or, with null, makes it LOCAL again; the items stay either way. */
    fun setRemote(id: Long, remoteId: String?, state: PlaylistSync, at: Instant? = null, error: String? = null) {
        q.setRemote(remoteId, state.name, at?.toEpochMilliseconds(), error, id)
    }
    fun setState(id: Long, state: PlaylistSync, error: String? = null) { q.setSyncState(state.name, error, id) }
    fun markSynced(id: Long, at: Instant) { q.setSynced(PlaylistSync.SYNCED.name, at.toEpochMilliseconds(), id) }

    /** The visible videos (a tombstone for a removed video is hidden). */
    fun items(id: Long): List<PlaylistVideo> = q.selectItems(id).executeAsList().map {
        PlaylistVideo(it.videoId, it.title, it.channelId, it.channelName, it.thumbnailUrl, it.durationSeconds, rowState(it.rowState))
    }
    /** Every row including tombstones (for the sync). */
    fun rows(id: Long): List<PlaylistVideo> = q.selectAllRows(id).executeAsList().map {
        PlaylistVideo(it.videoId, it.title, it.channelId, it.channelName, it.thumbnailUrl, it.durationSeconds, rowState(it.rowState))
    }
    fun rowState(id: Long, videoId: String): RowState? = q.selectRow(id, videoId).executeAsOneOrNull()?.let { rowState(it.rowState) }

    fun contains(id: Long, videoId: String): Boolean = q.selectItemVideoIds(id).executeAsList().contains(videoId)

    /** Appends [videos] that are not already in the playlist (a hidden tombstone is revived); returns how many were added. New rows get [state]. */
    fun add(id: Long, videos: List<VideoSummary>, at: Instant, state: RowState = RowState.SYNCED): Int = q.transactionWithResult {
        val existing = q.selectAllVideoIds(id).executeAsList().toHashSet()
        val visible = q.selectItemVideoIds(id).executeAsList().toHashSet()
        var next = q.maxPosition(id).executeAsOne() + 1
        var added = 0
        for (v in videos) {
            if (!visible.add(v.id)) continue
            if (v.id in existing) { q.setRowState(RowState.SYNCED.name, id, v.id); added++; continue }   // revived: it never left YouTube
            existing.add(v.id)
            q.insertItem(id, v.id, next++, v.title, v.channelId, v.channelName, v.thumbnailUrl, v.durationSeconds, at.toEpochMilliseconds(), state.name)
            added++
        }
        q.touch(at.toEpochMilliseconds(), id)
        added
    }

    fun setRowState(id: Long, videoId: String, state: RowState) { q.setRowState(state.name, id, videoId) }
    fun changeRowStates(id: Long, from: RowState, to: RowState) { q.changeRowStates(to.name, id, from.name) }
    fun dropTombstones(id: Long) { q.dropTombstones(id) }

    /** Deletes the row for good (a LOCAL playlist, or a pending add that never reached YouTube). */
    fun remove(id: Long, videoId: String) = q.transaction {
        q.deleteItem(id, videoId)
        renumber(id, q.selectAllRows(id).executeAsList().map { it.videoId })
    }

    /** Moves the item at [from] to index [to] (both 0-based); out-of-range moves are ignored. Only LOCAL playlists are reordered (YouTube's order is the truth for the others). */
    fun move(id: Long, from: Int, to: Int) = q.transaction {
        val ids = q.selectItems(id).executeAsList().map { it.videoId }.toMutableList()
        if (from !in ids.indices || to !in ids.indices || from == to) return@transaction
        ids.add(to, ids.removeAt(from))
        renumber(id, ids)
    }

    private fun renumber(id: Long, ordered: List<String>) {
        ordered.forEachIndexed { index, videoId -> q.setItemPosition(index.toLong(), id, videoId) }
    }

    /** Rewrites the order of the SYNCED rows to [remoteOrder] (YouTube's), keeping every other row after them in its current order. */
    fun applyRemoteOrder(id: Long, remoteOrder: List<String>) = q.transaction {
        val rows = q.selectAllRows(id).executeAsList().map { it.videoId }
        val have = rows.toHashSet()
        val first = remoteOrder.filter { it in have }.distinct()
        val rest = rows.filter { it !in first.toHashSet() }
        if (first + rest != rows) renumber(id, first + rest)
    }

    companion object {
        const val WATCH_LATER = "watch_later"
    }
}

/** What waits to be written to a synced playlist's YouTube copy. */
enum class OpKind { ADD, REMOVE, RENAME, DELETE }
data class PlaylistOpRow(val id: Long, val playlistId: Long, val kind: OpKind, val videoId: String?, val payload: String?, val attempts: Int, val lastError: String?)

/**
 * The outbox of account writes for synced playlists. It is data, not cache: a restart (or "Clear cache") never loses a pending change.
 * An ADD followed by a REMOVE of the same video cancels both, and a repeated RENAME keeps the last.
 */
class PlaylistOpRepository(private val db: VelaDatabase, private val accountId: String = LOCAL_ACCOUNT) {
    private val q get() = db.playlistOpQueries

    private fun row(o: com.greenaltambe.vela.data.db.PlaylistOp) = PlaylistOpRow(o.id, o.playlistId, runCatching { OpKind.valueOf(o.kind) }.getOrDefault(OpKind.ADD), o.videoId, o.payload, o.attempts.toInt(), o.lastError)

    fun all(): List<PlaylistOpRow> = q.selectForAccount(accountId).executeAsList().map(::row)
    fun forPlaylist(playlistId: Long): List<PlaylistOpRow> = q.selectForPlaylist(accountId, playlistId).executeAsList().map(::row)
    fun count(): Int = q.countForAccount(accountId).executeAsOne().toInt()

    fun enqueueAdd(playlistId: Long, videoId: String, at: Instant) = q.transaction {
        if (q.findVideoOp(accountId, playlistId, OpKind.ADD.name, videoId).executeAsOneOrNull() != null) return@transaction
        val remove = q.findVideoOp(accountId, playlistId, OpKind.REMOVE.name, videoId).executeAsOneOrNull()
        if (remove != null) { q.delete(remove.id); return@transaction }   // removed here, added again before it was sent: nothing to say to YouTube
        q.insert(accountId, playlistId, OpKind.ADD.name, videoId, null, at.toEpochMilliseconds())
    }

    fun enqueueRemove(playlistId: Long, videoId: String, at: Instant) = q.transaction {
        val add = q.findVideoOp(accountId, playlistId, OpKind.ADD.name, videoId).executeAsOneOrNull()
        if (add != null) { q.delete(add.id); return@transaction }          // added here, removed before it was sent: both cancel
        if (q.findVideoOp(accountId, playlistId, OpKind.REMOVE.name, videoId).executeAsOneOrNull() != null) return@transaction
        q.insert(accountId, playlistId, OpKind.REMOVE.name, videoId, null, at.toEpochMilliseconds())
    }

    fun enqueueRename(playlistId: Long, name: String, at: Instant) = q.transaction {
        val cur = q.findOp(accountId, playlistId, OpKind.RENAME.name).executeAsOneOrNull()
        if (cur != null) q.setPayload(name, cur.id) else q.insert(accountId, playlistId, OpKind.RENAME.name, null, name, at.toEpochMilliseconds())
    }

    /** Deleting the playlist on YouTube: every other op of it is moot. [remoteId] rides in the payload because the local row is gone by then. */
    fun enqueueDelete(playlistId: Long, remoteId: String, at: Instant) = q.transaction {
        q.deleteForPlaylist(accountId, playlistId)
        q.insert(accountId, playlistId, OpKind.DELETE.name, null, remoteId, at.toEpochMilliseconds())
    }

    /** Drops a waiting ADD of [videoId] (the video left the playlist before it was ever sent). */
    fun cancelAdd(playlistId: Long, videoId: String) { q.findVideoOp(accountId, playlistId, OpKind.ADD.name, videoId).executeAsOneOrNull()?.let { q.delete(it.id) } }
    fun hasAdd(playlistId: Long, videoId: String): Boolean = q.findVideoOp(accountId, playlistId, OpKind.ADD.name, videoId).executeAsOneOrNull() != null
    /** Drops a waiting REMOVE of [videoId] (the video came back before it was sent, or the removal failed). */
    fun cancelRemove(playlistId: Long, videoId: String) { q.findVideoOp(accountId, playlistId, OpKind.REMOVE.name, videoId).executeAsOneOrNull()?.let { q.delete(it.id) } }
    fun clearRename(playlistId: Long) { q.findOp(accountId, playlistId, OpKind.RENAME.name).executeAsOneOrNull()?.let { q.delete(it.id) } }
    /** Lets a playlist that gave up (3 failures) try again. */
    fun retry(playlistId: Long) { q.resetAttempts(accountId, playlistId) }

    fun done(id: Long) { q.delete(id) }
    fun failed(id: Long, error: String) { q.failed(error.take(300), id) }
    fun clearPlaylist(playlistId: Long) { q.deleteForPlaylist(accountId, playlistId) }
}

/** The cached subscription feed: shown at once, refreshed afterwards. */
class FeedRepository(private val db: VelaDatabase, private val accountId: String = LOCAL_ACCOUNT) {
    private val q get() = db.feedItemQueries

    fun entries(limit: Long = 500): List<FeedEntry> = q.selectForAccount(accountId, limit).executeAsList().map {
        val published = it.publishedAt?.let(Instant::fromEpochMilliseconds)
        FeedEntry(
            VideoSummary(it.videoId, it.title, it.channelId, it.channelName, it.thumbnailUrl, it.durationSeconds, it.viewCount, it.isShort != 0L, uploadedAt = published),
            published,
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


