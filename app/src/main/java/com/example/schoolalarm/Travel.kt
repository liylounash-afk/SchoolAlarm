package com.example.schoolalarm

import android.location.Location
import org.json.JSONObject
import kotlin.math.ceil

/**
 * Travel time = route distance (OpenStreetMap routing) / your speed, plus a % margin for
 * traffic and crowds. The free routing service has no live traffic, so the margin stands in.
 */
object Travel {
    fun minutes(p: Prefs): Int {
        val scooter = p.mode == "scooter"
        val meters = routeMeters(scooter, p) ?: straightLineMeters(p) * 1.3
        val kmh = if (scooter) p.scooterKmh.coerceAtLeast(5).toDouble() else 5.0
        val raw = meters / 1000.0 / kmh * 60.0
        return ceil(raw * (1 + p.trafficPct / 100.0)).toInt().coerceAtLeast(1)
    }

    private fun routeMeters(scooter: Boolean, p: Prefs): Double? = try {
        val base = if (scooter) "routed-bike" else "routed-foot"
        // These instances always use "driving" in the path; the profile is chosen by the base name.
        val url = "https://routing.openstreetmap.de/$base/route/v1/driving/" +
            "${p.homeLon},${p.homeLat};${p.schoolLon},${p.schoolLat}?overview=false"
        JSONObject(Net.get(url)).getJSONArray("routes").getJSONObject(0).getDouble("distance")
    } catch (e: Exception) {
        null
    }

    private fun straightLineMeters(p: Prefs): Double {
        val out = FloatArray(1)
        Location.distanceBetween(p.homeLat, p.homeLon, p.schoolLat, p.schoolLon, out)
        return out[0].toDouble()
    }
}
