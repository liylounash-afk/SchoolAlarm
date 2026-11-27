package com.example.schoolalarm

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/** Fires at the bag time and the leave time. Does nothing if the phone isn't near home. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return
        val pending = goAsync()
        Thread {
            try {
                val p = Prefs(ctx)
                if (!isNearHome(ctx, p)) return@Thread
                Notifier.ensureChannels(ctx)
                if (type == "pre") {
                    Notifier.pre(ctx, p)
                    Notifier.twoBeeps()
                } else {
                    Notifier.leave(ctx, p)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    /** If the location can't be read, we assume you're home so you never miss an alarm. */
    private fun isNearHome(ctx: Context, p: Prefs): Boolean {
        if (p.homeLat.isNaN()) return true
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return true
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val loc: Location = try {
            Tasks.await(client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null), 5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            null
        } ?: try {
            Tasks.await(client.lastLocation, 2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            null
        } ?: return true

        val out = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, p.homeLat, p.homeLon, out)
        return out[0] <= p.radiusM
    }
}

/** After a reboot all alarms are gone, so plan again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Planner.schedule(ctx)
    }
}
