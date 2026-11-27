package com.example.schoolalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Works out the next school day and schedules the two alarms. */
object Planner {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM")
    private const val CODE_PRE = 1
    private const val CODE_LEAVE = 2

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

    private fun plan(ctx: Context, p: Prefs): String {
        if (p.schoolLat.isNaN()) return "Choose your school first."
        if (p.homeLat.isNaN()) return "Set your home location first."

        val now = ZonedDateTime.now()
        val courses = Timetable.courses(now.toLocalDate(), 15, p.anchorEpochDay)
        val travel = Travel.minutes(p)

        for (d in 0..13) {
            val day = now.toLocalDate().plusDays(d.toLong())
            val list = courses.filter { it.start.toLocalDate() == day }.sortedBy { it.start }
            if (list.isEmpty()) continue

            val first = list.first().start
            val leaveAt = first.minusMinutes((travel + p.bufferMin).toLong())
            if (!leaveAt.isAfter(now.plusSeconds(15))) continue // already past, try next day

            val preAt = leaveAt.minusMinutes(p.prepMin.toLong())

            p.scheduleText = list.joinToString("\n") {
                val room = if (it.room.isNotBlank()) " · ${it.room}" else ""
                "${it.start.format(hm)}–${it.end.format(hm)}  ${it.subject}$room"
            }
            p.leaveAtMillis = leaveAt.toInstant().toEpochMilli()

            setAlarm(ctx, CODE_PRE, "pre", preAt)
            setAlarm(ctx, CODE_LEAVE, "leave", leaveAt)

            val how = if (p.mode == "scooter") "Scooter" else "Walk"
            return "${day.format(dayFmt)}: first class ${first.format(hm)}. " +
                "$how ~$travel min (+${p.bufferMin} buffer).\n" +
                "Bag alarm ${preAt.format(hm)} · Leave ${leaveAt.format(hm)}"
        }
        return "No upcoming classes found in the next 2 weeks."
    }

    private fun setAlarm(ctx: Context, code: Int, type: String, at: ZonedDateTime) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, AlarmReceiver::class.java).putExtra("type", type),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (!at.isAfter(ZonedDateTime.now())) {
            am.cancel(pi)
            return
        }
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at.toInstant().toEpochMilli(), show), pi)
    }

    /** Re-plans every 3 hours (picks up timetable changes) and right now. */
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(
            "plan-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PlanWorker>(3, TimeUnit.HOURS).setConstraints(net).build()
        )
        wm.enqueueUniqueWork(
            "plan-now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PlanWorker>().setConstraints(net).build()
        )
    }
}

class PlanWorker(c: Context, w: WorkerParameters) : Worker(c, w) {
    override fun doWork(): Result {
        Planner.run(applicationContext)
        return Result.success()
    }
}
