package com.squeeze.app.ui.composition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.theme.chartPalette
import com.squeeze.core.trend.ChartScale
import com.squeeze.core.trend.TrendChange
import com.squeeze.core.trend.TrendPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * A single measure over time: the filtered trend, every reading behind it, and a plain
 * sentence saying whether anything has really changed.
 *
 * One series per chart, always. Body fat and lean mass are different quantities on
 * different scales, and putting them on a shared axis with two y-scales would let the
 * apparent crossing point be set by axis choice rather than by the data.
 *
 * **Redrawn for reading at a glance.** The first version sized its axis to the edges of the
 * 95% band, so a two-scan history ran from 11.7 to 19.1 with the line lost in a slab of
 * blue, and the latest value was printed on top of the nearest axis label. Now:
 *
 * - the answer comes first, in words, above the plot — the latest value and what it did
 *   since the first reading, with the filter's verdict on whether that is real;
 * - the axis is round numbers and never shorter than [TrendFactor.minSpan], so a change
 *   inside one scan's noise looks as small as it is;
 * - every reading is a visible dot, the trend a line through them, and the dates sit under
 *   the readings they belong to.
 *
 * The uncertainty band is gone from the plot because it was the absolute accuracy of each
 * estimate (±3.3 points), which is dominated by an offset that cancels between two scans of
 * the same person — drawing it made every change look meaningless. The absolute figure is
 * still on the hero card; this chart is about change.
 */
@Composable
fun TrendChart(
    title: String,
    unitSuffix: String,
    points: List<TrendPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = chartPalette.bodyFat,
    minSpan: Double = 1.0,
) {
    val palette = chartPalette
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val surface = MaterialTheme.colorScheme.surface
    // The series hue, lighter: a reading belongs to its trend, and a grey ring on a grey
    // grid is the part of the old chart nobody could find.
    val readingColor = lineColor.copy(alpha = 0.6f)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            points.lastOrNull()?.let { last ->
                Text(
                    text = "%.1f%s".format(last.level, unitSuffix),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (points.size < 2) {
            Text(
                text = "Two measurements are needed before a trend can be drawn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        TrendChange.of(points)?.let { change ->
            Text(
                text = changeSentence(change, unitSuffix),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (change.significant) FontWeight.SemiBold else FontWeight.Normal,
                color = if (change.significant) lineColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val axis = ChartScale.axis(points.map { it.level } + points.map { it.raw }, minSpan) ?: return@Column
        val labelled = dateLabelIndices(points.size)

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(top = 8.dp),
        ) {
            val axisWidth = 36.dp.toPx()
            val dateHeight = 18.dp.toPx()
            val inset = 8.dp.toPx()
            val left = axisWidth
            val right = size.width - inset
            val top = inset
            val bottom = size.height - dateHeight - inset
            if (right <= left || bottom <= top) return@Canvas

            // One step per record, not per day: readings land whenever the user remembers,
            // and a real time axis would hand most of the width to gaps. The dates under the
            // dots give elapsed time back.
            val lastIndex = (points.size - 1).coerceAtLeast(1).toFloat()
            fun x(index: Int) = left + index / lastIndex * (right - left)
            fun y(value: Double) =
                bottom - ((value - axis.min) / (axis.max - axis.min)).toFloat() * (bottom - top)

            // Round-number rules, labelled on the left where nothing else is drawn.
            axis.ticks.forEach { tick ->
                val ty = y(tick)
                drawLine(
                    color = palette.grid,
                    start = Offset(left, ty),
                    end = Offset(right, ty),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
                )
                val label = textMeasurer.measure("%.${axis.decimals}f".format(tick), labelStyle)
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(left - label.size.width - 6.dp.toPx(), ty - label.size.height / 2f),
                )
            }

            // A soft fill under the trend, so its direction reads before its detail.
            val area = Path().apply {
                moveTo(x(0), bottom)
                points.forEachIndexed { i, p -> lineTo(x(i), y(p.level)) }
                lineTo(x(points.lastIndex), bottom)
                close()
            }
            drawPath(area, color = lineColor.copy(alpha = palette.bandAlpha * 0.6f))

            val line = Path().apply {
                points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(x(index), y(point.level)) else lineTo(x(index), y(point.level))
                }
            }
            drawPath(line, color = lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

            // Every reading, hollow, so it is visible without competing with the trend.
            points.forEachIndexed { index, point ->
                val c = Offset(x(index), y(point.raw))
                drawCircle(surface, 4.dp.toPx(), c)
                drawCircle(readingColor, 4.dp.toPx(), c, style = Stroke(width = 1.5.dp.toPx()))
            }

            val lastCenter = Offset(x(points.lastIndex), y(points.last().level))
            drawCircle(surface, 7.dp.toPx(), lastCenter)
            drawCircle(lineColor, 5.dp.toPx(), lastCenter)

            // Dates under the readings they belong to; ends anchored so they never clip.
            labelled.forEach { index ->
                val text = textMeasurer.measure(shortDate(points[index].epochDay), labelStyle)
                val cx = x(index)
                val lx = when (index) {
                    0 -> cx - 4.dp.toPx()
                    points.lastIndex -> cx - text.size.width + 4.dp.toPx()
                    else -> cx - text.size.width / 2f
                }.coerceIn(0f, maxOf(0f, size.width - text.size.width))
                drawText(text, topLeft = Offset(lx, size.height - dateHeight))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendMark(filled = true, color = lineColor, label = "Trend")
            LegendMark(filled = false, color = lineColor.copy(alpha = 0.6f), label = "Each measurement · ${points.size}")
        }
    }
}

@Composable
private fun LegendMark(filled: Boolean, color: Color, label: String) {
    val surface = MaterialTheme.colorScheme.surface
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) {
            if (filled) {
                drawCircle(color)
            } else {
                drawCircle(surface)
                drawCircle(color, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * The chart's answer in one sentence: how far it moved and whether that is real.
 *
 * "Real" is the filter's verdict, not the size of the move: a two-point change of 0.6 on a
 * method whose readings wobble by more than that is not a change, however the line slopes.
 */
internal fun changeSentence(change: TrendChange, unitSuffix: String): String {
    val arrow = when {
        change.delta > 0.05 -> "▲"
        change.delta < -0.05 -> "▼"
        else -> "■"
    }
    val amount = "%.1f%s".format(abs(change.delta), unitSuffix)
    val since = "since ${shortDate(change.fromDay)}"
    return if (change.significant) {
        val per = "%.2f%s a week".format(abs(change.weeklyChange), unitSuffix)
        "$arrow $amount $since. A real change of $per."
    } else {
        "$arrow $amount $since. Within measurement noise, so no real change yet."
    }
}

/** First and last always; the rest only while there is room for every one to be read. */
private fun dateLabelIndices(count: Int): List<Int> = when {
    count <= 1 -> listOf(0)
    count <= 5 -> (0 until count).toList()
    else -> listOf(0, count / 2, count - 1)
}

private fun shortDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM"))
