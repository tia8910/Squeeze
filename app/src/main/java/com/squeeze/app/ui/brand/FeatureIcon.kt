package com.squeeze.app.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The brand sheet's four feature glyphs.
 *
 * Path data is copied from the sheet's inline SVG. Two shapes there were authored as
 * `<circle>` and `<rect>` rather than as paths and are transcribed to equivalent path
 * commands here, since a path is the only geometry [PathParser] accepts — the arcs and the
 * rounded corners are the same curves, expressed differently.
 *
 * All four are stroked at width 2 in a 24-unit viewport, unfilled, which is what makes them
 * read as a set rather than as four icons that happen to sit next to each other.
 */
enum class FeatureGlyph(internal val paths: List<String>) {
    /** Corner brackets around a lens: a frame being placed on a body. */
    SMART_SCAN(
        listOf(
            "M4 8V4h4M16 4h4v4M20 16v4h-4M8 20H4v-4",
            // <circle cx="12" cy="12" r="3">
            "M12 9a3 3 0 110 6a3 3 0 110-6",
        ),
    ),

    /** A rising line with an arrowhead. */
    TRACK_TRENDS(listOf("M3 17l6-6 4 4 8-9", "M15 6h6v6")),

    /** A trophy. */
    STAY_MOTIVATED(
        listOf(
            "M8 21h8M12 17v4M7 4h10v4a5 5 0 01-10 0V4z",
            "M7 6H4v2a4 4 0 004 4M17 6h3v2a4 4 0 01-4 4",
        ),
    ),

    /** A shield with a padlock. */
    PRIVACY_FIRST(
        listOf(
            "M12 3l8 4v5c0 5-3.4 8.6-8 9-4.6-.4-8-4-8-9V7l8-4z",
            // <rect x="9" y="10" width="6" height="5" rx="1">
            "M10 10h4a1 1 0 011 1v3a1 1 0 01-1 1h-4a1 1 0 01-1-1v-3a1 1 0 011-1z",
            "M10 10V9a2 2 0 014 0v1",
        ),
    ),
}

/** The viewport the glyph paths are authored in. */
private const val GLYPH_VIEWPORT = 24f

@Composable
fun FeatureIcon(
    glyph: FeatureGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    contentDescription: String? = null,
) {
    val parsed = remember(glyph) {
        glyph.paths.map { PathParser().parsePathString(it).toPath() }
    }

    // Bound to a local so the assignment below reads the parameter rather than the semantics
    // property of the same name, and so the null check narrows the type for the setter.
    val label = contentDescription

    Canvas(
        modifier
            .size(size)
            .then(
                if (label != null) {
                    Modifier.semantics { this.contentDescription = label }
                } else {
                    Modifier
                },
            ),
    ) {
        val factor = this.size.minDimension / GLYPH_VIEWPORT

        scale(scale = factor, pivot = Offset.Zero) {
            // Stroke width is specified in viewport units, so it scales with the glyph and
            // stays visually consistent at any size — dividing by the factor here would make
            // a large icon look hairline and a small one look heavy.
            val style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            parsed.forEach { drawPath(it, tint, style = style) }
        }
    }
}
