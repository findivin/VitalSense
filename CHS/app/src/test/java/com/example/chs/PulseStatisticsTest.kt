package com.example.chs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PulseStatisticsTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 14)

    @Test
    fun daysGroupReadingsAndKeepEmptyDaysEmpty() {
        val readings = listOf(
            Reading(Instant.parse("2026-11-10T09:00:00Z"), 80),
            Reading(Instant.parse("2026-11-10T10:00:00Z"), 100),
            Reading(Instant.parse("2026-11-15T10:00:00Z"), 120)
        )
        val buckets = bucketsFor(Period.DAYS, 0, today, readings, zone)
        assertEquals(LocalDate.of(2026, 11, 9), buckets.first().start)
        assertEquals(java.time.DayOfWeek.MONDAY, buckets.first().start.dayOfWeek)
        assertEquals(90f, buckets[1].average!!, 0f)
        assertNull(buckets[2].average)
        assertEquals(120f, buckets[6].average!!, 0f)
    }

    @Test
    fun weeksAndMonthsNavigateAcrossYearBoundaries() {
        val weeks = bucketsFor(Period.WEEKS, -1, today, emptyList(), zone)
        assertEquals(LocalDate.of(2026, 9, 20), weeks.last().end)
        val months = bucketsFor(Period.MONTHS, 0, today, emptyList(), zone)
        assertEquals(LocalDate.of(2025, 12, 1), months.first().start)
        assertEquals(LocalDate.of(2026, 11, 30), months.last().end)
    }
}

class ChartScaleTest {
    @Test
    fun pulseScaleStartsAtZeroAndGrowsInStepsOf50() {
        assertEquals(ChartScale(0, 200), chartScale(Metric.PULSE, emptyList()))
        assertEquals(ChartScale(0, 200), chartScale(Metric.PULSE, listOf(70f, 190f)))
        assertEquals(ChartScale(0, 250), chartScale(Metric.PULSE, listOf(210f)))
        assertEquals(50, ChartScale(0, 200).step)
    }

    @Test
    fun oxygenScaleIsZoomedOnTheUsefulRange() {
        assertEquals(ChartScale(80, 100), chartScale(Metric.OXYGEN, emptyList()))
        assertEquals(ChartScale(80, 100), chartScale(Metric.OXYGEN, listOf(94f, 100f)))
        assertEquals(ChartScale(60, 100), chartScale(Metric.OXYGEN, listOf(75f, 98f)))
        assertEquals(5, ChartScale(80, 100).step)
    }

    @Test
    fun fractionMapsAndClamps() {
        val scale = ChartScale(80, 100)
        assertEquals(0.85f, scale.fraction(97f), 1e-5f)
        assertEquals(0f, scale.fraction(70f), 0f)
        assertEquals(1f, scale.fraction(101f), 0f)
    }
}
