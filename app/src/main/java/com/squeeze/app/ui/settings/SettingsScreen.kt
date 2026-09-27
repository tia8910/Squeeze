package com.squeeze.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.squeeze.app.notify.NotificationSettings
import com.squeeze.app.notify.Notifier
import com.squeeze.app.notify.ReminderScheduler
import com.squeeze.core.coach.ReminderKind
import com.squeeze.core.coach.ReminderSlot
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
import com.squeeze.app.ui.components.SecondaryButton
import com.squeeze.app.ui.components.SectionHeader
import com.squeeze.core.model.Goal
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
    goal: Goal,
    targetBodyFatPercent: Double?,
    targetWeightKg: Double?,
    targetEpochDay: Long?,
    onGoalChange: (Goal, Double?, Double?, Long?) -> Unit,
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

        GoalSection(
            goal = goal,
            targetBodyFatPercent = targetBodyFatPercent,
            targetWeightKg = targetWeightKg,
            targetEpochDay = targetEpochDay,
            onGoalChange = onGoalChange,
        )

        // Order: who you are, what the app connects to and tells you, how it looks and
        // sounds, then privacy. The scan-labelling tool is gone from here: it builds a
        // training set, which is not something a person tracking their body came to do.
        com.squeeze.app.health.ConnectedAppsSection()

        NotificationsSection()

        SectionHeader(
            eyebrow = "Look and sound",
            title = "App",
        )

        ThemeSection(themeMode = themeMode, onThemeModeChange = onThemeModeChange)

        SettingToggle(
            title = "Sound effects",
            description = "A short chime on save and capture. Silent when your phone is.",
            checked = soundEnabled,
            onCheckedChange = onSoundEnabledChange,
        )

        SettingToggle(
            title = "Background music",
            // Says it will not interrupt: this is the toggle that could take something away.
            description = "A looping track while the app is open. Never interrupts your own music.",
            checked = ambientEnabled,
            onCheckedChange = onAmbientEnabledChange,
        )

        SectionHeader(
            eyebrow = "On this device",
            title = "Privacy",
            caption = "Everything is stored encrypted on your phone. The app has no internet " +
                "permission, so nothing can leave it.",
        )

        SettingToggle(
            title = "Block screenshots",
            description = "Stops screenshots and hides the app in the recent-apps view.",
            checked = blockScreenshots,
            onCheckedChange = onBlockScreenshotsChange,
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

/**
 * Which reminders arrive, and when.
 *
 * Self-contained — it reads and writes [NotificationSettings] and re-arms the alarms itself —
 * because nothing else on this screen depends on it, and threading five more parameters
 * through [SettingsScreen] for a section that owns its own state would only add places to
 * get it wrong.
 */
@Composable
private fun NotificationsSection() {
    val context = LocalContext.current
    val settings = remember { NotificationSettings(context) }
    val enabled by settings.enabled.collectAsState()
    val morning by settings.morningMinutes.collectAsState()
    val evening by settings.eveningMinutes.collectAsState()
    var allowed by remember { mutableStateOf(Notifier.canPost(context)) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted && Notifier.canPost(context)
        if (allowed) ReminderScheduler.scheduleAll(context)
    }
    fun ensureAllowed() {
        if (Notifier.canPost(context)) {
            allowed = true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            settings.permissionAsked = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Below Android 13 there is no prompt; the only switch is in system settings.
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    SectionHeader(
        eyebrow = "Notifications",
        title = "Reminders",
        caption = "At most one in the morning and one in the evening — only when there's something to do.",
    )

    if (!allowed) {
        BrandCard(Modifier.fillMaxWidth()) {
            Text("Notifications are off", style = MaterialTheme.typography.titleSmall)
            Text(
                "Allow them to get these reminders.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(
                text = "Allow notifications",
                onClick = { ensureAllowed() },
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }

    listOf(
        Triple(ReminderKind.WORKOUT, "Workouts", "Today's session in the morning; a nudge at night if it's not logged."),
        Triple(ReminderKind.CHECK_IN, "Weekly scan", "A week after your last scan, with tips for a matching photo."),
        Triple(ReminderKind.STEPS, "Steps", "In the evening, when a short walk would reach your goal."),
        Triple(ReminderKind.WEEK_SUMMARY, "Sunday summary", "Sessions and sets done against the plan."),
    ).forEach { (kind, title, description) ->
        SettingToggle(
            title = title,
            description = description,
            checked = kind in enabled,
            onCheckedChange = { on ->
                settings.setEnabled(kind, on)
                if (on) ensureAllowed()
            },
        )
    }

    BrandCard(Modifier.fillMaxWidth()) {
        Text("Morning", style = MaterialTheme.typography.titleSmall)
        TimeChips(NotificationSettings.MORNING_CHOICES, morning) {
            settings.setMorningMinutes(it)
            ReminderScheduler.schedule(context, ReminderSlot.MORNING)
        }
        Text("Evening", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        TimeChips(NotificationSettings.EVENING_CHOICES, evening) {
            settings.setEveningMinutes(it)
            ReminderScheduler.schedule(context, ReminderSlot.EVENING)
        }
    }
}

@Composable
private fun TimeChips(choices: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
    ) {
        choices.forEach { minutes ->
            FilterChip(
                selected = minutes == selected,
                onClick = { onSelect(minutes) },
                label = { Text(NotificationSettings.format(minutes)) },
            )
        }
    }
}

