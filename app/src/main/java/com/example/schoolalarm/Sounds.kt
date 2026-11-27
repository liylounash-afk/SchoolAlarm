package com.example.schoolalarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator

/** The sounds an alarm can use. Every one plays once and stops: nothing repeats. */
object Sounds {
    val KEYS = listOf("ting", "chime", "alarm", "silent")

    fun label(key: String) = when (key) {
        "ting" -> "Ting ting"
        "chime" -> "Soft chime"
        "alarm" -> "Alarm tone"
        else -> "Silent"
    }

    fun describe(key: String) = when (key) {
        "ting" -> "Ting ting: two short loud beeps"
        "chime" -> "Soft chime: your notification sound, once"
        "alarm" -> "Alarm tone: your alarm sound, 5 seconds"
        else -> "Silent: only the notification"
    }

    /** Plays the sound once. Blocks for up to ~5 s, so call it off the main thread. */
    fun play(ctx: Context, key: String, vibrate: Boolean) {
        if (vibrate) vibrate(ctx)
        when (key) {
            "ting" -> twoBeeps()
            "chime" -> ringtone(ctx, RingtoneManager.TYPE_NOTIFICATION, 2500)
            "alarm" -> ringtone(ctx, RingtoneManager.TYPE_ALARM, 5000)
        }
    }

    private fun twoBeeps() {
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

    private fun ringtone(ctx: Context, type: Int, ms: Long) {
        val r = RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(type)) ?: return
        r.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        r.play()
        try {
            Thread.sleep(ms)
        } finally {
            r.stop()
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ctx: Context) {
        val v = ctx.getSystemService(Vibrator::class.java) ?: return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
    }
}
