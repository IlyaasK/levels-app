package com.levelsapp

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val API_URL = "http://77.42.82.186:8181/api/stats"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnRefresh = findViewById<Button>(R.id.btn_refresh)
        btnRefresh.setOnClickListener { fetchStats() }

        fetchStats()
    }

    private fun fetchStats() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(API_URL).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    val totalXp = json.optInt("totalDailyXp", 0).toString()
                    val goHours = json.optJSONObject("bootdev")?.optString("goHours", "--") ?: "--"
                    val calc2Hours = json.optJSONObject("mathacademy")?.optString("calc2Hours", "--") ?: "--"
                    val lastPolled = json.optString("lastPolled", "")

                    withContext(Dispatchers.Main) {
                        findViewById<TextView>(R.id.tv_total_xp).text = totalXp
                        findViewById<TextView>(R.id.tv_go_hours).text = goHours
                        findViewById<TextView>(R.id.tv_calc2_hours).text = calc2Hours
                        if (lastPolled.isNotEmpty()) {
                            try {
                                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                                val date = sdf.parse(lastPolled)
                                val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
                                findViewById<TextView>(R.id.tv_last_updated).text = "Last updated: ${fmt.format(date!!)}"
                            } catch (_: Exception) {
                                findViewById<TextView>(R.id.tv_last_updated).text = "Last updated: $lastPolled"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Could not reach server", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
