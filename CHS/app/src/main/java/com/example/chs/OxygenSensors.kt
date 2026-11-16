package com.example.chs

/** Plain description of a hardware sensor, so matching can be unit-tested without Android. */
data class SensorInfo(val name: String, val stringType: String, val type: Int)

/** Where to read raw red / infrared PPG light from. */
sealed class PpgSource<T> {
    /** Two sensors: one reports infrared light, the other red light (values[0] of each). */
    data class Separate<T>(val ir: T, val red: T) : PpgSource<T>()

    /** One raw HRM sensor reporting both channels: values[0] = IR, values[1] = RED. */
    data class Combined<T>(val sensor: T) : PpgSource<T>()
}

private const val TYPE_HEART_RATE = 21
private val ppgHint = Regex("""\b(hrm|ppg|spo2|oximeter|bio|led)\b|hrm_|ppg_""")
private val irWord = Regex("""(\b|_)(ir|infrared)(\b|_)""")
private val redWord = Regex("""(\b|_)red(\b|_)""")
private val rawWord = Regex("""hrm[ _]?raw|raw[ _]?(hrm|ppg)|\bppg\b|ppg_raw""")

private fun SensorInfo.text() = "$name $stringType".lowercase()

fun <T> findPpgSensors(sensors: List<T>, info: (T) -> SensorInfo): PpgSource<T>? {
    // Galaxy S10 (MAX86915): "MAX86915 IR" / "MAX86915 RED", readable with BODY_SENSORS.
    // Other LED sensors on the same chip (e.g. rear_led_red) need Samsung-only permissions.
    val exactIr = sensors.firstOrNull { info(it).stringType.lowercase().endsWith("hrm_led_ir") }
    val exactRed = sensors.firstOrNull { info(it).stringType.lowercase().endsWith("hrm_led_red") }
    if (exactIr != null && exactRed != null) return PpgSource.Separate(exactIr, exactRed)

    val candidates = sensors.filter { info(it).type != TYPE_HEART_RATE && ppgHint.containsMatchIn(info(it).text()) }
    val ir = candidates.firstOrNull { val t = info(it).text(); irWord.containsMatchIn(t) && !redWord.containsMatchIn(t) }
    val red = candidates.firstOrNull { val t = info(it).text(); redWord.containsMatchIn(t) && !irWord.containsMatchIn(t) }
    if (ir != null && red != null) return PpgSource.Separate(ir, red)
    val raw = candidates.firstOrNull { rawWord.containsMatchIn(info(it).text()) }
    return raw?.let { PpgSource.Combined(it) }
}
