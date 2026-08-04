package com.squeeze.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.squeeze.app.BuildConfig
import com.squeeze.app.ui.brand.SqueezeMark
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.theme.ThemeMode
import com.squeeze.core.model.Sex

@Composable
fun SettingsScreen(
    blockScreenshots: Boolean,
    onBlockScreenshotsChange: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    soundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    ambientEnabled: Boolean,
    onAmbientEnabledChange: (Boolean) -> Unit,
    heightCm: Double?,
    birthYear: Int?,
    sex: Sex?,
    onProfileChange: (Double?, Int?, Sex?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileSection(
            heightCm = heightCm,
            birthYear = birthYear,
            sex = sex,
            onProfileChange = onProfileChange,
        )

        Text("Appearance", style = MaterialTheme.typography.titleMedium)

        ThemeSection(themeMode = themeMode, onThemeModeChange = onThemeModeChange)

        Text("Sound", style = MaterialTheme.typography.titleMedium)

        SettingToggle(
            title = "Sound effects",
            description = "A short chime when a measurement saves, when a photo is captured, " +
                "and on the celebration screen. These play over your music rather than " +
                "interrupting it, and stay silent when your phone is on silent or vibrate.",
            checked = soundEnabled,
            onCheckedChange = onSoundEnabledChange,
        )

        SettingToggle(
            title = "Motivational background music",
            // Stated plainly because this is the toggle that can take something away from
            // the user. Someone who already has a playlist running needs to know why this
            // one is different before they turn it on, not after.
            description = "A slow, looping backing track while the app is open. It will not " +
                "start if something else is already playing, and it stops as soon as another " +
                "app wants the audio. Off by default so it never interrupts your own music.",
            checked = ambientEnabled,
            onCheckedChange = onAmbientEnabledChange,
        )

        Text(
            text = "All sound is generated on the device as it plays. No audio files are " +
                "bundled, downloaded or streamed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Privacy", style = MaterialTheme.typography.titleMedium)

        SettingToggle(
            title = "Block screenshots",
            // Stated concretely rather than as a vague privacy promise, because the
            // recents thumbnail is the part users do not know about and the part that
            // actually leaks: they never chose to create it.
            description = "Prevents screenshots and screen recording, and hides the app's " +
                "contents in the recent-apps switcher. Turn this on if you do not want a " +
                "preview of this app visible when switching between apps.",
            checked = blockScreenshots,
            onCheckedChange = onBlockScreenshotsChange,
        )

        Text(
            text = "Your measurements never leave this device. This setting only controls " +
                "what other apps on your phone can capture from the screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AboutCard()
    }
}

/**
 * Light, dark or follow the system.
 *
 * An explicit choice is offered rather than only tracking the system because this app is
 * used in gyms and bathrooms at 6am — the places where a phone's automatic theme is least
 * likely to match what the user actually wants to look at.
 */
@Composable
private fun ThemeSection(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    BrandCard(Modifier.fillMaxWidth()) {
        Text("Theme", style = MaterialTheme.typography.titleSmall)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    label = {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AboutCard() {
    BrandCard(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SqueezeMark(size = 28.dp)
            Text(
                text = "Squeeze.fit ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(
            text = "Build ${BuildConfig.VERSION_CODE} · no internet permission — verify it " +
                "under App info › Permissions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    BrandCard(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
