package com.levelsapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Scraper {

    private val cookieStore = HashMap<String, MutableList<Cookie>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies.toMutableList()
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: ArrayList()
            }
        })
        .build()

    data class Stats(
        val totalDailyXp: String,
        val bootdevGoHours: String,
        val mathacademyCalc2Hours: String,
        val lastPolled: String
    )

    suspend fun pollStats(context: Context): Stats? = withContext(Dispatchers.IO) {
        try {
            val db = context.getSharedPreferences("levels_db", Context.MODE_PRIVATE)
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

            val bootdevUser = db.getString("CREDS_BOOTDEV", "") ?: ""
            val maEmail = db.getString("CREDS_MA_EMAIL", "") ?: ""
            val maPass = db.getString("CREDS_MA_PASS", "") ?: ""

            if (bootdevUser.isEmpty() || maEmail.isEmpty() || maPass.isEmpty()) {
                throw Exception("Missing set credentials")
            }

            // 1. Scrape Bootdev
            var bXp = 0
            val bReq = Request.Builder().url("https://boot.dev/u/${bootdevUser}").build()
            val bRes = client.newCall(bReq).execute()
            bRes.body?.string()?.let { html ->
                val doc = Jsoup.parse(html)
                val text = doc.body().text().replace(Regex("\\s+"), " ")
                
                val match = Regex("([0-9,]+)\\s*XP", RegexOption.IGNORE_CASE).find(text)
                if (match != null) {
                    bXp = match.groupValues[1].replace(",", "").toIntOrNull() ?: 0
                } else {
                    val crsMatch = Regex("(\\d+)\\s*Courses Completed", RegexOption.IGNORE_CASE).find(text)
                    if (crsMatch != null) {
                        bXp = (crsMatch.groupValues[1].toIntOrNull() ?: 0) * 1000
                    }
                }
            }

            // 2. Scrape MathAcademy
            var mDaily = 0
            var mTotal = 0
            val loginGetReq = Request.Builder().url("https://mathacademy.com/login").build()
            val loginGetRes = client.newCall(loginGetReq).execute()
            loginGetRes.body?.string()?.let { html ->
                val doc = Jsoup.parse(html)
                val formBody = FormBody.Builder()
                doc.select("form[action=\"/login\"] input[type=hidden]").forEach { input ->
                    formBody.add(input.attr("name"), input.attr("value"))
                }
                formBody.add("usernameOrEmail", maEmail)
                formBody.add("password", maPass)
                formBody.add("submit", "LOGIN")

                val loginPostReq = Request.Builder()
                    .url("https://mathacademy.com/login")
                    .post(formBody.build())
                    .addHeader("Referer", "https://mathacademy.com/login")
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build()
                val loginPostRes = client.newCall(loginPostReq).execute()
                val dashHtml = loginPostRes.body?.string() ?: ""
                
                val dashDoc = Jsoup.parse(dashHtml)
                val text = dashDoc.body().text().replace(Regex("\\s+"), " ")

                val todayExact = Regex("TODAY[^0-9a-z]*(\\d+)(?:/\\d+)?\\s*XP", RegexOption.IGNORE_CASE).find(text)
                if (todayExact != null) mDaily = todayExact.groupValues[1].replace(",", "").toInt()
                else {
                    val todayFb = Regex("today[^0-9]{0,20}([0-9,]+)", RegexOption.IGNORE_CASE).find(text)
                    if (todayFb != null) mDaily = todayFb.groupValues[1].replace(",", "").toInt()
                }

                val totalExact = Regex("TOTAL\\s*EARNED[^0-9a-z]*([0-9,]+)\\s*XP", RegexOption.IGNORE_CASE).find(text)
                if (totalExact != null) mTotal = totalExact.groupValues[1].replace(",", "").toInt()
                else {
                    val generic = Regex("(?:^|\\s)([0-9,]+)\\s*XP", RegexOption.IGNORE_CASE).find(text)
                    if (generic != null) mTotal = generic.groupValues[1].replace(",", "").toInt()
                }
            }

            // Calc processDailyStats for bootdev
            val bLastDate = db.getString("bootdev_date", "")
            var bStartTotal = db.getInt("bootdev_start", bXp)
            var bCurrentTotal = db.getInt("bootdev_current", bXp)

            if (bLastDate == "") {
                db.edit().putString("bootdev_date", today)
                    .putInt("bootdev_start", bXp)
                    .putInt("bootdev_current", bXp).apply()
            } else if (bLastDate != today) {
                bStartTotal = if (bCurrentTotal > 0) bCurrentTotal else bXp
                db.edit().putString("bootdev_date", today)
                    .putInt("bootdev_start", bStartTotal).apply()
            }

            db.edit().putInt("bootdev_current", bXp).apply()

            val bDaily = java.lang.Math.max(0, bXp - bStartTotal)
            val totalDailyXpMins = bDaily + mDaily
            val totalDailyXpHours = String.format(Locale.getDefault(), "%.1f", totalDailyXpMins / 60.0)

            val calc2Hours = if (mTotal > 0) String.format(Locale.getDefault(), "%.1f", (15404.0 - mTotal) / 60.0) else "0.0"
            val goHours = if (bXp > 0) String.format(Locale.getDefault(), "%.1f", (1030383.0 - bXp) / 1800.0) else "0.0"

            val lastPolled = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(Date())

            val finalStats = Stats(totalDailyXpHours, goHours, calc2Hours, lastPolled)

            db.edit()
                .putString("CACHE_totalDailyXp", finalStats.totalDailyXp)
                .putString("CACHE_bootdevGoHours", finalStats.bootdevGoHours)
                .putString("CACHE_mathacademyCalc2Hours", finalStats.mathacademyCalc2Hours)
                .putString("CACHE_lastPolled", finalStats.lastPolled)
                .apply()

            finalStats
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getCachedStats(context: Context): Stats? {
        val db = context.getSharedPreferences("levels_db", Context.MODE_PRIVATE)
        if (!db.contains("CACHE_totalDailyXp")) return null
        return Stats(
            db.getString("CACHE_totalDailyXp", "0")!!,
            db.getString("CACHE_bootdevGoHours", "--")!!,
            db.getString("CACHE_mathacademyCalc2Hours", "--")!!,
            db.getString("CACHE_lastPolled", "")!!
        )
    }
}
