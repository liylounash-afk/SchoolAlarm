package com.example.schoolalarm

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** week = 'A' or 'B' when the class only happens in that week, null when it happens every week. */
data class Lesson(
    val day: Int, // 1 = Monday … 5 = Friday
    val start: String,
    val end: String,
    val subject: String,
    val teacher: String,
    val room: String,
    val week: Char?,
    val group: String = ""
)

/** The yearly timetable (from the Pronote PDF, 2026-2027). To change it, edit the list below. */
object Timetable {
    private const val M1 = "08:15-09:10"
    private const val M2 = "09:15-10:10"
    private const val M3 = "10:25-11:10"
    private const val M4 = "11:25-12:10"
    private const val M12 = "08:15-10:10"
    private const val M34 = "10:25-12:10"
    private const val A1 = "13:50-14:50"
    private const val A2 = "14:50-16:00"
    private const val A3 = "16:00-17:00"
    private const val A12 = "13:50-16:00"
    private const val A34 = "16:00-17:55"

    private const val MATHS = "Mathématiques"
    private const val ARABE = "Arabe Langue Vivante A"
    private const val FRANCAIS = "Français"
    private const val HG = "Histoire-Géographie-EMC"
    private const val HG_ARABE = "Histoire-géo Arabe"
    private const val ANGLAIS = "Anglais LVB"
    private const val ESPAGNOL = "Espagnol LVB"
    private const val EPS = "EPS"
    private const val SVT = "Sciences de la vie et de la terre"
    private const val PHYS = "Sciences physiques et chimie"
    private const val TECHNO = "Technologie"

    private const val BRITEL = "BRITEL M."
    private const val CHRAOUTI = "CHRAOUTI D."
    private const val GUEPRATTE = "GUEPRATTE M."
    private const val ID_MOUSSA = "ID MOUSSA A."
    private const val EL_HACHIMI = "EL HACHIMI M."
    private const val AFFLELOU = "AFFLELOU L."
    private const val EL_OURZADI = "EL OURZADI H."
    private const val ARFA = "ARFA M."
    private const val FOUITAH = "FOUITAH B."
    private const val EZZAYDY = "EZZAYDY N."
    private const val MRANI = "M'RANI ALAOUI A."

    private fun l(day: Int, slot: String, subject: String, teacher: String, room: String, week: Char? = null, group: String = ""): Lesson {
        val (s, e) = slot.split("-")
        return Lesson(day, s, e, subject, teacher, room, week, group)
    }

    val lessons: List<Lesson> = listOf(
        // ---- Monday ----
        l(1, M34, PHYS, EZZAYDY, "B.05", 'A', "302 Gr.B"),
        l(1, M1, MATHS, BRITEL, "B.12", 'B'),
        l(1, M2, ARABE, CHRAOUTI, "B.05", 'B'),
        l(1, M3, FRANCAIS, GUEPRATTE, "B.14_info", 'B'),
        l(1, M4, HG, ID_MOUSSA, "B.13", 'B'),
        l(1, A1, HG_ARABE, EL_HACHIMI, "B.16", 'A'),
        l(1, A1, MATHS, BRITEL, "B.12", 'B'),
        l(1, A2, FRANCAIS, GUEPRATTE, "B.10", 'A'),
        l(1, A2, ANGLAIS, AFFLELOU, "B.10", 'B'),
        l(1, A34, EPS, ARFA, "Gymnase-1"),

        // ---- Tuesday ----
        l(2, M1, MATHS, BRITEL, "B.12", 'A'),
        l(2, M12, TECHNO, MRANI, "B.03", 'B', "302 Gr.B"),
        l(2, M2, ARABE, CHRAOUTI, "B.12", 'A'),
        l(2, M3, ARABE, CHRAOUTI, "B.02", 'A'),
        l(2, M34, SVT, FOUITAH, "B.04", 'B', "302 Gr.B"),
        l(2, M4, HG_ARABE, EL_HACHIMI, "B.12", 'A'),
        l(2, A12, FRANCAIS, GUEPRATTE, "B.14_info"),
        l(2, A3, ANGLAIS, AFFLELOU, "B.10"),

        // ---- Wednesday ----
        l(3, M1, FRANCAIS, GUEPRATTE, "B.14_info"),
        l(3, M2, ESPAGNOL, EL_OURZADI, "B.16", 'A', "302 Esp"),
        l(3, M2, ARABE, CHRAOUTI, "B.01", 'B'),
        l(3, M3, EPS, ARFA, "Gymnase-1"),
        l(3, M4, ARABE, CHRAOUTI, "B.01"),

        // ---- Thursday ----
        l(4, M1, "Education musicale", "JAOUHARI A.", "B.01"),
        l(4, M2, HG, ID_MOUSSA, "B.13"),
        l(4, M3, ANGLAIS, AFFLELOU, "B.10", 'A'),
        l(4, M3, ARABE, CHRAOUTI, "B.01", 'B'),
        l(4, M4, ARABE, CHRAOUTI, "B.14_info", 'A'),
        l(4, M4, ANGLAIS, AFFLELOU, "B.10", 'B'),
        l(4, A1, MATHS, BRITEL, "B.12", 'A'),
        l(4, A1, HG_ARABE, EL_HACHIMI, "B.12", 'B'),
        l(4, A2, SVT, FOUITAH, "B.04", 'A'),
        l(4, A2, PHYS, EZZAYDY, "B.05", 'B'),

        // ---- Friday ----
        l(5, M1, MATHS, BRITEL, "B.15"),
        l(5, M2, ESPAGNOL, EL_OURZADI, "B.10", null, "302 Esp"),
        l(5, M3, HG, ID_MOUSSA, "B.13", 'A'),
        l(5, M3, ARABE, CHRAOUTI, "B.01", 'B'),
        l(5, M4, TECHNO, MRANI, "B.03", 'A'),
        l(5, M4, MATHS, BRITEL, "B.12", 'B'),
        l(5, A1, "Arts plastiques", "FADILI R.", "B.16"),
        l(5, A2, ESPAGNOL, EL_OURZADI, "B.10", 'A', "302 Esp"),
        l(5, A2, ARABE, CHRAOUTI, "B.12", 'B'),
        l(5, A3, HG_ARABE, EL_HACHIMI, "B.13", 'A'),
        l(5, A3, ESPAGNOL, EL_OURZADI, "B.10", 'B', "302 Esp")
    )

    /** 'A' or 'B' for the week containing [date]. [anchorEpochDay] is the Monday of any week that is an A week. */
    fun weekOf(date: LocalDate, anchorEpochDay: Long): Char {
        val monday = date.with(DayOfWeek.MONDAY)
        val weeks = Math.floorDiv(monday.toEpochDay() - anchorEpochDay, 7L)
        return if (Math.floorMod(weeks, 2L) == 0L) 'A' else 'B'
    }

    fun lessonsFor(day: Int, week: Char): List<Lesson> =
        lessons.filter { it.day == day && (it.week == null || it.week == week) }.sortedBy { it.start }

    /** Real dated classes for the next [days] days, used by the alarm planner. */
    fun courses(from: LocalDate, days: Int, anchorEpochDay: Long): List<Course> {
        val zone = ZoneId.systemDefault()
        val out = ArrayList<Course>()
        for (i in 0 until days) {
            val date = from.plusDays(i.toLong())
            val dow = date.dayOfWeek.value
            if (dow > 5) continue
            for (x in lessonsFor(dow, weekOf(date, anchorEpochDay))) {
                out.add(
                    Course(
                        date.atTime(LocalTime.parse(x.start)).atZone(zone),
                        date.atTime(LocalTime.parse(x.end)).atZone(zone),
                        x.subject, x.room, false
                    )
                )
            }
        }
        return out
    }

    /** Soft pastel per subject, close to the Pronote colours. */
    fun color(subject: String): Int = when {
        subject.startsWith("Math") -> 0xFFD9998F
        subject.startsWith("Arabe") -> 0xFF8FB8C6
        subject.startsWith("Fran") -> 0xFFB7BDC7
        subject.startsWith("Histoire-géo Arabe") -> 0xFF9FA3E6
        subject.startsWith("Histoire") -> 0xFFC49A97
        subject.startsWith("Anglais") -> 0xFF6DB8DD
        subject.startsWith("Espagnol") -> 0xFFE2A9C1
        subject == "EPS" -> 0xFF6FD06F
        subject.startsWith("Sciences de la vie") -> 0xFFB48DA0
        subject.startsWith("Sciences physiques") -> 0xFFB78FC2
        subject.startsWith("Techno") -> 0xFFB78FC2
        subject.startsWith("Education") -> 0xFFCCC66D
        subject.startsWith("Arts") -> 0xFFD6CB5C
        else -> 0xFF9AA5B1
    }.toInt()
}
