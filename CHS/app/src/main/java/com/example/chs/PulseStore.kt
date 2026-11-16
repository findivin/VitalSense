package com.example.chs

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Reading(val time: Instant, val value: Int)
data class Profile(val id: Long, val name: String, val age: Int?, val description: String, val isSample: Boolean)

enum class Metric(val title: String, val unit: String, val table: String, val column: String, val averageLabel: String, val disclaimer: String) {
    PULSE("Puls", "bpm", "readings", "bpm", "Puls mediu", "Măsurătorile sunt orientative și nu reprezintă un diagnostic medical."),
    OXYGEN(
        "Oxigen (SpO₂)", "%", "oxygen_readings", "spo2", "Saturație medie",
        "SpO₂ este estimat din lumina roșie și infraroșie a senzorului, fără calibrare medicală. Valorile sunt orientative și nu reprezintă un diagnostic medical."
    )
}

class PulseStore(context: Context, databaseName: String = "pulse.db") : SQLiteOpenHelper(context, databaseName, null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        createProfiles(db)
        db.execSQL("CREATE TABLE readings (id INTEGER PRIMARY KEY, time INTEGER NOT NULL, bpm INTEGER NOT NULL, profile_id INTEGER NOT NULL DEFAULT 1)")
        createOxygen(db)
        seedSamples(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createProfiles(db)
            db.execSQL("ALTER TABLE readings ADD COLUMN profile_id INTEGER NOT NULL DEFAULT 1")
        }
        if (oldVersion < 3) createOxygen(db)
        if (oldVersion < 2) {
            seedSamples(db)
        } else if (oldVersion < 3) {
            seedOxygenForExistingSamples(db)
        }
    }

    private fun createProfiles(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE profiles (id INTEGER PRIMARY KEY, name TEXT NOT NULL, age INTEGER, description TEXT NOT NULL, is_sample INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("INSERT INTO profiles (id, name, description) VALUES (1, 'Profil personal', '')")
    }

    private fun createOxygen(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE oxygen_readings (id INTEGER PRIMARY KEY, time INTEGER NOT NULL, spo2 INTEGER NOT NULL, profile_id INTEGER NOT NULL)")
    }

    private fun seedSamples(db: SQLiteDatabase) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        sampleProfiles.forEach { sample ->
            val profileId = insertProfile(db, sample.name, sample.age, sample.description, true)
            sampleReadings(sample, today, zone).forEach { insertReading(db, Metric.PULSE, profileId, it) }
            sampleOxygenReadings(sample, today, zone).forEach { insertReading(db, Metric.OXYGEN, profileId, it) }
        }
    }

    private fun seedOxygenForExistingSamples(db: SQLiteDatabase) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        sampleProfiles.forEach { sample ->
            db.query("profiles", arrayOf("id"), "is_sample = 1 AND name = ?", arrayOf(sample.name), null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val profileId = cursor.getLong(0)
                    sampleOxygenReadings(sample, today, zone).forEach { insertReading(db, Metric.OXYGEN, profileId, it) }
                }
            }
        }
    }

    private fun insertProfile(db: SQLiteDatabase, name: String, age: Int?, description: String, isSample: Boolean): Long {
        val values = ContentValues().apply {
            put("name", name)
            if (age != null) put("age", age) else putNull("age")
            put("description", description)
            put("is_sample", if (isSample) 1 else 0)
        }
        return db.insertOrThrow("profiles", null, values)
    }

    fun addProfile(name: String, age: Int?, description: String): Long =
        insertProfile(writableDatabase, name, age, description, false)

    fun profiles(): List<Profile> {
        val result = mutableListOf<Profile>()
        readableDatabase.query("profiles", arrayOf("id", "name", "age", "description", "is_sample"), null, null, null, null, "id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                result.add(Profile(cursor.getLong(0), cursor.getString(1), if (cursor.isNull(2)) null else cursor.getInt(2), cursor.getString(3), cursor.getInt(4) == 1))
            }
        }
        return result
    }

    private fun insertReading(db: SQLiteDatabase, metric: Metric, profileId: Long, reading: Reading) {
        val values = ContentValues().apply {
            put("profile_id", profileId)
            put("time", reading.time.toEpochMilli())
            put(metric.column, reading.value)
        }
        db.insertOrThrow(metric.table, null, values)
    }

    fun save(metric: Metric, profileId: Long, reading: Reading) = insertReading(writableDatabase, metric, profileId, reading)

    fun allReadings(metric: Metric, profileId: Long): List<Reading> {
        val readings = mutableListOf<Reading>()
        readableDatabase.query(metric.table, arrayOf("time", metric.column), "profile_id = ?", arrayOf(profileId.toString()), null, null, "time DESC").use { cursor ->
            while (cursor.moveToNext()) {
                readings.add(Reading(Instant.ofEpochMilli(cursor.getLong(0)), cursor.getInt(1)))
            }
        }
        return readings
    }
}
