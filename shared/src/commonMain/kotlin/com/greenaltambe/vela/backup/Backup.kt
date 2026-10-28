package com.greenaltambe.vela.backup

import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingScope
import com.greenaltambe.vela.data.*
import com.greenaltambe.vela.data.db.VelaDatabase
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Instant

/** The current file format. Older versions are upgraded by [BackupService.migrate]; a NEWER one is refused with a message. */
const val BACKUP_SCHEMA_VERSION = 2

@Serializable data class BackupSetting(val key: String, val scope: String, val id: String, val value: String)
@Serializable data class BackupSubscription(val channelId: String, val name: String, val avatarUrl: String? = null, val subscribedAt: Long)
@Serializable data class BackupVideo(val videoId: String, val title: String, val channelId: String, val channelName: String, val thumbnailUrl: String? = null, val durationSeconds: Long? = null)
@Serializable data class BackupPlaylist(val name: String, val watchLater: Boolean = false, val videos: List<BackupVideo> = emptyList())
@Serializable data class BackupHistory(val videoId: String, val channelId: String, val title: String, val watchedAt: Long, val positionMs: Long, val durationMs: Long, val watchedMs: Long)
@Serializable data class BackupSearch(val query: String, val at: Long = 0)

/**
 * One JSON file with everything personal that is not a credential and not a file: settings (including per-channel and per-video overrides),
 * subscriptions, local playlists (with Watch later), watch history and search history of the LOCAL account. NEVER in it: accounts, cookies
 * or any credential, downloads, caches, the account-mirrored playlists (they come back from the account), or state like the remote-config
 * version. History and search history are optional ([include]).
 */
@Serializable
data class BackupFile(
    val format: String = "vela-backup",
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val createdAt: Long,
    val settings: List<BackupSetting> = emptyList(),
    val subscriptions: List<BackupSubscription> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val history: List<BackupHistory> = emptyList(),
    val searchHistory: List<BackupSearch> = emptyList(),
)

/** What a restore would change, before anything is written. */
data class RestorePreview(
    val settingsChanged: List<String>, val settingsUnchanged: Int,
    val newSubscriptions: Int, val alreadySubscribed: Int,
    val newPlaylists: List<String>, val mergedPlaylists: Map<String, Int>,
    val newHistory: Int, val newSearches: Int,
    val fromVersion: Int, val warnings: List<String>,
) {
    fun describe(): String = buildString {
        append("Settings: ${settingsChanged.size} would change, $settingsUnchanged already match. ")
        append("Subscriptions: $newSubscriptions new ($alreadySubscribed already there). ")
        append("Playlists: ${newPlaylists.size} new${newPlaylists.takeIf { it.isNotEmpty() }?.let { " (${it.joinToString()})" } ?: ""}, ${mergedPlaylists.size} merged (+${mergedPlaylists.values.sum()} videos). ")
        append("History: $newHistory rows to add. Search history: $newSearches new.")
        warnings.forEach { append(" Note: $it") }
    }
}

class BackupException(message: String) : Exception(message)

class BackupService(private val db: VelaDatabase, private val clock: Clock = Clock.System, private val accountId: String = LOCAL_ACCOUNT) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val store get() = SqlDelightSettingsStore(db)

    fun create(includeHistory: Boolean = true, includeSearchHistory: Boolean = true): String {
        val subs = SubscriptionRepository(db, accountId).all().map { BackupSubscription(it.channelId, it.name, it.avatarUrl, it.subscribedAt.toEpochMilliseconds()) }
        val playlists = PlaylistRepository(db, accountId).let { repo ->
            repo.all().filter { !it.isMirror }.map { p -> BackupPlaylist(p.name, p.isWatchLater, repo.items(p.id).map { BackupVideo(it.videoId, it.title, it.channelId, it.channelName, it.thumbnailUrl, it.durationSeconds) }) }
        }
        val history = if (includeHistory) HistoryLibrary(db, accountId).recent(100_000).map { BackupHistory(it.videoId, it.channelId, it.title, it.watchedAt.toEpochMilliseconds(), it.positionMs, it.durationMs, it.watchedMs) } else emptyList()
        val searches = if (includeSearchHistory) SearchHistoryRepository(db, accountId).recent(1000).map { BackupSearch(it) } else emptyList()
        val settings = store.entries().filter { backedUp(it.key) }.map { BackupSetting(it.key, it.scope, it.id, it.value) }
        return json.encodeToString(BackupFile.serializer(), BackupFile(createdAt = clock.now().toEpochMilliseconds(), settings = settings, subscriptions = subs, playlists = playlists, history = history, searchHistory = searches))
    }

    /** Parses and upgrades; throws [BackupException] for anything that is not a Vela backup this version can read. */
    fun parse(text: String): Pair<BackupFile, Int> {
        val root = try { json.parseToJsonElement(text).jsonObject } catch (e: Exception) { throw BackupException("This is not a Vela backup file (not valid JSON).") }
        if (root["format"]?.jsonPrimitive?.contentOrNull != "vela-backup") throw BackupException("This is not a Vela backup file.")
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: throw BackupException("The backup has no schema version.")
        if (version > BACKUP_SCHEMA_VERSION) throw BackupException("This backup was made by a newer Vela (format $version; this one reads up to $BACKUP_SCHEMA_VERSION). Update the app first.")
        return json.decodeFromJsonElement(BackupFile.serializer(), migrate(root, version)) to version
    }

    /**
     * Upgrades older files, one step per version. v1 (the first format) had no per-setting scope/id (global only) and no search timestamps.
     */
    internal fun migrate(root: JsonObject, from: Int): JsonObject {
        var o = root
        if (from < 2) {
            o = JsonObject(o.toMutableMap().apply {
                put("settings", JsonArray((o["settings"] as? JsonArray).orEmpty().map { s ->
                    val so = s.jsonObject
                    JsonObject(so + mapOf("scope" to (so["scope"] ?: JsonPrimitive("GLOBAL")), "id" to (so["id"] ?: JsonPrimitive(""))))
                }))
                put("searchHistory", JsonArray((o["searchHistory"] as? JsonArray).orEmpty().map { q -> if (q is JsonPrimitive) JsonObject(mapOf("query" to q)) else q }))
                put("schemaVersion", JsonPrimitive(2))
            })
        }
        return o
    }

    fun preview(text: String): RestorePreview = plan(text, apply = false)

    /** Applies the merge and returns what it did. Nothing is ever deleted: subscriptions, playlists, history and searches are only added, settings are overwritten with the backup's value. */
    fun restore(text: String, includeSettings: Boolean = true): RestorePreview = plan(text, apply = true, includeSettings = includeSettings)

    private fun plan(text: String, apply: Boolean, includeSettings: Boolean = true): RestorePreview {
        val (file, fromVersion) = parse(text)
        val warnings = mutableListOf<String>()
        val s = store
        // settings
        val changed = mutableListOf<String>(); var same = 0
        val known = com.greenaltambe.vela.core.settings.Settings.all.map { it.key }.toSet()
        var unknownKeys = 0
        for (b in file.settings) {
            if (!backedUp(b.key)) continue
            val layer = runCatching { if (b.scope == SettingScope.GLOBAL.name) SettingLayer.Global else SettingLayer(SettingScope.valueOf(b.scope), b.id) }.getOrNull() ?: continue
            if (b.key !in known && !b.key.startsWith("account.")) unknownKeys++
            if (s.get(b.key, layer) == b.value) { same++; continue }
            changed += b.key + (if (layer.id != null) " [${layer.scope.name.lowercase()} ${layer.id}]" else "")
            if (apply && includeSettings) s.put(b.key, layer, b.value)
        }
        // subscriptions
        val subs = SubscriptionRepository(db, accountId)
        var newSubs = 0; var haveSubs = 0
        for (b in file.subscriptions) { if (subs.isSubscribed(b.channelId)) haveSubs++ else { newSubs++; if (apply) subs.subscribe(b.channelId, b.name, b.avatarUrl, Instant.fromEpochMilliseconds(b.subscribedAt)) } }
        // playlists: by name (Watch later by its system role); videos are only added
        val lists = PlaylistRepository(db, accountId)
        val newLists = mutableListOf<String>(); val merged = linkedMapOf<String, Int>()
        val now = clock.now()
        for (b in file.playlists) {
            val existing = if (b.watchLater) lists.all().firstOrNull { it.isWatchLater } else lists.all().firstOrNull { !it.isMirror && !it.isWatchLater && it.name.equals(b.name, true) }
            val videos = b.videos.map { VideoSummary(it.videoId, it.title, it.channelId, it.channelName, it.thumbnailUrl, it.durationSeconds) }
            if (existing == null) { newLists += b.name; if (apply) lists.add(lists.create(b.name, now), videos, now) }
            else {
                val missing = videos.filter { !lists.contains(existing.id, it.id) }
                if (missing.isNotEmpty()) { merged[b.name] = missing.size; if (apply) lists.add(existing.id, missing, now) }
            }
        }
        // history: a row is the same when video and time match
        var newHist = 0
        if (file.history.isNotEmpty()) {
            val have = HistoryLibrary(db, accountId).recent(100_000).map { it.videoId to it.watchedAt.toEpochMilliseconds() }.toHashSet()
            val repo = com.greenaltambe.vela.data.SqlDelightWatchHistoryRepository(db)
            for (h in file.history) if (have.add(h.videoId to h.watchedAt)) { newHist++; if (apply) { val id = repo.begin(accountId, h.videoId, h.channelId, h.title, Instant.fromEpochMilliseconds(h.watchedAt), h.positionMs, h.durationMs, h.watchedMs); repo.update(id, Instant.fromEpochMilliseconds(h.watchedAt), h.positionMs, h.durationMs, h.watchedMs) } }
        }
        // search history
        val searches = SearchHistoryRepository(db, accountId); val haveQ = searches.recent(100_000).toHashSet(); var newQ = 0
        for (q in file.searchHistory) if (haveQ.add(q.query)) { newQ++; if (apply) searches.add(q.query, Instant.fromEpochMilliseconds(q.at)) }
        if (unknownKeys > 0) warnings += "$unknownKeys setting(s) this version does not know are restored as they are."
        return RestorePreview(changed, same, newSubs, haveSubs, newLists, merged, newHist, newQ, fromVersion, warnings)
    }

    companion object {
        /** Settings rows that are state, caches, secrets or per-device flags and so are neither saved nor restored. */
        private val excluded = listOf("health.", "release.", "remoteConfig.file", "remoteConfig.version", "session.", "battery.", "sponsorblock.userId", "captcha.", "governor.")
        fun backedUp(key: String) = excluded.none { key.startsWith(it) }
    }
}
