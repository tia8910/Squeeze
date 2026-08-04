package com.squeeze.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The smooth blue trend line from the brand sheet.
 *
 * Two properties of the design's curve are load-bearing and are reproduced deliberately:
 * it is smooth rather than a polyline, and it carries a filled dot at the leading end. The
 * dot is what makes the line read as "here is where you are now" rather than as decoration.
 *
 * Smoothing is Catmull-Rom converted to cubic Bezier, which passes exactly through every
 * data point. That distinction matters for a chart: a plain Bezier fit would let the drawn
 * curve miss the actual readings, so the picture would disagree with the numbers beside it.
 * The curve can still overshoot vertically between points, so it is clamped to the plotting
 * box — a body fat line that bulges above the highest reading would be inventing data.
 *
 * @param values in data order, oldest first. Fewer than two renders just the leading dot.
 * @param animated sweeps the line on the first time it appears.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    strokeWidth: Dp = 4.dp,
    dotRadius: Dp = 5.dp,
    color: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    animated: Boolean = true,
) {
    val progress = remember { Animatable(if (animated) 0f else 1f) }

    LaunchedEffect(values) {
        if (animated) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }

    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas

        val stroke = strokeWidth.toPx()
        val dot = dotRadius.toPx()

        // Inset so the stroke's own width and the end dot stay inside the canvas instead of
        // being clipped in half at the edges.
        val inset = maxOf(stroke / 2f, dot)
        val left = inset
        val right = size.width - inset
        val top = inset
        val bottom = size.height - inset

        if (right <= left || bottom <= top) return@Canvas

        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 1e-9 }

        fun pointAt(index: Int): Offset {
            val x = if (values.size == 1) {
                right
            } else {
                left + (right - left) * index / (values.size - 1)
            }
            // A flat series sits on the centre line rather than collapsing onto an edge,
            // which would read as a floor or a ceiling that the data does not imply.
            val fraction = span?.let { (values[index] - min) / it } ?: 0.5
            // Higher value draws higher on screen, so the axis is inverted here.
            val y = bottom - (bottom - top) * fraction.toFloat()
            return Offset(x, y)
        }

        val points = List(values.size) { pointAt(it) }
        val head = points.last()

        if (points.size >= 2) {
            val full = smoothPath(points, top, bottom)

            val drawn = if (progress.value >= 1f) {
                full
            } else {
                val measure = PathMeasure().apply { setPath(full, false) }
                Path().also { measure.getSegment(0f, measure.length * progress.value, it, true) }
            }

            drawPath(
                path = drawn,
                color = color,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // The dot lands with the line rather than before it, so a partly swept chart never
        // shows an endpoint the curve has not reached.
        if (progress.value >= 1f || points.size < 2) {
            drawCircle(color = color, radius = dot, center = head)
        }
    }
}

/**
 * Catmull-Rom through [points], emitted as cubic Beziers.
 *
 * Tangents at the endpoints are duplicated rather than extrapolated, which keeps the first
 * and last segments from flaring outward — an extrapolated tangent on a two-point series
 * produces a visible hook that looks like a data feature.
 *
 * Control points are clamped to [top]..[bottom] so the curve cannot overshoot the range of
 * the data it is drawing.
 */
private fun smoothPath(points: List<Offset>, top: Float, bottom: Float): Path {
    val path = Path()
    path.moveTo(points[0].x, points[0].y)

    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]

        val c1 = Offset(
            x = p1.x + (p2.x - p0.x) / 6f,
            y = (p1.y + (p2.y - p0.y) / 6f).coerceIn(top, bottom),
        )
        val c2 = Offset(
            x = p2.x - (p3.x - p1.x) / 6f,
            y = (p2.y - (p3.y - p1.y) / 6f).coerceIn(top, bottom),
        )

        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }

    return path
}
