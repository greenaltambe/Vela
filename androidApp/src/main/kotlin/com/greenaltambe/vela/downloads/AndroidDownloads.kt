package com.greenaltambe.vela.downloads

import android.content.Context
import com.greenaltambe.vela.core.network.NetworkMonitor
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.db.VelaDatabase
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.modules.Modules
import com.greenaltambe.vela.net.OkHttpRangeFetcher
import com.greenaltambe.vela.player.StreamLeases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The Android side of downloads: the manager, with storage in the app's folder or a SAF tree, the MediaMuxer merger,
 * notifications, and the foreground service started whenever the queue becomes non-empty (the service ends itself when
 * it empties again).
 */
fun createAndroidDownloads(
    context: Context,
    database: VelaDatabase,
    extractor: Extractor,
    modules: Modules,
    settings: SettingsResolver,
    network: NetworkMonitor,
    /** The active account's id; downloads are listed and scheduled per account. */
    accountId: () -> String = { com.greenaltambe.vela.data.LOCAL_ACCOUNT },
): Downloads {
    val app = context.applicationContext
    val repo = DownloadRepository(database, accountId)
    val provider = AndroidStorageProvider(app, settings)
    val fetcher = OkHttpRangeFetcher()
    val merger = AndroidMediaMerger()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val manager = DownloadManager(
        repo, StreamLeases(extractor), provider::get, fetcher, merger,
        OfflineCollector(extractor, modules, fetcher, provider::get, settings),
        settings, network, scope, AndroidDownloadNotifier(app),
    )
    manager.start()
    scope.launch {
        manager.activeCount.collectLatest { active ->
            if (active > 0) runCatching { VelaDownloadService.start(app) }
        }
    }
    return Downloads(manager, repo, provider::get, merger, diagnostics = { listOf("Folder: ${provider.get().description}", "Service: foreground (dataSync) while downloads are queued") })
}
