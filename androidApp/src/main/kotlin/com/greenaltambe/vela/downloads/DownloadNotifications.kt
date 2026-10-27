package com.greenaltambe.vela.downloads

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.greenaltambe.vela.MainActivity
import com.greenaltambe.vela.R

internal const val CHANNEL_PROGRESS = "downloads_progress"
internal const val CHANNEL_RESULT = "downloads_result"
internal const val NOTIFICATION_PROGRESS = 4201
private const val NOTIFICATION_RESULT_BASE = 4300

internal fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CHANNEL_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
    nm.createNotificationChannel(NotificationChannel(CHANNEL_RESULT, "Finished and failed downloads", NotificationManager.IMPORTANCE_DEFAULT))
}

private fun openApp(context: Context) = PendingIntent.getActivity(
    context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

private fun action(context: Context, name: String, code: Int) = PendingIntent.getService(
    context, code, Intent(context, VelaDownloadService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** The ongoing notification of the foreground service: what is downloading, a progress bar, Pause all and Cancel all. */
internal fun progressNotification(context: Context, items: List<DownloadItem>): Notification {
    val active = items.filter { it.isActive }
    val running = active.filter { it.state == com.greenaltambe.vela.data.DownloadState.DOWNLOADING }
    val done = active.sumOf { it.bytesDone }
    val total = active.takeIf { list -> list.all { it.bytesTotal != null } }?.sumOf { it.bytesTotal!! }
    val title = when {
        active.isEmpty() -> "Downloads"
        active.size == 1 -> active.first().title
        else -> "${active.size} downloads"
    }
    return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title)
        .setContentText(if (running.isEmpty()) "Waiting" else running.mapNotNull { it.speedBps }.sum().takeIf { it > 0 }?.let { "${it * 10 / (1024 * 1024) / 10.0} MB/s" } ?: "Downloading")
        .setProgress(100, if (total != null && total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0, total == null || total == 0L)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openApp(context))
        .addAction(0, "Pause all", action(context, VelaDownloadService.ACTION_PAUSE_ALL, 1))
        .addAction(0, "Cancel all", action(context, VelaDownloadService.ACTION_CANCEL_ALL, 2))
        .build()
}

/** Completed / failed notifications. Without the notification permission (denied on Android 13+) they are simply not shown. */
class AndroidDownloadNotifier(private val context: Context) : DownloadNotifier {
    private var next = 0

    private fun post(title: String, text: String, icon: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(icon).setContentTitle(title).setContentText(text)
            .setAutoCancel(true).setContentIntent(openApp(context)).build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_RESULT_BASE + (next++ % 50), n) }
    }

    override fun completed(title: String) = post("Download complete", title, android.R.drawable.stat_sys_download_done)
    override fun failed(title: String, reason: String) = post("Download failed: $title", reason, android.R.drawable.stat_notify_error)
}
