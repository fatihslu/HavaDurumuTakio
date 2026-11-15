package com.example.havadurumutakip

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.havadurumutakip.cosa.CosaApiClient
import com.example.havadurumutakip.cosa.CosaDailyReport
import com.example.havadurumutakip.cosa.CosaEndpointSummary
import com.example.havadurumutakip.cosa.CosaPreferences
import com.example.havadurumutakip.cosa.GasTariffPreferences
import com.example.havadurumutakip.data.LocationPreferences
import com.example.havadurumutakip.data.WeatherDatabaseHelper
import com.example.havadurumutakip.data.WeatherRepository
import com.example.havadurumutakip.export.ExcelExporter
import com.example.havadurumutakip.model.DailySummary
import com.example.havadurumutakip.model.GeocodingResult
import com.example.havadurumutakip.model.MetricCategory
import com.example.havadurumutakip.model.MetricPreferences
import com.example.havadurumutakip.model.SavedLocation
import com.example.havadurumutakip.model.WeatherMeasurement
import com.example.havadurumutakip.model.WeatherMetric
import com.example.havadurumutakip.network.OpenMeteoClient
import com.example.havadurumutakip.ui.AnalysisScreen
import com.example.havadurumutakip.ui.formatDuration
import com.example.havadurumutakip.widget.WeatherWidgetProvider
import com.example.havadurumutakip.worker.WeatherSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        WeatherSyncWorker.scheduleHourly(this)

        val glassColors = darkColorScheme(
            primary = Color(0xFF38BDF8),
            onPrimary = Color(0xFF062033),
            surface = Color(0xFF131C31),
            onSurface = Color(0xFFF8FAFC),
            onSurfaceVariant = Color(0xFFCBD5E1),
            background = Color(0xFF0A1128),
            onBackground = Color(0xFFF8FAFC)
        )

        setContent {
            MaterialTheme(colorScheme = glassColors) {
                GlassBackground { WeatherTrackerApp() }
            }
        }
    }
}

@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(colors = listOf(Color(0xFF091024), Color(0xFF13224A), Color(0xFF1C1C4C), Color(0xFF0B1325)))
        )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0x5538BDF8), Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.16f), radius = size.width * 0.78f), radius = size.width * 0.78f, center = Offset(size.width * 0.15f, size.height * 0.16f))
            drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0x4A818CF8), Color.Transparent), center = Offset(size.width * 0.88f, size.height * 0.44f), radius = size.width * 0.72f), radius = size.width * 0.72f, center = Offset(size.width * 0.88f, size.height * 0.44f))
            drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0x382DD4BF), Color.Transparent), center = Offset(size.width * 0.25f, size.height * 0.86f), radius = size.width * 0.68f), radius = size.width * 0.68f, center = Offset(size.width * 0.25f, size.height * 0.86f))
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    highlight: Boolean = false,
    warmHighlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val fillBrush = when {
        warmHighlight -> Brush.linearGradient(colors = listOf(Color(0x3DFB923C), Color(0x22F43F5E), Color(0x14FFFFFF)))
        highlight -> Brush.linearGradient(colors = listOf(Color(0x3D38BDF8), Color(0x24818CF8), Color(0x14FFFFFF)))
        else -> Brush.verticalGradient(colors = listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f)))
    }
    val borderBrush = Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.24f)))

    Column(
        modifier = modifier.clip(shape).background(brush = fillBrush).border(width = 1.dp, brush = borderBrush, shape = shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp),
        content = content
    )
}

@Composable
fun GlassChipButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    accent: Color = Color.White
) {
    val shape = RoundedCornerShape(50)
    val bgBrush = if (selected) Brush.verticalGradient(colors = listOf(Color(0x6638BDF8), Color(0x3338BDF8)))
    else Brush.verticalGradient(colors = listOf(Color.White.copy(alpha = if (enabled) 0.20f else 0.08f), Color.White.copy(alpha = if (enabled) 0.08f else 0.04f)))
    val borderColor = if (selected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.30f)

    Box(
        modifier = modifier.clip(shape).background(bgBrush).border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = if (enabled) accent else Color.White.copy(alpha = 0.45f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherTrackerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refreshTick by WeatherRepository.refreshTick.collectAsState()
    val locPrefs = remember { LocationPreferences(context) }
    val metricPrefs = remember { MetricPreferences(context) }
    val cosaPrefs = remember { CosaPreferences(context) }
    val tariffPrefs = remember { GasTariffPreferences(context) }

    var trackedLocations by remember { mutableStateOf(locPrefs.getLocations()) }
    var primaryLocName by remember { mutableStateOf(locPrefs.getPrimaryLocationName()) }
    var selectedMetrics by remember { mutableStateOf(metricPrefs.getSelectedMetrics()) }
    var cosaConnected by remember { mutableStateOf(cosaPrefs.isConnected()) }
    var cosaName by remember { mutableStateOf(cosaPrefs.getEndpointName()) }
    var latestCosa by remember { mutableStateOf<WeatherMeasurement?>(null) }
    var latestReport by remember { mutableStateOf<CosaDailyReport?>(null) }
    var latestByLocation by remember { mutableStateOf<Map<String, WeatherMeasurement?>>(emptyMap()) }
    var dailySummaries by remember { mutableStateOf<List<DailySummary>>(emptyList()) }
    var selectedFilterLocation by remember { mutableStateOf<String?>(null) }

    var showAnalysis by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<DailySummary?>(null) }
    var dayPendingDelete by remember { mutableStateOf<DailySummary?>(null) }
    var locationPendingRemove by remember { mutableStateOf<SavedLocation?>(null) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showMetricDialog by remember { mutableStateOf(false) }
    var showCosaDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isMeasuring by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var statusError by remember { mutableStateOf<String?>(null) }

    val createExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isExporting = true
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val db = WeatherDatabaseHelper(context)
                        val summaries = db.getDailySummaries()
                        val measurements = db.getAllMeasurements()
                        val reports = db.getCosaReports()
                        val bands = db.getTemperatureBandStats(locPrefs.getPrimaryLocation()?.name)
                        db.close()
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            ExcelExporter.writeXlsx(out, summaries, measurements, selectedMetrics, reports, bands, tariffPrefs.getM3PerHour(), tariffPrefs.getPricePerM3())
                        } ?: error("Dosya açılamadı")
                    }
                }
                isExporting = false
                result.onSuccess { Toast.makeText(context, "Excel dosyası kaydedildi!", Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(context, "Hata: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    fun shareExcel() {
        scope.launch {
            isExporting = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val db = WeatherDatabaseHelper(context)
                    val summaries = db.getDailySummaries()
                    val measurements = db.getAllMeasurements()
                    val reports = db.getCosaReports()
                    val bands = db.getTemperatureBandStats(locPrefs.getPrimaryLocation()?.name)
                    db.close()
                    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val file = File(exportDir, "hava_durumu_${LocalDate.now()}.xlsx")
                    file.outputStream().use { out ->
                        ExcelExporter.writeXlsx(out, summaries, measurements, selectedMetrics, reports, bands, tariffPrefs.getM3PerHour(), tariffPrefs.getPricePerM3())
                    }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
            }
            isExporting = false
            result.onSuccess { fileUri ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Excel Paylaş / Aç"))
            }.onFailure { Toast.makeText(context, "Hata: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
        }
    }

    fun reloadLocationsFromPrefs() {
        trackedLocations = locPrefs.getLocations()
        primaryLocName = locPrefs.getPrimaryLocationName()
        cosaConnected = cosaPrefs.isConnected()
        cosaName = cosaPrefs.getEndpointName()
    }

    LaunchedEffect(refreshTick, trackedLocations, cosaConnected) {
        withContext(Dispatchers.IO) {
            val db = WeatherDatabaseHelper(context)
            dailySummaries = db.getDailySummaries()
            val map = mutableMapOf<String, WeatherMeasurement?>()
            for (loc in trackedLocations) map[loc.name] = db.getLatestMeasurementForLocation(loc.name)
            latestByLocation = map
            latestCosa = if (cosaConnected) db.getLatestMeasurementForLocation("🏠 Cosa: $cosaName") else null
            latestReport = if (cosaConnected) db.getLatestCosaReport() else null
            db.close()
        }
    }

    LaunchedEffect(Unit) {
        val needsInitial = withContext(Dispatchers.IO) {
            val db = WeatherDatabaseHelper(context)
            val anyMissing = trackedLocations.any { db.getLatestMeasurementForLocation(it.name) == null }
            db.close()
            anyMissing && trackedLocations.isNotEmpty()
        }
        if (needsInitial) {
            isMeasuring = true; WeatherRepository.measureAndSaveAll(context); isMeasuring = false
        }
    }

    val filterNames = remember(dailySummaries) { dailySummaries.map { it.locationName }.distinct() }
    val visibleSummaries = remember(dailySummaries, selectedFilterLocation) {
        if (selectedFilterLocation == null) dailySummaries else dailySummaries.filter { it.locationName == selectedFilterLocation }
    }

    if (showAnalysis) {
        BackHandler { showAnalysis = false }
        AnalysisScreen(onBack = { showAnalysis = false })
    } else if (selectedDay != null) {
        BackHandler { selectedDay = null }
        DayDetailScreen(summary = selectedDay!!, selectedMetrics = selectedMetrics, onOpenMetricMenu = { showMetricDialog = true }, onBack = { selectedDay = null })
    } else {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White),
                    title = { Text("Hava Durumu Takip", fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1) }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
            ) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            GlassChipButton(
                                text = if (cosaConnected) "🔥 Cosa" else "🏠 Cosa Bağla",
                                onClick = { showCosaDialog = true },
                                selected = cosaConnected,
                                accent = if (cosaConnected) Color(0xFFFDBA74) else Color.White
                            )
                        }
                        item { GlassChipButton(text = "📈 Analiz", onClick = { showAnalysis = true }) }
                        item { GlassChipButton(text = "⚙️ Veriler (${selectedMetrics.size}/${WeatherMetric.entries.size})", onClick = { showMetricDialog = true }, selected = true) }
                        item { GlassChipButton(text = "➕ Konum", onClick = { showLocationDialog = true }) }
                        item { GlassChipButton(text = "📊 Excel", onClick = { showExportDialog = true }) }
                    }
                }

                if (cosaConnected) {
                    item {
                        CosaLiveCard(
                            deviceName = cosaName, latest = latestCosa, report = latestReport,
                            m3PerHour = tariffPrefs.getM3PerHour(), pricePerM3 = tariffPrefs.getPricePerM3(),
                            onOpenAnalysis = { showAnalysis = true },
                            onDisconnect = {
                                cosaPrefs.clear(); reloadLocationsFromPrefs(); latestCosa = null; latestReport = null
                                WeatherWidgetProvider.updateAllWidgets(context)
                                Toast.makeText(context, "Cosa bağlantısı kesildi", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                item {
                    Text("Takip Edilen Konumlar (${trackedLocations.size})", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }

                if (trackedLocations.isEmpty()) {
                    item { GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) { Text("Henüz takip edilen konum yok. Yukarıdan '➕ Konum' ile ekleyin.", color = Color.White) } }
                } else {
                    items(trackedLocations, key = { it.name }) { loc ->
                        val latest = latestByLocation[loc.name]
                        val isWidgetLoc = loc.name == primaryLocName
                        TrackedLocationCard(
                            location = loc, latest = latest, selectedMetrics = selectedMetrics, isWidgetPrimary = isWidgetLoc,
                            onSetWidgetPrimary = {
                                locPrefs.setPrimaryLocationName(loc.name); primaryLocName = loc.name
                                WeatherWidgetProvider.updateAllWidgets(context)
                                Toast.makeText(context, "Widget konumu: ${loc.name}", Toast.LENGTH_SHORT).show()
                            },
                            onRemoveClick = { locationPendingRemove = loc }
                        )
                    }
                }

                item {
                    if (statusError != null) Text("Hata: $statusError", color = Color(0xFFFDA4AF), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                    GlassChipButton(
                        text = if (isMeasuring) "Ölçülüyor..." else "↻ Tümünü Ölç ve Kaydet (Hava + Cosa)",
                        enabled = !isMeasuring && (trackedLocations.isNotEmpty() || cosaConnected),
                        onClick = {
                            scope.launch {
                                isMeasuring = true; statusError = null
                                val res = WeatherRepository.measureAndSaveAll(context)
                                res.onFailure { statusError = it.localizedMessage }
                                isMeasuring = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        accent = Color(0xFFE0F2FE)
                    )
                }

                item { Spacer(modifier = Modifier.height(4.dp)); Text("Günlük Geçmiş", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }

                if (filterNames.size > 1) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { GlassChipButton(text = "Tümü", selected = selectedFilterLocation == null, onClick = { selectedFilterLocation = null }) }
                            items(filterNames) { name -> GlassChipButton(text = name.substringBefore(","), selected = selectedFilterLocation == name, onClick = { selectedFilterLocation = name }) }
                        }
                    }
                }

                if (visibleSummaries.isEmpty()) {
                    item { GlassCard(modifier = Modifier.fillMaxWidth()) { Text("Henüz geçmiş kaydı yok.", color = Color.White.copy(alpha = 0.75f)) } }
                } else {
                    items(items = visibleSummaries, key = { "${it.dateKey}|${it.locationName}" }) { day ->
                        DailySummaryCard(day = day, onClick = { selectedDay = day }, onDeleteClick = { dayPendingDelete = day })
                    }
                }
            }
        }
    }

    if (showCosaDialog) {
        CosaLoginDialog(initialEmail = cosaPrefs.getEmail(), onDismiss = { showCosaDialog = false }, onConnected = { email, password, token, endpoint ->
            cosaPrefs.saveSession(email, password, token, endpoint.id, endpoint.name); reloadLocationsFromPrefs(); showCosaDialog = false
            scope.launch { isMeasuring = true; WeatherRepository.measureAndSaveAll(context); WeatherWidgetProvider.updateAllWidgets(context); isMeasuring = false }
            Toast.makeText(context, "Cosa bağlandı: ${endpoint.name}", Toast.LENGTH_SHORT).show()
        })
    }

    if (showMetricDialog) {
        MetricSelectorDialog(currentSelection = selectedMetrics, onDismiss = { showMetricDialog = false }, onSave = { updated ->
            metricPrefs.saveSelectedMetrics(updated); selectedMetrics = updated; showMetricDialog = false
        })
    }

    if (locationPendingRemove != null) {
        val target = locationPendingRemove!!
        AlertDialog(
            containerColor = Color(0xF0131C31), titleContentColor = Color.White, textContentColor = Color(0xFFCBD5E1),
            onDismissRequest = { locationPendingRemove = null }, title = { Text("Konumu Kaldır") },
            text = { Text("${target.name} otomatik ölçümlerden çıkarılsın mı?") },
            confirmButton = {
                TextButton(onClick = {
                    locationPendingRemove = null; locPrefs.removeLocation(target.name)
                    if (selectedFilterLocation == target.name) selectedFilterLocation = null
                    reloadLocationsFromPrefs(); WeatherWidgetProvider.updateAllWidgets(context)
                }) { Text("Kaldır", color = Color(0xFFFB7185), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { locationPendingRemove = null }) { Text("İptal", color = Color(0xFF94A3B8)) } }
        )
    }

    if (dayPendingDelete != null) {
        val target = dayPendingDelete!!
        AlertDialog(
            containerColor = Color(0xF0131C31), titleContentColor = Color.White, textContentColor = Color(0xFFCBD5E1),
            onDismissRequest = { dayPendingDelete = null }, title = { Text("Günü Sil") },
            text = { Text("${formatDateTurkish(target.dateKey)}\n${target.locationName}\n\n${target.measurementCount} ölçüm silinsin mi?") },
            confirmButton = {
                TextButton(onClick = {
                    dayPendingDelete = null
                    scope.launch {
                        withContext(Dispatchers.IO) { val db = WeatherDatabaseHelper(context); db.deleteDay(target.dateKey, target.locationName); db.close(); WeatherWidgetProvider.updateAllWidgets(context) }
                        WeatherRepository.notifyChanged(); Toast.makeText(context, "Günlük kayıt silindi", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Sil", color = Color(0xFFFB7185), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { dayPendingDelete = null }) { Text("İptal", color = Color(0xFF94A3B8)) } }
        )
    }

    if (showLocationDialog) {
        LocationSearchDialog(onDismiss = { showLocationDialog = false }, onSelectLocation = { picked ->
            val saved = SavedLocation(picked.displayName, picked.latitude, picked.longitude)
            locPrefs.addLocation(saved); reloadLocationsFromPrefs(); showLocationDialog = false
            scope.launch { isMeasuring = true; WeatherRepository.measureAndSaveAll(context); WeatherWidgetProvider.updateAllWidgets(context); isMeasuring = false }
        })
    }

    if (showExportDialog) {
        AlertDialog(
            containerColor = Color(0xF0131C31), titleContentColor = Color.White, textContentColor = Color(0xFFCBD5E1),
            onDismissRequest = { if (!isExporting) showExportDialog = false }, title = { Text("Excel Dışa Aktar (.xlsx)") },
            text = {
                Column {
                    Text("3 sayfalı Excel oluşturulur:\n• Günlük Özet\n• Saatlik Kayıtlar (${selectedMetrics.size} veri)\n• Kombi & Maliyet Analizi", fontSize = 14.sp)
                    if (isExporting) { Spacer(modifier = Modifier.height(12.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = { Button(enabled = !isExporting, onClick = { showExportDialog = false; createExcelLauncher.launch("hava_durumu_${LocalDate.now()}.xlsx") }) { Text("Telefona Kaydet") } },
            dismissButton = {
                Row {
                    TextButton(enabled = !isExporting, onClick = { showExportDialog = false; shareExcel() }) { Text("Paylaş", color = Color(0xFF38BDF8)) }
                    TextButton(enabled = !isExporting, onClick = { showExportDialog = false }) { Text("İptal", color = Color(0xFF94A3B8)) }
                }
            }
        )
    }
}

@Composable
fun CosaLiveCard(deviceName: String, latest: WeatherMeasurement?, report: CosaDailyReport?, m3PerHour: Double, pricePerM3: Double, onOpenAnalysis: () -> Unit, onDisconnect: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), warmHighlight = true, onClick = onOpenAnalysis) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("🏠 Cosa v5 • $deviceName", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
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
                    Text("Kombi: $combi  •  Hedef: ${String.format(Locale.US, "%.1f°", target)}", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Nem: %${latest.humidity}  •  Mod: $mode  •  Son: ${latest.timeLabel}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.78f))
                }
            }
        } else {
            Text("Cosa verisi henüz çekilmedi.", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
        if (report != null) {
            val m3 = (report.totalSec / 3600.0) * m3PerHour
            val tl = m3 * pricePerM3
            Spacer(modifier = Modifier.height(8.dp)); HorizontalDivider(color = Color.White.copy(alpha = 0.14f)); Spacer(modifier = Modifier.height(6.dp))
            Text(String.format(Locale.US, "📈 Son 24s: %s çalıştı • ~%.1f m³ • ~%.1f TL (Detay için dokun)", formatDuration(report.totalSec), m3, tl), fontSize = 11.sp, color = Color(0xFFFDE68A), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun MetricDetailsGrid(measurement: WeatherMeasurement, selectedMetrics: Set<WeatherMetric>, excludeTopBasic: Boolean = false) {
    val extra = remember(measurement.extraJson) { measurement.extraObj() }
    val isCosaRow = measurement.weatherCode < 0
    val list = remember(selectedMetrics, excludeTopBasic, isCosaRow) {
        if (isCosaRow) listOf(WeatherMetric.COSA_TARGET_TEMP, WeatherMetric.COSA_ROOM_HUMIDITY, WeatherMetric.COSA_COMBI_STATE, WeatherMetric.COSA_MODE, WeatherMetric.COSA_BATTERY)
        else WeatherMetric.entries.filter { m -> m in selectedMetrics && (!excludeTopBasic || (m != WeatherMetric.TEMPERATURE && m != WeatherMetric.WEATHER_DESC)) }
    }
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
fun TrackedLocationCard(location: SavedLocation, latest: WeatherMeasurement?, selectedMetrics: Set<WeatherMetric>, isWidgetPrimary: Boolean, onSetWidgetPrimary: () -> Unit, onRemoveClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), highlight = isWidgetPrimary) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(location.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
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
                    Text("Son ölçüm: ${latest.dateKey} ${latest.timeLabel}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f))
                }
            }
            Spacer(modifier = Modifier.height(10.dp)); HorizontalDivider(color = Color.White.copy(alpha = 0.14f)); Spacer(modifier = Modifier.height(8.dp))
            MetricDetailsGrid(measurement = latest, selectedMetrics = selectedMetrics, excludeTopBasic = true)
        } else {
            Text("Bu konum için henüz ölçüm alınmadı.", color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(String.format(Locale.US, "💧 Toplam Yağış: %.1f mm  •  ☁️ Ort. Bulut: %%%d  •  ☀️ Max UV: %.1f", day.totalPrecipitation, day.avgCloudCover, day.maxUvIndex), fontSize = 11.sp, color = Color.White.copy(alpha = 0.72f))
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(String.format(Locale.US, "💧 Günlük Toplam Yağış: %.1f mm", livePrecip), fontSize = 12.sp, color = Color(0xFFE0F2FE), modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Saatlik Ölçümler (${hourlyList.size})", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(hourlyList, key = { it.id }) { item ->
                    val (emoji, desc) = OpenMeteoClient.weatherDescription(item.weatherCode)
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, warmHighlight = item.weatherCode < 0) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.timeLabel}  •  $emoji $desc", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f))
                            Text(String.format(Locale.US, "%.1f °C", item.temperature), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (item.weatherCode < 0) Color(0xFFFDBA74) else Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(8.dp))
                            GlassChipButton(text = "🗑️", onClick = { itemPendingDelete = item }, accent = Color(0xFFFDA4AF))
                        }
                        Spacer(modifier = Modifier.height(8.dp)); HorizontalDivider(color = Color.White.copy(alpha = 0.14f)); Spacer(modifier = Modifier.height(8.dp))
                        MetricDetailsGrid(measurement = item, selectedMetrics = selectedMetrics, excludeTopBasic = true)
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

@Composable
fun CosaLoginDialog(initialEmail: String, onDismiss: () -> Unit, onConnected: (String, String, String, CosaEndpointSummary) -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var fetchedToken by remember { mutableStateOf<String?>(null) }
    var endpoints by remember { mutableStateOf<List<CosaEndpointSummary>>(emptyList()) }

    AlertDialog(
        containerColor = Color(0xF5131C31), titleContentColor = Color.White, textContentColor = Color(0xFFCBD5E1),
        onDismissRequest = { if (!loading) onDismiss() }, title = { Text("🏠 Cosa v5 Bağla") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Bilgiler Keystore ile şifrelenip yalnızca telefonunuzda saklanır ve sadece kiwi-api.nuvia.com.tr sunucusuna bağlanır.", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(10.dp))
                if (endpoints.isEmpty()) {
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Cosa E-posta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Şifre") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                } else {
                    Text("Cihazı seçin:", color = Color.White, fontWeight = FontWeight.Medium); Spacer(modifier = Modifier.height(8.dp))
                    endpoints.forEach { ep ->
                        GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), cornerRadius = 14.dp, onClick = { onConnected(email, password, fetchedToken!!, ep) }) {
                            Text(ep.name, color = Color.White, fontWeight = FontWeight.Bold); Text("ID: ${ep.id}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
                if (loading) { Spacer(modifier = Modifier.height(10.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                if (errorMsg != null) { Spacer(modifier = Modifier.height(8.dp)); Text(errorMsg!!, color = Color(0xFFFDA4AF), fontSize = 12.sp) }
            }
        },
        confirmButton = {
            if (endpoints.isEmpty()) {
                Button(enabled = !loading && email.isNotBlank() && password.isNotBlank(), onClick = {
                    scope.launch {
                        loading = true; errorMsg = null
                        val res = withContext(Dispatchers.IO) { runCatching { val token = CosaApiClient.login(email, password); token to CosaApiClient.getEndpoints(token) } }
                        loading = false
                        res.onSuccess { (token, list) ->
                            if (list.isEmpty()) errorMsg = "Hesapta Cosa cihazı bulunamadı."
                            else if (list.size == 1) onConnected(email, password, token, list.first())
                            else { fetchedToken = token; endpoints = list }
                        }.onFailure { errorMsg = it.localizedMessage ?: "Bağlantı hatası" }
                    }
                }) { Text("Bağlan") }
            }
        },
        dismissButton = { TextButton(enabled = !loading, onClick = onDismiss) { Text("İptal", color = Color(0xFF94A3B8)) } }
    )
}

@Composable
fun MetricSelectorDialog(currentSelection: Set<WeatherMetric>, onDismiss: () -> Unit, onSave: (Set<WeatherMetric>) -> Unit) {
    var draft by remember { mutableStateOf(currentSelection) }
    AlertDialog(
        containerColor = Color(0xF5131C31), titleContentColor = Color.White, textContentColor = Color(0xFFCBD5E1), onDismissRequest = onDismiss,
        title = { Column { Text("Gösterilecek & Excel Verileri", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Seçili: ${draft.size} / ${WeatherMetric.entries.size}", fontSize = 11.sp, color = Color(0xFF94A3B8)) } },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassChipButton("Tümünü Seç", onClick = { draft = WeatherMetric.entries.toSet() }, modifier = Modifier.weight(1f))
                    GlassChipButton("Varsayılan", onClick = { draft = WeatherMetric.entries.filter { it.defaultEnabled }.toSet() }, modifier = Modifier.weight(1f))
                    GlassChipButton("Temizle", onClick = { draft = setOf(WeatherMetric.TEMPERATURE) }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    MetricCategory.entries.forEach { category ->
                        item(key = "cat_${category.name}") { Text(category.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                        items(WeatherMetric.entries.filter { it.category == category }, key = { it.name }) { metric ->
                            val checked = metric in draft
                            Row(modifier = Modifier.fillMaxWidth().clickable { draft = if (checked) draft - metric else draft + metric }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = { isChecked -> draft = if (isChecked) draft + metric else draft - metric })
                                Text(metric.label, fontSize = 13.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(draft) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal", color = Color(0xFF94A3B8)) } }
    )
}

@Composable
fun LocationSearchDialog(onDismiss: () -> Unit, onSelectLocation: (GeocodingResult) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeocodingResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        containerColor = Color(0xF0131C31), titleContentColor = Color.White, textContentColor = Color(0xFFCBD5E1), onDismissRequest = onDismiss, title = { Text("Konum Ekle") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Şehir veya ilçe") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (query.isBlank()) return@Button
                        scope.launch {
                            searching = true; errorMsg = null
                            try { results = withContext(Dispatchers.IO) { OpenMeteoClient.searchLocations(query) }; if (results.isEmpty()) errorMsg = "Sonuç bulunamadı." }
                            catch (e: Exception) { errorMsg = e.localizedMessage }
                            searching = false
                        }
                    }, enabled = !searching) { Text("Ara") }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (errorMsg != null) Text(errorMsg!!, color = Color(0xFFFDA4AF), fontSize = 12.sp)
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(results) { item ->
                        Column(modifier = Modifier.fillMaxWidth().clickable { onSelectLocation(item) }.padding(vertical = 8.dp)) {
                            Text(item.displayName, fontWeight = FontWeight.Medium, color = Color.White)
                            Text("Enlem: ${item.latitude}, Boylam: ${item.longitude}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            HorizontalDivider(modifier = Modifier.padding(top = 6.dp), color = Color.White.copy(alpha = 0.14f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat", color = Color(0xFF94A3B8)) } }
    )
}

private fun formatDateTurkish(dateKey: String): String {
    return try {
        LocalDate.parse(dateKey).format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale.forLanguageTag("tr-TR")))
    } catch (_: Exception) { dateKey }
}
