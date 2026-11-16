package com.example.havadurumutakip.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import com.example.havadurumutakip.data.WeatherDatabaseHelper
import com.example.havadurumutakip.data.WeatherRepository
import com.example.havadurumutakip.model.DailySummary
import com.example.havadurumutakip.model.MetricCategory
import com.example.havadurumutakip.model.SavedLocation
import com.example.havadurumutakip.model.WeatherMeasurement
import com.example.havadurumutakip.model.WeatherMetric
import com.example.havadurumutakip.network.OpenMeteoClient
import com.example.havadurumutakip.widget.WeatherWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CosaLiveCard(
    deviceName: String,
    latest: WeatherMeasurement?,
    report: CosaDailyReport?,
    m3PerHour: Double,
    pricePerM3: Double,
    onOpenAnalysis: () -> Unit,
    onDisconnect: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), warmHighlight = true, onClick = onOpenAnalysis) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("🏠 Cosa • $deviceName", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            GlassChipButton(text = "Çıkış", onClick = onDisconnect, accent = Color(0xFFFDA4AF))
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (latest != null) {
            val extra = remember(latest.extraJson) { latest.extraObj() }
            val combi = extra.optString("cosa_combi", "Beklemede")
            val target = extra.optDouble("cosa_target", 0.0)
            val mode = extra.optString("cosa_mode", "-")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(String.format(Locale.US, "%.1f °C", latest.temperature), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFDBA74))
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text("$combi  •  Hedef: ${String.format(Locale.US, "%.1f°", target)}", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Nem %${latest.humidity}  •  $mode  •  ${latest.timeLabel}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.78f))
                }
            }
        } else {
            Text("Cosa verisi henüz çekilmedi.", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
        if (report != null) {
            val m3 = (report.totalSec / 3600.0) * m3PerHour
            val tl = m3 * pricePerM3
            Spacer(modifier = Modifier.height(8.dp)); HorizontalDivider(color = Color.White.copy(alpha = 0.14f)); Spacer(modifier = Modifier.height(6.dp))
            Text(String.format(Locale.US, "📈 24s: %s çalıştı • ~%.1f m³ • ~%.0f TL  (Analiz →)", formatDuration(report.totalSec), m3, tl), fontSize = 11.sp, color = Color(0xFFFDE68A), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun TrackedLocationCard(
    location: SavedLocation,
    latest: WeatherMeasurement?,
    isWidgetPrimary: Boolean,
    onClick: () -> Unit,
    onSetWidgetPrimary: () -> Unit,
    onRemoveClick: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = isWidgetPrimary, onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(location.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            GlassChipButton(text = if (isWidgetPrimary) "⭐ Widget" else "☆ Widget", selected = isWidgetPrimary, onClick = onSetWidgetPrimary)
            Spacer(modifier = Modifier.width(6.dp))
            GlassChipButton(text = "🗑️", onClick = onRemoveClick, accent = Color(0xFFFDA4AF))
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (latest != null) {
            val (emoji, desc) = OpenMeteoClient.weatherDescription(latest.weatherCode)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(String.format(Locale.US, "%.1f °C", latest.temperature), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text("$emoji $desc", fontSize = 15.sp, color = Color(0xFFE0F2FE), fontWeight = FontWeight.SemiBold)
                    Text("Son: ${latest.dateKey} ${latest.timeLabel}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = String.format(Locale.US, "Hissedilen %.1f° • Nem %%%d • Rüzgâr %.1f km/sa • Yağış %.1f mm", latest.apparentTemp, latest.humidity, latest.windSpeed, latest.precipitation),
                fontSize = 12.sp,
                color = Color(0xFFBAE6FD)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Tüm seçili verileri gör →", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
        } else {
            Text("Bu konum için henüz ölçüm alınmadı.", color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
        }
    }
}

@Composable
fun MetricDetailsGrid(measurement: WeatherMeasurement, selectedMetrics: Set<WeatherMetric>, excludeTopBasic: Boolean = false) {
    val extra = remember(measurement.extraJson) { measurement.extraObj() }
    val isCosaRow = measurement.weatherCode < 0
    val list = remember(selectedMetrics, excludeTopBasic, isCosaRow) {
        if (isCosaRow) listOf(WeatherMetric.COSA_TARGET_TEMP, WeatherMetric.COSA_ROOM_HUMIDITY, WeatherMetric.COSA_COMBI_STATE, WeatherMetric.COSA_MODE, WeatherMetric.COSA_BATTERY)
        else WeatherMetric.entries.filter { m ->
            m in selectedMetrics && m.category != MetricCategory.COSA &&
                (!excludeTopBasic || (m != WeatherMetric.TEMPERATURE && m != WeatherMetric.WEATHER_DESC))
        }
    }
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { metric ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(metric.label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.62f), maxLines = 1)
                        Text(metric.formatDisplay(measurement, extra), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE0F2FE))
                    }
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DailySummaryCard(day: DailySummary, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    val isCosa = day.locationName.startsWith("🏠 Cosa")
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, warmHighlight = isCosa, onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(formatDateTurkish(day.dateKey), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                Text("${day.locationName} • ${day.measurementCount} ölçüm", fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f))
            }
            GlassChipButton(text = "🗑️", onClick = onDeleteClick, accent = Color(0xFFFDA4AF))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TempBadge("Min", day.minTemp, Color(0xFF38BDF8)); TempBadge("Ort", day.avgTemp, Color(0xFF2DD4BF)); TempBadge("Max", day.maxTemp, Color(0xFFFB923C))
        }
        if (!isCosa) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(String.format(Locale.US, "💧 Yağış: %.1f mm  •  ☁️ Bulut: %%%d  •  ☀️ UV: %.1f", day.totalPrecipitation, day.avgCloudCover, day.maxUvIndex), fontSize = 11.sp, color = Color.White.copy(alpha = 0.72f))
        }
    }
}

@Composable
fun TempBadge(label: String, value: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.68f))
        Text(String.format(Locale.US, "%.1f °C", value), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(summary: DailySummary, selectedMetrics: Set<WeatherMetric>, onOpenMetricMenu: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hourlyList by remember { mutableStateOf<List<WeatherMeasurement>>(emptyList()) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var itemPendingDelete by remember { mutableStateOf<WeatherMeasurement?>(null) }

    LaunchedEffect(summary) {
        withContext(Dispatchers.IO) {
            val db = WeatherDatabaseHelper(context); hourlyList = db.getHourlyForDay(summary.dateKey, summary.locationName); db.close()
        }
    }

    val liveMin = if (hourlyList.isNotEmpty()) hourlyList.minOf { it.temperature } else summary.minTemp
    val liveAvg = if (hourlyList.isNotEmpty()) hourlyList.map { it.temperature }.average() else summary.avgTemp
    val liveMax = if (hourlyList.isNotEmpty()) hourlyList.maxOf { it.temperature } else summary.maxTemp
    val livePrecip = if (hourlyList.isNotEmpty()) hourlyList.sumOf { it.precipitation } else summary.totalPrecipitation
    val isCosa = summary.locationName.startsWith("🏠 Cosa")

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White),
                title = { Column { Text(formatDateTurkish(summary.dateKey), fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(summary.locationName, fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f)) } },
                navigationIcon = { GlassChipButton(text = "← Geri", onClick = onBack, modifier = Modifier.padding(start = 8.dp, end = 6.dp)) },
                actions = { if (!isCosa) GlassChipButton(text = "⚙️ Veriler", onClick = onOpenMetricMenu, modifier = Modifier.padding(end = 10.dp)) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            GlassCard(modifier = Modifier.fillMaxWidth(), highlight = !isCosa, warmHighlight = isCosa) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    TempBadge("Min", liveMin, Color(0xFF38BDF8)); TempBadge("Ortalama", liveAvg, Color(0xFF2DD4BF)); TempBadge("Max", liveMax, Color(0xFFFB923C))
                }
                if (!isCosa) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(String.format(Locale.US, "💧 Günlük Toplam Yağış: %.1f mm", livePrecip), fontSize = 12.sp, color = Color(0xFFE0F2FE), modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text("Saatlik Ölçümler (${hourlyList.size}) • Detay için dokunun", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(hourlyList, key = { it.id }) { item ->
                    val (emoji, desc) = OpenMeteoClient.weatherDescription(item.weatherCode)
                    val isExpanded = expandedId == item.id
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        warmHighlight = item.weatherCode < 0,
                        onClick = { expandedId = if (isExpanded) null else item.id }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${item.timeLabel}  •  $emoji $desc", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                if (item.weatherCode >= 0) {
                                    Text(String.format(Locale.US, "Nem %%%d • Rüzgâr %.1f km/sa • Yağış %.1f mm", item.humidity, item.windSpeed, item.precipitation), fontSize = 11.sp, color = Color.White.copy(alpha = 0.68f))
                                } else {
                                    val extra = remember(item.extraJson) { item.extraObj() }
                                    Text("Hedef ${String.format(Locale.US, "%.1f°", extra.optDouble("cosa_target", 0.0))} • Nem %${item.humidity} • ${extra.optString("cosa_mode", "-")}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.68f))
                                }
                            }
                            Text(String.format(Locale.US, "%.1f °C", item.temperature), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (item.weatherCode < 0) Color(0xFFFDBA74) else Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(8.dp))
                            GlassChipButton(text = "🗑️", onClick = { itemPendingDelete = item }, accent = Color(0xFFFDA4AF))
                        }
                        AnimatedVisibility(visible = isExpanded) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp)); HorizontalDivider(color = Color.White.copy(alpha = 0.14f)); Spacer(modifier = Modifier.height(8.dp))
                                MetricDetailsGrid(measurement = item, selectedMetrics = selectedMetrics, excludeTopBasic = true)
                            }
                        }
                    }
                }
            }
        }
    }

    if (itemPendingDelete != null) {
        val target = itemPendingDelete!!
        AlertDialog(
            containerColor = Color(0xF0131C31), titleContentColor = Color.White, textContentColor = Color(0xFFCBD5E1),
            onDismissRequest = { itemPendingDelete = null }, title = { Text("Ölçümü Sil") },
            text = { Text("${target.timeLabel} saatindeki (${target.temperature} °C) ölçüm silinsin mi?") },
            confirmButton = {
                TextButton(onClick = {
                    itemPendingDelete = null
                    scope.launch {
                        val remaining = withContext(Dispatchers.IO) {
                            val db = WeatherDatabaseHelper(context); db.deleteMeasurementById(target.id)
                            val updated = db.getHourlyForDay(summary.dateKey, summary.locationName); db.close()
                            WeatherWidgetProvider.updateAllWidgets(context); updated
                        }
                        WeatherRepository.notifyChanged(); hourlyList = remaining
                        Toast.makeText(context, "Ölçüm silindi", Toast.LENGTH_SHORT).show()
                        if (remaining.isEmpty()) onBack()
                    }
                }) { Text("Sil", color = Color(0xFFFB7185), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { itemPendingDelete = null }) { Text("İptal", color = Color(0xFF94A3B8)) } }
        )
    }
}

fun formatDateTurkish(dateKey: String): String {
    return try {
        LocalDate.parse(dateKey).format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale.forLanguageTag("tr-TR")))
    } catch (_: Exception) { dateKey }
}
