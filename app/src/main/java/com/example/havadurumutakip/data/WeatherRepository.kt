package com.example.havadurumutakip.data

import android.content.Context
import com.example.havadurumutakip.cosa.CosaApiClient
import com.example.havadurumutakip.cosa.CosaDailyReport
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SyncStatusPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sync_status_prefs", Context.MODE_PRIVATE)
    fun saveStatus(info: String, error: String) {
        prefs.edit().putString("last_info", info).putString("last_error", error).apply()
    }
    fun getLastInfo(): String = prefs.getString("last_info", "") ?: ""
    fun getLastError(): String = prefs.getString("last_error", "") ?: ""
}

object WeatherRepository {
    private val _refreshTick = MutableStateFlow(0L)
    val refreshTick: StateFlow<Long> = _refreshTick

    fun notifyChanged() {
        _refreshTick.value = System.currentTimeMillis()
    }

    suspend fun refreshCosaReportOnly(context: Context): Result<CosaDailyReport?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                val report = CosaApiClient.fetchSavedReport(appContext)
                if (report != null) {
                    val db = WeatherDatabaseHelper(appContext)
                    db.upsertCosaReport(report)
                    db.close()
                    notifyChanged()
                }
                report
            }
        }

    suspend fun measureAndSaveAll(context: Context, source: String = "Manuel"): Result<List<WeatherMeasurement>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                val locations = LocationPreferences(appContext).getLocations()
                val cosaPrefs = CosaPreferences(appContext)
                val statusPrefs = SyncStatusPrefs(appContext)

                if (locations.isEmpty() && !cosaPrefs.isConnected()) {
                    throw IllegalStateException("Takip edilen konum veya Cosa cihazı yok.")
                }

                val nowDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                val nowTs = System.currentTimeMillis()
                val errors = mutableListOf<String>()

                val cosaPair = coroutineScope {
                    val d = async {
                        if (!cosaPrefs.isConnected()) null
                        else runCatching { CosaApiClient.fetchSavedDeviceDetail(appContext) }
                            .onFailure { errors.add("Cosa: ${it.localizedMessage}") }
                            .getOrNull()
                    }
                    val r = async {
                        if (!cosaPrefs.isConnected()) null
                        else runCatching { CosaApiClient.fetchSavedReport(appContext) }.getOrNull()
                    }
                    d.await() to r.await()
                }
                val cosaDetail = cosaPair.first
                val cosaReport = cosaPair.second

                val fetchedOutdoor = coroutineScope {
                    locations.map { loc ->
                        async {
                            loc to runCatching { OpenMeteoClient.fetchCurrentWeather(loc.latitude, loc.longitude) }
                        }
                    }.awaitAll()
                }

                val db = WeatherDatabaseHelper(appContext)
                val toInsert = mutableListOf<WeatherMeasurement>()

                for ((loc, res) in fetchedOutdoor) {
                    res.onFailure { e ->
                        errors.add("${loc.name.substringBefore(",")}: ${e.localizedMessage}")
                    }.onSuccess { w ->
                        // Bugun kacirilan saatler varsa tamamla (en son kayittan sonraki saatler)
                        val existingToday = db.getHourlyForDay(nowDate, loc.name)
                        if (existingToday.isNotEmpty()) {
                            val lastHour = existingToday.maxOfOrNull { it.timeLabel.take(2).toIntOrNull() ?: -1 } ?: -1
                            val existingHours = existingToday.map { it.timeLabel.take(2) }.toSet()
                            val zone = ZoneId.systemDefault()
                            for (snap in w.todayHourly) {
                                val hInt = snap.timeLabel.take(2).toIntOrNull() ?: continue
                                if (hInt > lastHour && snap.timeLabel.take(2) !in existingHours) {
                                    val snapTs = LocalDate.parse(snap.dateKey).atTime(hInt, 0).atZone(zone).toInstant().toEpochMilli()
                                    toInsert.add(
                                        WeatherMeasurement(
                                            dateKey = snap.dateKey, timeLabel = snap.timeLabel, timestamp = snapTs,
                                            locationName = loc.name, latitude = loc.latitude, longitude = loc.longitude,
                                            temperature = snap.temperature, apparentTemp = snap.apparentTemp,
                                            humidity = snap.humidity, windSpeed = snap.windSpeed,
                                            precipitation = snap.precipitation, cloudCover = snap.cloudCover,
                                            isDay = snap.isDay, uvIndex = snap.uvIndex,
                                            weatherCode = snap.weatherCode, extraJson = snap.extraJson
                                        )
                                    )
                                }
                            }
                        }

                        val extraObj = runCatching { JSONObject(w.extraJson) }.getOrElse { JSONObject() }
                        if (cosaDetail != null) putCosaFields(extraObj, cosaDetail)
                        toInsert.add(
                            WeatherMeasurement(
                                dateKey = nowDate, timeLabel = nowTime, timestamp = nowTs,
                                locationName = loc.name, latitude = loc.latitude, longitude = loc.longitude,
                                temperature = w.temperature, apparentTemp = w.apparentTemp,
                                humidity = w.humidity, windSpeed = w.windSpeed,
                                precipitation = w.precipitation, cloudCover = w.cloudCover,
                                isDay = w.isDay, uvIndex = w.uvIndex,
                                weatherCode = w.weatherCode, extraJson = extraObj.toString()
                            )
                        )
                    }
                }

                if (cosaDetail != null) {
                    val cosaExtra = JSONObject()
                    putCosaFields(cosaExtra, cosaDetail)
                    cosaExtra.put("cosa_voltage", cosaDetail.batteryVoltage)
                    cosaExtra.put("cosa_rssi", cosaDetail.rssi)
                    toInsert.add(
                        WeatherMeasurement(
                            dateKey = nowDate, timeLabel = nowTime, timestamp = nowTs + 1,
                            locationName = "🏠 Cosa: ${cosaDetail.name}",
                            latitude = 0.0, longitude = 0.0,
                            temperature = cosaDetail.temperature, apparentTemp = cosaDetail.temperature,
                            humidity = cosaDetail.humidity, windSpeed = 0.0, precipitation = 0.0,
                            cloudCover = 0, isDay = true, uvIndex = 0.0,
                            weatherCode = if (cosaDetail.combiOn) -2 else -1,
                            extraJson = cosaExtra.toString()
                        )
                    )
                }

                for (m in toInsert) db.insertMeasurement(m)
                if (cosaReport != null) db.upsertCosaReport(cosaReport)
                db.close()

                val errText = errors.joinToString(" | ")
                statusPrefs.saveStatus("$nowTime ($source)", errText)

                if (toInsert.isEmpty()) {
                    notifyChanged()
                    throw RuntimeException(errText.ifBlank { "Ölçüm alınamadı" })
                }

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
