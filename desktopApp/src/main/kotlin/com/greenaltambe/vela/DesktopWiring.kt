package com.greenaltambe.vela

import com.greenaltambe.vela.auth.AccountManager
import com.greenaltambe.vela.auth.CredentialStore
import com.greenaltambe.vela.auth.DbAuditLog
import com.greenaltambe.vela.auth.DbBreakerStore
import com.greenaltambe.vela.auth.GovernorRegistry
import com.greenaltambe.vela.auth.SecretStore
import com.greenaltambe.vela.auth.governorLimits
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.db.VelaDatabase
import com.greenaltambe.vela.extractor.chain.AccountOp
import com.greenaltambe.vela.extractor.chain.AttemptOutcome
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.chain.ExtractorTier
import com.greenaltambe.vela.extractor.chain.FallbackChain
import com.greenaltambe.vela.extractor.chain.PacedExtractor
import com.greenaltambe.vela.extractor.chain.TierAttempt
import com.greenaltambe.vela.extractor.chain.TierId
import com.greenaltambe.vela.extractor.chain.TierOrder
import com.greenaltambe.vela.extractor.chain.downloadAccountMode
import com.greenaltambe.vela.extractor.chain.withFaults
import com.greenaltambe.vela.extractor.newpipe.NewPipeExtractor
import com.greenaltambe.vela.extractor.ytdlp.AccountCookies
import com.greenaltambe.vela.extractor.ytdlp.AuthenticatedYtDlpExtractor
import com.greenaltambe.vela.extractor.ytdlp.BinaryLocator
import com.greenaltambe.vela.extractor.ytdlp.FoundBinary
import com.greenaltambe.vela.extractor.ytdlp.ProcessRunner
import com.greenaltambe.vela.extractor.ytdlp.YTDLP_AGE_RESTRICTED_VERIFIED
import com.greenaltambe.vela.extractor.ytdlp.chooseYtDlp
import com.greenaltambe.vela.extractor.ytdlp.compareYtDlpVersions
import com.greenaltambe.vela.extractor.ytdlp.createYtDlpTier
import com.greenaltambe.vela.health.DbHealthStore
import com.greenaltambe.vela.health.ExtractorHealth
import com.greenaltambe.vela.innertube.AltClientExtractor
import com.greenaltambe.vela.innertube.OkHttpStreamProbe
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Duration

/** Everything the desktop app builds before it opens a window, so a test can build the same thing against a temp database. */
class DesktopWiring(
    val log: DiagnosticsLog,
    val accounts: AccountManager,
    val audit: DbAuditLog,
    val governors: GovernorRegistry,
    val tiers: List<ExtractorTier>,
    val chain: FallbackChain,
    val downloadChain: FallbackChain,
    val health: ExtractorHealth,
    val ytDlp: FoundBinary,
    val jsRuntime: FoundBinary,
    val ffmpeg: FoundBinary,
    /** Every yt-dlp found, with its version and the command that would update it; the app never changes them. */
    val ytDlpCopies: List<com.greenaltambe.vela.health.ToolCopy>,
    val ytDlpVersion: String?,
    val ytDlpUpdater: com.greenaltambe.vela.health.ManagedUpdater?,
)

/** The command that updates a yt-dlp installed at [path]. Reported, never run. */
fun ytDlpUpdateHint(path: String, home: String = System.getProperty("user.home").orEmpty()): String = when {
    path.startsWith("/usr/") || path.startsWith("/bin/") -> "sudo dnf upgrade yt-dlp (or your distribution's package manager)"
    path.startsWith("$home/.local/") -> "pipx upgrade yt-dlp (or pip install -U yt-dlp; yt-dlp -U for the standalone binary)"
    else -> "$path -U"
}

fun buildDesktopWiring(
    database: VelaDatabase,
    settings: SettingsResolver,
    secrets: SecretStore,
    runner: ProcessRunner,
    log: DiagnosticsLog = DiagnosticsLog(),
    logSink: com.greenaltambe.vela.extractor.chain.LogLineSink? = null,
    locator: BinaryLocator = BinaryLocator(),
    clock: Clock = Clock.System,
    /** Signed remote configuration (profiles, kill switches, canary videos); null = bundled data only. */
    remote: com.greenaltambe.vela.config.RemoteConfigManager? = null,
    /** Where the app keeps its own yt-dlp copy (`<data dir>/bin`); null = no managed copy. */
    managedDir: java.io.File? = null,
    /** Debug-only: wraps the anonymous tiers so Diagnostics can simulate 429 / captcha / offline / expired URLs. */
    faults: com.greenaltambe.vela.extractor.chain.FaultInjector? = null,
): DesktopWiring {
    fun note(subject: String, tier: TierId, detail: String, outcome: AttemptOutcome = AttemptOutcome.NOTE) =
        log.record(TierAttempt(subject, tier, outcome, null, detail, Duration.ZERO, clock.now()))

    // Programs are found by an explicit lookup (setting, PATH, common locations) because a launcher's PATH is short.
    val configured = settings.resolve(Settings.ytDlpPath).trim()
    val explicit = configured.contains('/') || configured != Settings.ytDlpPath.default
    // yt-dlp is often installed twice (distro package + pip) and the two differ by months; the newest one wins unless the user named one.
    val (chosen, probed) = if (explicit) {
        locator.locate("yt-dlp", configured).takeIf { it.found }?.let { runBlocking { chooseYtDlp(runner, listOf(it)) } } ?: (null to emptyList())
    } else runBlocking {
        val managed = managedDir?.let { java.io.File(it, "yt-dlp") }?.takeIf { it.isFile && it.canExecute() }?.let { FoundBinary("yt-dlp", it.path, "managed by Vela") }
        chooseYtDlp(runner, listOfNotNull(managed) + locator.locateAll("yt-dlp").filter { it.path != managed?.path })
    }
    val ytDlpFound = chosen?.binary ?: locator.locate("yt-dlp", configured).let { if (it.found) FoundBinary("yt-dlp", null, "${it.path} does not run") else it }
    probed.forEach { c -> note("binaries", TierId.YT_DLP, "yt-dlp candidate ${c.binary.path} (${c.binary.source}): ${c.version ?: "does not run"}${if (c === chosen) "  <- used" else ""}") }
    if (chosen != null) note("binaries", TierId.YT_DLP, "yt-dlp: ${chosen.binary.path} ${chosen.version}")
    else note("binaries", TierId.YT_DLP, ytDlpFound.description)
    val ffmpegFound = locator.locate("ffmpeg")
    val jsFound = listOf("deno", "node", "bun", "qjs").map { locator.locate(it) }.firstOrNull { it.found }
        ?: FoundBinary("JavaScript runtime", null, "none of deno, node, bun, qjs was found")
    note("binaries", TierId.YT_DLP, jsFound.description)
    note("binaries", TierId.YT_DLP, ffmpegFound.description)
    chosen?.version?.takeIf { compareYtDlpVersions(it, YTDLP_AGE_RESTRICTED_VERIFIED) < 0 }?.let {
        note("binaries", TierId.YT_DLP, "yt-dlp $it is older than $YTDLP_AGE_RESTRICTED_VERIFIED, the oldest build measured to play age-restricted videos without an account (2026.08.19 did not). Update it: pipx upgrade yt-dlp / yt-dlp -U")
    }

    // yt-dlp is probed once at startup (`yt-dlp --version`); if it isn't there the tier is simply not registered.
    var ytDlpArgs = emptyList<String>()
    val activeYtDlp = java.util.concurrent.atomic.AtomicReference(ytDlpFound.path)
    val ytDlpBinary = { activeYtDlp.get() ?: settings.resolve(Settings.ytDlpPath) }
    val ytDlp = if (ytDlpFound.found) runBlocking {
        createYtDlpTier(runner, binary = ytDlpBinary, log = log, path = locator, onReady = { ytDlpArgs = it })
    } else {
        note("yt-dlp setup", TierId.YT_DLP, "yt-dlp not installed (${ytDlpFound.source})", AttemptOutcome.SKIPPED)
        null
    }
    note("credential store", TierId.AUTHENTICATED, secrets.description)
    val accounts = AccountManager(database, CredentialStore(secrets))
    val audit = DbAuditLog(database, sink = logSink)
    val governors = GovernorRegistry(clock, { governorLimits(settings) }, DbBreakerStore(database), audit)
    val signedIn = { accounts.active.value.takeIf { it.isSignedIn } }
    val ytDlpLabel = chosen?.let { "yt-dlp ${it.version} (${it.binary.path}; client picked by yt-dlp itself, see yt-dlp -v)" }
    val accountTier = ytDlp?.let {
        AuthenticatedYtDlpExtractor(
            governors = governors,
            account = { signedIn()?.let { a -> accounts.cookies(a.id)?.let { jar -> AccountCookies(a.id, a.label, jar) } } },
            onCookiesUpdated = { id, jar -> accounts.saveCookies(id, jar) },
            runner = runner, binary = ytDlpBinary, extraArgs = { ytDlpArgs },
        )
    }
    // Chain order: ANONYMOUS (NewPipe), AUTHENTICATED (yt-dlp --cookies as the active account), ALT_CLIENT (iOS/Android player), YT_DLP.
    val tiers = (listOfNotNull(
        ExtractorTier(TierId.ANONYMOUS, PacedExtractor(NewPipeExtractor()), "NewPipeExtractor ${com.greenaltambe.vela.health.NewPipeVersion.VERSION}"),
        accountTier?.let { ExtractorTier(TierId.AUTHENTICATED, it, ytDlpLabel) },
        ExtractorTier(TierId.ALT_CLIENT, AltClientExtractor(profiles = { remote?.profiles(com.greenaltambe.vela.innertube.ClientProfile.load()) ?: com.greenaltambe.vela.innertube.ClientProfile.load() }, onProfile = { line -> note("alt client", TierId.ALT_CLIENT, line) }, contentChecks = { settings.resolve(Settings.allowControversial) }), "innertube-profiles.json"),
        ytDlp?.let { ExtractorTier(TierId.YT_DLP, PacedExtractor(it.extractor), ytDlpLabel) },
    )).withFaults(faults)
    val health = ExtractorHealth(tiers, OkHttpStreamProbe(), DbHealthStore(database), clock, onAlert = { com.greenaltambe.vela.downloads.NotifySendNotifier().healthAlert(it.message()) }, canaries = { ExtractorHealth.DefaultCanaries + remote?.canaryVideoIds().orEmpty().map { com.greenaltambe.vela.health.Canary(it, "remote") } })
    val hint = { id: TierId ->
        if (id != TierId.YT_DLP) null
        else if (!ytDlpFound.found) "yt-dlp is not installed (${ytDlpFound.source}); install it to play age-restricted videos without an account"
        else if (!jsFound.found) "No JavaScript runtime found: yt-dlp cannot solve YouTube's challenges without one (Fedora: sudo dnf install nodejs)"
        else chosen?.version?.takeIf { compareYtDlpVersions(it, YTDLP_AGE_RESTRICTED_VERIFIED) < 0 }
            ?.let { "yt-dlp $it is older than $YTDLP_AGE_RESTRICTED_VERIFIED, which is known to play these videos; update it (pipx upgrade yt-dlp)" }
    }
    val policy = { op: AccountOp -> when (op) {
        AccountOp.PLAYBACK -> true
        AccountOp.DETAILS -> settings.resolve(Settings.accountUseForDetails)
        AccountOp.CHANNELS -> settings.resolve(Settings.accountUseForChannels)
        AccountOp.NEVER -> false
    } }
    val paused = { signedIn()?.let { governors.forAccount(it.id).cooldownUntil() }?.let { "account cooling down until ${it.toString().substring(11, 16)} UTC" } }
    val order = { TierOrder.parse(settings.resolve(Settings.accountTierOrder)) }
    val chain = FallbackChain(tiers, accountMode = { settings.resolve(Settings.accountFallbackMode) }, log = log, clock = clock, allowAgeRestricted = { settings.resolve(Settings.allowAgeRestricted) }, signedInAccount = { signedIn()?.label },
        accountMayServe = policy, tierOrder = order, accountPaused = paused, tierHint = hint)
    // Downloads use the anonymous tiers; the account only when the user allowed it and the anonymous tiers failed.
    val downloadChain = FallbackChain(tiers, accountMode = { downloadAccountMode(settings.resolve(Settings.accountDownloadsMayUse)) }, log = log, clock = clock, allowAgeRestricted = { settings.resolve(Settings.allowAgeRestricted) },
        signedInAccount = { signedIn()?.label }, accountMayServe = policy, tierOrder = order, accountPaused = paused, tierHint = hint)
    val copies = probed.map { com.greenaltambe.vela.health.ToolCopy(it.binary.path.orEmpty(), it.version, it === chosen, ytDlpUpdateHint(it.binary.path.orEmpty())) }
    val updater = if (managedDir != null && ytDlp != null) {
        val core = com.greenaltambe.vela.extractor.ytdlp.YtDlpUpdater(managedDir, runner = runner, healthCheck = { bin ->
            // The same canary the Health screen uses for this tier: the new binary must still resolve a normal video's streams.
            val r = com.greenaltambe.vela.extractor.ytdlp.YtDlpExtractor(runner, { bin }, { ytDlpArgs }).streams("dQw4w9WgXcQ")
            r is com.greenaltambe.vela.extractor.ExtractorResult.Success && (r.value.videoStreams.isNotEmpty() || r.value.audioStreams.isNotEmpty())
        })
        object : com.greenaltambe.vela.health.ManagedUpdater {
            override val status = core.status
            override suspend fun install(tag: String): Boolean {
                val r = core.update(tag)
                if (r is com.greenaltambe.vela.extractor.ytdlp.UpdateResult.Updated) { activeYtDlp.set(core.managed.path); note("yt-dlp updater", TierId.YT_DLP, core.status.value) } else note("yt-dlp updater", TierId.YT_DLP, core.status.value, AttemptOutcome.FAILED)
                return r is com.greenaltambe.vela.extractor.ytdlp.UpdateResult.Updated
            }
        }
    } else null
    return DesktopWiring(log, accounts, audit, governors, tiers, chain, downloadChain, health, ytDlpFound, jsFound, ffmpegFound, copies, chosen?.version, updater)
}
