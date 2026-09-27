package com.squeeze.app.ui.training

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import com.squeeze.core.program.BlockOverview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.squeeze.core.model.Goal
import com.squeeze.core.model.TrainingAge
import com.squeeze.core.program.Equipment
import com.squeeze.core.program.WeakPoint
import com.squeeze.core.program.WeakPointAnalysis
import com.squeeze.core.program.Session
import com.squeeze.app.data.VolumeRow
import com.squeeze.core.workout.Discipline
import com.squeeze.core.workout.HybridWeek
import com.squeeze.core.workout.PlannedSession
import com.squeeze.core.workout.PlannedDay
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.components.SecondaryButton
import com.squeeze.app.ui.components.SectionHeader
import androidx.compose.runtime.setValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.width
import com.squeeze.core.program.TrainingWeek

@Composable
fun TrainingScreen(
    viewModel: TrainingViewModel = hiltViewModel(),
    onOpenNutrition: () -> Unit = {},
    onLog: () -> Unit = {},
    onScanMachine: () -> Unit = {},
    onProgress: () -> Unit = {},
    nextStep: com.squeeze.core.coach.JourneyStep? = null,
    onNextStep: () -> Unit = {},
    onPlanChanged: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A new week changes nutrition, the dashboard and the next step.
    androidx.compose.runtime.LaunchedEffect(state.week) { onPlanChanged() }
    // Back from the log: this week's volume has changed.
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshWeek() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.profileMissing) {
            InfoCard(
                title = "Set your profile first",
                body = "Training volume is scaled by your training age and goal, both set in " +
                    "Settings.",
            )
            return@Column
        }

        var editing by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        val week = state.week

        if (week == null || editing) {
            // ── Setup: the only thing on screen until there is a week ──────────────────
            SectionHeader(
                title = if (week == null) "Build your week" else "Edit your week",
                eyebrow = "Train",
                caption = "Pick your sports and days — the plan does the rest",
            )
            BrandCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SportsSection(state, viewModel)
                    SetupSection(state, viewModel)
                    Text(
                        "Goal: ${state.goal.label()} — change it in You.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PrimaryButton(
                text = if (week == null) "Create my week" else "Save changes",
                onClick = { viewModel.createWeek(); editing = false },
            )
            if (week != null) {
                androidx.compose.material3.TextButton(onClick = { editing = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        } else {
            // ── The week: today first, then the days, then everything optional ─────────
            nextStep?.let { com.squeeze.app.ui.components.NextStepBanner(it, onNextStep) }

            val todayIndex = java.time.LocalDate.now().dayOfWeek.value - 1
            TodayCard(week.days[todayIndex], onStart = { session -> viewModel.startLog(session); onLog() })

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SectionHeader(
                    title = "This week",
                    caption = week.disciplines.joinToString(" · ") { it.label } + " · ${week.trainingDays} days",
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(onClick = { editing = true }) { Text("Edit") }
            }
            WeekList(week, todayIndex, onLogSession = { session -> viewModel.startLog(session); onLog() })

            if (state.volume.isNotEmpty()) VolumeCard(state.volume)

            com.squeeze.app.ui.components.BrandRow(onClick = onProgress) {
                Column(Modifier.weight(1f)) {
                    Text("Your progress", style = MaterialTheme.typography.titleSmall)
                    Text("Progressive overload on every lift · workout summaries", style = MaterialTheme.typography.bodySmall)
                }
                Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(text = "Log a workout", onClick = onLog, modifier = Modifier.weight(1f))
                SecondaryButton(text = "Scan a machine", onClick = onScanMachine, modifier = Modifier.weight(1f))
            }

            Expandable("How your week was built") {
                week.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                state.plannedKcalPerDay?.let {
                    Text("Burns about $it kcal a day on average — already in your nutrition plan.", style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.TextButton(onClick = onOpenNutrition) { Text("Open nutrition ›") }
            }
        }

        // The optional multi-week gym block, folded away: most people never need it, and
        // the week above already covers the gym days.
        if (week != null && !editing && Discipline.GYM in state.disciplines) {
            Expandable("Advanced: 6-week gym progression block") {
                Text(
                    "Volume climbs week to week, then a deload — built from the same goal and weak points.",
                    style = MaterialTheme.typography.bodySmall,
                )
                androidx.compose.material3.OutlinedButton(onClick = viewModel::generate, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.mesocycle == null) "Generate gym block" else "Regenerate gym block")
                }
                state.adjustmentRationale?.let { InfoCard(title = "Adjusted from your measurements", body = it) }
                if (state.weakPoints.isNotEmpty()) WeakPointCard(state.weakPoints)
                MesocycleView(state, viewModel)
            }
        }
    }
}

@Composable
private fun MesocycleView(state: TrainingUiState, viewModel: TrainingViewModel) {
    val mesocycle = state.mesocycle ?: return
    val summaries = BlockOverview.weeks(mesocycle)
    val selected = state.selectedWeek.coerceIn(0, (mesocycle.weeks.size - 1).coerceAtLeast(0))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(mesocycle.name, style = MaterialTheme.typography.titleLarge)

        // The block's shape in one picture: how much work each week holds, rising to the
        // peak and dropping for the deload. The chips it replaces said "Week 1 … Deload"
        // and nothing about what changes between them. Each bar is also the week picker.
        WeekStrip(summaries, selected, onSelect = viewModel::selectWeek)

        summaries.getOrNull(selected)?.let { summary ->
            mesocycle.weeks.getOrNull(selected)?.let { week ->
                WeekDetail(week, summary, weeks = summaries.size)
            }
        }

        if (mesocycle.notes.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("How to run this", style = MaterialTheme.typography.titleSmall)
                    mesocycle.notes.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** One bar per week, height by total sets; tap to open that week. */
@Composable
private fun WeekStrip(
    weeks: List<BlockOverview.WeekSummary>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val most = weeks.maxOfOrNull { it.totalSets }?.coerceAtLeast(1) ?: 1
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Bottom,
    ) {
        weeks.forEachIndexed { index, week ->
            val isSelected = index == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "${week.totalSets}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .width(22.dp)
                        .height((56f * week.totalSets / most).coerceAtLeast(6f).dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .background(if (isSelected) accent else accent.copy(alpha = if (week.isDeload) 0.18f else 0.35f)),
                )
                Text(
                    if (week.isDeload) "Deload" else "Wk ${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Text(
        "Hard sets per week. Volume builds, then the deload lets it pay off.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SetupSection(state: TrainingUiState, viewModel: TrainingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Days per week", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..7).forEach { days ->
                FilterChip(
                    selected = state.daysPerWeek == days,
                    onClick = { viewModel.setDaysPerWeek(days) },
                    label = { Text("$days") },
                )
            }
        }

        Text("Experience", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrainingAge.entries.forEach { age ->
                FilterChip(
                    selected = state.trainingAge == age,
                    onClick = { viewModel.setTrainingAge(age) },
                    label = { Text(age.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        if (Discipline.GYM in state.disciplines) {
            Text("Equipment", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Equipment.entries.forEach { equipment ->
                    FilterChip(
                        selected = equipment in state.equipment,
                        onClick = { viewModel.toggleEquipment(equipment) },
                        label = {
                            Text(equipment.name.lowercase().replaceFirstChar { it.uppercase() })
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekDetail(week: TrainingWeek, summary: BlockOverview.WeekSummary, weeks: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The week in one card: where it sits in the block, how much it holds, and how hard
        // to push — said once, in words, instead of "@ 3 RIR" repeated on every row.
        BrandCard(Modifier.fillMaxWidth()) {
            Text(
                if (summary.isDeload) "Deload · week ${summary.weekIndex + 1} of $weeks" else "Week ${summary.weekIndex + 1} of $weeks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${summary.sessions} sessions · ${summary.totalSets} hard sets",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summary.rir?.let {
                Text(
                    BlockOverview.effortSentence(it),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (summary.isDeload) {
                Text(
                    "Volume drops to maintenance and effort backs off. This is scheduled, " +
                        "not earned: waiting until you feel you need one costs two weeks of progress.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        week.sessions.forEachIndexed { index, session ->
            SessionCard(session, dayNumber = index + 1, weekRir = summary.rir)
        }
    }
}

/**
 * One gym day, organised the way it is run: what it trains and how long it takes, then the
 * heavy lifts to do first and fresh, then the accessories.
 */
@Composable
private fun SessionCard(session: Session, dayNumber: Int, weekRir: Int?) {
    val summary = BlockOverview.session(session)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "DAY $dayNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(session.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text("~${summary.minutes} min", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${session.totalSets} sets",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                summary.focus.joinToString(" · ") { BlockOverview.label(it) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            var number = 0
            if (summary.main.isNotEmpty()) {
                ExerciseGroupHeader("Main lifts", "Heavy — do these first, while fresh")
                summary.main.forEach { ExerciseRow(++number, it, weekRir) }
            }
            if (summary.accessory.isNotEmpty()) {
                ExerciseGroupHeader("Accessories", "Lighter — control the weight, chase the pump")
                summary.accessory.forEach { ExerciseRow(++number, it, weekRir) }
            }
        }
    }
}

@Composable
private fun ExerciseGroupHeader(title: String, caption: String) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExerciseRow(number: Int, prescription: com.squeeze.core.program.SetPrescription, weekRir: Int?) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            "$number",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(prescription.exerciseName, style = MaterialTheme.typography.bodyMedium)
            Text(
                BlockOverview.label(prescription.muscleGroup) +
                    // Only when it differs from the week's: a number repeated on every row
                    // is a number nobody reads.
                    if (weekRir == null || prescription.targetRir != weekRir) " · stop ${prescription.targetRir} short" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Sets × reps. The load is deliberately absent: the programme prescribes proximity to
        // failure and the lifter picks the weight, which is safer and more accurate than a
        // percentage table.
        Text(
            "${prescription.sets} × ${prescription.repRangeLow}–${prescription.repRangeHigh}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun Goal.label(): String = when (this) {
    Goal.HYPERTROPHY -> "Muscle"
    Goal.STRENGTH -> "Strength"
    Goal.CUT -> "Cut"
    Goal.RECOMP -> "Recomp"
    Goal.MAKE_WEIGHT -> "Make weight"
}

/**
 * What the scan says is lagging, and what the programme did about it.
 *
 * Every ratio behind this comes from a single photograph at a single scale, so the scale
 * error that troubles the absolute centimetres divides out — which is what makes it
 * defensible to prescribe from measurements that may themselves be a few per cent off.
 *
 * Framed as a training decision rather than a judgement. The user did not ask to be graded
 * against an ideal physique, and the useful content is which of their own parts is furthest
 * from where the rest of their body sits.
 */
@Composable
private fun WeakPointCard(weakPoints: List<WeakPoint>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Prioritised from your measurements",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "These are proportions, not absolutes — the parts furthest from where " +
                    "the rest of your body sits. The block below gives the first " +
                    "${WeakPointAnalysis.MAX_PRIORITIES} of them extra sets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            weakPoints.forEachIndexed { index, point ->
                Column(Modifier.padding(top = 10.dp)) {
                    Text(
                        text = point.group.name.lowercase().replaceFirstChar { it.uppercase() } +
                            if (index < WeakPointAnalysis.MAX_PRIORITIES) " · prioritised" else "",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = point.finding,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = point.prescription,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SportsSection(state: TrainingUiState, viewModel: TrainingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your sports — pick one or combine several", style = MaterialTheme.typography.titleSmall)
        Discipline.entries.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { d ->
                    FilterChip(
                        selected = d in state.disciplines,
                        onClick = { viewModel.toggleDiscipline(d) },
                        label = { Text(d.label) },
                    )
                }
            }
        }
    }
}

/** Today's sessions, with one button to start and log. */
@Composable
private fun TodayCard(day: PlannedDay, onStart: (PlannedSession) -> Unit) {
    BrandCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            if (day.rest) {
                Text("Rest day", style = MaterialTheme.typography.titleLarge)
                Text("Recovery is where training turns into results. Walk, stretch, sleep well.", style = MaterialTheme.typography.bodySmall)
            } else {
                day.sessions.forEach { session ->
                    Text(session.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${session.minutes} min · ${session.intensity.label.lowercase()}" +
                            session.items.firstOrNull()?.let { " · starts with ${it.name}" }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    PrimaryButton(text = "Start & log", onClick = { onStart(session) })
                }
            }
        }
    }
}

/** The week as one line per day; tap a day to see its sessions. */
@Composable
private fun WeekList(week: HybridWeek, todayIndex: Int, onLogSession: (PlannedSession) -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(-1) }
    BrandCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            week.days.forEach { day ->
                com.squeeze.app.ui.components.BrandRow(onClick = { open = if (open == day.index) -1 else day.index }) {
                    Text(
                        day.name.take(3),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (day.index == todayIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (day.index == todayIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(44.dp),
                    )
                    Text(
                        if (day.rest) "Rest" else day.sessions.joinToString(" + ") { it.title },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (day.rest) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (!day.rest) Text(if (open == day.index) "▲" else "▼", style = MaterialTheme.typography.labelSmall)
                }
                if (open == day.index) {
                    Column(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)) {
                        day.sessions.forEach { PlannedSessionView(it, onLogSession) }
                    }
                }
            }
        }
    }
}

/** A titled section that stays folded until asked for. */
@Composable
private fun Expandable(title: String, content: @Composable () -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    com.squeeze.app.ui.components.BrandRow(onClick = { open = !open }) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(if (open) "▲" else "▼", style = MaterialTheme.typography.labelSmall)
    }
    if (open) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun PlannedSessionView(session: PlannedSession, onLog: (PlannedSession) -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    session.title + if (session.addOn) " · add-on" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "${session.minutes} min · ${session.intensity.label.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            androidx.compose.material3.TextButton(onClick = { open = !open }) { Text(if (open) "Hide" else "Details") }
        }
        if (open) {
            Text(session.why, style = MaterialTheme.typography.bodySmall)
            session.items.forEach { item ->
                Column(Modifier.padding(start = 8.dp)) {
                    Text(
                        item.name + if (item.weakPoint) "  ★ weak point" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(item.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = { onLog(session) }, modifier = Modifier.fillMaxWidth()) { Text("Log this session") }
        }
    }
}

@Composable
private fun VolumeCard(rows: List<VolumeRow>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("This week's sets", style = MaterialTheme.typography.titleSmall)
            Text(
                "Logged since Monday against your plan. ★ marks the AI scan's weak points.",
                style = MaterialTheme.typography.bodySmall,
            )
            rows.forEach { row ->
                val target = row.planned.coerceAtLeast(1)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        (if (row.weakPoint) "★ " else "") + row.group.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(110.dp),
                    )
                    LinearProgressIndicator(
                        progress = { (row.done.toFloat() / target).coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${row.done}/${row.planned}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
