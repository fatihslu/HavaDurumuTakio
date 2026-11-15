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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.havadurumutakip.data.LocationPreferences
import com.example.havadurumutakip.data.WeatherDatabaseHelper
import com.example.havadurumutakip.data.WeatherRepository
import com.example.havadurumutakip.export.ExcelExporter
import com.example.havadurumutakip.model.DailySummary
import com.example.havadurumutakip.model.GeocodingResult
import com.example.havadurumutakip.model.SavedLocation
import com.example.havadurumutakip.model.WeatherMeasurement
import com.example.havadurumutakip.network.OpenMeteoClient
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
                GlassBackground {
                    WeatherTrackerApp()
                }
            }
        }
    }
}

@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF091024),
                        Color(0xFF13224A),
                        Color(0xFF1C1C4C),
                        Color(0xFF0B1325)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x5538BDF8), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.16f),
                    radius = size.width * 0.78f
                ),
                radius = size.width * 0.78f,
                center = Offset(size.width * 0.15f, size.height * 0.16f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x4A818CF8), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.44f),
                    radius = size.width * 0.72f
                ),
                radius = size.width * 0.72f,
                center = Offset(size.width * 0.88f, size.height * 0.44f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x382DD4BF), Color.Transparent),
                    center = Offset(size.width * 0.25f, size.height * 0.86f),
                    radius = size.width * 0.68f
                ),
                radius = size.width * 0.68f,
                center = Offset(size.width * 0.25f, size.height * 0.86f)
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val fillBrush = if (highlight) {
        Brush.linearGradient(
            colors = listOf(
                Color(0x3D38BDF8),
                Color(0x24818CF8),
                Color(0x14FFFFFF)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.06f)
            )
        )
    }
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.45f),
            Color.White.copy(alpha = 0.12f),
            Color.White.copy(alpha = 0.24f)
        )
    )

    Column(
        modifier = modifier
            .clip(shape)
            .background(brush = fillBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
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
    val bgBrush = if (selected) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x6638BDF8),
                Color(0x3338BDF8)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (enabled) 0.20f else 0.08f),
                Color.White.copy(alpha = if (enabled) 0.08f else 0.04f)
            )
        )
    }
    val borderColor = if (selected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.30f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) accent else Color.White.copy(alpha = 0.45f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherTrackerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refreshTick by WeatherRepository.refreshTick.collectAsState()
    val locPrefs = remember { LocationPreferences(context) }

    var trackedLocations by remember { mutableStateOf(locPrefs.getLocations()) }
    var primaryLocName by remember { mutableStateOf(locPrefs.getPrimaryLocationName()) }
    var latestByLocation by remember { mutableStateOf<Map<String, WeatherMeasurement?>>(emptyMap()) }
    var dailySummaries by remember { mutableStateOf<List<DailySummary>>(emptyList()) }
    var selectedFilterLocation by remember { mutableStateOf<String?>(null) }

    var selectedDay by remember { mutableStateOf<DailySummary?>(null) }
    var dayPendingDelete by remember { mutableStateOf<DailySummary?>(null) }
    var locationPendingRemove by remember { mutableStateOf<SavedLocation?>(null) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isMeasuring by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var statusError by remember { mutableStateOf<String?>(null) }

    val createExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isExporting = true
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val db = WeatherDatabaseHelper(context)
                        val summaries = db.getDailySummaries()
                        val measurements = db.getAllMeasurements()
                        db.close()
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            ExcelExporter.writeXlsx(out, summaries, measurements)
                        } ?: error("Dosya açılamadı")
                    }
                }
                isExporting = false
                result.onSuccess {
                    Toast.makeText(context, "Excel dosyası kaydedildi!", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(context, "Hata: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                }
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
                    db.close()

                    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val file = File(exportDir, "hava_durumu_${LocalDate.now()}.xlsx")
                    file.outputStream().use { out ->
                        ExcelExporter.writeXlsx(out, summaries, measurements)
                    }
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
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
            }.onFailure {
                Toast.makeText(context, "Hata: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun reloadLocationsFromPrefs() {
        trackedLocations = locPrefs.getLocations()
        primaryLocName = locPrefs.getPrimaryLocationName()
    }

    LaunchedEffect(refreshTick, trackedLocations) {
        withContext(Dispatchers.IO) {
            val db = WeatherDatabaseHelper(context)
            dailySummaries = db.getDailySummaries()
            val map = mutableMapOf<String, WeatherMeasurement?>()
            for (loc in trackedLocations) {
                map[loc.name] = db.getLatestMeasurementForLocation(loc.name)
            }
            latestByLocation = map
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
            isMeasuring = true
            WeatherRepository.measureAndSaveAll(context)
            isMeasuring = false
        }
    }

    val filterNames = remember(dailySummaries) {
        dailySummaries.map { it.locationName }.distinct()
    }
    val visibleSummaries = remember(dailySummaries, selectedFilterLocation) {
        if (selectedFilterLocation == null) dailySummaries
        else dailySummaries.filter { it.locationName == selectedFilterLocation }
    }

    if (selectedDay != null) {
        BackHandler { selectedDay = null }
        DayDetailScreen(
            summary = selectedDay!!,
            onBack = { selectedDay = null }
        )
    } else {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    ),
                    title = {
                        Text(
                            "Hava Durumu",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            maxLines = 1
                        )
                    },
                    actions = {
                        GlassChipButton(
                            text = "📊 Excel",
                            onClick = { showExportDialog = true },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        GlassChipButton(
                            text = "➕ Konum",
                            onClick = { showLocationDialog = true },
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Takip Edilen Konumlar (${trackedLocations.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                if (trackedLocations.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
                            Text(
                                "Henüz takip edilen konum yok. Sağ üstten '➕ Konum' ile şehir/ilçe ekleyin.",
                                color = Color.White
                            )
                        }
                    }
                } else {
                    items(trackedLocations, key = { it.name }) { loc ->
                        val latest = latestByLocation[loc.name]
                        val isWidgetLoc = loc.name == primaryLocName
                        TrackedLocationCard(
                            location = loc,
                            latest = latest,
                            isWidgetPrimary = isWidgetLoc,
                            onSetWidgetPrimary = {
                                locPrefs.setPrimaryLocationName(loc.name)
                                primaryLocName = loc.name
                                WeatherWidgetProvider.updateAllWidgets(context)
                                Toast.makeText(context, "Widget konumu: ${loc.name}", Toast.LENGTH_SHORT).show()
                            },
                            onRemoveClick = { locationPendingRemove = loc }
                        )
                    }
                }

                item {
                    if (statusError != null) {
                        Text(
                            text = "Hata: $statusError",
                            color = Color(0xFFFDA4AF),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    GlassChipButton(
                        text = if (isMeasuring) "Tüm Konumlar Ölçülüyor..." else "↻ Tüm Konumları Ölç ve Kaydet",
                        enabled = !isMeasuring && trackedLocations.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                isMeasuring = true
                                statusError = null
                                val res = WeatherRepository.measureAndSaveAll(context)
                                res.onFailure { statusError = it.localizedMessage }
                                isMeasuring = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        accent = Color(0xFFE0F2FE)
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Günlük Geçmiş",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                if (filterNames.size > 1) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                GlassChipButton(
                                    text = "Tümü",
                                    selected = selectedFilterLocation == null,
                                    onClick = { selectedFilterLocation = null }
                                )
                            }
                            items(filterNames) { name ->
                                val shortName = name.substringBefore(",")
                                GlassChipButton(
                                    text = shortName,
                                    selected = selectedFilterLocation == name,
                                    onClick = { selectedFilterLocation = name }
                                )
                            }
                        }
                    }
                }

                if (visibleSummaries.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Henüz geçmiş kaydı yok.",
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                } else {
                    items(
                        items = visibleSummaries,
                        key = { "${it.dateKey}|${it.locationName}" }
                    ) { day ->
                        DailySummaryCard(
                            day = day,
                            onClick = { selectedDay = day },
                            onDeleteClick = { dayPendingDelete = day }
                        )
                    }
                }
            }
        }
    }

    if (locationPendingRemove != null) {
        val target = locationPendingRemove!!
        AlertDialog(
            containerColor = Color(0xF0131C31),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCBD5E1),
            onDismissRequest = { locationPendingRemove = null },
            title = { Text("Konumu Kaldır") },
            text = {
                Text("${target.name} artık otomatik saatlik ölçümlerden çıkarılsın mı? (Eski geçmiş kayıtları silinmez.)")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        locationPendingRemove = null
                        locPrefs.removeLocation(target.name)
                        if (selectedFilterLocation == target.name) {
                            selectedFilterLocation = null
                        }
                        reloadLocationsFromPrefs()
                        WeatherWidgetProvider.updateAllWidgets(context)
                    }
                ) {
                    Text("Kaldır", color = Color(0xFFFB7185), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { locationPendingRemove = null }) {
                    Text("İptal", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    if (dayPendingDelete != null) {
        val target = dayPendingDelete!!
        AlertDialog(
            containerColor = Color(0xF0131C31),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCBD5E1),
            onDismissRequest = { dayPendingDelete = null },
            title = { Text("Günü Sil") },
            text = {
                Text(
                    "${formatDateTurkish(target.dateKey)}\n${target.locationName}\n\nBu güne ait ${target.measurementCount} ölçüm kalıcı olarak silinsin mi?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dayPendingDelete = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = WeatherDatabaseHelper(context)
                                db.deleteDay(target.dateKey, target.locationName)
                                db.close()
                                WeatherWidgetProvider.updateAllWidgets(context)
                            }
                            WeatherRepository.notifyChanged()
                            Toast.makeText(context, "Günlük kayıt silindi", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Sil", color = Color(0xFFFB7185), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { dayPendingDelete = null }) {
                    Text("İptal", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    if (showLocationDialog) {
        LocationSearchDialog(
            onDismiss = { showLocationDialog = false },
            onSelectLocation = { picked ->
                val saved = SavedLocation(picked.displayName, picked.latitude, picked.longitude)
                locPrefs.addLocation(saved)
                reloadLocationsFromPrefs()
                showLocationDialog = false
                scope.launch {
                    isMeasuring = true
                    WeatherRepository.measureAndSaveAll(context)
                    WeatherWidgetProvider.updateAllWidgets(context)
                    isMeasuring = false
                }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            containerColor = Color(0xF0131C31),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCBD5E1),
            onDismissRequest = { if (!isExporting) showExportDialog = false },
            title = { Text("Excel Dışa Aktar (.xlsx)") },
            text = {
                Column {
                    Text(
                        "Tüm konumlarınız ve verileriniz 2 sayfalı Excel dosyasına aktarılır:\n• Günlük Özet (Min, Ort, Max, Yağış, Bulut, UV)\n• Saatlik Kayıtlar (Sıcaklık, Hissedilen, Nem, Yağış, Güneş/Bulut, UV, Rüzgâr)",
                        fontSize = 14.sp
                    )
                    if (isExporting) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isExporting,
                    onClick = {
                        showExportDialog = false
                        createExcelLauncher.launch("hava_durumu_${LocalDate.now()}.xlsx")
                    }
                ) {
                    Text("Telefona Kaydet")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        enabled = !isExporting,
                        onClick = {
                            showExportDialog = false
                            shareExcel()
                        }
                    ) {
                        Text("Paylaş", color = Color(0xFF38BDF8))
                    }
                    TextButton(
                        enabled = !isExporting,
                        onClick = { showExportDialog = false }
                    ) {
                        Text("İptal", color = Color(0xFF94A3B8))
                    }
                }
            }
        )
    }
}

@Composable
fun TrackedLocationCard(
    location: SavedLocation,
    latest: WeatherMeasurement?,
    isWidgetPrimary: Boolean,
    onSetWidgetPrimary: () -> Unit,
    onRemoveClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        highlight = isWidgetPrimary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = location.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            GlassChipButton(
                text = if (isWidgetPrimary) "⭐ Widget" else "☆ Widget Yap",
                selected = isWidgetPrimary,
                onClick = onSetWidgetPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            GlassChipButton(
                text = "🗑️",
                onClick = onRemoveClick,
                accent = Color(0xFFFDA4AF)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (latest != null) {
            val (emoji, desc) = OpenMeteoClient.weatherDescription(latest.weatherCode)
            val sun = OpenMeteoClient.sunCondition(latest.isDay, latest.cloudCover, latest.precipitation)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format(Locale.US, "%.1f °C", latest.temperature),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "$emoji $desc  •  Hissedilen: ${String.format(Locale.US, "%.1f°", latest.apparentTemp)}",
                        fontSize = 14.sp,
                        color = Color(0xFFE0F2FE)
                    )
                    Text(
                        text = "☀️ $sun (%${latest.cloudCover} bulut)  •  💧 ${latest.precipitation} mm",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.80f)
                    )
                    Text(
                        text = "UV: ${latest.uvIndex}  •  Son: ${latest.dateKey} ${latest.timeLabel}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
        } else {
            Text(
                "Bu konum için henüz ölçüm alınmadı.",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun DailySummaryCard(
    day: DailySummary,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDateTurkish(day.dateKey),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Text(
                    text = "${day.locationName} • ${day.measurementCount} ölçüm",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.72f)
                )
            }
            GlassChipButton(
                text = "🗑️",
                onClick = onDeleteClick,
                accent = Color(0xFFFDA4AF)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TempBadge("Min", day.minTemp, Color(0xFF38BDF8))
            TempBadge("Ort", day.avgTemp, Color(0xFF2DD4BF))
            TempBadge("Max", day.maxTemp, Color(0xFFFB923C))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = String.format(
                Locale.US,
                "💧 Toplam Yağış: %.1f mm  •  ☁️ Ort. Bulut: %%%d  •  ☀️ Max UV: %.1f",
                day.totalPrecipitation,
                day.avgCloudCover,
                day.maxUvIndex
            ),
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.72f)
        )
    }
}

@Composable
fun TempBadge(label: String, value: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.68f)
        )
        Text(
            text = String.format(Locale.US, "%.1f °C", value),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(summary: DailySummary, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hourlyList by remember { mutableStateOf<List<WeatherMeasurement>>(emptyList()) }
    var itemPendingDelete by remember { mutableStateOf<WeatherMeasurement?>(null) }

    LaunchedEffect(summary) {
        withContext(Dispatchers.IO) {
            val db = WeatherDatabaseHelper(context)
            hourlyList = db.getHourlyForDay(summary.dateKey, summary.locationName)
            db.close()
        }
    }

    val liveMin = if (hourlyList.isNotEmpty()) hourlyList.minOf { it.temperature } else summary.minTemp
    val liveAvg = if (hourlyList.isNotEmpty()) hourlyList.map { it.temperature }.average() else summary.avgTemp
    val liveMax = if (hourlyList.isNotEmpty()) hourlyList.maxOf { it.temperature } else summary.maxTemp
    val livePrecip = if (hourlyList.isNotEmpty()) hourlyList.sumOf { it.precipitation } else summary.totalPrecipitation

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                ),
                title = {
                    Column {
                        Text(formatDateTurkish(summary.dateKey), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            summary.locationName,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                },
                navigationIcon = {
                    GlassChipButton(
                        text = "← Geri",
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 6.dp)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth(), highlight = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    TempBadge("Min", liveMin, Color(0xFF38BDF8))
                    TempBadge("Ortalama", liveAvg, Color(0xFF2DD4BF))
                    TempBadge("Max", liveMax, Color(0xFFFB923C))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = String.format(Locale.US, "💧 Günlük Toplam Yağış: %.1f mm", livePrecip),
                    fontSize = 12.sp,
                    color = Color(0xFFE0F2FE),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Saatlik Ölçümler",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(hourlyList, key = { it.id }) { item ->
                    val (emoji, desc) = OpenMeteoClient.weatherDescription(item.weatherCode)
                    val sun = OpenMeteoClient.sunCondition(item.isDay, item.cloudCover, item.precipitation)
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.timeLabel}  •  $emoji $desc",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "☀️ $sun (%${item.cloudCover})  •  💧 ${item.precipitation} mm  •  UV ${item.uvIndex}",
                                    fontSize = 12.sp,
                                    color = Color(0xFFBAE6FD)
                                )
                                Text(
                                    text = "Hissedilen: ${String.format(Locale.US, "%.1f°", item.apparentTemp)}  •  Nem: %${item.humidity}  •  Rüzgâr: ${item.windSpeed} km/sa",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = String.format(Locale.US, "%.1f °C", item.temperature),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                GlassChipButton(
                                    text = "🗑️",
                                    onClick = { itemPendingDelete = item },
                                    accent = Color(0xFFFDA4AF)
                                )
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
            containerColor = Color(0xF0131C31),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCBD5E1),
            onDismissRequest = { itemPendingDelete = null },
            title = { Text("Ölçümü Sil") },
            text = {
                Text("${target.timeLabel} saatindeki (${target.temperature} °C) ölçüm silinsin mi?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemPendingDelete = null
                        scope.launch {
                            val remaining = withContext(Dispatchers.IO) {
                                val db = WeatherDatabaseHelper(context)
                                db.deleteMeasurementById(target.id)
                                val updated = db.getHourlyForDay(summary.dateKey, summary.locationName)
                                db.close()
                                WeatherWidgetProvider.updateAllWidgets(context)
                                updated
                            }
                            WeatherRepository.notifyChanged()
                            hourlyList = remaining
                            Toast.makeText(context, "Ölçüm silindi", Toast.LENGTH_SHORT).show()
                            if (remaining.isEmpty()) {
                                onBack()
                            }
                        }
                    }
                ) {
                    Text("Sil", color = Color(0xFFFB7185), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemPendingDelete = null }) {
                    Text("İptal", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

@Composable
fun LocationSearchDialog(
    onDismiss: () -> Unit,
    onSelectLocation: (GeocodingResult) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeocodingResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        containerColor = Color(0xF0131C31),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCBD5E1),
        onDismissRequest = onDismiss,
        title = { Text("Konum Ekle") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Şehir veya ilçe") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (query.isBlank()) return@Button
                            scope.launch {
                                searching = true
                                errorMsg = null
                                try {
                                    results = withContext(Dispatchers.IO) {
                                        OpenMeteoClient.searchLocations(query)
                                    }
                                    if (results.isEmpty()) errorMsg = "Sonuç bulunamadı."
                                } catch (e: Exception) {
                                    errorMsg = e.localizedMessage
                                }
                                searching = false
                            }
                        },
                        enabled = !searching
                    ) {
                        Text("Ara")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                if (searching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (errorMsg != null) {
                    Text(errorMsg!!, color = Color(0xFFFDA4AF), fontSize = 12.sp)
                }

                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(results) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectLocation(item) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(item.displayName, fontWeight = FontWeight.Medium, color = Color.White)
                            Text(
                                text = "Enlem: ${item.latitude}, Boylam: ${item.longitude}",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 6.dp),
                                color = Color.White.copy(alpha = 0.14f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = Color(0xFF94A3B8))
            }
        }
    )
}

private fun formatDateTurkish(dateKey: String): String {
    return try {
        val date = LocalDate.parse(dateKey)
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale.forLanguageTag("tr-TR"))
        date.format(formatter)
    } catch (_: Exception) {
        dateKey
    }
}
