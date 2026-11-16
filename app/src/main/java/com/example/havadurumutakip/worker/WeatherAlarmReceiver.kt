package com.example.havadurumutakip.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WeatherAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WeatherSyncWorker.scheduleHourly(context)
            scheduleNext(context)
            return
        }
        WeatherSyncWorker.triggerOneTime(context, source = "Otomatik (Alarm)")
        scheduleNext(context)
    }

    companion object {
        private const val ACTION_ALARM_SYNC = "com.example.havadurumutakip.ACTION_ALARM_SYNC"

        fun scheduleNext(context: Context) {
            val appCtx = context.applicationContext
            val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(appCtx, WeatherAlarmReceiver::class.java).apply { action = ACTION_ALARM_SYNC }
            val pending = PendingIntent.getBroadcast(
                appCtx, 101, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + 60L * 60L * 1000L
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }
}
