package com.example.chs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

val PulseRed = Color(0xFFD10A45)
val ChartGridColor = Color(0xFFE4E4E7)

data class ChartScale(val min: Int, val max: Int) {
    val step get() = (max - min) / 4
    fun fraction(value: Float): Float = ((value - min) / (max - min)).coerceIn(0f, 1f)
}

fun chartScale(metric: Metric, values: List<Float>): ChartScale = when (metric) {
    Metric.PULSE -> ChartScale(0, maxOf(200, ceil((values.maxOrNull() ?: 0f) / 50f).toInt() * 50))
    Metric.OXYGEN -> ChartScale(minOf(80, floor((values.minOrNull() ?: 100f) / 20f).toInt() * 20), 100)
}

@Composable
fun PulseChart(buckets: List<PulseBucket>, period: Period, metric: Metric = Metric.PULSE) {
    val scale = chartScale(metric, buckets.mapNotNull { it.average })
    val formatter = when (period) {
        Period.DAYS, Period.WEEKS -> DateTimeFormatter.ofPattern("d/M")
        Period.MONTHS -> DateTimeFormatter.ofPattern("MMM", Locale("ro", "RO"))
    }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(190.dp)) {
                val inset = 9.dp.toPx()
                val chartHeight = size.height - inset * 2
                for (i in 0..4) {
                    val y = inset + chartHeight * i / 4f
                    drawLine(ChartGridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                buckets.forEachIndexed { index, bucket ->
                    bucket.average?.let { value ->
                        val x = size.width * (index + 0.5f) / buckets.size
                        val y = inset + chartHeight * (1f - scale.fraction(value))
                        drawCircle(PulseRed, radius = 5.dp.toPx(), center = Offset(x, y))
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                buckets.forEachIndexed { index, bucket ->
                    val label = if (period != Period.MONTHS || index % 2 == 0) bucket.start.format(formatter) else ""
                    Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.height(190.dp), verticalArrangement = Arrangement.SpaceBetween) {
            for (i in 4 downTo 0) {
                Text("${scale.min + scale.step * i}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

data class LivePulseSample(val timestampNanos: Long, val bpm: Int)

fun addLiveSample(samples: List<LivePulseSample>, sample: LivePulseSample): List<LivePulseSample> {
    val windowNanos = 30_000_000_000L
    return (samples + sample).filter { it.timestampNanos >= sample.timestampNanos - windowNanos }.takeLast(100)
}

@Composable
fun LivePulseChart(samples: List<LivePulseSample>) {
    Text("Valori intermediare (bpm)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        for (i in 0..2) {
            val y = size.height * (i + 1) / 4f
            drawLine(ChartGridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        if (samples.isNotEmpty()) {
            val lastTime = samples.last().timestampNanos
            val minBpm = samples.minOf { it.bpm } - 10f
            val maxBpm = samples.maxOf { it.bpm } + 10f
            val inset = 6.dp.toPx()
            val width = size.width - 2 * inset
            val height = size.height - 2 * inset
            val points = samples.map { sample ->
                val x = inset + width * (1f - ((lastTime - sample.timestampNanos).toDouble() / 30_000_000_000.0).toFloat().coerceIn(0f, 1f))
                val y = inset + height * (1f - (sample.bpm - minBpm) / (maxBpm - minBpm))
                Offset(x, y)
            }
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, PulseRed, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
            drawCircle(PulseRed, radius = 4.dp.toPx(), center = points.last())
        }
    }
    Text("Ultimele 30 de secunde • nu este ECG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
