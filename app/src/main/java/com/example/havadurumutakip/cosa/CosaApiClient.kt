package com.example.havadurumutakip.cosa

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
            throw RuntimeException("Cosa giriş hatası: ${resp.optString("code", "login_failed")}")
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
            if (id.isNotBlank()) list.add(CosaEndpointSummary(id, name))
        }
        return list
    }

    fun getEndpointDetail(endpointId: String, token: String): CosaEndpointDetail {
        val resp = postJson("/api/endpoints/getEndpoint", JSONObject().put("endpoint", endpointId), token)
        if (resp.optInt("ok", 0) == 0) throw RuntimeException("API_${resp.optString("code", "error")}")
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

        val batteryPct = when (ep.optString("powerState", "")) {
            "level4" -> 100
            "level3" -> 75
            "level2" -> 50
            "level1" -> 25
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

    fun getReportsAnalyzed(endpointId: String, token: String): CosaDailyReport {
        val resp = postJson("/api/endpoints/getReportsAnalyzed", JSONObject().put("endpoint", endpointId), token)
        if (resp.optInt("ok", 0) == 0) throw RuntimeException("Rapor alınamadı")
        val report = resp.optJSONObject("report") ?: JSONObject()
        val summary = report.optJSONObject("summary") ?: JSONObject()
        val runtimes = summary.optJSONObject("runtimes") ?: JSONObject()
        val avgTemps = summary.optJSONObject("averageTemperatures") ?: JSONObject()
        val stats = report.optJSONObject("stats") ?: JSONObject()

        return CosaDailyReport(
            dateKey = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
            totalSec = runtimes.optLong("total", 0L),
            homeSec = runtimes.optLong("home", 0L),
            sleepSec = runtimes.optLong("sleep", 0L),
            awaySec = runtimes.optLong("away", 0L),
            customSec = runtimes.optLong("custom", 0L),
            frozenSec = runtimes.optLong("frozen", 0L),
            avgIndoor = safeDouble(avgTemps, "total") ?: 0.0,
            avgOutdoor = safeDouble(stats, "placeAverageTemperature") ?: 0.0,
            minIndoor = safeDouble(stats, "minTemperature") ?: 0.0,
            maxIndoor = safeDouble(stats, "maxTemperature") ?: 0.0,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun fetchSavedDeviceDetail(context: Context): CosaEndpointDetail? {
        val prefs = CosaPreferences(context)
        if (!prefs.isConnected()) return null
        val endpointId = prefs.getEndpointId() ?: return null
        if (prefs.getToken().isNullOrBlank()) return null
        return withAutoRelogin(prefs) { activeToken -> getEndpointDetail(endpointId, activeToken) }
    }

    fun fetchSavedReport(context: Context): CosaDailyReport? {
        val prefs = CosaPreferences(context)
        if (!prefs.isConnected()) return null
        val endpointId = prefs.getEndpointId() ?: return null
        if (prefs.getToken().isNullOrBlank()) return null
        return withAutoRelogin(prefs) { activeToken -> getReportsAnalyzed(endpointId, activeToken) }
    }

    private fun <T> withAutoRelogin(prefs: CosaPreferences, block: (String) -> T): T {
        val token = prefs.getToken() ?: throw RuntimeException("Token yok")
        return try {
            block(token)
        } catch (e: Exception) {
            val email = prefs.getEmail()
            val pass = prefs.getPassword()
            if (email.isNotBlank() && pass.isNotBlank()) {
                val newToken = login(email, pass)
                prefs.updateToken(newToken)
                block(newToken)
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
            if (!token.isNullOrBlank()) setRequestProperty("authtoken", token)
        }
        return try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw RuntimeException("HTTP $code: $text")
            JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
