package com.levelsapp

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class StatsWidgetProvider : AppWidgetProvider() {

    private val client = OkHttpClient()
    // VPS Deployment endpoint
    private val API_URL = "http://77.42.82.186:8181/api/stats"

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: IntArray? = null,
        singleId: Int = -1
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(API_URL).build()
                val response = client.newCall(request).execute()
                val responseData = response.body?.string()

                if (response.isSuccessful && responseData != null) {
                    val json = JSONObject(responseData)
                    val totalXp = json.optInt("totalDailyXp", 0).toString()
                    val bootdevHours = json.optJSONObject("bootdev")?.optString("goHours", "--") ?: "--"
                    val mathHours = json.optJSONObject("mathacademy")?.optString("calc2Hours", "--") ?: "--"

                    withContext(Dispatchers.Main) {
                        val views = RemoteViews(context.packageName, R.layout.widget_stats)
                        views.setTextViewText(R.id.tv_total_xp, totalXp)
                        views.setTextViewText(R.id.tv_bootdev_hours, bootdevHours)
                        views.setTextViewText(R.id.tv_math_hours, mathHours)

                        if (appWidgetId != null) {
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        } else if (singleId != -1) {
                            appWidgetManager.updateAppWidget(singleId, views)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
