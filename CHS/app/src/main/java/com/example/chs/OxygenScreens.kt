package com.example.chs

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun OxygenMeasurementScreen(modifier: Modifier = Modifier, onBack: () -> Unit, onSave: (Int) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val allSensors = remember { manager.getSensorList(Sensor.TYPE_ALL) }
    val source = remember { findPpgSensors(allSensors) { SensorInfo(it.name, it.stringType.orEmpty(), it.type) } }
    val buffer = remember { PpgBuffer() }
    val session = remember { MeasurementSession() }
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<MeasurementState>(MeasurementState.WaitingForFinger) }
    var signalLost by remember { mutableStateOf(false) }
    var wave by remember { mutableStateOf(emptyList<Float>()) }
    var diagnostics by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showSensors by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    val listener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (source) {
                    is PpgSource.Separate -> when (event.sensor) {
                        source.ir -> event.values.firstOrNull()?.let { buffer.addIr(LightSample(event.timestamp, it)) }
                        source.red -> event.values.firstOrNull()?.let { buffer.addRed(LightSample(event.timestamp, it)) }
                    }
                    is PpgSource.Combined -> if (event.values.size >= 2) buffer.addBoth(event.timestamp, event.values[0], event.values[1])
                    null -> Unit
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
    }

    DisposableEffect(source, lifecycle, attempt) {
        buffer.clear()
        session.reset()
        state = MeasurementState.WaitingForFinger
        signalLost = false
        wave = emptyList()
        error = null
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && listening) {
                manager.unregisterListener(listener)
                listening = false
                error = "Măsurarea s-a oprit. Reveniți și încercați din nou."
            }
        }
        lifecycle.addObserver(observer)
        val sensors = when (source) {
            is PpgSource.Separate -> listOf(source.ir, source.red)
            is PpgSource.Combined -> listOf(source.sensor)
            null -> emptyList()
        }
        when {
            sensors.isEmpty() -> error = "Senzorul de lumină roșie/infraroșie nu este accesibil aplicațiilor pe acest dispozitiv."
            !sensors.all { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST) } -> {
                manager.unregisterListener(listener)
                error = "Senzorul de lumină roșie/infraroșie nu a putut fi pornit. Încercați din nou."
            }
            else -> listening = true
        }
        onDispose {
            manager.unregisterListener(listener)
            listening = false
            lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(listening, attempt) {
        while (listening) {
            delay(500)
            val samples = buffer.paired()
            val estimate = Spo2Calculator.estimate(samples)
            state = session.update(SystemClock.elapsedRealtimeNanos(), estimate?.spo2)
            signalLost = session.signalLost
            wave = waveform(samples)
            diagnostics = describeSignal(samples, estimate)
            if (state is MeasurementState.Done || state is MeasurementState.TimedOut) {
                manager.unregisterListener(listener)
                listening = false
            }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("‹ Înapoi") }
        Text("Măsurare oxigen (SpO₂)", modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(32.dp))
        Text("O₂", color = PulseRed, style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(20.dp))
        Text(
            "Acoperiți complet senzorul de pe spatele telefonului cu vârful degetului. Nu apăsați prea tare și nu vă mișcați.",
            textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(24.dp))
        val currentError = error
        val current = state
        when {
            currentError != null -> {
                Text(currentError, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                if (!listening && source != null) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { attempt++ }) { Text("Încearcă din nou") }
                }
            }
            current is MeasurementState.Done -> {
                Text("${current.value} %", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSave(current.value) }) { Text("Salvează") }
                    OutlinedButton(onClick = { attempt++ }) { Text("Repetă") }
                }
            }
            current is MeasurementState.TimedOut -> {
                Text("Nu s-a obținut un semnal stabil. Verificați poziția degetului și încercați din nou.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { attempt++ }) { Text("Repetă") }
            }
            current is MeasurementState.Measuring -> {
                Text("Se măsoară... ${(current.progress * 100).roundToInt()}%", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { current.progress }, modifier = Modifier.fillMaxWidth(), color = PulseRed)
                Spacer(Modifier.height(8.dp))
                Text("Valoare curentă: ~${current.current} %", style = MaterialTheme.typography.bodyMedium)
            }
            signalLost -> Text("Semnal pierdut. Țineți degetul nemișcat pe senzor.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            else -> Text("Se așteaptă degetul pe senzor...", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(28.dp))
        WaveformChart(wave)
        if (diagnostics.isNotEmpty()) {
            Text(diagnostics, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { showSensors = !showSensors }) { Text("Senzori detectați (${allSensors.size}) ${if (showSensors) "▴" else "▾"}") }
        if (showSensors) {
            val used = when (source) {
                is PpgSource.Separate -> "IR: ${source.ir.name}\nRED: ${source.red.name}"
                is PpgSource.Combined -> "IR+RED: ${source.sensor.name}"
                null -> "Niciun senzor PPG găsit"
            }
            Text(used, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            allSensors.forEach {
                Text("${it.name} — ${it.stringType} (${it.type})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(Metric.OXYGEN.disclaimer, style = MaterialTheme.typography.bodySmall)
    }
}

fun describeSignal(samples: List<PpgSample>, estimate: Spo2Estimate?): String {
    if (samples.size < 2) return ""
    val seconds = (samples.last().timestampNanos - samples.first().timestampNanos) / 1e9
    val rate = if (seconds > 0) (samples.size - 1) / seconds else 0.0
    val base = String.format(Locale.US, "%d eșantioane • %.0f Hz", samples.size, rate)
    return if (estimate == null) base else base + String.format(Locale.US, " • R = %.2f • PI = %.2f%%", estimate.ratio, estimate.perfusionIndex * 100)
}

@Composable
fun WaveformChart(points: List<Float>) {
    Text("Undă puls (lumină infraroșie)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        drawLine(ChartGridColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
        if (points.size > 1) {
            val min = points.min()
            val max = points.max()
            val range = (max - min).takeIf { it > 0f } ?: 1f
            val inset = 6.dp.toPx()
            val height = size.height - 2 * inset
            val path = Path()
            points.forEachIndexed { index, value ->
                val x = size.width * index / (points.size - 1)
                val y = inset + height * (1f - (value - min) / range)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, PulseRed, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
    Text("Ultimele 5 secunde", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
