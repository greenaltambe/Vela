package com.greenaltambe.vela

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.greenaltambe.vela.auth.desktopSecretStore
import com.greenaltambe.vela.auth.accountsAvailable
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

fun main(args: Array<String>) {
    // `--smoke-test`: start the real runtime without a window, report what was found, exit 0 (see SmokeTest).
    if ("--smoke-test" in args) kotlin.system.exitProcess(SmokeTest.run())
    // `vela <youtube link>`: opens that link; if Vela is already running the link goes to it and this process ends (see SingleInstance).
    val links = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    val startLink = args.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
    if (!SingleInstance.claim(XdgPaths.dataDir(), startLink) { links.trySend(it) }) return
    startLink?.let { links.trySend(it) }
    // The window class on X11 / XWayland is "Vela" (the menu entry's StartupWMClass), not the Java main class; best effort, needs --add-opens (set in the build).
    if (System.getProperty("os.name").orEmpty().startsWith("Linux")) runCatching {
        // The toolkit instance must exist first: setting the field on a class that is only loaded is overwritten (measured: WM_CLASS stayed the main class, so the dock showed the generic Java icon).
        val toolkit = java.awt.Toolkit.getDefaultToolkit().javaClass
        toolkit.getDeclaredField("awtAppClassName").apply { isAccessible = true }.set(null, "Vela")
    }
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
    val secretStore = desktopSecretStore(XdgPaths.dataDir())
    val wiring = buildDesktopWiring(database, settings, secretStore, SystemProcessRunner(), log, logSink = logFile, remote = remote, managedDir = java.io.File(XdgPaths.dataDir(), "bin"), faults = faults)
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
        com.greenaltambe.vela.core.network.FixedNetworkMonitor(), settings, notifier = com.greenaltambe.vela.data.RecordingDownloadNotifier(com.greenaltambe.vela.downloads.NotifySendNotifier(), { com.greenaltambe.vela.data.NotificationRepository(database, wiring.accounts.active.value.id) }, { settings.resolve(com.greenaltambe.vela.core.settings.Settings.notifyDownloads) }),
    )
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
        kotlinx.coroutines.delay(120_000)
        while (true) { runCatching { autoDownloader.run() }; kotlinx.coroutines.delay(30 * 60_000L) }
    }

    // SIGTERM / logout: Java leaves child processes running, so yt-dlp and ffmpeg are stopped here.
    Runtime.getRuntime().addShutdownHook(Thread({ com.greenaltambe.vela.platform.ChildProcesses.destroyAll() }, "vela-children"))

    // Tells the signed-in account what is watched (Settings > Privacy, off by default); each tab's player feeds it through an ActivityAddon.
    val activity = com.greenaltambe.vela.innertube.InnertubeAccountActivity(
        accounts, governors,
        enabled = { settings.resolve(com.greenaltambe.vela.core.settings.Settings.accountReportActivity) },
        note = { line -> log.record(com.greenaltambe.vela.extractor.chain.TierAttempt("activity report", com.greenaltambe.vela.extractor.chain.TierId.AUTHENTICATED, com.greenaltambe.vela.extractor.chain.AttemptOutcome.NOTE, null, line, kotlin.time.Duration.ZERO, kotlin.time.Clock.System.now())) },
        profiles = profileStore,
    )
    application {
        // Every tab has its own player (a libmpv instance made the first time the tab plays something; see TabPlayer). Without libmpv the app still runs
        // and a banner says so. The library is probed once here for that banner; tabs then create their own instances.
        val libmpv = remember { com.greenaltambe.vela.player.mpv.MpvLibrary.load().also { r -> r.onFailure { System.err.println("Vela: no video playback: ${it.message}") } } }
        var tabsMade = 0
        val playerFor: (TabModel) -> com.greenaltambe.vela.ui.nav.TabPlayer? = {
            if (libmpv.isFailure) null else {
                val sponsorBlock = modules.newSponsorBlock()
                val first = tabsMade++ == 0 // only the first tab keeps the "restore the last video" snapshot
                com.greenaltambe.vela.ui.nav.TabPlayer(
                    create = { register ->
                        DesktopPlaybackRuntime.create(chain, settings, SqlDelightWatchHistoryRepository(database), listOf(sponsorBlock, com.greenaltambe.vela.player.ActivityAddon(activity)), accountId = { accounts.active.value.id })
                            .onFailure { System.err.println("Vela: no video playback: ${it.message}") }
                            .getOrNull()?.let { rt -> register(rt); DesktopPlayerHost(rt) }
                    },
                    sponsorBlock = sponsorBlock, persistsNowPlaying = first,
                )
            }
        }

        // The window opens where and as large as it was left (maximized too); the first start, or a screen that is gone, uses the default size.
        val geometry = remember { SavedGeometry.load(SqlDelightSettingsStore(database)) ?: SavedGeometry.Default }
        val workspace = remember { Workspace(geometry.size(), geometry.position(), geometry.placement(), playerFor) }
        // mpv is in-process: quitting must stop every tab's player and release the audio device before the JVM exits. The hook covers exits that bypass the window (SIGTERM, System.exit); release() is idempotent.
        remember(workspace) { Runtime.getRuntime().addShutdownHook(Thread({ runCatching { workspace.releaseAllPlayers() } }, "vela-mpv-shutdown")) }
        val geometryStore = remember { SqlDelightSettingsStore(database) }
        // "System" theme: Compose Desktop reports light on Linux whatever the desktop says, so the desktop is asked itself (and again every few seconds).
        var systemDark by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(Unit) {
            if (!SystemTheme.supported()) return@LaunchedEffect
            while (true) {
                val now = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SystemTheme.isDark() }
                if (now != null && now != systemDark) systemDark = now
                kotlinx.coroutines.delay(3000)
            }
        }
        val services = remember {
            AppServices(chain, settings, log, database, null, utcOffsetMinutes = { TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000 }, modules = modules, downloads = downloads, accounts = accounts, login = if (accountsAvailable(secretStore)) InnertubeAccountLogin(accounts, governors) else null, sync = InnertubeAccountSync(accounts, governors, database, profiles = profileStore), actions = com.greenaltambe.vela.innertube.InnertubeAccountActions(accounts, governors, profiles = profileStore), reads = com.greenaltambe.vela.innertube.InnertubeAccountReads(accounts, governors, profiles = profileStore), profiles = com.greenaltambe.vela.innertube.InnertubeAccountProfiles(accounts, governors, profileStore), audit = audit, governors = governors, health = health, releases = releases, managedYtDlp = wiring.ytDlpUpdater, faults = faults,
                backupFiles = AwtBackupFiles(), syncFolders = AwtSyncFolders(), backupStorage = com.greenaltambe.vela.backup.fileBackupStorage(java.io.File(XdgPaths.dataDir(), "backups").path, "This app's folder"), backupFolders = AwtBackupFolders(), webDavFactory = { u, n, pw -> com.greenaltambe.vela.syncwebdav.WebDavSyncBackend(u, n, pw) }, lanTransport = com.greenaltambe.vela.synclan.LanSyncTransport(java.io.File(com.greenaltambe.vela.data.XdgPaths.dataDir(), "sync-lan")), deviceName = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Linux desktop", liveChat = com.greenaltambe.vela.innertube.LiveChatClient(), autoDownloader = autoDownloader,
                exportLog = { val dir = java.io.File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory } ?: java.io.File(System.getProperty("user.home")); val out = java.io.File(dir, "vela-log-${System.currentTimeMillis() / 1000}.txt"); out.writeText(logFile.readAll()); out.path })
        }

        androidx.compose.runtime.LaunchedEffect(services) { services.syncService.startAuto(this); services.autoBackups.startAuto(this) }

        // Reading the account's watch history by the `history.accountSync` setting (ON_START at launch, EVERY_6H on this timer); nothing runs when it is Off or Manual.
        androidx.compose.runtime.LaunchedEffect(services) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { services.accountHistory.syncIfDue(com.greenaltambe.vela.auth.HistorySyncTrigger.START) }
                while (true) {
                    kotlinx.coroutines.delay(30 * 60_000L)
                    runCatching { services.accountHistory.syncIfDue(com.greenaltambe.vela.auth.HistorySyncTrigger.PERIODIC) }
                }
            }
        }

        androidx.compose.runtime.LaunchedEffect(Unit) {
            for (link in links) {
                // A link handed over by another launch of the app opens in a tab of its own, in the window used last.
                if (link.isNotBlank()) workspace.focused?.let { w -> workspace.newTab(w).navigator.openYoutubeLink(link) }
                workspace.focused?.awt?.let { f -> (f as? java.awt.Frame)?.let { fr -> fr.extendedState = fr.extendedState and java.awt.Frame.ICONIFIED.inv() }; f.toFront() }
            }
        }
        // Media keys and the desktop's media widgets (GNOME/KDE) through MPRIS on the session bus, following the player of the tab in front; absent without a bus.
        val bridgeScope = rememberCoroutineScope()
        val bridge = remember(workspace) { TabPlayerBridge(workspace, bridgeScope) }
        androidx.compose.runtime.DisposableEffect(bridge) {
            // MPRIS is the Linux D-Bus media interface; other systems run without it.
            val mpris = if (libmpv.isSuccess && !com.greenaltambe.vela.data.isWindows) {
                fun session() = bridge.frontHost()?.session
                com.greenaltambe.vela.mpris.MprisService.start(bridge.frontState, bridge.frontMedia, com.greenaltambe.vela.mpris.MprisCommands(
                    play = { session()?.play() }, pause = { session()?.pause() },
                    toggle = { if (bridge.frontState.value.status == com.greenaltambe.vela.player.PlaybackStatus.PLAYING) session()?.pause() else session()?.play() },
                    stop = { session()?.stop(); workspace.activeNavigator()?.clearNowPlaying() }, seekTo = { session()?.seekTo(it) },
                    next = { workspace.activeNavigator()?.playNext() }, previous = { session()?.seekTo(0) },
                    raise = { workspace.focused?.awt?.toFront() }, quit = { exitApplication() },
                ))
            } else null
            // Keeps the screen awake while a video plays in any tab (freedesktop ScreenSaver on the session bus; Linux only).
            val inhibitor = if (libmpv.isSuccess && !com.greenaltambe.vela.data.isWindows) com.greenaltambe.vela.mpris.ScreenSaverInhibitor.start(bridge.anyState, bridge.anySession) else null
            onDispose { mpris?.close(); inhibitor?.close() }
        }

        val playerNote: @Composable () -> Unit = { if (libmpv.isFailure) PlayerMissingBanner(libmpv.exceptionOrNull()?.message) }
        run {
            for (w in workspace.windows.toList()) androidx.compose.runtime.key(w.id) {
                DesktopWindow(
                    w, workspace, services, geometryStore, systemDark, playerNote, first = w.id == 1, icon = velaWindowIcon, iconImages = velaWindowImages, blankPointer = blankPointer,
                    onNoWindowsLeft = { workspace.releaseAllPlayers(); exitApplication() },
                )
            }
        }
    }
}

private val velaWindowImages: List<java.awt.Image> by lazy {
    val loader = Thread.currentThread().contextClassLoader
    listOf(256, 128, 64, 48).mapNotNull { size -> loader.getResourceAsStream("icons/vela-$size.png")?.use { javax.imageio.ImageIO.read(it) } }
}

/** A fully transparent pointer, for fullscreen video once the controls are hidden. */
private val blankPointer = androidx.compose.ui.input.pointer.PointerIcon(
    java.awt.Toolkit.getDefaultToolkit().createCustomCursor(java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB), java.awt.Point(0, 0), "vela-blank"),
)

/** The window / taskbar icon: the launcher art (docs/icon), a few sizes so the shell picks a sharp one. */
private val velaWindowIcon: androidx.compose.ui.graphics.painter.Painter by lazy {
    val loader = Thread.currentThread().contextClassLoader
    val image = listOf(256, 128, 64, 48).firstNotNullOfOrNull { loader.getResourceAsStream("icons/vela-$it.png") }?.use { javax.imageio.ImageIO.read(it) }
    androidx.compose.ui.graphics.painter.BitmapPainter(image!!.toComposeImageBitmap())
}
