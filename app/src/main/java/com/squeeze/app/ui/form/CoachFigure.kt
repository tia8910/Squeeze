package com.squeeze.app.ui.form

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.squeeze.app.R

/** The Squeeze coach figure, from the three angles it was made in. */
enum class CoachView(@DrawableRes val photo: Int, @DrawableRes val ghost: Int) {
    FRONT(R.drawable.coach_front, R.drawable.coach_front_ghost),
    QUARTER(R.drawable.coach_quarter, R.drawable.coach_quarter_ghost),
    BACK(R.drawable.coach_back, R.drawable.coach_back_ghost),
}

/**
 * The coach figure as a see through guide over the camera: the pose to match.
 *
 * It floats gently so it reads as a guide rather than part of the picture, and brightens with a
 * green glow behind it once the body in front of the camera matches.
 *
 * @param mirrored flips the figure, for the side pose turned the other way
 */
@Composable
fun CoachGhost(view: CoachView, matched: Boolean, modifier: Modifier = Modifier, mirrored: Boolean = false) {
    val float = rememberInfiniteTransition(label = "coach float")
    val lift by float.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lift",
    )
    val glow by animateFloatAsState(if (matched) 1f else 0f, tween(400), label = "glow")
    val alpha by animateFloatAsState(if (matched) 0.85f else 0.55f, tween(400), label = "alpha")

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = glow }
                .background(Brush.radialGradient(listOf(Color(0x664ADE80), Color.Transparent))),
        )
        Image(
            painter = painterResource(view.ghost),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = lift
                    this.alpha = alpha
                    if (mirrored) scaleX = -1f
                },
        )
    }
}

/** The coach figure as a picture, on its own dark background, for cards and setup screens. */
@Composable
fun CoachPhoto(view: CoachView, modifier: Modifier = Modifier, mirrored: Boolean = false) {
    Image(
        painter = painterResource(view.photo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
        modifier = modifier.graphicsLayer { if (mirrored) scaleX = -1f },
    )
}
