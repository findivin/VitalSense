package com.example.chs

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.chs.ui.theme.CHSTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CHSTheme { PulseApp() } }
    }
}

enum class Screen { HOME, PULSE, OXYGEN }

private val romanianDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ro", "RO"))

@Composable
fun PulseApp(databaseName: String = "pulse.db", preferencesName: String = "profiles") {
    val context = LocalContext.current
    val store = remember { PulseStore(context.applicationContext, databaseName) }
    val preferences = remember { context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE) }
    var profiles by remember { mutableStateOf(store.profiles()) }
    var selectedId by remember { mutableStateOf(preferences.getLong("selected_profile", 1L)) }
    val selected = profiles.firstOrNull { it.id == selectedId } ?: profiles.first()
    var dataVersion by remember { mutableIntStateOf(0) }
    val pulseReadings = remember(selected.id, dataVersion) { store.allReadings(Metric.PULSE, selected.id) }
    val oxygenReadings = remember(selected.id, dataVersion) { store.allReadings(Metric.OXYGEN, selected.id) }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var measuring by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) measuring = true
    }
    val selectProfile: (Profile) -> Unit = { profile ->
        selectedId = profile.id
        preferences.edit().putLong("selected_profile", profile.id).apply()
    }
    val addProfile: (String, Int?, String) -> Unit = { name, age, description ->
        val id = store.addProfile(name, age, description)
        profiles = store.profiles()
        selectProfile(profiles.first { it.id == id })
    }
    val goHome = { screen = Screen.HOME; measuring = false; permissionDenied = false }

    DisposableEffect(store) { onDispose { store.close() } }
    BackHandler(enabled = screen != Screen.HOME) { if (measuring) measuring = false else goHome() }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        val metric = if (screen == Screen.OXYGEN) Metric.OXYGEN else Metric.PULSE
        val save: (Int) -> Unit = { value ->
            store.save(metric, selected.id, Reading(Instant.now(), value))
            dataVersion++
            measuring = false
        }
        when {
            screen == Screen.HOME -> HomeScreen(
                profiles = profiles,
                selected = selected,
                onSelectProfile = selectProfile,
                onAddProfile = addProfile,
                latestPulse = pulseReadings.firstOrNull(),
                latestOxygen = oxygenReadings.firstOrNull(),
                modifier = Modifier.padding(padding),
                onOpen = { screen = if (it == Metric.OXYGEN) Screen.OXYGEN else Screen.PULSE }
            )
            measuring && metric == Metric.PULSE -> MeasurementScreen(Modifier.padding(padding), onBack = { measuring = false }, onSave = save)
            measuring -> OxygenMeasurementScreen(Modifier.padding(padding), onBack = { measuring = false }, onSave = save)
            else -> key(metric) {
                HistoryScreen(
                    metric = metric,
                    readings = if (metric == Metric.OXYGEN) oxygenReadings else pulseReadings,
                    profiles = profiles,
                    selected = selected,
                    onSelectProfile = selectProfile,
                    onAddProfile = addProfile,
                    permissionDenied = permissionDenied,
                    modifier = Modifier.padding(padding),
                    onBack = goHome,
                    onMeasure = {
                        permissionDenied = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) {
                            measuring = true
                        } else {
                            permission.launch(Manifest.permission.BODY_SENSORS)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    profiles: List<Profile>,
    selected: Profile,
    onSelectProfile: (Profile) -> Unit,
    onAddProfile: (String, Int?, String) -> Unit,
    latestPulse: Reading?,
    latestOxygen: Reading?,
    modifier: Modifier = Modifier,
    onOpen: (Metric) -> Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("CHS", style = MaterialTheme.typography.headlineMedium)
        ProfilePicker(profiles, selected, onSelectProfile, onAddProfile)
        Spacer(Modifier.height(8.dp))
        MetricCard("♥", Metric.PULSE, latestPulse) { onOpen(Metric.PULSE) }
        Spacer(Modifier.height(16.dp))
        MetricCard("O₂", Metric.OXYGEN, latestOxygen) { onOpen(Metric.OXYGEN) }
        Spacer(Modifier.height(16.dp))
        Text("Măsurătorile sunt orientative și nu reprezintă un diagnostic medical.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun MetricCard(symbol: String, metric: Metric, latest: Reading?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, color = PulseRed, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(metric.title, style = MaterialTheme.typography.titleLarge)
                if (latest == null) {
                    Text("Nicio măsurătoare", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Ultima: ${latest.value} ${metric.unit}", style = MaterialTheme.typography.bodyLarge)
                    Text(latest.time.atZone(ZoneId.systemDefault()).toLocalDate().format(romanianDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("›", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun HistoryScreen(
    metric: Metric,
    readings: List<Reading>,
    profiles: List<Profile>,
    selected: Profile,
    onSelectProfile: (Profile) -> Unit,
    onAddProfile: (String, Int?, String) -> Unit,
    permissionDenied: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onMeasure: () -> Unit
) {
    var period by remember { mutableStateOf(Period.DAYS) }
    var offset by remember { mutableIntStateOf(0) }
    val zone = ZoneId.systemDefault()
    val buckets = bucketsFor(period, offset, LocalDate.now(zone), readings, zone)
    val start = buckets.first().start
    val end = buckets.last().end
    val inPeriod = readings.filter {
        val day = it.time.atZone(zone).toLocalDate()
        !day.isBefore(start) && !day.isAfter(end)
    }
    val average = if (inPeriod.isEmpty()) "--" else inPeriod.map { it.value }.average().roundToInt().toString()

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("‹ Înapoi") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(metric.title, style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onMeasure) { Text("+", style = MaterialTheme.typography.headlineSmall) }
        }
        ProfilePicker(profiles, selected, onSelectProfile, onAddProfile)
        if (permissionDenied) {
            Text("Permisiunea pentru senzor este necesară. Activați-o în setările aplicației și încercați din nou.", color = MaterialTheme.colorScheme.error)
        }
        TabRow(selectedTabIndex = period.ordinal) {
            Period.entries.forEach { item ->
                Tab(selected = item == period, onClick = { period = item; offset = 0 }, text = { Text(item.title) })
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { offset-- }) { Text("‹") }
                    Text("${start.format(romanianDate)} – ${end.format(romanianDate)}", textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    TextButton(onClick = { offset++ }, enabled = offset < 0) { Text("›") }
                }
                Spacer(Modifier.height(8.dp))
                Text("$average ${metric.unit}", style = MaterialTheme.typography.headlineLarge)
                Text("${metric.averageLabel}: $average ${metric.unit}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                if (inPeriod.isEmpty()) Text("Nu există măsurători în această perioadă.")
                PulseChart(buckets, period, metric)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(metric.disclaimer, style = MaterialTheme.typography.bodySmall)
    }
}
