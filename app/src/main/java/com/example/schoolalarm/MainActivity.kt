package com.example.schoolalarm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var planText: TextView
    private lateinit var dayRow: LinearLayout
    private lateinit var weekRow: LinearLayout
    private lateinit var lessonsBox: LinearLayout
    private lateinit var schoolLine: TextView
    private lateinit var homeLine: TextView
    private lateinit var anchorLabel: TextView
    private lateinit var anchorRow: LinearLayout
    private lateinit var modeRow: LinearLayout
    private lateinit var advanced: LinearLayout
    private lateinit var speed: EditText
    private lateinit var traffic: EditText
    private lateinit var buffer: EditText
    private lateinit var prep: EditText
    private lateinit var radius: EditText

    private var selDay = 1
    private var selWeek = 'A'
    private var scooter = false

    private val repo = "liylounash-afk/SchoolAlarm"
    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")

    // Colours
    private val bg = 0xFFF3F5F8.toInt()
    private val ink = 0xFF1B2430.toInt()
    private val muted = 0xFF6B7684.toInt()
    private val accent = 0xFF2F6FED.toInt()
    private val chipBg = 0xFFE6EAF0.toInt()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }

    private fun text(t: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun card(parent: LinearLayout, fill: Int = Color.WHITE): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(fill, 18)
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        parent.addView(c, lp(top = 12))
        return c
    }

    private fun button(label: String, filled: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(if (filled) Color.WHITE else accent)
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(if (filled) accent else 0xFFE8EFFD.toInt(), 14)
        setPadding(dp(12), dp(13), dp(12), dp(13))
        setOnClickListener { onClick() }
    }

    private fun chips(row: LinearLayout, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        row.removeAllViews()
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            val chip = TextView(this).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(if (sel) Color.WHITE else ink)
                if (sel) setTypeface(typeface, Typeface.BOLD)
                background = rounded(if (sel) accent else chipBg, 20)
                setPadding(dp(6), dp(10), dp(6), dp(10))
                setOnClickListener { onPick(i) }
            }
            row.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
    }

    private fun field(hint: String, value: String) = EditText(this).apply {
        this.hint = hint
        setText(value)
        inputType = InputType.TYPE_CLASS_NUMBER
        textSize = 15f
    }

    // ------------------------------------------------------------------

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        Notifier.ensureChannels(this)
        scooter = p.mode == "scooter"

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(40))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(col)
        })

        col.addView(text("School Alarm", 28f, ink, true))
        schoolLine = text("", 14f, muted)
        col.addView(schoolLine)

        // --- Next alarms ---
        val next = card(col, accent)
        next.addView(text("NEXT ALARMS", 12f, 0xB3FFFFFF.toInt(), true))
        planText = text("", 18f, Color.WHITE, true).apply { setPadding(0, dp(6), 0, 0) }
        next.addView(planText)

        // --- Timetable ---
        val tt = card(col)
        tt.addView(text("Timetable", 18f, ink, true))
        dayRow = LinearLayout(this)
        tt.addView(dayRow, lp(top = 10))
        weekRow = LinearLayout(this)
        tt.addView(weekRow, lp(top = 8))
        lessonsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tt.addView(lessonsBox, lp(top = 8))

        // --- Setup ---
        val setup = card(col)
        setup.addView(text("Setup", 18f, ink, true))

        setup.addView(text("School", 13f, muted).apply { setPadding(0, dp(12), 0, 0) })
        homeLine = text("", 15f)
        // school line inside card
        val schoolInCard = text("", 16f, ink, true)
        setup.addView(schoolInCard)
        val schoolBtns = LinearLayout(this)
        schoolBtns.addView(button("Find near me") { findNearby() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(6), 0) })
        schoolBtns.addView(button("Search name") { searchByName() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        setup.addView(schoolBtns, lp(top = 8))
        schoolLine.tag = schoolInCard

        setup.addView(text("Home", 13f, muted).apply { setPadding(0, dp(16), 0, 0) })
        setup.addView(homeLine)
        setup.addView(button("I'm at home now") { setHomeHere() }, lp(top = 8))

        setup.addView(text("I go to school", 13f, muted).apply { setPadding(0, dp(16), 0, 0) })
        modeRow = LinearLayout(this)
        setup.addView(modeRow, lp(top = 6))

        anchorLabel = text("", 13f, muted).apply { setPadding(0, dp(16), 0, 0) }
        setup.addView(anchorLabel)
        anchorRow = LinearLayout(this)
        setup.addView(anchorRow, lp(top = 6))

        col.addView(button("Save & plan my alarms", true) { saveAndPlan() }, lp(top = 16))

        // --- More ---
        val more = card(col)
        more.addView(button("Advanced settings") {
            advanced.visibility = if (advanced.visibility == View.GONE) View.VISIBLE else View.GONE
        })
        advanced = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        speed = field("15", p.scooterKmh.toString())
        traffic = field("15", p.trafficPct.toString())
        buffer = field("5", p.bufferMin.toString())
        prep = field("10", p.prepMin.toString())
        radius = field("300", p.radiusM.toString())
        for ((label, f) in listOf(
            "Scooter speed (km/h)" to speed,
            "Traffic margin (% added to the trip)" to traffic,
            "Arrive early (minutes)" to buffer,
            "Bag/toilet reminder, minutes before leaving" to prep,
            "Only ring within this many metres of home" to radius
        )) {
            advanced.addView(text(label, 13f, muted).apply { setPadding(0, dp(10), 0, 0) })
            advanced.addView(f)
        }
        advanced.addView(button("Allow location 'all the time'") { askBackgroundLocation() }, lp(top = 14))
        more.addView(advanced, lp(top = 4))
        more.addView(button("Check for update") { checkUpdate() }, lp(top = 8))
        more.addView(text("Version ${versionLabel()}", 12f, muted).apply { setPadding(0, dp(8), 0, 0) })

        initSelection()
        renderAll()
        askBasicPermissions()
    }

    // ---------- rendering ----------

    private fun upcomingMonday(): LocalDate {
        val t = LocalDate.now()
        return if (t.dayOfWeek.value >= 6) t.plusDays((8 - t.dayOfWeek.value).toLong()) else t.with(DayOfWeek.MONDAY)
    }

    /** Opens the timetable on the next school day. */
    private fun initSelection() {
        var d = LocalDate.now()
        if (LocalTime.now().isAfter(LocalTime.of(17, 55))) d = d.plusDays(1)
        while (d.dayOfWeek.value > 5) d = d.plusDays(1)
        selDay = d.dayOfWeek.value
        selWeek = Timetable.weekOf(d, p.anchorEpochDay)
    }

    private fun renderAll() {
        (schoolLine.tag as TextView).text =
            if (p.schoolLat.isNaN()) "Not chosen yet" else p.schoolAddress
        schoolLine.text = if (p.schoolLat.isNaN()) "Choose your school to start" else p.schoolAddress
        homeLine.text = if (p.homeLat.isNaN()) "Not set yet" else "Saved ✓"
        planText.text = p.lastResult.ifEmpty { "Choose your school and home, then tap “Save & plan my alarms”." }

        chips(dayRow, days, selDay - 1) { selDay = it + 1; renderAll() }
        chips(weekRow, listOf("Week A", "Week B"), if (selWeek == 'A') 0 else 1) { selWeek = if (it == 0) 'A' else 'B'; renderAll() }
        chips(modeRow, listOf("On foot", "Scooter"), if (scooter) 1 else 0) { scooter = it == 1; renderAll() }

        val ref = upcomingMonday()
        anchorLabel.text = "Week of ${ref.format(DateTimeFormatter.ofPattern("EEE d MMM"))} is…"
        val cur = Timetable.weekOf(ref, p.anchorEpochDay)
        chips(anchorRow, listOf("Week A", "Week B"), if (cur == 'A') 0 else 1) { i ->
            p.anchorEpochDay = if (i == 0) ref.toEpochDay() else ref.minusDays(7).toEpochDay()
            initSelection()
            renderAll()
        }
        renderLessons()
    }

    private fun renderLessons() {
        lessonsBox.removeAllViews()
        val list = Timetable.lessonsFor(selDay, selWeek)
        if (list.isEmpty()) {
            lessonsBox.addView(text("No classes 🎉", 15f, muted).apply { setPadding(0, dp(12), 0, dp(4)) })
            return
        }
        for (l in list) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(7), 0, dp(7))
            }
            row.addView(text("${l.start}\n${l.end}", 13f, muted), LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT))
            val bar = View(this).apply { background = rounded(Timetable.color(l.subject), 4) }
            row.addView(bar, LinearLayout.LayoutParams(dp(6), ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(dp(4), 0, dp(12), 0) })
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(text(l.subject, 16f, ink, true))
            val sub = listOf(l.room, l.teacher, l.group).filter { it.isNotBlank() }.joinToString(" · ")
            info.addView(text(sub, 13f, muted))
            row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            lessonsBox.addView(row)
        }
    }

    private fun versionLabel(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }

    @Suppress("DEPRECATION")
    private fun versionCode(): Long = try {
        val i = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) i.longVersionCode else i.versionCode.toLong()
    } catch (e: Exception) { 0L }

    // ---------- permissions ----------

    private fun hasLocation() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun askBasicPermissions() {
        val need = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (!hasLocation()) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    private fun askBackgroundLocation() {
        if (!hasLocation()) { askBasicPermissions(); return }
        if (Build.VERSION.SDK_INT >= 29) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
        }
    }

    // ---------- location ----------

    private fun withLocation(then: (Double, Double) -> Unit) {
        if (!hasLocation()) {
            askBasicPermissions()
            Toast.makeText(this, "Allow location, then tap again", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Getting your location…", Toast.LENGTH_SHORT).show()
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc == null) Toast.makeText(this, "Couldn't get your location", Toast.LENGTH_SHORT).show()
                else then(loc.latitude, loc.longitude)
            }
            .addOnFailureListener { Toast.makeText(this, "Couldn't get your location", Toast.LENGTH_SHORT).show() }
    }

    private fun setHomeHere() = withLocation { lat, lon ->
        p.homeLat = lat
        p.homeLon = lon
        renderAll()
        Toast.makeText(this, "Home saved", Toast.LENGTH_SHORT).show()
    }

    // ---------- school ----------

    private fun findNearby() = withLocation { lat, lon ->
        background("Looking for schools nearby…") {
            val list = SchoolFinder.nearby(lat, lon)
            runOnUiThread { showSchools(list, "No schools found within 4 km. Try searching by name.") }
        }
    }

    private fun searchByName() {
        val input = EditText(this).apply { setText("Honoré de Balzac, Casablanca") }
        AlertDialog.Builder(this)
            .setTitle("School name and city")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) background("Searching…") {
                    val list = SchoolFinder.search(q)
                    runOnUiThread { showSchools(list, "Nothing found. Try another spelling or add the city.") }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSchools(list: List<School>, emptyMsg: String) {
        if (list.isEmpty()) {
            Toast.makeText(this, emptyMsg, Toast.LENGTH_LONG).show()
            return
        }
        val labels = list.map { s ->
            s.name + (s.distM?.let { "  (${if (it >= 1000) "%.1f km".format(it / 1000.0) else "$it m"})" } ?: "")
        }
        AlertDialog.Builder(this)
            .setTitle("Pick your school")
            .setItems(labels.toTypedArray()) { _, i ->
                val s = list[i]
                p.schoolAddress = s.name
                p.schoolLat = s.lat
                p.schoolLon = s.lon
                renderAll()
            }
            .show()
    }

    // ---------- planning ----------

    private fun saveAndPlan() {
        p.mode = if (scooter) "scooter" else "foot"
        p.scooterKmh = speed.text.toString().toIntOrNull() ?: 15
        p.trafficPct = traffic.text.toString().toIntOrNull() ?: 15
        p.bufferMin = buffer.text.toString().toIntOrNull() ?: 5
        p.prepMin = prep.text.toString().toIntOrNull() ?: 10
        p.radiusM = radius.text.toString().toIntOrNull() ?: 300
        planText.text = "Planning…"
        Thread {
            val msg = Planner.run(this)
            Planner.schedule(this)
            runOnUiThread { renderAll(); planText.text = msg }
        }.start()
    }

    // ---------- updates ----------

    private fun checkUpdate() {
        background("Checking…") {
            var msg: String
            var url: String? = null
            try {
                val tag = JSONObject(Net.get("https://api.github.com/repos/$repo/releases/latest")).getString("tag_name")
                val latest = tag.filter { it.isDigit() }.toLongOrNull() ?: 0L
                if (latest > versionCode()) {
                    msg = "Update available (build $latest). Downloading…"
                    url = "https://github.com/$repo/releases/latest/download/app-debug.apk"
                } else {
                    msg = "You're up to date."
                }
            } catch (e: Exception) {
                msg = "Couldn't check: ${e.message}"
            }
            val finalMsg = msg
            val finalUrl = url
            runOnUiThread {
                Toast.makeText(this, finalMsg, Toast.LENGTH_LONG).show()
                finalUrl?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
            }
        }
    }

    private fun background(toast: String, work: () -> Unit) {
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show()
        Thread {
            try {
                work()
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
