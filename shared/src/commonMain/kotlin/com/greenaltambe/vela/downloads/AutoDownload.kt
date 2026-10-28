package com.greenaltambe.vela.downloads

import com.greenaltambe.vela.core.network.NetworkMonitor
import com.greenaltambe.vela.core.network.NetworkType
import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.core.settings.SettingsStore
import com.greenaltambe.vela.data.DownloadKind
import com.greenaltambe.vela.data.DownloadState
import com.greenaltambe.vela.data.HistoryLibrary
import com.greenaltambe.vela.data.PlaylistRepository
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** [target] is a channel id, or [WATCH_LATER] for the local Watch later playlist. Only videos published after [sinceMs] are fetched (no backlog). */
@Serializable
data class AutoDownloadRule(
    val target: String, val name: String, val enabled: Boolean = true,
    val maxHeight: Int? = 720, val audioOnly: Boolean = false, val wifiOnly: Boolean = true, val sinceMs: Long = 0,
) { companion object { const val WATCH_LATER = "watch-later" } }

/** Rules, and the two small lists the engine needs, kept as JSON in the settings store (no schema change). */
class AutoDownloadStore(private val store: SettingsStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val rulesKey = "downloads.auto.rules"; private val seenKey = "downloads.auto.seen"; private val autoKey = "downloads.auto.ids"
    private val ids = ListSerializer(kotlinx.serialization.serializer<String>())

    fun rules(): List<AutoDownloadRule> = store.get(rulesKey, SettingLayer.Global)?.let { runCatching { json.decodeFromString(ListSerializer(AutoDownloadRule.serializer()), it) }.getOrNull() }.orEmpty()
    fun saveRules(list: List<AutoDownloadRule>) = store.put(rulesKey, SettingLayer.Global, json.encodeToString(ListSerializer(AutoDownloadRule.serializer()), list))
    fun upsert(rule: AutoDownloadRule) = saveRules(rules().filter { it.target != rule.target } + rule)
    fun remove(target: String) = saveRules(rules().filter { it.target != target })
    fun ruleFor(target: String) = rules().firstOrNull { it.target == target }

    /** Videos the engine already queued once (so a deleted download is not fetched again). */
    fun seen(): Set<String> = read(seenKey).toSet()
    fun markSeen(id: String) = write(seenKey, (read(seenKey) + id).distinct().takeLast(3000))
    /** Downloads the engine started: only these are ever cleaned up automatically. */
    fun autoIds(): Set<String> = read(autoKey).toSet()
    fun markAuto(id: String) = write(autoKey, (read(autoKey) + id).distinct().takeLast(3000))
    fun forgetAuto(id: String) = write(autoKey, read(autoKey) - id)
    private fun read(key: String): List<String> = store.get(key, SettingLayer.Global)?.let { runCatching { json.decodeFromString(ids, it) }.getOrNull() }.orEmpty()
    private fun write(key: String, v: List<String>) = store.put(key, SettingLayer.Global, json.encodeToString(ids, v))
}

class AutoDownloadReport(val queued: List<VideoSummary>, val notes: List<String>, val cleaned: Int) {
    fun describe() = buildString {
        append(if (queued.isEmpty()) "Nothing new to download." else "Queued ${queued.size} new video${if (queued.size == 1) "" else "s"}.")
        if (cleaned > 0) append(" Removed $cleaned watched download${if (cleaned == 1) "" else "s"}.")
        notes.forEach { append(" $it") }
    }
}

/**
 * One pass of the automatic downloads. Always through the DownloadManager and the ANONYMOUS extractor passed in (the account is never used
 * here: the caller hands in the downloads chain, whose account mode is "only if the viewer allowed it"). Limits per pass keep it quiet:
 * at most [perRule] videos per rule. Storage: when a cap is set, watched downloads that this engine started are removed oldest-watched-first
 * to make room; manual downloads and unwatched ones are never touched, and if there is still no room the video is skipped with a note.
 */
class AutoDownloader(
    private val store: AutoDownloadStore,
    private val extractor: Extractor,
    private val manager: DownloadManager,
    private val repo: DownloadRepository,
    private val playlists: () -> PlaylistRepository,
    private val history: () -> HistoryLibrary,
    private val network: NetworkMonitor,
    private val settings: SettingsResolver,
    private val clock: Clock = Clock.System,
    private val notifier: DownloadNotifier? = null,
    private val perRule: Int = 5,
    private val watchedAt: () -> Map<String, Long> = { history().recent(5000).groupBy { it.videoId }.mapValues { e -> e.value.maxOf { it.watchedAt.toEpochMilliseconds() } } },
) {
    suspend fun run(): AutoDownloadReport {
        val queued = mutableListOf<VideoSummary>(); val notes = mutableListOf<String>()
        val cleanedAfterWatch = cleanupWatched()
        for (rule in store.rules().filter { it.enabled }) {
            if (rule.wifiOnly && network.type.value != NetworkType.UNMETERED) { notes += "${rule.name}: waiting for Wi-Fi."; continue }
            if (network.type.value == NetworkType.OFFLINE) { notes += "${rule.name}: offline."; continue }
            val fresh = candidates(rule) ?: run { notes += "${rule.name}: could not check for new videos."; null } ?: continue
            for (v in fresh.take(perRule)) {
                if (!makeRoom(v)) { notes += "Storage cap reached; skipped \"${v.title.take(40)}\"."; continue }
                val id = runCatching { manager.enqueue(DownloadRequest(v, rule.audioOnly, rule.maxHeight)) }.getOrNull() ?: continue
                store.markSeen(v.id); store.markAuto(v.id); queued += v
                if (id < 0) continue
            }
        }
        val cleanedCap = 0
        val report = AutoDownloadReport(queued, notes, cleanedAfterWatch + cleanedCap)
        if (queued.isNotEmpty()) notifier?.summary("Automatic downloads: ${queued.size} new video${if (queued.size == 1) "" else "s"} queued")
        return report
    }

    /** Videos for the rule that are new, not downloaded or queued, and never fetched before. Null = the check failed. */
    private suspend fun candidates(rule: AutoDownloadRule): List<VideoSummary>? {
        val seen = store.seen()
        val list = if (rule.target == AutoDownloadRule.WATCH_LATER) {
            playlists().let { p -> p.items(p.watchLaterId()).map { it.toSummary() } }
        } else when (val r = extractor.recentUploads(rule.target)) {
            is ExtractorResult.Success -> r.value.filter { (it.uploadedAt?.toEpochMilliseconds() ?: 0L) >= rule.sinceMs && !it.isLive }
            is ExtractorResult.Failure -> return null
        }
        return list.filter { it.id !in seen && repo.find(it.id, if (rule.audioOnly) DownloadKind.AUDIO_ONLY else DownloadKind.VIDEO) == null }
            .sortedBy { it.uploadedAt?.toEpochMilliseconds() ?: 0L }
    }

    /** True when [video] fits under the cap (after removing watched automatic downloads, oldest watched first). */
    private fun makeRoom(video: VideoSummary): Boolean {
        val capBytes = (settings.resolve(Settings.downloadsStorageCapGb) * 1_000_000_000L).toLong()
        if (capBytes <= 0) return true
        val estimate = 200_000_000L   // a typical 720p video; the real size is not known before resolving
        var used = repo.completedBytes()
        if (used + estimate <= capBytes) return true
        val watched = watchedAt(); val auto = store.autoIds()
        val victims = manager.items.value.filter { it.state == DownloadState.COMPLETED && it.videoId in auto && it.videoId in watched }.sortedBy { watched[it.videoId] }
        for (d in victims) {
            if (used + estimate <= capBytes) break
            used -= d.bytesTotal ?: d.bytesDone; manager.remove(d.id); store.forgetAuto(d.videoId)
        }
        return used + estimate <= capBytes
    }

    /** Automatic downloads watched more than the configured number of days ago are removed (0 = keep). */
    private fun cleanupWatched(): Int {
        val days = settings.resolve(Settings.autoDownloadDeleteDays).toInt(); if (days <= 0) return 0
        val cutoff = (clock.now() - days.days).toEpochMilliseconds()
        val watched = watchedAt(); val auto = store.autoIds(); var n = 0
        for (d in manager.items.value.filter { it.state == DownloadState.COMPLETED && it.videoId in auto }) {
            val at = watched[d.videoId] ?: continue
            if (at <= cutoff) { manager.remove(d.id); store.forgetAuto(d.videoId); n++ }
        }
        return n
    }
}
