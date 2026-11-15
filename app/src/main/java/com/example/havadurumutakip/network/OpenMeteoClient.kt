package com.example.havadurumutakip.network

import com.example.havadurumutakip.model.GeocodingResult
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CurrentWeatherData(
    val temperature: Double,
    val apparentTemp: Double,
    val humidity: Int,
    val precipitation: Double,
    val cloudCover: Int,
    val isDay: Boolean,
    val uvIndex: Double,
    val windSpeed: Double,
    val weatherCode: Int
)

object OpenMeteoClient {

    fun searchLocations(query: String): List<GeocodingResult> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val urlStr = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=tr&format=json"
        val jsonStr = httpGet(urlStr)
        val root = JSONObject(jsonStr)
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
        val urlStr = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,cloud_cover,is_day,uv_index,weather_code,wind_speed_10m" +
            "&timezone=auto"
        val jsonStr = httpGet(urlStr)
        val current = JSONObject(jsonStr).getJSONObject("current")
        val temp = current.getDouble("temperature_2m")
        return CurrentWeatherData(
            temperature = temp,
            apparentTemp = current.optDouble("apparent_temperature", temp),
            humidity = current.optInt("relative_humidity_2m", 0),
            precipitation = current.optDouble("precipitation", 0.0),
            cloudCover = current.optInt("cloud_cover", 0),
            isDay = current.optInt("is_day", 1) == 1,
            uvIndex = current.optDouble("uv_index", 0.0),
            windSpeed = current.optDouble("wind_speed_10m", 0.0),
            weatherCode = current.optInt("weather_code", 0)
        )
    }

    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code: $body")
            }
            body
        } finally {
            conn.disconnect()
        }
    }

    fun weatherDescription(code: Int): Pair<String, String> = when (code) {
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
