package com.levelsapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ScraperWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val stats = Scraper.pollStats(applicationContext)
            
            val widgetIntent = android.content.Intent(applicationContext, StatsWidgetProvider::class.java)
            widgetIntent.action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = android.appwidget.AppWidgetManager.getInstance(applicationContext).getAppWidgetIds(
                android.content.ComponentName(applicationContext, StatsWidgetProvider::class.java)
            )
            widgetIntent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            applicationContext.sendBroadcast(widgetIntent)

            if (stats != null) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
