package com.squeeze.app.ui.landing

import androidx.compose.animation.core.Animatable
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
 * The mark animates its own meaning on entry — the figure appears, then the squeeze bands
 * sweep across it — so the first second explains the name without a word of copy.
 *
 * This is the only screen that gets to be loud. Everywhere else the data is the subject.
 */
@Composable
fun LandingScreen(onGetStarted: () -> Unit) {
    val squeeze = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        squeeze.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        contentAlpha.animateTo(1f, tween(600))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))

        SqueezeLockup(markSize = 92.dp, squeeze = squeeze.value)

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Body composition built around controlled effort: squeeze, measure, " +
                "improve. Everything is worked out on this phone and stays there.",
            style = MaterialTheme.typography.bodyLarge,
            color = if (LocalIsDarkTheme.current) Brand.DarkSub else Brand.Body,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(contentAlpha.value),
        )

        Spacer(Modifier.height(30.dp))

        Column(
            modifier = Modifier.fillMaxWidth().alpha(contentAlpha.value),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Two rows of two. The sheet's four-across grid is a desktop layout; at phone
            // width its own breakpoint drops to two columns, which is what is used here.
            features.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { FeatureCard(it) }
                }
            }
        }

        Spacer(Modifier.height(36.dp))

        Column(
            modifier = Modifier.fillMaxWidth().alpha(contentAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrimaryButton(text = "Start tracking", onClick = onGetStarted)

            TextButton(onClick = onGetStarted) {
                Text(
                    text = "Free. No account. No sign-up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted,
                )
            }
        }
    }
}

@Composable
private fun RowScope.FeatureCard(feature: Feature) {
    val shape = RoundedCornerShape(22.dp)
    val dark = LocalIsDarkTheme.current

    Column(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(if (dark) Brand.DarkCard else Brand.Card)
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
