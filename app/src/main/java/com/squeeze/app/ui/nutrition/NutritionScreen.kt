package com.squeeze.app.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.squeeze.app.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.squeeze.app.ui.components.entrance
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.squeeze.app.data.NutritionContext
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.HeroMetric
import com.squeeze.app.ui.components.NoticePill
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.components.SecondaryButton
import com.squeeze.app.ui.components.SectionHeader
import com.squeeze.app.ui.components.StatRow
import com.squeeze.app.ui.components.StatTile
import com.squeeze.core.model.Goal
import com.squeeze.core.nutrition.FoodGroup
import com.squeeze.core.nutrition.FoodLibrary
import com.squeeze.core.nutrition.Macros
import com.squeeze.core.nutrition.Meal
import com.squeeze.core.nutrition.MicroCoverage
import com.squeeze.core.nutrition.Micronutrient
import com.squeeze.core.text.fixed

/**
 * Nutrition, in three places instead of one long page:
 *
 *  - **Today** — the only numbers most people need: calories and macros for a training or rest
 *    day.
 *  - **Meals** — the week of meals and the favourite foods it is built from.
 *  - **Details** — why each number is what it is, micronutrients, and the links to change the
 *    inputs.
 *
 * Nothing is typed in here. Weight and body fat come from the scan, activity from the week of
 * sports, and the meals from the favourite foods.
 */
@Composable
fun NutritionScreen(
    onScan: () -> Unit,
    onOpenTraining: () -> Unit,
    onEditGoal: () -> Unit,
    nextStep: com.squeeze.core.coach.JourneyStep? = null,
    onNextStep: () -> Unit = {},
    onPlanChanged: () -> Unit = {},
    viewModel: NutritionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.context?.favourites) { onPlanChanged() }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val context = state.context
        when {
            state.loading -> Unit
            context == null -> NeedsScan(onScan)
            else -> {
                val plan = context.plan
                SectionHeader(
                    title = goalLabel(context.goal),
                    eyebrow = "Your plan",
                    caption = when {
                        plan.intendedKgPerWeek < -0.01 -> "Losing ${(-plan.intendedKgPerWeek).fixed(2)} kg a week"
                        plan.intendedKgPerWeek > 0.01 -> "Gaining ${plan.intendedKgPerWeek.fixed(2)} kg a week"
                        else -> "Holding weight while composition shifts"
                    },
                )
                nextStep?.let { com.squeeze.app.ui.components.NextStepBanner(it, onNextStep) }

                com.squeeze.app.ui.components.SegmentedControl(
                    options = listOf("Today", "Meals", "Details"),
                    selected = tab,
                    onSelect = { tab = it },
                )

                when (tab) {
                    0 -> TodayTab(context, state.showTrainingDay, viewModel::showTrainingDay)
                    1 -> {
                        FavouritesSection(state, viewModel)
                        WeekSection(context, state.selectedDay, viewModel::selectDay)
                    }
                    else -> DetailsTab(context, onScan, onOpenTraining, onEditGoal)
                }
            }
        }
    }
}

@Composable
private fun NeedsScan(onScan: () -> Unit) {
    SectionHeader(
        title = "Your nutrition plan",
        eyebrow = "Nutrition",
        caption = "Built from your own body, it starts with one scan.",
    )
    BrandCard(Modifier.fillMaxWidth()) {
        Text(
            "The scan asks your weight and reads your body fat from a photo. From those, your " +
                "goal and your training, every number here is worked out for you.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    PrimaryButton(text = "Start body scan", onClick = onScan)
}

private fun goalLabel(goal: Goal) = when (goal) {
    Goal.HYPERTROPHY -> "Build muscle"
    Goal.STRENGTH -> "Get stronger"
    Goal.CUT -> "Lose fat"
    Goal.RECOMP -> "Recomposition"
    Goal.MAKE_WEIGHT -> "Make weight"
}

@Composable
private fun TodayTab(context: NutritionContext, trainingDay: Boolean, onDayType: (Boolean) -> Unit) {
    val plan = context.plan
    val day = if (trainingDay) plan.trainingDay else plan.restDay

    com.squeeze.app.ui.components.SegmentedControl(
        options = listOf("Training day", "Rest day"),
        selected = if (trainingDay) 0 else 1,
        onSelect = { onDayType(it == 0) },
    )

    BrandCard(Modifier.fillMaxWidth().entrance(0)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The ring is the day's target against maintenance: full at maintenance, short
            // of it in a deficit, past it (capped) in a surplus.
            com.squeeze.app.ui.components.ProgressRing(
                progress = (day.calories.toFloat() / plan.maintenanceCalories.coerceAtLeast(1)).coerceIn(0f, 1f),
                size = 124.dp,
                stroke = 12.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    com.squeeze.app.ui.components.AnimatedNumber(
                        value = day.calories.toDouble(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text("kcal target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val total = (day.proteinG * 4f + day.carbsG * 4f + day.fatG * 9f).coerceAtLeast(1f)
                MacroLine("Protein", day.proteinG, day.proteinG * 4f / total, com.squeeze.app.ui.theme.Brand.IconBlueLight)
                MacroLine("Carbs", day.carbsG, day.carbsG * 4f / total, MaterialTheme.colorScheme.primary)
                MacroLine("Fat", day.fatG, day.fatG * 9f / total, com.squeeze.app.ui.theme.Brand.OutlineBlue)
            }
        }
        Text(
            "Maintenance ≈ %,d kcal".format(plan.maintenanceCalories),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Box(Modifier.height(6.dp))
        Text(
            "Water ${plan.waterLitres} L · Fibre ${plan.fiberG} g · Sodium ${"%,d".format(plan.sodiumMg)} mg",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (plan.adjustmentKcal != 0) {
        NoticePill(
            "Adjusted ${if (plan.adjustmentKcal > 0) "+" else ""}${plan.adjustmentKcal} kcal from your weight trend",
        )
    }
    plan.warnings.take(2).forEach { warning ->
        Text("• $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    // Today's meals, in the order they are eaten, the one around training marked.
    val today = java.time.LocalDate.now().dayOfWeek.value - 1
    plan.week.getOrNull(today)?.let { MealTimeline(it.meals) }

    val gaps = plan.micros.filter { it.short }
    gaps.firstOrNull()?.let { gap ->
        val amber = androidx.compose.ui.graphics.Color(0xFFF59E0B)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(amber.copy(alpha = 0.12f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${(gap.supplied / gap.target * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                color = if (com.squeeze.app.ui.theme.LocalIsDarkTheme.current) androidx.compose.ui.graphics.Color(0xFFF8B84A) else androidx.compose.ui.graphics.Color(0xFFB45309),
            )
            Text(
                "${gap.nutrient.label} is short this week" +
                    (if (gaps.size > 1) " (and ${gaps.size - 1} more)" else "") + ", see Details for the foods that fix it.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun MacroLine(label: String, grams: Int, share: Float, colour: androidx.compose.ui.graphics.Color) {
    val fill by androidx.compose.animation.core.animateFloatAsState(share, androidx.compose.animation.core.tween(900), label = "macro")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("$grams g", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outline),
        ) {
            Box(Modifier.fillMaxWidth(fill.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(colour))
        }
    }
}

/** A day's meals as a timeline: a dot per meal, the line joining them, cards beside. */
@Composable
private fun MealTimeline(meals: List<Meal>) {
    val line = MaterialTheme.colorScheme.outline
    Column {
        meals.forEachIndexed { i, meal ->
            val training = meal.name == "Around training"
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                Column(Modifier.width(22.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .padding(top = 18.dp)
                            .size(14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (training) MaterialTheme.colorScheme.primary else line),
                    )
                    if (i < meals.lastIndex) {
                        Box(Modifier.width(2.dp).weight(1f).background(line))
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 10.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (training) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                        )
                        .border(1.dp, if (training) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else line, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row {
                        Text(if (training) "Pre/post-workout" else meal.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            "${meal.macros.calories} kcal · ${meal.macros.proteinG} g P",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (training) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        meal.items.joinToString(" · ") { "${it.food} ${it.grams} g".replace(" (not counted)", "") },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsTab(
    context: NutritionContext,
    onScan: () -> Unit,
    onOpenTraining: () -> Unit,
    onEditGoal: () -> Unit,
) {
    val plan = context.plan
    SectionHeader(title = "Why these numbers", caption = "Each one traced back to your data")
    BrandCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            plan.reasoning.forEachIndexed { i, line ->
                Row {
                    Text(
                        "${i + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    SectionHeader(title = "Micronutrients", caption = "An average day of your meals against your targets")
    BrandCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            plan.micros.forEach { MicroRow(it) }
            plan.microAdvice.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }

    SectionHeader(title = "Change the plan", caption = "It follows these, update them and it updates")
    SecondaryButton(text = if (context.hasPhysique) "Scan check-in (weight + photo)" else "Body scan (weight + photo)", onClick = onScan)
    SecondaryButton(text = "Training · ${context.trainingDaysPerWeek} days a week", onClick = onOpenTraining)
    SecondaryButton(text = "Goal & deadline", onClick = onEditGoal)

    Text(
        "General guidance for healthy adults, not medical advice. If you have a medical " +
            "condition, are pregnant, or have a history of disordered eating, check with a " +
            "doctor or dietitian before changing how you eat.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Protein, carbs and fat as shares of the day's calories, in one bar. */
@Composable
private fun MacroBar(day: Macros) {
    val p = day.proteinG * 4f
    val c = day.carbsG * 4f
    val f = day.fatG * 9f
    val total = (p + c + f).coerceAtLeast(1f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp)),
    ) {
        Box(Modifier.weight((p / total).coerceAtLeast(0.001f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        Box(Modifier.weight((c / total).coerceAtLeast(0.001f)).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
        Box(Modifier.weight((f / total).coerceAtLeast(0.001f)).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
    }
    Text(
        "Protein ${(p / total * 100).toInt()}% · Carbs ${(c / total * 100).toInt()}% · Fat ${(f / total * 100).toInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun amount(nutrient: Micronutrient, value: Double): String = when {
    value >= 100 -> "%,d".format(value.toInt())
    value >= 10 -> value.fixed(0)
    else -> value.fixed(1)
} + " " + nutrient.unit

@Composable
private fun MicroRow(coverage: MicroCoverage) {
    val fraction = (coverage.supplied / coverage.target).toFloat().coerceIn(0f, 1f)
    val colour = if (coverage.short) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(coverage.nutrient.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${amount(coverage.nutrient, coverage.supplied)} / ${amount(coverage.nutrient, coverage.target)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(colour))
        }
    }
}

@Composable
private fun MealCard(meal: Meal) {
    BrandCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                meal.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${meal.macros.calories} kcal · ${meal.macros.proteinG}P ${meal.macros.carbsG}C ${meal.macros.fatG}F",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.height(6.dp))
        meal.items.forEach { item ->
            Row {
                Text(item.food, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${item.grams} g", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Favourite foods, grouped; the week of meals is built from these. */
@Composable
private fun FavouritesSection(state: NutritionUiState, viewModel: NutritionViewModel) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val open = editing || state.favourites.isEmpty() || state.favouritesDirty
    if (!open) {
        com.squeeze.app.ui.components.BrandRow(onClick = { editing = true }) {
            Column(Modifier.weight(1f)) {
                Text("Built from your ${state.favourites.size} favourite foods", style = MaterialTheme.typography.titleSmall)
                Text("Meals rotate through them across the week", style = MaterialTheme.typography.bodySmall)
            }
            Text("Edit ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    SectionHeader(
        title = "Your favourite foods",
        caption = "Tick what you like to eat, your meals are built from them",
    )
    BrandCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FoodGroup.entries.forEach { group ->
                val foods = FoodLibrary.ALL.filter { it.group == group }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { viewModel.toggleGroup(foods.map { it.name }) }) {
                        Text(if (foods.all { it.name in state.favourites }) "Clear" else "All")
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    foods.forEach { food ->
                        FilterChip(
                            selected = food.name in state.favourites,
                            onClick = { viewModel.toggleFood(food.name) },
                            label = { Text(food.name) },
                        )
                    }
                }
            }
        }
    }
    if (state.favouritesDirty) {
        PrimaryButton(
            text = "Build my meals from these (${state.favourites.size})",
            onClick = { viewModel.buildMeals(); editing = false },
        )
    }
}

/** The seven days of meals. */
@Composable
private fun WeekSection(context: NutritionContext, selected: Int, onSelect: (Int) -> Unit) {
    val week = context.plan.week
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        week.forEachIndexed { i, day ->
            FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(day.name.substringBefore(" ·")) })
        }
    }
    week.getOrNull(selected)?.let { day ->
        val m = day.macros
        Text(
            "${day.name.substringAfter("· ", "")} day · ${m.calories} kcal · ${m.proteinG} g protein · ${m.carbsG} g carbs · ${m.fatG} g fat",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MealTimeline(day.meals)
    }
}
