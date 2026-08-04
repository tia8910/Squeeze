package com.squeeze.app.ui.composition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.theme.chartPalette
import com.squeeze.core.trend.TrendPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * A single measure over time, with its confidence band and the raw readings behind it.
 *
 * One series per chart, always. Body fat and lean mass are different quantities on
 * different scales, and putting them on a shared axis with two y-scales would let the
 * apparent crossing point be set by axis choice rather than by the data. Two charts cost a
 * little vertical space and cannot lie in that particular way.
 *
 * Because there is one series, there is no legend — the title names it. The latest value is
 * labelled directly on the plot; the rest are not, since a number on every point is noise
 * and the exact history is available in the measurement list.
 */
@Composable
fun TrendChart(
    title: String,
    unitSuffix: String,
    points: List<TrendPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = chartPalette.bodyFat,
) {
    val palette = chartPalette
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val valueStyle = MaterialTheme.typography.labelMedium.copy(
        // Text wears text tokens rather than the series colour; the mark beside it carries
        // identity. Coloured numerals read as decoration and lose contrast.
        color = MaterialTheme.colorScheme.onSurface,
    )

    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (points.size < 2) {
            Text(
                text = "Two measurements are needed before a trend can be drawn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(end = 44.dp, bottom = 18.dp, top = 8.dp),
        ) {
            // One step per record, not per day. Measurements land whenever the user
            // remembers to take them, so a real time axis bunches a fortnight of daily
            // readings into a smear and then hands two thirds of the width to the gap where
            // nothing happened. Even spacing puts every reading where it can be seen and
            // compared, which is what this chart is for. The dates below the plot give the
            // elapsed time back, so the spacing is a layout choice rather than a claim.
            val lastIndex = (points.size - 1).coerceAtLeast(1).toFloat()

            val lows = points.map { it.level - it.levelConfidence95 } + points.map { it.raw }
            val highs = points.map { it.level + it.levelConfidence95 } + points.map { it.raw }
            val minValue = lows.min()
            val maxValue = highs.max()
            val valueRange = (maxValue - minValue).coerceAtLeast(0.5)

            fun x(index: Int) = index / lastIndex * size.width
            fun y(value: Double) =
                size.height - ((value - minValue) / valueRange).toFloat() * size.height

            drawGridAndAxisLabels(
                minValue = minValue,
                maxValue = maxValue,
                palette.grid,
                textMeasurer,
                labelStyle,
                ::y,
            )

            // Band first so the line and points sit above it.
            drawConfidenceBand(points, ::x, ::y, lineColor.copy(alpha = palette.bandAlpha))

            val line = Path().apply {
                points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(x(index), y(point.level))
                    else lineTo(x(index), y(point.level))
                }
            }
            // 2px: thin enough to read as a precise estimate rather than a wide brush.
            drawPath(line, color = lineColor, style = Stroke(width = 2.dp.toPx()))

            // Raw readings stay visible so nothing is hidden by the smoothing, but they are
            // recessive: the filtered line is the claim, these are the evidence.
            points.forEachIndexed { index, point ->
                drawCircle(
                    color = palette.rawMark,
                    radius = 2.dp.toPx(),
                    center = Offset(x(index), y(point.raw)),
                )
            }

            val last = points.last()
            val lastCenter = Offset(x(points.lastIndex), y(last.level))

            // A surface ring separates the marker from whatever it overlaps.
            drawCircle(Color.White.copy(alpha = 0.9f), 5.dp.toPx(), lastCenter)
            drawCircle(lineColor, 4.dp.toPx(), lastCenter)

            val label = textMeasurer.measure("%.1f%s".format(last.level, unitSuffix), valueStyle)
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    x = (lastCenter.x + 8.dp.toPx())
                        .coerceAtMost(size.width - label.size.width + 40.dp.toPx()),
                    y = lastCenter.y - label.size.height / 2f,
                ),
            )
        }

        // The x-axis is evenly spaced, so it carries no time information on its own. These
        // two dates put it back: without them a fortnight and a year look identical, and a
        // rate of change read off the slope would be meaningless.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 44.dp),
        ) {
            Text(
                text = shortDate(points.first().epochDay),
                style = labelStyle,
            )
            Text(
                text = "${points.size} entries",
                style = labelStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = shortDate(points.last().epochDay),
                style = labelStyle,
            )
        }
    }
}

private fun shortDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM"))

/**
 * Horizontal rules at the extremes and midpoint, with their values.
 *
 * Three lines rather than a dense lattice: enough to judge magnitude, few enough that the
 * grid stays behind the data instead of competing with it.
 */
private fun DrawScope.drawGridAndAxisLabels(
    minValue: Double,
    maxValue: Double,
    gridColor: Color,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    y: (Double) -> Float,
) {
    val midValue = (minValue + maxValue) / 2.0
    listOf(maxValue, midValue, minValue).forEach { value ->
        val lineY = y(value)
        drawLine(
            color = gridColor,
            start = Offset(0f, lineY),
            end = Offset(size.width, lineY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
        )

        val label = textMeasurer.measure("%.1f".format(value), labelStyle)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(size.width + 6.dp.toPx(), lineY - label.size.height / 2f),
        )
    }
}

private fun DrawScope.drawConfidenceBand(
    points: List<TrendPoint>,
    x: (Int) -> Float,
    y: (Double) -> Float,
    color: Color,
) {
    val band = Path().apply {
        points.forEachIndexed { index, point ->
            val px = x(index)
            val py = y(point.level + point.levelConfidence95)
            if (index == 0) moveTo(px, py) else lineTo(px, py)
        }
        for (index in points.indices.reversed()) {
            lineTo(x(index), y(points[index].level - points[index].levelConfidence95))
        }
        close()
    }
    drawPath(band, color = color)
}
