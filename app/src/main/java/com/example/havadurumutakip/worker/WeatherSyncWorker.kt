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
import com.example.havadurumutakip.data.WeatherRepository
import java.util.concurrent.TimeUnit

class WeatherSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val result = WeatherRepository.measureAndSaveAll(applicationContext)
        return if (result.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "hourly_weather_measurement"

        fun scheduleHourly(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<WeatherSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }

        fun triggerOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<WeatherSyncWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }
    }
}
