package com.example.schoolalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Works out the next school day and schedules the bag, leave and lunch alarms. */
object Planner {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM")
    private const val CODE_PRE = 1
    private const val CODE_LEAVE = 2
    private const val CODE_LUNCH = 3
    private val lunchStart: LocalTime = LocalTime.of(12, 30)

    /** Returns "" when planning worked, otherwise the message to show. */
    fun run(ctx: Context): String {
        val p = Prefs(ctx)
        val result = try {
            plan(ctx, p)
        } catch (e: Exception) {
            "Could not plan: ${e.message}"
        }
        p.lastResult = result
        return result
    }

    private fun lines(list: List<Course>) = list.joinToString("\n") {
        val room = if (it.room.isNotBlank()) " · ${it.room}" else ""
        "${it.start.format(hm)}–${it.end.format(hm)}  ${it.subject}$room"
    }

    private fun label(day: LocalDate, today: LocalDate) = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> day.format(dayFmt)
    }

    private fun plan(ctx: Context, p: Prefs): String {
        if (p.schoolLat.isNaN()) return "Choose your school first (open Setup)."
        if (p.homeLat.isNaN()) return "Set your home location first (open Setup)."

        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        val courses = Timetable.courses(today, 15, p.anchorEpochDay)
        val travel = Travel.minutes(p)
        val lead = (travel + p.bufferMin).toLong()
        val limit = now.plusSeconds(15)

        var preAt: ZonedDateTime? = null
        var leaveAt: ZonedDateTime? = null
        var lunchAt: ZonedDateTime? = null
        p.preAtMillis = 0L; p.leaveAtMillis = 0L; p.lunchAtMillis = 0L
        p.planDay = ""; p.lunchDay = ""

        // Morning: leave before the first class of the next school day
        for (d in 0..13) {
            val day = today.plusDays(d.toLong())
            val list = courses.filter { it.start.toLocalDate() == day }.sortedBy { it.start }
            if (list.isEmpty()) continue
            val first = list.first().start
            val leave = first.minusMinutes(lead)
            if (!leave.isAfter(limit)) continue // already past, try the next day
            leaveAt = leave
            preAt = leave.minusMinutes(p.prepMin.toLong())
            p.scheduleText = lines(list)
            p.planDay = label(day, today)
            p.planFirst = first.format(hm)
            p.planTrip = (if (p.mode == "scooter") "Scooter" else "Walk") + " ~$travel min (+${p.bufferMin} min early)"
            p.preAtMillis = preAt.toInstant().toEpochMilli()
            p.leaveAtMillis = leave.toInstant().toEpochMilli()
            break
        }

        // Lunch: leave for the first afternoon class (only on days that also have morning classes)
        for (d in 0..13) {
            val day = today.plusDays(d.toLong())
            val list = courses.filter { it.start.toLocalDate() == day }.sortedBy { it.start }
            val morning = list.any { it.start.toLocalTime().isBefore(lunchStart) }
            val afternoon = list.filter { !it.start.toLocalTime().isBefore(lunchStart) }
            if (!morning || afternoon.isEmpty()) continue
            val leave = afternoon.first().start.minusMinutes(lead)
            if (!leave.isAfter(limit)) continue
            lunchAt = leave
            p.lunchText = lines(afternoon)
            p.lunchDay = label(day, today)
            p.lunchAtMillis = leave.toInstant().toEpochMilli()
            break
        }

        if (leaveAt == null && lunchAt == null) return "No upcoming classes found in the next 2 weeks."

        set(ctx, CODE_PRE, "pre", if (p.alarmEnabled("pre")) preAt else null)
        set(ctx, CODE_LEAVE, "leave", if (p.alarmEnabled("leave")) leaveAt else null)
        set(ctx, CODE_LUNCH, "lunch", if (p.alarmEnabled("lunch")) lunchAt else null)
        return ""
    }

    /** Sets the alarm, or cancels it when [at] is null. */
    private fun set(ctx: Context, code: Int, kind: String, at: ZonedDateTime?) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, AlarmReceiver::class.java).putExtra("type", kind),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (at == null || !at.isAfter(ZonedDateTime.now())) {
            am.cancel(pi)
            return
        }
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at.toInstant().toEpochMilli(), show), pi)
    }

    /** Re-plans every 3 hours (and right now). */
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.enqueueUniquePeriodicWork(
            "plan-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PlanWorker>(3, TimeUnit.HOURS).build()
        )
        wm.enqueueUniqueWork("plan-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<PlanWorker>().build())
    }
}

class PlanWorker(c: Context, w: WorkerParameters) : Worker(c, w) {
    override fun doWork(): Result {
        Planner.run(applicationContext)
        return Result.success()
    }
}
