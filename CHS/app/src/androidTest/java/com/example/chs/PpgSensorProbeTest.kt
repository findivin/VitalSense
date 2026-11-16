package com.example.chs

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * Runs only on phones that expose raw red/IR PPG (e.g. Galaxy S10). Reads the real sensors for a few
 * seconds and checks that both channels stream data fast enough for the SpO₂ algorithm.
 * Values are logged under the "PpgProbe" tag.
 */
@RunWith(AndroidJUnit4::class)
class PpgSensorProbeTest {
    @Test
    fun rawRedAndInfraredSensorsStreamData() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.BODY_SENSORS)
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val source = findPpgSensors(manager.getSensorList(Sensor.TYPE_ALL)) { SensorInfo(it.name, it.stringType.orEmpty(), it.type) }
        assumeTrue("No raw PPG sensor on this device", source != null)

        val ir = Collections.synchronizedList(mutableListOf<LightSample>())
        val red = Collections.synchronizedList(mutableListOf<LightSample>())
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val value = event.values.firstOrNull() ?: return
                when (source) {
                    is PpgSource.Separate -> if (event.sensor == source.ir) ir.add(LightSample(event.timestamp, value)) else red.add(LightSample(event.timestamp, value))
                    is PpgSource.Combined -> if (event.values.size >= 2) { ir.add(LightSample(event.timestamp, value)); red.add(LightSample(event.timestamp, event.values[1])) }
                    null -> Unit
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val thread = HandlerThread("ppg-probe").apply { start() }
        val sensors = when (source) {
            is PpgSource.Separate -> listOf(source.ir, source.red)
            is PpgSource.Combined -> listOf(source.sensor)
            null -> emptyList()
        }
        try {
            sensors.forEach { assertTrue("register ${it.name}", manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST, Handler(thread.looper))) }
            Thread.sleep((InstrumentationRegistry.getArguments().getString("probeSeconds")?.toLongOrNull() ?: 6L) * 1_000)
        } finally {
            manager.unregisterListener(listener)
            thread.quitSafely()
        }

        fun stats(name: String, samples: List<LightSample>): Double {
            if (samples.size < 2) { Log.i("PpgProbe", "$name: ${samples.size} samples"); return 0.0 }
            val seconds = (samples.last().timestampNanos - samples.first().timestampNanos) / 1e9
            val values = samples.map { it.value }
            val rate = (samples.size - 1) / seconds
            Log.i("PpgProbe", "$name: n=${samples.size} rate=%.1f Hz min=%.1f max=%.1f mean=%.1f first=%s".format(rate, values.min(), values.max(), values.average(), values.take(8)))
            return rate
        }
        val irSnapshot = synchronized(ir) { ir.toList() }
        val redSnapshot = synchronized(red) { red.toList() }
        val irRate = stats("IR (${sensors.first().name})", irSnapshot)
        val redRate = stats("RED (${sensors.last().name})", redSnapshot)
        val paired = pairSamples(irSnapshot, redSnapshot)
        InstrumentationRegistry.getArguments().getString("probeFile")?.let { name ->
            val file = java.io.File(context.getExternalFilesDir(null), name)
            file.printWriter().use { out ->
                out.println("t_ns,ir,red")
                paired.forEach { out.println("${it.timestampNanos},${it.ir},${it.red}") }
            }
            Log.i("PpgProbe", "wrote ${file.absolutePath}")
        }
        Log.i("PpgProbe", "paired=${paired.size} estimate=${Spo2Calculator.estimate(paired)} ${describeSignal(paired, null)}")

        assertTrue("IR rate $irRate Hz", irRate >= Spo2Calculator.MIN_RATE_HZ)
        assertTrue("RED rate $redRate Hz", redRate >= Spo2Calculator.MIN_RATE_HZ)
        assertTrue(irSnapshot.all { it.value.isFinite() } && redSnapshot.all { it.value.isFinite() })
        assertTrue("pairing keeps most samples", paired.size >= irSnapshot.size * 0.8)
    }
}
