package com.example.chs

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class Period(val title: String, val count: Int) {
    DAYS("Zile", 7), WEEKS("Săptămâni", 8), MONTHS("Luni", 12)
}

data class PulseBucket(val start: LocalDate, val end: LocalDate, val average: Float?)

fun bucketsFor(period: Period, offset: Int, today: LocalDate, readings: List<Reading>, zone: ZoneId): List<PulseBucket> {
    val end = when (period) {
        Period.DAYS -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusWeeks(offset.toLong())
        Period.WEEKS -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusWeeks(offset.toLong() * period.count)
        Period.MONTHS -> today.withDayOfMonth(1).plusMonths(offset.toLong() * period.count + 1).minusDays(1)
    }
    return (0 until period.count).map { index ->
        val remaining = period.count - 1L - index
        val bucketEnd = when (period) {
            Period.DAYS -> end.minusDays(remaining)
            Period.WEEKS -> end.minusWeeks(remaining)
            Period.MONTHS -> end.withDayOfMonth(1).minusMonths(remaining).plusMonths(1).minusDays(1)
        }
        val start = when (period) {
            Period.DAYS -> bucketEnd
            Period.WEEKS -> bucketEnd.minusDays(6)
            Period.MONTHS -> bucketEnd.withDayOfMonth(1)
        }
        val values = readings.filter {
            val day = it.time.atZone(zone).toLocalDate()
            !day.isBefore(start) && !day.isAfter(bucketEnd)
        }
        PulseBucket(start, bucketEnd, values.takeIf { it.isNotEmpty() }?.map { it.value }?.average()?.toFloat())
    }
}
