package com.levelsapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, singleId = appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        singleId: Int = -1
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_stats)
        views.setTextViewText(R.id.tv_widget_total_xp, "↻")
        appWidgetManager.updateAppWidget(singleId, views)

        CoroutineScope(Dispatchers.IO).launch {
            val stats = Scraper.pollStats(context) ?: Scraper.getCachedStats(context)

            if (stats != null) {
                views.setTextViewText(R.id.tv_widget_total_xp, stats.totalDailyXp)
                views.setTextViewText(R.id.tv_widget_go_hours, stats.bootdevGoHours)
                views.setTextViewText(R.id.tv_widget_calc2_hours, stats.mathacademyCalc2Hours)
            } else {
                views.setTextViewText(R.id.tv_widget_total_xp, "--")
                views.setTextViewText(R.id.tv_widget_go_hours, "--")
                views.setTextViewText(R.id.tv_widget_calc2_hours, "--")
            }

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(singleId, views)
        }
    }
}
