package com.squeeze.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squeeze.app.BuildConfig
import com.squeeze.app.ui.brand.SqueezeMark
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.SegmentedControl
import com.squeeze.app.ui.components.entrance
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.app.ui.theme.ThemeMode
import com.squeeze.core.model.Goal
import com.squeeze.core.model.Sex

/**
 * You: who the plan is for, the backup, and a privacy centre that says in plain facts what
 * happens to the data — then appearance and sound.
 */
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
    goal: Goal,
    targetBodyFatPercent: Double?,
    targetWeightKg: Double?,
    targetEpochDay: Long?,
    onGoalChange: (Goal, Double?, Double?, Long?) -> Unit,
    @Suppress("UNUSED_PARAMETER") onLabelPhotos: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val muted = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Who this is: the goal and frame everything is calculated for.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.entrance(0)) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Brand.IconBlueLight, Brand.IconBlueDeep))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(goalTitle(goal), style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(heightCm?.let { "${it.toInt()} cm" }, birthYear?.let { "born $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        }

        ProfileSection(
            heightCm = heightCm,
            birthYear = birthYear,
            sex = sex,
            onProfileChange = onProfileChange,
        )

        GoalSection(
            goal = goal,
            targetBodyFatPercent = targetBodyFatPercent,
            targetWeightKg = targetWeightKg,
            targetEpochDay = targetEpochDay,
            onGoalChange = onGoalChange,
        )

        GroupLabel("GOOGLE DRIVE BACKUP")
        BrandCard(Modifier.fillMaxWidth()) {
            com.squeeze.app.ui.backup.GoogleBackupCard(compact = false)
        }

        // What happens to the data, as facts with a tick, then the one switch.
        GroupLabel("PRIVACY CENTRE")
        GroupCard {
            InfoRow(Icons.Rounded.PhoneAndroid, "Photos stay on this phone", "Analysed on-device and stored encrypted — never uploaded, not even to your backup.")
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            InfoRow(Icons.Rounded.Lock, "Encrypted database", "Everything is encrypted with a key kept in your phone's secure hardware.")
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            InfoRow(Icons.Rounded.CloudDone, "Network only for your backup", "The app connects only to your own Google Drive, and only after you sign in. No ads, no analytics.")
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            ToggleRow(
                icon = Icons.Rounded.Screenshot,
                title = "Block screenshots",
                detail = "Also hides the app's preview in recent apps.",
                checked = blockScreenshots,
                onCheckedChange = onBlockScreenshotsChange,
            )
        }

        GroupLabel("APPEARANCE")
        SegmentedControl(
            options = listOf("Light", "Dark", "System"),
            selected = when (themeMode) {
                ThemeMode.LIGHT -> 0
                ThemeMode.DARK -> 1
                ThemeMode.SYSTEM -> 2
            },
            onSelect = { onThemeModeChange(listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)[it]) },
        )

        GroupLabel("SOUND")
        GroupCard {
            ToggleRow(
                icon = Icons.Rounded.MusicNote,
                title = "Sound effects",
                detail = "Short cues on save and capture; silent on vibrate.",
                checked = soundEnabled,
                onCheckedChange = onSoundEnabledChange,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            ToggleRow(
                icon = Icons.Rounded.Headphones,
                title = "Background music",
                detail = "Generated on the phone; never starts over your own music.",
                checked = ambientEnabled,
                onCheckedChange = onAmbientEnabledChange,
            )
        }

        AboutCard()
    }
}

private fun goalTitle(goal: Goal) = when (goal) {
    Goal.HYPERTROPHY -> "Building muscle"
    Goal.STRENGTH -> "Getting stronger"
    Goal.CUT -> "Losing fat"
    Goal.RECOMP -> "Recomposition"
    Goal.MAKE_WEIGHT -> "Making weight"
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
    )
}

/** Rows sharing one card, separated by hairlines. */
@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    BrandCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) { content() }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Brand.Success, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
            text = "Build ${BuildConfig.VERSION_CODE} · the network is used only for Google " +
                "Drive backup, and only after you sign in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
