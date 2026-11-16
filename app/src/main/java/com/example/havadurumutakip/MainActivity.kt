package com.example.havadurumutakip

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.havadurumutakip.cosa.CosaDailyReport
import com.example.havadurumutakip.cosa.CosaPreferences
import com.example.havadurumutakip.cosa.GasTariffPreferences
import com.example.havadurumutakip.data.LocationPreferences
import com.example.havadurumutakip.data.SyncStatusPrefs
import com.example.havadurumutakip.data.WeatherDatabaseHelper
import com.example.havadurumutakip.data.WeatherRepository
import com.example.havadurumutakip.export.ExcelExporter
import com.example.havadurumutakip.model.DailySummary
import com.example.havadurumutakip.model.MetricPreferences
import com.example.havadurumutakip.model.SavedLocation
import com.example.havadurumutakip.model.WeatherMeasurement
import com.example.havadurumutakip.model.WeatherMetric
import com.example.havadurumutakip.ui.*
import com.example.havadurumutakip.widget.WeatherWidgetProvider
import com.example.havadurumutakip.worker.WeatherSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        WeatherSyncWorker.scheduleHourly(this)

        val glassColors = darkColorScheme(
            primary = Color(0xFF38BDF8), onPrimary = Color(0xFF062033),
            surface = Color(0xFF131C31), onSurface = Color(0xFFF8FAFC),
            onSurfaceVariant = Color(0xFFCBD5E1), background = Color(0xFF0A1128), onBackground = Color(0xFFF8FAFC)
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
    val syncPrefs = remember { SyncStatusPrefs(context) }

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
    var lastSyncInfo by remember { mutableStateOf(syncPrefs.getLastInfo()) }
    var lastSyncError by remember { mutableStateOf(syncPrefs.getLastError()) }

    val pm = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var batteryUnrestricted by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }

    var showAnalysis by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<DailySummary?>(null) }
    var detailLocationMeasurement by remember { mutableStateOf<WeatherMeasurement?>(null) }
    var dayPendingDelete by remember { mutableStateOf<DailySummary?>(null) }
    var locationPendingRemove by remember { mutableStateOf<SavedLocation?>(null) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showMetricDialog by remember { mutableStateOf(false) }
    var showCosaDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isMeasuring by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    val createExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isExporting = true
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val db = WeatherDatabaseHelper(context)
                        val s = db.getDailySummaries(); val m = db.getAllMeasurements(); val r = db.getCosaReports()
                        val b = db.getTemperatureBandStats(locPrefs.getPrimaryLocation()?.name); db.close()
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            ExcelExporter.writeXlsx(out, s, m, selectedMetrics, r, b, tariffPrefs.getM3PerHour(), tariffPrefs.getPricePerM3())
                        } ?: error("Dosya açılamadı")
                    }
                }
                isExporting = false
                result.onSuccess { Toast.makeText(context, "Excel kaydedildi!", Toast.LENGTH_LONG).show() }
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
                    val s = db.getDailySummaries(); val m = db.getAllMeasurements(); val r = db.getCosaReports()
                    val b = db.getTemperatureBandStats(locPrefs.getPrimaryLocation()?.name); db.close()
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val file = File(dir, "hava_durumu_${LocalDate.now()}.xlsx")
                    file.outputStream().use { out ->
                        ExcelExporter.writeXlsx(out, s, m, selectedMetrics, r, b, tariffPrefs.getM3PerHour(), tariffPrefs.getPricePerM3())
                    }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
            }
            isExporting = false
            result.onSuccess { uri ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Excel Paylaş / Aç"))
            }.onFailure { Toast.makeText(context, "Hata: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
        }
    }

    fun reloadPrefs() {
        trackedLocations = locPrefs.getLocations(); primaryLocName = locPrefs.getPrimaryLocationName()
        cosaConnected = cosaPrefs.isConnected(); cosaName = cosaPrefs.getEndpointName()
    }

    LaunchedEffect(refreshTick, trackedLocations, cosaConnected) {
        batteryUnrestricted = pm.isIgnoringBatteryOptimizations(context.packageName)
        lastSyncInfo = syncPrefs.getLastInfo()
        lastSyncError = syncPrefs.getLastError()
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
                    title = { Text("Hava Takip", fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1) }
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
                                onClick = { showCosaDialog = true }, selected = cosaConnected,
                                accent = if (cosaConnected) Color(0xFFFDBA74) else Color.White
                            )
                        }
                        item { GlassChipButton(text = "📈 Analiz", onClick = { showAnalysis = true }) }
                        item { GlassChipButton(text = "➕ Konum", onClick = { showLocationDialog = true }) }
                        item { GlassChipButton(text = "📊 Excel", onClick = { showExportDialog = true }) }
                        item { GlassChipButton(text = "⚙️ Seçim", onClick = { showMetricDialog = true }) }
                    }
                }

                if (!batteryUnrestricted) {
                    item {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp, warmHighlight = true,
                            onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                        ) {
                            Text("⚡ Saatlik otomatik ölçümün aksamaması için Pil ayarından bu uygulamayı 'Kısıtlanmamış' yapın (Ayarları Aç →)", fontSize = 12.sp, color = Color(0xFFFDE68A))
                        }
                    }
                }

                if (cosaConnected) {
                    item {
                        CosaLiveCard(
                            deviceName = cosaName, latest = latestCosa, report = latestReport,
                            m3PerHour = tariffPrefs.getM3PerHour(), pricePerM3 = tariffPrefs.getPricePerM3(),
                            onOpenAnalysis = { showAnalysis = true },
                            onDisconnect = {
                                cosaPrefs.clear(); reloadPrefs(); latestCosa = null; latestReport = null
                                WeatherWidgetProvider.updateAllWidgets(context)
                                Toast.makeText(context, "Cosa bağlantısı kesildi", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                if (trackedLocations.isEmpty()) {
                    item { GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) { Text("Henüz konum yok. Yukarıdan '➕ Konum' ile ekleyin.", color = Color.White) } }
                } else {
                    items(trackedLocations, key = { it.name }) { loc ->
                        val latest = latestByLocation[loc.name]
                        TrackedLocationCard(
                            location = loc, latest = latest, isWidgetPrimary = loc.name == primaryLocName,
                            onClick = { if (latest != null) detailLocationMeasurement = latest },
                            onSetWidgetPrimary = {
                                locPrefs.setPrimaryLocationName(loc.name); primaryLocName = loc.name
                                WeatherWidgetProvider.updateAllWidgets(context)
                                Toast.makeText(context, "Widget: ${loc.name}", Toast.LENGTH_SHORT).show()
                            },
                            onRemoveClick = { locationPendingRemove = loc }
                        )
                    }
                }

                item {
                    if (lastSyncError.isNotBlank()) {
                        Text("Son hata: $lastSyncError", color = Color(0xFFFDA4AF), fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    GlassChipButton(
                        text = if (isMeasuring) "Ölçülüyor..." else "↻ Şimdi Ölç ve Kaydet",
                        enabled = !isMeasuring && (trackedLocations.isNotEmpty() || cosaConnected),
                        onClick = {
                            scope.launch {
                                isMeasuring = true
                                WeatherRepository.measureAndSaveAll(context, source = "Manuel")
                                isMeasuring = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        accent = Color(0xFFE0F2FE)
                    )
                    if (lastSyncInfo.isNotBlank()) {
                        Text("Son kontrol: $lastSyncInfo", fontSize = 11.sp, color = Color.White.copy(alpha = 0.58f), modifier = Modifier.padding(top = 4.dp))
                    }
                }

                item { Spacer(modifier = Modifier.height(2.dp)); Text("Günlük Geçmiş", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }

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

    if (detailLocationMeasurement != null) {
        LocationDetailsDialog(measurement = detailLocationMeasurement!!, selectedMetrics = selectedMetrics, onDismiss = { detailLocationMeasurement = null })
    }

    if (showCosaDialog) {
        CosaLoginDialog(initialEmail = cosaPrefs.getEmail(), onDismiss = { showCosaDialog = false }, onConnected = { email, password, token, ep ->
            cosaPrefs.saveSession(email, password, token, ep.id, ep.name); reloadPrefs(); showCosaDialog = false
            scope.launch { isMeasuring = true; WeatherRepository.measureAndSaveAll(context, "Manuel"); WeatherWidgetProvider.updateAllWidgets(context); isMeasuring = false }
            Toast.makeText(context, "Cosa bağlandı: ${ep.name}", Toast.LENGTH_SHORT).show()
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
                    reloadPrefs(); WeatherWidgetProvider.updateAllWidgets(context)
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
            locPrefs.addLocation(saved); reloadPrefs(); showLocationDialog = false
            scope.launch { isMeasuring = true; WeatherRepository.measureAndSaveAll(context, "Manuel"); WeatherWidgetProvider.updateAllWidgets(context); isMeasuring = false }
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
