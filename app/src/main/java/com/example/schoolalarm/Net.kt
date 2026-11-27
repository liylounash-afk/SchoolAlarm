package com.example.schoolalarm

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Net {
    fun get(url: String): String {
        val fixed = url.trim().replaceFirst("webcal://", "https://")
        val c = URL(fixed).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "SchoolAlarm/1.0 (personal Android app)")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Address -> (lat, lon) using OpenStreetMap Nominatim. */
    fun geocode(address: String): Pair<Double, Double>? {
        val q = URLEncoder.encode(address, "UTF-8")
        val arr = JSONArray(get("https://nominatim.openstreetmap.org/search?q=$q&format=json&limit=1"))
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        return Pair(o.getString("lat").toDouble(), o.getString("lon").toDouble())
    }
}
