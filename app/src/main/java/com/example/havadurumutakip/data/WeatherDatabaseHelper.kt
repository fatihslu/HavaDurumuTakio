package com.example.havadurumutakip.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.havadurumutakip.model.DailySummary
import com.example.havadurumutakip.model.WeatherMeasurement

class WeatherDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "weather_history.db", null, 3) {

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
    }

    fun insertMeasurement(m: WeatherMeasurement): Long {
        val values = ContentValues().apply {
            put("date_key", m.dateKey)
            put("time_label", m.timeLabel)
            put("timestamp", m.timestamp)
            put("location_name", m.locationName)
            put("latitude", m.latitude)
            put("longitude", m.longitude)
            put("temperature", m.temperature)
            put("apparent_temp", m.apparentTemp)
            put("humidity", m.humidity)
            put("wind_speed", m.windSpeed)
            put("precipitation", m.precipitation)
            put("cloud_cover", m.cloudCover)
            put("is_day", if (m.isDay) 1 else 0)
            put("uv_index", m.uvIndex)
            put("weather_code", m.weatherCode)
            put("extra_json", m.extraJson)
        }
        return writableDatabase.insert("measurements", null, values)
    }

    fun deleteDay(dateKey: String, locationName: String): Int {
        return writableDatabase.delete(
            "measurements",
            "date_key = ? AND location_name = ?",
            arrayOf(dateKey, locationName)
        )
    }

    fun deleteMeasurementById(id: Long): Int {
        return writableDatabase.delete(
            "measurements",
            "id = ?",
            arrayOf(id.toString())
        )
    }

    fun getDailySummaries(): List<DailySummary> {
        val list = mutableListOf<DailySummary>()
        val sql = """
            SELECT
                date_key,
                location_name,
                MIN(temperature) AS min_temp,
                MAX(temperature) AS max_temp,
                AVG(temperature) AS avg_temp,
                SUM(precipitation) AS total_precip,
                AVG(cloud_cover) AS avg_cloud,
                MAX(uv_index) AS max_uv,
                COUNT(*) AS cnt
            FROM measurements
            GROUP BY date_key, location_name
            ORDER BY date_key DESC, MAX(timestamp) DESC
        """.trimIndent()

        readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    DailySummary(
                        dateKey = cursor.getString(0),
                        locationName = cursor.getString(1),
                        minTemp = cursor.getDouble(2),
                        maxTemp = cursor.getDouble(3),
                        avgTemp = cursor.getDouble(4),
                        totalPrecipitation = cursor.getDouble(5),
                        avgCloudCover = cursor.getInt(6),
                        maxUvIndex = cursor.getDouble(7),
                        measurementCount = cursor.getInt(8)
                    )
                )
            }
        }
        return list
    }

    fun getHourlyForDay(dateKey: String, locationName: String): List<WeatherMeasurement> {
        val list = mutableListOf<WeatherMeasurement>()
        val sql = """
            SELECT id, date_key, time_label, timestamp, location_name,
                   latitude, longitude, temperature, apparent_temp, humidity,
                   wind_speed, precipitation, cloud_cover, is_day, uv_index, weather_code, extra_json
            FROM measurements
            WHERE date_key = ? AND location_name = ?
            ORDER BY timestamp ASC
        """.trimIndent()

        readableDatabase.rawQuery(sql, arrayOf(dateKey, locationName)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(readMeasurement(cursor))
            }
        }
        return list
    }

    fun getAllMeasurements(): List<WeatherMeasurement> {
        val list = mutableListOf<WeatherMeasurement>()
        val sql = """
            SELECT id, date_key, time_label, timestamp, location_name,
                   latitude, longitude, temperature, apparent_temp, humidity,
                   wind_speed, precipitation, cloud_cover, is_day, uv_index, weather_code, extra_json
            FROM measurements
            ORDER BY timestamp DESC
        """.trimIndent()

        readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(readMeasurement(cursor))
            }
        }
        return list
    }

    fun getLatestMeasurementForLocation(locationName: String): WeatherMeasurement? {
        val sql = """
            SELECT id, date_key, time_label, timestamp, location_name,
                   latitude, longitude, temperature, apparent_temp, humidity,
                   wind_speed, precipitation, cloud_cover, is_day, uv_index, weather_code, extra_json
            FROM measurements
            WHERE location_name = ?
            ORDER BY timestamp DESC
            LIMIT 1
        """.trimIndent()

        readableDatabase.rawQuery(sql, arrayOf(locationName)).use { cursor ->
            if (cursor.moveToFirst()) {
                return readMeasurement(cursor)
            }
        }
        return null
    }

    fun getDaySummary(dateKey: String, locationName: String): DailySummary? {
        val sql = """
            SELECT
                date_key,
                location_name,
                MIN(temperature) AS min_temp,
                MAX(temperature) AS max_temp,
                AVG(temperature) AS avg_temp,
                SUM(precipitation) AS total_precip,
                AVG(cloud_cover) AS avg_cloud,
                MAX(uv_index) AS max_uv,
                COUNT(*) AS cnt
            FROM measurements
            WHERE date_key = ? AND location_name = ?
            GROUP BY date_key, location_name
        """.trimIndent()

        readableDatabase.rawQuery(sql, arrayOf(dateKey, locationName)).use { cursor ->
            if (cursor.moveToFirst()) {
                return DailySummary(
                    dateKey = cursor.getString(0),
                    locationName = cursor.getString(1),
                    minTemp = cursor.getDouble(2),
                    maxTemp = cursor.getDouble(3),
                    avgTemp = cursor.getDouble(4),
                    totalPrecipitation = cursor.getDouble(5),
                    avgCloudCover = cursor.getInt(6),
                    maxUvIndex = cursor.getDouble(7),
                    measurementCount = cursor.getInt(8)
                )
            }
        }
        return null
    }

    private fun readMeasurement(cursor: Cursor): WeatherMeasurement {
        return WeatherMeasurement(
            id = cursor.getLong(0),
            dateKey = cursor.getString(1),
            timeLabel = cursor.getString(2),
            timestamp = cursor.getLong(3),
            locationName = cursor.getString(4),
            latitude = cursor.getDouble(5),
            longitude = cursor.getDouble(6),
            temperature = cursor.getDouble(7),
            apparentTemp = cursor.getDouble(8),
            humidity = cursor.getInt(9),
            windSpeed = cursor.getDouble(10),
            precipitation = cursor.getDouble(11),
            cloudCover = cursor.getInt(12),
            isDay = cursor.getInt(13) == 1,
            uvIndex = cursor.getDouble(14),
            weatherCode = cursor.getInt(15),
            extraJson = cursor.getString(16) ?: "{}"
        )
    }
}
