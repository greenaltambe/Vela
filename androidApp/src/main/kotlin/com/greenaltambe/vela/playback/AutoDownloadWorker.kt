package com.greenaltambe.vela.playback

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Checks the automatic-download rules about every 6 hours: needs a network and not a low battery. It only QUEUES downloads (the download
 * service does the transfer), and posts one summary notification when something was queued. (Compile-only: not run by the agent.)
 */
class AutoDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        PlaybackRuntime.get(applicationContext).autoDownloader.run(); Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { Result.retry() }

    companion object {
        private const val NAME = "vela-auto-download"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoDownloadWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
