package com.example.chs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SampleProfilesTest {
    private val zone = ZoneId.of("Europe/Bucharest")
    private val today = LocalDate.of(2024, 12, 31)

    @Test
    fun samplesCoverEveryDayOfPreviousCalendarYear() {
        sampleProfiles.forEach { profile ->
            listOf(sampleReadings(profile, today, zone), sampleOxygenReadings(profile, today, zone)).forEach { readings ->
                val days = readings.map { it.time.atZone(zone).toLocalDate() }
                assertEquals(366, days.size)
                assertEquals(today.minusYears(1).plusDays(1), days.first())
                assertEquals(today, days.last())
                assertEquals(days.size, days.toSet().size)
                assertTrue(readings.map { it.value }.toSet().size > 1)
            }
        }
    }

    @Test
    fun oxygenSamplesStayRealisticAndCloseToTheProfileBaseline() {
        sampleProfiles.forEach { profile ->
            val values = sampleOxygenReadings(profile, today, zone).map { it.value }
            assertTrue(values.all { it in 90..100 })
            assertTrue(values.all { it in profile.restingSpo2 - 1..profile.restingSpo2 + 1 })
            assertEquals(profile.restingSpo2.toDouble(), values.average(), 0.5)
        }
        assertEquals(94, sampleProfiles.first { it.description.contains("cardiacă") }.restingSpo2)
    }

    @Test
    fun samplesAreDeterministic() {
        val profile = sampleProfiles.first()
        assertEquals(sampleOxygenReadings(profile, today, zone), sampleOxygenReadings(profile, today, zone))
        assertEquals(sampleReadings(profile, today, zone), sampleReadings(profile, today, zone))
    }
}
