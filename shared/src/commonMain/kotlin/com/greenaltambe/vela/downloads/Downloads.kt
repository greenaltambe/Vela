package com.greenaltambe.vela.downloads

import com.greenaltambe.vela.data.db.Download
import com.greenaltambe.vela.extractor.Chapter
import com.greenaltambe.vela.extractor.Comment
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.modules.sponsorblock.Segment
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A finished download with everything stored for its page. Every field of [info] and below can be missing (a part that failed to save). */
class OfflineVideo(
    val download: Download,
    val summary: VideoSummary,
    val uri: String,
    val hasVideo: Boolean,
    val info: StoredInfo?,
    val description: String,
    val chapters: List<Chapter>,
    val comments: List<Comment>,
    val segments: List<Segment>,
    val branding: StoredBranding?,
    val thumbnailPath: String?,
)

/** What the platform contributes to downloads, and the pieces the UI needs from them. */
class Downloads(
    val manager: DownloadManager,
    val repo: DownloadRepository,
    val storage: () -> DownloadStorage,
    val merger: MediaMerger,
    /** Extra lines for Diagnostics (notification tool, service state...). */
    val diagnostics: () -> List<String> = { emptyList() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The finished download of [videoId] with its stored page, or null if it is not downloaded (or its file path is gone). */
    fun offline(videoId: String): OfflineVideo? {
        val row = repo.completedFor(videoId) ?: return null
        val uri = row.mergedPath ?: return null
        val data = repo.offlineData(row.id)
        fun <T> decode(raw: String?, serializer: kotlinx.serialization.KSerializer<T>): T? = raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
        val info = decode(data?.infoJson, StoredInfo.serializer())
        return OfflineVideo(
            download = row,
            summary = VideoSummary(row.videoId, row.title, row.channelId, info?.channelName.orEmpty(), data?.thumbnailPath, info?.durationSeconds, info?.viewCount),
            uri = uri,
            hasVideo = row.kind == com.greenaltambe.vela.data.DownloadKind.VIDEO,
            info = info,
            description = data?.description ?: info?.description.orEmpty(),
            chapters = decode(data?.chaptersJson, ListSerializer(Chapter.serializer())).orEmpty(),
            comments = decode(data?.commentsJson, ListSerializer(Comment.serializer())).orEmpty(),
            segments = decode(data?.sponsorBlockJson, ListSerializer(Segment.serializer())).orEmpty(),
            branding = decode(data?.deArrowJson, StoredBranding.serializer()),
            thumbnailPath = data?.thumbnailPath,
        )
    }

    fun isDownloaded(videoId: String): Boolean = repo.completedFor(videoId) != null
}
