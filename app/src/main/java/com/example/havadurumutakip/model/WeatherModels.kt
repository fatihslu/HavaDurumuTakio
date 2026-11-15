package com.example.havadurumutakip.model

data class SavedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class GeocodingResult(
    val name: String,
    val admin1: String,
    val country: String,
    val latitude: Double,
    val longitude: Double
) {
    val displayName: String
        get() = listOf(name, admin1, country)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
}

data class WeatherMeasurement(
    val id: Long = 0,
    val dateKey: String,
    val timeLabel: String,
    val timestamp: Long,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val temperature: Double,
    val apparentTemp: Double,
    val humidity: Int,
    val windSpeed: Double,
    val precipitation: Double,
    val cloudCover: Int,
    val isDay: Boolean,
    val uvIndex: Double,
    val weatherCode: Int
)

data class DailySummary(
    val dateKey: String,
    val locationName: String,
    val minTemp: Double,
    val maxTemp: Double,
    val avgTemp: Double,
    val totalPrecipitation: Double,
    val avgCloudCover: Int,
    val maxUvIndex: Double,
    val measurementCount: Int
)
