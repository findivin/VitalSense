package com.example.chs

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sin
import kotlin.random.Random

data class SampleProfile(val name: String, val age: Int, val description: String, val restingPulse: Int, val restingSpo2: Int)

val sampleProfiles = listOf(
    SampleProfile("Ion Popescu", 65, "Persoană cu afecțiune cardiacă", 78, 94),
    SampleProfile("Andrei Ionescu", 30, "Alergător", 62, 98),
    SampleProfile("Mihai Dumitrescu", 40, "Persoană sedentară", 82, 96),
    SampleProfile("Alexandru Radu", 25, "Sportiv de performanță", 54, 98),
    SampleProfile("Maria Stan", 43, "Mamă", 73, 97)
)

fun sampleReadings(profile: SampleProfile, today: LocalDate, zone: ZoneId): List<Reading> {
    val random = Random(profile.name.hashCode())
    return sampleDays(today).mapIndexed { index, day ->
        val variation = random.nextInt(-5, 6) + (2 * sin(index / 15.0)).toInt()
        Reading(day.atStartOfDay(zone).toInstant(), profile.restingPulse + variation)
    }
}

fun sampleOxygenReadings(profile: SampleProfile, today: LocalDate, zone: ZoneId): List<Reading> {
    val random = Random(profile.name.hashCode() * 31 + 7)
    return sampleDays(today).map { day ->
        val value = (profile.restingSpo2 + random.nextInt(-1, 2)).coerceAtMost(100)
        Reading(day.atStartOfDay(zone).plusHours(9).toInstant(), value)
    }
}

private fun sampleDays(today: LocalDate): List<LocalDate> {
    val days = mutableListOf<LocalDate>()
    var day = today.minusYears(1).plusDays(1)
    while (!day.isAfter(today)) {
        days.add(day)
        day = day.plusDays(1)
    }
    return days
}
