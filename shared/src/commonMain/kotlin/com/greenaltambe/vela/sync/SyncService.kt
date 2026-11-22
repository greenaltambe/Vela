package com.greenaltambe.vela.sync

import com.greenaltambe.vela.auth.CredentialStore
import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingsStore
import com.greenaltambe.vela.data.db.VelaDatabase
import com.greenaltambe.vela.feed.Blocklist
import com.greenaltambe.vela.feed.SubscriptionGroups
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** The platform's way to choose and open the folder sync uses. Returns null when the viewer cancels. */
interface SyncFolders {
    /** Lets the viewer pick a folder; returns a descriptor to remember (a path or a content URI). */
    suspend fun choose(): String?
    /** A short readable name for the folder ("Dropbox/Vela"). */
    fun label(descriptor: String): String
    fun open(descriptor: String): SyncBackend
}

enum class SyncMode { LAN, FOLDER }

data class SyncConfig(
    val mode: SyncMode = SyncMode.LAN,
    val folder: String? = null,
    val hasCode: Boolean = false,
    val auto: Boolean = true,
    val kinds: Set<SyncKind> = SyncKind.entries.toSet(),
    val deviceName: String = "",
) { val isSetUp get() = hasCode && (mode == SyncMode.LAN || folder != null) }

sealed interface SyncStatus {
    data object NotSetUp : SyncStatus
    data class Idle(val lastSyncAt: Long?, val devices: List<SyncDevice>) : SyncStatus
    data object Syncing : SyncStatus
    /** [wrongCode] is true when other devices' files could not be opened with this code. */
    data class Failed(val message: String, val wrongCode: Boolean = false) : SyncStatus
}

/** Owns Sync's settings, secret and schedule. Everything the viewer sets in the Sync screen goes through here. */
class SyncService(
    private val db: VelaDatabase,
    private val store: SettingsStore,
    private val secrets: CredentialStore,
    private val folders: SyncFolders?,
    private val lan: LanTransport? = null,
    private val blocklist: Blocklist,
    private val accounts: com.greenaltambe.vela.auth.AccountManager? = null,
    private val deviceLabel: String,
    private val clock: Clock = Clock.System,
    /** Test seam: replaces the folder backend and codec. */
    private val backendOverride: ((String) -> SyncBackend)? = null,
    /** Where the merge runs. */
    private val work: kotlin.coroutines.CoroutineContext = Dispatchers.Default,
) {
    private val mutex = Mutex()
    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<SyncConfig> get() = _config
    private val _status = MutableStateFlow<SyncStatus>(initialStatus())
    val status: StateFlow<SyncStatus> get() = _status
    private var nudgeJob: Job? = null
    init { if (_config.value.isSetUp) ensureTransport() }

    val isAvailable: Boolean get() = folders != null || lan != null || backendOverride != null
    val lanAvailable: Boolean get() = lan != null
    /** Devices on this Wi-Fi right now (empty unless the connection is "Same Wi-Fi"). */
    val nearby: kotlinx.coroutines.flow.StateFlow<List<LanPeer>> get() = lan?.peers ?: MutableStateFlow(emptyList())
    fun folderLabel(): String? = _config.value.folder?.let { folders?.label(it) ?: it }
    /** The sync code, to show on this device so it can be typed into the next one. */
    fun code(): String? = secrets.secret(CODE)

    private fun get(key: String) = store.get(key, SettingLayer.Global)
    private fun put(key: String, v: String) = store.put(key, SettingLayer.Global, v)

    private fun loadConfig() = SyncConfig(
        mode = if (get(MODE) == "FOLDER" || (lan == null && get(MODE) != "LAN")) SyncMode.FOLDER else SyncMode.LAN,
        folder = get(FOLDER)?.ifBlank { null },
        hasCode = secrets.secret(CODE) != null,
        auto = get(AUTO) != "false",
        kinds = SyncKind.entries.filter { if (it == SyncKind.ACCOUNTS) get(KIND + it.name) == "true" else get(KIND + it.name) != "false" }.toSet(),
        deviceName = get(NAME) ?: deviceLabel,
    )

    private fun initialStatus(): SyncStatus = if (_config.value.isSetUp) SyncStatus.Idle(get(LAST)?.toLongOrNull(), emptyList()) else SyncStatus.NotSetUp

    private fun refresh() {
        _config.value = loadConfig()
        ensureTransport()
        if (!_config.value.isSetUp) _status.value = SyncStatus.NotSetUp
        else if (_status.value is SyncStatus.NotSetUp) _status.value = SyncStatus.Idle(get(LAST)?.toLongOrNull(), emptyList())
    }

    fun setMode(mode: SyncMode) { put(MODE, mode.name); refresh() }
    fun setFolder(descriptor: String) { put(FOLDER, descriptor); refresh() }
    fun setCode(code: String) { secrets.putSecret(CODE, normalizeSyncCode(code)); refresh() }
    fun setAuto(on: Boolean) { put(AUTO, on.toString()); refresh() }
    fun setKind(kind: SyncKind, on: Boolean) { put(KIND + kind.name, on.toString()); refresh() }
    fun setDeviceName(name: String) { put(NAME, name.trim().ifEmpty { deviceLabel }); refresh() }

    /** Stops syncing on this device. Data already on this device and the files in the folder stay as they are. */
    fun turnOff() {
        secrets.deleteSecret(CODE)
        store.remove(FOLDER, SettingLayer.Global); store.remove(STATE, SettingLayer.Global); store.remove(LAST, SettingLayer.Global)
        lan?.stop(); lan?.reset()
        refresh(); _status.value = SyncStatus.NotSetUp
    }

    /** Starts or stops the Wi-Fi side to match the settings. Cheap to call often. */
    fun ensureTransport() {
        val c = _config.value
        val code = secrets.secret(CODE)
        if (lan != null && c.isSetUp && c.mode == SyncMode.LAN && code != null) lan.start(code, deviceId(), c.deviceName) { nudgeSoon(1_500) }
        else lan?.stop()
    }

    private fun nudgeSoon(delayMs: Long) {
        if (!_config.value.isSetUp) return
        nudgeJob?.cancel()
        nudgeJob = scope.launch { delay(delayMs); syncNow() }
    }

    /** For tests. */
    fun deviceIdForTest(): String = deviceId()

    private fun deviceId(): String = get(DEVICE_ID) ?: List(8) { "0123456789abcdef".random() }.joinToString("").also { put(DEVICE_ID, it) }

    /** Owned by the service, so a screen leaving (the setup screen turns into the status screen mid-sync) cannot cancel a sync halfway. */
    private val scope = CoroutineScope(SupervisorJob() + work)

    /** Runs one sync now. Safe to call from anywhere and often: overlapping calls wait for each other; the caller going away does not stop it. */
    suspend fun syncNow(): SyncStatus = scope.async { runSync() }.await()

    private suspend fun runSync(): SyncStatus = mutex.withLock {
        val cfg = _config.value
        val code = secrets.secret(CODE)
        val folder = if (cfg.mode == SyncMode.LAN) "lan" else cfg.folder
        if (folder == null || code == null || !cfg.isSetUp) return@withLock SyncStatus.NotSetUp.also { _status.value = it }
        _status.value = SyncStatus.Syncing
        ensureTransport()
        val result = try {
            withContext(work) {
              suspend fun once(): SyncResult {
                val backend = backendOverride?.invoke(folder) ?: if (cfg.mode == SyncMode.LAN) lan!!.backend() else folders!!.open(folder)
                val domains = buildDomains(cfg)
                return SyncEngine(deviceId(), domains, backend, passphraseCodec(code), object : SyncStateStore {
                    override fun load() = get(STATE)
                    override fun save(json: String) = put(STATE, json)
                }, { clock.now().toEpochMilliseconds() }, cfg.deviceName).sync()
              }
              val before = accounts?.all?.value?.map { it.id }?.toSet()
              val first = once()
              // A sign-in that arrived adds an account whose data was not covered yet: go once more now instead of waiting.
              if (accounts != null && accounts.all.value.map { it.id }.toSet() != before) once() else first
            }
        } catch (e: CancellationException) {
            _status.value = SyncStatus.Idle(get(LAST)?.toLongOrNull(), emptyList())
            throw e
        } catch (e: SyncWrongCodeException) {
            return@withLock SyncStatus.Failed("That sync code does not open the files in the folder. Check it matches the code shown on your other device.", wrongCode = true).also { _status.value = it }
        } catch (e: SyncAbortedException) {
            return@withLock SyncStatus.Failed(e.message ?: "Sync stopped to protect your data.").also { _status.value = it }
        } catch (e: Exception) {
            return@withLock SyncStatus.Failed("Could not reach the sync folder: ${e.message ?: e::class.simpleName}").also { _status.value = it }
        }
        val at = clock.now().toEpochMilliseconds()
        put(LAST, at.toString())
        lastFingerprint = runCatching { withContext(work) { localFingerprint(cfg) } }.getOrNull()
        val others = result.devices.filter { it.id != deviceId() }
        val st: SyncStatus = SyncStatus.Idle(at, others)
        _status.value = st
        st
    }

    /** Syncs soon (after a short wait, so a burst of edits makes one sync). No-op when sync is off or switched to manual. */
    fun nudge(scope: CoroutineScope, delayMs: Long = 15_000) {
        if (!_config.value.isSetUp || !_config.value.auto) return
        nudgeJob?.cancel()
        nudgeJob = scope.launch { delay(delayMs); syncNow() }
    }

    private fun buildDomains(cfg: SyncConfig): List<SyncDomain> = buildList {
        val now = { clock.now() }
        // The local profile, and every Google account this device has: each keeps its own subscriptions, groups, playlists and history.
        val ids = listOf(com.greenaltambe.vela.data.LOCAL_ACCOUNT) + (accounts?.all?.value.orEmpty().filter { it.kind == com.greenaltambe.vela.auth.AccountKind.GOOGLE }.map { it.id })
        for (id in ids) {
            if (SyncKind.SUBSCRIPTIONS in cfg.kinds) { add(SubscriptionsDomain(db, now, id)); add(GroupsDomain(id) { SubscriptionGroups(store, id) }) }
            if (SyncKind.PLAYLISTS in cfg.kinds) add(PlaylistsDomain(db, now, id))
            if (SyncKind.HISTORY in cfg.kinds) add(HistoryDomain(db, now, id))
        }
        if (SyncKind.BLOCKLIST in cfg.kinds) add(BlocklistDomain(blocklist))
        if (SyncKind.SETTINGS in cfg.kinds) add(SettingsDomain(db))
        if (SyncKind.ACCOUNTS in cfg.kinds && accounts != null) add(AccountsDomain(accounts, secrets))
    }

    /** A cheap summary of everything that would be synced, to notice that something changed without syncing. Order independent. */
    private fun localFingerprint(cfg: SyncConfig): Long {
        var sum = 0L
        var n = 0
        for (d in buildDomains(cfg)) for ((k, v) in d.read()) { sum += k.hashCode() * 31L + v.hashCode(); n++ }
        return sum * 31 + n
    }

    @Volatile private var lastFingerprint: Long? = null
    @Volatile private var lastChangeSyncAt = 0L

    /**
     * Called every few seconds while the app is open: when the synced data differs from after the last sync (you subscribed, finished a video, edited a playlist,
     * changed a setting), syncs shortly after. At most once every 20 seconds, so a video that keeps updating its position does not keep syncing.
     */
    suspend fun checkForChanges() {
        val cfg = _config.value
        if (!cfg.isSetUp || !cfg.auto || _status.value == SyncStatus.Syncing) return
        val fp = withContext(work) { runCatching { localFingerprint(cfg) }.getOrNull() } ?: return
        val last = lastFingerprint
        if (last == null) { lastFingerprint = fp; return }
        if (fp != last && clock.now().toEpochMilliseconds() - lastChangeSyncAt > 20_000) {
            lastChangeSyncAt = clock.now().toEpochMilliseconds()
            delay(1_500)
            syncNow()
        }
    }

    /** Syncs shortly after start and then every [everyMs] while the app runs. */
    fun startAuto(scope: CoroutineScope, everyMs: Long = 10 * 60_000L, checkEveryMs: Long = 15_000L) {
        scope.launch {
            ensureTransport()
            delay(5_000)
            var lastFull = 0L
            while (isActive) {
                val now = clock.now().toEpochMilliseconds()
                if (_config.value.isSetUp && _config.value.auto) {
                    if (now - lastFull >= everyMs) { lastFull = now; syncNow() } else runCatching { checkForChanges() }
                }
                delay(checkEveryMs)
            }
        }
    }

    private companion object {
        const val MODE = "sync.mode"; const val FOLDER = "sync.folder"; const val CODE = "sync.code"; const val AUTO = "sync.auto"; const val KIND = "sync.kind."
        const val NAME = "sync.deviceName"; const val STATE = "sync.state"; const val LAST = "sync.lastAt"; const val DEVICE_ID = "sync.deviceId"
    }
}
