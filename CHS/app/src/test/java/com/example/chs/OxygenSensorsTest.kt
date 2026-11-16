package com.example.chs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OxygenSensorsTest {
    private val accelerometer = SensorInfo("LSM6DSO Accelerometer", "android.sensor.accelerometer", 1)
    private val heartRate = SensorInfo("HRM Sensor", "android.sensor.heart_rate", 21)
    private val proximity = SensorInfo("TMD4907 Proximity Sensor (IR)", "android.sensor.proximity", 8)
    private val light = SensorInfo("TMD4907 RGB Light Sensor", "com.samsung.sensor.light_cct", 65587)
    private val ir = SensorInfo("MAX86915 HRM LED IR Sensor", "com.samsung.sensor.hrm_led_ir", 65572)
    private val red = SensorInfo("MAX86915 HRM LED RED Sensor", "com.samsung.sensor.hrm_led_red", 65573)
    private val raw = SensorInfo("HRM Raw Sensor", "com.samsung.sensor.hrm_raw", 65562)

    private fun find(vararg sensors: SensorInfo) = findPpgSensors(sensors.toList()) { it }

    @Test
    fun findsSamsungInfraredAndRedLedSensors() {
        assertEquals(PpgSource.Separate(ir, red), find(accelerometer, heartRate, proximity, light, red, ir))
    }

    /** Copied from `adb shell dumpsys sensorservice` on the user's Galaxy S10 (SM-G973F, Android 12). */
    @Test
    fun picksTheBodySensorLedsOnARealGalaxyS10() {
        val s10 = listOf(
            SensorInfo("LSM6DSO Acceleration Sensor", "android.sensor.accelerometer", 1),
            SensorInfo("TCS3407 RGB IR Sensor", "com.samsung.sensor.light_ir", 65578),
            SensorInfo("TCS3407 RGB Sensor", "com.samsung.sensor.light_cct", 65587),
            SensorInfo("MAX86915 HRM RAW", "com.samsung.sensor.bio", 65561),
            SensorInfo("MAX86915 Rear ALS", "com.samsung.sensor.bio_alc", 65577),
            SensorInfo("MAX86915 Rear PROX", "com.samsung.sensor.rear_prox_detect", 65580),
            SensorInfo("MAX86915 REAR SVC LED RED", "com.samsung.sensor.rear_led_red", 65627),
            SensorInfo("MAX86915 IR", "com.samsung.sensor.hrm_led_ir", 65571),
            SensorInfo("MAX86915 RED", "com.samsung.sensor.hrm_led_red", 65572),
            SensorInfo("MAX86915 GREEN", "com.samsung.sensor.hrm_led_green", 65573),
            SensorInfo("MAX86915 BLUE", "com.samsung.sensor.hrm_led_blue", 65574),
            SensorInfo("HRM Sensor", "com.samsung.sensor.bio_hrm", 65562),
            SensorInfo("HeartRate Sensor", "android.sensor.heart_rate", 21)
        )
        val source = find(*s10.toTypedArray()) as PpgSource.Separate
        assertEquals("MAX86915 IR", source.ir.name)
        assertEquals("MAX86915 RED", source.red.name)
    }

    @Test
    fun matchesByStringTypeWhenNamesAreGeneric() {
        val a = SensorInfo("Sensor 1", "com.samsung.sensor.hrm_led_ir", 100)
        val b = SensorInfo("Sensor 2", "com.samsung.sensor.hrm_led_red", 101)
        assertEquals(PpgSource.Separate(a, b), find(b, a))
    }

    @Test
    fun matchesByNameWhenStringTypeIsEmpty() {
        val a = SensorInfo("ADPD PPG IR", "", 100)
        val b = SensorInfo("ADPD PPG Red", "", 101)
        assertEquals(PpgSource.Separate(a, b), find(a, b))
    }

    @Test
    fun fallsBackToASingleRawSensor() {
        assertEquals(PpgSource.Combined(raw), find(accelerometer, heartRate, raw))
        assertEquals(PpgSource.Combined(raw), find(ir, raw))
    }

    @Test
    fun prefersSeparateChannelsOverRaw() {
        assertEquals(PpgSource.Separate(ir, red), find(raw, ir, red))
    }

    @Test
    fun ignoresHeartRateProximityAndLightSensors() {
        assertNull(find())
        assertNull(find(accelerometer, heartRate, proximity, light))
        assertNull(find(ir))
    }
}
