package com.example.chs

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.roundToInt

/** A heart-rate value older than this means the sensor stopped reporting (finger lifted). */
private const val STALE_NANOS = 3_000_000_000L
private const val DETECT_SHARE = 0.3f
private const val DETECT_TIME_CONSTANT_SECONDS = 4.0

/**
 * One progress bar for the whole measurement, like Samsung Health.
 * While the sensor is still locking on (no bpm yet) the bar creeps towards 30 % with time;
 * once values arrive the stable-measurement progress fills the remaining 70 %.
 */
fun pulseProgress(detectingNanos: Long?, measuring: Float?): Float = when {
    measuring != null -> DETECT_SHARE + (1f - DETECT_SHARE) * measuring.coerceIn(0f, 1f)
    detectingNanos != null -> (DETECT_SHARE * (1 - exp(-detectingNanos / 1e9 / DETECT_TIME_CONSTANT_SECONDS))).toFloat()
    else -> 0f
}

@Composable
fun MeasurementScreen(modifier: Modifier = Modifier, onBack: () -> Unit, onSave: (Int) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { manager.getDefaultSensor(Sensor.TYPE_HEART_RATE) }
    val session = remember { MeasurementSession(requiredNanos = 10_000_000_000L) }
    var attempt by remember { mutableIntStateOf(0) }
    var latestBpm by remember { mutableStateOf<Int?>(null) }
    var latestAt by remember { mutableLongStateOf(0L) }
    var liveSamples by remember { mutableStateOf(emptyList<LivePulseSample>()) }
    var state by remember { mutableStateOf<MeasurementState>(MeasurementState.WaitingForFinger) }
    var signalLost by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }
    var contactSince by remember { mutableStateOf<Long?>(null) }
    var noContact by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var waitStart by remember { mutableLongStateOf(0L) }
    val listener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val value = event.values.firstOrNull() ?: return
                if (event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT) {
                    noContact = true
                    contactSince = null
                    latestBpm = null
                    return
                }
                noContact = false
                if (contactSince == null) contactSince = SystemClock.elapsedRealtimeNanos()
                if (event.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE && value.isFinite() && value in 30f..220f) {
                    val bpm = value.roundToInt()
                    latestBpm = bpm
                    latestAt = SystemClock.elapsedRealtimeNanos()
                    liveSamples = addLiveSample(liveSamples, LivePulseSample(event.timestamp, bpm))
                } else {
                    latestBpm = null
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
    }

    DisposableEffect(sensor, lifecycle, attempt) {
        session.reset()
        state = MeasurementState.WaitingForFinger
        signalLost = false
        latestBpm = null
        liveSamples = emptyList()
        error = null
        contactSince = null
        noContact = false
        progress = 0f
        waitStart = SystemClock.elapsedRealtimeNanos()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && listening) {
                manager.unregisterListener(listener)
                listening = false
                error = "Măsurarea s-a oprit pentru că aplicația a fost închisă."
            }
        }
        lifecycle.addObserver(observer)
        when {
            sensor == null -> error = "Senzorul de puls nu este disponibil pe acest dispozitiv."
            !manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST) -> error = "Senzorul nu a putut fi pornit. Încercați din nou."
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
            val now = SystemClock.elapsedRealtimeNanos()
            val bpm = latestBpm?.takeIf { now - latestAt < STALE_NANOS }
            state = session.update(now, bpm?.toFloat())
            signalLost = session.signalLost
            if (signalLost || noContact) { contactSince = null; waitStart = now }
            progress = when (val current = state) {
                is MeasurementState.Measuring -> pulseProgress(null, current.progress)
                is MeasurementState.Done -> 1f
                // Events without a value yet still mean the finger is on; if the sensor sends nothing
                // at all before its first value, count from the start so the bar still moves.
                else -> pulseProgress(now - (contactSince ?: waitStart), null)
            }
            if (state is MeasurementState.Done || state is MeasurementState.TimedOut) {
                manager.unregisterListener(listener)
                listening = false
            }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("‹ Înapoi") }
        Text("Măsurare puls", modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(32.dp))
        val beatMillis = (60_000 / ((state as? MeasurementState.Measuring)?.current ?: 75)).coerceIn(300, 1500)
        val beat = rememberInfiniteTransition(label = "beat").animateFloat(
            initialValue = 1f, targetValue = if (listening) 1.18f else 1f,
            animationSpec = infiniteRepeatable(tween(beatMillis / 2), RepeatMode.Reverse), label = "scale"
        )
        Text("♥", color = PulseRed, style = MaterialTheme.typography.displayLarge, modifier = Modifier.scale(beat.value))
        Spacer(Modifier.height(20.dp))
        Text(
            "Puneți degetul pe senzorul de puls de pe spatele telefonului. Nu apăsați prea tare și nu vă mișcați.",
            textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(24.dp))
        val currentError = error
        val current = state
        when {
            currentError != null -> {
                Text(currentError, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                if (sensor != null) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { attempt++ }) { Text("Încearcă din nou") }
                }
            }
            current is MeasurementState.Done -> {
                Text("${current.value} bpm", style = MaterialTheme.typography.displayMedium)
                Text(pulseCategory(current.value), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSave(current.value) }) { Text("Salvează") }
                    OutlinedButton(onClick = { attempt++ }) { Text("Repetă") }
                }
            }
            current is MeasurementState.TimedOut -> {
                Text("Nu s-a detectat pulsul. Verificați poziția degetului și încercați din nou.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { attempt++ }) { Text("Repetă") }
            }
            noContact -> Text("Puneți degetul pe senzor.", style = MaterialTheme.typography.bodyLarge)
            else -> {
                if (current is MeasurementState.Measuring) {
                    Text("${current.current} bpm", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                }
                val label = when {
                    current is MeasurementState.Measuring -> "Se măsoară"
                    signalLost -> "Semnal pierdut. Țineți degetul nemișcat pe senzor"
                    else -> "Se detectează pulsul"
                }
                Text("$label... ${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = if (signalLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = PulseRed)
            }
        }
        Spacer(Modifier.height(28.dp))
        LivePulseChart(liveSamples)
        Spacer(Modifier.height(16.dp))
        Text(Metric.PULSE.disclaimer, style = MaterialTheme.typography.bodySmall)
    }
}

/** Resting heart rate for adults: below 60 is low, above 100 is high. */
fun pulseCategory(bpm: Int): String = when {
    bpm < 60 -> "Sub intervalul obișnuit în repaus (60–100 bpm)"
    bpm <= 100 -> "În intervalul obișnuit în repaus (60–100 bpm)"
    else -> "Peste intervalul obișnuit în repaus (60–100 bpm)"
}
