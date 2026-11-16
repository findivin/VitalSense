package com.example.chs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class PulseStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "pulse_store_migration_test.db"
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val start = today.minusYears(1).plusDays(1)
    private val days = ChronoUnit.DAYS.between(start, today).toInt() + 1

    @Before
    fun clean() { context.deleteDatabase(name) }

    @After
    fun cleanup() { context.deleteDatabase(name) }

    private fun createVersion1() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE readings (id INTEGER PRIMARY KEY, time INTEGER NOT NULL, bpm INTEGER NOT NULL)")
            db.execSQL("INSERT INTO readings (time, bpm) VALUES (123456, 75)")
            db.version = 1
        }
    }

    private fun assertSamplesCoverLastYear(store: PulseStore, metric: Metric, profile: Profile) {
        val dates = store.allReadings(metric, profile.id).map { it.time.atZone(zone).toLocalDate() }.toSet()
        assertEquals("${metric.name} ${profile.name}", start, dates.minOrNull())
        assertEquals(today, dates.maxOrNull())
        assertEquals(days, dates.size)
    }

    @Test
    fun upgradeFromVersion1PreservesReadingsAndSeedsBothMetrics() {
        createVersion1()
        PulseStore(context, name).use { store ->
            val profiles = store.profiles()
            assertEquals(6, profiles.size)
            assertEquals(75, store.allReadings(Metric.PULSE, 1).single().value)
            assertTrue(store.allReadings(Metric.OXYGEN, 1).isEmpty())
            profiles.drop(1).forEach { profile ->
                assertTrue(profile.isSample)
                assertSamplesCoverLastYear(store, Metric.PULSE, profile)
                assertSamplesCoverLastYear(store, Metric.OXYGEN, profile)
            }
        }
    }

    @Test
    fun upgradeFromVersion2AddsOxygenWithoutTouchingPulse() {
        createVersion1()
        // Build a real version-2 database with the old schema by stopping the upgrade at v2.
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("CREATE TABLE profiles (id INTEGER PRIMARY KEY, name TEXT NOT NULL, age INTEGER, description TEXT NOT NULL, is_sample INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("INSERT INTO profiles (id, name, description) VALUES (1, 'Profil personal', '')")
            db.execSQL("ALTER TABLE readings ADD COLUMN profile_id INTEGER NOT NULL DEFAULT 1")
            db.execSQL("INSERT INTO profiles (id, name, age, description, is_sample) VALUES (2, 'Andrei Ionescu', 30, 'Alergător', 1)")
            db.execSQL("INSERT INTO profiles (id, name, description) VALUES (3, 'Vecin', '')")
            db.execSQL("INSERT INTO readings (time, bpm, profile_id) VALUES (222, 61, 2)")
            db.version = 2
        }
        PulseStore(context, name).use { store ->
            assertEquals(3, store.profiles().size)
            assertEquals(75, store.allReadings(Metric.PULSE, 1).single().value)
            assertEquals(61, store.allReadings(Metric.PULSE, 2).single().value)
            assertSamplesCoverLastYear(store, Metric.OXYGEN, store.profiles()[1])
            assertTrue(store.allReadings(Metric.OXYGEN, 2).all { it.value in 97..99 })
            assertTrue(store.allReadings(Metric.OXYGEN, 1).isEmpty())
            assertTrue(store.allReadings(Metric.OXYGEN, 3).isEmpty())
        }
    }

    @Test
    fun freshInstallSeedsSamplesForBothMetrics() {
        PulseStore(context, name).use { store ->
            val profiles = store.profiles()
            assertEquals(6, profiles.size)
            assertTrue(store.allReadings(Metric.PULSE, 1).isEmpty())
            assertTrue(store.allReadings(Metric.OXYGEN, 1).isEmpty())
            profiles.drop(1).forEach {
                assertSamplesCoverLastYear(store, Metric.PULSE, it)
                assertSamplesCoverLastYear(store, Metric.OXYGEN, it)
            }
        }
    }

    @Test
    fun readingsAreSeparatedByMetricAndProfileAndSurviveReopening() {
        val id = PulseStore(context, name).use { store ->
            val id = store.addProfile("Test", 35, "")
            store.save(Metric.PULSE, id, Reading(Instant.ofEpochMilli(1_000), 80))
            store.save(Metric.OXYGEN, id, Reading(Instant.ofEpochMilli(2_000), 96))
            store.save(Metric.OXYGEN, id, Reading(Instant.ofEpochMilli(3_000), 98))
            id
        }
        PulseStore(context, name).use { store ->
            assertEquals(7, store.profiles().size)
            assertEquals(listOf(80), store.allReadings(Metric.PULSE, id).map { it.value })
            assertEquals(listOf(98, 96), store.allReadings(Metric.OXYGEN, id).map { it.value })
            assertTrue(store.allReadings(Metric.OXYGEN, 1).isEmpty())
        }
    }
}
