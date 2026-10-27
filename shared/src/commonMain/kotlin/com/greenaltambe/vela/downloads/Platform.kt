package com.greenaltambe.vela.downloads

import com.greenaltambe.vela.core.http.TransportException

/** Receives bytes. Written once, sequentially, then closed. */
interface DownloadSink {
    fun write(buffer: ByteArray, offset: Int, length: Int)
    fun close()
}

/**
 * Where a download's files live. Everything happens in a local *working area* (partial files, the merge); finishing a
 * file [publish]es it to the place the viewer chose, which for a plain folder is a rename and for an Android SAF tree
 * is a copy. Names are flat file names, never paths.
 */
interface DownloadStorage {
    /** For the settings screen: where finished downloads go. */
    val description: String

    /** Size of a working file, or null if there is none. */
    fun size(name: String): Long?

    /** Opens a working file for appending, creating it if needed. */
    fun append(name: String): DownloadSink

    fun delete(name: String)
    fun rename(from: String, to: String)

    /** A local path to a working file, for tools (ffmpeg) that need a real file. */
    fun localPath(name: String): String

    /** Moves the working file [name] to its final home and returns the URI/path a player opens. */
    fun publish(name: String): String

    /** Deletes a published file. */
    fun deletePublished(uri: String)

    /** Stores a small file the app keeps for itself (thumbnails) and returns its path; never in the user's folder. */
    fun writeAsset(name: String, bytes: ByteArray): String

    fun deleteAsset(path: String)

    /** Free space where downloads are written, or null if unknown. */
    fun freeBytes(): Long?

    /** Total size of the published downloads, for the storage display. */
    fun usedBytes(): Long
}

class RangeResponse(
    val status: Int,
    /** Bytes written to the sink. */
    val bytes: Long,
    /** The whole file's size when the server said (Content-Range, or Content-Length of a full answer). */
    val totalLength: Long?,
)

/** Fetches a byte range straight into a sink. Connection failures throw [TransportException]; every HTTP status is a normal return. */
interface RangeFetcher {
    suspend fun fetch(url: String, headers: Map<String, String>, start: Long, endInclusive: Long?, sink: DownloadSink): RangeResponse
}

class MergeJob(
    val videoPath: String,
    val audioPath: String,
    val outputPath: String,
    val container: Container,
)

class MergeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Muxes a video-only and an audio-only file into one without re-encoding. */
interface MediaMerger {
    /** True if this platform can merge at all (for the diagnostics note: "ffmpeg not found"). */
    val available: Boolean

    /** A line for Diagnostics: what is used, or why nothing is. */
    val description: String

    /** The containers this merger can write; the download picks the best of these for the codecs (see [Containers.forCodecs]). */
    val containers: Set<Container> get() = Container.entries.toSet()

    /** The container for this pair of codecs, which must be one of [containers]. */
    fun containerFor(videoCodec: String, audioCodec: String): Container = Containers.forCodecs(videoCodec, audioCodec, containers)

    suspend fun merge(job: MergeJob)

    /** Copies one stream into another container without re-encoding (WebM Opus -> Ogg Opus). */
    suspend fun remux(inputPath: String, outputPath: String) { throw MergeException("remuxing is not supported here") }
}

/** Shown to the viewer: download notifications on the desktop (notify-send), the foreground service's on Android. */
interface DownloadNotifier {
    fun completed(title: String)
    fun failed(title: String, reason: String)
}
