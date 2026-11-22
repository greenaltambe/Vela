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

enum class SyncMode { LAN, FOLDER, WEBDAV }

/** How eagerly this device syncs by itself. [LIVE] is what most people want; the slower ones save battery and data. */
enum class SyncFrequency(val label: String, val detail: String) {
    LIVE("As it happens", "Recommended. Changes are sent within seconds and your other devices' changes are picked up within about a minute while Vela is open."),
    EVERY_15("Every 15 minutes", "Syncs on a timer. Less battery and data, but changes take up to 15 minutes to show."),
    HOURLY("Every hour", "Syncs about once an hour. Best for saving battery and data."),
    MANUAL("Only when I tap Sync now", "Nothing syncs by itself. Use the Sync now button."),
}

data class SyncConfig(
    val mode: SyncMode = SyncMode.LAN,
    val folder: String? = null,
    val webDavUrl: String? = null,
    val webDavUser: String = "",
    val hasWebDavPassword: Boolean = false,
    val hasCode: Boolean = false,
    val frequency: SyncFrequency = SyncFrequency.LIVE,
    /** Skip automatic syncs on mobile data (online storage only; the Sync now button always works). */
    val wifiOnly: Boolean = false,
    val kinds: Set<SyncKind> = SyncKind.entries.toSet(),
    val deviceName: String = "",
) { val auto get() = frequency != SyncFrequency.MANUAL
    val isSetUp get() = hasCode && when (mode) { SyncMode.LAN -> true; SyncMode.FOLDER -> folder != null; SyncMode.WEBDAV -> webDavUrl != null && hasWebDavPassword } }

sealed interface SyncStatus {
    data object NotSetUp : SyncStatus
    data class Idle(val lastSyncAt: Long?, val devices: List<SyncDevice>) : SyncStatus
    /** Nothing to do right now because the device is offline, or on mobile data with "Only on Wi-Fi" on. */
    data class Waiting(val lastSyncAt: Long?, val reason: String) : SyncStatus
    data object Syncing : SyncStatus
    /** [wrongCode] is true when other devices' files could not be opened with this code. */
    data class Failed(val message: String, val wrongCode: Boolean = false, val retryInSeconds: Int? = null) : SyncStatus
}

/** Owns Sync's settings, secret and schedule. Everything the viewer sets in the Sync screen goes through here. */
class SyncService(
    private val db: VelaDatabase,
    private val store: SettingsStore,
    private val secrets: CredentialStore,
    private val folders: SyncFolders?,
    private val lan: LanTransport? = null,
    private val webDav: WebDavFactory? = null,
    private val blocklist: Blocklist,
    private val accounts: com.greenaltambe.vela.auth.AccountManager? = null,
    private val deviceLabel: String,
    private val clock: Clock = Clock.System,
    /** Test seam: replaces the folder backend and codec. */
    private val backendOverride: ((String) -> SyncBackend)? = null,
    /** Where the merge runs. */
    private val work: kotlin.coroutines.CoroutineContext = Dispatchers.Default,
    /** The current connection type, when the platform can tell (for "Only on Wi-Fi" and for not failing while offline). */
    private val network: (() -> com.greenaltambe.vela.core.network.NetworkType)? = null,
    /** Makes a safety backup before sync changes local data; [reason] is `first` (the first sync on this device) or `large` (a sync about to change many items). Null skips backups. */
    private val safetyBackup: (suspend (reason: String) -> Unit)? = null,
) {
    /** One lock for the whole process: the app and a background job must never merge at the same time. */
    private val mutex get() = globalMutex
    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<SyncConfig> get() = _config
    private val _status = MutableStateFlow<SyncStatus>(initialStatus())
    val status: StateFlow<SyncStatus> get() = _status
    private var nudgeJob: Job? = null
    init { if (_config.value.isSetUp) ensureTransport() }

    val isAvailable: Boolean get() = folders != null || lan != null || backendOverride != null
    val lanAvailable: Boolean get() = lan != null
    val webDavAvailable: Boolean get() = webDav != null
    /** Devices on this Wi-Fi right now (empty unless the connection is "Same Wi-Fi"). */
    val nearby: kotlinx.coroutines.flow.StateFlow<List<LanPeer>> get() = lan?.peers ?: MutableStateFlow(emptyList())
    fun folderLabel(): String? = _config.value.folder?.let { folders?.label(it) ?: it }
    /** The sync code, to show on this device so it can be typed into the next one. */
    fun code(): String? = secrets.secret(CODE)

    private fun get(key: String) = store.get(key, SettingLayer.Global)
    private fun put(key: String, v: String) = store.put(key, SettingLayer.Global, v)

    private fun loadConfig() = SyncConfig(
        mode = when { get(MODE) == "WEBDAV" -> SyncMode.WEBDAV; get(MODE) == "FOLDER" || (lan == null && get(MODE) != "LAN") -> SyncMode.FOLDER; else -> SyncMode.LAN },
        webDavUrl = get(WEBDAV_URL)?.ifBlank { null }, webDavUser = get(WEBDAV_USER).orEmpty(), hasWebDavPassword = secrets.secret(WEBDAV_PASSWORD) != null,
        folder = get(FOLDER)?.ifBlank { null },
        hasCode = secrets.secret(CODE) != null,
        frequency = SyncFrequency.entries.firstOrNull { it.name == get(FREQUENCY) } ?: if (get(AUTO) == "false") SyncFrequency.MANUAL else SyncFrequency.LIVE,
        wifiOnly = get(WIFI_ONLY) == "true",
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
    fun setWebDav(url: String, user: String, password: String) {
        put(WEBDAV_URL, url.trim()); put(WEBDAV_USER, user.trim()); secrets.putSecret(WEBDAV_PASSWORD, password); refresh()
    }

    /** Opens the folder with these details (creating it when missing). Null when it works, else a sentence saying what is wrong. */
    suspend fun testWebDav(url: String, user: String, password: String): String? = withContext(work) {
        val factory = webDav ?: return@withContext "WebDAV is not available on this device."
        try { factory.create(url, user, password).let { b -> b.write(".vela-test.vsync", ByteArray(1)); b.delete(".vela-test.vsync"); b.list() }; null }
        catch (e: IllegalArgumentException) { "That does not look like a web address. It should start with https://" }
        catch (e: Exception) { e.message ?: "Could not connect." }
    }

    fun setFolder(descriptor: String) { put(FOLDER, descriptor); refresh() }
    fun setCode(code: String) { secrets.putSecret(CODE, normalizeSyncCode(code)); refresh() }
    fun setAuto(on: Boolean) = setFrequency(if (on) SyncFrequency.LIVE else SyncFrequency.MANUAL)
    fun setFrequency(f: SyncFrequency) { put(FREQUENCY, f.name); put(AUTO, (f != SyncFrequency.MANUAL).toString()); refresh() }
    fun setWifiOnly(on: Boolean) { put(WIFI_ONLY, on.toString()); refresh() }
    val networkKnown: Boolean get() = network != null
    fun setKind(kind: SyncKind, on: Boolean) { put(KIND + kind.name, on.toString()); refresh() }
    fun setDeviceName(name: String) { put(NAME, name.trim().ifEmpty { deviceLabel }); refresh() }

    /** Stops syncing on this device. Data already on this device and the files in the folder stay as they are. */
    fun turnOff() {
        secrets.deleteSecret(CODE)
        secrets.deleteSecret(WEBDAV_PASSWORD); store.remove(WEBDAV_URL, SettingLayer.Global); store.remove(WEBDAV_USER, SettingLayer.Global)
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
        if (!_config.value.isSetUp || _config.value.frequency != SyncFrequency.LIVE) return
        nudgeJob?.cancel()
        nudgeJob = scope.launch { delay(delayMs); syncNow() }
    }

    /**
     * Call when the viewer deliberately clears a lot at once (Clear history, Clear recent searches): the next sync may then delete everything on the other
     * devices too, instead of being stopped by the safety rule against a sync that wipes most of what it knows (meant for a failed read). Valid for 15 minutes
     * or until the next successful sync, whichever is first.
     */
    fun markIntentionalDelete() { put(ALLOW_MASS_DELETE, clock.now().toEpochMilliseconds().toString()); if (_config.value.isSetUp) lastFingerprint = null }

    private fun massDeleteAllowed(): Boolean = (get(ALLOW_MASS_DELETE)?.toLongOrNull() ?: 0L).let { it > 0 && clock.now().toEpochMilliseconds() - it < 15 * 60_000L }

    /** For tests. */
    fun deviceIdForTest(): String = deviceId()

    private fun deviceId(): String = get(DEVICE_ID) ?: List(8) { "0123456789abcdef".random() }.joinToString("").also { put(DEVICE_ID, it) }

    /** Owned by the service, so a screen leaving (the setup screen turns into the status screen mid-sync) cannot cancel a sync halfway. */
    private val scope = CoroutineScope(SupervisorJob() + work)

    /** Runs one sync now. Safe to call from anywhere and often: overlapping calls wait for each other; the caller going away does not stop it. */
    suspend fun syncNow(manual: Boolean = false): SyncStatus = scope.async { runSync(manual) }.await()

    private var failures = 0
    private var retryJob: Job? = null
    private val retryDelays = intArrayOf(30, 60, 120, 300, 600)

    /**
     * Run by the Android background job (every ~30 minutes) when the app is not open. Only for online storage and shared folders (Wi-Fi sync needs both apps
     * open), only when sync is on and not manual, and only when this device's schedule says it is due.
     */
    suspend fun syncInBackground() {
        val c = _config.value
        if (!c.isSetUp || c.mode == SyncMode.LAN || c.frequency == SyncFrequency.MANUAL) return
        val every = when (c.frequency) { SyncFrequency.HOURLY -> 55 * 60_000L; SyncFrequency.EVERY_15 -> 14 * 60_000L; else -> 0L }
        val last = get(LAST)?.toLongOrNull() ?: 0L
        if (clock.now().toEpochMilliseconds() - last < every) return
        syncNow()
    }

    private suspend fun runSync(manual: Boolean): SyncStatus = mutex.withLock {
        val cfg = _config.value
        val code = secrets.secret(CODE)
        val folder = when (cfg.mode) { SyncMode.LAN -> "lan"; SyncMode.FOLDER -> cfg.folder; SyncMode.WEBDAV -> cfg.webDavUrl }
        if (folder == null || code == null || !cfg.isSetUp) return@withLock SyncStatus.NotSetUp.also { _status.value = it }
        if (!manual && cfg.mode != SyncMode.LAN) {
            val net = network?.invoke()
            val last = get(LAST)?.toLongOrNull()
            if (net == com.greenaltambe.vela.core.network.NetworkType.OFFLINE) return@withLock SyncStatus.Waiting(last, "No internet connection. Sync continues when you are back online.").also { _status.value = it }
            if (cfg.wifiOnly && net == com.greenaltambe.vela.core.network.NetworkType.METERED) return@withLock SyncStatus.Waiting(last, "Waiting for Wi-Fi (Only on Wi-Fi is on). Tap Sync now to sync on mobile data.").also { _status.value = it }
        }
        _status.value = SyncStatus.Syncing
        ensureTransport()
        // The very first sync on a device: keep a copy of what is here before anything merges into it.
        if (get(STATE) == null && safetyBackup != null) {
            try { safetyBackup.invoke("first") } catch (e: CancellationException) { throw e } catch (e: Exception) {
                return@withLock SyncStatus.Failed("Sync did not start: the safety backup of your data could not be made first (${e.message ?: e::class.simpleName}). Nothing was changed.").also { _status.value = it }
            }
        }
        val result = try {
            withContext(work) {
              suspend fun once(): SyncResult {
                val backend = backendFor(cfg, folder)
                val domains = buildDomains(cfg)
                return SyncEngine(deviceId(), domains, backend, passphraseCodec(code), object : SyncStateStore {
                    override fun load() = get(STATE)
                    override fun save(json: String) = put(STATE, json)
                }, { clock.now().toEpochMilliseconds() }, cfg.deviceName, massDeleteFloor = if (massDeleteAllowed()) Int.MAX_VALUE else 10, beforeApply = { n ->
                    // A merge that changes many items at once is the one that can hurt: keep a copy first (at most one every 6 hours).
                    val last = get(LAST_LARGE_BACKUP)?.toLongOrNull() ?: 0L
                    if (n >= LARGE_CHANGE && clock.now().toEpochMilliseconds() - last > 6 * 3600_000L && safetyBackup != null) {
                        safetyBackup.invoke("large"); put(LAST_LARGE_BACKUP, clock.now().toEpochMilliseconds().toString())
                    }
                }).sync()
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
            // A failed connection is retried by itself, after a growing pause (30 s, 1, 2, 5, then every 10 minutes), unless sync is manual.
            val wait = retryDelays[minOf(failures, retryDelays.size - 1)]
            failures++
            val auto = _config.value.frequency != SyncFrequency.MANUAL
            retryJob?.cancel()
            if (auto) retryJob = scope.launch { delay(wait * 1000L); syncNow() }
            return@withLock SyncStatus.Failed("Sync could not finish: ${e.message ?: e::class.simpleName}", retryInSeconds = if (auto) wait else null).also { _status.value = it }
        }
        failures = 0; retryJob?.cancel(); store.remove(ALLOW_MASS_DELETE, SettingLayer.Global)
        val at = clock.now().toEpochMilliseconds()
        put(LAST, at.toString())
        lastFingerprint = runCatching { withContext(work) { localFingerprint(cfg) } }.getOrNull()
        if (cfg.mode == SyncMode.WEBDAV) lastRemoteFingerprint = runCatching { withContext(work) { backendFor(cfg, folder).fingerprint() } }.getOrNull()
        val others = result.devices.filter { it.id != deviceId() }
        val st: SyncStatus = SyncStatus.Idle(at, others)
        _status.value = st
        st
    }

    /** Syncs soon (after a short wait, so a burst of edits makes one sync). No-op when sync is off or switched to manual. */
    fun nudge(scope: CoroutineScope, delayMs: Long = 15_000) {
        if (!_config.value.isSetUp || _config.value.frequency != SyncFrequency.LIVE) return
        nudgeJob?.cancel()
        nudgeJob = scope.launch { delay(delayMs); syncNow() }
    }

    private fun backendFor(cfg: SyncConfig, folder: String): SyncBackend = backendOverride?.invoke(folder) ?: when (cfg.mode) {
        SyncMode.LAN -> lan!!.backend()
        SyncMode.FOLDER -> folders!!.open(folder)
        SyncMode.WEBDAV -> webDav!!.create(folder, cfg.webDavUser, secrets.secret(WEBDAV_PASSWORD).orEmpty())
    }

    private fun buildDomains(cfg: SyncConfig): List<SyncDomain> = buildList {
        val now = { clock.now() }
        // The local profile, and every Google account this device has: each keeps its own subscriptions, groups, playlists and history.
        val ids = listOf(com.greenaltambe.vela.data.LOCAL_ACCOUNT) + (accounts?.all?.value.orEmpty().filter { it.kind == com.greenaltambe.vela.auth.AccountKind.GOOGLE }.map { it.id })
        for (id in ids) {
            if (SyncKind.SUBSCRIPTIONS in cfg.kinds) { add(SubscriptionsDomain(db, now, id)); add(GroupsDomain(id) { SubscriptionGroups(store, id) }) }
            if (SyncKind.PLAYLISTS in cfg.kinds) add(PlaylistsDomain(db, now, id))
            if (SyncKind.HISTORY in cfg.kinds) { add(HistoryDomain(db, now, id)); add(SearchHistoryDomain(db, now, id)) }
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
    @Volatile private var lastRemoteFingerprint: String? = null
    private var pollTick = 0
    @Volatile private var lastChangeSyncAt = 0L

    /**
     * Called every few seconds while the app is open: when the synced data differs from after the last sync (you subscribed, finished a video, edited a playlist,
     * changed a setting), syncs shortly after. At most once every 20 seconds, so a video that keeps updating its position does not keep syncing.
     */
    suspend fun checkForChanges() {
        val cfg = _config.value
        if (!cfg.isSetUp || cfg.frequency != SyncFrequency.LIVE || _status.value == SyncStatus.Syncing) return
        val fp = withContext(work) { runCatching { localFingerprint(cfg) }.getOrNull() } ?: return
        // Online storage cannot tell us when it changes, so look at it once a minute (a listing only, no downloads).
        if (cfg.mode == SyncMode.WEBDAV && ++pollTick % 4 == 0) {
            val remote = withContext(work) { runCatching { backendFor(cfg, cfg.webDavUrl.orEmpty()).fingerprint() }.getOrNull() }
            val seen = lastRemoteFingerprint
            if (remote != null && seen != null && remote != seen) { syncNow(); return }
            if (remote != null && seen == null) lastRemoteFingerprint = remote
        }
        val last = lastFingerprint
        if (last == null) { lastFingerprint = fp; return }
        if (fp != last && clock.now().toEpochMilliseconds() - lastChangeSyncAt > 20_000) {
            lastChangeSyncAt = clock.now().toEpochMilliseconds()
            delay(1_500)
            syncNow()
        }
    }

    /** Syncs shortly after start and then on the schedule the viewer chose, while the app runs. */
    fun startAuto(scope: CoroutineScope, everyMs: Long = 10 * 60_000L, checkEveryMs: Long = 15_000L) {
        scope.launch {
            ensureTransport()
            delay(5_000)
            var lastFull = 0L
            while (isActive) {
                val now = clock.now().toEpochMilliseconds()
                val c = _config.value
                if (c.isSetUp) {
                    val interval = when (c.frequency) { SyncFrequency.LIVE -> everyMs; SyncFrequency.EVERY_15 -> 15 * 60_000L; SyncFrequency.HOURLY -> 60 * 60_000L; SyncFrequency.MANUAL -> Long.MAX_VALUE }
                    if (c.frequency != SyncFrequency.MANUAL && now - lastFull >= interval) { lastFull = now; syncNow() }
                    else if (c.frequency == SyncFrequency.LIVE) runCatching { checkForChanges() }
                }
                delay(checkEveryMs)
            }
        }
    }

    private companion object {
        val globalMutex = Mutex()
        const val LARGE_CHANGE = 25
        const val ALLOW_MASS_DELETE = "sync.allowMassDelete"
        const val LAST_LARGE_BACKUP = "sync.lastLargeBackup"
        const val WEBDAV_URL = "sync.webdav.url"; const val WEBDAV_USER = "sync.webdav.user"; const val WEBDAV_PASSWORD = "sync.webdav.password"; const val MODE = "sync.mode"; const val FOLDER = "sync.folder"; const val CODE = "sync.code"; const val AUTO = "sync.auto"; const val FREQUENCY = "sync.frequency"; const val WIFI_ONLY = "sync.wifiOnly"; const val KIND = "sync.kind."
        const val NAME = "sync.deviceName"; const val STATE = "sync.state"; const val LAST = "sync.lastAt"; const val DEVICE_ID = "sync.deviceId"
    }
}
