package com.example.havadurumutakip.cosa

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CosaEndpointSummary(val id: String, val name: String)

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

data class CosaDailyReport(
    val dateKey: String,
    val totalSec: Long,
    val homeSec: Long,
    val sleepSec: Long,
    val awaySec: Long,
    val customSec: Long,
    val frozenSec: Long,
    val avgIndoor: Double,
    val avgOutdoor: Double,
    val minIndoor: Double,
    val maxIndoor: Double,
    val updatedAt: Long
)

data class TempBandStat(val label: String, val totalSamples: Int, val combiOnSamples: Int) {
    val activeRatio: Double get() = if (totalSamples > 0) combiOnSamples.toDouble() / totalSamples else 0.0
}

class GasTariffPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("gas_tariff_prefs", Context.MODE_PRIVATE)
    fun getM3PerHour(): Double = prefs.getString("m3_per_hour", null)?.toDoubleOrNull() ?: 1.5
    fun getPricePerM3(): Double = prefs.getString("price_per_m3", null)?.toDoubleOrNull() ?: 8.5
    fun save(m3PerHour: Double, pricePerM3: Double) {
        prefs.edit().putString("m3_per_hour", m3PerHour.toString()).putString("price_per_m3", pricePerM3.toString()).apply()
    }
}

private object KeystoreCrypto {
    private const val ALIAS = "cosa_aes_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val enc = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + enc.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(enc, 0, combined, iv.size, enc.size)
            "ENC:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrDefault("")
    }

    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        if (!stored.startsWith("ENC:")) return stored
        return runCatching {
            val combined = Base64.decode(stored.removePrefix("ENC:"), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, combined.copyOfRange(0, 12)))
            String(cipher.doFinal(combined.copyOfRange(12, combined.size)), Charsets.UTF_8)
        }.getOrDefault("")
    }
}

class CosaPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("cosa_prefs", Context.MODE_PRIVATE)
    fun saveSession(email: String, password: String, token: String, endpointId: String, endpointName: String) {
        prefs.edit().putString("email", email).putString("password_enc", KeystoreCrypto.encrypt(password))
            .remove("password").putString("token", token).putString("endpoint_id", endpointId).putString("endpoint_name", endpointName).apply()
    }
    fun updateToken(token: String) { prefs.edit().putString("token", token).apply() }
    fun isConnected(): Boolean = !getToken().isNullOrBlank() && !getEndpointId().isNullOrBlank()
    fun getEmail(): String = prefs.getString("email", "") ?: ""
    fun getPassword(): String {
        val enc = prefs.getString("password_enc", null)
        return if (!enc.isNullOrBlank()) KeystoreCrypto.decrypt(enc) else prefs.getString("password", "") ?: ""
    }
    fun getToken(): String? = prefs.getString("token", null)
    fun getEndpointId(): String? = prefs.getString("endpoint_id", null)
    fun getEndpointName(): String = prefs.getString("endpoint_name", "Cosa") ?: "Cosa"
    fun clear() { prefs.edit().clear().apply() }
}
