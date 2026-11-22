package com.greenaltambe.vela.playback

import android.content.Context
import android.os.Build
import androidx.work.*
import com.greenaltambe.vela.data.SqlDelightSettingsStore
import com.greenaltambe.vela.feed.Blocklist
import com.greenaltambe.vela.sync.AndroidSyncFolders
import com.greenaltambe.vela.sync.SyncService
import com.greenaltambe.vela.syncwebdav.WebDavSyncBackend
import java.util.concurrent.TimeUnit

/**
 * Keeps Sync going while the app is closed: about every 30 minutes (Android's practical minimum is 15) it runs one sync, but only for online storage or a
 * shared folder (Wi-Fi sync needs both apps open), only when sync is on and not manual, and only when the viewer's schedule says one is due (the run
 * checks all of that and ends at once otherwise). (Compile-only: not run on a device by the agent.)
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val rt = PlaybackRuntime.get(applicationContext)
        val store = SqlDelightSettingsStore(rt.database)
        SyncService(
            rt.database, store, rt.accounts.credentials, AndroidSyncFolders(applicationContext) { null }, null,
            { u, n, p -> WebDavSyncBackend(u, n, p) }, Blocklist(store), rt.accounts, Build.MODEL ?: "Android", network = { rt.network.type.value },
        ).syncInBackground()
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { Result.retry() }

    companion object {
        private const val NAME = "vela-sync"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
