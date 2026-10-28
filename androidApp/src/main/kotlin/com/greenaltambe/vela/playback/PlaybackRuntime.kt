package com.greenaltambe.vela.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.AndroidDatabaseDriverFactory
import com.greenaltambe.vela.data.SqlDelightSettingsStore
import com.greenaltambe.vela.data.SqlDelightWatchHistoryRepository
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.chain.FallbackChain
import com.greenaltambe.vela.extractor.chain.withFaults
import com.greenaltambe.vela.auth.AccountManager
import com.greenaltambe.vela.auth.AndroidKeystoreSecretStore
import com.greenaltambe.vela.auth.CredentialStore
import com.greenaltambe.vela.auth.DbAuditLog
import com.greenaltambe.vela.auth.DbBreakerStore
import com.greenaltambe.vela.auth.GovernorRegistry
import com.greenaltambe.vela.auth.governorLimits
import com.greenaltambe.vela.extractor.chain.PacedExtractor
import com.greenaltambe.vela.extractor.chain.TierOrder
import com.greenaltambe.vela.health.DbHealthStore
import com.greenaltambe.vela.health.ExtractorHealth
import com.greenaltambe.vela.innertube.OkHttpStreamProbe
import com.greenaltambe.vela.extractor.chain.downloadAccountMode
import com.greenaltambe.vela.innertube.InnertubeAccountSync
import com.greenaltambe.vela.extractor.chain.AccountOp
import com.greenaltambe.vela.extractor.chain.AttemptOutcome
import com.greenaltambe.vela.extractor.chain.ExtractorTier
import com.greenaltambe.vela.extractor.chain.TierAttempt
import com.greenaltambe.vela.extractor.chain.TierId
import com.greenaltambe.vela.innertube.AltClientExtractor
import com.greenaltambe.vela.innertube.InnertubeAccountLogin
import kotlin.time.Clock
import kotlin.time.Duration
import com.greenaltambe.vela.extractor.newpipe.NewPipeExtractor
import com.greenaltambe.vela.downloads.createAndroidDownloads
import com.greenaltambe.vela.modules.createModules
import com.greenaltambe.vela.net.OkHttpTransport
import com.greenaltambe.vela.player.PlaybackSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Everything that has to outlive the activity because playback does: the player (which the playback service also
 * wraps in a media session), the extractor chain, and the [PlaybackSession] driving them. One per process; the
 * foreground service keeps the process, and so this, alive while audio plays in the background.
 */
class PlaybackRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext

    /** One database for settings and history; opened on first use. */
    val database by lazy { createDatabase(AndroidDatabaseDriverFactory(appContext)) }

    val settings = SettingsResolver(SqlDelightSettingsStore(database))
    val network = AndroidNetworkMonitor(appContext)
    /** Bounded rotating redacted log (tier attempts and governor decisions) in the app's own files folder. */
    val logFile = com.greenaltambe.vela.net.FileLogSink(java.io.File(appContext.filesDir, "vela.log"))
    val log = DiagnosticsLog(sink = logFile)
    val modules by lazy { createModules(OkHttpTransport(), database, settings) }
    /** Accounts and their credentials (Keystore-encrypted, excluded from backup). */
    val accounts by lazy { AccountManager(database, CredentialStore(AndroidKeystoreSecretStore(appContext))) }
    val audit by lazy { DbAuditLog(database, sink = logFile) }
    val governors by lazy { GovernorRegistry(Clock.System, { governorLimits(settings) }, DbBreakerStore(database), audit) }
    val login by lazy { InnertubeAccountLogin(accounts, governors) }
    val sync by lazy { InnertubeAccountSync(accounts, governors, database) }
    private fun pausedReason(): String? = accounts.active.value.takeIf { it.isSignedIn }
        ?.let { governors.forAccount(it.id).cooldownUntil() }?.let { "account cooling down until ${it.toString().substring(11, 16)} UTC" }

    /** Signed remote configuration (profiles, kill switches, canary videos). Android 13+ verifies Ed25519 natively; older devices keep the bundled data. */
    val remote by lazy { com.greenaltambe.vela.config.createRemoteConfig({ settings.resolve(Settings.remoteConfigUrl) }, SqlDelightSettingsStore(database)) }

    // No account tier on Android: yt-dlp does not run here, and neither NewPipe with account headers nor an own player call
    // with cookies gave playable streams (docs/reports/13-accounts.md). Anonymous -> alt client; the chain skips the account tier.
    /** Simulated failures (Diagnostics) exist only in debuggable builds. */
    val faults: com.greenaltambe.vela.extractor.chain.FaultInjector? =
        if (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) com.greenaltambe.vela.extractor.chain.FaultInjector() else null
    private val tiers by lazy {
        listOf(
            ExtractorTier(TierId.ANONYMOUS, PacedExtractor(NewPipeExtractor()), "NewPipeExtractor ${com.greenaltambe.vela.health.NewPipeVersion.VERSION}"),
            ExtractorTier(TierId.ALT_CLIENT, AltClientExtractor(profiles = { remote.profiles(com.greenaltambe.vela.innertube.ClientProfile.load()) }, onProfile = { line ->
                log.record(TierAttempt("alt client", TierId.ALT_CLIENT, AttemptOutcome.NOTE, null, line, Duration.ZERO, Clock.System.now()))
            }, contentChecks = { settings.resolve(Settings.allowControversial) }), "innertube-profiles.json"),
        ).withFaults(faults)
    }
    val health by lazy { ExtractorHealth(tiers, OkHttpStreamProbe(), DbHealthStore(database), onAlert = { HealthNotifier.show(appContext, it) }, canaries = { ExtractorHealth.DefaultCanaries + remote.canaryVideoIds().map { com.greenaltambe.vela.health.Canary(it, "remote") } }) }
    val chain by lazy {
        FallbackChain(tiers, accountMode = { settings.resolve(Settings.accountFallbackMode) }, log = log, allowAgeRestricted = { settings.resolve(Settings.allowAgeRestricted) },
            signedInAccount = { accounts.active.value.takeIf { it.isSignedIn }?.label },
            accountMayServe = { op -> when (op) {
                AccountOp.PLAYBACK -> true
                AccountOp.DETAILS -> settings.resolve(Settings.accountUseForDetails)
                AccountOp.CHANNELS -> settings.resolve(Settings.accountUseForChannels)
                AccountOp.NEVER -> false
            } },
            tierOrder = { TierOrder.parse(settings.resolve(Settings.accountTierOrder)) }, accountPaused = ::pausedReason)
    }
    /** Downloads use the anonymous tiers; the account only when the user allowed it and they failed. */
    val downloadChain by lazy {
        FallbackChain(tiers, accountMode = { downloadAccountMode(settings.resolve(Settings.accountDownloadsMayUse)) }, log = log, allowAgeRestricted = { settings.resolve(Settings.allowAgeRestricted) },
            signedInAccount = { accounts.active.value.takeIf { it.isSignedIn }?.label },
            accountMayServe = { op -> when (op) {
                AccountOp.PLAYBACK -> true
                AccountOp.DETAILS -> settings.resolve(Settings.accountUseForDetails)
                AccountOp.CHANNELS -> settings.resolve(Settings.accountUseForChannels)
                AccountOp.NEVER -> false
            } },
            tierOrder = { TierOrder.parse(settings.resolve(Settings.accountTierOrder)) }, accountPaused = ::pausedReason)
    }

    /** Downloads and the offline library; the queue is recovered when this is first used. */
    val downloads by lazy { createAndroidDownloads(appContext, database, downloadChain, modules, settings, network) }

    private val bandwidthMeter by lazy { DefaultBandwidthMeter.Builder(appContext).build() }

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(appContext)
            .setBandwidthMeter(bandwidthMeter)
            .setLoadControl(streamingLoadControl())
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    val controller by lazy { Media3PlayerController(player, bandwidthMeter, androidx.media3.datasource.DefaultDataSource.Factory(appContext)) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val flags = SqlDelightSettingsStore(database)
    private val wasPlayingKey = "battery.wasPlaying"
    private val explainedKey = "battery.explained"
    /** True when the previous process of this app died while something was playing (no clean pause/stop): the system, or a battery manager, killed it. Evaluated once, before this process records anything. */
    val previousProcessDiedWhilePlaying: Boolean = flags.get(wasPlayingKey, com.greenaltambe.vela.core.settings.SettingLayer.Global) == "1"
    val batteryHintAlreadyShown: Boolean get() = flags.get(explainedKey, com.greenaltambe.vela.core.settings.SettingLayer.Global) == "1"
    fun markBatteryHintShown() = flags.put(explainedKey, com.greenaltambe.vela.core.settings.SettingLayer.Global, "1")
    init {
        scope.launch {
            controller.state.map { it.status == com.greenaltambe.vela.player.PlaybackStatus.PLAYING }.distinctUntilChanged()
                .collect { flags.put(wasPlayingKey, com.greenaltambe.vela.core.settings.SettingLayer.Global, if (it) "1" else "0") }
        }
    }

    val session: PlaybackSession by lazy {
        PlaybackSession(
            chain, controller, settings, SqlDelightWatchHistoryRepository(database), scope,
            network = network, constraints = { deviceConstraints(appContext) }, addons = listOf(modules.sponsorBlock),
            accountId = { accounts.active.value.id },
        )
    }

    companion object {
        @Volatile private var instance: PlaybackRuntime? = null

        fun get(context: Context): PlaybackRuntime =
            instance ?: synchronized(this) { instance ?: PlaybackRuntime(context).also { instance = it } }
    }
}
