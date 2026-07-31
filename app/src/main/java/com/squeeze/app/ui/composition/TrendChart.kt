package com.squeeze.app.ui.composition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.squeeze.core.trend.TrendPoint

/**
 * Draws the filtered trend with its confidence band, and the raw readings behind it.
 *
 * Showing all three together is a deliberate honesty choice. Most apps in this category
 * plot raw readings joined by straight lines, which invites the user to read every wobble
 * as a real change when day-to-day scatter routinely exceeds a week of genuine progress.
 * Here the raw points stay visible as faint dots so nothing is hidden, the filtered line
 * shows what the data actually supports, and the band shows how sure that is.
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    bandColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
    rawColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
) {
    if (points.size < 2) {
        Box(
            modifier = modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Two measurements are needed before a trend can be drawn.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        val minDay = points.first().epochDay
        val maxDay = points.last().epochDay
        val dayRange = (maxDay - minDay).coerceAtLeast(1L).toFloat()

        // Scale to include the confidence band, so it is never clipped at the edges.
        val lows = points.map { it.level - it.levelConfidence95 } + points.map { it.raw }
        val highs = points.map { it.level + it.levelConfidence95 } + points.map { it.raw }
        val minValue = lows.min()
        val maxValue = highs.max()
        val valueRange = (maxValue - minValue).coerceAtLeast(0.5)

        fun x(day: Long) = (day - minDay) / dayRange * size.width
        fun y(value: Double) = size.height - ((value - minValue) / valueRange).toFloat() * size.height

        drawConfidenceBand(points, ::x, ::y, bandColor)

        val line = Path().apply {
            points.forEachIndexed { index, point ->
                val px = x(point.epochDay)
                val py = y(point.level)
                if (index == 0) moveTo(px, py) else lineTo(px, py)
            }
        }
        drawPath(line, color = lineColor, style = Stroke(width = 3.dp.toPx()))

        points.forEach { point ->
            drawCircle(
                color = rawColor,
                radius = 2.5.dp.toPx(),
                center = Offset(x(point.epochDay), y(point.raw)),
            )
        }
    }
}

private fun DrawScope.drawConfidenceBand(
    points: List<TrendPoint>,
    x: (Long) -> Float,
    y: (Double) -> Float,
    color: Color,
) {
    val band = Path().apply {
        points.forEachIndexed { index, point ->
            val px = x(point.epochDay)
            val py = y(point.level + point.levelConfidence95)
            if (index == 0) moveTo(px, py) else lineTo(px, py)
        }
        // Return along the lower bound to close the filled region.
        points.asReversed().forEach { point ->
            lineTo(x(point.epochDay), y(point.level - point.levelConfidence95))
        }
        close()
    }
    drawPath(band, color = color)
}
