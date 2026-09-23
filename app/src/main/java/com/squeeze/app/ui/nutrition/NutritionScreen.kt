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
 * The nutrition plan, built from the user's own measurements, goal and training.
 *
 * Nothing on this page is entered here. Each number says where it came from, and each source
 * is one tap away: the weight log, the AI scan, the training block, the goal — so changing any
 * of them is how the plan is changed.
 */
@Composable
fun NutritionScreen(
    onLogWeight: () -> Unit,
    onScan: () -> Unit,
    onOpenTraining: () -> Unit,
    onEditGoal: () -> Unit,
    viewModel: NutritionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val context = state.context
        when {
            state.loading -> Unit
            context == null -> NeedsWeight(onLogWeight)
            else -> {
                Plan(context, state.showTrainingDay, viewModel::showTrainingDay, onLogWeight, onScan, onOpenTraining, onEditGoal)
                FavouritesSection(state, viewModel)
                WeekSection(context, state.selectedDay, viewModel::selectDay)
                SourcesSection(context, onLogWeight, onScan, onOpenTraining, onEditGoal)
            }
        }
    }
}

@Composable
private fun NeedsWeight(onLogWeight: () -> Unit) {
    SectionHeader(
        title = "Your nutrition plan",
        caption = "Built from your weight, body fat, goal and training — log a weight to start.",
    )
    BrandCard(Modifier.fillMaxWidth()) {
        Text(
            "Every number here is worked out from your own body. The one thing it cannot " +
                "guess is your weight: log it once and the plan appears, then follows your " +
                "trend from there.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    PrimaryButton(text = "Log your weight", onClick = onLogWeight)
}

private fun goalLabel(goal: Goal) = when (goal) {
    Goal.HYPERTROPHY -> "Build muscle"
    Goal.STRENGTH -> "Get stronger"
    Goal.CUT -> "Lose fat"
    Goal.RECOMP -> "Recomposition"
    Goal.MAKE_WEIGHT -> "Make weight"
}

@Composable
private fun Plan(
    context: NutritionContext,
    trainingDay: Boolean,
    onDayType: (Boolean) -> Unit,
    onLogWeight: () -> Unit,
    onScan: () -> Unit,
    onOpenTraining: () -> Unit,
    onEditGoal: () -> Unit,
) {
    val plan = context.plan
    val day = if (trainingDay) plan.trainingDay else plan.restDay

    SectionHeader(
        title = "Your nutrition plan",
        eyebrow = goalLabel(context.goal),
        caption = when {
            plan.intendedKgPerWeek < -0.01 -> "Losing ${(-plan.intendedKgPerWeek).fixed(2)} kg a week"
            plan.intendedKgPerWeek > 0.01 -> "Gaining ${plan.intendedKgPerWeek.fixed(2)} kg a week"
            else -> "Holding weight while composition shifts"
        },
    )

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
        StatRow {
            StatTile("${plan.fiberG} g", "Fibre", Modifier.weight(1f))
            StatTile("${plan.waterLitres} L", "Water", Modifier.weight(1f))
            StatTile("%,d mg".format(plan.sodiumMg), "Sodium", Modifier.weight(1f))
        }
    }

    if (plan.adjustmentKcal != 0) {
        NoticePill(
            "Adjusted ${if (plan.adjustmentKcal > 0) "+" else ""}${plan.adjustmentKcal} kcal from your weight trend",
        )
    }

    plan.warnings.forEach { warning ->
        BrandCard(Modifier.fillMaxWidth()) {
            Text(warning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }

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

    SectionHeader(
        title = "Micronutrients",
        caption = "What an average day of your week of meals supplies against your daily targets",
    )
    BrandCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            plan.micros.forEach { MicroRow(it) }
        }
    }
    if (plan.microAdvice.isNotEmpty()) {
        BrandCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plan.microAdvice.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }

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
    SectionHeader(
        title = "Your favourite foods",
        caption = "Tick what you like to eat — your week of meals is built from them",
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
        PrimaryButton(text = "Build my meals from these (${state.favourites.size})", onClick = viewModel::buildMeals)
    }
}

/** The seven days of meals. */
@Composable
private fun WeekSection(context: NutritionContext, selected: Int, onSelect: (Int) -> Unit) {
    val week = context.plan.week
    SectionHeader(
        title = "Your week of meals",
        caption = if (context.favourites.isEmpty()) {
            "Built from staple foods — tick favourites above to make it yours"
        } else {
            "Built from your favourites, rotated so the days differ"
        },
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        week.forEachIndexed { i, day ->
            FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(day.name) })
        }
    }
    week.getOrNull(selected)?.let { day ->
        val m = day.macros
        Text(
            "${m.calories} kcal · ${m.proteinG} g protein · ${m.carbsG} g carbs · ${m.fatG} g fat",
            style = MaterialTheme.typography.bodyMedium,
        )
        day.meals.forEach { MealCard(it) }
    }
}

/** The links back to everything the plan is built from. */
@Composable
private fun SourcesSection(
    context: NutritionContext,
    onLogWeight: () -> Unit,
    onScan: () -> Unit,
    onOpenTraining: () -> Unit,
    onEditGoal: () -> Unit,
) {
    SectionHeader(title = "Change the plan", caption = "It follows these — update them and it updates")
    PrimaryButton(text = "Log today's weight", onClick = onLogWeight)
    SecondaryButton(
        text = if (context.hasPhysique) "Rescan with the AI" else "Scan with the AI for body fat & weak points",
        onClick = onScan,
    )
    SecondaryButton(text = "Training: ${context.trainingDaysPerWeek} days a week — your sports & log", onClick = onOpenTraining)
    SecondaryButton(text = "Goal & deadline", onClick = onEditGoal)
}
