package com.greenaltambe.vela

import com.greenaltambe.vela.auth.AccountLogin
import com.greenaltambe.vela.auth.AccountManager
import com.greenaltambe.vela.auth.DbAuditLog
import com.greenaltambe.vela.auth.DbBreakerStore
import com.greenaltambe.vela.auth.GovernorRegistry
import com.greenaltambe.vela.auth.governorLimits
import com.greenaltambe.vela.auth.CredentialStore
import com.greenaltambe.vela.auth.InMemorySecretStore
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.FeedRepository
import com.greenaltambe.vela.data.HistoryLibrary
import com.greenaltambe.vela.data.PlaylistRepository
import com.greenaltambe.vela.data.SearchHistoryRepository
import com.greenaltambe.vela.data.SubscriptionRepository
import com.greenaltambe.vela.data.db.VelaDatabase
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.feed.FeedService
import com.greenaltambe.vela.downloads.Downloads
import com.greenaltambe.vela.modules.Modules
import com.greenaltambe.vela.player.PlayerHost
import kotlin.time.Clock

/**
 * Everything the screens need, built once by the platform entry point. Screens take this rather than a long list of
 * parameters, so a new feature adds a field here instead of a parameter to every composable above it.
 */
class AppServices(
    extractor: Extractor,
    val settings: SettingsResolver,
    val log: DiagnosticsLog,
    database: VelaDatabase,
    /** The platform's player, or null when it has none (the video page then lists what would play). */
    val player: PlayerHost?,
    val clock: Clock = Clock.System,
    /** The viewer's offset from UTC in minutes, for grouping history by local day. */
    val utcOffsetMinutes: () -> Int = { 0 },
    /** SponsorBlock, Return YouTube Dislike and DeArrow. */
    val modules: Modules,
    /** Downloads and the offline library; null where the platform has none yet. */
    val downloads: Downloads? = null,
    /** The accounts and which is active; defaults to a local-only manager whose credentials live in memory. */
    val accounts: AccountManager = AccountManager(database, CredentialStore(InMemorySecretStore())),
    /** Validates and adds Google accounts; null where the platform has no login yet. */
    val login: AccountLogin? = null,
    /** Starts a web sign-in on platforms that have one (Android's WebView); null elsewhere. */
    val webLogin: (() -> Unit)? = null,
    /** Writes to the signed-in account (subscribe, like, Watch Later, playlists); null where there is no login. */
    val actions: com.greenaltambe.vela.auth.AccountActions? = null,
    /** Reads from the signed-in account (Recommended Home, playlists); null where there is no login. */
    val reads: com.greenaltambe.vela.auth.AccountReads? = null,
    /** Pulls account data (subscriptions) into the local tables; null where there is no login. */
    val sync: com.greenaltambe.vela.auth.AccountSync? = null,
    /** Every request that carries account credentials passes through these; the audit log is what Diagnostics shows. */
    /** Canary checks of the anonymous tiers; null where the platform has none. */
    val health: com.greenaltambe.vela.health.ExtractorHealth? = null,
    /** Newer-release watches (NewPipeExtractor, yt-dlp copies); shown on Health. */
    val releases: List<com.greenaltambe.vela.health.ReleaseWatch> = emptyList(),
    /** The app's own yt-dlp copy and its updater (Linux); null elsewhere. */
    val managedYtDlp: com.greenaltambe.vela.health.ManagedUpdater? = null,
    /** Debug-only switch for simulated failures (Diagnostics); null in normal builds. */
    val faults: com.greenaltambe.vela.extractor.chain.FaultInjector? = null,
    /** Writes the redacted log file somewhere the viewer can reach and returns where; null when the platform has no log file. */
    val exportLog: (() -> String)? = null,
    /** Runs the automatic downloads now (the scheduled runs use the same object); null where there are no downloads. */
    val autoDownloader: com.greenaltambe.vela.downloads.AutoDownloader? = null,
    /** Read-only live chat and chat replay (anonymous polling); null where unavailable. */
    val liveChat: com.greenaltambe.vela.extractor.LiveChatSource? = null,
    /** The pending captcha challenge (Android shows a banner and a WebView); null where captchas cannot be solved in the app. */
    val captcha: com.greenaltambe.vela.extractor.CaptchaCoordinator? = null,
    /** Opens the platform's captcha solver for a challenge page. */
    val solveCaptcha: ((String) -> Unit)? = null,
    /** Where backups are saved and read from (a file dialog on Linux, the document picker on Android); null hides the screen's buttons. */
    val backupFiles: com.greenaltambe.vela.backup.BackupFiles? = null,
    /** Empties the caches only (see CacheMaintenance) and returns what was cleared. */
    val rebuildCaches: (() -> String)? = { com.greenaltambe.vela.data.CacheMaintenance.rebuild(database) },
    val audit: DbAuditLog = DbAuditLog(database),
    val governors: GovernorRegistry = GovernorRegistry(clock, { governorLimits(settings) }, DbBreakerStore(database), audit),
) {
    /** Every screen's data source: bounded in time, so no screen can spin forever (a failure shows the message and Retry). */
    val extractor: Extractor = if (extractor is com.greenaltambe.vela.extractor.TimeoutExtractor || extractor is com.greenaltambe.vela.extractor.CaptchaAwareExtractor) extractor else com.greenaltambe.vela.extractor.TimeoutExtractor(extractor)
    /** The app database, for screens that need it directly (backup). */
    val database: com.greenaltambe.vela.data.db.VelaDatabase = database
    /** The automatic-download rules (stored as settings JSON). */
    val autoDownloadRules = com.greenaltambe.vela.downloads.AutoDownloadStore(com.greenaltambe.vela.data.SqlDelightSettingsStore(database))
    private val db = database
    private val feeds = HashMap<String, FeedService>()

    /** The repositories below follow the active account: each read builds one for the id that is active right now. */
    val activeId: String get() = accounts.active.value.id
    val subscriptions get() = SubscriptionRepository(db, activeId)
    val history get() = HistoryLibrary(db, activeId)
    val searchHistory get() = SearchHistoryRepository(db, activeId)
    val playlists get() = PlaylistRepository(db, activeId)

    /** One feed service per account, so each keeps its own entries and refresh state. */
    val feed: FeedService
        get() = activeId.let { id -> feeds.getOrPut(id) { FeedService(extractor, SubscriptionRepository(db, id), FeedRepository(db, id), clock) } }
}
