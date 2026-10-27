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
import com.greenaltambe.vela.auth.AccountManager
import com.greenaltambe.vela.auth.AndroidKeystoreSecretStore
import com.greenaltambe.vela.auth.CredentialStore
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
    val log = DiagnosticsLog()
    val modules by lazy { createModules(OkHttpTransport(), database, settings) }
    /** Accounts and their credentials (Keystore-encrypted, excluded from backup). */
    val accounts by lazy { AccountManager(database, CredentialStore(AndroidKeystoreSecretStore(appContext))) }
    val login by lazy { InnertubeAccountLogin(accounts) }

    // No account tier on Android: yt-dlp does not run here, and neither NewPipe with account headers nor an own player call
    // with cookies gave playable streams (docs/reports/13-accounts.md). Anonymous -> alt client; the chain skips the account tier.
    private val tiers by lazy {
        listOf(
            ExtractorTier(TierId.ANONYMOUS, NewPipeExtractor()),
            ExtractorTier(TierId.ALT_CLIENT, AltClientExtractor(onProfile = { line ->
                log.record(TierAttempt("alt client", TierId.ALT_CLIENT, AttemptOutcome.NOTE, null, line, Duration.ZERO, Clock.System.now()))
            })),
        )
    }
    val chain by lazy {
        FallbackChain(tiers, accountMode = { settings.resolve(Settings.accountFallbackMode) }, log = log,
            signedInAccount = { accounts.active.value.takeIf { it.isSignedIn }?.label })
    }

    /** Downloads and the offline library; the queue is recovered when this is first used. */
    val downloads by lazy { createAndroidDownloads(appContext, database, chain, modules, settings, network) }

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
