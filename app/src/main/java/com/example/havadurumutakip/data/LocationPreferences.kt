package com.example.havadurumutakip.data

import android.content.Context
import com.example.havadurumutakip.model.SavedLocation
import org.json.JSONArray
import org.json.JSONObject

class LocationPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    fun getLocations(): List<SavedLocation> {
        val raw = prefs.getString("locations_json", null)
        if (raw != null) {
            return runCatching {
                val arr = JSONArray(raw)
                List(arr.length()) { i ->
                    val obj = arr.getJSONObject(i)
                    SavedLocation(
                        name = obj.getString("name"),
                        latitude = obj.getDouble("lat"),
                        longitude = obj.getDouble("lon")
                    )
                }
            }.getOrElse { emptyList() }
        }

        // Eski tekli konum kaydından otomatik geçiş
        val legacyName = prefs.getString("loc_name", null) ?: "İstanbul, Türkiye"
        val legacyLat = prefs.getString("loc_lat", null)?.toDoubleOrNull() ?: 41.0082
        val legacyLon = prefs.getString("loc_lon", null)?.toDoubleOrNull() ?: 28.9784
        val initial = listOf(SavedLocation(legacyName, legacyLat, legacyLon))
        saveLocations(initial)
        setPrimaryLocationName(legacyName)
        return initial
    }

    private fun saveLocations(list: List<SavedLocation>) {
        val arr = JSONArray()
        for (loc in list) {
            arr.put(
                JSONObject().apply {
                    put("name", loc.name)
                    put("lat", loc.latitude)
                    put("lon", loc.longitude)
                }
            )
        }
        prefs.edit().putString("locations_json", arr.toString()).apply()
    }

    fun addLocation(location: SavedLocation) {
        val current = getLocations().toMutableList()
        val exists = current.any { it.name.equals(location.name, ignoreCase = true) }
        if (!exists) {
            current.add(location)
            saveLocations(current)
        }
        if (getPrimaryLocationName() == null || current.size == 1) {
            setPrimaryLocationName(location.name)
        }
    }

    fun removeLocation(name: String) {
        val current = getLocations().filterNot { it.name == name }
        saveLocations(current)
        if (getPrimaryLocationName() == name) {
            setPrimaryLocationName(current.firstOrNull()?.name)
        }
    }

    fun setPrimaryLocationName(name: String?) {
        prefs.edit().putString("primary_loc_name", name).apply()
    }

    fun getPrimaryLocationName(): String? {
        return prefs.getString("primary_loc_name", null)
    }

    fun getPrimaryLocation(): SavedLocation? {
        val list = getLocations()
        val primaryName = getPrimaryLocationName()
        return list.firstOrNull { it.name == primaryName } ?: list.firstOrNull()
    }
}
