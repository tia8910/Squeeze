package com.squeeze.app.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squeeze.app.ui.theme.Brand

/**
 * The Squeeze.fit mark, drawn from the brand sheet's own path data.
 *
 * These three strings are the artwork — copied verbatim out of the brand sheet's SVG rather
 * than redrawn — so the mark here is the mark in the design, not an interpretation of it.
 * They are parsed with [PathParser], the same code path Compose uses for vector drawables,
 * which means the curves are exact rather than approximated with hand-placed control points.
 *
 * `res/drawable/ic_launcher_foreground.xml` carries the same figure data for the launcher
 * icon, where an XML vector is required and cannot reference a Kotlin constant. If one
 * changes, change both.
 */
private const val FIGURE_PATH =
    "M100 28c-15 0-26 11-26 25 0 10 6 19 15 23-7 3-13 8-17 15l-10 18c-8 13-20 24-35 31l12 " +
        "18c19-8 35-22 46-39l7-12v31l-22 23 17 13 13-13 13 13 17-13-22-23v-31l7 12c11 17 27 " +
        "31 46 39l12-18c-15-7-27-18-35-31l-10-18c-4-7-10-12-17-15 9-4 15-13 15-23 0-14-11-25-26-25z"

/** The upper squeeze band across the chest. */
private const val BAND_UPPER_PATH = "M60 90c16 8 30 12 40 12s24-4 40-12"

/** The lower squeeze band. Wider and thinner, so the pair reads as compression. */
private const val BAND_LOWER_PATH = "M52 110c18 8 34 12 48 12s30-4 48-12"

/** The coordinate space the paths above are authored in. */
private const val VIEWPORT = 200f

/**
 * The flexing figure with the two squeeze bands across it.
 *
 * @param squeeze 0..1, how far the bands have swept across the chest. Animating this from 0
 *   to 1 makes the mark perform its own name — the figure appears, then the squeeze lands.
 *   Left at 1 the mark is simply the static logo.
 * @param disc when set, the pale circle the brand sheet places behind the full-colour mark.
 *   Omitted for the compact monochrome uses, which sit directly on the surface.
 */
@Composable
fun SqueezeMark(
    size: Dp,
    modifier: Modifier = Modifier,
    squeeze: Float = 1f,
    figureBrush: Brush? = null,
    figureColor: Color = Brand.Blue,
    bandColor: Color = Color.White,
    disc: Color? = null,
) {
    // Parsed once and reused. Re-parsing three path strings on every recomposition would be
    // wasted work on a mark that never changes shape.
    val figure = remember { PathParser().parsePathString(FIGURE_PATH).toPath() }
    val bandUpper = remember { PathParser().parsePathString(BAND_UPPER_PATH).toPath() }
    val bandLower = remember { PathParser().parsePathString(BAND_LOWER_PATH).toPath() }

    Canvas(modifier.size(size)) {
        // The paths are authored against a 200x200 viewport; scale the whole draw rather
        // than rewriting coordinates, so the geometry stays identical to the source.
        val factor = this.size.minDimension / VIEWPORT

        scale(scale = factor, pivot = Offset.Zero) {
            disc?.let {
                drawCircle(color = it, radius = 90f, center = Offset(100f, 100f))
            }

            if (figureBrush != null) {
                drawPath(figure, figureBrush)
            } else {
                drawPath(figure, figureColor)
            }

            if (squeeze > 0f) {
                drawSweptBand(bandUpper, bandColor, strokeWidth = 10f, progress = squeeze)
                drawSweptBand(bandLower, bandColor, strokeWidth = 8f, progress = squeeze)
            }
        }
    }
}

/**
 * Draws a band revealed left to right.
 *
 * The reveal is done by measuring the path and extracting the leading fraction, not by
 * clipping to a rectangle. Clipping would cut the round cap into a straight edge partway
 * through the sweep; this keeps the cap intact at every frame, so the band looks drawn on
 * rather than wiped in.
 */
private fun DrawScope.drawSweptBand(
    source: Path,
    colour: Color,
    strokeWidth: Float,
    progress: Float,
) {
    val path = if (progress >= 1f) {
        source
    } else {
        val measure = PathMeasure().apply { setPath(source, false) }
        Path().also { measure.getSegment(0f, measure.length * progress, it, true) }
    }

    drawPath(
        path = path,
        color = colour,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}

/** The navy-to-blue gradient the brand sheet fills the full-colour mark with. */
@Composable
fun squeezeMarkBrush(): Brush = Brush.linearGradient(
    colors = listOf(Brand.Navy, Brand.Blue),
    start = Offset.Zero,
    end = Offset(VIEWPORT, VIEWPORT),
)

/**
 * Mark plus wordmark, for app bars.
 *
 * The brand sheet sets the mark flat blue at this size — the gradient is reserved for the
 * hero lockup, because at 34px it reads as a muddy fill rather than as a gradient.
 */
@Composable
fun SqueezeWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 34.dp,
    fontSize: TextUnit = 18.sp,
    squeeze: Float = 1f,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SqueezeMark(
            size = markSize,
            squeeze = squeeze,
            figureColor = Brand.Blue,
            bandColor = MaterialTheme.colorScheme.surface,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "squeeze",
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = ".fit",
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The hero lockup: gradient mark beside the wordmark, tagline beneath.
 *
 * Horizontal rather than stacked, matching the brand sheet. The tagline's 8sp tracking is
 * from the design and is what makes three short words hold the width of the lockup above.
 */
@Composable
fun SqueezeLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = 120.dp,
    squeeze: Float = 1f,
    showTagline: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SqueezeMark(
                size = markSize,
                squeeze = squeeze,
                figureBrush = squeezeMarkBrush(),
                disc = MaterialTheme.colorScheme.surfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "squeeze",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = ".fit",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (showTagline) {
            Row {
                Text(
                    text = "SMALL STEPS. ",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "BIG CHANGE.",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
