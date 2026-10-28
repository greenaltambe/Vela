package com.greenaltambe.vela.downloads

import com.greenaltambe.vela.core.network.FixedNetworkMonitor
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.XdgPaths
import com.greenaltambe.vela.data.db.VelaDatabase
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.modules.Modules
import com.greenaltambe.vela.net.FfmpegMediaMerger
import com.greenaltambe.vela.net.FileDownloadStorage
import com.greenaltambe.vela.net.OkHttpRangeFetcher
import com.greenaltambe.vela.player.StreamLeases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.concurrent.TimeUnit

/** The configured folder (Settings > Downloads), else `Videos/Vela`; one [FileDownloadStorage] per folder value. */
class DesktopStorageProvider(private val settings: SettingsResolver) {
    private var folder: String? = null
    private var current: FileDownloadStorage? = null

    @Synchronized
    fun get(): DownloadStorage {
        val configured = settings.resolve(Settings.downloadsFolder)
        if (current == null || folder != configured) {
            folder = configured
            current = FileDownloadStorage(if (configured.isNotBlank()) File(configured) else File(XdgPaths.videosDir(), "Vela"))
        }
        return current!!
    }
}

/** `notify-send` if the system has it. A missing tool only means no pop-ups; downloads work the same. */
class NotifySendNotifier : DownloadNotifier {
    val available: Boolean by lazy {
        runCatching {
            val p = ProcessBuilder("notify-send", "--version").redirectErrorStream(true).start()
            p.inputStream.readBytes()
            p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)
    }

    private fun send(summary: String, body: String) {
        if (!available) return
        Thread {
            runCatching { ProcessBuilder("notify-send", "-a", "Vela", "-i", "folder-download", summary, body).start().waitFor(5, TimeUnit.SECONDS) }
        }.apply { isDaemon = true; start() }
    }

    override fun completed(title: String) = send("Download complete", title)
    override fun failed(title: String, reason: String) = send("Download failed", "$title\n$reason")
}

fun createDesktopDownloads(
    database: VelaDatabase,
    extractor: Extractor,
    modules: Modules,
    settings: SettingsResolver,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ffmpeg: String = "ffmpeg",
): Downloads {
    val repo = DownloadRepository(database)
    val provider = DesktopStorageProvider(settings)
    val fetcher = OkHttpRangeFetcher()
    val merger = FfmpegMediaMerger(ffmpeg)
    val notifier = NotifySendNotifier()
    val collector = OfflineCollector(extractor, modules, fetcher, provider::get, settings)
    // The desktop does not track the connection type; it is treated as unmetered like the player's network monitor.
    val manager = DownloadManager(repo, StreamLeases(extractor), provider::get, fetcher, merger, collector, settings, FixedNetworkMonitor(), scope, notifier)
    manager.start()
    return Downloads(
        manager, repo, provider::get, merger,
        diagnostics = { listOf("Notifications: " + if (notifier.available) "notify-send found" else "notify-send not found (install libnotify to get pop-ups)", "Folder: ${provider.get().description}") },
    )
}
