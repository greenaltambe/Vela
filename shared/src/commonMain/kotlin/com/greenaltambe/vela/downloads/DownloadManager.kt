package com.greenaltambe.vela.downloads

import com.greenaltambe.vela.core.network.NetworkMonitor
import com.greenaltambe.vela.core.network.NetworkType
import com.greenaltambe.vela.core.settings.PreferredCodec
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.DownloadKind
import com.greenaltambe.vela.data.DownloadState
import com.greenaltambe.vela.data.db.Download
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.StreamSet
import com.greenaltambe.vela.player.PlaybackConstraints
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackPrefs
import com.greenaltambe.vela.player.StreamLeases
import com.greenaltambe.vela.player.StreamSelector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The download queue. States (stored in the database, so they survive the process dying):
 *
 * ```
 * QUEUED -> DOWNLOADING -> MERGING -> COMPLETED
 *    ^           |            |
 *    |           +--> FAILED <-+      (retry: FAILED -> QUEUED)
 *    +-- PAUSED <--+                  (resume: PAUSED -> QUEUED)
 * ```
 *
 * A video can have one VIDEO and one AUDIO_ONLY download. Nothing but this class writes the download tables, and the
 * partial files on disk are the only progress that matters: [start] turns anything left DOWNLOADING or MERGING by a
 * dead process back into QUEUED, and the next run continues each stream from the size of its partial file.
 *
 * Every run **resolves fresh stream URLs** through [leases] (the fallback chain) and matches them to the streams
 * chosen when the download was first planned; a URL that dies mid-way (HTTP 401/403/404/410) is resolved again, at most
 * [maxReResolves] times, and the transfer continues from where the file ends. Wi-Fi-only and the number of
 * simultaneous downloads come from settings and apply to the next scheduling decision; a running download whose
 * connection turns metered stops and waits (QUEUED, with [waiting] saying why).
 */
class DownloadManager(
    private val repo: DownloadRepository,
    private val leases: StreamLeases,
    private val storage: () -> DownloadStorage,
    private val fetcher: RangeFetcher,
    private val merger: MediaMerger,
    private val collector: OfflineCollector,
    private val settings: SettingsResolver,
    private val network: NetworkMonitor,
    private val scope: CoroutineScope,
    private val notifier: DownloadNotifier? = null,
    private val clock: Clock = Clock.System,
    private val chunkBytes: Long = 10L * 1024 * 1024,
    private val maxRetries: Int = 5,
    private val backoff: Backoff = Backoff.Exponential,
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
    private val maxReResolves: Int = 3,
    private val tick: Duration = 300.milliseconds,
) {
    private val chunked = ChunkedFetch(fetcher, storage.let { s -> LazyStorage(s) }, chunkBytes, maxRetries, backoff, sleep)

    /** Delegates to whatever folder is currently configured, so changing the setting takes effect for the next transfer. */
    private class LazyStorage(private val provider: () -> DownloadStorage) : DownloadStorage {
        private val s get() = provider()
        override val description get() = s.description
        override fun size(name: String) = s.size(name)
        override fun append(name: String) = s.append(name)
        override fun delete(name: String) = s.delete(name)
        override fun rename(from: String, to: String) = s.rename(from, to)
        override fun localPath(name: String) = s.localPath(name)
        override fun publish(name: String) = s.publish(name)
        override fun deletePublished(uri: String) = s.deletePublished(uri)
        override fun writeAsset(name: String, bytes: ByteArray) = s.writeAsset(name, bytes)
        override fun deleteAsset(path: String) = s.deleteAsset(path)
        override fun freeBytes() = s.freeBytes()
        override fun usedBytes() = s.usedBytes()
    }

    private val lock = Any()
    private val jobs = HashMap<Long, Job>()
    private val live = HashMap<Long, LiveProgress>()
    private val kicks = Channel<Unit>(Channel.CONFLATED)

    private class LiveProgress(@Volatile var base: Long = 0, @Volatile var bytes: Long = 0, var lastSampleBytes: Long = 0, var lastSampleAtMs: Long = 0, @Volatile var speedBps: Long = 0)

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())

    /** Every download, newest first, refreshed a few times a second while anything transfers. */
    val items: StateFlow<List<DownloadItem>> = _items

    private val _waiting = MutableStateFlow<String?>(null)

    /** Why queued downloads are not running ("Waiting for Wi-Fi"), or null. */
    val waiting: StateFlow<String?> = _waiting

    private val _active = MutableStateFlow(0)

    /** How many downloads are queued or running: the Android service stays alive while this is above zero. */
    val activeCount: StateFlow<Int> = _active

    // ---- control ----

    /** Call once at startup: recovers from a dead process, then schedules. */
    fun start() {
        for (row in repo.notCompleted()) {
            if (row.state == DownloadState.DOWNLOADING || row.state == DownloadState.MERGING) repo.setState(row.id, DownloadState.QUEUED, null)
        }
        refresh()
        scope.launch { for (k in kicks) schedule() }
        scope.launch { network.type.collectLatest { kick() } }
        scope.launch { settings.revision.collectLatest { kick() } }
        kick()
    }

    /** Adds a download, or returns the id of the one that already exists (restarting it if it had stopped or failed). */
    fun enqueue(request: DownloadRequest): Long {
        val kind = if (request.audioOnly) DownloadKind.AUDIO_ONLY else DownloadKind.VIDEO
        repo.find(request.video.id, kind)?.let { existing ->
            when (existing.state) {
                DownloadState.PAUSED -> resume(existing.id)
                DownloadState.FAILED -> retry(existing.id)
                else -> {}
            }
            return existing.id
        }
        val plan = StoredPlan(
            maxHeight = request.maxHeight,
            preferAvc = request.preferAvc ?: settings.resolve(Settings.downloadsPreferAvc),
            audioOnly = request.audioOnly,
            keepOriginalAudio = settings.resolve(Settings.downloadsKeepOriginalAudio),
        )
        val label = if (request.audioOnly) "Audio" else request.maxHeight?.let { "up to ${it}p" } ?: "Best"
        val id = repo.insert(request, plan, label)
        refresh(); kick()
        return id
    }

    fun pause(id: Long) {
        val row = repo.get(id) ?: return
        if (row.state != DownloadState.QUEUED && row.state != DownloadState.DOWNLOADING) return
        stopJob(id)
        repo.setState(id, DownloadState.PAUSED, null)
        refresh(); kick()
    }

    fun pauseAll() = repo.notCompleted().forEach { pause(it.id) }

    fun resume(id: Long) {
        if (repo.get(id)?.state != DownloadState.PAUSED) return
        repo.setState(id, DownloadState.QUEUED, null)
        refresh(); kick()
    }

    fun retry(id: Long) {
        if (repo.get(id)?.state != DownloadState.FAILED) return
        repo.resetForRetry(id)
        refresh(); kick()
    }

    /** Cancels a download in any state and deletes everything it wrote (or, for a finished one, deletes the download). */
    fun remove(id: Long) {
        val row = repo.get(id) ?: return
        stopJob(id)
        deleteFiles(row)
        repo.delete(id)
        refresh(); kick()
    }

    /** The finished download of [videoId], if any (video preferred over audio-only). */
    fun completedFor(videoId: String): Download? = repo.completedFor(videoId)

    // ---- scheduling ----

    private fun kick() { kicks.trySend(Unit) }

    private var ticker: Job? = null

    /** Refreshes the list a few times a second, but only while something transfers (so an idle queue schedules nothing). */
    private fun ensureTicker() = synchronized(lock) {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (synchronized(lock) { jobs.isNotEmpty() }) {
                delay(tick)
                sample(); refresh()
            }
        }
    }

    private fun stopJob(id: Long) {
        val job = synchronized(lock) { jobs.remove(id) }
        job?.cancel()
        synchronized(lock) { live.remove(id) }
    }

    private fun schedule() {
        val type = network.type.value
        val wifiOnly = settings.resolve(Settings.downloadsWifiOnly)
        val blocked: String? = when {
            type == NetworkType.OFFLINE -> "Waiting for a connection"
            wifiOnly && type != NetworkType.UNMETERED -> "Waiting for Wi-Fi"
            else -> null
        }
        _waiting.value = blocked?.takeIf { repo.notCompleted().any { it.state == DownloadState.QUEUED || it.state == DownloadState.DOWNLOADING } }
        if (blocked != null) {
            // Running transfers stop and wait in the queue; their partial files stay.
            val running = synchronized(lock) { jobs.keys.toList() }
            for (id in running) { stopJob(id); repo.setState(id, DownloadState.QUEUED, null) }
            refresh()
            return
        }
        val limit = settings.resolve(Settings.downloadsConcurrent).toInt().coerceIn(1, 8)
        val free = limit - synchronized(lock) { jobs.size }
        if (free <= 0) { refresh(); return }
        val next = repo.notCompleted().filter { it.state == DownloadState.QUEUED }.filter { synchronized(lock) { it.id !in jobs } }.take(free)
        for (row in next) {
            synchronized(lock) { jobs[row.id] = scope.launch { run(row.id) } }
        }
        if (next.isNotEmpty()) ensureTicker()
        refresh()
    }

    // ---- one download ----

    private suspend fun run(id: Long) {
        try {
            repo.setState(id, DownloadState.DOWNLOADING, null)
            synchronized(lock) { live[id] = LiveProgress() }
            refresh()
            val row = repo.get(id) ?: return
            val finalUri = transfer(row)
            repo.setPaths(id, null, null, finalUri)
            val fresh = repo.get(id)!!
            val collected = try { collector.collect(fresh) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            collected?.let { repo.saveOfflineData(it.data) }
            repo.setState(id, DownloadState.COMPLETED, collected?.missing?.takeIf { it.isNotEmpty() }?.let { "Saved without: ${it.joinToString()}" })
            notifier?.completed(row.title)
        } catch (e: CancellationException) {
            throw e // pause / remove / network change already set the state
        } catch (e: DownloadFailure) {
            fail(id, e.message ?: "Download failed")
        } catch (e: MergeException) {
            fail(id, "Could not merge: ${e.message}")
        } catch (e: Exception) {
            fail(id, "${e::class.simpleName}: ${e.message}")
        } finally {
            synchronized(lock) { jobs.remove(id); live.remove(id) }
            refresh(); kick()
        }
    }

    private fun fail(id: Long, message: String) {
        val title = repo.get(id)?.title.orEmpty()
        repo.setState(id, DownloadState.FAILED, message)
        notifier?.failed(title, message)
    }

    /** Resolves, plans, transfers every stream, merges, publishes; returns the URI of the finished file. */
    private suspend fun transfer(row: Download): String {
        var plan = repo.plan(row)
        // A merged file already waiting in the working area (publishing failed last time): just publish it, no network.
        plan.finalName?.takeIf { plan.streams.isNotEmpty() && storage().size(it) != null }?.let { return storage().publish(it) }
        var resolved = resolveStreams(row, plan)
        plan = resolved.first
        var streams = resolved.second
        storage().let { st -> synchronized(lock) { live[row.id]?.base = plan.streams.sumOf { st.size(it.fileName) ?: st.size(it.partName) ?: 0L } } }
        repo.savePlan(row.id, plan, row.qualityLabel.let { labelFor(plan, it) }, plan.streams.sumOf { it.expectedLength ?: 0L }.takeIf { plan.streams.all { s -> s.expectedLength != null } })

        var reResolves = 0
        val totals = HashMap<String, Long?>()
        plan.streams.forEach { totals[it.fileName] = it.expectedLength }
        var index = 0
        while (index < streams.size) {
            val stream = streams[index]
            try {
                val size = chunked.download(
                    stream, totals[stream.stored.fileName],
                    onBytes = { n -> synchronized(lock) { live[row.id]?.let { it.bytes += n } } },
                    onChunk = { _, total ->
                        total?.let { totals[stream.stored.fileName] = it }
                        saveProgress(row.id, plan, totals)
                    },
                )
                totals[stream.stored.fileName] = size
                saveProgress(row.id, plan, totals)
                index++
            } catch (e: StreamDeadException) {
                if (++reResolves > maxReResolves) throw DownloadFailure("The stream URL keeps being rejected (HTTP ${e.status}); giving up after $maxReResolves fresh attempts")
                // The URL died (expired, or its signature stopped matching): ask the chain for fresh ones and carry on from the file's end.
                streams = resolveStreams(repo.get(row.id)!!, plan).second
            }
        }

        val st = storage()
        val finalName = plan.finalName ?: error("plan has no final name")
        if (plan.streams.size == 2) {
            repo.setState(row.id, DownloadState.MERGING, null)
            val video = plan.streams.first { it.role == "video" }
            val audio = plan.streams.first { it.role == "audio" }
            if (!merger.available) throw MergeException(merger.description)
            val container = Container.entries.firstOrNull { it.extension == plan.container } ?: merger.containerFor(video.codec, audio.codec)
            merger.merge(MergeJob(st.localPath(video.fileName), st.localPath(audio.fileName), st.localPath(finalName), container))
            st.delete(video.fileName); st.delete(audio.fileName)
        } else {
            val source = plan.streams.single().fileName
            if (source.substringAfterLast('.') == finalName.substringAfterLast('.')) {
                st.rename(source, finalName)
            } else {
                // The viewer asked for another container (Opus out of WebM into .opus): a stream copy, or the original if that is not possible.
                try {
                    if (!merger.available) throw MergeException(merger.description)
                    merger.remux(st.localPath(source), st.localPath(finalName))
                    st.delete(source)
                } catch (e: MergeException) {
                    st.delete(finalName)
                    val fallback = finalName.substringBeforeLast('.') + "." + source.substringAfterLast('.')
                    st.rename(source, fallback)
                    return st.publish(fallback)
                }
            }
        }
        return st.publish(finalName)
    }

    private fun saveProgress(id: Long, plan: StoredPlan, totals: Map<String, Long?>) {
        val st = storage()
        val done = plan.streams.sumOf { st.size(it.fileName) ?: st.size(it.partName) ?: 0L }
        val total = if (plan.streams.all { totals[it.fileName] != null }) plan.streams.sumOf { totals[it.fileName]!! } else null
        repo.setProgress(id, done, total)
        refresh()
    }

    private fun labelFor(plan: StoredPlan, fallback: String): String {
        val video = plan.streams.firstOrNull { it.role != "audio" }
        return if (plan.audioOnly) "Audio" else video?.height?.takeIf { it > 0 }?.let { "${it}p" } ?: fallback
    }

    /**
     * Fresh URLs for the plan. The first time there is no plan yet, so the streams are chosen here (the same selector
     * playback uses, with every codec allowed and the viewer's height cap); later runs find the *same* streams again
     * by itag, or failing that by height and codec.
     */
    private suspend fun resolveStreams(row: Download, stored: StoredPlan): Pair<StoredPlan, List<ResolvedStream>> {
        val set = when (val r = leases.resolve(row.videoId)) {
            is ExtractorResult.Success -> r.value
            is ExtractorResult.Failure -> throw DownloadFailure("Could not get the streams: ${r.error.reason} ${r.error.message}")
        }
        repo.setUrl(row.id, set.videoStreams.firstOrNull()?.url ?: set.audioStreams.firstOrNull()?.url, set.expiresAt)
        if (stored.streams.isEmpty()) return choose(row, stored, set)

        val resolved = stored.streams.map { s -> match(s, set) ?: return choose(row, stored.copy(streams = emptyList()), set).also { discardPartials(stored) } }
        return stored to resolved
    }

    private fun choose(row: Download, stored: StoredPlan, set: StreamSet): Pair<StoredPlan, List<ResolvedStream>> {
        val prefs = PlaybackPrefs(
            maxHeight = stored.maxHeight, preferredCodec = if (stored.preferAvc) PreferredCodec.AVC else settings.resolve(Settings.preferredCodec),
            audioOnly = stored.audioOnly,
        )
        val constraints = PlaybackConstraints(supportsHls = false, network = NetworkType.UNMETERED)
        val base = "${row.videoId}.${row.kind.name.lowercase()}"
        var plan0 = StreamSelector.plan(set, prefs, constraints)
        (plan0 as? PlaybackPlan.Separate)?.let { sep ->
            if (!merger.canMerge(sep.video.codec, sep.audio.codec)) plan0 = StreamSelector.plan(set, prefs.copy(preferredCodec = PreferredCodec.AVC), constraints)
        }
        val chosen = when (val p = plan0) {
            is PlaybackPlan.Separate -> listOf(
                ResolvedStream(StoredStream("video", p.video.itag, p.video.mimeType, p.video.codec, p.video.height, p.video.bitrate, p.video.contentLength, "$base.video.${Containers.extensionFor(p.video.mimeType, false)}"), p.video.url, p.videoHeaders),
                ResolvedStream(StoredStream("audio", p.audio.itag, p.audio.mimeType, p.audio.codec, 0, p.audio.bitrate, p.audio.contentLength, "$base.audio.${Containers.extensionFor(p.audio.mimeType, true)}"), p.audio.url, p.audioHeaders),
            )
            is PlaybackPlan.Muxed -> listOf(ResolvedStream(StoredStream("muxed", p.stream.itag, p.stream.mimeType, p.stream.codec, p.stream.height, p.stream.bitrate, p.stream.contentLength, "$base.muxed.${Containers.extensionFor(p.stream.mimeType, false)}"), p.stream.url, p.headers))
            is PlaybackPlan.AudioOnly -> listOf(ResolvedStream(StoredStream("audio", p.audio.itag, p.audio.mimeType, p.audio.codec, 0, p.audio.bitrate, p.audio.contentLength, "$base.audio.${Containers.extensionFor(p.audio.mimeType, true)}"), p.audio.url, p.headers))
            is PlaybackPlan.Hls, is PlaybackPlan.Local -> throw DownloadFailure("This video is only offered as a live/HLS stream, which cannot be downloaded")
            is PlaybackPlan.NothingPlayable -> throw DownloadFailure("Nothing to download: ${p.reason}")
        }
        val (container, finalName) = finalFile(row, stored, chosen.map { it.stored })
        val plan = stored.copy(streams = chosen.map { it.stored }, finalName = finalName, container = container?.extension)
        return plan to chosen
    }

    private fun finalFile(row: Download, plan: StoredPlan, streams: List<StoredStream>): Pair<Container?, String> {
        val title = sanitize(row.title)
        return if (streams.size == 2) {
            val c = merger.containerFor(streams.first { it.role == "video" }.codec, streams.first { it.role == "audio" }.codec)
            c to "$title [${row.videoId}].${c.extension}"
        } else {
            val s = streams.single()
            var ext = s.fileName.substringAfterLast('.')
            if (plan.audioOnly && !plan.keepOriginalAudio && ext == "webm" && s.codec.contains("opus", ignoreCase = true)) ext = "opus"
            // An audio-only file is named apart from the video of the same title, so the two never look like duplicates.
            null to "$title [${row.videoId}]${if (plan.audioOnly) " (audio)" else ""}.$ext"
        }
    }

    private fun match(s: StoredStream, set: StreamSet): ResolvedStream? {
        fun headersFor(extra: Map<String, String>) = set.headers + extra
        return when (s.role) {
            "audio" -> (set.audioStreams.firstOrNull { it.itag != null && it.itag == s.itag } ?: set.audioStreams.firstOrNull { it.codec == s.codec && it.bitrate == s.bitrate })
                ?.let { ResolvedStream(s, it.url, headersFor(it.headers)) }
            else -> (set.videoStreams.firstOrNull { it.itag != null && it.itag == s.itag } ?: set.videoStreams.firstOrNull { it.codec == s.codec && it.height == s.height && it.hasAudio == (s.role == "muxed") })
                ?.let { ResolvedStream(s, it.url, headersFor(it.headers)) }
        }
    }

    /** The streams on offer changed under a half-finished download: what was written belongs to a stream that is gone. */
    private fun discardPartials(plan: StoredPlan) {
        val st = storage()
        plan.streams.forEach { st.delete(it.partName); st.delete(it.fileName) }
    }

    private fun deleteFiles(row: Download) {
        val st = storage()
        val plan = repo.plan(row)
        plan.streams.forEach { st.delete(it.partName); st.delete(it.fileName) }
        row.mergedPath?.let { runCatching { st.deletePublished(it) } }
        repo.offlineData(row.id)?.thumbnailPath?.let { runCatching { st.deleteAsset(it) } }
    }

    private fun sanitize(title: String): String {
        val cleaned = title.map { if (it in "\\/:*?\"<>|" || it.code < 32) '_' else it }.joinToString("").trim().trimEnd('.')
        return cleaned.take(80).ifEmpty { "video" }
    }

    // ---- observation ----

    private fun sample() {
        val now = clock.now().toEpochMilliseconds()
        synchronized(lock) {
            for (p in live.values) {
                if (p.lastSampleAtMs == 0L) { p.lastSampleAtMs = now; p.lastSampleBytes = p.bytes; continue }
                val dt = now - p.lastSampleAtMs
                if (dt <= 0) continue
                val instant = (p.bytes - p.lastSampleBytes) * 1000 / dt
                // A light smoothing so the number does not jump with every sample.
                p.speedBps = if (p.speedBps == 0L) instant else (p.speedBps * 3 + instant) / 4
                p.lastSampleAtMs = now; p.lastSampleBytes = p.bytes
            }
        }
    }

    private fun refresh() {
        val rows = repo.all()
        val snapshot = synchronized(lock) { live.mapValues { (it.value.base + it.value.bytes) to it.value.speedBps } }
        _items.value = rows.map { row ->
            val l = snapshot[row.id]
            repo.toItem(row, speedBps = l?.second?.takeIf { it > 0 }, liveBytes = l?.first)
        }
        _active.value = rows.count { it.state == DownloadState.QUEUED || it.state == DownloadState.DOWNLOADING || it.state == DownloadState.MERGING }
    }
}
