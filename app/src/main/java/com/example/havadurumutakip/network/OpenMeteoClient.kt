package com.example.havadurumutakip.network

import com.example.havadurumutakip.model.GeocodingResult
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class HourlySnapshot(
    val dateKey: String,
    val timeLabel: String,
    val temperature: Double,
    val apparentTemp: Double,
    val humidity: Int,
    val precipitation: Double,
    val cloudCover: Int,
    val isDay: Boolean,
    val uvIndex: Double,
    val windSpeed: Double,
    val weatherCode: Int,
    val extraJson: String
)

data class CurrentWeatherData(
    val temperature: Double,
    val apparentTemp: Double,
    val humidity: Int,
    val precipitation: Double,
    val cloudCover: Int,
    val isDay: Boolean,
    val uvIndex: Double,
    val windSpeed: Double,
    val weatherCode: Int,
    val extraJson: String,
    val todayHourly: List<HourlySnapshot> = emptyList()
)

object OpenMeteoClient {

    fun searchLocations(query: String): List<GeocodingResult> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val urlStr = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=tr&format=json"
        val root = JSONObject(httpGet(urlStr))
        val resultsArray = root.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<GeocodingResult>()
        for (i in 0 until resultsArray.length()) {
            val item = resultsArray.getJSONObject(i)
            list.add(
                GeocodingResult(
                    name = item.optString("name", ""),
                    admin1 = item.optString("admin1", ""),
                    country = item.optString("country", ""),
                    latitude = item.getDouble("latitude"),
                    longitude = item.getDouble("longitude")
                )
            )
        }
        return list
    }

    fun fetchCurrentWeather(latitude: Double, longitude: Double): CurrentWeatherData {
        var lastErr: Exception? = null
        for (attempt in 1..2) {
            try {
                return fetchOnce(latitude, longitude)
            } catch (e: Exception) {
                lastErr = e
                if (attempt == 1) Thread.sleep(1200)
            }
        }
        throw (lastErr ?: RuntimeException("Hava durumu alınamadı"))
    }

    private fun fetchOnce(latitude: Double, longitude: Double): CurrentWeatherData {
        val forecastUrl = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,rain,showers,snowfall,weather_code,cloud_cover,pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            "&hourly=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,cloud_cover,is_day,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,dew_point_2m,precipitation_probability,visibility,shortwave_radiation,direct_radiation,diffuse_radiation,uv_index,cape,freezing_level_height,evapotranspiration,soil_temperature_0cm,soil_moisture_0_to_1cm" +
            "&daily=sunrise,sunset,daylight_duration,sunshine_duration" +
            "&forecast_days=1&timezone=auto"

        val root = JSONObject(httpGet(forecastUrl))
        val current = root.getJSONObject("current")
        val temp = current.getDouble("temperature_2m")
        val apparent = safeObjDouble(current, "apparent_temperature") ?: temp
        val humidity = current.optInt("relative_humidity_2m", 0)
        val precip = safeObjDouble(current, "precipitation") ?: 0.0
        val cloud = current.optInt("cloud_cover", 0)
        val isDay = current.optInt("is_day", 1) == 1
        val windSpeed = safeObjDouble(current, "wind_speed_10m") ?: 0.0
        val weatherCode = current.optInt("weather_code", 0)

        val extra = JSONObject()
        safeObjDouble(current, "rain")?.let { extra.put("rain", it) }
        safeObjDouble(current, "showers")?.let { extra.put("showers", it) }
        safeObjDouble(current, "snowfall")?.let { extra.put("snowfall", it) }
        safeObjDouble(current, "pressure_msl")?.let { extra.put("pressure_msl", it) }
        safeObjDouble(current, "surface_pressure")?.let { extra.put("surface_pressure", it) }
        safeObjDouble(current, "wind_gusts_10m")?.let { extra.put("wind_gusts", it) }
        if (current.has("wind_direction_10m") && !current.isNull("wind_direction_10m")) {
            extra.put("wind_dir", current.optInt("wind_direction_10m", 0))
        }

        val daily = root.optJSONObject("daily")
        var sunriseStr = ""
        var sunsetStr = ""
        var sunshineHrs: Double? = null
        var daylightHrs: Double? = null
        if (daily != null) {
            val sr = daily.optJSONArray("sunrise")?.optString(0, "") ?: ""
            val ss = daily.optJSONArray("sunset")?.optString(0, "") ?: ""
            if (sr.contains("T")) { sunriseStr = sr.substringAfter("T").take(5); extra.put("sunrise", sunriseStr) }
            if (ss.contains("T")) { sunsetStr = ss.substringAfter("T").take(5); extra.put("sunset", sunsetStr) }
            safeArrDouble(daily.optJSONArray("sunshine_duration"), 0)?.let { sunshineHrs = it / 3600.0; extra.put("sunshine_hrs", sunshineHrs) }
            safeArrDouble(daily.optJSONArray("daylight_duration"), 0)?.let { daylightHrs = it / 3600.0; extra.put("daylight_hrs", daylightHrs) }
        }

        var uvIndex = 0.0
        val todayHourly = mutableListOf<HourlySnapshot>()
        val hourly = root.optJSONObject("hourly")
        if (hourly != null) {
            val currentTime = current.optString("time", "")
            val curDate = currentTime.take(10)
            val hourPrefix = if (currentTime.length >= 13) currentTime.substring(0, 13) + ":00" else ""
            val timeArr = hourly.optJSONArray("time")
            var curIdx = 0
            if (timeArr != null && hourPrefix.isNotEmpty()) {
                for (i in 0 until timeArr.length()) {
                    if (timeArr.optString(i) == hourPrefix) { curIdx = i; break }
                }
            }
            fillHourlyExtras(hourly, curIdx, extra)
            safeArrDouble(hourly.optJSONArray("uv_index"), curIdx)?.let { uvIndex = it }

            if (timeArr != null && curDate.length == 10) {
                for (i in 0 until curIdx) {
                    val ts = timeArr.optString(i, "")
                    if (!ts.startsWith(curDate) || !ts.contains("T")) continue
                    val hTemp = safeArrDouble(hourly.optJSONArray("temperature_2m"), i) ?: continue
                    val hExtra = JSONObject()
                    if (sunriseStr.isNotEmpty()) hExtra.put("sunrise", sunriseStr)
                    if (sunsetStr.isNotEmpty()) hExtra.put("sunset", sunsetStr)
                    sunshineHrs?.let { hExtra.put("sunshine_hrs", it) }
                    daylightHrs?.let { hExtra.put("daylight_hrs", it) }
                    safeArrDouble(hourly.optJSONArray("pressure_msl"), i)?.let { hExtra.put("pressure_msl", it) }
                    safeArrDouble(hourly.optJSONArray("wind_gusts_10m"), i)?.let { hExtra.put("wind_gusts", it) }
                    safeArrDouble(hourly.optJSONArray("wind_direction_10m"), i)?.let { hExtra.put("wind_dir", it.toInt()) }
                    fillHourlyExtras(hourly, i, hExtra)

                    todayHourly.add(
                        HourlySnapshot(
                            dateKey = curDate,
                            timeLabel = ts.substringAfter("T").take(5),
                            temperature = hTemp,
                            apparentTemp = safeArrDouble(hourly.optJSONArray("apparent_temperature"), i) ?: hTemp,
                            humidity = (safeArrDouble(hourly.optJSONArray("relative_humidity_2m"), i) ?: 0.0).toInt(),
                            precipitation = safeArrDouble(hourly.optJSONArray("precipitation"), i) ?: 0.0,
                            cloudCover = (safeArrDouble(hourly.optJSONArray("cloud_cover"), i) ?: 0.0).toInt(),
                            isDay = (safeArrDouble(hourly.optJSONArray("is_day"), i) ?: 1.0).toInt() == 1,
                            uvIndex = safeArrDouble(hourly.optJSONArray("uv_index"), i) ?: 0.0,
                            windSpeed = safeArrDouble(hourly.optJSONArray("wind_speed_10m"), i) ?: 0.0,
                            weatherCode = (safeArrDouble(hourly.optJSONArray("weather_code"), i) ?: 0.0).toInt(),
                            extraJson = hExtra.toString()
                        )
                    )
                }
            }
        }

        runCatching {
            val aqUrl = "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=european_aqi,us_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone,dust,alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen" +
                "&timezone=auto"
            val aqCur = JSONObject(httpGet(aqUrl)).optJSONObject("current")
            if (aqCur != null) {
                if (aqCur.has("european_aqi") && !aqCur.isNull("european_aqi")) extra.put("eu_aqi", aqCur.optInt("european_aqi", 0))
                safeObjDouble(aqCur, "us_aqi")?.let { extra.put("us_aqi", it) }
                safeObjDouble(aqCur, "pm2_5")?.let { extra.put("pm2_5", it) }
                safeObjDouble(aqCur, "pm10")?.let { extra.put("pm10", it) }
                safeObjDouble(aqCur, "ozone")?.let { extra.put("ozone", it) }
                safeObjDouble(aqCur, "nitrogen_dioxide")?.let { extra.put("no2", it) }
                safeObjDouble(aqCur, "sulphur_dioxide")?.let { extra.put("so2", it) }
                safeObjDouble(aqCur, "carbon_monoxide")?.let { extra.put("co", it) }
                safeObjDouble(aqCur, "dust")?.let { extra.put("dust", it) }
                val pollens = listOf(
                    "Kızılağaç" to safeObjDouble(aqCur, "alder_pollen"),
                    "Huş" to safeObjDouble(aqCur, "birch_pollen"),
                    "Çimen" to safeObjDouble(aqCur, "grass_pollen"),
                    "Pelin" to safeObjDouble(aqCur, "mugwort_pollen"),
                    "Zeytin" to safeObjDouble(aqCur, "olive_pollen"),
                    "Kanaryaotu" to safeObjDouble(aqCur, "ragweed_pollen")
                ).mapNotNull { (label, v) ->
                    if (v != null && v > 0.0) "$label: ${String.format(Locale.US, "%.0f", v)}" else null
                }
                if (pollens.isNotEmpty()) extra.put("pollen_text", pollens.joinToString(", "))
            }
        }

        return CurrentWeatherData(temp, apparent, humidity, precip, cloud, isDay, uvIndex, windSpeed, weatherCode, extra.toString(), todayHourly)
    }

    private fun fillHourlyExtras(hourly: JSONObject, idx: Int, out: JSONObject) {
        safeArrDouble(hourly.optJSONArray("dew_point_2m"), idx)?.let { out.put("dew_point", it) }
        safeArrDouble(hourly.optJSONArray("precipitation_probability"), idx)?.let { out.put("precip_prob", it) }
        safeArrDouble(hourly.optJSONArray("visibility"), idx)?.let { out.put("visibility_km", it / 1000.0) }
        safeArrDouble(hourly.optJSONArray("shortwave_radiation"), idx)?.let { out.put("shortwave_rad", it) }
        safeArrDouble(hourly.optJSONArray("direct_radiation"), idx)?.let { out.put("direct_rad", it) }
        safeArrDouble(hourly.optJSONArray("diffuse_radiation"), idx)?.let { out.put("diffuse_rad", it) }
        safeArrDouble(hourly.optJSONArray("cape"), idx)?.let { out.put("cape", it) }
        safeArrDouble(hourly.optJSONArray("freezing_level_height"), idx)?.let { out.put("freezing_level", it) }
        safeArrDouble(hourly.optJSONArray("evapotranspiration"), idx)?.let { out.put("evapotranspiration", it) }
        safeArrDouble(hourly.optJSONArray("soil_temperature_0cm"), idx)?.let { out.put("soil_temp", it) }
        safeArrDouble(hourly.optJSONArray("soil_moisture_0_to_1cm"), idx)?.let { out.put("soil_moisture", it) }
    }

    private fun safeObjDouble(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val v = obj.optDouble(key, Double.NaN)
        return if (v.isNaN() || v.isInfinite()) null else v
    }

    private fun safeArrDouble(arr: JSONArray?, index: Int): Double? {
        if (arr == null || index < 0 || index >= arr.length() || arr.isNull(index)) return null
        val v = arr.optDouble(index, Double.NaN)
        return if (v.isNaN() || v.isInfinite()) null else v
    }

    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
        }
        return try {
            val code = conn.responseCode
            val stream = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?: throw RuntimeException("HTTP $code boş yanıt")
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw RuntimeException("HTTP $code: $body")
            body
        } finally {
            conn.disconnect()
        }
    }

    fun weatherDescription(code: Int): Pair<String, String> = when (code) {
        -2 -> "🔥" to "Cosa (Kombi Yanıyor)"
        -1 -> "🏠" to "Cosa (Kombi Beklemede)"
        0 -> "☀️" to "Açık"
        1, 2 -> "🌤️" to "Az Bulutlu"
        3 -> "☁️" to "Çok Bulutlu"
        45, 48 -> "🌫️" to "Sisli"
        51, 53, 55, 56, 57 -> "🌦️" to "Çisenti"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️" to "Yağmurlu"
        71, 73, 75, 77, 85, 86 -> "❄️" to "Karlı"
        95, 96, 99 -> "⛈️" to "Fırtınalı"
        else -> "🌡️" to "Diğer ($code)"
    }

    fun sunCondition(isDay: Boolean, cloudCover: Int, precipitation: Double): String {
        if (!isDay) return "Gece"
        if (precipitation > 0.0) return "Yağışlı / Kapalı"
        return when {
            cloudCover <= 20 -> "Güneşli"
            cloudCover <= 55 -> "Parçalı Güneşli"
            cloudCover <= 80 -> "Çok Bulutlu"
            else -> "Kapalı"
        }
    }
}
