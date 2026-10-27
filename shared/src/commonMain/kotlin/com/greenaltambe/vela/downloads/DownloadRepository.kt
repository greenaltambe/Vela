package com.greenaltambe.vela.downloads

import com.greenaltambe.vela.data.DownloadKind
import com.greenaltambe.vela.data.DownloadState
import com.greenaltambe.vela.data.LOCAL_ACCOUNT
import com.greenaltambe.vela.data.db.Download
import com.greenaltambe.vela.data.db.VelaDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** The stored copy of a video's page, written when a download completes. */
data class OfflineData(
    val downloadId: Long,
    val description: String?,
    val infoJson: String?,
    val chaptersJson: String?,
    val commentsJson: String?,
    val sponsorBlockJson: String?,
    val deArrowJson: String?,
    val thumbnailPath: String?,
)

@Serializable
data class StoredInfo(
    val title: String,
    val description: String,
    val channelId: String,
    val channelName: String,
    val durationSeconds: Long,
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val uploadedAtMs: Long? = null,
)

/** The `Download` table with the JSON in `planJson` decoded. All writes are single statements; the manager is the only writer. */
class DownloadRepository(private val db: VelaDatabase, private val accountId: String = LOCAL_ACCOUNT, private val clock: kotlin.time.Clock = kotlin.time.Clock.System) {
    private val q get() = db.downloadQueries
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun get(id: Long): Download? = q.selectById(id).executeAsOneOrNull()
    fun all(): List<Download> = q.selectForAccount(accountId).executeAsList()
    fun notCompleted(): List<Download> = q.selectAllNotCompleted(accountId).executeAsList()
    fun find(videoId: String, kind: DownloadKind): Download? = q.selectByVideoAndKind(accountId, videoId, kind).executeAsOneOrNull()
    fun completedFor(videoId: String): Download? = q.selectByVideo(accountId, videoId).executeAsOneOrNull()
    fun completedBytes(): Long = q.totalCompletedBytes(accountId).executeAsOne().toLong()

    fun insert(request: DownloadRequest, plan: StoredPlan, label: String): Long = q.transactionWithResult {
        val now = clock.now().toEpochMilliseconds()
        q.insert(accountId, request.video.id, request.video.channelId, request.video.title, DownloadState.QUEUED,
            if (request.audioOnly) DownloadKind.AUDIO_ONLY else DownloadKind.VIDEO, label, now, now)
        val id = q.lastInsertId().executeAsOne()
        q.updatePlan(json.encodeToString(StoredPlan.serializer(), plan), label, null, now, id)
        id
    }

    fun plan(row: Download): StoredPlan = row.planJson?.let { runCatching { json.decodeFromString(StoredPlan.serializer(), it) }.getOrNull() } ?: StoredPlan()

    fun savePlan(id: Long, plan: StoredPlan, label: String, totalBytes: Long?) {
        q.updatePlan(json.encodeToString(StoredPlan.serializer(), plan), label, totalBytes, clock.now().toEpochMilliseconds(), id)
    }

    fun setState(id: Long, state: DownloadState, error: String? = null) { q.updateState(state, error, clock.now().toEpochMilliseconds(), id) }
    fun setProgress(id: Long, done: Long, total: Long?) { q.updateProgress(done, total, clock.now().toEpochMilliseconds(), id) }
    fun setUrl(id: Long, url: String?, expiresAt: Instant?) { q.updateResolvedUrl(url, expiresAt?.toEpochMilliseconds(), clock.now().toEpochMilliseconds(), id) }
    fun setPaths(id: Long, video: String?, audio: String?, merged: String?) { q.updatePaths(video, audio, merged, clock.now().toEpochMilliseconds(), id) }
    fun setRetry(id: Long, count: Long) { q.updateRetry(count, clock.now().toEpochMilliseconds(), id) }
    fun resetForRetry(id: Long) { q.resetForRetry(clock.now().toEpochMilliseconds(), id) }
    fun delete(id: Long) { q.delete(id) }

    fun offlineData(id: Long): OfflineData? = q.selectOfflineData(id).executeAsOneOrNull()?.let {
        OfflineData(it.downloadId, it.descriptionText, it.infoJson, it.chaptersJson, it.commentsJson, it.sponsorBlockJson, it.deArrowJson, it.thumbnailPath)
    }

    fun saveOfflineData(d: OfflineData) {
        q.upsertOfflineData(d.downloadId, accountId, d.sponsorBlockJson, d.deArrowJson, d.commentsJson, d.chaptersJson, d.description, d.infoJson, d.thumbnailPath)
    }

    fun toItem(row: Download, speedBps: Long? = null, liveBytes: Long? = null): DownloadItem = DownloadItem(
        id = row.id, videoId = row.videoId, channelId = row.channelId, title = row.title, kind = row.kind, qualityLabel = row.qualityLabel,
        state = row.state, bytesDone = maxOf(row.bytesDone, liveBytes ?: 0), bytesTotal = row.bytesTotal, error = row.errorMessage,
        uri = row.mergedPath, speedBps = speedBps,
    )
}
