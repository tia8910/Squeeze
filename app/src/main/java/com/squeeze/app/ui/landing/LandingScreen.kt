package com.squeeze.app.ui.landing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.brand.SqueezeLockup
import com.squeeze.app.ui.components.AuroraScaffoldBackground
import com.squeeze.app.ui.theme.Brand
import kotlinx.coroutines.delay

/** One rotating claim. Each is something the app actually does, not a slogan. */
private data class Pitch(val headline: String, val detail: String)

private val pitches = listOf(
    Pitch(
        headline = "Measures what's real",
        detail = "Separates genuine change from measurement noise, and stays quiet until " +
            "the data actually supports a verdict.",
    ),
    Pitch(
        headline = "Scans from two photos",
        detail = "Finds your waist, neck, hips and chest automatically — on this device, " +
            "with models that ship inside the app.",
    ),
    Pitch(
        headline = "Nothing leaves your phone",
        detail = "No internet permission at all. Not a promise in a policy — a permission " +
            "the system enforces and you can verify.",
    ),
    Pitch(
        headline = "Training that listens",
        detail = "Your programme adapts to what your composition is doing, not just to " +
            "what you lifted last week.",
    ),
)

/**
 * First-run landing screen.
 *
 * The logo animates its own meaning on entry — arcs closing to a waist, then the
 * measurement line landing — so the first two seconds explain the product without a word
 * of copy. Claims then rotate on a timer, each one something the app genuinely does.
 *
 * This is the only screen that gets to be loud. Everywhere else the data is the subject.
 */
@Composable
fun LandingScreen(onGetStarted: () -> Unit) {
    val markProgress = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    var pitchIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        // The squeeze happens first and alone; copy arrives once the gesture has landed.
        markProgress.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        contentAlpha.animateTo(1f, tween(600))
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4200)
            pitchIndex = (pitchIndex + 1) % pitches.size
        }
    }

    AuroraScaffoldBackground(intensity = 1f) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.9f))

            SqueezeLockup(markSize = 104.dp, progress = markProgress.value)

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Body composition, honestly measured.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(contentAlpha.value),
            )

            Spacer(Modifier.weight(0.7f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .alpha(contentAlpha.value),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = pitchIndex,
                    transitionSpec = {
                        (slideInVertically { it / 3 } + fadeIn(tween(420)))
                            .togetherWith(slideOutVertically { -it / 3 } + fadeOut(tween(220)))
                    },
                    label = "pitch",
                ) { index ->
                    val pitch = pitches[index]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = pitch.headline,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = pitch.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(contentAlpha.value),
            ) {
                pitches.indices.forEach { index ->
                    Surface(
                        shape = CircleShape,
                        color = if (index == pitchIndex) {
                            Brand.Teal
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                        },
                        modifier = Modifier.size(if (index == pitchIndex) 8.dp else 6.dp),
                    ) {}
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth().alpha(contentAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.Teal),
                ) {
                    Text("Start tracking", style = MaterialTheme.typography.titleMedium)
                }

                TextButton(onClick = onGetStarted) {
                    Text(
                        text = "Free. No account. No sign-up.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
