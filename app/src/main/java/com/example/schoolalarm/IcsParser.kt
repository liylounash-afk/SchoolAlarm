package com.example.schoolalarm

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class Course(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val subject: String,
    val room: String,
    val cancelled: Boolean
)

/** Minimal iCal reader for the calendar export Pronote provides. */
object IcsParser {
    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun parse(text: String): List<Course> {
        // Unfold continuation lines (start with space or tab)
        val lines = ArrayList<String>()
        for (raw in text.lines()) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.size - 1] = lines.last() + raw.substring(1)
            } else {
                lines.add(raw)
            }
        }

        val out = ArrayList<Course>()
        var cur: MutableMap<String, String>? = null
        for (l in lines) {
            when {
                l == "BEGIN:VEVENT" -> cur = HashMap()
                l == "END:VEVENT" -> {
                    cur?.let { toCourse(it) }?.let { out.add(it) }
                    cur = null
                }
                cur != null -> {
                    val i = l.indexOf(':')
                    if (i > 0) {
                        val head = l.substring(0, i)
                        val key = head.substringBefore(';').uppercase()
                        cur[key] = l.substring(i + 1)
                        val tz = head.split(';').firstOrNull { it.uppercase().startsWith("TZID=") }
                        if (tz != null) cur["$key.TZID"] = tz.substringAfter('=')
                    }
                }
            }
        }
        return out
    }

    private fun toCourse(m: Map<String, String>): Course? {
        val start = time(m["DTSTART"], m["DTSTART.TZID"]) ?: return null
        val end = time(m["DTEND"], m["DTEND.TZID"]) ?: start.plusMinutes(55)
        val subject = unescape(m["SUMMARY"] ?: "Class")
        val room = unescape(m["LOCATION"] ?: "")
        val cancelled = m["STATUS"]?.uppercase() == "CANCELLED" ||
            subject.contains("annul", ignoreCase = true)
        return Course(start, end, subject, room, cancelled)
    }

    private fun time(v: String?, tzid: String?): ZonedDateTime? {
        if (v == null || !v.contains('T')) return null // skip all-day events
        return try {
            val sys = ZoneId.systemDefault()
            if (v.endsWith("Z")) {
                LocalDateTime.parse(v.dropLast(1), fmt).atZone(ZoneOffset.UTC).withZoneSameInstant(sys)
            } else {
                val zone = tzid?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: sys
                LocalDateTime.parse(v, fmt).atZone(zone).withZoneSameInstant(sys)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun unescape(s: String) =
        s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim()
}
