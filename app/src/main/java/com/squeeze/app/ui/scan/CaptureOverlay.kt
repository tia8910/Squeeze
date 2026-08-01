package com.squeeze.app.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Framing guide drawn over the camera preview.
 *
 * The single largest source of error in this method is the user standing too close or
 * getting cut off, because height is what converts pixels into centimetres — a body that
 * runs off the top of the frame does not merely measure less, it mis-scales everything.
 * Telling someone that in a paragraph does not work; showing them a box to stand inside
 * does.
 *
 * The guide leaves a margin at top and bottom deliberately: fitting inside it means the
 * whole body is in shot with room to spare, which is also far enough back for perspective
 * distortion to stay small.
 */
@Composable
fun CaptureGuideOverlay(
    modifier: Modifier = Modifier,
    guideColor: Color = Color.White.copy(alpha = 0.55f),
) {
    Canvas(modifier.fillMaxSize()) {
        val marginY = size.height * VERTICAL_MARGIN_FRACTION
        val frameHeight = size.height - marginY * 2f
        val frameWidth = size.width * FRAME_WIDTH_FRACTION
        val left = (size.width - frameWidth) / 2f

        val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))

        drawRoundRect(
            color = guideColor,
            topLeft = Offset(left, marginY),
            size = Size(frameWidth, frameHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f),
            style = Stroke(width = 2.dp.toPx(), pathEffect = dash),
        )

        // Head and feet rules: the two extremes the scale depends on, so they are called
        // out separately rather than left implicit in the box.
        listOf(marginY, marginY + frameHeight).forEach { y ->
            drawLine(
                color = guideColor,
                start = Offset(left, y),
                end = Offset(left + frameWidth, y),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

/** Large countdown numeral shown while the self-timer runs. */
@Composable
fun CountdownOverlay(secondsRemaining: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = secondsRemaining.toString(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

private const val VERTICAL_MARGIN_FRACTION = 0.06f
private const val FRAME_WIDTH_FRACTION = 0.62f
