package com.example.havadurumutakip.data

import android.content.Context
import com.example.havadurumutakip.model.WeatherMeasurement
import com.example.havadurumutakip.network.OpenMeteoClient
import com.example.havadurumutakip.widget.WeatherWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    suspend fun measureAndSaveAll(context: Context): Result<List<WeatherMeasurement>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                val locations = LocationPreferences(appContext).getLocations()
                if (locations.isEmpty()) {
                    throw IllegalStateException("Takip edilen konum yok. Lütfen konum ekleyin.")
                }

                val nowDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                val nowTs = System.currentTimeMillis()

                val fetched = coroutineScope {
                    locations.map { loc ->
                        async {
                            runCatching {
                                val w = OpenMeteoClient.fetchCurrentWeather(loc.latitude, loc.longitude)
                                WeatherMeasurement(
                                    dateKey = nowDate,
                                    timeLabel = nowTime,
                                    timestamp = nowTs,
                                    locationName = loc.name,
                                    latitude = loc.latitude,
                                    longitude = loc.longitude,
                                    temperature = w.temperature,
                                    apparentTemp = w.apparentTemp,
                                    humidity = w.humidity,
                                    windSpeed = w.windSpeed,
                                    precipitation = w.precipitation,
                                    cloudCover = w.cloudCover,
                                    isDay = w.isDay,
                                    uvIndex = w.uvIndex,
                                    weatherCode = w.weatherCode
                                )
                            }
                        }
                    }.awaitAll()
                }

                val successes = fetched.mapNotNull { it.getOrNull() }
                if (successes.isEmpty()) {
                    val firstError = fetched.firstOrNull()?.exceptionOrNull()
                    throw (firstError ?: RuntimeException("Ölçüm alınamadı"))
                }

                val db = WeatherDatabaseHelper(appContext)
                for (m in successes) {
                    db.insertMeasurement(m)
                }
                db.close()

                WeatherWidgetProvider.updateAllWidgets(appContext)
                notifyChanged()
                successes
            }
        }
}
