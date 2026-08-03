package com.squeeze.app.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squeeze.app.ui.theme.Brand

/**
 * The Squeeze mark: two arcs closing on a waist, crossed by a measurement line.
 *
 * The shape is the product argument in one glyph. The arcs read simultaneously as a torso
 * narrowing and as calipers closing — the squeeze — and the horizontal rule through the
 * narrowest point is the measurement being taken. It also happens to trace an S.
 *
 * Drawn as vector geometry rather than shipped as a bitmap so it stays sharp at any size,
 * animates, and re-tints with the theme without a second asset.
 *
 * @param progress 0..1, how far the arcs have closed. Animating this from 0 to 1 makes the
 *   logo perform its own meaning on the landing screen.
 */
@Composable
fun SqueezeMark(
    size: Dp,
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    colors: List<Color> = Brand.LogoGradient,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.13f

        // How far the arcs bow inward. At progress 0 they are nearly straight; at 1 they
        // pinch to the waist, so the animation is literally the squeeze happening.
        val pinch = w * 0.30f * progress
        val brush = Brush.linearGradient(colors)

        val left = Path().apply {
            moveTo(w * 0.24f, h * 0.12f)
            cubicTo(
                w * 0.24f, h * 0.34f,
                w * 0.24f + pinch, h * 0.40f,
                w * 0.24f + pinch, h * 0.50f,
            )
            cubicTo(
                w * 0.24f + pinch, h * 0.60f,
                w * 0.24f, h * 0.66f,
                w * 0.24f, h * 0.88f,
            )
        }

        val right = Path().apply {
            moveTo(w * 0.76f, h * 0.12f)
            cubicTo(
                w * 0.76f, h * 0.34f,
                w * 0.76f - pinch, h * 0.40f,
                w * 0.76f - pinch, h * 0.50f,
            )
            cubicTo(
                w * 0.76f - pinch, h * 0.60f,
                w * 0.76f, h * 0.66f,
                w * 0.76f, h * 0.88f,
            )
        }

        drawPath(left, brush, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawPath(right, brush, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // The measurement line fades in only as the arcs finish closing: the reading is
        // taken after the squeeze, never during it.
        val lineAlpha = ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f)
        if (lineAlpha > 0f) {
            drawLine(
                brush = brush,
                start = androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.50f),
                end = androidx.compose.ui.geometry.Offset(w * 0.88f, h * 0.50f),
                strokeWidth = stroke * 0.62f,
                cap = StrokeCap.Round,
                alpha = lineAlpha,
            )
        }
    }
}

/**
 * Mark plus wordmark.
 *
 * "squeeze" is set heavy and tight; ".fit" is lighter and tinted, so the domain reads as a
 * suffix rather than as part of the word.
 */
@Composable
fun SqueezeWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 40.dp,
    fontSize: TextUnit = 28.sp,
    progress: Float = 1f,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SqueezeMark(size = markSize, progress = progress)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "squeeze",
                style = MaterialTheme.typography.headlineMedium,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            Text(
                text = ".fit",
                style = MaterialTheme.typography.headlineMedium,
                fontSize = fontSize,
                fontWeight = FontWeight.Light,
                color = Brand.Teal,
                letterSpacing = (-1).sp,
            )
        }
    }
}

/** Stacked lockup for the landing screen, where the mark leads. */
@Composable
fun SqueezeLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = 96.dp,
    progress: Float = 1f,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SqueezeMark(size = markSize, progress = progress)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "squeeze",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp,
            )
            Text(
                text = ".fit",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = Brand.Teal,
                letterSpacing = (-2).sp,
            )
        }
    }
}
