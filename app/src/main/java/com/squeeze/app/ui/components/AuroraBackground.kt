package com.squeeze.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.squeeze.app.ui.theme.auroraPalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * Slow-drifting colour fields behind the content.
 *
 * Three soft radial blobs orbit on offset paths, so the background is never quite the same
 * twice without ever competing with what sits on top of it. The motion is deliberately slow
 * — a full cycle takes the better part of a minute — because a background that moves at
 * reading speed is a background the user has to ignore.
 *
 * @param intensity 0..1 multiplier on blob alpha. The landing screen turns this up; content
 *   screens keep it low enough that text contrast is untouched.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = auroraPalette
    val transition = rememberInfiniteTransition(label = "aurora")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 48_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "auroraPhase",
    )

    // A second, slower phase on a different period. Two incommensurate cycles keep the
    // composite from visibly looping, which a single phase always eventually does.
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 71_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "auroraDrift",
    )

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val radius = maxOf(w, h) * 0.62f

            val centres = listOf(
                Offset(
                    x = w * (0.28f + 0.16f * cos(phase)),
                    y = h * (0.20f + 0.10f * sin(phase * 0.8f)),
                ),
                Offset(
                    x = w * (0.76f + 0.14f * cos(drift + 1.4f)),
                    y = h * (0.42f + 0.14f * sin(drift)),
                ),
                Offset(
                    x = w * (0.44f + 0.18f * sin(phase * 0.6f + drift)),
                    y = h * (0.82f + 0.08f * cos(drift * 1.2f)),
                ),
            )

            centres.forEachIndexed { index, centre ->
                val colour = colors[index % colors.size]
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colour.copy(alpha = colour.alpha * intensity),
                            Color.Transparent,
                        ),
                        center = centre,
                        radius = radius,
                    ),
                    radius = radius,
                    center = centre,
                )
            }
        }

        content()
    }
}

/**
 * Aurora over the theme background colour.
 *
 * The base colour is drawn first so the blobs blend into an opaque surface rather than
 * whatever happens to sit behind the window.
 */
@Composable
fun AuroraScaffoldBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit,
) {
    // Read in composition, not inside the draw lambda — a DrawScope is not a composable
    // scope and cannot resolve a theme colour.
    val base = MaterialTheme.colorScheme.background

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawRect(color = base) }
        AuroraBackground(intensity = intensity, content = content)
    }
}
