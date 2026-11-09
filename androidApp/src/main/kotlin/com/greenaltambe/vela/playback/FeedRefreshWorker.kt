package com.greenaltambe.vela.playback

import android.content.Context
import androidx.work.*
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.data.FeedRepository
import com.greenaltambe.vela.data.NotificationRepository
import com.greenaltambe.vela.data.SubscriptionRepository
import com.greenaltambe.vela.feed.FeedService
import java.util.concurrent.TimeUnit

/**
 * Looks for new uploads of the subscribed channels every few hours, so the notification feed (and, for channels set to All, a system
 * notification) is up to date when the app is closed. Anonymous requests only; skipped when the setting is off.
 * (Compile-only: not run on a device by the agent.)
 */
class FeedRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val rt = PlaybackRuntime.get(applicationContext)
        if (rt.settings.resolve(Settings.notifyBackground)) {
            val id = rt.accounts.active.value.id
            val system = rt.settings.resolve(Settings.notifySystem)
            FeedService(
                rt.chain, SubscriptionRepository(rt.database, id), FeedRepository(rt.database, id),
                notifications = NotificationRepository(rt.database, id),
                onNewUploads = { if (system) UploadNotifier.show(applicationContext, it) },
                isBlocked = com.greenaltambe.vela.feed.Blocklist(com.greenaltambe.vela.data.SqlDelightSettingsStore(rt.database)).rules()::blocks,
            ).refresh()
        }
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { Result.retry() }

    companion object {
        private const val NAME = "vela-feed-refresh"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
