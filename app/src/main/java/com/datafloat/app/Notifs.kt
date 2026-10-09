package com.datafloat.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifs {
    private const val CH_TRACK = "tracking"
    private const val CH_LIMIT = "limits"
    const val ID_TRACK = 1
    private const val ID_LIMIT = 2

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_TRACK, "Tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Silent status notification required while DataFloat tracks in the background"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_LIMIT, "Data limit warnings", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Shown at most three times per period: 80%, 90% and 100% of your limit"
            }
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun tracking(ctx: Context, text: String): Notification {
        val stop = PendingIntent.getService(
            ctx, 1,
            Intent(ctx, TrackerService::class.java).setAction(TrackerService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(ctx, CH_TRACK)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("DataFloat")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Stop tracking", stop)
            .build()
    }

    fun updateTracking(ctx: Context, text: String) {
        ctx.getSystemService(NotificationManager::class.java).notify(ID_TRACK, tracking(ctx, text))
    }

    fun limit(ctx: Context, title: String, text: String) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CH_LIMIT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_LIMIT, n)
    }
}
