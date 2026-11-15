package com.example.havadurumutakip.model

import android.content.Context
import com.example.havadurumutakip.network.OpenMeteoClient
import org.json.JSONObject
import java.util.Locale

enum class MetricCategory(val title: String) {
    BASIC("Temel & Sıcaklık"),
    SUN_PRECIP("Yağış, Bulut & Güneş"),
    WIND_ATMOS("Rüzgâr, Basınç & Atmosfer"),
    SOIL_AGRI("Toprak & Tarım"),
    AIR_POLLEN("Hava Kalitesi & Polen")
}

enum class WeatherMetric(
    val label: String,
    val excelHeader: String,
    val category: MetricCategory,
    val defaultEnabled: Boolean,
    val isNumeric: Boolean = false
) {
    TEMPERATURE("Sıcaklık (°C)", "Sicaklik (C)", MetricCategory.BASIC, true, true),
    APPARENT_TEMP("Hissedilen Sıcaklık (°C)", "Hissedilen (C)", MetricCategory.BASIC, true, true),
    DEW_POINT("Çiy Noktası (°C)", "Ciy Noktasi (C)", MetricCategory.BASIC, true, true),
    HUMIDITY("Bağıl Nem (%)", "Nem (%)", MetricCategory.BASIC, true, true),

    WEATHER_DESC("Hava Durumu", "Hava Durumu", MetricCategory.SUN_PRECIP, true, false),
    SUN_CONDITION("Güneş / Gökyüzü Durumu", "Gunes Durumu", MetricCategory.SUN_PRECIP, true, false),
    CLOUD_COVER("Bulutluluk (%)", "Bulutluluk (%)", MetricCategory.SUN_PRECIP, true, true),
    PRECIPITATION("Toplam Yağış (mm)", "Toplam Yagis (mm)", MetricCategory.SUN_PRECIP, true, true),
    RAIN("Yağmur (mm)", "Yagmur (mm)", MetricCategory.SUN_PRECIP, false, true),
    SHOWERS("Sağanak (mm)", "Saganak (mm)", MetricCategory.SUN_PRECIP, false, true),
    SNOWFALL("Kar Yağışı (cm)", "Kar Yagisi (cm)", MetricCategory.SUN_PRECIP, false, true),
    PRECIP_PROB("Yağış İhtimali (%)", "Yagis Ihtimali (%)", MetricCategory.SUN_PRECIP, true, true),
    UV_INDEX("UV İndeksi", "UV Indeksi", MetricCategory.SUN_PRECIP, true, true),
    SHORTWAVE_RAD("Güneş Radyasyonu (W/m²)", "Gunes Radyasyonu (W/m2)", MetricCategory.SUN_PRECIP, true, true),
    DIRECT_RAD("Direkt Işınım (W/m²)", "Direkt Isinim (W/m2)", MetricCategory.SUN_PRECIP, false, true),
    DIFFUSE_RAD("Dağınık Işınım (W/m²)", "Daginik Isinim (W/m2)", MetricCategory.SUN_PRECIP, false, true),
    SUNRISE_SUNSET("Gün Doğumu / Batımı", "Gun Dogumu - Batimi", MetricCategory.SUN_PRECIP, true, false),
    SUNSHINE_DUR("Günlük Güneşlenme (sa)", "Guneslenme (sa)", MetricCategory.SUN_PRECIP, true, true),
    DAYLIGHT_DUR("Gün Uzunluğu (sa)", "Gun Uzunlugu (sa)", MetricCategory.SUN_PRECIP, false, true),

    WIND_SPEED("Rüzgâr Hızı (km/sa)", "Ruzgar Hizi (km/sa)", MetricCategory.WIND_ATMOS, true, true),
    WIND_DIR("Rüzgâr Yönü", "Ruzgar Yonu", MetricCategory.WIND_ATMOS, true, false),
    WIND_GUSTS("Rüzgâr Hamlesi (km/sa)", "Ruzgar Hamlesi (km/sa)", MetricCategory.WIND_ATMOS, true, true),
    PRESSURE_MSL("Deniz Seviyesi Basıncı (hPa)", "Basinc MSL (hPa)", MetricCategory.WIND_ATMOS, true, true),
    SURFACE_PRESSURE("Yüzey Basıncı (hPa)", "Yuzey Basinci (hPa)", MetricCategory.WIND_ATMOS, false, true),
    VISIBILITY("Görüş Mesafesi (km)", "Gorus Mesafesi (km)", MetricCategory.WIND_ATMOS, true, true),
    CAPE("Fırtına Kararsızlığı CAPE (J/kg)", "CAPE (J/kg)", MetricCategory.WIND_ATMOS, false, true),
    FREEZING_LEVEL("0°C Donma Yüksekliği (m)", "Donma Yuksekligi (m)", MetricCategory.WIND_ATMOS, false, true),

    EVAPOTRANSPIRATION("Buharlaşma ET₀ (mm)", "Buharlasma ET0 (mm)", MetricCategory.SOIL_AGRI, false, true),
    SOIL_TEMP("Toprak Sıcaklığı 0cm (°C)", "Toprak Sicakligi (C)", MetricCategory.SOIL_AGRI, false, true),
    SOIL_MOISTURE("Toprak Nemi (m³/m³)", "Toprak Nemi (m3/m3)", MetricCategory.SOIL_AGRI, false, true),

    EUROPEAN_AQI("Avrupa Hava Kalitesi (AQI)", "Avrupa AQI", MetricCategory.AIR_POLLEN, true, false),
    US_AQI("ABD Hava Kalitesi (AQI)", "ABD AQI", MetricCategory.AIR_POLLEN, false, true),
    PM2_5("İnce Partikül PM2.5 (µg/m³)", "PM2.5 (ug/m3)", MetricCategory.AIR_POLLEN, true, true),
    PM10("Partikül PM10 (µg/m³)", "PM10 (ug/m3)", MetricCategory.AIR_POLLEN, true, true),
    OZONE("Ozon O₃ (µg/m³)", "Ozon O3 (ug/m3)", MetricCategory.AIR_POLLEN, false, true),
    NO2("Azot Dioksit NO₂ (µg/m³)", "NO2 (ug/m3)", MetricCategory.AIR_POLLEN, false, true),
    SO2("Kükürt Dioksit SO₂ (µg/m³)", "SO2 (ug/m3)", MetricCategory.AIR_POLLEN, false, true),
    CO("Karbon Monoksit CO (µg/m³)", "CO (ug/m3)", MetricCategory.AIR_POLLEN, false, true),
    DUST("Çöl / Atmosfer Tozu (µg/m³)", "Toz (ug/m3)", MetricCategory.AIR_POLLEN, false, true),
    POLLEN("Polen Seviyeleri (tane/m³)", "Polenler", MetricCategory.AIR_POLLEN, false, false);

    fun formatDisplay(m: WeatherMeasurement, extra: JSONObject = m.extraObj()): String {
        return when (this) {
            TEMPERATURE -> String.format(Locale.US, "%.1f °C", m.temperature)
            APPARENT_TEMP -> String.format(Locale.US, "%.1f °C", m.apparentTemp)
            DEW_POINT -> fmtNum(extra, "dew_point", "%.1f °C")
            HUMIDITY -> "%${m.humidity}"
            WEATHER_DESC -> {
                val (e, d) = OpenMeteoClient.weatherDescription(m.weatherCode)
                "$e $d"
            }
            SUN_CONDITION -> OpenMeteoClient.sunCondition(m.isDay, m.cloudCover, m.precipitation)
            CLOUD_COVER -> "%${m.cloudCover}"
            PRECIPITATION -> String.format(Locale.US, "%.1f mm", m.precipitation)
            RAIN -> fmtNum(extra, "rain", "%.1f mm")
            SHOWERS -> fmtNum(extra, "showers", "%.1f mm")
            SNOWFALL -> fmtNum(extra, "snowfall", "%.1f cm")
            PRECIP_PROB -> fmtInt(extra, "precip_prob", "%%%d")
            UV_INDEX -> String.format(Locale.US, "%.1f", m.uvIndex)
            SHORTWAVE_RAD -> fmtNum(extra, "shortwave_rad", "%.0f W/m²")
            DIRECT_RAD -> fmtNum(extra, "direct_rad", "%.0f W/m²")
            DIFFUSE_RAD -> fmtNum(extra, "diffuse_rad", "%.0f W/m²")
            SUNRISE_SUNSET -> {
                val sr = extra.optString("sunrise", "--:--")
                val ss = extra.optString("sunset", "--:--")
                "$sr / $ss"
            }
            SUNSHINE_DUR -> fmtNum(extra, "sunshine_hrs", "%.1f sa")
            DAYLIGHT_DUR -> fmtNum(extra, "daylight_hrs", "%.1f sa")
            WIND_SPEED -> String.format(Locale.US, "%.1f km/sa", m.windSpeed)
            WIND_DIR -> {
                if (extra.has("wind_dir") && !extra.isNull("wind_dir")) {
                    windDirectionLabel(extra.optInt("wind_dir", 0))
                } else "-"
            }
            WIND_GUSTS -> fmtNum(extra, "wind_gusts", "%.1f km/sa")
            PRESSURE_MSL -> fmtNum(extra, "pressure_msl", "%.1f hPa")
            SURFACE_PRESSURE -> fmtNum(extra, "surface_pressure", "%.1f hPa")
            VISIBILITY -> fmtNum(extra, "visibility_km", "%.1f km")
            CAPE -> fmtNum(extra, "cape", "%.0f J/kg")
            FREEZING_LEVEL -> fmtNum(extra, "freezing_level", "%.0f m")
            EVAPOTRANSPIRATION -> fmtNum(extra, "evapotranspiration", "%.2f mm")
            SOIL_TEMP -> fmtNum(extra, "soil_temp", "%.1f °C")
            SOIL_MOISTURE -> fmtNum(extra, "soil_moisture", "%.3f m³/m³")
            EUROPEAN_AQI -> {
                if (extra.has("eu_aqi") && !extra.isNull("eu_aqi")) {
                    val v = extra.optInt("eu_aqi", 0)
                    "$v (${aqiLabel(v)})"
                } else "-"
            }
            US_AQI -> fmtInt(extra, "us_aqi", "%d")
            PM2_5 -> fmtNum(extra, "pm2_5", "%.1f µg/m³")
            PM10 -> fmtNum(extra, "pm10", "%.1f µg/m³")
            OZONE -> fmtNum(extra, "ozone", "%.1f µg/m³")
            NO2 -> fmtNum(extra, "no2", "%.1f µg/m³")
            SO2 -> fmtNum(extra, "so2", "%.1f µg/m³")
            CO -> fmtNum(extra, "co", "%.0f µg/m³")
            DUST -> fmtNum(extra, "dust", "%.1f µg/m³")
            POLLEN -> {
                val p = extra.optString("pollen_text", "")
                if (p.isBlank()) "0 (Yok)" else p
            }
        }
    }

    fun excelNumberOrNull(m: WeatherMeasurement, extra: JSONObject = m.extraObj()): Double? {
        if (!isNumeric) return null
        return when (this) {
            TEMPERATURE -> m.temperature
            APPARENT_TEMP -> m.apparentTemp
            DEW_POINT -> optValidDouble(extra, "dew_point")
            HUMIDITY -> m.humidity.toDouble()
            CLOUD_COVER -> m.cloudCover.toDouble()
            PRECIPITATION -> m.precipitation
            RAIN -> optValidDouble(extra, "rain")
            SHOWERS -> optValidDouble(extra, "showers")
            SNOWFALL -> optValidDouble(extra, "snowfall")
            PRECIP_PROB -> optValidDouble(extra, "precip_prob")
            UV_INDEX -> m.uvIndex
            SHORTWAVE_RAD -> optValidDouble(extra, "shortwave_rad")
            DIRECT_RAD -> optValidDouble(extra, "direct_rad")
            DIFFUSE_RAD -> optValidDouble(extra, "diffuse_rad")
            SUNSHINE_DUR -> optValidDouble(extra, "sunshine_hrs")
            DAYLIGHT_DUR -> optValidDouble(extra, "daylight_hrs")
            WIND_SPEED -> m.windSpeed
            WIND_GUSTS -> optValidDouble(extra, "wind_gusts")
            PRESSURE_MSL -> optValidDouble(extra, "pressure_msl")
            SURFACE_PRESSURE -> optValidDouble(extra, "surface_pressure")
            VISIBILITY -> optValidDouble(extra, "visibility_km")
            CAPE -> optValidDouble(extra, "cape")
            FREEZING_LEVEL -> optValidDouble(extra, "freezing_level")
            EVAPOTRANSPIRATION -> optValidDouble(extra, "evapotranspiration")
            SOIL_TEMP -> optValidDouble(extra, "soil_temp")
            SOIL_MOISTURE -> optValidDouble(extra, "soil_moisture")
            US_AQI -> optValidDouble(extra, "us_aqi")
            PM2_5 -> optValidDouble(extra, "pm2_5")
            PM10 -> optValidDouble(extra, "pm10")
            OZONE -> optValidDouble(extra, "ozone")
            NO2 -> optValidDouble(extra, "no2")
            SO2 -> optValidDouble(extra, "so2")
            CO -> optValidDouble(extra, "co")
            DUST -> optValidDouble(extra, "dust")
            else -> null
        }
    }

    companion object {
        private fun optValidDouble(obj: JSONObject, key: String): Double? {
            if (!obj.has(key) || obj.isNull(key)) return null
            val v = obj.optDouble(key, Double.NaN)
            return if (v.isNaN() || v.isInfinite()) null else v
        }

        private fun fmtNum(obj: JSONObject, key: String, pattern: String): String {
            val v = optValidDouble(obj, key) ?: return "-"
            return String.format(Locale.US, pattern, v)
        }

        private fun fmtInt(obj: JSONObject, key: String, pattern: String): String {
            val v = optValidDouble(obj, key) ?: return "-"
            return String.format(Locale.US, pattern, v.toInt())
        }

        fun windDirectionLabel(degrees: Int): String {
            val dirs = listOf("K", "KD", "D", "GD", "G", "GB", "B", "KB")
            val norm = ((degrees % 360) + 360) % 360
            val idx = ((norm + 22) / 45) % 8
            return "${dirs[idx]} (${norm}°)"
        }

        fun aqiLabel(aqi: Int): String = when {
            aqi <= 20 -> "Çok İyi"
            aqi <= 40 -> "İyi"
            aqi <= 60 -> "Orta"
            aqi <= 80 -> "Kötü"
            aqi <= 100 -> "Çok Kötü"
            else -> "Tehlikeli"
        }
    }
}

class MetricPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("metric_prefs", Context.MODE_PRIVATE)

    fun getSelectedMetrics(): Set<WeatherMetric> {
        val saved = prefs.getStringSet("selected_metrics", null)
        if (saved == null) {
            return WeatherMetric.entries.filter { it.defaultEnabled }.toSet()
        }
        return saved.mapNotNull { name ->
            runCatching { WeatherMetric.valueOf(name) }.getOrNull()
        }.toSet()
    }

    fun saveSelectedMetrics(metrics: Set<WeatherMetric>) {
        prefs.edit().putStringSet("selected_metrics", metrics.map { it.name }.toSet()).apply()
    }
}
