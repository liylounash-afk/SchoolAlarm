package com.example.schoolalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shows the lock-screen notification. The sound is played separately by [Sounds]. */
object Notifier {
    private const val CH = "alarms_v2"
    private const val SAMPLE = "08:15–09:10  Mathématiques · B.12\n09:15–10:10  Arabe Langue Vivante A · B.12"

    private fun idOf(kind: String) = when (kind) { "pre" -> 11; "leave" -> 12; else -> 13 }

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("pre_v1")
        nm.deleteNotificationChannel("leave_v1")
        val ch = NotificationChannel(CH, "School alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null) // sound is chosen per alarm in the app
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    private fun stopAction(ctx: Context, id: Int): NotificationCompat.Action {
        val pi = PendingIntent.getBroadcast(
            ctx, id, Intent(ctx, StopReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Works from the lock screen without unlocking
        return NotificationCompat.Action.Builder(0, "Stop", pi).setAuthenticationRequired(false).build()
    }

    private fun clock(millis: Long): String =
        if (millis <= 0) "--:--"
        else DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun fire(ctx: Context, kind: String, p: Prefs, test: Boolean) {
        ensureChannels(ctx)
        val day = p.scheduleText.ifBlank { SAMPLE }
        val afternoon = p.lunchText.ifBlank { day }
        val tag = if (test) "TEST · " else ""

        val title: String
        val short: String
        val body: String
        when (kind) {
            "pre" -> {
                title = "${tag}Leaving in ${p.prepMin} min"
                short = "Toilet + books in your bag"
                body = "Toilet, then put your books in your bag.\nLeave at ${clock(p.leaveAtMillis)}\n\n$day"
            }
            "leave" -> {
                title = "${tag}Time to leave for school"
                short = day.lines().first()
                body = "Time to go!\n\n$day"
            }
            else -> {
                title = "${tag}Time to head back to school"
                short = afternoon.lines().first()
                body = "Lunch is over. This afternoon:\n\n$afternoon"
            }
        }

        val id = idOf(kind)
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(if (kind == "pre") NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(15 * 60_000L)
            .setAutoCancel(true)
            .addAction(stopAction(ctx, id))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}

/** The "Stop" button on the notification (works from the lock screen). */
class StopReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        ctx.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra("id", 0))
    }
}
