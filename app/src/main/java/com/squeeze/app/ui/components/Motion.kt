package com.squeeze.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The app's motion vocabulary, kept small so it reads as one product:
 *
 *  - **Aurora** — slow drifting light behind hero surfaces; ambient, never in front of data.
 *  - **Entrance** — content rises in, staggered, the first time a screen shows it.
 *  - **Press** — anything tappable gives a little under the finger.
 *  - **Count-up / ring** — numbers and progress arrive by moving to their value, so a result
 *    reads as computed rather than pasted.
 *  - **Scan pulse** — the one illustration that says "AI is looking at your body".
 *
 * Every loop is long and low-contrast. Motion that competes with the numbers is noise.
 */

/** Slowly drifting blue light, for heroes and onboarding. Draw content on top. */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    intense: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val dark = LocalIsDarkTheme.current
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(19_000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val ground = if (dark) Brand.DarkGround else Brand.Ground
    val glow = if (dark) Brand.DarkBlue else Brand.Blue
    val alpha = when {
        intense && dark -> 0.42f
        intense -> 0.30f
        dark -> 0.24f
        else -> 0.16f
    }
    Box(
        modifier
            .background(ground)
            .drawBehindAurora(a, b, glow, alpha),
        content = content,
    )
}

private fun Modifier.drawBehindAurora(a: Float, b: Float, glow: Color, alpha: Float) = drawBehind {
    val w = size.width
    val h = size.height
    val r = maxOf(w, h) * 0.7f
    val first = Offset(w * (0.15f + 0.5f * a), h * (0.12f + 0.2f * b))
    val second = Offset(w * (0.9f - 0.4f * b), h * (0.55f + 0.3f * a))
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = alpha), Color.Transparent), center = first, radius = r), radius = r, center = first)
    drawCircle(
        Brush.radialGradient(listOf(Brand.IconBlueLight.copy(alpha = alpha * 0.8f), Color.Transparent), center = second, radius = r * 0.8f),
        radius = r * 0.8f,
        center = second,
    )
}

/**
 * Rises and fades in once, [index] steps after the screen appears — for lists of cards, so
 * they land one after another instead of all at once.
 */
fun Modifier.entrance(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60L * index.coerceAtMost(8))
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

/** Gives a little under the finger. Pass the same source the clickable uses. */
fun Modifier.pressScale(source: MutableInteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A number that counts up to [value] when it first appears or changes. */
@Composable
fun AnimatedNumber(
    value: Double,
    decimals: Int = 0,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    color: Color = Color.Unspecified,
    prefix: String = "",
    suffix: String = "",
    modifier: Modifier = Modifier,
) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(value) { shown.animateTo(value.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
    val text = if (decimals == 0) {
        "%,d".format(shown.value.roundToInt())
    } else {
        "%.${decimals}f".format(shown.value)
    }
    Text(prefix + text + suffix, style = style, color = color, fontWeight = FontWeight.Bold, modifier = modifier)
}

/**
 * Progress as a ring that sweeps to its value. [content] sits in the middle — usually the
 * number the ring stands for.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    stroke: Dp = 8.dp,
    track: Color? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val dark = LocalIsDarkTheme.current
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(progress) { sweep.animateTo(progress.coerceIn(0f, 1f), tween(1_000, easing = FastOutSlowInEasing)) }
    val trackColour = track ?: if (dark) Brand.DarkLine else Brand.Line
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val px = stroke.toPx()
            val arcSize = Size(this.size.width - px, this.size.height - px)
            val topLeft = Offset(px / 2, px / 2)
            drawArc(trackColour, -90f, 360f, false, topLeft, arcSize, style = Stroke(px, cap = StrokeCap.Round))
            drawArc(
                Brush.sweepGradient(listOf(Brand.IconBlueLight, Brand.Blue, Brand.BlueDeep, Brand.IconBlueLight)),
                -90f, 360f * sweep.value, false, topLeft, arcSize,
                style = Stroke(px, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/** Page position as dots, the current one stretched into a pill. */
@Composable
fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val dark = LocalIsDarkTheme.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 7.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "dot")
            Box(
                Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == current) MaterialTheme.colorScheme.primary else if (dark) Brand.DarkLine else Brand.Line),
            )
        }
    }
}

/**
 * An outlined body with rings pulsing out from it and a scan line sweeping down — the picture
 * of "the AI is reading your physique", for onboarding and empty states.
 */
@Composable
fun ScanPulse(modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val dark = LocalIsDarkTheme.current
    val t = rememberInfiniteTransition(label = "scan")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing)), label = "ring")
    val line by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "line")
    val blue = if (dark) Brand.DarkBlue else Brand.Blue
    val ink = if (dark) Brand.DarkInk else Brand.Navy
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2, h / 2)
        // Rings, two out of phase.
        listOf(ring, (ring + 0.5f) % 1f).forEach { p ->
            drawCircle(blue.copy(alpha = (1f - p) * 0.35f), radius = w * (0.22f + 0.28f * p), center = c, style = Stroke(2.dp.toPx()))
        }
        drawCircle(Brush.radialGradient(listOf(blue.copy(alpha = 0.18f), Color.Transparent), center = c, radius = w * 0.45f), radius = w * 0.45f, center = c)
        // A simple front-pose figure.
        val body = Path().apply {
            val s = w / 200f
            fun x(v: Float) = c.x + v * s
            fun y(v: Float) = c.y + v * s
            moveTo(x(-10f), y(-48f))
            cubicTo(x(-24f), y(-46f), x(-38f), y(-40f), x(-40f), y(-26f))
            lineTo(x(-46f), y(18f))
            lineTo(x(-36f), y(18f))
            lineTo(x(-30f), y(-14f))
            lineTo(x(-24f), y(20f))
            lineTo(x(-26f), y(70f))
            lineTo(x(-8f), y(70f))
            lineTo(x(0f), y(28f))
            lineTo(x(8f), y(70f))
            lineTo(x(26f), y(70f))
            lineTo(x(24f), y(20f))
            lineTo(x(30f), y(-14f))
            lineTo(x(36f), y(18f))
            lineTo(x(46f), y(18f))
            lineTo(x(40f), y(-26f))
            cubicTo(x(38f), y(-40f), x(24f), y(-46f), x(10f), y(-48f))
            close()
        }
        drawPath(body, ink.copy(alpha = 0.08f))
        drawPath(body, ink.copy(alpha = 0.55f), style = Stroke(2.dp.toPx()))
        drawCircle(ink.copy(alpha = 0.55f), radius = w * 0.07f, center = Offset(c.x, c.y - w * 0.32f), style = Stroke(2.dp.toPx()))
        // Scan line with a soft tail.
        val top = c.y - w * 0.42f
        val bottom = c.y + w * 0.38f
        val ly = top + (bottom - top) * line
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, blue.copy(alpha = 0.28f)), startY = ly - 36.dp.toPx(), endY = ly),
            topLeft = Offset(c.x - w * 0.3f, ly - 36.dp.toPx()),
            size = Size(w * 0.6f, 36.dp.toPx()),
        )
        drawLine(blue, Offset(c.x - w * 0.32f, ly), Offset(c.x + w * 0.32f, ly), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        // Landmark points the "AI" has found, lighting up as the line passes.
        listOf(-0.2f to -0.2f, 0.2f to -0.2f, -0.12f to 0.08f, 0.12f to 0.08f, -0.22f to 0.09f, 0.22f to 0.09f).forEach { (dx, dy) ->
            val p = Offset(c.x + dx * w, c.y + dy * w)
            val lit = ly > p.y
            drawCircle(if (lit) blue else ink.copy(alpha = 0.25f), radius = if (lit) 4.dp.toPx() else 3.dp.toPx(), center = p)
        }
    }
}

/** A small orbiting accent, for "working" states that deserve more than a spinner. */
@Composable
fun OrbitLoader(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val t = rememberInfiniteTransition(label = "orbit")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "angle")
    val blue = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2 - 4.dp.toPx()
        val c = center
        drawCircle(blue.copy(alpha = 0.15f), radius = r, center = c, style = Stroke(3.dp.toPx()))
        repeat(3) { i ->
            val a = (angle + i * 120f) * PI.toFloat() / 180f
            drawCircle(blue.copy(alpha = 1f - i * 0.3f), radius = (4 - i).dp.toPx(), center = Offset(c.x + r * cos(a), c.y + r * sin(a)))
        }
    }
}

/**
 * Light / dark switch: a sun or moon that turns as it swaps. Reads the current rendering, so
 * a user on "follow system" flips to the opposite of what they see.
 */
@Composable
fun ThemeToggle(onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalIsDarkTheme.current
    val turn by animateFloatAsState(if (dark) 180f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "turn")
    val source = remember { MutableInteractionSource() }
    androidx.compose.material3.IconButton(
        onClick = onToggle,
        interactionSource = source,
        modifier = modifier
            .pressScale(source)
            .clip(RoundedCornerShape(50))
            .background(if (dark) Brand.DarkCard.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.85f)),
    ) {
        androidx.compose.material3.Icon(
            if (dark) androidx.compose.material.icons.Icons.Rounded.LightMode else androidx.compose.material.icons.Icons.Rounded.DarkMode,
            contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer { rotationZ = turn },
        )
    }
}
