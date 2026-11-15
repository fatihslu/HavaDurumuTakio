package com.example.havadurumutakip.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.havadurumutakip.MainActivity
import com.example.havadurumutakip.R
import com.example.havadurumutakip.cosa.CosaPreferences
import com.example.havadurumutakip.data.LocationPreferences
import com.example.havadurumutakip.data.WeatherDatabaseHelper
import com.example.havadurumutakip.network.OpenMeteoClient
import com.example.havadurumutakip.worker.WeatherSyncWorker
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            WeatherSyncWorker.triggerOneTime(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.havadurumutakip.ACTION_REFRESH_WIDGET"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, WeatherWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) {
                updateSingleWidget(context, manager, id)
            }
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val location = LocationPreferences(context).getPrimaryLocation()
            val cosaPrefs = CosaPreferences(context)
            val views = RemoteViews(context.packageName, R.layout.widget_weather)
            val db = WeatherDatabaseHelper(context)

            val cosaLatest = if (cosaPrefs.isConnected()) {
                db.getLatestMeasurementForLocation("🏠 Cosa: ${cosaPrefs.getEndpointName()}")
            } else null

            val cosaSuffix = if (cosaLatest != null) {
                val fire = if (cosaLatest.weatherCode == -2) " 🔥" else ""
                String.format(Locale.US, " • Oda: %.1f°%s", cosaLatest.temperature, fire)
            } else ""

            if (location != null) {
                val latest = db.getLatestMeasurementForLocation(location.name)
                val todayKey = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val todaySummary = db.getDaySummary(todayKey, location.name)

                views.setTextViewText(R.id.widget_location, location.name)

                if (latest != null) {
                    val (emoji, desc) = OpenMeteoClient.weatherDescription(latest.weatherCode)
                    views.setTextViewText(
                        R.id.widget_temp,
                        String.format(Locale.US, "%.1f°C", latest.temperature)
                    )
                    views.setTextViewText(R.id.widget_condition, "$emoji $desc")
                    views.setTextViewText(R.id.widget_time, "Son: ${latest.timeLabel}$cosaSuffix")
                } else {
                    views.setTextViewText(R.id.widget_temp, "--°C")
                    views.setTextViewText(R.id.widget_condition, "Henüz ölçüm yok")
                    views.setTextViewText(R.id.widget_time, "Yenile'ye basın$cosaSuffix")
                }

                if (todaySummary != null) {
                    val stats = String.format(
                        Locale.US,
                        "Min: %.1f° | Ort: %.1f° | Max: %.1f°",
                        todaySummary.minTemp,
                        todaySummary.avgTemp,
                        todaySummary.maxTemp
                    )
                    views.setTextViewText(R.id.widget_stats, stats)
                } else {
                    views.setTextViewText(R.id.widget_stats, "Min: --° | Ort: --° | Max: --°")
                }
            } else {
                views.setTextViewText(R.id.widget_location, "Konum seçilmedi")
                views.setTextViewText(R.id.widget_temp, "--°C")
                views.setTextViewText(R.id.widget_condition, "Uygulamadan konum ekleyin")
                views.setTextViewText(R.id.widget_stats, "Min: --° | Ort: --° | Max: --°")
                views.setTextViewText(R.id.widget_time, "--:--$cosaSuffix")
            }
            db.close()

            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPending = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPending)

            val refreshIntent = Intent(context, WeatherWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
