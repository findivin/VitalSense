package com.example.chs

import org.junit.Assert.assertEquals
import org.junit.Test

class LivePulseChartTest {
    @Test
    fun keepsOnlyRecentSensorSamples() {
        val samples = listOf(LivePulseSample(0, 70), LivePulseSample(15_000_000_000L, 72))
        val result = addLiveSample(samples, LivePulseSample(31_000_000_000L, 74))
        assertEquals(listOf(72, 74), result.map { it.bpm })
    }

    @Test
    fun limitsTraceSize() {
        val samples = (1..100).map { LivePulseSample(it.toLong(), 70) }
        val result = addLiveSample(samples, LivePulseSample(101, 71))
        assertEquals(100, result.size)
        assertEquals(2L, result.first().timestampNanos)
        assertEquals(71, result.last().bpm)
    }
}

class PulseCategoryTest {
    @Test
    fun usesTheAdultRestingRange() {
        assertEquals("Sub intervalul obișnuit în repaus (60–100 bpm)", pulseCategory(59))
        assertEquals("În intervalul obișnuit în repaus (60–100 bpm)", pulseCategory(60))
        assertEquals("În intervalul obișnuit în repaus (60–100 bpm)", pulseCategory(100))
        assertEquals("Peste intervalul obișnuit în repaus (60–100 bpm)", pulseCategory(101))
    }
}

class PulseProgressTest {
    @Test
    fun detectingCreepsTowardsThirtyPercentThenMeasuringFillsTheRest() {
        assertEquals(0f, pulseProgress(null, null), 0f)
        val early = pulseProgress(1_000_000_000L, null)
        val late = pulseProgress(20_000_000_000L, null)
        org.junit.Assert.assertTrue(early > 0f && early < late && late < 0.3f)
        assertEquals(0.3f, pulseProgress(null, 0f), 1e-6f)
        assertEquals(0.65f, pulseProgress(null, 0.5f), 1e-6f)
        assertEquals(1f, pulseProgress(5_000_000_000L, 1f), 1e-6f)
    }
}
