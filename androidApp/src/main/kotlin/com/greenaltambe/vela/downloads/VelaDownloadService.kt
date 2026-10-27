package com.greenaltambe.vela.downloads

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.greenaltambe.vela.data.DownloadState
import com.greenaltambe.vela.playback.PlaybackRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps downloads alive when the app is closed. The [DownloadManager] itself lives in [PlaybackRuntime] (one per
 * process, shared with the UI); this service only holds the process in the foreground, with a progress notification,
 * for as long as something is queued or downloading, and stops itself when the queue is empty.
 *
 * A foreground service rather than WorkManager: downloads are long, user-started and need a live progress notification
 * with Pause / Cancel; WorkManager would run the same foreground service underneath (`setForeground`) while adding a
 * second scheduler, a second place that persists the queue, and no benefit for work the user just asked for. Type
 * `dataSync`; Android 15 caps such services at about six hours per day, at which point [onTimeout] pauses the queue
 * (it resumes when the app is next opened or the viewer taps Resume).
 */
class VelaDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager get() = PlaybackRuntime.get(this).downloads.manager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
        startInForeground(emptyList())
        scope.launch {
            manager.items.collectLatest { items ->
                if (items.none { it.isActive }) { stopSelf(); return@collectLatest }
                getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_PROGRESS, progressNotification(this@VelaDownloadService, items))
            }
        }
    }

    private fun startInForeground(items: List<DownloadItem>) {
        val notification = progressNotification(this, items)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_PROGRESS, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> manager.pauseAll()
            ACTION_CANCEL_ALL -> manager.items.value.filter { it.state != DownloadState.COMPLETED }.forEach { manager.remove(it.id) }
        }
        // Restarted by the system after the process died: the runtime is created on first use above and recovers the queue.
        return START_STICKY
    }

    /** Android 15: the dataSync allowance ran out. Pause cleanly instead of being killed mid-chunk. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        manager.pauseAll()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PAUSE_ALL = "com.greenaltambe.vela.downloads.PAUSE_ALL"
        const val ACTION_CANCEL_ALL = "com.greenaltambe.vela.downloads.CANCEL_ALL"

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, VelaDownloadService::class.java))
    }
}
