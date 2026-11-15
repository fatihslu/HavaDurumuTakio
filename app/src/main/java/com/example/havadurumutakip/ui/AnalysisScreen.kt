package com.example.havadurumutakip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havadurumutakip.GlassCard
import com.example.havadurumutakip.GlassChipButton
import com.example.havadurumutakip.cosa.CosaDailyReport
import com.example.havadurumutakip.cosa.CosaPreferences
import com.example.havadurumutakip.cosa.GasTariffPreferences
import com.example.havadurumutakip.cosa.TempBandStat
import com.example.havadurumutakip.data.LocationPreferences
import com.example.havadurumutakip.data.WeatherDatabaseHelper
import com.example.havadurumutakip.data.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tariffPrefs = remember { GasTariffPreferences(context) }
    val cosaConnected = remember { CosaPreferences(context).isConnected() }

    var m3PerHour by remember { mutableStateOf(tariffPrefs.getM3PerHour()) }
    var pricePerM3 by remember { mutableStateOf(tariffPrefs.getPricePerM3()) }
    var reports by remember { mutableStateOf<List<CosaDailyReport>>(emptyList()) }
    var bandStats by remember { mutableStateOf<List<TempBandStat>>(emptyList()) }
    var showTariffDialog by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun loadData() {
        withContext(Dispatchers.IO) {
            val db = WeatherDatabaseHelper(context)
            val primaryName = LocationPreferences(context).getPrimaryLocation()?.name
            reports = db.getCosaReports()
            bandStats = db.getTemperatureBandStats(primaryName)
            db.close()
        }
    }

    LaunchedEffect(Unit) {
        loadData()
        if (cosaConnected && reports.isEmpty()) {
            refreshing = true
            WeatherRepository.refreshCosaReportOnly(context)
            loadData()
            refreshing = false
        }
    }

    val latest = reports.firstOrNull()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White),
                title = { Text("Kombi & Maliyet Analizi", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    GlassChipButton(text = "← Geri", onClick = onBack, modifier = Modifier.padding(start = 8.dp, end = 6.dp))
                },
                actions = {
                    if (cosaConnected) {
                        GlassChipButton(
                            text = if (refreshing) "..." else "↻ Yenile",
                            enabled = !refreshing,
                            onClick = {
                                scope.launch {
                                    refreshing = true
                                    WeatherRepository.refreshCosaReportOnly(context)
                                    loadData()
                                    refreshing = false
                                }
                            },
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hesaplama Tarifesi", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            Text(
                                text = String.format(Locale.US, "Tüketim: %.2f m³/saat  •  Fiyat: %.2f TL/m³", m3PerHour, pricePerM3),
                                fontSize = 12.sp,
                                color = Color(0xFFBAE6FD)
                            )
                        }
                        GlassChipButton(text = "✏️ Ayarla", onClick = { showTariffDialog = true })
                    }
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), warmHighlight = true) {
                    Text("Son 24 Saat (Cosa Raporu)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    if (latest != null) {
                        val totalHrs = latest.totalSec / 3600.0
                        val estM3 = totalHrs * m3PerHour
                        val estCost = estM3 * pricePerM3

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCol("Çalışma", formatDuration(latest.totalSec), Color(0xFF38BDF8))
                            StatCol("Tahmini m³", String.format(Locale.US, "%.2f m³", estM3), Color(0xFF2DD4BF))
                            StatCol("Tahmini Tutar", String.format(Locale.US, "%.1f TL", estCost), Color(0xFFFDBA74))
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Modlara Göre Dağılım:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f))
                        Spacer(modifier = Modifier.height(4.dp))
                        ModeCostRow("Evde", latest.homeSec, m3PerHour, pricePerM3)
                        ModeCostRow("Uyku", latest.sleepSec, m3PerHour, pricePerM3)
                        ModeCostRow("Dışarı", latest.awaySec, m3PerHour, pricePerM3)
                        ModeCostRow("Manuel", latest.customSec, m3PerHour, pricePerM3)

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "Ort. Oda: %.1f°C  •  Ort. Dış: %.1f°C  •  30 Gün Tahmini: ~%.0f TL",
                                latest.avgIndoor, latest.avgOutdoor, estCost * 30.0
                            ),
                            fontSize = 11.sp,
                            color = Color(0xFFE0F2FE)
                        )
                    } else {
                        Text(
                            if (cosaConnected) "Henüz rapor çekilmedi. Sağ üstten '↻ Yenile'ye basın."
                            else "Analiz için önce ana ekrandan Cosa v5'i bağlayın.",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
                    Text("Dış Sıcaklığa Göre Kombi Analizi", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    Text(
                        "Saatlik ölçümler biriktikçe hangi havada ne kadar yaktığı burada toplanır.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val hasAny = bandStats.any { it.totalSamples > 0 }
                    if (!hasAny) {
                        Text("Henüz Cosa + Dış Sıcaklık ortak saatlik ölçümü yok.", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                    } else {
                        bandStats.filter { it.totalSamples > 0 }.forEach { b ->
                            val pct = (b.activeRatio * 100.0).toInt()
                            val estM3 = b.combiOnSamples * m3PerHour
                            val estTl = estM3 * pricePerM3
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(b.label, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 13.sp)
                                    Text("${b.totalSamples} ölçümde ${b.combiOnSamples} kez aktifti (%$pct)", fontSize = 11.sp, color = Color.White.copy(alpha = 0.70f))
                                }
                                Text(
                                    text = String.format(Locale.US, "~%.1f m³  •  ~%.0f TL", estM3, estTl),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFDBA74)
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        }
                    }
                }
            }

            if (reports.isNotEmpty()) {
                item {
                    Text("Günlük Kombi Kayıtları", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                items(reports, key = { it.dateKey }) { r ->
                    val hrs = r.totalSec / 3600.0
                    val estM3 = hrs * m3PerHour
                    val estTl = estM3 * pricePerM3
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(r.dateKey, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                Text("Süre: ${formatDuration(r.totalSec)}  •  Oda: ${String.format(Locale.US, "%.1f°", r.avgIndoor)}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(String.format(Locale.US, "~%.1f TL", estTl), fontWeight = FontWeight.Bold, color = Color(0xFFFDBA74), fontSize = 15.sp)
                                Text(String.format(Locale.US, "~%.2f m³", estM3), fontSize = 11.sp, color = Color(0xFF2DD4BF))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTariffDialog) {
        var m3Text by remember { mutableStateOf(String.format(Locale.US, "%.2f", m3PerHour)) }
        var priceText by remember { mutableStateOf(String.format(Locale.US, "%.2f", pricePerM3)) }
        AlertDialog(
            containerColor = Color(0xF5131C31), titleContentColor = Color.White, textContentColor = Color(0xFFCBD5E1),
            onDismissRequest = { showTariffDialog = false },
            title = { Text("Doğalgaz Tarife Ayarı") },
            text = {
                Column {
                    OutlinedTextField(value = m3Text, onValueChange = { m3Text = it }, label = { Text("Kombi tüketimi (m³/saat)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = priceText, onValueChange = { priceText = it }, label = { Text("Birim fiyat (TL / m³)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("İpucu: 1 gün sayaç farkını Cosa çalışma saatine bölerek m³/saat değerinizi netleştirebilirsiniz.", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
            },
            confirmButton = {
                Button(onClick = {
                    val m3 = m3Text.replace(',', '.').toDoubleOrNull()?.coerceIn(0.1, 10.0) ?: m3PerHour
                    val pr = priceText.replace(',', '.').toDoubleOrNull()?.coerceIn(0.1, 200.0) ?: pricePerM3
                    tariffPrefs.save(m3, pr)
                    m3PerHour = m3
                    pricePerM3 = pr
                    showTariffDialog = false
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { showTariffDialog = false }) { Text("İptal", color = Color(0xFF94A3B8)) } }
        )
    }
}

@Composable
private fun StatCol(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.68f))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun ModeCostRow(label: String, seconds: Long, m3PerHour: Double, pricePerM3: Double) {
    if (seconds <= 0) return
    val m3 = (seconds / 3600.0) * m3PerHour
    val tl = m3 * pricePerM3
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("• $label: ${formatDuration(seconds)}", fontSize = 12.sp, color = Color.White)
        Text(String.format(Locale.US, "~%.2f m³ (%.1f TL)", m3, tl), fontSize = 12.sp, color = Color(0xFFE0F2FE))
    }
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}s ${m}dk" else "${m}dk"
}
