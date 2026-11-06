package com.greenaltambe.vela.playback

import android.Manifest
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
import com.greenaltambe.vela.extractor.VideoSummary

/** The "New videos" system notification, for channels whose bell is set to All. One notification summarising the batch, not one per video. */
object UploadNotifier {
    const val CHANNEL = "new_videos"
    private const val ID = 4200

    fun show(context: Context, videos: List<VideoSummary>) {
        if (videos.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "New videos", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val first = videos.first()
        val title = if (videos.size == 1) first.channelName.ifBlank { "New video" } else "${videos.size} new videos"
        val text = if (videos.size == 1) first.title else videos.take(3).joinToString(" · ") { it.channelName.ifBlank { it.title } }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.greenaltambe.vela.R.drawable.ic_stat_vela).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(videos.take(5).joinToString("\n") { "${it.channelName}: ${it.title}" }))
            .setAutoCancel(true).setContentIntent(open).build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, n) }
    }
}
