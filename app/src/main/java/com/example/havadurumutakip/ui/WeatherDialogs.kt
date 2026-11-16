package com.example.havadurumutakip.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.havadurumutakip.GlassCard
import com.example.havadurumutakip.GlassChipButton
import com.example.havadurumutakip.cosa.CosaApiClient
import com.example.havadurumutakip.cosa.CosaEndpointSummary
import com.example.havadurumutakip.model.GeocodingResult
import com.example.havadurumutakip.model.MetricCategory
import com.example.havadurumutakip.model.WeatherMeasurement
import com.example.havadurumutakip.model.WeatherMetric
import com.example.havadurumutakip.network.OpenMeteoClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LocationDetailsDialog(
    measurement: WeatherMeasurement,
    selectedMetrics: Set<WeatherMetric>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        containerColor = Color(0xF5131C31),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCBD5E1),
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(measurement.locationName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Son ölçüm: ${measurement.dateKey} ${measurement.timeLabel}", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                MetricDetailsGrid(measurement = measurement, selectedMetrics = selectedMetrics, excludeTopBasic = false)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kapat", color = Color(0xFF38BDF8)) }
        }
    )
}

@Composable
fun CosaLoginDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onConnected: (String, String, String, CosaEndpointSummary) -> Unit
) {
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
