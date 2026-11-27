package com.example.schoolalarm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var checklist: TextView
    private lateinit var result: TextView
    private lateinit var ics: EditText
    private lateinit var foot: RadioButton
    private lateinit var scooter: RadioButton
    private lateinit var speed: EditText
    private lateinit var traffic: EditText
    private lateinit var buffer: EditText
    private lateinit var prep: EditText
    private lateinit var radius: EditText
    private lateinit var advanced: LinearLayout

    private val repo = "liylounash-afk/SchoolAlarm"

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun heading(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(22), 0, dp(4))
    }

    private fun note(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        alpha = 0.7f
    }

    private fun field(hint: String, value: String, number: Boolean = false) = EditText(this).apply {
        this.hint = hint
        setText(value)
        if (number) inputType = InputType.TYPE_CLASS_NUMBER
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        Notifier.ensureChannels(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(40))
        }
        setContentView(ScrollView(this).apply { addView(col) })

        col.addView(TextView(this).apply {
            text = "School Alarm"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })

        result = TextView(this).apply {
            textSize = 17f
            setPadding(0, dp(10), 0, dp(6))
        }
        col.addView(result)
        checklist = TextView(this).apply { textSize = 15f; setPadding(0, 0, 0, dp(4)) }
        col.addView(checklist)

        // 1. Pronote
        col.addView(heading("1. Pronote"))
        col.addView(note("Paste your Pronote calendar link (iCal / webcal)."))
        ics = field("https://… or webcal://…", p.icsUrl)
        col.addView(ics)

        // 2. School
        col.addView(heading("2. School"))
        col.addView(button("Find my school near me") { findNearby() })
        col.addView(button("Search a school by name") { searchByName() })

        // 3. Home
        col.addView(heading("3. Home"))
        col.addView(note("Stand at home, then tap. Alarms only ring when you're near here."))
        col.addView(button("I'm at home now") { setHomeHere() })

        // 4. Way to school
        col.addView(heading("4. How do you go?"))
        val rg = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        foot = RadioButton(this).apply { text = "On foot"; id = View.generateViewId() }
        scooter = RadioButton(this).apply { text = "Scooter"; id = View.generateViewId() }
        rg.addView(foot); rg.addView(scooter)
        (if (p.mode == "scooter") scooter else foot).isChecked = true
        col.addView(rg)

        col.addView(button("Save & plan my alarms") { saveAndPlan() })

        // Advanced
        advanced = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        speed = field("Scooter speed km/h", p.scooterKmh.toString(), true)
        traffic = field("Traffic margin %", p.trafficPct.toString(), true)
        buffer = field("Arrive early (min)", p.bufferMin.toString(), true)
        prep = field("Bag reminder before leaving (min)", p.prepMin.toString(), true)
        radius = field("Ring only within (metres of home)", p.radiusM.toString(), true)
        for ((label, f) in listOf(
            "Scooter speed (km/h)" to speed, "Traffic margin (%)" to traffic,
            "Arrive early (minutes)" to buffer, "Bag/toilet reminder, minutes before leaving" to prep,
            "Only ring within this many metres of home" to radius
        )) {
            advanced.addView(note(label)); advanced.addView(f)
        }
        col.addView(heading("More"))
        col.addView(button("Advanced settings") {
            advanced.visibility = if (advanced.visibility == View.GONE) View.VISIBLE else View.GONE
        })
        col.addView(advanced)
        col.addView(button("Allow location 'all the time'") { askBackgroundLocation() })
        col.addView(button("Check for update") { checkUpdate() })
        col.addView(note("Version ${versionLabel()}"))

        refresh()
        askBasicPermissions()
    }

    private fun versionLabel(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }

    @Suppress("DEPRECATION")
    private fun versionCode(): Long = try {
        val i = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) i.longVersionCode else i.versionCode.toLong()
    } catch (e: Exception) { 0L }

    private fun refresh() {
        val ok = "✓ "
        val no = "○ "
        checklist.text = listOf(
            (if (ics.text.isNotBlank() || p.icsUrl.isNotBlank()) ok else no) + "Pronote link",
            (if (!p.schoolLat.isNaN()) ok + "School: ${p.schoolAddress}" else no + "School not chosen"),
            (if (!p.homeLat.isNaN()) ok else no) + "Home location"
        ).joinToString("\n")
        result.text = p.lastResult.ifEmpty { "Do the steps below, then tap “Save & plan”." }
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
        refresh()
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
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) background("Searching…") {
                    val list = SchoolFinder.search(text)
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
        val labels = list.map { s -> s.name + (s.distM?.let { "  (${if (it >= 1000) "%.1f km".format(it / 1000.0) else "$it m"})" } ?: "") }
        AlertDialog.Builder(this)
            .setTitle("Pick your school")
            .setItems(labels.toTypedArray()) { _, i ->
                val s = list[i]
                p.schoolAddress = s.name
                p.schoolLat = s.lat
                p.schoolLon = s.lon
                refresh()
            }
            .show()
    }

    // ---------- planning ----------

    private fun saveAndPlan() {
        p.icsUrl = ics.text.toString()
        p.mode = if (scooter.isChecked) "scooter" else "foot"
        p.scooterKmh = speed.text.toString().toIntOrNull() ?: 15
        p.trafficPct = traffic.text.toString().toIntOrNull() ?: 15
        p.bufferMin = buffer.text.toString().toIntOrNull() ?: 5
        p.prepMin = prep.text.toString().toIntOrNull() ?: 10
        p.radiusM = radius.text.toString().toIntOrNull() ?: 300
        refresh()
        result.text = "Planning…"
        Thread {
            val msg = Planner.run(this)
            Planner.schedule(this)
            runOnUiThread { result.text = msg; refresh() }
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
