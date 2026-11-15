package com.example.havadurumutakip.data

import android.content.Context
import com.example.havadurumutakip.cosa.CosaApiClient
import com.example.havadurumutakip.cosa.CosaEndpointDetail
import com.example.havadurumutakip.cosa.CosaPreferences
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
import org.json.JSONObject
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
                val cosaPrefs = CosaPreferences(appContext)
                if (locations.isEmpty() && !cosaPrefs.isConnected()) {
                    throw IllegalStateException("Takip edilen konum veya Cosa cihazı yok.")
                }

                val nowDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                val nowTs = System.currentTimeMillis()

                val cosaDetail: CosaEndpointDetail? = coroutineScope {
                    async {
                        runCatching { CosaApiClient.fetchSavedDeviceDetail(appContext) }.getOrNull()
                    }.await()
                }

                val fetchedOutdoor = coroutineScope {
                    locations.map { loc ->
                        async {
                            runCatching {
                                val w = OpenMeteoClient.fetchCurrentWeather(loc.latitude, loc.longitude)
                                val extraObj = runCatching { JSONObject(w.extraJson) }.getOrElse { JSONObject() }
                                if (cosaDetail != null) {
                                    putCosaFields(extraObj, cosaDetail)
                                }
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
                                    weatherCode = w.weatherCode,
                                    extraJson = extraObj.toString()
                                )
                            }
                        }
                    }.awaitAll()
                }

                val toInsert = mutableListOf<WeatherMeasurement>()
                toInsert.addAll(fetchedOutdoor.mapNotNull { it.getOrNull() })

                if (cosaDetail != null) {
                    val cosaExtra = JSONObject()
                    putCosaFields(cosaExtra, cosaDetail)
                    cosaExtra.put("cosa_voltage", cosaDetail.batteryVoltage)
                    cosaExtra.put("cosa_rssi", cosaDetail.rssi)

                    toInsert.add(
                        WeatherMeasurement(
                            dateKey = nowDate,
                            timeLabel = nowTime,
                            timestamp = nowTs + 1,
                            locationName = "🏠 Cosa: ${cosaDetail.name}",
                            latitude = 0.0,
                            longitude = 0.0,
                            temperature = cosaDetail.temperature,
                            apparentTemp = cosaDetail.temperature,
                            humidity = cosaDetail.humidity,
                            windSpeed = 0.0,
                            precipitation = 0.0,
                            cloudCover = 0,
                            isDay = true,
                            uvIndex = 0.0,
                            weatherCode = if (cosaDetail.combiOn) -2 else -1,
                            extraJson = cosaExtra.toString()
                        )
                    )
                }

                if (toInsert.isEmpty()) {
                    val firstError = fetchedOutdoor.firstOrNull()?.exceptionOrNull()
                    throw (firstError ?: RuntimeException("Ölçüm alınamadı"))
                }

                val db = WeatherDatabaseHelper(appContext)
                for (m in toInsert) {
                    db.insertMeasurement(m)
                }
                db.close()

                WeatherWidgetProvider.updateAllWidgets(appContext)
                notifyChanged()
                toInsert
            }
        }

    private fun putCosaFields(target: JSONObject, cosa: CosaEndpointDetail) {
        target.put("cosa_temp", cosa.temperature)
        target.put("cosa_humidity", cosa.humidity)
        target.put("cosa_target", cosa.targetTemperature)
        target.put("cosa_combi", if (cosa.combiOn) "Yanıyor 🔥" else "Beklemede")
        target.put("cosa_mode", cosa.modeOptionLabel())
        target.put("cosa_battery", cosa.batteryPercent)
    }
}
