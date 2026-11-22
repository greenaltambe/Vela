package com.greenaltambe.vela.backup

import com.greenaltambe.vela.auth.AccountKind
import com.greenaltambe.vela.auth.AccountManager
import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingsStore
import com.greenaltambe.vela.data.LOCAL_ACCOUNT
import com.greenaltambe.vela.data.db.VelaDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class StoredBackup(val name: String, val sizeBytes: Long, val modifiedMs: Long)

/** A place backups are kept: the app's own folder, or a folder the viewer chose (which survives reinstalling the app). */
interface BackupStorage {
    val label: String
    suspend fun list(): List<StoredBackup>
    suspend fun read(name: String): String?
    suspend fun write(name: String, text: String)
    suspend fun delete(name: String)
}

/** Opens and describes the optional second backup folder; null on platforms that cannot pick one. */
interface BackupFolders {
    suspend fun choose(): String?
    fun label(descriptor: String): String
    fun open(descriptor: String): BackupStorage
}

enum class BackupKind(val prefix: String, val title: String, val keep: Int) {
    AUTO("auto", "Daily backup", 7),
    FIRST_SYNC("first-sync", "Before the first sync", 5),
    BEFORE_CHANGE("before-change", "Before a large sync change", 5),
    MANUAL("manual", "Backup made on request", 10),
}

data class BackupItem(val name: String, val kind: BackupKind?, val account: String?, val modifiedMs: Long, val sizeBytes: Long, val storage: String)

/**
 * Automatic backups, the safety net under everything else: a copy of the local data every day, one before the first sync on a device, and one before a sync
 * changes a lot at once. Each backup is written to the app's own folder and, when chosen, to a second folder too (so reinstalling the app does not lose
 * them). Only the newest few of each kind are kept. A backup holds what Backup and restore holds (no sign-ins), for the local profile and for every Google
 * account on the device (their subscriptions, groups, history and playlists).
 */
class AutoBackups(
    private val db: VelaDatabase,
    private val settings: SettingsStore,
    private val internal: BackupStorage?,
    private val folders: BackupFolders? = null,
    private val accounts: AccountManager? = null,
    private val clock: Clock = Clock.System,
) {
    private fun get(key: String) = settings.get(key, SettingLayer.Global)
    private fun put(key: String, v: String) = settings.put(key, SettingLayer.Global, v)

    val isAvailable: Boolean get() = internal != null
    val foldersAvailable: Boolean get() = folders != null
    var enabled: Boolean
        get() = get(ENABLED) != "false"
        set(v) { put(ENABLED, v.toString()) }
    val externalLabel: String? get() = get(FOLDER)?.takeIf { it.isNotBlank() }?.let { folders?.label(it) ?: it }
    fun setExternalFolder(descriptor: String?) { if (descriptor == null) settings.remove(FOLDER, SettingLayer.Global) else put(FOLDER, descriptor) }
    val lastAutoAt: Long? get() = get(LAST_AUTO)?.toLongOrNull()

    private fun storages(): List<BackupStorage> = listOfNotNull(internal, get(FOLDER)?.takeIf { it.isNotBlank() }?.let { d -> runCatching { folders?.open(d) }.getOrNull() })

    private fun accountIds(): List<String> = listOf(LOCAL_ACCOUNT) + accounts?.all?.value.orEmpty().filter { it.kind == AccountKind.GOOGLE }.map { it.id }

    /** Writes a backup of every profile to every storage. Returns the names written. Throws when the app's own folder cannot be written (a backup that does not exist must not be reported as made). */
    suspend fun create(kind: BackupKind): List<String> {
        val primary = internal ?: throw BackupException("This device has nowhere to keep automatic backups.")
        val stamp = stamp(clock.now().toEpochMilliseconds())
        val written = mutableListOf<String>()
        for (id in accountIds()) {
            val text = BackupService(db, clock, id).create(includeHistory = true, includeSearchHistory = true)
            val name = "vela-${kind.prefix}-$stamp" + (if (id == LOCAL_ACCOUNT) "" else "-" + id.takeLast(8)) + ".json"
            primary.write(name, text)
            written += name
            for (extra in storages().drop(1)) runCatching { extra.write(name, text) }       // a missing second folder must not fail the backup
        }
        prune(kind)
        if (kind == BackupKind.AUTO) put(LAST_AUTO, clock.now().toEpochMilliseconds().toString())
        return written
    }

    /** Checks shortly after start and then every hour while the app runs (the backup itself happens at most once a day). */
    fun startAuto(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            delay(20_000)
            while (isActive) { runCatching { runIfDue() }; delay(3_600_000) }
        }
    }

    /** Daily backup when switched on and a day has passed. Returns true when it made one. */
    suspend fun runIfDue(): Boolean {
        if (!enabled || internal == null) return false
        val last = lastAutoAt ?: 0L
        if (clock.now().toEpochMilliseconds() - last < DAY) return false
        return runCatching { create(BackupKind.AUTO) }.isSuccess
    }

    private suspend fun prune(kind: BackupKind) {
        for (s in storages()) runCatching {
            val mine = s.list().filter { kindOf(it.name) == kind }
            // keep the newest `keep` backups (all profiles made in the same run share a timestamp)
            val stamps = mine.map { stampOf(it.name) }.distinct().sortedDescending()
            val drop = stamps.drop(kind.keep).toSet()
            mine.filter { stampOf(it.name) in drop }.forEach { s.delete(it.name) }
        }
    }

    suspend fun list(): List<BackupItem> {
        val out = mutableListOf<BackupItem>()
        for (s in storages()) runCatching { s.list().forEach { out += BackupItem(it.name, kindOf(it.name), accountOf(it.name), it.modifiedMs, it.sizeBytes, s.label) } }
        return out.sortedByDescending { it.modifiedMs }
    }

    suspend fun read(item: BackupItem): String? = storages().firstOrNull { it.label == item.storage }?.read(item.name)

    suspend fun delete(item: BackupItem) { storages().firstOrNull { it.label == item.storage }?.delete(item.name) }

    companion object {
        private const val ENABLED = "backup.auto"; private const val FOLDER = "backup.folder"; private const val LAST_AUTO = "backup.lastAuto"
        private const val DAY = 24L * 3600 * 1000

        fun kindOf(name: String): BackupKind? = BackupKind.entries.sortedByDescending { it.prefix.length }.firstOrNull { name.startsWith("vela-${it.prefix}-") }
        /** The last 8 characters of the account id for a file made for a Google account, null for the local profile. */
        fun accountOf(name: String): String? = Regex("""\d{8}-\d{6}-([A-Za-z0-9_]+)\.json""").find(name)?.groupValues?.get(1)
        private fun stampOf(name: String) = Regex("""(\d{8}-\d{6})""").find(name)?.value ?: ""

        /** `20261122-150402` in UTC. */
        fun stamp(epochMs: Long): String {
            val days = epochMs.floorDiv(86_400_000L); val secs = epochMs.mod(86_400_000L) / 1000
            // civil-from-days (Howard Hinnant)
            val z = days + 719468; val era = z.floorDiv(146097L); val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365; val y = yoe + era * 400
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100); val mp = (5 * doy + 2) / 153
            val d = doy - (153 * mp + 2) / 5 + 1; val m = if (mp < 10) mp + 3 else mp - 9
            val year = if (m <= 2) y + 1 else y
            fun two(n: Long) = n.toString().padStart(2, '0')
            return "${year.toString().padStart(4, '0')}${two(m)}${two(d)}-${two(secs / 3600)}${two(secs % 3600 / 60)}${two(secs % 60)}"
        }
    }
}
