package com.squeeze.app.ui.composition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.squeeze.core.render.BodyFigure
import com.squeeze.core.render.FigurePoint

/**
 * Draws a [BodyFigure].
 *
 * The figure arrives in units of the person's own stature, so this does nothing but choose a
 * scale and stroke it: all the anatomy is decided in core, where it can be tested without a
 * device. The one decision made here is that the figure is fitted to the panel's height and
 * centred horizontally, which keeps two records comparable side by side — a body drawn to fit
 * its own width would make a wider person no wider on screen, defeating the entire point.
 */
@Composable
fun BodyFigureView(
    figure: BodyFigure,
    fill: Color,
    line: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxSize()) {
        val scale = size.height * FIGURE_HEIGHT_FRACTION
        val top = (size.height - scale) / 2f
        val centreX = size.width / 2f

        fun place(point: FigurePoint) = Offset(
            x = centreX + (point.x * scale).toFloat(),
            y = top + (point.y * scale).toFloat(),
        )

        figure.outline.forEach { polygon ->
            drawPath(closedPath(polygon, ::place), color = fill)
        }

        figure.detail.forEach { polyline ->
            drawPath(
                openPath(polyline, ::place),
                color = line,
                style = Stroke(width = DETAIL_STROKE.toPx()),
            )
        }
    }
}

/** How much of the panel's height the figure occupies, crown to floor. */
private const val FIGURE_HEIGHT_FRACTION = 0.92f

private val DETAIL_STROKE = 1.2.dp

private fun closedPath(points: List<FigurePoint>, place: (FigurePoint) -> Offset): Path =
    Path().apply {
        points.forEachIndexed { index, point ->
            val offset = place(point)
            if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
        }
        close()
    }

private fun openPath(points: List<FigurePoint>, place: (FigurePoint) -> Offset): Path =
    Path().apply {
        points.forEachIndexed { index, point ->
            val offset = place(point)
            if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
        }
    }
