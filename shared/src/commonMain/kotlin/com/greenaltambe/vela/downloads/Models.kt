package com.greenaltambe.vela.downloads

import com.greenaltambe.vela.data.DownloadKind
import com.greenaltambe.vela.data.DownloadState
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.serialization.Serializable

/** What the viewer asked for when starting a download. */
data class DownloadRequest(
    val video: VideoSummary,
    val audioOnly: Boolean = false,
    /** Cap for the video's height in pixels; null for the best available. */
    val maxHeight: Int? = null,
    /** Prefer H.264 + AAC (plays everywhere) over VP9/AV1; null follows the setting. */
    val preferAvc: Boolean? = null,
)

/** One stream the plan downloads. [fileName] is its final name in the storage; the partial file is [partName]. */
@Serializable
data class StoredStream(
    val role: String, // "muxed" | "video" | "audio"
    val itag: Int?,
    val mimeType: String,
    val codec: String,
    val height: Int,
    val bitrate: Int,
    val expectedLength: Long?,
    val fileName: String,
) {
    val partName: String get() = "$fileName.part"
}

/** The request and, once resolved, the streams chosen for it: what a resume needs to pick the same ones from fresh URLs. */
@Serializable
data class StoredPlan(
    val maxHeight: Int? = null,
    val preferAvc: Boolean = false,
    val audioOnly: Boolean = false,
    val keepOriginalAudio: Boolean = true,
    val streams: List<StoredStream> = emptyList(),
    /** The file the viewer gets (after merging, if there are two streams). */
    val finalName: String? = null,
    val container: String? = null,
)

/** A row of the downloads list, as the UI shows it. */
data class DownloadItem(
    val id: Long,
    val videoId: String,
    val channelId: String,
    val title: String,
    val kind: DownloadKind,
    val qualityLabel: String,
    val state: DownloadState,
    val bytesDone: Long,
    val bytesTotal: Long?,
    val error: String?,
    /** Where the finished file is (a path or content URI the player can open); null until completed. */
    val uri: String?,
    /** Recent transfer speed in bytes per second while downloading. */
    val speedBps: Long? = null,
) {
    val progress: Float? get() = bytesTotal?.takeIf { it > 0 }?.let { (bytesDone.toDouble() / it).toFloat().coerceIn(0f, 1f) }
    val isActive: Boolean get() = state == DownloadState.QUEUED || state == DownloadState.DOWNLOADING || state == DownloadState.MERGING
}

class DownloadFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The streams of a video as offered right now, matched to a [StoredPlan]. */
class ResolvedStream(val stored: StoredStream, val url: String, val headers: Map<String, String>)
