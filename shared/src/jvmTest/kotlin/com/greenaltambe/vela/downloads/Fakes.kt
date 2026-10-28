package com.greenaltambe.vela.downloads

import com.greenaltambe.vela.core.http.TransportException
import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Files in memory: working files by name, "published" ones by URI. */
class FakeStorage : DownloadStorage {
    val work = LinkedHashMap<String, ByteArray>()
    val published = LinkedHashMap<String, ByteArray>()
    val assets = LinkedHashMap<String, ByteArray>()
    override val description = "memory"
    override fun size(name: String) = work[name]?.size?.toLong()
    override fun append(name: String): DownloadSink {
        work.getOrPut(name) { ByteArray(0) }
        return object : DownloadSink {
            override fun write(buffer: ByteArray, offset: Int, length: Int) { work[name] = work.getValue(name) + buffer.copyOfRange(offset, offset + length) }
            override fun close() {}
        }
    }
    override fun delete(name: String) { work.remove(name) }
    override fun rename(from: String, to: String) { work[to] = work.remove(from) ?: error("no $from") }
    override fun localPath(name: String) = "/work/$name"
    /** How many upcoming [publish] calls fail (a full disk, a SAF folder that went away). */
    var failPublishes = 0
    override fun publish(name: String): String {
        if (failPublishes > 0) { failPublishes--; throw DownloadFailure("could not write to the destination") }
        return "/library/$name".also { published[it] = work.remove(name) ?: error("nothing to publish: $name") }
    }
    override fun deletePublished(uri: String) { published.remove(uri) }
    override fun writeAsset(name: String, bytes: ByteArray): String = "/assets/$name".also { assets[it] = bytes }
    override fun deleteAsset(path: String) { assets.remove(path) }
    var free: Long? = null
    override fun freeBytes(): Long? = free
    override fun usedBytes() = published.values.sumOf { it.size.toLong() }
}

/** Serves byte arrays by URL with real Range semantics; every request is recorded and failures can be scripted. */
class FakeFetcher : RangeFetcher {
    class Request(val url: String, val start: Long, val end: Long?)
    val bodies = HashMap<String, ByteArray>()
    val requests = mutableListOf<Request>()
    /** Per-URL scripted behaviour, consulted before serving: return a status to answer with, throw to fail, or null to serve normally. */
    var script: suspend (Request) -> Int? = { null }
    /** When set, every fetch waits for this before answering (for concurrency tests). */
    var gate: CompletableDeferred<Unit>? = null
    /** When set, responses claim this total size whatever the real one is (a server that cuts the file short). */
    var reportedTotal: Long? = null

    override suspend fun fetch(url: String, headers: Map<String, String>, start: Long, endInclusive: Long?, sink: DownloadSink): RangeResponse {
        val request = Request(url, start, endInclusive)
        requests += request
        gate?.await()
        script(request)?.let { return RangeResponse(it, 0, null) }
        val body = bodies[url] ?: return RangeResponse(404, 0, null)
        if (start >= body.size) return RangeResponse(416, 0, reportedTotal ?: body.size.toLong())
        val last = minOf(endInclusive ?: (body.size - 1L), body.size - 1L).toInt()
        val slice = body.copyOfRange(start.toInt(), last + 1)
        sink.write(slice, 0, slice.size)
        return RangeResponse(if (start == 0L && endInclusive == null) 200 else 206, slice.size.toLong(), reportedTotal ?: body.size.toLong())
    }
}

class FakeMerger(override val available: Boolean = true, private val rejects: (String, String) -> Boolean = { _, _ -> false }) : MediaMerger {
    override fun canMerge(videoCodec: String, audioCodec: String) = !rejects(videoCodec, audioCodec)
    val jobs = mutableListOf<MergeJob>()
    var failWith: String? = null
    var storage: FakeStorage? = null
    override val description = if (available) "fake" else "ffmpeg not found"
    override suspend fun merge(job: MergeJob) {
        failWith?.let { throw MergeException(it) }
        jobs += job
        val s = storage ?: return
        // The output "file" is the two inputs back to back, so tests can see both were used.
        val v = s.work[job.videoPath.removePrefix("/work/")] ?: error("no video input")
        val a = s.work[job.audioPath.removePrefix("/work/")] ?: error("no audio input")
        s.work[job.outputPath.removePrefix("/work/")] = v + a
    }
}

class RecordingNotifier : DownloadNotifier {
    val completed = mutableListOf<String>()
    val failed = mutableListOf<Pair<String, String>>()
    override fun completed(title: String) { completed += title }
    override fun failed(title: String, reason: String) { failed += title to reason }
}

fun bytesOf(size: Int, seed: Int = 0) = ByteArray(size) { ((it * 31 + seed) and 0xff).toByte() }

/** Hands out a stream set whose URLs carry a generation number, so a test can see which resolve a request came from. */
class FakeExtractor(
    private val video: ByteArray,
    private val audio: ByteArray,
    private val fetcher: FakeFetcher,
) : Extractor by StubExtractor() {
    var resolves = 0
    var withOpus = false
    var failResolve: ExtractorError? = null
    var hlsOnly = false
    var infoCalls = 0
    var commentPages = 3
    var videoStreams: (Int) -> List<VideoStream> = { gen ->
        listOf(
            VideoStream("https://v/$gen/1080", "video/mp4", "avc1.640028", 4_000_000, 1920, 1080, 30, itag = 137, contentLength = video.size.toLong()),
            VideoStream("https://v/$gen/720", "video/webm", "vp9", 2_000_000, 1280, 720, 30, itag = 247, contentLength = video.size.toLong()),
        )
    }

    private fun register(gen: Int) {
        for (s in videoStreams(gen)) fetcher.bodies[s.url] = video
        fetcher.bodies["https://a/$gen"] = audio
        fetcher.bodies["https://ao/$gen"] = audio
        fetcher.bodies["https://img/thumb.jpg"] = bytesOf(50, 9)
    }

    override suspend fun streams(videoId: String): ExtractorResult<StreamSet> {
        failResolve?.let { return ExtractorResult.Failure(it) }
        if (hlsOnly) return ExtractorResult.Success(StreamSet(emptyList(), emptyList(), hlsUrl = "https://hls/master.m3u8"))
        resolves++
        register(resolves)
        return ExtractorResult.Success(
            StreamSet(
                videoStreams = videoStreams(resolves),
                audioStreams = listOf(AudioStream("https://a/$resolves", "audio/mp4", "mp4a.40.2", 128_000, itag = 140, contentLength = audio.size.toLong(), isOriginalTrack = true)) +
                    (if (withOpus) listOf(AudioStream("https://ao/$resolves", "audio/webm", "opus", 96_000, itag = 251, contentLength = audio.size.toLong(), isOriginalTrack = true)) else emptyList()),
                expiresAt = Instant.fromEpochSeconds(2_000_000_000), client = "fake",
            ),
        )
    }

    override suspend fun videoInfo(videoId: String): ExtractorResult<VideoInfo> {
        infoCalls++
        return ExtractorResult.Success(VideoInfo(videoId, "Title of $videoId", "0:00 a\n1:00 b\n2:00 c", "UCc", "Chan", 200, 10, 5, "https://img/thumb.jpg", uploadDate = Instant.fromEpochSeconds(1_700_000_000)))
    }

    override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?): ExtractorResult<CommentsPage> {
        val page = (continuation?.toInt() ?: 0) + 1
        if (page > commentPages) return ExtractorResult.Failure(ExtractorError.Network("no more"))
        return ExtractorResult.Success(CommentsPage(listOf(Comment("c$page", "someone", "comment $page")), if (page < commentPages) page.toString() else null))
    }
}

val SomeVideo = VideoSummary("vid1", "A: video/with*bad?chars", "UCc", "Chan")
@Suppress("unused") val longLived = 6.hours
@Suppress("unused") val transport: Class<TransportException> = TransportException::class.java
