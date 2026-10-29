package com.greenaltambe.vela

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import com.greenaltambe.vela.ui.platform.LocalVolumeSlider
import com.greenaltambe.vela.ui.platform.LocalWindowControls
import com.greenaltambe.vela.ui.platform.WindowControls
import java.util.TimeZone
import androidx.compose.ui.window.application
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.SqlDelightSettingsStore
import com.greenaltambe.vela.data.SqlDelightWatchHistoryRepository
import com.greenaltambe.vela.data.XdgPaths
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.chain.FallbackChain
import com.greenaltambe.vela.auth.AccountManager
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
import com.greenaltambe.vela.auth.CredentialStore
import com.greenaltambe.vela.auth.linuxSecretStore
import com.greenaltambe.vela.extractor.chain.AccountOp
import com.greenaltambe.vela.extractor.chain.AttemptOutcome
import com.greenaltambe.vela.extractor.chain.ExtractorTier
import com.greenaltambe.vela.extractor.chain.TierAttempt
import com.greenaltambe.vela.extractor.chain.TierId
import com.greenaltambe.vela.extractor.ytdlp.AccountCookies
import com.greenaltambe.vela.extractor.ytdlp.AuthenticatedYtDlpExtractor
import com.greenaltambe.vela.innertube.AltClientExtractor
import com.greenaltambe.vela.innertube.InnertubeAccountLogin
import com.greenaltambe.vela.innertube.InnertubeAccountSync
import kotlin.time.Clock
import kotlin.time.Duration
import com.greenaltambe.vela.extractor.newpipe.NewPipeExtractor
import com.greenaltambe.vela.extractor.ytdlp.SystemProcessRunner
import com.greenaltambe.vela.extractor.ytdlp.createYtDlpTier
import com.greenaltambe.vela.downloads.createDesktopDownloads
import com.greenaltambe.vela.modules.createModules
import com.greenaltambe.vela.net.OkHttpTransport
import com.greenaltambe.vela.playback.DesktopPlaybackRuntime
import com.greenaltambe.vela.playback.DesktopPlayerHost
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    // Settings and history live in $XDG_DATA_HOME/vela/vela.db; if that cannot be opened, run with an in-memory
    // database rather than not at all.
    fun openDatabase(): com.greenaltambe.vela.data.db.VelaDatabase = createDatabase(JvmDatabaseDriverFactory(XdgPaths.databaseFile().path))
    val database = try {
        openDatabase()
    } catch (e: Exception) {
        System.err.println("Vela: cannot open the database (${e.message}); settings and history will not be saved")
        // Say it on screen too: a silent in-memory session would look like all history and downloads had vanished. The file on disk was left alone.
        var recovered: com.greenaltambe.vela.data.db.VelaDatabase? = null
        if (e is com.greenaltambe.vela.data.DatabaseStartupException) runCatching {
            val choice = javax.swing.JOptionPane.showConfirmDialog(null, "${e.message}\n\nTry to recover? The damaged file is kept next to the new one; accounts, downloads, playlists, history, subscriptions and settings are copied over table by table, caches are rebuilt.",
                "Vela: database problem", javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.ERROR_MESSAGE)
            if (choice == javax.swing.JOptionPane.YES_OPTION) {
                val r = com.greenaltambe.vela.data.DatabaseRecovery.salvage(XdgPaths.databaseFile().path)
                recovered = openDatabase()
                javax.swing.JOptionPane.showMessageDialog(null, r.describe(), "Vela: recovery", javax.swing.JOptionPane.INFORMATION_MESSAGE)
            }
        }
        recovered ?: createDatabase(JvmDatabaseDriverFactory(null))
    }
    val settings = SettingsResolver(SqlDelightSettingsStore(database))
    val logFile = com.greenaltambe.vela.net.FileLogSink(java.io.File(XdgPaths.dataDir(), "vela.log"))
    val log = DiagnosticsLog(sink = logFile)
    // The account manager is built a few lines below (inside the wiring); the modules only need its active id when a segment is submitted.
    val activeAccountId = java.util.concurrent.atomic.AtomicReference<() -> String>({ com.greenaltambe.vela.data.LOCAL_ACCOUNT })
    val modules = createModules(OkHttpTransport(), database, settings, accountId = { activeAccountId.get()() })
    // Credentials: libsecret when a keyring answers, else an encrypted file (Diagnostics shows which).
    // Simulated failures exist only when started with VELA_DEBUG=1 (e.g. `VELA_DEBUG=1 ./gradlew :desktopApp:run`).
    val faults = if (System.getenv("VELA_DEBUG") == "1") com.greenaltambe.vela.extractor.chain.FaultInjector() else null
    val remote = com.greenaltambe.vela.config.createRemoteConfig({ settings.resolve(Settings.remoteConfigUrl) }, SqlDelightSettingsStore(database))
    val wiring = buildDesktopWiring(database, settings, linuxSecretStore(XdgPaths.dataDir()), SystemProcessRunner(), log, logSink = logFile, remote = remote, managedDir = java.io.File(XdgPaths.dataDir(), "bin"), faults = faults)
    // Fetched in the background at start; failures only change the status line (and keep the last good config).
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        remote.refresh()
        log.record(TierAttempt("remote config", TierId.ALT_CLIENT, AttemptOutcome.NOTE, null, remote.status.value, Duration.ZERO, Clock.System.now()))
    }
    val chain = wiring.chain
    val accounts = wiring.accounts
    activeAccountId.set { accounts.active.value.id }
    val profileStore = com.greenaltambe.vela.auth.ProfileStore(SqlDelightSettingsStore(database))
    val audit = wiring.audit
    val governors = wiring.governors
    val health = wiring.health
    val downloadChain = wiring.downloadChain
    // Newer-release watches: asked at most once a day (ReleaseCheck), anonymous, through the shared API client.
    val releaseClient = com.greenaltambe.vela.core.http.ApiClient(OkHttpTransport(), com.greenaltambe.vela.core.http.InMemoryApiCache())
    val releaseStore = SqlDelightSettingsStore(database)
    val releases = listOfNotNull(
        com.greenaltambe.vela.health.ReleaseWatch(com.greenaltambe.vela.health.ReleaseCheck("NewPipeExtractor", com.greenaltambe.vela.health.NewPipeVersion.VERSION, "TeamNewPipe/NewPipeExtractor", releaseClient, releaseStore)),
        wiring.ytDlpVersion?.let { v -> com.greenaltambe.vela.health.ReleaseWatch(com.greenaltambe.vela.health.ReleaseCheck("yt-dlp", v, "yt-dlp/yt-dlp", releaseClient, releaseStore), wiring.ytDlpCopies) },
    )
    // Downloads run in the app process; whatever was mid-way when it last exited is picked up again here.
    val downloads = createDesktopDownloads(database, downloadChain, modules, settings, ffmpeg = wiring.ffmpeg.path ?: "ffmpeg", accountId = { wiring.accounts.active.value.id })

    // Automatic downloads: a timer while the app runs (first check two minutes after start, then every 30 minutes), always through the anonymous downloads chain.
    val autoDownloader = com.greenaltambe.vela.downloads.AutoDownloader(
        com.greenaltambe.vela.downloads.AutoDownloadStore(com.greenaltambe.vela.data.SqlDelightSettingsStore(database)), downloadChain, downloads.manager, downloads.repo,
        { com.greenaltambe.vela.data.PlaylistRepository(database, wiring.accounts.active.value.id) }, { com.greenaltambe.vela.data.HistoryLibrary(database, wiring.accounts.active.value.id) },
        com.greenaltambe.vela.core.network.FixedNetworkMonitor(), settings, notifier = com.greenaltambe.vela.downloads.NotifySendNotifier(),
    )
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
        kotlinx.coroutines.delay(120_000)
        while (true) { runCatching { autoDownloader.run() }; kotlinx.coroutines.delay(30 * 60_000L) }
    }

    // SIGTERM / logout: Java leaves child processes running, so yt-dlp and ffmpeg are stopped here.
    Runtime.getRuntime().addShutdownHook(Thread({ com.greenaltambe.vela.platform.ChildProcesses.destroyAll() }, "vela-children"))

    application {
        // Created inside the application so the Swing main dispatcher the session scope uses is up. Without libmpv
        // the app still runs and the debug screen says there is no player.
        val runtime = remember {
            DesktopPlaybackRuntime.create(chain, settings, SqlDelightWatchHistoryRepository(database), listOf(modules.sponsorBlock), accountId = { accounts.active.value.id })
                .onFailure { System.err.println("Vela: no video playback: ${it.message}") }
                .getOrNull()
        }
        val host = remember(runtime) { runtime?.let(::DesktopPlayerHost) }

        // mpv is in-process: closing must stop it and release the audio device before the JVM exits. The hook covers
        // exits that bypass the window (SIGTERM, System.exit); close() is idempotent.
        remember(runtime) {
            runtime?.let { Runtime.getRuntime().addShutdownHook(Thread({ it.controller.release() }, "vela-mpv-shutdown")) }
        }

        val windowState = rememberWindowState(size = DpSize(1100.dp, 800.dp))
        val windowControls = remember(windowState) {
            object : WindowControls {
                override val isFullscreen: Boolean get() = windowState.placement == WindowPlacement.Fullscreen
                override fun toggleFullscreen() {
                    windowState.placement = if (isFullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
                }
            }
        }
        val services = remember(host) {
            AppServices(chain, settings, log, database, host, utcOffsetMinutes = { TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000 }, modules = modules, downloads = downloads, accounts = accounts, login = InnertubeAccountLogin(accounts, governors), sync = InnertubeAccountSync(accounts, governors, database, profiles = profileStore), actions = com.greenaltambe.vela.innertube.InnertubeAccountActions(accounts, governors, profiles = profileStore), reads = com.greenaltambe.vela.innertube.InnertubeAccountReads(accounts, governors, profiles = profileStore), profiles = com.greenaltambe.vela.innertube.InnertubeAccountProfiles(accounts, governors, profileStore), audit = audit, governors = governors, health = health, releases = releases, managedYtDlp = wiring.ytDlpUpdater, faults = faults,
                backupFiles = AwtBackupFiles(), liveChat = com.greenaltambe.vela.innertube.LiveChatClient(), autoDownloader = autoDownloader,
                exportLog = { val dir = java.io.File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory } ?: java.io.File(System.getProperty("user.home")); val out = java.io.File(dir, "vela-log-${System.currentTimeMillis() / 1000}.txt"); out.writeText(logFile.readAll()); out.path })
        }

        val navigator = remember { com.greenaltambe.vela.ui.nav.Navigator() }
        // Media keys and the desktop's media widgets (GNOME/KDE) through MPRIS on the session bus; absent without a bus or without a player.
        androidx.compose.runtime.DisposableEffect(host) {
            val mpris = host?.let { h ->
                val sess = h.session
                com.greenaltambe.vela.mpris.MprisService.start(h.controllerState, sess.media, com.greenaltambe.vela.mpris.MprisCommands(
                    play = { sess.play() }, pause = { sess.pause() },
                    toggle = { if (h.controllerState.value.status == com.greenaltambe.vela.player.PlaybackStatus.PLAYING) sess.pause() else sess.play() },
                    stop = { sess.stop(); navigator.clearNowPlaying() }, seekTo = { sess.seekTo(it) },
                    next = { navigator.playNext() }, previous = { sess.seekTo(0) },
                    raise = { java.awt.Frame.getFrames().firstOrNull { it.isVisible }?.toFront() }, quit = { exitApplication() },
                ))
            }
            onDispose { mpris?.close() }
        }

        Window(
            onCloseRequest = {
                runtime?.close()
                exitApplication()
            },
            state = windowState,
            title = "Vela",
            icon = velaWindowIcon,
        ) {
            CompositionLocalProvider(LocalWindowControls provides windowControls, LocalVolumeSlider provides true) { App(services, navigator) }
        }
    }
}

/** The window / taskbar icon: the launcher art (docs/icon), a few sizes so the shell picks a sharp one. */
private val velaWindowIcon: androidx.compose.ui.graphics.painter.Painter by lazy {
    val loader = Thread.currentThread().contextClassLoader
    val image = listOf(256, 128, 64, 48).firstNotNullOfOrNull { loader.getResourceAsStream("icons/vela-$it.png") }?.use { javax.imageio.ImageIO.read(it) }
    androidx.compose.ui.graphics.painter.BitmapPainter(image!!.toComposeImageBitmap())
}
