package com.greenaltambe.vela.playback

import android.content.Context
import androidx.work.*
import com.greenaltambe.vela.auth.LibrarySyncTrigger
import com.greenaltambe.vela.auth.PlaylistService
import java.util.concurrent.TimeUnit

/**
 * Sends the changes waiting in the playlist outbox (the hourly account-action limit may have refused them earlier) and reads synced playlists when
 * `library.accountSync` says it is due. The run checks the account and the setting itself, so a worker with nothing to do ends at once without a request.
 * (Compile-only: not run on a device by the agent.)
 */
class PlaylistSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val rt = PlaybackRuntime.get(applicationContext)
        PlaylistService(rt.database, rt.accounts, rt.actions, rt.reads, settings = rt.settings).runScheduled(LibrarySyncTrigger.PERIODIC)
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { Result.retry() }

    companion object {
        private const val NAME = "vela-playlist-sync"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PlaylistSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
