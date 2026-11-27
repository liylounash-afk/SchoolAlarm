package com.example.schoolalarm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private lateinit var p: Prefs

    private lateinit var nextBox: LinearLayout
    private lateinit var alarmsBox: LinearLayout
    private lateinit var dayRow: LinearLayout
    private lateinit var weekRow: LinearLayout
    private lateinit var lessonsBox: LinearLayout
    private lateinit var subtitle: TextView

    private lateinit var setupBody: LinearLayout
    private lateinit var setupArrow: TextView
    private lateinit var setupSummary: TextView
    private lateinit var schoolName: TextView
    private lateinit var homeLine: TextView
    private lateinit var modeRow: LinearLayout
    private lateinit var anchorLabel: TextView
    private lateinit var anchorRow: LinearLayout
    private lateinit var speed: EditText
    private lateinit var traffic: EditText
    private lateinit var buffer: EditText
    private lateinit var radius: EditText

    private var selDay = 1
    private var selWeek = 'A'
    private var scooter = false

    private val repo = "liylounash-afk/SchoolAlarm"
    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    private val clockFmt = DateTimeFormatter.ofPattern("HH:mm")

    // Colours
    private val bg = 0xFFF2F4F8.toInt()
    private val ink = 0xFF18202B.toInt()
    private val muted = 0xFF6A7584.toInt()
    private val accent = 0xFF3A6DF0.toInt()
    private val accentSoft = 0xFFE7EEFF.toInt()
    private val chipBg = 0xFFE9EDF3.toInt()
    private val line = 0xFFE8ECF1.toInt()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }

    private fun weight(left: Int = 0, right: Int = 0) =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(left), 0, dp(right), 0) }

    private fun text(t: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun card(parent: LinearLayout, fill: Drawable? = null): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = fill ?: rounded(Color.WHITE, 20)
            elevation = dp(2).toFloat()
            setPadding(dp(18), dp(16), dp(18), dp(18))
        }
        parent.addView(c, lp(top = 14))
        return c
    }

    private fun button(label: String, filled: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(if (filled) Color.WHITE else accent)
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(if (filled) accent else accentSoft, 14)
        setPadding(dp(12), dp(13), dp(12), dp(13))
        setOnClickListener { onClick() }
    }

    private fun smallChip(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(accent)
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(accentSoft, 12)
        setPadding(dp(12), dp(7), dp(12), dp(7))
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
            row.addView(chip, weight(3, 3))
        }
    }

    private fun numberField(hint: String, value: String) = EditText(this).apply {
        this.hint = hint
        setText(value)
        inputType = InputType.TYPE_CLASS_NUMBER
        textSize = 15f
    }

    private fun clock(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(clockFmt)

    // ------------------------------------------------------------------

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        Notifier.ensureChannels(this)
        scooter = p.mode == "scooter"

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(22), dp(16), dp(48))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            clipToPadding = false
            addView(col)
        })

        col.addView(text("School Alarm", 30f, ink, true))
        subtitle = text("", 14f, muted)
        col.addView(subtitle)

        // --- Next alarms (gradient hero card) ---
        val hero = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF3A6DF0.toInt(), 0xFF6B4EF3.toInt())).apply {
            cornerRadius = dp(24).toFloat()
        }
        val heroCard = card(col, hero)
        heroCard.setPadding(dp(20), dp(18), dp(20), dp(20))
        nextBox = heroCard

        // --- Alarms ---
        val alarms = card(col)
        alarms.addView(text("Alarms", 18f, ink, true))
        alarms.addView(text("Tap an alarm to change its sound. Test plays it right now.", 13f, muted).apply { setPadding(0, dp(2), 0, dp(4)) })
        alarmsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        alarms.addView(alarmsBox)

        // --- Timetable ---
        val tt = card(col)
        tt.addView(text("Timetable", 18f, ink, true))
        dayRow = LinearLayout(this)
        tt.addView(dayRow, lp(top = 12))
        weekRow = LinearLayout(this)
        tt.addView(weekRow, lp(top = 8))
        lessonsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tt.addView(lessonsBox, lp(top = 8))

        // --- Setup (collapsed once it is done) ---
        val setup = card(col)
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { toggleSetup() }
        }
        head.addView(text("Setup", 18f, ink, true), weight())
        setupArrow = text("Edit  ▾", 14f, accent, true)
        head.addView(setupArrow)
        setup.addView(head)
        setupSummary = text("", 13f, muted).apply { setPadding(0, dp(4), 0, 0) }
        setup.addView(setupSummary)

        setupBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setup.addView(setupBody)

        setupBody.addView(text("School", 13f, muted).apply { setPadding(0, dp(14), 0, 0) })
        schoolName = text("", 16f, ink, true)
        setupBody.addView(schoolName)
        val schoolBtns = LinearLayout(this)
        schoolBtns.addView(button("Find near me") { findNearby() }, weight(0, 6))
        schoolBtns.addView(button("Search name") { searchByName() }, weight(6, 0))
        setupBody.addView(schoolBtns, lp(top = 8))

        setupBody.addView(text("Home", 13f, muted).apply { setPadding(0, dp(16), 0, 0) })
        homeLine = text("", 16f, ink, true)
        setupBody.addView(homeLine)
        setupBody.addView(button("I'm at home now") { setHomeHere() }, lp(top = 8))

        setupBody.addView(text("I go to school", 13f, muted).apply { setPadding(0, dp(16), 0, 0) })
        modeRow = LinearLayout(this)
        setupBody.addView(modeRow, lp(top = 6))

        anchorLabel = text("", 13f, muted).apply { setPadding(0, dp(16), 0, 0) }
        setupBody.addView(anchorLabel)
        anchorRow = LinearLayout(this)
        setupBody.addView(anchorRow, lp(top = 6))

        speed = numberField("15", p.scooterKmh.toString())
        traffic = numberField("15", p.trafficPct.toString())
        buffer = numberField("5", p.bufferMin.toString())
        radius = numberField("300", p.radiusM.toString())
        for ((label, f) in listOf(
            "Scooter speed (km/h)" to speed,
            "Traffic margin (% added to the trip)" to traffic,
            "Arrive early (minutes)" to buffer,
            "Only ring within this many metres of home" to radius
        )) {
            setupBody.addView(text(label, 13f, muted).apply { setPadding(0, dp(12), 0, 0) })
            setupBody.addView(f)
        }
        setupBody.addView(button("Save & update alarms", true) { savePrefs(); replan("Alarms updated") }, lp(top = 16))
        setupBody.addView(button("Allow location 'all the time'") { askBackgroundLocation() }, lp(top = 8))
        setupBody.addView(button("Check for update") { checkUpdate() }, lp(top = 8))
        setupBody.addView(text("Version ${versionLabel()}", 12f, muted).apply { setPadding(0, dp(8), 0, 0) })

        setupBody.visibility = if (needsSetup()) View.VISIBLE else View.GONE

        initSelection()
        renderAll()
        askBasicPermissions()
        Planner.schedule(this)
    }

    private fun needsSetup() = p.schoolLat.isNaN() || p.homeLat.isNaN()

    private fun toggleSetup() {
        setupBody.visibility = if (setupBody.visibility == View.GONE) View.VISIBLE else View.GONE
        renderSetupHeader()
    }

    private fun renderSetupHeader() {
        val open = setupBody.visibility == View.VISIBLE
        setupArrow.text = if (open) "Close  ▴" else "Edit  ▾"
        setupSummary.visibility = if (open) View.GONE else View.VISIBLE
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
        val schoolSet = !p.schoolLat.isNaN()
        subtitle.text = if (schoolSet) p.schoolAddress else "Open Setup to choose your school"
        schoolName.text = if (schoolSet) p.schoolAddress else "Not chosen yet"
        homeLine.text = if (p.homeLat.isNaN()) "Not set yet" else "Saved ✓"
        setupSummary.text = listOf(
            if (schoolSet) p.schoolAddress else "No school yet",
            if (p.homeLat.isNaN()) "no home" else "home saved",
            if (scooter) "scooter" else "on foot"
        ).joinToString(" · ")
        renderSetupHeader()

        chips(modeRow, listOf("On foot", "Scooter"), if (scooter) 1 else 0) { scooter = it == 1; savePrefs(); renderAll(); replan(null) }

        val ref = upcomingMonday()
        anchorLabel.text = "Week of ${ref.format(DateTimeFormatter.ofPattern("EEE d MMM"))} is…"
        val cur = Timetable.weekOf(ref, p.anchorEpochDay)
        chips(anchorRow, listOf("Week A", "Week B"), if (cur == 'A') 0 else 1) { i ->
            p.anchorEpochDay = if (i == 0) ref.toEpochDay() else ref.minusDays(7).toEpochDay()
            initSelection()
            renderAll()
            replan(null)
        }

        chips(dayRow, days, selDay - 1) { selDay = it + 1; renderAll() }
        chips(weekRow, listOf("Week A", "Week B"), if (selWeek == 'A') 0 else 1) { selWeek = if (it == 0) 'A' else 'B'; renderAll() }

        renderNext()
        renderAlarms()
        renderLessons()
    }

    private fun renderNext() {
        nextBox.removeAllViews()
        val now = System.currentTimeMillis()
        nextBox.addView(text("NEXT ALARMS", 12f, 0xB3FFFFFF.toInt(), true))

        if (p.lastResult.isNotEmpty()) {
            nextBox.addView(text(p.lastResult, 18f, Color.WHITE, true).apply { setPadding(0, dp(8), 0, 0) })
            return
        }
        if (p.leaveAtMillis <= 0L && p.lunchAtMillis <= 0L) {
            nextBox.addView(text("Open Setup, choose your school and home, and your alarms appear here.", 16f, Color.WHITE, true).apply { setPadding(0, dp(8), 0, 0) })
            return
        }
        if (p.leaveAtMillis > now) {
            nextBox.addView(text("${p.planDay} · first class ${p.planFirst}", 20f, Color.WHITE, true).apply { setPadding(0, dp(6), 0, 0) })
            nextBox.addView(text(p.planTrip, 13f, 0xCCFFFFFF.toInt()))
        }
        fun row(icon: String, millis: Long, name: String, day: String) {
            if (millis <= now) return
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(text(icon, 22f), LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT))
            r.addView(text(clock(millis), 28f, Color.WHITE, true), LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT))
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(text(name, 15f, Color.WHITE, true))
            if (day.isNotBlank()) c.addView(text(day, 12f, 0xB3FFFFFF.toInt()))
            r.addView(c)
            nextBox.addView(r, lp(top = 12))
        }
        if (p.alarmEnabled("pre")) row("🎒", p.preAtMillis, "Bag & toilet check", p.planDay)
        if (p.alarmEnabled("leave")) row("🚪", p.leaveAtMillis, "Time to leave", p.planDay)
        if (p.alarmEnabled("lunch")) row("🍽", p.lunchAtMillis, "Back to school after lunch", p.lunchDay)
    }

    private fun alarmSummary(kind: String): String {
        val sound = Sounds.label(p.alarmSound(kind))
        val what = when (kind) {
            "pre" -> "${p.prepMin} min before leaving"
            "leave" -> "When it's time to go"
            else -> "Back to school after lunch"
        }
        return "$what · $sound" + if (p.alarmVibrate(kind)) " · vibrate" else ""
    }

    private fun renderAlarms() {
        alarmsBox.removeAllViews()
        val items = listOf(
            Triple("pre", "🎒", "Bag & toilet check"),
            Triple("leave", "🚪", "Time to leave"),
            Triple("lunch", "🍽", "Lunch break → back to school")
        )
        items.forEachIndexed { i, (kind, icon, name) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(12))
                setOnClickListener { editAlarm(kind) }
            }
            row.addView(text(icon, 24f), LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT))
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(text(name, 16f, ink, true))
            info.addView(text(alarmSummary(kind), 13f, muted))
            row.addView(info, weight())
            row.addView(smallChip("Test") { testAlarm(kind, p.alarmSound(kind), p.alarmVibrate(kind)) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), 0, dp(10), 0) })
            val sw = Switch(this).apply {
                isChecked = p.alarmEnabled(kind)
                setOnCheckedChangeListener { _, on -> p.setAlarmEnabled(kind, on); replan(null); renderNext() }
            }
            row.addView(sw)
            alarmsBox.addView(row)
            if (i < items.size - 1) alarmsBox.addView(View(this).apply { setBackgroundColor(line) }, lp(h = dp(1)))
        }
    }

    private fun minutesOf(t: String): Int {
        val (h, m) = t.split(":")
        return h.toInt() * 60 + m.toInt()
    }

    private fun renderLessons() {
        lessonsBox.removeAllViews()
        val list = Timetable.lessonsFor(selDay, selWeek)
        if (list.isEmpty()) {
            lessonsBox.addView(text("No classes 🎉", 15f, muted).apply { setPadding(0, dp(12), 0, dp(4)) })
            return
        }
        list.forEachIndexed { i, l ->
            if (i > 0) {
                val gap = minutesOf(l.start) - minutesOf(list[i - 1].end)
                if (gap >= 60) addBreakRow(list[i - 1].end, l.start, gap)
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(7), 0, dp(7))
            }
            row.addView(text("${l.start}\n${l.end}", 13f, muted), LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT))
            val bar = View(this).apply { background = rounded(Timetable.color(l.subject), 4) }
            row.addView(bar, LinearLayout.LayoutParams(dp(6), ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(dp(4), 0, dp(12), 0) })
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(text(l.subject, 16f, ink, true))
            info.addView(text(listOf(l.room, l.teacher, l.group).filter { it.isNotBlank() }.joinToString(" · "), 13f, muted))
            row.addView(info, weight())
            lessonsBox.addView(row)
        }
    }

    /** A long gap between two classes (lunch). Tap it to set the "back to school" alarm. */
    private fun addBreakRow(from: String, to: String, gap: Int) {
        val lunch = minutesOf(from) >= 12 * 60 && minutesOf(to) <= 14 * 60
        val name = if (lunch) "Lunch break" else "Free period"
        val h = gap / 60
        val m = gap % 60
        val dur = if (m == 0) "${h}h" else "${h}h${"%02d".format(m)}"
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(if (lunch) accentSoft else chipBg, 14)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        box.addView(text(if (lunch) "🍽" else "☕", 20f), LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(text("$name · $dur", 15f, ink, true))
        info.addView(text("$from – $to", 13f, muted))
        box.addView(info, weight())
        if (lunch) {
            val on = p.alarmEnabled("lunch")
            box.addView(text(if (on) "🔔 Alarm on" else "🔕 Set alarm", 13f, accent, true))
            box.setOnClickListener { editAlarm("lunch") }
        }
        lessonsBox.addView(box, lp(top = 6).apply { bottomMargin = dp(6) })
    }

    private fun versionLabel(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }

    @Suppress("DEPRECATION")
    private fun versionCode(): Long = try {
        val i = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) i.longVersionCode else i.versionCode.toLong()
    } catch (e: Exception) { 0L }

    // ---------- alarm editor + test ----------

    private fun testAlarm(kind: String, sound: String, vibrate: Boolean) {
        Toast.makeText(this, "Playing test…", Toast.LENGTH_SHORT).show()
        Thread {
            Notifier.fire(this, kind, p, true)
            Sounds.play(this, sound, vibrate)
        }.start()
    }

    private fun editAlarm(kind: String) {
        val title = when (kind) {
            "pre" -> "🎒 Bag & toilet check"
            "leave" -> "🚪 Time to leave"
            else -> "🍽 Back to school after lunch"
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(0))
        }
        val on = Switch(this).apply { text = "Alarm on"; isChecked = p.alarmEnabled(kind); textSize = 16f }
        box.addView(on)

        var minutes: EditText? = null
        if (kind == "pre") {
            box.addView(text("Minutes before leaving", 13f, muted).apply { setPadding(0, dp(14), 0, 0) })
            minutes = numberField("10", p.prepMin.toString())
            box.addView(minutes)
        }

        box.addView(text("Sound", 13f, muted).apply { setPadding(0, dp(14), 0, dp(4)) })
        val group = RadioGroup(this)
        val ids = HashMap<String, Int>()
        for (key in Sounds.KEYS) {
            val rb = RadioButton(this).apply {
                text = Sounds.describe(key)
                id = View.generateViewId()
                textSize = 15f
            }
            ids[key] = rb.id
            group.addView(rb)
        }
        group.check(ids[p.alarmSound(kind)] ?: ids.getValue("ting"))
        box.addView(group)

        val vib = CheckBox(this).apply { text = "Vibrate"; isChecked = p.alarmVibrate(kind); textSize = 15f }
        box.addView(vib)

        fun chosenSound(): String = ids.entries.firstOrNull { it.value == group.checkedRadioButtonId }?.key ?: "ting"

        val dlg = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                p.setAlarmEnabled(kind, on.isChecked)
                p.setAlarmSound(kind, chosenSound())
                p.setAlarmVibrate(kind, vib.isChecked)
                minutes?.let { p.prepMin = it.text.toString().toIntOrNull() ?: 10 }
                replan("Saved")
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Test", null)
            .create()
        dlg.show()
        // Keep the dialog open when testing
        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            testAlarm(kind, chosenSound(), vib.isChecked)
        }
    }

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
        replan("Home saved")
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
                replan("School saved")
            }
            .show()
    }

    // ---------- planning ----------

    private fun savePrefs() {
        p.mode = if (scooter) "scooter" else "foot"
        p.scooterKmh = speed.text.toString().toIntOrNull() ?: 15
        p.trafficPct = traffic.text.toString().toIntOrNull() ?: 15
        p.bufferMin = buffer.text.toString().toIntOrNull() ?: 5
        p.radiusM = radius.text.toString().toIntOrNull() ?: 300
    }

    /** Recomputes and reschedules the alarms in the background, then refreshes the screen. */
    private fun replan(done: String?) {
        Thread {
            Planner.run(this)
            runOnUiThread {
                if (done != null) Toast.makeText(this, done, Toast.LENGTH_SHORT).show()
                renderAll()
            }
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
