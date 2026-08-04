package com.squeeze.app.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squeeze.app.R
import com.squeeze.app.ui.theme.LocalIsDarkTheme

/**
 * The Squeeze.fit mark: the real artwork, shipped as an asset.
 *
 * This was previously drawn from the path data in the brand HTML, which turned out to be a
 * crude stand-in rather than the logo — rendering it produced a blob with a head and a fan,
 * nothing like the flexing figure the brand actually uses. The logo in the design mockup is
 * a detailed illustration: a muscular figure with its fists pressed together at the chest
 * and blue energy radiating from the point of effort. Reproducing that in hand-written path
 * commands would be a poor imitation of it and impossible to maintain, so the artwork is
 * extracted from the mockup and shipped as a drawable instead.
 *
 * Two variants, because one is not enough. The navy figure is nearly invisible against the
 * dark theme's background, so the dark variant is the mockup's own white treatment — the
 * same one its app-icon tiles use on navy, blue and black.
 *
 * The variant is chosen from [LocalIsDarkTheme] rather than from a `-night` resource
 * qualifier, because the app has its own Light/Dark/System setting and a qualifier would
 * follow the system regardless of what the user picked.
 */
@Composable
fun SqueezeMark(
    size: Dp,
    modifier: Modifier = Modifier,
    onDark: Boolean = LocalIsDarkTheme.current,
) {
    Image(
        painter = painterResource(
            if (onDark) R.drawable.logo_squeeze_on_dark else R.drawable.logo_squeeze,
        ),
        // Decorative wherever it appears: the wordmark beside it already names the product,
        // and the app bar it sits in is not a control.
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

/**
 * Mark plus wordmark, for app bars.
 *
 * "squeeze" is set heavy against a blue ".fit", so the domain reads as a suffix rather than
 * as part of the word.
 */
@Composable
fun SqueezeWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 34.dp,
    fontSize: TextUnit = 18.sp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SqueezeMark(size = markSize)

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
 * The hero lockup: mark above the wordmark, tagline beneath.
 *
 * Stacked rather than side by side, matching the mockup. The tagline's wide tracking is from
 * the design and is what lets three short words hold the width of the wordmark above them.
 */
@Composable
fun SqueezeLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = 132.dp,
    showTagline: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SqueezeMark(size = markSize)

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

        if (showTagline) {
            Row {
                Text(
                    text = "SMALL STEPS. ",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "BIG CHANGE.",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
