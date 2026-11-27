package com.example.schoolalarm

import android.content.Context

/** All settings and the last computed plan, stored in SharedPreferences. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("school", Context.MODE_PRIVATE)

    private fun str(k: String) = p.getString(k, "") ?: ""
    private fun putStr(k: String, v: String) = p.edit().putString(k, v).apply()
    private fun int(k: String, d: Int) = p.getInt(k, d)
    private fun putInt(k: String, v: Int) = p.edit().putInt(k, v).apply()
    private fun dbl(k: String) = Double.fromBits(p.getLong(k, Double.NaN.toRawBits()))
    private fun putDbl(k: String, v: Double) = p.edit().putLong(k, v.toRawBits()).apply()

    /** Epoch day of a Monday that starts an "A" week; the A/B rhythm is counted from it. */
    var anchorEpochDay: Long
        get() = p.getLong("anchor", java.time.LocalDate.of(2026, 11, 30).toEpochDay())
        set(v) = p.edit().putLong("anchor", v).apply()

    var icsUrl: String
        get() = str("ics")
        set(v) = putStr("ics", v.trim())
    var schoolAddress: String
        get() = str("schoolAddress")
        set(v) = putStr("schoolAddress", v.trim())
    var schoolLat: Double
        get() = dbl("schoolLat")
        set(v) = putDbl("schoolLat", v)
    var schoolLon: Double
        get() = dbl("schoolLon")
        set(v) = putDbl("schoolLon", v)
    var homeLat: Double
        get() = dbl("homeLat")
        set(v) = putDbl("homeLat", v)
    var homeLon: Double
        get() = dbl("homeLon")
        set(v) = putDbl("homeLon", v)

    /** "foot" or "scooter" */
    var mode: String
        get() = str("mode").ifEmpty { "foot" }
        set(v) = putStr("mode", v)
    var scooterKmh: Int
        get() = int("scooterKmh", 15)
        set(v) = putInt("scooterKmh", v)
    var trafficPct: Int
        get() = int("trafficPct", 15)
        set(v) = putInt("trafficPct", v)
    var bufferMin: Int
        get() = int("bufferMin", 5)
        set(v) = putInt("bufferMin", v)
    var prepMin: Int
        get() = int("prepMin", 10)
        set(v) = putInt("prepMin", v)
    var radiusM: Int
        get() = int("radiusM", 300)
        set(v) = putInt("radiusM", v)

    // Result of the last planning run
    var scheduleText: String
        get() = str("scheduleText")
        set(v) = putStr("scheduleText", v)
    var leaveAtMillis: Long
        get() = p.getLong("leaveAt", 0L)
        set(v) = p.edit().putLong("leaveAt", v).apply()
    /** Empty when the last plan worked, otherwise the error to show. */
    var lastResult: String
        get() = str("lastResult")
        set(v) = putStr("lastResult", v)

    var preAtMillis: Long
        get() = p.getLong("preAt", 0L)
        set(v) = p.edit().putLong("preAt", v).apply()
    var lunchAtMillis: Long
        get() = p.getLong("lunchAt", 0L)
        set(v) = p.edit().putLong("lunchAt", v).apply()
    var lunchText: String
        get() = str("lunchText")
        set(v) = putStr("lunchText", v)
    var planDay: String
        get() = str("planDay")
        set(v) = putStr("planDay", v)
    var planFirst: String
        get() = str("planFirst")
        set(v) = putStr("planFirst", v)
    var planTrip: String
        get() = str("planTrip")
        set(v) = putStr("planTrip", v)
    var lunchDay: String
        get() = str("lunchDay")
        set(v) = putStr("lunchDay", v)

    // Per-alarm settings. kind = "pre" (bag), "leave", "lunch"
    fun alarmEnabled(kind: String): Boolean = p.getBoolean("al.$kind.on", kind != "lunch")
    fun setAlarmEnabled(kind: String, v: Boolean) = p.edit().putBoolean("al.$kind.on", v).apply()
    fun alarmSound(kind: String): String = p.getString("al.$kind.sound", null)
        ?: when (kind) { "pre" -> "ting"; "leave" -> "alarm"; else -> "chime" }
    fun setAlarmSound(kind: String, v: String) = p.edit().putString("al.$kind.sound", v).apply()
    fun alarmVibrate(kind: String): Boolean = p.getBoolean("al.$kind.vib", kind == "leave")
    fun setAlarmVibrate(kind: String, v: Boolean) = p.edit().putBoolean("al.$kind.vib", v).apply()
}
