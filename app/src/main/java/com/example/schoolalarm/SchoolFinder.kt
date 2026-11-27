package com.example.schoolalarm

import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class School(val name: String, val lat: Double, val lon: Double, val distM: Int?)

/** Finds schools with OpenStreetMap (free, no account). */
object SchoolFinder {

    /** Schools within 4 km of the phone, closest first. */
    fun nearby(lat: Double, lon: Double): List<School> {
        val q = "[out:json][timeout:20];(" +
            "node[\"amenity\"=\"school\"](around:4000,$lat,$lon);" +
            "way[\"amenity\"=\"school\"](around:4000,$lat,$lon););out center 80;"
        val json = JSONObject(Net.get("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(q, "UTF-8")))
        val els: JSONArray = json.getJSONArray("elements")
        val out = LinkedHashMap<String, School>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val name = e.optJSONObject("tags")?.optString("name").orEmpty()
            if (name.isBlank()) continue
            val c = if (e.has("center")) e.getJSONObject("center") else e
            if (!c.has("lat")) continue
            val sLat = c.getDouble("lat")
            val sLon = c.getDouble("lon")
            val d = FloatArray(1)
            Location.distanceBetween(lat, lon, sLat, sLon, d)
            val s = School(name, sLat, sLon, d[0].toInt())
            val old = out[name]
            if (old == null || (s.distM ?: 0) < (old.distM ?: 0)) out[name] = s
        }
        return out.values.sortedBy { it.distM }
    }

    /** Search by name, e.g. "Honoré de Balzac, Casablanca". */
    fun search(text: String): List<School> {
        val q = URLEncoder.encode(text, "UTF-8")
        val arr = JSONArray(Net.get("https://nominatim.openstreetmap.org/search?q=$q&format=json&limit=8"))
        val out = ArrayList<School>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val parts = o.getString("display_name").split(",").map { it.trim() }
            val label = parts.take(3).joinToString(", ")
            out.add(School(label, o.getString("lat").toDouble(), o.getString("lon").toDouble(), null))
        }
        return out
    }
}
