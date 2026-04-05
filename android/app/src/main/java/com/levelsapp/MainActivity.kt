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
import org.json.JSONObject
import android.net.Uri
import android.os.Environment
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_refresh).setOnClickListener { fetchStats() }
        findViewById<Button>(R.id.btn_settings).setOnClickListener { showSettingsDialog() }

        loadCachedStats()
        fetchStats()
        checkForUpdates()
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

    private fun checkForUpdates() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/IlyaasK/levels-app/releases/tags/latest")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                val response = OkHttpClient().newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    val publishedAt = json.optString("published_at", "")
                    val assets = json.optJSONArray("assets")
                    var downloadUrl = ""
                    
                    if (assets != null && assets.length() > 0) {
                        downloadUrl = assets.getJSONObject(0).optString("browser_download_url", "")
                    }

                    val db = getSharedPreferences("levels_db", Context.MODE_PRIVATE)
                    val savedDate = db.getString("installed_apk_date", "")

                    if (savedDate == "") {
                        // First run, lock in the current date
                        db.edit().putString("installed_apk_date", publishedAt).apply()
                    } else if (publishedAt != "" && publishedAt != savedDate && downloadUrl.isNotEmpty()) {
                        // An update is available!
                        withContext(Dispatchers.Main) {
                            showUpdateDialog(downloadUrl, publishedAt)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showUpdateDialog(downloadUrl: String, newPublishedAt: String) {
        AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage("A newer version of Levels App was published to GitHub! Would you like to update now?")
            .setPositiveButton("Download & Install") { _, _ ->
                downloadAndInstallApk(downloadUrl, newPublishedAt)
            }
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show()
    }

    private fun downloadAndInstallApk(apkUrl: String, newPublishedAt: String) {
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Levels App Update")
            .setDescription("Downloading latest APK from GitHub...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "levels-app-update.apk")

        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    getSharedPreferences("levels_db", Context.MODE_PRIVATE).edit().putString("installed_apk_date", newPublishedAt).apply()
                    installApk()
                    unregisterReceiver(this)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        Toast.makeText(this, "Downloading update in background...", Toast.LENGTH_SHORT).show()
    }

    private fun installApk() {
        try {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "levels-app-update.apk")
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(this, "com.levelsapp.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to launch installer automatically. Tap the download notification instead.", Toast.LENGTH_LONG).show()
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
