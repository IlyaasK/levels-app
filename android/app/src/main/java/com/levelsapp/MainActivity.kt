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
