package com.closer.app.notify

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
import com.closer.app.MainActivity
import com.closer.app.R
import java.time.LocalDateTime

class Notifier(private val context: Context) {

    companion object {
        const val CH_PROXIMITY = "proximity"
        const val CH_LIVE = "live"
        const val ID_PROXIMITY = 1001
        const val ID_LIVE = 1002
    }

    init {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CH_PROXIMITY,
                context.getString(R.string.channel_proximity),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.channel_proximity_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CH_LIVE,
                context.getString(R.string.channel_live),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.channel_live_desc) }
        )
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Fires when the two of you cross into the radius. */
    fun proximity(partnerName: String, distanceText: String) {
        if (!canPost()) return
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_PROXIMITY)
            .setSmallIcon(R.drawable.ic_stat_closer)
            .setContentTitle(context.getString(R.string.notif_proximity_title))
            .setContentText("You and $partnerName are $distanceText apart — meet up?")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 60, 60, 60))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_PROXIMITY, n) }
    }

    /** The subtle ongoing notification required by Live-mode foreground service. */
    fun live(partnerName: String): android.app.Notification {
        val pi = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat_closer)
            .setContentTitle(context.getString(R.string.notif_live_title))
            .setContentText(context.getString(R.string.notif_live_text, partnerName.ifEmpty { "your partner" }))
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    fun isQuietHour(fromHour: Int, toHour: Int, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val h = now.hour
        return if (fromHour <= toHour) h in fromHour until toHour
        else h >= fromHour || h < toHour
    }
}
