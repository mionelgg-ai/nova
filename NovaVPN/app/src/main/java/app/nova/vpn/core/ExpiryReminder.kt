package app.nova.vpn.core

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.nova.vpn.Cfg
import app.nova.vpn.MainActivity
import app.nova.vpn.R
import java.time.LocalDate
import java.time.ZoneId

/** One local notification a few days before the subscription ends (re-scheduled every time the app opens). */
object ExpiryReminder {
    fun schedule(context: Context, expires: String?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context)
        am.cancel(pi)
        val date = expires?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val trigger = date.minusDays(Cfg.EXPIRY_WARN_DAYS.toLong()).atTime(12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (trigger > System.currentTimeMillis()) am.set(AlarmManager.RTC, trigger, pi)
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 7, Intent(context, ExpiryReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

class ExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("reminders", context.getString(R.string.notif_channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            context, 8, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, "reminders")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_expiry_title))
            .setContentText(context.getString(R.string.notif_expiry_text, Cfg.EXPIRY_WARN_DAYS))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setColor(0xFFB6FF1A.toInt())
            .build()
        nm.notify(2, n)
    }
}
