package com.greenaltambe.vela

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
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
import com.greenaltambe.vela.extractor.chain.stubTiers
import com.greenaltambe.vela.extractor.newpipe.NewPipeExtractor
import com.greenaltambe.vela.extractor.ytdlp.SystemProcessRunner
import com.greenaltambe.vela.extractor.ytdlp.createYtDlpTier
import com.greenaltambe.vela.downloads.createDesktopDownloads
import com.greenaltambe.vela.modules.createModules
import com.greenaltambe.vela.net.OkHttpTransport
import com.greenaltambe.vela.playback.DesktopPlaybackRuntime
import com.greenaltambe.vela.playback.DesktopPlayerHost
import kotlinx.coroutines.runBlocking

fun main() {
    // Settings and history live in $XDG_DATA_HOME/vela/vela.db; if that cannot be opened, run with an in-memory
    // database rather than not at all.
    val database = try {
        createDatabase(JvmDatabaseDriverFactory(XdgPaths.databaseFile().path))
    } catch (e: Exception) {
        System.err.println("Vela: cannot open the database (${e.message}); settings and history will not be saved")
        createDatabase(JvmDatabaseDriverFactory(null))
    }
    val settings = SettingsResolver(SqlDelightSettingsStore(database))
    val log = DiagnosticsLog()
    val modules = createModules(OkHttpTransport(), database, settings)
    // yt-dlp is probed once at startup (`yt-dlp --version`); if it isn't there the tier is simply not registered.
    val ytDlp = runBlocking {
        createYtDlpTier(SystemProcessRunner(), binary = { settings.resolve(Settings.ytDlpPath) }, log = log)
    }
    // Chain order: ANONYMOUS (NewPipe), AUTHENTICATED (stub), ALT_CLIENT (stub), YT_DLP (real, or absent).
    val tiers = stubTiers(anonymous = NewPipeExtractor(), ytDlp = ytDlp?.extractor)
    val chain = FallbackChain(tiers, accountMode = { settings.resolve(Settings.accountFallbackMode) }, log = log)
    // Downloads run in the app process; whatever was mid-way when it last exited is picked up again here.
    val downloads = createDesktopDownloads(database, chain, modules, settings)

    application {
        // Created inside the application so the Swing main dispatcher the session scope uses is up. Without libmpv
        // the app still runs and the debug screen says there is no player.
        val runtime = remember {
            DesktopPlaybackRuntime.create(chain, settings, SqlDelightWatchHistoryRepository(database), listOf(modules.sponsorBlock))
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
            AppServices(chain, settings, log, database, host, utcOffsetMinutes = { TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000 }, modules = modules, downloads = downloads)
        }

        Window(
            onCloseRequest = {
                runtime?.close()
                exitApplication()
            },
            state = windowState,
            title = "Vela",
        ) {
            CompositionLocalProvider(LocalWindowControls provides windowControls) { App(services) }
        }
    }
}
