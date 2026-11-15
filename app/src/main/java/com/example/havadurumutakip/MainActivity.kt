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
    accent: Color = Color.White
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (enabled) 0.20f else 0.08f),
                        Color.White.copy(alpha = if (enabled) 0.08f else 0.04f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.30f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) accent else Color.White.copy(alpha = 0.45f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherTrackerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refreshTick by WeatherRepository.refreshTick.collectAsState()

    var currentLocation by remember { mutableStateOf(LocationPreferences(context).getLocation()) }
    var dailySummaries by remember { mutableStateOf<List<DailySummary>>(emptyList()) }
    var latestMeasurement by remember { mutableStateOf<WeatherMeasurement?>(null) }
    var selectedDay by remember { mutableStateOf<DailySummary?>(null) }
    var dayPendingDelete by remember { mutableStateOf<DailySummary?>(null) }
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

    LaunchedEffect(refreshTick, currentLocation) {
        withContext(Dispatchers.IO) {
            val db = WeatherDatabaseHelper(context)
            dailySummaries = db.getDailySummaries()
            latestMeasurement = db.getLatestMeasurementForLocation(currentLocation.name)
            db.close()
        }
    }

    LaunchedEffect(Unit) {
        val hasData = withContext(Dispatchers.IO) {
            val db = WeatherDatabaseHelper(context)
            val m = db.getLatestMeasurementForLocation(currentLocation.name)
            db.close()
            m != null
        }
        if (!hasData) {
            isMeasuring = true
            WeatherRepository.measureAndSaveNow(context)
            isMeasuring = false
        }
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
                            text = "📍 Konum",
                            onClick = { showLocationDialog = true },
                            modifier = Modifier.padding(end = 10.dp)
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
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    highlight = true
                ) {
                    Text(
                        text = currentLocation.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val latest = latestMeasurement
                    if (latest != null) {
                        val (emoji, desc) = OpenMeteoClient.weatherDescription(latest.weatherCode)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = String.format(Locale.US, "%.1f °C", latest.temperature),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "$emoji $desc",
                                    fontSize = 16.sp,
                                    color = Color(0xFFE0F2FE)
                                )
                                Text(
                                    text = "Son ölçüm: ${latest.dateKey} ${latest.timeLabel}",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                            }
                        }
                    } else {
                        Text(
                            "Bu konum için henüz ölçüm alınmadı.",
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    if (statusError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Hata: $statusError",
                            color = Color(0xFFFDA4AF),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    GlassChipButton(
                        text = if (isMeasuring) "Ölçülüyor..." else "↻ Şimdi Ölç ve Kaydet",
                        enabled = !isMeasuring,
                        onClick = {
                            scope.launch {
                                isMeasuring = true
                                statusError = null
                                val res = WeatherRepository.measureAndSaveNow(context)
                                res.onFailure { statusError = it.localizedMessage }
                                isMeasuring = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        accent = Color(0xFFE0F2FE)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Günlük Geçmiş",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (dailySummaries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Henüz geçmiş kaydı yok.",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            items = dailySummaries,
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
                LocationPreferences(context).saveLocation(saved)
                currentLocation = saved
                showLocationDialog = false
                scope.launch {
                    isMeasuring = true
                    WeatherRepository.measureAndSaveNow(context)
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
                        "Tüm geçmişiniz 2 sayfalı gerçek Excel dosyasına aktarılır:\n• Günlük Özet (Min / Ort / Max)\n• Tüm Saatlik Kayıtlar",
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
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TempBadge("Min", day.minTemp, Color(0xFF38BDF8))
            TempBadge("Ort", day.avgTemp, Color(0xFF2DD4BF))
            TempBadge("Max", day.maxTemp, Color(0xFFFB923C))
        }
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
                                    text = "Nem: %${item.humidity}  •  Rüzgâr: ${item.windSpeed} km/sa",
                                    fontSize = 12.sp,
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
                                Spacer(modifier = Modifier.width(10.dp))
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
        title = { Text("Konum Ara") },
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
