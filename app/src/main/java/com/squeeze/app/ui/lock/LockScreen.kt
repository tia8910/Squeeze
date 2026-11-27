package com.squeeze.app.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squeeze.app.ui.brand.SqueezeMark
import java.time.LocalTime

/** Where the unlock is: waiting for a touch, reading the fingerprint, or through. */
enum class LockStage { IDLE, SCANNING, DONE }

private val Night = Color(0xFF040A17)
private val Sky = Color(0xFF8DB3FF)
private val Mist = Color(0xFFC9D6F0)
private val Faint = Color(0xFF7F8FB0)
private val Mint = Color(0xFF9CF5C0)
private val Safe = Color(0xFF4ADE80)

/**
 * Shown until biometric authentication succeeds.
 *
 * Always dark, whatever the theme: it is the first thing a returning user sees every time, so
 * it is drawn as a moment rather than a form. Slow aurora light behind, the mark floating in
 * pulsing rings, a greeting for the time of day, one big fingerprint button that sweeps while
 * the system prompt is reading and turns green with a check on success.
 *
 * Renders no data of any kind, so nothing is visible in the recent-apps thumbnail even before
 * FLAG_SECURE is considered.
 */
@Composable
fun LockScreen(onAuthenticate: () -> Unit, stage: LockStage = LockStage.IDLE) {
    val loop = rememberInfiniteTransition(label = "lock")
    val drift by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
    val ring by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3_000, easing = LinearEasing)), label = "ring")
    val float by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    val twinkle by loop.animateFloat(0.15f, 0.8f, infiniteRepeatable(tween(1_500), RepeatMode.Reverse), label = "twinkle")

    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }

    val greeting = when (LocalTime.now().hour) {
        in 0..11 -> "GOOD MORNING"
        in 12..17 -> "GOOD AFTERNOON"
        else -> "GOOD EVENING"
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        // Two slow aurora clouds.
        Box(
            Modifier
                .offset(x = (-120 + 40 * drift).dp, y = (-80 + 60 * drift).dp)
                .size(360.dp)
                .blur(12.dp)
                .background(Brush.radialGradient(listOf(Color(0x8C1768FF), Color.Transparent)), CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (150 - 50 * drift).dp, y = (-80 + 40 * drift).dp)
                .size(380.dp)
                .blur(14.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0x6636A0FF), Color(0x2E7C3AED), Color.Transparent)),
                    CircleShape,
                ),
        )
        // A few stars.
        Canvas(Modifier.fillMaxSize()) {
            listOf(0.15f to 0.17f, 0.8f to 0.13f, 0.72f to 0.36f, 0.1f to 0.5f, 0.87f to 0.62f).forEachIndexed { i, (x, y) ->
                drawCircle(
                    Color(0xFFBFD4FF).copy(alpha = if (i % 2 == 0) twinkle else 0.95f - twinkle),
                    radius = 1.6.dp.toPx(),
                    center = Offset(size.width * x, size.height * y),
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().alpha(reveal.value), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = Safe, modifier = Modifier.size(13.dp))
                    Text(
                        "Encrypted on this phone",
                        color = Color(0xFFD6E2F7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            Spacer(Modifier.weight(0.6f))

            // The mark, floating inside two pulsing rings.
            Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    // Two rings half a cycle apart, each growing outward and fading.
                    listOf(ring, (ring + 0.5f) % 1f).forEach { t ->
                        drawCircle(
                            Sky.copy(alpha = 0.5f * (1f - t)),
                            radius = size.minDimension / 2f * (0.55f + 0.45f * t),
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                }
                Box(
                    Modifier
                        .offset(y = (-6 * float).dp)
                        .size(112.dp)
                        .clip(RoundedCornerShape(34.dp))
                        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(34.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    SqueezeMark(size = 72.dp, onDark = true)
                }
            }

            Column(Modifier.alpha(reveal.value).padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(greeting, color = Sky, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Text(
                    "Welcome back",
                    color = Color.White,
                    fontSize = 32.sp,
                    lineHeight = 37.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    if (stage == LockStage.DONE) "Opening your plan." else "Your scans, plan and progress are locked to you. Touch to open.",
                    color = Mist,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            FingerprintButton(stage = stage, onClick = onAuthenticate)
            Text(
                when (stage) {
                    LockStage.IDLE -> "Touch to unlock"
                    LockStage.SCANNING -> "Reading fingerprint"
                    LockStage.DONE -> "Unlocked"
                },
                color = if (stage == LockStage.DONE) Safe else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                "Locks again after 30 seconds away",
                color = Faint,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun FingerprintButton(stage: LockStage, onClick: () -> Unit) {
    val loop = rememberInfiniteTransition(label = "scan")
    val sweep by loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sweep")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin")
    val pulse by loop.animateFloat(1f, 1.06f, infiniteRepeatable(tween(1_200), RepeatMode.Reverse), label = "pulse")

    val pop = remember { Animatable(1f) }
    LaunchedEffect(stage) {
        if (stage == LockStage.DONE) {
            pop.snapTo(0.4f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f))
        }
    }

    val done = stage == LockStage.DONE
    Box(
        Modifier
            .scale(if (stage == LockStage.IDLE) pulse else 1f)
            .size(96.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    if (done) listOf(Color(0xFF22C55E), Color(0xFF15803D))
                    else listOf(Color(0xFF36A0FF), Color(0xFF1768FF), Color(0xFF0E4BD8)),
                ),
            )
            .clickable(enabled = stage == LockStage.IDLE, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (done) "Unlocked" else "Unlock with fingerprint" },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp).scale(pop.value))
        } else {
            Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = Color.White, modifier = Modifier.size(50.dp))
        }
        if (stage == LockStage.SCANNING) {
            Box(
                Modifier
                    .offset(y = (30 * sweep).dp)
                    .size(width = 66.dp, height = 3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Mint),
            )
            Canvas(Modifier.fillMaxSize().padding(4.dp).rotate(spin)) {
                drawArc(Mint, 0f, 80f, useCenter = false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}
