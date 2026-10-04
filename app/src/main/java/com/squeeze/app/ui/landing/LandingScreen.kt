package com.squeeze.app.ui.landing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.squeeze.app.ui.components.AuroraBackground
import com.squeeze.app.ui.components.ScanPulse
import com.squeeze.app.ui.components.entrance
import kotlinx.coroutines.delay
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.brand.FeatureGlyph
import com.squeeze.app.ui.brand.FeatureIcon
import com.squeeze.app.ui.brand.SqueezeLockup
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme

/** The brand sheet's four feature cards, in its order. */
private data class Feature(
    val glyph: FeatureGlyph,
    val title: String,
    val detail: String,
)

private val features = listOf(
    Feature(
        glyph = FeatureGlyph.SMART_SCAN,
        title = "Smart Scan",
        detail = "Quick body tracking from one clean flow.",
    ),
    Feature(
        glyph = FeatureGlyph.TRACK_TRENDS,
        title = "Track Trends",
        detail = "Clear progress insights over time.",
    ),
    Feature(
        glyph = FeatureGlyph.STAY_MOTIVATED,
        title = "Stay Motivated",
        detail = "Celebrate consistency and wins.",
    ),
    Feature(
        glyph = FeatureGlyph.PRIVACY_FIRST,
        title = "Privacy First",
        detail = "Your body data stays yours.",
    ),
)

/**
 * First-run landing screen, laid out as the brand sheet's hero panel.
 *
 * The mark settles in ahead of the copy, so the first thing on screen is the logo rather
 * than a paragraph.
 *
 * This is the only screen that gets to be loud. Everywhere else the data is the subject.
 */
@Composable
fun LandingScreen(onGetStarted: () -> Unit, onToggleTheme: () -> Unit = {}) {
    val markScale = remember { Animatable(0.82f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // The mark settles in first and the rest of the page follows it.
        markScale.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        contentAlpha.animateTo(1f, tween(600))
    }

    // What the app does, one line at a time — the headline moves, the page does not.
    val lines = listOf("Scan your body with AI.", "Train with a plan that adapts.", "Eat for your goal.", "Watch it all add up.")
    var line by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_600)
            line = (line + 1) % lines.size
        }
    }

    AuroraBackground(Modifier.fillMaxSize(), intense = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                com.squeeze.app.ui.components.ThemeToggle(onToggle = onToggleTheme)
            }
            SqueezeLockup(
                markSize = 84.dp,
                modifier = Modifier.scale(markScale.value),
            )

            ScanPulse(size = 200.dp, modifier = Modifier.padding(top = 8.dp).alpha(contentAlpha.value))

            AnimatedContent(
                targetState = line,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn(tween(400))) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut(tween(300)))
                },
                label = "headline",
            ) { i ->
                Text(
                    text = lines[i],
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = "Body composition, training and nutrition in one coach that " +
                    "reads your body on this phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = if (LocalIsDarkTheme.current) Brand.DarkSub else Brand.Body,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp).alpha(contentAlpha.value),
            )

            Spacer(Modifier.height(26.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                features.chunked(2).forEachIndexed { row, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        pair.forEachIndexed { col, f -> FeatureCard(f, index = 2 + row * 2 + col) }
                    }
                }
            }

            Spacer(Modifier.height(30.dp))

            Column(
                modifier = Modifier.fillMaxWidth().entrance(6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton(text = "Get started", onClick = onGetStarted)

                Text(
                    text = "Free · photos never leave your phone · optional Google backup",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.FeatureCard(feature: Feature, index: Int) {
    val shape = RoundedCornerShape(22.dp)
    val dark = LocalIsDarkTheme.current

    Column(
        modifier = Modifier
            .weight(1f)
            .entrance(index)
            .clip(shape)
            .background(if (dark) Brand.DarkCard.copy(alpha = 0.85f) else Brand.Card.copy(alpha = 0.9f))
            .border(1.dp, if (dark) Brand.DarkLine else Brand.Line, shape)
            .padding(horizontal = 14.dp, vertical = 18.dp),
    ) {
        FeatureIcon(
            glyph = feature.glyph,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = null,
        )

        Text(
            text = feature.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )

        Text(
            text = feature.detail,
            style = MaterialTheme.typography.bodySmall,
            color = if (dark) Brand.DarkMuted else Brand.Muted,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}
