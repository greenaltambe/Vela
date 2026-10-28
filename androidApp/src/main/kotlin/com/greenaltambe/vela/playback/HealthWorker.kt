package com.greenaltambe.vela.playback

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Runs the anonymous health canaries in the background roughly every 12 hours: only with a network, not on low battery. The result
 * is the same stored data the Health screen and the "Playback may be broken" banner read, so the banner is there when the app opens.
 * (Compile-only: not run on a device by the agent.)
 */
class HealthWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        PlaybackRuntime.get(applicationContext).health.run()
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { Result.retry() }

    companion object {
        private const val NAME = "vela-health-canary"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
