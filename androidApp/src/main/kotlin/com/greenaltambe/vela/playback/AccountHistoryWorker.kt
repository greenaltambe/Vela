package com.greenaltambe.vela.playback

import android.content.Context
import androidx.work.*
import com.greenaltambe.vela.auth.AccountHistoryService
import com.greenaltambe.vela.auth.HistorySyncTrigger
import java.util.concurrent.TimeUnit

/**
 * Reads the signed-in account's watch history into the app every six hours when `history.accountSync` is "Every 6 hours" (the run itself checks the
 * setting, the account and the time of the last read, so a worker that finds nothing due ends at once and makes no request). A read-only listing
 * through the account's governor as BROWSE. (Compile-only: not run on a device by the agent.)
 */
class AccountHistoryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val rt = PlaybackRuntime.get(applicationContext)
        AccountHistoryService(rt.reads, rt.database, rt.accounts, rt.settings).syncIfDue(HistorySyncTrigger.PERIODIC)
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { Result.retry() }

    companion object {
        private const val NAME = "vela-account-history"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AccountHistoryWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
