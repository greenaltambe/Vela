package com.greenaltambe.vela.sync

import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingScope
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.data.*
import com.greenaltambe.vela.data.db.VelaDatabase
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.feed.Blocklist
import com.greenaltambe.vela.feed.SubscriptionGroups
import kotlin.time.Instant

/** What the viewer can switch on or off in Sync. Only data of the LOCAL account is ever synced; account data comes from YouTube itself. */
enum class SyncKind(val title: String, val detail: String) {
    SUBSCRIPTIONS("Subscriptions and groups", "Channels you follow, their notification level and your groups."),
    PLAYLISTS("Playlists and Watch later", "Your own playlists and their videos."),
    HISTORY("Watch and search history", "What you watched and where you stopped, and your recent searches."),
    BLOCKLIST("Blocklist", "Blocked channels and words."),
    SETTINGS("Settings and look", "Playback, feed and appearance settings (not paths, downloads or sign-in)."),
    ACCOUNTS("Google sign-in (off by default)", "Signs your other devices in to the same Google accounts, and keeps each account's own subscriptions, groups, playlists and history in step. The sign-in is encrypted with your sync code; only use this on devices you own."),
}

/** Keys with `/` in names would clash with the key structure, so names are escaped. */
internal fun esc(s: String) = s.replace("%", "%25").replace("/", "%2F").replace("\t", "%09").replace("\n", "%0A")
internal fun unesc(s: String) = s.replace("%0A", "\n").replace("%09", "\t").replace("%2F", "/").replace("%25", "%")
/** Local-profile keys keep their short prefix; a Google account's data lives under `a/<id>/` so each account's data stays its own. */
internal fun scopePrefix(accountId: String) = if (accountId == LOCAL_ACCOUNT) "" else "a/${esc(accountId)}/"

internal fun fields(v: String) = v.split('\t').map(::unesc)
internal fun join(vararg f: Any?) = f.joinToString("\t") { esc(it?.toString().orEmpty()) }

class SubscriptionsDomain(private val db: VelaDatabase, private val now: () -> Instant, private val accountId: String = LOCAL_ACCOUNT) : SyncDomain {
    override val prefix = scopePrefix(accountId) + "sub/"
    private val repo get() = SubscriptionRepository(db, accountId)

    override fun read(): Map<String, String> {
        val levels = repo.notifyLevels()
        return repo.all().associate { "$prefix${esc(it.channelId)}" to join(it.name, it.avatarUrl, it.subscribedAt.toEpochMilliseconds(), (levels[it.channelId] ?: NotifyLevel.PERSONALIZED).name) }
    }

    override fun apply(changes: Map<String, String?>) {
        for ((k, v) in changes) {
            val id = unesc(k.removePrefix(prefix))
            if (v == null) { repo.unsubscribe(id); continue }
            val f = fields(v)
            repo.subscribe(id, f[0], f.getOrNull(1)?.ifBlank { null }, Instant.fromEpochMilliseconds(f.getOrNull(2)?.toLongOrNull() ?: now().toEpochMilliseconds()))
            NotifyLevel.entries.firstOrNull { it.name == f.getOrNull(3) }?.let { repo.setNotifyLevel(id, it) }
        }
    }
}

/** Groups are matched by name (ids are per device), members one key each so two devices adding different channels both survive. */
class GroupsDomain(accountId: String = LOCAL_ACCOUNT, private val groups: () -> SubscriptionGroups) : SyncDomain {
    override val prefix = scopePrefix(accountId) + "grp/"
    private fun norm(name: String) = esc(name.trim().lowercase())

    override fun read(): Map<String, String> = buildMap {
        for (g in groups().groups()) {
            put("$prefix${norm(g.name)}", g.name)
            for (c in g.channelIds) put("$prefix${norm(g.name)}/m/${esc(c)}", "1")
        }
    }

    override fun apply(changes: Map<String, String?>) {
        val g = groups()
        for ((k, v) in changes.entries.sortedBy { it.key }) {
            val parts = k.removePrefix(prefix).split("/m/")
            val groupKey = parts[0]
            var group = g.groups().firstOrNull { norm(it.name) == groupKey }
            if (parts.size == 1) {
                if (v == null) group?.let { g.delete(it.id) } else if (group == null) g.create(v)
            } else {
                if (group == null && v != null) group = g.create(unesc(groupKey))
                val ch = unesc(parts[1])
                if (group != null) g.setMembers(group.id, if (v == null) group.channelIds - ch else group.channelIds + ch)
            }
        }
    }
}

class BlocklistDomain(private val blocklist: Blocklist) : SyncDomain {
    override val prefix = "blk/"
    override fun read(): Map<String, String> = buildMap {
        blocklist.channels().forEach { put("blk/c/${esc(it.id)}", it.name) }
        blocklist.keywords().forEach { put("blk/w/${esc(it.lowercase())}", it) }
    }

    override fun apply(changes: Map<String, String?>) {
        for ((k, v) in changes) {
            val rest = k.removePrefix(prefix)
            val id = unesc(rest.drop(2))
            if (rest.startsWith("c/")) { if (v == null) blocklist.unblockChannel(id) else blocklist.blockChannel(id, v) }
            else if (rest.startsWith("w/")) { if (v == null) blocklist.removeKeyword(id) else blocklist.addKeyword(v) }
        }
    }
}

/** Playlists are matched by name, items by video id. Item order is not synced (each device appends), so the value never depends on position. */
class PlaylistsDomain(private val db: VelaDatabase, private val now: () -> Instant, private val accountId: String = LOCAL_ACCOUNT) : SyncDomain {
    override val prefix = scopePrefix(accountId) + "pl/"
    private val repo get() = PlaylistRepository(db, accountId)
    private fun listKey(p: PlaylistSummary) = if (p.isWatchLater) WL else esc(p.name.trim().lowercase())

    override fun read(): Map<String, String> = buildMap {
        val r = repo
        for (p in r.all().filter { !it.isMirror }) {
            val lk = listKey(p)
            put("$prefix$lk", p.name)
            for (v in r.items(p.id)) put("$prefix$lk/i/${esc(v.videoId)}", join(v.title, v.channelId, v.channelName, v.thumbnailUrl, v.durationSeconds))
        }
    }

    override fun apply(changes: Map<String, String?>) {
        val r = repo
        for ((k, v) in changes.entries.sortedBy { it.key }) {
            val parts = k.removePrefix(prefix).split("/i/")
            var list = r.all().firstOrNull { !it.isMirror && listKey(it) == parts[0] }
            if (parts.size == 1) {
                if (v == null) { if (list != null && !list.isWatchLater) r.delete(list.id) }
                else if (list == null) r.create(v, now())
            } else {
                if (list == null && v != null) list = r.all().firstOrNull { it.isWatchLater && parts[0] == WL } ?: r.create(unesc(parts[0]), now()).let { id -> r.all().first { it.id == id } }
                if (list == null) continue
                val vid = unesc(parts[1])
                if (v == null) r.remove(list.id, vid)
                else {
                    val f = fields(v)
                    r.add(list.id, listOf(VideoSummary(vid, f[0], f[1], f[2], f.getOrNull(3)?.ifBlank { null }, f.getOrNull(4)?.toLongOrNull())), now())
                }
            }
        }
    }

    private companion object { const val WL = "@watchlater" }
}

/** One record per video: the latest watch. Its time is when the video was watched, so an old edit found late cannot beat a newer watch elsewhere. */
class HistoryDomain(private val db: VelaDatabase, private val now: () -> Instant, private val accountId: String = LOCAL_ACCOUNT, private val limit: Long = 10_000) : SyncDomain {
    override val prefix = scopePrefix(accountId) + "hist/"
    private val lib get() = HistoryLibrary(db, accountId)

    private fun latestByVideo(): Map<String, WatchRecord> {
        val out = LinkedHashMap<String, WatchRecord>()
        for (r in lib.recent(limit * 3)) { val cur = out[r.videoId]; if (cur == null || r.watchedAt > cur.watchedAt) out[r.videoId] = r }
        return out
    }

    override fun read(): Map<String, String> = latestByVideo().entries.take(limit.toInt()).associate { (id, r) ->
        "$prefix${esc(id)}" to join(r.watchedAt.toEpochMilliseconds(), r.positionMs, r.durationMs, r.watchedMs, r.channelId, r.title)
    }

    override fun happenedAt(value: String) = value.substringBefore('\t').toLongOrNull()

    override fun apply(changes: Map<String, String?>) {
        val lib = lib
        val repo = SqlDelightWatchHistoryRepository(db)
        val have = latestByVideo()
        for ((k, v) in changes) {
            val id = unesc(k.removePrefix(prefix))
            if (v == null) { lib.deleteVideo(id); continue }
            val f = fields(v)
            val at = Instant.fromEpochMilliseconds(f[0].toLongOrNull() ?: continue)
            if (have[id]?.watchedAt?.let { it >= at } == true && have[id]?.watchedAt != at) continue
            val row = repo.begin(accountId, id, f.getOrElse(4) { "" }, f.getOrElse(5) { "" }, at, f[1].toLongOrNull() ?: 0, f[2].toLongOrNull() ?: 0, f[3].toLongOrNull() ?: 0)
            repo.update(row, at, f[1].toLongOrNull() ?: 0, f[2].toLongOrNull() ?: 0, f[3].toLongOrNull() ?: 0)
        }
    }
}

/** Recent searches: one record per query, timestamped by when it was searched, so a later search wins over an older delete and a delete wins over an older search. */
class SearchHistoryDomain(private val db: VelaDatabase, private val now: () -> Instant, private val accountId: String = LOCAL_ACCOUNT) : SyncDomain {
    override val prefix = scopePrefix(accountId) + "srch/"
    private val repo get() = SearchHistoryRepository(db, accountId)

    override fun read(): Map<String, String> = repo.recentWithTime(1000).associate { (q, at) -> "$prefix${esc(q.lowercase())}" to join(q, at) }

    override fun happenedAt(value: String) = fields(value).getOrNull(1)?.toLongOrNull()

    override fun apply(changes: Map<String, String?>) {
        val r = repo
        for ((k, v) in changes) {
            if (v == null) { val have = r.recentWithTime(1000).firstOrNull { "$prefix${esc(it.first.lowercase())}" == k }; if (have != null) r.remove(have.first); continue }
            val f = fields(v)
            val at = f.getOrNull(1)?.toLongOrNull() ?: continue
            // the same query in another case is the same search: keep one row
            r.recentWithTime(1000).firstOrNull { it.first.equals(f[0], ignoreCase = true) && it.first != f[0] }?.let { r.remove(it.first) }
            r.add(f[0], Instant.fromEpochMilliseconds(at))
        }
    }
}

/** Settings rows that mean the same on any device. Layout (density, text size, bars), paths, downloads, sign-in and state rows are left alone. */
class SettingsDomain(private val db: VelaDatabase) : SyncDomain {
    override val prefix = "set/"
    private val store get() = SqlDelightSettingsStore(db)

    override fun read(): Map<String, String> = store.entries().filter { syncable(it.key) }.associate { "set/${it.scope}/${esc(it.id)}/${it.key}" to it.value }

    override fun apply(changes: Map<String, String?>) {
        val s = store
        for ((k, v) in changes) {
            val p = k.removePrefix(prefix).split('/', limit = 3)
            if (p.size < 3 || !syncable(p[2])) continue
            val layer = runCatching { if (p[0] == SettingScope.GLOBAL.name) SettingLayer.Global else SettingLayer(SettingScope.valueOf(p[0]), unesc(p[1])) }.getOrNull() ?: continue
            if (v == null) s.remove(p[2], layer) else s.put(p[2], layer, v)
        }
    }

    companion object {
        private val never = listOf("account.", "downloads.", "ytdlp.", "debug.", "remoteConfig.", "session.", "health.", "release.", "battery.", "captcha.", "governor.", "profile.",
            "sync.", "sponsorblock.userId", "blocklist.", "subscriptions.groups.", "notify.", "history.accountSync", "content.region")
        private val appearance = setOf("look.theme", "look.colorMode", "look.colorScheme", "look.palette", "look.amoled", "look.customColor", "look.font", "look.corners", "look.glass",
            "look.iconStyle", "look.iconWeight", "look.iconRounded", "look.titleWeight", "look.progressStyle", "look.reduceMotion")
        private val schema: Set<String> by lazy { Settings.all.map { it.key }.toSet() }
        fun syncable(key: String) = never.none { key.startsWith(it) } && (key in schema || key in appearance)
    }
}
