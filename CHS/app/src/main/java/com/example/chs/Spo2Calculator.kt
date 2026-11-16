package com.example.chs

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One raw light sample. */
data class LightSample(val timestampNanos: Long, val value: Float)

/** Red and infrared samples at the same instant. */
data class PpgSample(val timestampNanos: Long, val ir: Float, val red: Float)

data class Spo2Estimate(val spo2: Float, val ratio: Float, val perfusionIndex: Float)

private const val SECOND = 1_000_000_000L

/**
 * Keeps the last [windowNanos] of raw light and turns it into paired red/IR samples.
 * Separate IR and RED sensors deliver events independently, so red is linearly
 * interpolated at each IR timestamp.
 */
class PpgBuffer(private val windowNanos: Long = 8 * SECOND, private val warmUpNanos: Long = SECOND) {
    private val ir = ArrayDeque<LightSample>()
    private val red = ArrayDeque<LightSample>()
    private var firstTimestamp: Long? = null

    fun addIr(sample: LightSample) = add(ir, sample)
    fun addRed(sample: LightSample) = add(red, sample)
    fun addBoth(timestampNanos: Long, irValue: Float, redValue: Float) {
        addIr(LightSample(timestampNanos, irValue))
        addRed(LightSample(timestampNanos, redValue))
    }

    fun clear() { ir.clear(); red.clear(); firstTimestamp = null }

    private fun add(queue: ArrayDeque<LightSample>, sample: LightSample) {
        // The S10 reports zeros and then a short plateau while the LEDs start (measured: ~220 ms).
        if (!sample.value.isFinite() || sample.value <= 0f) return
        val first = firstTimestamp ?: sample.timestampNanos.also { firstTimestamp = it }
        if (sample.timestampNanos - first < warmUpNanos) return
        if (queue.isNotEmpty() && sample.timestampNanos < queue.last().timestampNanos) return
        queue.addLast(sample)
        while (queue.isNotEmpty() && queue.first().timestampNanos < sample.timestampNanos - windowNanos) queue.removeFirst()
    }

    fun paired(): List<PpgSample> = pairSamples(ir.toList(), red.toList())
}

fun pairSamples(ir: List<LightSample>, red: List<LightSample>): List<PpgSample> {
    if (ir.isEmpty() || red.size < 2) {
        return ir.mapNotNull { i -> red.firstOrNull { it.timestampNanos == i.timestampNanos }?.let { PpgSample(i.timestampNanos, i.value, it.value) } }
    }
    val result = ArrayList<PpgSample>(ir.size)
    var j = 0
    for (sample in ir) {
        val t = sample.timestampNanos
        if (t < red.first().timestampNanos || t > red.last().timestampNanos) continue
        while (j < red.size - 2 && red[j + 1].timestampNanos < t) j++
        val a = red[j]
        val b = red[j + 1]
        val span = (b.timestampNanos - a.timestampNanos).toFloat()
        val value = if (span <= 0f) a.value else a.value + (b.value - a.value) * ((t - a.timestampNanos) / span)
        result.add(PpgSample(t, sample.value, value))
    }
    return result
}

/** Removes the slowly changing part (DC + drift) with a centred moving average about [windowNanos] long. */
fun highPass(times: LongArray, values: FloatArray, windowNanos: Long = SECOND): FloatArray {
    val n = values.size
    val prefix = DoubleArray(n + 1)
    for (i in 0 until n) prefix[i + 1] = prefix[i] + values[i]
    val out = FloatArray(n)
    var lo = 0
    var hi = 0
    for (i in 0 until n) {
        while (times[i] - times[lo] > windowNanos / 2) lo++
        while (hi + 1 < n && times[hi + 1] - times[i] <= windowNanos / 2) hi++
        val mean = (prefix[hi + 1] - prefix[lo]) / (hi - lo + 1)
        out[i] = (values[i] - mean).toFloat()
    }
    return out
}

/**
 * Ratio-of-ratios pulse oximetry: R = (AC_red / DC_red) / (AC_ir / DC_ir), SpO₂ ≈ 110 − 25·R.
 * The coefficients are the common textbook approximation and are NOT calibrated for this phone.
 */
object Spo2Calculator {
    const val MIN_DURATION_NANOS = 4 * SECOND
    const val MIN_RATE_HZ = 20.0
    const val MIN_PERFUSION = 0.0005f
    const val MAX_PERFUSION = 0.2f
    const val MIN_CORRELATION = 0.6f
    /** Largest change allowed between two consecutive samples, relative to DC. Heartbeats are smooth; jumps are movement or LED start-up. */
    const val MAX_STEP = 0.05f

    fun estimate(samples: List<PpgSample>, minIrDc: Float = 1f): Spo2Estimate? {
        if (samples.size < 2) return null
        val duration = samples.last().timestampNanos - samples.first().timestampNanos
        if (duration < MIN_DURATION_NANOS) return null
        if ((samples.size - 1) / (duration.toDouble() / SECOND) < MIN_RATE_HZ) return null

        val times = LongArray(samples.size) { samples[it].timestampNanos }
        val ir = FloatArray(samples.size) { samples[it].ir }
        val red = FloatArray(samples.size) { samples[it].red }
        if (ir.any { it <= 0f } || red.any { it <= 0f }) return null
        val dcIr = ir.average().toFloat()
        val dcRed = red.average().toFloat()
        if (dcIr < minIrDc) return null
        if (maxStep(ir) > MAX_STEP * dcIr || maxStep(red) > MAX_STEP * dcRed) return null

        // Ignore the edges, where the moving average window is cut short.
        val acIr = highPass(times, ir)
        val acRed = highPass(times, red)
        val from = samples.indexOfFirst { it.timestampNanos - times.first() >= SECOND / 2 }
        val to = samples.indexOfLast { times.last() - it.timestampNanos >= SECOND / 2 }
        if (from < 0 || to <= from) return null
        val irCore = acIr.copyOfRange(from, to + 1)
        val redCore = acRed.copyOfRange(from, to + 1)

        val rmsIr = rms(irCore)
        val rmsRed = rms(redCore)
        if (rmsIr <= 0f || rmsRed <= 0f) return null
        val perfusion = rmsIr / dcIr
        if (perfusion !in MIN_PERFUSION..MAX_PERFUSION) return null
        // Both channels must see the same heartbeat; otherwise it is motion or ambient light.
        if (correlation(irCore, redCore) < MIN_CORRELATION) return null

        val ratio = (rmsRed / dcRed) / (rmsIr / dcIr)
        if (!ratio.isFinite()) return null
        val spo2 = (110f - 25f * ratio).coerceIn(70f, 100f)
        return Spo2Estimate(spo2, ratio, perfusion)
    }

    private fun maxStep(values: FloatArray): Float {
        var max = 0f
        for (i in 1 until values.size) max = maxOf(max, kotlin.math.abs(values[i] - values[i - 1]))
        return max
    }

    private fun rms(values: FloatArray): Float {
        var sum = 0.0
        for (v in values) sum += v.toDouble() * v
        return sqrt(sum / values.size).toFloat()
    }

    fun correlation(a: FloatArray, b: FloatArray): Float {
        val ma = a.average()
        val mb = b.average()
        var num = 0.0
        var da = 0.0
        var db = 0.0
        for (i in a.indices) {
            val x = a[i] - ma
            val y = b[i] - mb
            num += x * y; da += x * x; db += y * y
        }
        if (da == 0.0 || db == 0.0) return 0f
        return (num / sqrt(da * db)).toFloat()
    }
}

sealed interface MeasurementState {
    data object WaitingForFinger : MeasurementState
    data class Measuring(val progress: Float, val current: Int) : MeasurementState
    data class Done(val value: Int) : MeasurementState
    data object TimedOut : MeasurementState
}

/**
 * Measurement flow like Samsung Health: wait for a good signal, collect [requiredNanos]
 * of continuous valid estimates, report their median. Losing the signal restarts the count.
 */
class MeasurementSession(
    private val requiredNanos: Long = 15 * SECOND,
    private val timeoutNanos: Long = 45 * SECOND
) {
    var state: MeasurementState = MeasurementState.WaitingForFinger
        private set
    var signalLost = false
        private set
    private var noSignalSince: Long? = null
    private var validSince: Long? = null
    private val estimates = mutableListOf<Float>()

    fun update(nowNanos: Long, spo2: Float?): MeasurementState {
        if (state is MeasurementState.Done || state is MeasurementState.TimedOut) return state
        if (spo2 == null) {
            if (validSince != null) signalLost = true
            validSince = null
            estimates.clear()
            val since = noSignalSince ?: nowNanos.also { noSignalSince = it }
            state = if (nowNanos - since >= timeoutNanos) MeasurementState.TimedOut else MeasurementState.WaitingForFinger
            return state
        }
        signalLost = false
        noSignalSince = null
        val since = validSince ?: nowNanos.also { validSince = it }
        estimates.add(spo2)
        val progress = ((nowNanos - since).toFloat() / requiredNanos).coerceIn(0f, 1f)
        state = if (progress >= 1f) MeasurementState.Done(median(estimates).roundToInt()) else MeasurementState.Measuring(progress, spo2.roundToInt())
        return state
    }

    fun reset() {
        state = MeasurementState.WaitingForFinger
        signalLost = false
        noSignalSince = null
        validSince = null
        estimates.clear()
    }
}

fun median(values: List<Float>): Float {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
}

/** Display trace: the pulsatile part of the IR light, inverted so a heartbeat points up, decimated to [points]. */
fun waveform(samples: List<PpgSample>, lastNanos: Long = 5 * SECOND, points: Int = 150): List<Float> {
    if (samples.size < 3) return emptyList()
    val end = samples.last().timestampNanos
    val times = LongArray(samples.size) { samples[it].timestampNanos }
    val ac = highPass(times, FloatArray(samples.size) { samples[it].ir })
    val recent = samples.indices.filter { end - times[it] <= lastNanos }.map { -ac[it] }
    if (recent.size <= points) return recent
    return List(points) { recent[it * recent.size / points] }
}
