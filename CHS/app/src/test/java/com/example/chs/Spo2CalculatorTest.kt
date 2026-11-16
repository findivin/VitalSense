package com.example.chs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val S = 1_000_000_000L

/** Synthetic finger PPG: IR with perfusion [pi], red with AC/DC = ratio * pi, so R == ratio. */
fun syntheticPpg(
    ratio: Float,
    seconds: Double = 8.0,
    hz: Int = 100,
    dcIr: Float = 50_000f,
    dcRed: Float = 30_000f,
    pi: Float = 0.02f,
    bpm: Double = 72.0,
    noise: Float = 0f,
    drift: Float = 0f,
    redBpm: Double = bpm,
    startNanos: Long = 0L
): List<PpgSample> {
    val random = Random(42)
    val count = (seconds * hz).toInt()
    return List(count) { i ->
        val t = i.toDouble() / hz
        val pulseIr = sin(2 * PI * bpm / 60 * t).toFloat()
        val pulseRed = sin(2 * PI * redBpm / 60 * t).toFloat()
        val trend = 1f + drift * (t / seconds).toFloat()
        val ir = dcIr * trend * (1f + pi * pulseIr) + noise * dcIr * pi * random.nextFloat().minus(0.5f) * 2
        val red = dcRed * trend * (1f + ratio * pi * pulseRed) + noise * dcRed * ratio * pi * random.nextFloat().minus(0.5f) * 2
        PpgSample(startNanos + (t * S).toLong(), ir, red)
    }
}

class Spo2CalculatorTest {
    @Test
    fun ratioOfHalfGivesAbout97Percent() {
        val estimate = Spo2Calculator.estimate(syntheticPpg(0.5f))!!
        assertEquals(0.5f, estimate.ratio, 0.01f)
        assertEquals(97.5f, estimate.spo2, 0.3f)
        assertEquals(0.02f * 0.707f, estimate.perfusionIndex, 0.003f)
    }

    @Test
    fun ratioOfOneGives85Percent() {
        assertEquals(85f, Spo2Calculator.estimate(syntheticPpg(1.0f))!!.spo2, 0.3f)
    }

    @Test
    fun resultIsClampedToPlausibleRange() {
        assertEquals(70f, Spo2Calculator.estimate(syntheticPpg(2.0f))!!.spo2, 0f)
        assertEquals(100f, Spo2Calculator.estimate(syntheticPpg(0.2f))!!.spo2, 0f)
    }

    @Test
    fun worksForSlowAndFastHeartRates() {
        listOf(45.0, 60.0, 90.0, 120.0, 170.0).forEach { bpm ->
            val estimate = Spo2Calculator.estimate(syntheticPpg(0.6f, bpm = bpm))
            assertNotNull("bpm $bpm", estimate)
            assertEquals("bpm $bpm", 95f, estimate!!.spo2, 0.5f)
        }
    }

    @Test
    fun toleratesNoiseAndBaselineDrift() {
        assertEquals(97.5f, Spo2Calculator.estimate(syntheticPpg(0.5f, noise = 0.2f))!!.spo2, 1f)
        assertEquals(97.5f, Spo2Calculator.estimate(syntheticPpg(0.5f, drift = 0.1f))!!.spo2, 1f)
    }

    @Test
    fun independentOfAbsoluteLightLevels() {
        val a = Spo2Calculator.estimate(syntheticPpg(0.7f, dcIr = 2_000f, dcRed = 900f))!!.spo2
        val b = Spo2Calculator.estimate(syntheticPpg(0.7f, dcIr = 400_000f, dcRed = 150_000f))!!.spo2
        assertEquals(a, b, 0.1f)
    }

    @Test
    fun rejectsSignalsWithoutAPulse() {
        assertNull(Spo2Calculator.estimate(syntheticPpg(0.5f, pi = 0f)))
        assertNull("too weak", Spo2Calculator.estimate(syntheticPpg(0.5f, pi = 0.0001f)))
        assertNull("too strong (movement)", Spo2Calculator.estimate(syntheticPpg(0.5f, pi = 0.5f)))
    }

    @Test
    fun rejectsWhenNoFingerCoversTheSensor() {
        assertNull(Spo2Calculator.estimate(syntheticPpg(0.5f, dcIr = 0.5f)))
        assertNull(Spo2Calculator.estimate(syntheticPpg(0.5f, dcIr = 500f), minIrDc = 1_000f))
    }

    @Test
    fun rejectsWhenChannelsDisagree() {
        assertNull(Spo2Calculator.estimate(syntheticPpg(0.5f, bpm = 72.0, redBpm = 160.0)))
    }

    @Test
    fun rejectsTooLittleData() {
        assertNull(Spo2Calculator.estimate(emptyList()))
        assertNull("shorter than 4 s", Spo2Calculator.estimate(syntheticPpg(0.5f, seconds = 3.5)))
        assertNull("below 20 Hz", Spo2Calculator.estimate(syntheticPpg(0.5f, hz = 10)))
        assertNotNull(Spo2Calculator.estimate(syntheticPpg(0.5f, seconds = 4.5, hz = 25)))
    }

    @Test
    fun rejectsJumpsAndMissingLight() {
        val clean = syntheticPpg(0.5f)
        assertNotNull(Spo2Calculator.estimate(clean))
        val jump = clean.mapIndexed { i, s -> if (i > 400) s.copy(ir = s.ir * 1.1f, red = s.red * 1.1f) else s }
        assertNull("finger moved", Spo2Calculator.estimate(jump))
        val gap = clean.mapIndexed { i, s -> if (i == 300) s.copy(ir = 0f) else s }
        assertNull("LED dropped out", Spo2Calculator.estimate(gap))
    }

    @Test
    fun highPassRemovesConstantAndLinearTrend() {
        val times = LongArray(300) { it * S / 100 }
        val values = FloatArray(300) { 1000f + it * 2f }
        val out = highPass(times, values)
        for (i in 60 until 240) assertEquals(0f, out[i], 0.01f)
    }

    @Test
    fun correlationOfIdenticalAndOppositeSignals() {
        val a = FloatArray(100) { sin(it / 5.0).toFloat() }
        assertEquals(1f, Spo2Calculator.correlation(a, a), 1e-4f)
        assertEquals(-1f, Spo2Calculator.correlation(a, FloatArray(100) { -a[it] }), 1e-4f)
        assertEquals(0f, Spo2Calculator.correlation(a, FloatArray(100)), 0f)
    }

    @Test
    fun waveformIsInvertedAndDecimated() {
        val samples = syntheticPpg(0.5f, seconds = 8.0, hz = 100)
        val wave = waveform(samples, points = 150)
        assertEquals(150, wave.size)
        // Absorption rises with blood volume, so the trace is the negative IR pulse.
        val last = samples.last()
        val ac = highPass(LongArray(samples.size) { samples[it].timestampNanos }, FloatArray(samples.size) { samples[it].ir })
        assertEquals(-ac.last(), waveform(samples, points = 10_000).last(), 1e-3f)
        assertTrue(last.timestampNanos > 0)
        assertTrue(waveform(samples.take(2)).isEmpty())
    }

    @Test
    fun signalDescriptionShowsRateAndRatio() {
        val samples = syntheticPpg(0.5f)
        assertEquals("", describeSignal(samples.take(1), null))
        assertTrue(describeSignal(samples, null).startsWith("800 eșantioane • 100 Hz"))
        assertTrue(describeSignal(samples, Spo2Calculator.estimate(samples)).contains("R = 0.50"))
    }
}

class PpgBufferTest {
    @Test
    fun interpolatesRedAtInfraredTimestamps() {
        val ir = listOf(LightSample(5, 100f), LightSample(15, 101f))
        val red = listOf(LightSample(0, 10f), LightSample(10, 20f), LightSample(20, 40f))
        assertEquals(listOf(PpgSample(5, 100f, 15f), PpgSample(15, 101f, 30f)), pairSamples(ir, red))
    }

    @Test
    fun dropsInfraredOutsideTheRedRange() {
        val ir = listOf(LightSample(0, 1f), LightSample(10, 2f), LightSample(30, 3f))
        val red = listOf(LightSample(5, 10f), LightSample(20, 20f))
        assertEquals(listOf(10L), pairSamples(ir, red).map { it.timestampNanos })
    }

    @Test
    fun separateSensorsAtDifferentRatesStillGiveTheRightValue() {
        val reference = syntheticPpg(0.5f, hz = 400)
        val buffer = PpgBuffer()
        reference.forEachIndexed { i, s ->
            if (i % 4 == 0) buffer.addIr(LightSample(s.timestampNanos, s.ir))       // 100 Hz
            if (i % 8 == 3) buffer.addRed(LightSample(s.timestampNanos, s.red))     // 50 Hz, offset 7.5 ms
        }
        assertEquals(97.5f, Spo2Calculator.estimate(buffer.paired())!!.spo2, 0.5f)
    }

    @Test
    fun combinedSensorKeepsOnlyTheLastWindow() {
        val buffer = PpgBuffer(windowNanos = 8 * S)
        syntheticPpg(0.5f, seconds = 20.0).forEach { buffer.addBoth(it.timestampNanos, it.ir, it.red) }
        val paired = buffer.paired()
        val span = paired.last().timestampNanos - paired.first().timestampNanos
        assertTrue(span <= 8 * S)
        assertTrue(span >= 8 * S - S / 50)
    }

    @Test
    fun skipsZerosAndTheLedWarmUp() {
        val buffer = PpgBuffer(warmUpNanos = S)
        buffer.addBoth(0, 0f, 0f)                    // LEDs off: ignored, does not start the warm-up
        buffer.addBoth(100_000_000, 5926f, 5404f)    // warm-up starts here
        buffer.addBoth(1_050_000_000, 21000f, 21100f)
        buffer.addBoth(1_100_000_000, 21001f, 21101f)
        buffer.addBoth(1_150_000_000, 21002f, 21102f)
        assertEquals(listOf(1_100_000_000L, 1_150_000_000L), buffer.paired().map { it.timestampNanos })
        buffer.clear()
        buffer.addBoth(5 * S, 21000f, 21100f)
        assertTrue("warm-up restarts after clear", buffer.paired().isEmpty())
    }

    @Test
    fun ignoresInvalidAndOutOfOrderSamples() {
        val buffer = PpgBuffer(warmUpNanos = 0)
        buffer.addBoth(10, 1f, 2f)
        buffer.addBoth(20, Float.NaN, 3f)
        buffer.addBoth(5, 4f, 5f)
        buffer.addBoth(30, 6f, 7f)
        assertEquals(listOf(10L, 30L), buffer.paired().map { it.timestampNanos })
        buffer.clear()
        assertTrue(buffer.paired().isEmpty())
    }
}

class MeasurementSessionTest {
    private fun MeasurementSession.feed(fromSeconds: Double, toSeconds: Double, value: Float?): MeasurementState {
        var state: MeasurementState = MeasurementState.WaitingForFinger
        var t = fromSeconds
        while (t <= toSeconds + 1e-9) { state = update((t * S).toLong(), value); t += 0.5 }
        return state
    }

    @Test
    fun finishesAfterFifteenSecondsOfGoodSignalWithTheMedian() {
        val session = MeasurementSession()
        assertEquals(MeasurementState.WaitingForFinger, session.feed(0.0, 2.0, null))
        val halfway = session.update((10 * S), 96f) as MeasurementState.Measuring
        assertEquals(0f, halfway.progress, 0f)
        session.update(10 * S + 7_500_000_000L, 97f).let { assertEquals(0.5f, (it as MeasurementState.Measuring).progress, 0.01f) }
        session.update(12 * S, 99.6f)
        session.update(14 * S, 97.2f)
        assertEquals(MeasurementState.Done(97), session.update(25 * S, 90f))
    }

    @Test
    fun liftingTheFingerRestartsTheCount() {
        val session = MeasurementSession()
        session.feed(0.0, 10.0, 97f)
        assertEquals(MeasurementState.WaitingForFinger, session.update(10_500_000_000L, null))
        assertTrue(session.signalLost)
        val restarted = session.update(11 * S, 95f) as MeasurementState.Measuring
        assertEquals(0f, restarted.progress, 0f)
        assertTrue(!session.signalLost)
        assertEquals(MeasurementState.Done(95), session.feed(11.5, 26.0, 95f))
    }

    @Test
    fun timesOutAfter45SecondsWithoutSignal() {
        val session = MeasurementSession()
        assertEquals(MeasurementState.WaitingForFinger, session.feed(0.0, 44.5, null))
        assertEquals(MeasurementState.TimedOut, session.update(45 * S, null))
        assertEquals(MeasurementState.TimedOut, session.update(46 * S, 97f))
    }

    @Test
    fun timeoutCountsOnlyContinuousLossOfSignal() {
        val session = MeasurementSession()
        session.feed(0.0, 30.0, null)
        session.update(30 * S, 97f)
        assertEquals(MeasurementState.WaitingForFinger, session.feed(30.5, 70.0, null))
        assertEquals(MeasurementState.TimedOut, session.update(76 * S, null))
    }

    @Test
    fun resultStaysUntilReset() {
        val session = MeasurementSession()
        session.feed(0.0, 15.0, 98f)
        assertEquals(MeasurementState.Done(98), session.update(16 * S, null))
        session.reset()
        assertEquals(MeasurementState.WaitingForFinger, session.state)
        assertTrue(session.update(17 * S, 94f) is MeasurementState.Measuring)
    }

    @Test
    fun medianOfOddAndEvenLists() {
        assertEquals(3f, median(listOf(5f, 1f, 3f)), 0f)
        assertEquals(2.5f, median(listOf(4f, 1f, 2f, 3f)), 0f)
    }
}

/** Raw recording from the user's Galaxy S10 (MAX86915 IR/RED, ~100 Hz) with nothing on the sensor. */
class RealS10RecordingTest {
    private val recording: List<PpgSample> by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("ppg/s10_no_finger.csv")
        stream.bufferedReader().readLines().drop(1).map { line ->
            val (t, ir, red) = line.split(",")
            PpgSample(t.toLong(), ir.toFloat(), red.toFloat())
        }
    }

    @Test
    fun recordingLooksLikeTheDevice() {
        assertTrue(recording.size > 1400)
        assertEquals(0f, recording.first().ir, 0f)
        assertTrue(recording.last().ir in 20_000f..22_000f)
    }

    @Test
    fun noReadingWhenNothingIsOnTheSensor() {
        // The full recording includes the LED start-up jump that originally produced a false 84 %.
        assertNull(Spo2Calculator.estimate(recording))
        assertNull(Spo2Calculator.estimate(recording.filter { it.ir > 0f && it.red > 0f }))
    }

    @Test
    fun appLoopNeverProducesAValueOrResult() {
        // Replays the recording exactly as OxygenMeasurementScreen does: feed the buffer, estimate every 0.5 s.
        val buffer = PpgBuffer()
        val session = MeasurementSession()
        var nextTick = recording.first().timestampNanos + S / 2
        recording.forEach { sample ->
            buffer.addBoth(sample.timestampNanos, sample.ir, sample.red)
            if (sample.timestampNanos >= nextTick) {
                val estimate = Spo2Calculator.estimate(buffer.paired())
                assertNull("at ${(sample.timestampNanos - recording.first().timestampNanos) / 1e9} s", estimate)
                assertEquals(MeasurementState.WaitingForFinger, session.update(sample.timestampNanos, estimate?.spo2))
                nextTick += S / 2
            }
        }
    }

    @Test
    fun aPulseAddedToTheRealBaselineIsMeasured() {
        // Real S10 noise and drift plus a synthetic heartbeat (2 % IR, R = 0.5) must still give ~97.5 %.
        val start = recording.first().timestampNanos
        val withPulse = recording.filter { it.timestampNanos - start > 2 * S }.map {
            val beat = sin(2 * PI * 1.2 * (it.timestampNanos - start) / 1e9).toFloat()
            PpgSample(it.timestampNanos, it.ir * (1 + 0.02f * beat), it.red * (1 + 0.01f * beat))
        }
        val buffer = PpgBuffer(warmUpNanos = 0)
        withPulse.forEach { buffer.addBoth(it.timestampNanos, it.ir, it.red) }
        assertEquals(97.5f, Spo2Calculator.estimate(buffer.paired())!!.spo2, 0.5f)
    }
}
