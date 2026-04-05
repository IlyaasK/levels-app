package com.levelsapp

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import android.graphics.Color
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_refresh).setOnClickListener { fetchStats() }
        findViewById<Button>(R.id.btn_settings).setOnClickListener { showSettingsDialog() }

        loadCachedStats()
        fetchStats()
    }

    private fun loadCachedStats() {
        val stats = Scraper.getCachedStats(this)
        if (stats != null) {
            findViewById<TextView>(R.id.tv_total_xp).text = stats.totalDailyXp
            findViewById<TextView>(R.id.tv_go_hours).text = stats.bootdevGoHours
            findViewById<TextView>(R.id.tv_calc2_hours).text = stats.mathacademyCalc2Hours
            findViewById<TextView>(R.id.tv_last_updated).text = "Last updated: \n${stats.lastPolled}"
            setupChart()
        }
    }

    private fun setupChart() {
        val chart = findViewById<LineChart>(R.id.chart_daily_xp)
        val db = getSharedPreferences("levels_db", Context.MODE_PRIVATE)
        val historyStr = db.getString("history_log", "[]") ?: "[]"
        
        val entries = ArrayList<Entry>()
        val labels = ArrayList<String>()

        try {
            val array = JSONArray(historyStr)

            // Include TODAY's current data as the final point!
            val totalDailyXpStr = findViewById<TextView>(R.id.tv_total_xp).text.toString()
            val todaysHours = totalDailyXpStr.toFloatOrNull() ?: 0f

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val hours = item.optDouble("hours", 0.0).toFloat()
                val dateStr = item.optString("date", "")
                
                val shortDate = if (dateStr.length >= 10) dateStr.substring(5) else dateStr
                labels.add(shortDate)
                entries.add(Entry(i.toFloat(), hours))
            }

            labels.add("Today")
            entries.add(Entry(entries.size.toFloat(), todaysHours))

            val dataSet = LineDataSet(entries, "Daily Hours")
            dataSet.color = Color.parseColor("#FFD700")
            dataSet.valueTextColor = Color.WHITE
            dataSet.valueTextSize = 10f
            dataSet.lineWidth = 2.5f
            dataSet.setCircleColor(Color.parseColor("#FFD700"))
            dataSet.circleRadius = 4f
            dataSet.setDrawCircleHole(false)
            dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
            dataSet.setDrawFilled(true)
            dataSet.fillColor = Color.parseColor("#FFD700")
            dataSet.fillAlpha = 50

            val lineData = LineData(dataSet)
            chart.data = lineData

            val xAxis = chart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = Color.parseColor("#AAAAAA")
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val idx = value.toInt()
                    if (idx >= 0 && idx < labels.size) {
                        return labels[idx]
                    }
                    return ""
                }
            }

            chart.axisLeft.textColor = Color.parseColor("#AAAAAA")
            chart.axisLeft.axisMinimum = 0f
            chart.axisRight.isEnabled = false
            chart.legend.textColor = Color.WHITE
            chart.description.isEnabled = false

            chart.animateX(500)
            chart.invalidate()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchStats() {
        findViewById<Button>(R.id.btn_refresh).text = "↻ Polling..."
        CoroutineScope(Dispatchers.IO).launch {
            val stats = Scraper.pollStats(this@MainActivity)
            withContext(Dispatchers.Main) {
                findViewById<Button>(R.id.btn_refresh).text = "↻ Refresh"
                if (stats != null) {
                    loadCachedStats()
                } else {
                    Toast.makeText(this@MainActivity, "Update failed. Check credentials in Settings.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSettingsDialog() {
        val db = getSharedPreferences("levels_db", Context.MODE_PRIVATE)
        val layout = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50) 
        }
        
        val bootdevInput = EditText(this).apply { 
            hint = "Boot.dev Username"
            setText(db.getString("CREDS_BOOTDEV", "")) 
        }
        val maEmailInput = EditText(this).apply { 
            hint = "MathAcademy Email"
            setText(db.getString("CREDS_MA_EMAIL", "")) 
        }
        val maPassInput = EditText(this).apply { 
            hint = "MathAcademy Password"
            setText(db.getString("CREDS_MA_PASS", "")) 
        }
        
        layout.addView(bootdevInput)
        layout.addView(maEmailInput)
        layout.addView(maPassInput)

        AlertDialog.Builder(this)
            .setTitle("Scraper Credentials")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                db.edit()
                    .putString("CREDS_BOOTDEV", bootdevInput.text.toString())
                    .putString("CREDS_MA_EMAIL", maEmailInput.text.toString())
                    .putString("CREDS_MA_PASS", maPassInput.text.toString())
                    .apply()
                fetchStats()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
