package com.example.havadurumutakip

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
        WeatherSyncWorker.scheduleHourly(this)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WeatherTrackerApp()
                }
            }
        }
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
            topBar = {
                TopAppBar(
                    title = { Text("Hava Durumu", maxLines = 1) },
                    actions = {
                        TextButton(onClick = { showExportDialog = true }) {
                            Text("📊 Excel")
                        }
                        TextButton(onClick = { showLocationDialog = true }) {
                            Text("📍 Konum")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = currentLocation.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val latest = latestMeasurement
                        if (latest != null) {
                            val (emoji, desc) = OpenMeteoClient.weatherDescription(latest.weatherCode)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = String.format(Locale.US, "%.1f °C", latest.temperature),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(text = "$emoji $desc", fontSize = 16.sp)
                                    Text(
                                        text = "Son ölçüm: ${latest.dateKey} ${latest.timeLabel}",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            Text("Bu konum için henüz ölçüm alınmadı.")
                        }

                        if (statusError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Hata: $statusError",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isMeasuring = true
                                    statusError = null
                                    val res = WeatherRepository.measureAndSaveNow(context)
                                    res.onFailure { statusError = it.localizedMessage }
                                    isMeasuring = false
                                }
                            },
                            enabled = !isMeasuring,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isMeasuring) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ölçülüyor...")
                            } else {
                                Text("↻ Şimdi Ölç ve Kaydet")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Günlük Geçmiş",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (dailySummaries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Henüz geçmiş kaydı yok.")
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(dailySummaries) { day ->
                            DailySummaryCard(day = day, onClick = { selectedDay = day })
                        }
                    }
                }
            }
        }
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
                        Text("Paylaş")
                    }
                    TextButton(
                        enabled = !isExporting,
                        onClick = { showExportDialog = false }
                    ) {
                        Text("İptal")
                    }
                }
            }
        )
    }
}

@Composable
fun DailySummaryCard(day: DailySummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDateTurkish(day.dateKey),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${day.measurementCount} ölçüm",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = day.locationName,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TempBadge("Min", day.minTemp, Color(0xFF0284C7))
                TempBadge("Ort", day.avgTemp, Color(0xFF0D9488))
                TempBadge("Max", day.maxTemp, Color(0xFFEA580C))
            }
        }
    }
}

@Composable
fun TempBadge(label: String, value: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var hourlyList by remember { mutableStateOf<List<WeatherMeasurement>>(emptyList()) }

    LaunchedEffect(summary) {
        withContext(Dispatchers.IO) {
            val db = WeatherDatabaseHelper(context)
            hourlyList = db.getHourlyForDay(summary.dateKey, summary.locationName)
            db.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(formatDateTurkish(summary.dateKey), fontSize = 17.sp)
                        Text(summary.locationName, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    TempBadge("Min", summary.minTemp, Color(0xFF0284C7))
                    TempBadge("Ortalama", summary.avgTemp, Color(0xFF0D9488))
                    TempBadge("Max", summary.maxTemp, Color(0xFFEA580C))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Saatlik Ölçümler",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(hourlyList) { item ->
                    val (emoji, desc) = OpenMeteoClient.weatherDescription(item.weatherCode)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = item.timeLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(text = "$emoji $desc", fontSize = 13.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format(Locale.US, "%.1f °C", item.temperature),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Nem: %${item.humidity}  •  Rüzgâr: ${item.windSpeed} km/sa",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
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
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(results) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectLocation(item) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(item.displayName, fontWeight = FontWeight.Medium)
                            Text(
                                text = "Enlem: ${item.latitude}, Boylam: ${item.longitude}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
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
