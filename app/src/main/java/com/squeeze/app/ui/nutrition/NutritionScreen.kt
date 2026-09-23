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
import androidx.compose.material3.FilterChip
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Today", "Meals", "Details").forEachIndexed { i, label ->
                        FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label) })
                    }
                }

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
        caption = "Built from your own body — it starts with one scan.",
    )
    BrandCard(Modifier.fillMaxWidth()) {
        Text(
            "The scan asks your weight and reads your body fat from a photo. From those, your " +
                "goal and your training, every number here is worked out for you.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    PrimaryButton(text = "Start AI scan", onClick = onScan)
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

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = trainingDay, onClick = { onDayType(true) }, label = { Text("Training day") })
        FilterChip(selected = !trainingDay, onClick = { onDayType(false) }, label = { Text("Rest day") })
    }

    BrandCard(Modifier.fillMaxWidth()) {
        HeroMetric(
            value = "%,d".format(day.calories),
            unit = "kcal",
            label = if (trainingDay) "On training days" else "On rest days",
            band = "Maintenance ≈ %,d kcal".format(plan.maintenanceCalories),
        )
        Box(Modifier.height(12.dp))
        MacroBar(day)
        Box(Modifier.height(12.dp))
        StatRow {
            StatTile("${day.proteinG} g", "Protein", Modifier.weight(1f), tinted = true)
            StatTile("${day.carbsG} g", "Carbs", Modifier.weight(1f))
            StatTile("${day.fatG} g", "Fat", Modifier.weight(1f))
        }
        Box(Modifier.height(10.dp))
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
    val gaps = plan.micros.filter { it.short }
    if (gaps.isNotEmpty()) {
        Text(
            "Watch this week: ${gaps.joinToString { it.nutrient.label.lowercase() }} — see Details.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

    SectionHeader(title = "Change the plan", caption = "It follows these — update them and it updates")
    SecondaryButton(text = if (context.hasPhysique) "Scan check-in (weight + photo)" else "AI scan (weight + photo)", onClick = onScan)
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
        caption = "Tick what you like to eat — your meals are built from them",
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
        day.meals.forEach { MealCard(it) }
    }
}
