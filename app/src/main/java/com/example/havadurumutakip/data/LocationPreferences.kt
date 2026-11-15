package com.example.havadurumutakip.data

import android.content.Context
import com.example.havadurumutakip.model.SavedLocation

class LocationPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    fun saveLocation(location: SavedLocation) {
        prefs.edit()
            .putString("loc_name", location.name)
            .putString("loc_lat", location.latitude.toString())
            .putString("loc_lon", location.longitude.toString())
            .apply()
    }

    fun getLocation(): SavedLocation {
        val name = prefs.getString("loc_name", null) ?: "İstanbul, Türkiye"
        val lat = prefs.getString("loc_lat", null)?.toDoubleOrNull() ?: 41.0082
        val lon = prefs.getString("loc_lon", null)?.toDoubleOrNull() ?: 28.9784
        return SavedLocation(name, lat, lon)
    }
}
