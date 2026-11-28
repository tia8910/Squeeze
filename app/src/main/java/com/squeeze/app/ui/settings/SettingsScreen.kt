package com.squeeze.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    pro: com.squeeze.app.billing.EntitlementState = com.squeeze.app.billing.EntitlementState(),
    onOpenPro: () -> Unit = {},
    onOpenAchievements: () -> Unit = {},
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

        if (pro.onSale) ProCard(pro, onOpenPro)

        // Achievements sit with the plan: they are about the user's own progress.
        GroupCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAchievements)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🏅", style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Achievements", style = MaterialTheme.typography.titleSmall)
                    Text("Badges for showing up, private to you", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Three groups, in the order people come here for them: what the plan is built on,
        // what happens to their data, and how the app looks and sounds.
        GroupLabel("YOUR PLAN")
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

        // What happens to the data, as facts with a tick, then the one switch.
        GroupLabel("PRIVACY AND DATA")
        GroupCard {
            InfoRow(Icons.Rounded.PhoneAndroid, "Photos stay on this phone", "Analysed on this phone and stored encrypted, never uploaded, not even to your backup.")
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

        BrandCard(Modifier.fillMaxWidth()) {
            if (pro.pro) {
                com.squeeze.app.ui.backup.GoogleBackupCard(compact = false)
            } else {
                Text("Automatic backup to your own Google Drive is part of Pro.", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.TextButton(onClick = onOpenPro) { Text("See Squeeze Pro") }
            }
        }

        GroupLabel("APP")
        SegmentedControl(
            options = listOf("Light", "Dark", "System"),
            selected = when (themeMode) {
                ThemeMode.LIGHT -> 0
                ThemeMode.DARK -> 1
                ThemeMode.SYSTEM -> 2
            },
            onSelect = { onThemeModeChange(listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)[it]) },
        )

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

        ReminderCard()

        AboutCard()
    }
}

/** Pro status, and the way to it or to managing it. */
@Composable
private fun ProCard(pro: com.squeeze.app.billing.EntitlementState, onOpenPro: () -> Unit) {
    val paid = pro.pro && !pro.debugUnlocked
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Brand.IconBlueDeep, Brand.BlueDeep, Brand.Navy)))
            .clickable(onClick = onOpenPro)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("SQUEEZE PRO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
            Text(
                when {
                    paid && pro.trialDaysLeft != null -> trialLine(pro.trialDaysLeft)
                    paid -> "Pro is active"
                    pro.debugUnlocked -> "Unlocked in this test build"
                    else -> "Try 7 days free"
                },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                if (pro.plan == com.squeeze.app.billing.Products.REVIEW_ACCESS) "Unlocked with an access code"
                else if (paid && pro.trialDaysLeft != null) "Then billed by Google Play. Cancel anytime before it ends."
                else if (paid) "Manage or cancel in Google Play" else "Training, nutrition, coaching and backup",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = Color.White)
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

/**
 * Training reminders and the weekly check-in. Reads and writes [com.squeeze.app.notify.Reminders]
 * directly, since the alarms live outside the app's view models anyway.
 */
@Composable
private fun ReminderCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val reminders = com.squeeze.app.notify.Reminders
    var on by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(reminders.enabled(context)) }
    var time by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(reminders.time(context)) }
    var allowed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(reminders.canPost(context)) }

    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        allowed = granted && reminders.canPost(context)
        if (!allowed) openNotificationSettings(context)
    }

    GroupCard {
        ToggleRow(
            icon = Icons.Rounded.Notifications,
            title = "Notifications",
            detail = when {
                on && !allowed -> "Notifications are off for Squeeze. Tap the switch to allow them."
                on -> "Choose which ones below. Never your body data on the lock screen."
                else -> "Training, meals, progress and comeback nudges."
            },
            checked = on && allowed,
            onCheckedChange = { want ->
                on = want
                reminders.setEnabled(context, want)
                if (want && !reminders.canPost(context)) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        reminders.markPermissionAsked(context)
                        ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                }
            },
        )
        if (on && allowed) {
            com.squeeze.app.notify.ReminderKind.entries.forEach { kind ->
                var kindOn by androidx.compose.runtime.remember(kind) {
                    androidx.compose.runtime.mutableStateOf(reminders.enabled(context, kind))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                KindRow(
                    title = kind.title,
                    detail = kind.detail,
                    checked = kindOn,
                    onCheckedChange = {
                        kindOn = it
                        reminders.setEnabled(context, kind, it)
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        android.app.TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                time = java.time.LocalTime.of(hour, minute)
                                reminders.setTime(context, time)
                            },
                            time.hour,
                            time.minute,
                            android.text.format.DateFormat.is24HourFormat(context),
                        ).show()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text("Training reminder time", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                Text("%d:%02d".format(time.hour, time.minute), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** One kind of notification, indented under the main switch. */
@Composable
private fun KindRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 50.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** The system page for this app's notifications, for when the permission was refused before. */
private fun openNotificationSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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

/** "Free trial, 3 days left", or the last-day wording. */
internal fun trialLine(daysLeft: Int): String = when (daysLeft) {
    0 -> "Free trial ends today"
    1 -> "Free trial, 1 day left"
    else -> "Free trial, $daysLeft days left"
}
