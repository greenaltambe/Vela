package com.greenaltambe.vela

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.SqlDelightWatchHistoryRepository
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.chain.FallbackChain
import com.greenaltambe.vela.extractor.chain.stubTiers
import com.greenaltambe.vela.extractor.newpipe.NewPipeExtractor
import com.greenaltambe.vela.playback.DesktopPlayerHost
import com.greenaltambe.vela.modules.createModules
import com.greenaltambe.vela.net.OkHttpTransport
import com.greenaltambe.vela.playback.DesktopPlaybackRuntime
import com.greenaltambe.vela.player.SessionState
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import com.greenaltambe.vela.ui.platform.LocalWindowControls
import com.greenaltambe.vela.ui.platform.WindowControls
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.delay
import java.lang.management.ManagementFactory

/**
 * Not a test: a driver. Opens a real window (through XWayland on this machine; Compose Desktop uses AWT), plays a
 * video through the real PlayerScreen / PlaybackSession / DesktopPlayerHost, and prints once a second what reached
 * the screen. Mid-run it resizes the window, toggles fullscreen and pauses/resumes, then closes through the same
 * path as the window's close button.
 */
fun main(args: Array<String>) {
    val videoId = args.getOrElse(0) { "aqz-KE-bpKQ" }
    val seconds = args.getOrElse(1) { "45" }.toInt()
    val startQuality = args.getOrNull(2)?.toIntOrNull() // e.g. 720: play at that height from the start, to measure the render-size cap
    val settings = SettingsResolver(InMemorySettingsStore())
    val log = DiagnosticsLog()
    val chain = FallbackChain(stubTiers(anonymous = NewPipeExtractor()), accountMode = { settings.resolve(Settings.accountFallbackMode) }, log = log)
    val database = createDatabase(JvmDatabaseDriverFactory(null))
    val modules = createModules(OkHttpTransport(), database, settings)
    val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean

    application {
        val runtime = remember { DesktopPlaybackRuntime.create(chain, settings, SqlDelightWatchHistoryRepository(database), listOf(modules.sponsorBlock)).getOrThrow() }
        val host = remember { DesktopPlayerHost(runtime) }
        val windowState = rememberWindowState(size = DpSize(1100.dp, 760.dp))
        val controller = runtime.controller
        val services = remember { AppServices(chain, settings, log, database, host, modules = modules) }
        val navigator = remember { Navigator().also { it.push(Route.Video(VideoSummary(videoId, "live window test", "UC", "channel"))) } }
        val controls = remember(windowState) {
            object : WindowControls {
                override val isFullscreen get() = windowState.placement == WindowPlacement.Fullscreen
                override fun toggleFullscreen() { windowState.placement = if (isFullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen }
            }
        }

        LaunchedEffect(Unit) {
            val t0 = System.nanoTime()
            var cpu0 = os.processCpuTime; var rendered0 = 0L; var drawn0 = 0L; var tPrev = t0
            var second = 0
            var playingSince = -1
            while (second < seconds) {
                delay(1000); second++
                val now = System.nanoTime()
                val rendered = controller.framesRendered; val drawn = host.framesDrawn
                val cpu = os.processCpuTime
                val dt = (now - tPrev) / 1e9
                val st = controller.state.value
                println("t=%2ds %-9s pos=%6d ms  mpv-rendered %5.1f fps  window-drawn %5.1f fps  CPU %4.0f%%  dropped=%d  size=%s  session=%s".format(
                    second, st.status, st.positionMs, (rendered - rendered0) / dt, (drawn - drawn0) / dt, (cpu - cpu0) / (dt * 1e9) * 100,
                    controller.stats.value.droppedFrames, windowState.size, host.session.state.value::class.simpleName))
                rendered0 = rendered; drawn0 = drawn; cpu0 = cpu; tPrev = now
                if (st.status == com.greenaltambe.vela.player.PlaybackStatus.PLAYING && playingSince < 0) {
                    playingSince = second
                    if (startQuality != null) { println(">> quality ${startQuality}p from the start"); host.session.selectQuality(startQuality) }
                }
                if (playingSince > 0) when (second - playingSince) {
                    8 -> { println(">> resize to 700x500"); windowState.size = DpSize(700.dp, 500.dp) }
                    14 -> { println(">> fullscreen"); controls.toggleFullscreen() }
                    22 -> { println(">> back to floating"); controls.toggleFullscreen() }
                    26 -> { println(">> pause"); host.session.pause() }
                    29 -> { println(">> resume"); host.session.play() }
                    31 -> { println(">> seek to 120 s"); host.session.seekTo(120_000) }
                    34 -> if (startQuality == null) { println(">> quality 720p"); host.session.selectQuality(720) }
                    38 -> { println(">> speed 1.5x"); host.session.setSpeed(1.5f) }
                }
            }
            println(">> closing through onCloseRequest path")
            runtime.close()
            exitApplication()
        }

        Window(state = windowState, title = "Vela live window", onCloseRequest = { runtime.close(); exitApplication() }) {
            CompositionLocalProvider(LocalWindowControls provides controls, com.greenaltambe.vela.ui.platform.LocalVolumeSlider provides true) { App(services, navigator) }
        }
    }
}
