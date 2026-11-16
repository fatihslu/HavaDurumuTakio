package com.example.havadurumutakip.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.havadurumutakip.data.WeatherRepository
import java.util.concurrent.TimeUnit

class WeatherSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val source = inputData.getString("source") ?: "Otomatik"
        val result = WeatherRepository.measureAndSaveAll(applicationContext, source)
        return if (result.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "hourly_weather_measurement"

        fun scheduleHourly(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<WeatherSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(workDataOf("source" to "Otomatik (30dk)"))
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
            WeatherAlarmReceiver.scheduleNext(context)
        }

        fun triggerOneTime(context: Context, source: String = "Widget") {
            val request = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
                .setInputData(workDataOf("source" to source))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }
    }
}
