package com.greenaltambe.vela.data

import com.greenaltambe.vela.data.db.VelaDatabase
import kotlin.time.Instant

const val LOCAL_ACCOUNT = "local"

class WatchRecord(
    val id: Long,
    val accountId: String,
    val videoId: String,
    val channelId: String,
    val title: String,
    val watchedAt: Instant,
    val positionMs: Long,
    val durationMs: Long,
    val watchedMs: Long,
)

/** Local watch history, kept whether or not it is ever synced back to YouTube. One row per watch session. */
interface WatchHistoryRepository {
    /** The most recently watched row for [videoId], or null. */
    fun lastForVideo(accountId: String, videoId: String): WatchRecord?

    /** Creates the session's row and returns its id. */
    fun begin(accountId: String, videoId: String, channelId: String, title: String, at: Instant, positionMs: Long, durationMs: Long, watchedMs: Long): Long

    /** Brings a session's row up to date. */
    fun update(id: Long, at: Instant, positionMs: Long, durationMs: Long, watchedMs: Long)
}

class SqlDelightWatchHistoryRepository(private val db: VelaDatabase) : WatchHistoryRepository {
    private val queries get() = db.watchHistoryQueries

    override fun lastForVideo(accountId: String, videoId: String) =
        queries.selectLastForVideo(accountId, videoId).executeAsOneOrNull()?.let {
            WatchRecord(it.id, it.accountId, it.videoId, it.channelId, it.title, Instant.fromEpochMilliseconds(it.watchedAt), it.positionMs, it.durationMs, it.watchedMs)
        }

    override fun begin(accountId: String, videoId: String, channelId: String, title: String, at: Instant, positionMs: Long, durationMs: Long, watchedMs: Long): Long =
        queries.transactionWithResult {
            queries.insert(accountId, videoId, channelId, title, at.toEpochMilliseconds(), positionMs, durationMs, watchedMs, 0)
            queries.lastInsertRowId().executeAsOne()
        }

    override fun update(id: Long, at: Instant, positionMs: Long, durationMs: Long, watchedMs: Long) {
        queries.updateSession(at.toEpochMilliseconds(), positionMs, durationMs, watchedMs, id)
    }
}
