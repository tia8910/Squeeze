package com.squeeze.core.nutrition

import com.squeeze.core.model.Goal
import com.squeeze.core.model.Sex
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NutritionPlannerTest {

    private val base = NutritionInputs(
        sex = Sex.MALE,
        ageYears = 30,
        heightCm = 178.0,
        weightKg = 78.0,
        weightDaysOld = 1,
        bodyFatPercent = 16.0,
        goal = Goal.CUT,
        trainingDaysPerWeek = 4,
    )

    @Test
    fun `a cut eats below maintenance, a bulk above it`() {
        val cut = NutritionPlanner.plan(base)
        val bulk = NutritionPlanner.plan(base.copy(goal = Goal.HYPERTROPHY, bodyFatPercent = 12.0))

        assertTrue(cut.average.calories < cut.maintenanceCalories)
        assertTrue(cut.intendedKgPerWeek < 0)
        assertTrue(bulk.average.calories > bulk.maintenanceCalories)
        assertTrue(bulk.intendedKgPerWeek > 0)
    }

    @Test
    fun `resting burn comes from lean mass when body fat is known`() {
        val plan = NutritionPlanner.plan(base)
        val lean = 78.0 * 0.84
        assertEquals((370 + 21.6 * lean).toInt(), plan.bmr, 1.0)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Double) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, was $actual")

    @Test
    fun `the macros add up to the calories`() {
        val plan = NutritionPlanner.plan(base)
        val m = plan.average
        val fromMacros = m.proteinG * 4 + m.carbsG * 4 + m.fatG * 9
        assertTrue(abs(fromMacros - m.calories) <= 15, "$fromMacros vs ${m.calories}")
    }

    @Test
    fun `training days carry more carbohydrate and the week averages out`() {
        val plan = NutritionPlanner.plan(base)
        assertTrue(plan.trainingDay.carbsG > plan.restDay.carbsG)
        val weekly = plan.trainingDay.carbsG * 4 + plan.restDay.carbsG * 3
        assertTrue(abs(weekly - plan.average.carbsG * 7) <= 7)
    }

    @Test
    fun `a trend losing slower than planned lowers calories, within a cap`() {
        val plan = NutritionPlanner.plan(base)
        val stalled = NutritionPlanner.plan(base.copy(weightTrendKgPerWeek = 0.0, trendDays = 28))

        assertTrue(stalled.adjustmentKcal < 0)
        assertTrue(stalled.adjustmentKcal >= -300)
        assertTrue(stalled.average.calories < plan.average.calories)
    }

    @Test
    fun `a short trend is not trusted yet`() {
        val plan = NutritionPlanner.plan(base.copy(weightTrendKgPerWeek = 0.0, trendDays = 7))
        assertEquals(0, plan.adjustmentKcal)
    }

    @Test
    fun `an impossible deadline is clamped to a safe rate and flagged`() {
        val plan = NutritionPlanner.plan(
            base.copy(targetWeightKg = 60.0, daysToDeadline = 28),
        )
        assertTrue(plan.intendedKgPerWeek >= -0.01 * 78.0 - 1e-9)
        assertTrue(plan.warnings.any { "safe" in it })
    }

    @Test
    fun `calories never fall below the floor`() {
        val plan = NutritionPlanner.plan(
            base.copy(sex = Sex.FEMALE, weightKg = 48.0, heightCm = 150.0, bodyFatPercent = 30.0),
        )
        assertTrue(plan.average.calories >= 1_200)
    }

    @Test
    fun `without a body-fat reading it still plans, and says why it is less personal`() {
        val plan = NutritionPlanner.plan(base.copy(bodyFatPercent = null))
        assertTrue(plan.average.calories > 0)
        assertTrue(plan.warnings.any { "scan" in it })
    }

    @Test
    fun `the sample day lands near the training-day targets`() {
        val plan = NutritionPlanner.plan(base)
        val protein = plan.meals.sumOf { it.macros.proteinG }
        val calories = plan.meals.sumOf { it.macros.calories }
        assertTrue(abs(protein - plan.trainingDay.proteinG) <= plan.trainingDay.proteinG * 0.1)
        assertTrue(abs(calories - plan.trainingDay.calories) <= plan.trainingDay.calories * 0.1)
    }

    @Test
    fun `every tracked micronutrient has a target and a supply from the sample day`() {
        val plan = NutritionPlanner.plan(base)
        assertEquals(Micronutrient.entries.toSet(), plan.micros.map { it.nutrient }.toSet())
        assertTrue(plan.micros.all { it.target > 0 })
        // Oats, almonds and vegetables are in the day; magnesium and fibre cannot be zero.
        assertTrue(plan.micros.first { it.nutrient == Micronutrient.MAGNESIUM }.supplied > 100)
    }

    @Test
    fun `gaps are named with foods that close them`() {
        val plan = NutritionPlanner.plan(base)
        plan.micros.filter { it.short }.forEach { gap ->
            assertTrue(plan.microAdvice.any { gap.nutrient.label in it && gap.nutrient.fixes in it })
        }
    }

    @Test
    fun `women under fifty get the higher iron target`() {
        val man = MicroTargets.targets(Sex.MALE, 30, 4, 2500).getValue(Micronutrient.IRON)
        val woman = MicroTargets.targets(Sex.FEMALE, 30, 4, 2000).getValue(Micronutrient.IRON)
        assertTrue(woman > 2 * man)
    }

    @Test
    fun `training raises the sweat minerals`() {
        val rest = MicroTargets.targets(Sex.MALE, 30, 0, 2500)
        val hard = MicroTargets.targets(Sex.MALE, 30, 6, 2500)
        assertTrue(hard.getValue(Micronutrient.POTASSIUM) > rest.getValue(Micronutrient.POTASSIUM))
        assertTrue(MicroTargets.sodiumMg(6) > MicroTargets.sodiumMg(0))
    }
}
