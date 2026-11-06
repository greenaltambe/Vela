package com.greenaltambe.vela.data

import com.greenaltambe.vela.data.db.VelaDatabase
import kotlin.time.Instant

/** What a subscribed channel may notify about: everything it posts, a quieter selection (rows in the feed, no system notification), or nothing. */
enum class NotifyLevel(val label: String) { ALL("All"), PERSONALIZED("Personalized"), NONE("None") }

enum class NotificationKind { UPLOAD, DOWNLOAD_DONE, DOWNLOAD_FAILED, HEALTH, ACCOUNT }

data class NotificationEntry(
    val id: Long, val kind: NotificationKind, val title: String, val body: String,
    val channelId: String, val channelName: String, val videoId: String, val thumbnailUrl: String?,
    val createdAt: Instant, val read: Boolean,
)

/** The local notification feed of one account: new uploads, finished and failed downloads, health alerts. Writing the same (kind, key) twice keeps one row. */
class NotificationRepository(private val db: VelaDatabase, private val accountId: String, private val changed: () -> Unit = {}) {
    private val q get() = db.notificationItemQueries

    /** Returns true when a new row was added. */
    fun add(
        kind: NotificationKind, key: String, title: String, at: Instant, body: String = "",
        channelId: String = "", channelName: String = "", videoId: String = "", thumbnailUrl: String? = null,
    ): Boolean {
        q.insertIgnore(accountId, kind.name, key, title, body, channelId, channelName, videoId, thumbnailUrl, at.toEpochMilliseconds())
        val added = q.changes().executeAsOne() > 0
        if (added) { q.trim(accountId, accountId, KEEP.toLong()); changed() }
        return added
    }

    fun recent(limit: Int = 200): List<NotificationEntry> = q.recent(accountId, limit.toLong()).executeAsList().map {
        NotificationEntry(
            it.id, runCatching { NotificationKind.valueOf(it.kind) }.getOrDefault(NotificationKind.HEALTH), it.title, it.body, it.channelId, it.channelName,
            it.videoId, it.thumbnailUrl, Instant.fromEpochMilliseconds(it.createdAt), it.readAt != null,
        )
    }

    /** Channels with a new upload you have not looked at in Notifications yet. */
    fun unreadUploadChannels(): Set<String> = q.unreadUploadChannels(accountId).executeAsList().toSet()

    fun unreadCount(): Int = q.unreadCount(accountId).executeAsOne().toInt()

    fun markRead(id: Long, at: Instant) { q.markRead(at.toEpochMilliseconds(), id); changed() }
    fun markAllRead(at: Instant) { q.markAllRead(at.toEpochMilliseconds(), accountId); changed() }
    fun delete(id: Long) { q.delete(id); changed() }
    fun clear() { q.clear(accountId); changed() }

    companion object { const val KEEP = 500 }
}
