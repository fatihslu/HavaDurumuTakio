package com.example.havadurumutakip.data

import android.content.Context
import com.example.havadurumutakip.model.WeatherMeasurement
import com.example.havadurumutakip.network.OpenMeteoClient
import com.example.havadurumutakip.widget.WeatherWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object WeatherRepository {
    private val _refreshTick = MutableStateFlow(0L)
    val refreshTick: StateFlow<Long> = _refreshTick

    fun notifyChanged() {
        _refreshTick.value = System.currentTimeMillis()
    }

    suspend fun measureAndSaveNow(context: Context): Result<WeatherMeasurement> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                val location = LocationPreferences(appContext).getLocation()
                val weather = OpenMeteoClient.fetchCurrentWeather(location.latitude, location.longitude)

                val nowDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

                val measurement = WeatherMeasurement(
                    dateKey = nowDate,
                    timeLabel = nowTime,
                    timestamp = System.currentTimeMillis(),
                    locationName = location.name,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    temperature = weather.temperature,
                    humidity = weather.humidity,
                    windSpeed = weather.windSpeed,
                    weatherCode = weather.weatherCode
                )

                val db = WeatherDatabaseHelper(appContext)
                db.insertMeasurement(measurement)
                db.close()

                WeatherWidgetProvider.updateAllWidgets(appContext)
                notifyChanged()
                measurement
            }
        }
}
