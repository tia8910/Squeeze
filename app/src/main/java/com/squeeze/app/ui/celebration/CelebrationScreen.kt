package com.squeeze.app.ui.celebration

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.squeeze.app.audio.LocalSoundEngine
import com.squeeze.core.audio.Cue
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.components.SecondaryButton
import com.squeeze.app.ui.components.Sparkline
import com.squeeze.app.ui.components.StatRow
import com.squeeze.app.ui.components.StatTile
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme

/**
 * Shown once a measurement lands.
 *
 * The thing being celebrated is consistency, not a number going down. That is deliberate:
 * body fat can rise during a deliberate gaining phase, and an app that only cheers when the
 * number falls teaches the user that one direction is failure. What is always worth
 * reinforcing is that they measured at all, because measurement frequency is the input the
 * trend engine actually needs.
 */
@Composable
fun CelebrationScreen(
    bodyFatPercent: Double?,
    entries: Int,
    daysTracked: Long,
    trend: List<Double>,
    onViewProgress: () -> Unit,
    /** The journey's next step; when there is one, it is the main button. */
    nextStep: com.squeeze.core.coach.JourneyStep? = null,
    onNextStep: () -> Unit = {},
    /** Set for free users: Pro becomes the main next step after a saved scan. */
    onOpenPro: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val sound = LocalSoundEngine.current
    val contentAlpha = remember { Animatable(0f) }
    val markScale = remember { Animatable(0.7f) }

    LaunchedEffect(Unit) {
        // Fired alongside the mark's entrance rather than on a delay, so the arpeggio and
        // the squeeze land together and read as one event.
        sound?.play(Cue.CELEBRATION)
    }

    LaunchedEffect(Unit) {
        // The mark lands first, then the copy arrives behind it — the same order as the
        // landing screen, so the entrance reads as the app's signature rather than as an
        // effect that happens to be on two screens.
        markScale.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        contentAlpha.animateTo(1f, tween(420))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 50.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SavedRing(progress = contentAlpha.value, modifier = Modifier.scale(markScale.value))

        Column(
            modifier = Modifier.alpha(contentAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Scan saved",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 10.dp),
            )
            val next = java.time.LocalDate.now().plusDays(7)
            Text(
                text = (if (entries <= 1) "Your starting point is set. " else "Another check in on the record. ") +
                    "Next check in on " +
                    next.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM")) + ".",
                style = MaterialTheme.typography.bodyMedium,
                color = if (LocalIsDarkTheme.current) Brand.DarkSub else Brand.Sub,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(4.dp))

        StatRow(Modifier.alpha(contentAlpha.value)) {
            StatTile(
                value = daysTracked.toString(),
                label = if (daysTracked == 1L) "Day tracked" else "Days tracked",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = entries.toString(),
                label = if (entries == 1) "Entry" else "Entries",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = bodyFatPercent?.let { "%.1f%%".format(it) } ?: "--",
                label = "Body fat",
                modifier = Modifier.weight(1f),
            )
        }

        // The weekly check-in reminder, switched here where it makes the most sense.
        var remind by remember {
            mutableStateOf(
                com.squeeze.app.notify.Reminders.enabled(context) &&
                    com.squeeze.app.notify.Reminders.enabled(context, com.squeeze.app.notify.ReminderKind.TRAINING),
            )
        }
        BrandCard(Modifier.fillMaxWidth().alpha(contentAlpha.value)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Remind me next week", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(
                    checked = remind,
                    onCheckedChange = { on ->
                        remind = on
                        if (on) com.squeeze.app.notify.Reminders.setEnabled(context, true)
                        com.squeeze.app.notify.Reminders.setEnabled(context, com.squeeze.app.notify.ReminderKind.TRAINING, on)
                    },
                )
            }
        }

        BrandCard(
            Modifier
                .fillMaxWidth()
                .alpha(contentAlpha.value),
        ) {
            Text(
                text = "Consistency is everything.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Keep going, you're in control.",
                style = MaterialTheme.typography.bodySmall,
                color = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted,
                modifier = Modifier.padding(top = 4.dp),
            )

            // One reading is a dot, not a trend. Reserving the chart's height for it left a
            // tall band of empty card that reads as a rendering fault — the same defect the
            // home screen had, which was fixed there and missed here.
            if (trend.size >= 2) {
                Sparkline(
                    values = trend,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        Column(
            modifier = Modifier.alpha(contentAlpha.value),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onOpenPro != null) {
                // Free users: the scan is saved, and Pro is what turns it into a plan.
                com.squeeze.app.ui.pro.ProTeaserCard(
                    headline = "Your scan is saved",
                    detail = "Squeeze Pro turns it into a training week and meals for your goal.",
                    onOpenPro = onOpenPro,
                )
                androidx.compose.material3.TextButton(onClick = onViewProgress) { androidx.compose.material3.Text("View progress") }
            } else if (nextStep != null) {
                // The domino: finishing one step hands straight to the next.
                PrimaryButton(text = "Next: ${nextStep.title}", onClick = onNextStep)
                androidx.compose.material3.TextButton(onClick = onViewProgress) { androidx.compose.material3.Text("View progress") }
            } else {
                PrimaryButton(text = "View progress", onClick = onViewProgress)
            }
            SecondaryButton(
                text = "Share",
                onClick = { shareProgress(context, bodyFatPercent, entries, daysTracked) },
            )
        }
    }
}

/** A green ring that fills as the screen arrives, with a check in the middle. */
@Composable
private fun SavedRing(progress: Float, modifier: Modifier = Modifier) {
    val track = if (LocalIsDarkTheme.current) Brand.DarkLine else Brand.Line
    val green = Color(0xFF16A34A)
    Box(modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(120.dp)) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, useCenter = false, style = stroke)
            drawArc(green, -90f, 360f * progress, useCenter = false, style = stroke)
        }
        Text("✓", color = green, fontSize = 44.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
    }
}

/**
 * Hands a plain-text summary to the system share sheet.
 *
 * This is the only way anything leaves the app, and it stays consistent with the app holding
 * no INTERNET permission: the text is passed to whichever app the user picks, by the user, in
 * an explicit gesture. Nothing is transmitted by Squeeze itself, and nothing is attached
 * beyond the three figures shown on this screen — no photographs, no measurement history.
 */
private fun shareProgress(
    context: Context,
    bodyFatPercent: Double?,
    entries: Int,
    daysTracked: Long,
) {
    val summary = buildString {
        append("My Squeeze.fit progress\n\n")
        bodyFatPercent?.let { append("Body fat: %.1f%%\n".format(it)) }
        append("Entries: $entries\n")
        append("Days tracked: $daysTracked")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, summary)
    }

    context.startActivity(Intent.createChooser(intent, "Share progress"))
}
