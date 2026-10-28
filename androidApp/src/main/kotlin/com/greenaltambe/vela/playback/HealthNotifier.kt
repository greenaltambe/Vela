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
import com.greenaltambe.vela.health.HealthAlert

/** The "playback may be broken" notification: its own channel, posted once per failure streak (the caller decides when), skipped when the notification permission is denied. */
object HealthNotifier {
    const val CHANNEL = "health_alerts"
    private const val ID = 4100

    fun show(context: Context, alert: HealthAlert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Playback health alerts", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle("Playback may be broken").setContentText(alert.message())
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message())).setAutoCancel(true).setContentIntent(open).build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, n) }
    }
}
