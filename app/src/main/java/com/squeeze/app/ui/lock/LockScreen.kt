package com.squeeze.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.brand.SqueezeMark
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.app.R

/**
 * Shown until biometric authentication succeeds.
 *
 * Renders no data of any kind, so nothing is visible in the recent-apps thumbnail even
 * before FLAG_SECURE is considered.
 */
@Composable
fun LockScreen(onAuthenticate: () -> Unit) {
    val dark = LocalIsDarkTheme.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The mark, on a soft blue disc.
            //
            // This screen showed two lines of type and a button in the middle of an empty
            // field, which is the layout of an error state. It is the first thing a returning
            // user sees every single time they open the app, so it is worth more than the
            // minimum: the disc gives the mark somewhere to sit, and the mark is the only
            // thing here that says which app is asking for a fingerprint.
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                (if (dark) Brand.DarkBlue else Brand.Blue).copy(alpha = 0.18f),
                                (if (dark) Brand.DarkBlue else Brand.Blue).copy(alpha = 0.04f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                SqueezeMark(size = 64.dp)
            }

            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(R.string.lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = if (dark) Brand.DarkMuted else Brand.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            PrimaryButton(
                text = stringResource(R.string.lock_prompt),
                onClick = onAuthenticate,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
