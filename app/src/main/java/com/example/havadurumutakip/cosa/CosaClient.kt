package com.example.havadurumutakip.cosa

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CosaEndpointSummary(
    val id: String,
    val name: String
)

data class CosaEndpointDetail(
    val endpointId: String,
    val name: String,
    val temperature: Double,
    val humidity: Int,
    val targetTemperature: Double,
    val combiOn: Boolean,
    val mode: String,
    val option: String,
    val batteryPercent: Int,
    val batteryVoltage: Double,
    val rssi: Int
) {
    fun modeOptionLabel(): String {
        val m = when (mode) {
            "manual" -> "Manuel"
            "auto" -> "Otomatik"
            "schedule" -> "Haftalık"
            "turbo" -> "Turbo"
            "ventilation" -> "Havalandırma"
            else -> mode
        }
        val o = when (option) {
            "home" -> "Evde"
            "sleep" -> "Uyku"
            "away" -> "Dışarı"
            "custom" -> "Özel"
            "frozen" -> "Donma Koruma"
            else -> option
        }
        return if (o.isNotBlank() && mode == "manual") "$m ($o)"
        else if (o.isNotBlank()) "$m • $o"
        else m
    }
}

class CosaPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("cosa_prefs", Context.MODE_PRIVATE)

    fun saveSession(
        email: String,
        password: String,
        token: String,
        endpointId: String,
        endpointName: String
    ) {
        prefs.edit()
            .putString("email", email)
            .putString("password", password)
            .putString("token", token)
            .putString("endpoint_id", endpointId)
            .putString("endpoint_name", endpointName)
            .apply()
    }

    fun updateToken(token: String) {
        prefs.edit().putString("token", token).apply()
    }

    fun isConnected(): Boolean =
        !getToken().isNullOrBlank() && !getEndpointId().isNullOrBlank()

    fun getEmail(): String = prefs.getString("email", "") ?: ""
    fun getPassword(): String = prefs.getString("password", "") ?: ""
    fun getToken(): String? = prefs.getString("token", null)
    fun getEndpointId(): String? = prefs.getString("endpoint_id", null)
    fun getEndpointName(): String = prefs.getString("endpoint_name", "Cosa") ?: "Cosa"

    fun clear() {
        prefs.edit().clear().apply()
    }
}

object CosaApiClient {
    private const val BASE_URL = "https://kiwi-api.nuvia.com.tr"
    private const val USER_AGENT = "Cosa/1 CFNetwork/3860.200.71 Darwin/25.1.0"

    fun login(email: String, password: String): String {
        val body = JSONObject().apply {
            put("email", email.trim())
            put("password", password)
        }
        val resp = postJson("/api/users/login", body, token = null)
        if (resp.optInt("ok", 0) == 0) {
            val code = resp.optString("code", "login_failed")
            throw RuntimeException("Cosa giriş hatası: $code")
        }
        val token = resp.optString("authToken", "")
        if (token.isBlank()) throw RuntimeException("Cosa token alınamadı")
        return token
    }

    fun getEndpoints(token: String): List<CosaEndpointSummary> {
        val resp = postJson("/api/endpoints/getEndpoints", JSONObject(), token)
        if (resp.optInt("ok", 0) == 0) {
            throw RuntimeException("Cihaz listesi alınamadı (${resp.optString("code")})")
        }
        val arr = resp.optJSONArray("endpoints") ?: return emptyList()
        val list = mutableListOf<CosaEndpointSummary>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val id = item.optString("id", "").ifBlank { item.optString("_id", "") }
            val name = item.optString("name", "Cosa Termostat")
            if (id.isNotBlank()) {
                list.add(CosaEndpointSummary(id, name))
            }
        }
        return list
    }

    fun getEndpointDetail(endpointId: String, token: String): CosaEndpointDetail {
        val body = JSONObject().apply { put("endpoint", endpointId) }
        val resp = postJson("/api/endpoints/getEndpoint", body, token)
        if (resp.optInt("ok", 0) == 0) {
            throw RuntimeException("API_${resp.optString("code", "error")}")
        }
        val ep = resp.getJSONObject("endpoint")
        val mode = ep.optString("mode", "")
        val option = ep.optString("option", "")

        val targetTemp = when (option) {
            "home" -> safeDouble(ep, "homeTemperature")
            "away" -> safeDouble(ep, "awayTemperature")
            "sleep" -> safeDouble(ep, "sleepTemperature")
            "custom" -> safeDouble(ep, "customTemperature")
            else -> null
        } ?: safeDouble(ep, "targetTemperature") ?: 0.0

        val powerState = ep.optString("powerState", "")
        val batteryPct = when (powerState) {
            "level4" -> 100
            "level3" -> 75
            "level2" -> 50
            "level1" -> 25
            "level0" -> 0
            else -> 0
        }

        return CosaEndpointDetail(
            endpointId = endpointId,
            name = ep.optString("name", "Cosa Termostat"),
            temperature = safeDouble(ep, "temperature") ?: 0.0,
            humidity = (safeDouble(ep, "humidity") ?: 0.0).toInt(),
            targetTemperature = targetTemp,
            combiOn = ep.optString("combiState", "off") == "on",
            mode = mode,
            option = option,
            batteryPercent = batteryPct,
            batteryVoltage = safeDouble(ep, "batteryVoltage") ?: 0.0,
            rssi = ep.optInt("rssi", 0)
        )
    }

    fun fetchSavedDeviceDetail(context: Context): CosaEndpointDetail? {
        val prefs = CosaPreferences(context)
        if (!prefs.isConnected()) return null
        val endpointId = prefs.getEndpointId() ?: return null
        val token = prefs.getToken() ?: return null

        return try {
            getEndpointDetail(endpointId, token)
        } catch (e: Exception) {
            val email = prefs.getEmail()
            val pass = prefs.getPassword()
            if (email.isNotBlank() && pass.isNotBlank()) {
                val newToken = login(email, pass)
                prefs.updateToken(newToken)
                getEndpointDetail(endpointId, newToken)
            } else {
                throw e
            }
        }
    }

    private fun safeDouble(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val v = obj.optDouble(key, Double.NaN)
        return if (v.isNaN() || v.isInfinite()) null else v
    }

    private fun postJson(path: String, payload: JSONObject, token: String?): JSONObject {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("provider", "cosa")
            setRequestProperty("Accept", "*/*")
            if (!token.isNullOrBlank()) {
                setRequestProperty("authtoken", token)
            }
        }
        return try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code: $text")
            }
            JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
