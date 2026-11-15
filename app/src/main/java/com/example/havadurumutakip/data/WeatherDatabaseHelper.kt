package com.example.havadurumutakip.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.havadurumutakip.cosa.CosaDailyReport
import com.example.havadurumutakip.cosa.TempBandStat
import com.example.havadurumutakip.model.DailySummary
import com.example.havadurumutakip.model.WeatherMeasurement
import org.json.JSONObject

class WeatherDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "weather_history.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE measurements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date_key TEXT NOT NULL,
                time_label TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                location_name TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                temperature REAL NOT NULL,
                apparent_temp REAL NOT NULL DEFAULT 0.0,
                humidity INTEGER NOT NULL,
                wind_speed REAL NOT NULL,
                precipitation REAL NOT NULL DEFAULT 0.0,
                cloud_cover INTEGER NOT NULL DEFAULT 0,
                is_day INTEGER NOT NULL DEFAULT 1,
                uv_index REAL NOT NULL DEFAULT 0.0,
                weather_code INTEGER NOT NULL,
                extra_json TEXT NOT NULL DEFAULT '{}'
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_date_loc ON measurements(date_key, location_name)")
        createReportsTable(db)
    }

    private fun createReportsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cosa_daily_reports (
                date_key TEXT PRIMARY KEY,
                total_sec INTEGER NOT NULL,
                home_sec INTEGER NOT NULL,
                sleep_sec INTEGER NOT NULL,
                away_sec INTEGER NOT NULL,
                custom_sec INTEGER NOT NULL,
                frozen_sec INTEGER NOT NULL,
                avg_indoor REAL NOT NULL,
                avg_outdoor REAL NOT NULL,
                min_indoor REAL NOT NULL,
                max_indoor REAL NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE measurements ADD COLUMN apparent_temp REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE measurements ADD COLUMN precipitation REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE measurements ADD COLUMN cloud_cover INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE measurements ADD COLUMN is_day INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE measurements ADD COLUMN uv_index REAL NOT NULL DEFAULT 0.0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE measurements ADD COLUMN extra_json TEXT NOT NULL DEFAULT '{}'")
        }
        if (oldVersion < 4) {
            createReportsTable(db)
        }
    }

    fun insertMeasurement(m: WeatherMeasurement): Long {
        val values = ContentValues().apply {
            put("date_key", m.dateKey); put("time_label", m.timeLabel); put("timestamp", m.timestamp)
            put("location_name", m.locationName); put("latitude", m.latitude); put("longitude", m.longitude)
            put("temperature", m.temperature); put("apparent_temp", m.apparentTemp); put("humidity", m.humidity)
            put("wind_speed", m.windSpeed); put("precipitation", m.precipitation); put("cloud_cover", m.cloudCover)
            put("is_day", if (m.isDay) 1 else 0); put("uv_index", m.uvIndex); put("weather_code", m.weatherCode)
            put("extra_json", m.extraJson)
        }
        return writableDatabase.insert("measurements", null, values)
    }

    fun upsertCosaReport(r: CosaDailyReport) {
        val values = ContentValues().apply {
            put("date_key", r.dateKey); put("total_sec", r.totalSec); put("home_sec", r.homeSec)
            put("sleep_sec", r.sleepSec); put("away_sec", r.awaySec); put("custom_sec", r.customSec)
            put("frozen_sec", r.frozenSec); put("avg_indoor", r.avgIndoor); put("avg_outdoor", r.avgOutdoor)
            put("min_indoor", r.minIndoor); put("max_indoor", r.maxIndoor); put("updated_at", r.updatedAt)
        }
        writableDatabase.insertWithOnConflict("cosa_daily_reports", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getCosaReports(): List<CosaDailyReport> {
        val list = mutableListOf<CosaDailyReport>()
        val sql = "SELECT date_key, total_sec, home_sec, sleep_sec, away_sec, custom_sec, frozen_sec, avg_indoor, avg_outdoor, min_indoor, max_indoor, updated_at FROM cosa_daily_reports ORDER BY date_key DESC"
        readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                list.add(CosaDailyReport(c.getString(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getLong(4), c.getLong(5), c.getLong(6), c.getDouble(7), c.getDouble(8), c.getDouble(9), c.getDouble(10), c.getLong(11)))
            }
        }
        return list
    }

    fun getLatestCosaReport(): CosaDailyReport? = getCosaReports().firstOrNull()

    fun getTemperatureBandStats(primaryLocationName: String?): List<TempBandStat> {
        val sql = "SELECT date_key, time_label, location_name, temperature, extra_json FROM measurements WHERE weather_code >= 0 AND extra_json LIKE '%cosa_combi%' ORDER BY timestamp ASC"
        data class Sample(val temp: Double, val on: Boolean, val isPrimary: Boolean)
        val bySlot = LinkedHashMap<String, Sample>()
        readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                val slot = "${c.getString(0)}|${c.getString(1)}"
                val loc = c.getString(2)
                val temp = c.getDouble(3)
                val extra = runCatching { JSONObject(c.getString(4)) }.getOrNull()
                val combiStr = extra?.optString("cosa_combi", "") ?: ""
                if (combiStr.isBlank()) continue
                val isOn = combiStr.contains("Yanıyor", ignoreCase = true)
                val isPrimary = loc == primaryLocationName
                val existing = bySlot[slot]
                if (existing == null || (!existing.isPrimary && isPrimary)) bySlot[slot] = Sample(temp, isOn, isPrimary)
            }
        }
        val bands = listOf(
            "0 °C altı" to { t: Double -> t < 0.0 },
            "0 – 5 °C" to { t: Double -> t >= 0.0 && t < 5.0 },
            "5 – 10 °C" to { t: Double -> t >= 5.0 && t < 10.0 },
            "10 – 15 °C" to { t: Double -> t >= 10.0 && t < 15.0 },
            "15 °C üstü" to { t: Double -> t >= 15.0 }
        )
        return bands.map { (label, pred) ->
            val matched = bySlot.values.filter { pred(it.temp) }
            TempBandStat(label, matched.size, matched.count { it.on })
        }
    }

    fun deleteDay(dateKey: String, locationName: String): Int =
        writableDatabase.delete("measurements", "date_key = ? AND location_name = ?", arrayOf(dateKey, locationName))

    fun deleteMeasurementById(id: Long): Int =
        writableDatabase.delete("measurements", "id = ?", arrayOf(id.toString()))

    fun getDailySummaries(): List<DailySummary> {
        val list = mutableListOf<DailySummary>()
        val sql = "SELECT date_key, location_name, MIN(temperature), MAX(temperature), AVG(temperature), SUM(precipitation), AVG(cloud_cover), MAX(uv_index), COUNT(*) FROM measurements GROUP BY date_key, location_name ORDER BY date_key DESC, MAX(timestamp) DESC"
        readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) list.add(DailySummary(c.getString(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getDouble(5), c.getInt(6), c.getDouble(7), c.getInt(8)))
        }
        return list
    }

    fun getHourlyForDay(dateKey: String, locationName: String): List<WeatherMeasurement> {
        val list = mutableListOf<WeatherMeasurement>()
        val sql = "SELECT id, date_key, time_label, timestamp, location_name, latitude, longitude, temperature, apparent_temp, humidity, wind_speed, precipitation, cloud_cover, is_day, uv_index, weather_code, extra_json FROM measurements WHERE date_key = ? AND location_name = ? ORDER BY timestamp ASC"
        readableDatabase.rawQuery(sql, arrayOf(dateKey, locationName)).use { c -> while (c.moveToNext()) list.add(readMeasurement(c)) }
        return list
    }

    fun getAllMeasurements(): List<WeatherMeasurement> {
        val list = mutableListOf<WeatherMeasurement>()
        val sql = "SELECT id, date_key, time_label, timestamp, location_name, latitude, longitude, temperature, apparent_temp, humidity, wind_speed, precipitation, cloud_cover, is_day, uv_index, weather_code, extra_json FROM measurements ORDER BY timestamp DESC"
        readableDatabase.rawQuery(sql, null).use { c -> while (c.moveToNext()) list.add(readMeasurement(c)) }
        return list
    }

    fun getLatestMeasurementForLocation(locationName: String): WeatherMeasurement? {
        val sql = "SELECT id, date_key, time_label, timestamp, location_name, latitude, longitude, temperature, apparent_temp, humidity, wind_speed, precipitation, cloud_cover, is_day, uv_index, weather_code, extra_json FROM measurements WHERE location_name = ? ORDER BY timestamp DESC LIMIT 1"
        readableDatabase.rawQuery(sql, arrayOf(locationName)).use { c -> if (c.moveToFirst()) return readMeasurement(c) }
        return null
    }

    fun getDaySummary(dateKey: String, locationName: String): DailySummary? {
        val sql = "SELECT date_key, location_name, MIN(temperature), MAX(temperature), AVG(temperature), SUM(precipitation), AVG(cloud_cover), MAX(uv_index), COUNT(*) FROM measurements WHERE date_key = ? AND location_name = ? GROUP BY date_key, location_name"
        readableDatabase.rawQuery(sql, arrayOf(dateKey, locationName)).use { c ->
            if (c.moveToFirst()) return DailySummary(c.getString(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getDouble(5), c.getInt(6), c.getDouble(7), c.getInt(8))
        }
        return null
    }

    private fun readMeasurement(c: Cursor): WeatherMeasurement = WeatherMeasurement(
        id = c.getLong(0), dateKey = c.getString(1), timeLabel = c.getString(2), timestamp = c.getLong(3),
        locationName = c.getString(4), latitude = c.getDouble(5), longitude = c.getDouble(6),
        temperature = c.getDouble(7), apparentTemp = c.getDouble(8), humidity = c.getInt(9),
        windSpeed = c.getDouble(10), precipitation = c.getDouble(11), cloudCover = c.getInt(12),
        isDay = c.getInt(13) == 1, uvIndex = c.getDouble(14), weatherCode = c.getInt(15), extraJson = c.getString(16) ?: "{}"
    )
}
