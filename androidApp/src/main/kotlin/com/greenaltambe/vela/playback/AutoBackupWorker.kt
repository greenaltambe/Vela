package com.greenaltambe.vela.playback

import android.content.Context
import androidx.work.*
import com.greenaltambe.vela.backup.AutoBackups
import com.greenaltambe.vela.backup.fileBackupStorage
import com.greenaltambe.vela.data.SqlDelightSettingsStore
import com.greenaltambe.vela.sync.AndroidBackupFolders
import java.io.File
import java.util.concurrent.TimeUnit

/** The daily automatic backup, also when the app has not been opened (the run itself checks that it is switched on and that a day has passed). (Compile-only: not run on a device by the agent.) */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val rt = PlaybackRuntime.get(applicationContext)
        AutoBackups(rt.database, SqlDelightSettingsStore(rt.database), fileBackupStorage(File(applicationContext.filesDir, "backups").path, "This app's folder"),
            AndroidBackupFolders(applicationContext) { null }, rt.accounts).runIfDue()
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { Result.retry() }

    companion object {
        private const val NAME = "vela-auto-backup"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(12, TimeUnit.HOURS).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
