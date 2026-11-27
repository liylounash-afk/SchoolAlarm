package com.example.schoolalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import androidx.core.app.NotificationCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Notifier {
    private const val CH_PRE = "pre_v1"
    private const val CH_LEAVE = "leave_v1"
    private const val ID_PRE = 11
    private const val ID_LEAVE = 12

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)

        // Bag/toilet reminder: heads-up, no sound here (two loud beeps are played separately)
        val pre = NotificationChannel(CH_PRE, "Before leaving (toilet & bag)", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        // Leave alarm: the default alarm sound, played once (not insistent, so it never repeats)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val leave = NotificationChannel(CH_LEAVE, "Time to leave", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attrs)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(pre, leave))
    }

    private fun stopAction(ctx: Context, id: Int): NotificationCompat.Action {
        val pi = PendingIntent.getBroadcast(
            ctx, id, Intent(ctx, StopReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Works from the lock screen without unlocking (Android 12+)
        return NotificationCompat.Action.Builder(0, "Stop", pi).setAuthenticationRequired(false).build()
    }

    private fun leaveTime(p: Prefs): String =
        DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(p.leaveAtMillis).atZone(ZoneId.systemDefault()))

    fun pre(ctx: Context, p: Prefs) {
        val body = "Toilet, then put your books in your bag.\nLeave at ${leaveTime(p)}\n\n${p.scheduleText}"
        val n = NotificationCompat.Builder(ctx, CH_PRE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Leaving in ${p.prepMin} min")
            .setContentText("Toilet + books in your bag")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setTimeoutAfter(p.prepMin * 60_000L)
            .setAutoCancel(true)
            .addAction(stopAction(ctx, ID_PRE))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_PRE, n)
    }

    fun leave(ctx: Context, p: Prefs) {
        val first = p.scheduleText.lines().firstOrNull() ?: ""
        val body = "Time to go!\n\n${p.scheduleText}"
        val n = NotificationCompat.Builder(ctx, CH_LEAVE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time to leave for school")
            .setContentText(first)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(15 * 60_000L)
            .setAutoCancel(true)
            .addAction(stopAction(ctx, ID_LEAVE))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_LEAVE, n)
    }

    /** Two short, loud beeps on the alarm volume. Blocks ~1 second: call off the main thread. */
    fun twoBeeps() {
        val tg = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        try {
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            Thread.sleep(550)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            Thread.sleep(450)
        } finally {
            tg.release()
        }
    }
}

/** The "Stop" button on the notification (works from the lock screen). */
class StopReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        ctx.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra("id", 0))
    }
}
