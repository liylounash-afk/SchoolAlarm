package com.example.schoolalarm

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
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

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var ics: EditText
    private lateinit var school: EditText
    private lateinit var speed: EditText
    private lateinit var traffic: EditText
    private lateinit var buffer: EditText
    private lateinit var prep: EditText
    private lateinit var radius: EditText
    private lateinit var foot: RadioButton
    private lateinit var scooter: RadioButton
    private lateinit var homeStatus: TextView
    private lateinit var result: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 14f
        setPadding(0, dp(14), 0, dp(2))
    }

    private fun field(hint: String, value: String, number: Boolean = false) = EditText(this).apply {
        this.hint = hint
        setText(value)
        if (number) inputType = InputType.TYPE_CLASS_NUMBER
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

        col.addView(TextView(this).apply { text = "School Alarm"; textSize = 26f })

        result = TextView(this).apply {
            textSize = 16f
            setPadding(0, dp(10), 0, dp(6))
            text = p.lastResult.ifEmpty { "Fill in the settings below, then press Save & plan." }
        }
        col.addView(result)

        col.addView(label("Pronote calendar link (iCal / webcal)"))
        ics = field("https://… or webcal://…", p.icsUrl)
        col.addView(ics)

        col.addView(label("School address"))
        school = field("e.g. 12 rue Victor Hugo, Casablanca", p.schoolAddress)
        col.addView(school)

        col.addView(label("Home"))
        homeStatus = TextView(this)
        col.addView(homeStatus)
        col.addView(Button(this).apply {
            text = "I'm at home now: use my location"
            setOnClickListener { setHomeHere() }
        })

        col.addView(label("How do you go?"))
        val rg = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        foot = RadioButton(this).apply { text = "On foot"; id = View.generateViewId() }
        scooter = RadioButton(this).apply { text = "Scooter"; id = View.generateViewId() }
        rg.addView(foot); rg.addView(scooter)
        (if (p.mode == "scooter") scooter else foot).isChecked = true
        col.addView(rg)

        col.addView(label("Scooter average speed (km/h)"))
        speed = field("15", p.scooterKmh.toString(), true)
        col.addView(speed)

        col.addView(label("Traffic margin (% added to the trip)"))
        traffic = field("15", p.trafficPct.toString(), true)
        col.addView(traffic)

        col.addView(label("Arrive early by (minutes)"))
        buffer = field("5", p.bufferMin.toString(), true)
        col.addView(buffer)

        col.addView(label("Bag/toilet reminder, minutes before leaving"))
        prep = field("10", p.prepMin.toString(), true)
        col.addView(prep)

        col.addView(label("Only ring if I'm within this many metres of home"))
        radius = field("300", p.radiusM.toString(), true)
        col.addView(radius)

        col.addView(Button(this).apply {
            text = "Save & plan"
            setOnClickListener { saveAndPlan() }
        })

        col.addView(label("Full alarm permissions"))
        col.addView(Button(this).apply {
            text = "Allow location 'all the time' (for the at-home check)"
            setOnClickListener { askBackgroundLocation() }
        })

        refreshHome()
        askBasicPermissions()
    }

    private fun refreshHome() {
        homeStatus.text = if (p.homeLat.isNaN()) "Not set yet" else "Saved (%.4f, %.4f)".format(p.homeLat, p.homeLon)
    }

    private fun askBasicPermissions() {
        val need = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    private fun askBackgroundLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            askBasicPermissions()
            return
        }
        if (Build.VERSION.SDK_INT >= 29) {
            // On Android 11+ this opens the settings page where you pick "Allow all the time".
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
        }
    }

    private fun setHomeHere() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            askBasicPermissions()
            Toast.makeText(this, "Allow location, then press again", Toast.LENGTH_SHORT).show()
            return
        }
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc == null) {
                    Toast.makeText(this, "Couldn't get your location", Toast.LENGTH_SHORT).show()
                } else {
                    p.homeLat = loc.latitude
                    p.homeLon = loc.longitude
                    refreshHome()
                }
            }
    }

    private fun saveAndPlan() {
        p.icsUrl = ics.text.toString()
        p.schoolAddress = school.text.toString()
        p.mode = if (scooter.isChecked) "scooter" else "foot"
        p.scooterKmh = speed.text.toString().toIntOrNull() ?: 15
        p.trafficPct = traffic.text.toString().toIntOrNull() ?: 15
        p.bufferMin = buffer.text.toString().toIntOrNull() ?: 5
        p.prepMin = prep.text.toString().toIntOrNull() ?: 10
        p.radiusM = radius.text.toString().toIntOrNull() ?: 300

        result.text = "Planning…"
        Thread {
            // Look up the school's coordinates when the address is new
            val addr = p.schoolAddress
            if (addr.isNotBlank()) {
                try {
                    Net.geocode(addr)?.let { p.schoolLat = it.first; p.schoolLon = it.second }
                } catch (_: Exception) {
                }
            }
            val msg = Planner.run(this)
            Planner.schedule(this)
            runOnUiThread { result.text = msg }
        }.start()
    }
}
