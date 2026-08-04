package com.squeeze.core.bodycomp

import com.squeeze.core.model.Goal
import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoalPlanTest {

    private val today = 20_000L

    private fun target(bodyFat: Double, inDays: Long) = GoalTarget(
        goal = Goal.CUT,
        targetBodyFatPercent = bodyFat,
        targetEpochDay = today + inDays,
    )

    private fun evaluate(
        targetBodyFat: Double,
        inDays: Long,
        current: Double,
        ratePerWeek: Double?,
        weightKg: Double? = 80.0,
    ) = GoalPlanner.evaluate(
        target = target(targetBodyFat, inDays),
        currentBodyFatPercent = current,
        currentWeightKg = weightKg,
        actualRatePerWeek = ratePerWeek,
        todayEpochDay = today,
        sex = Sex.MALE,
    )

    @Test
    fun `a rate that arrives on time is on track`() {
        // 20% to 15% in 12 weeks needs 0.42 points a week; doing 0.5.
        val progress = evaluate(15.0, inDays = 84, current = 20.0, ratePerWeek = -0.5)

        assertNotNull(progress)
        assertEquals(GoalVerdict.ON_TRACK, progress.verdict)
        assertTrue(progress.actions.isEmpty(), "on track needs no instructions")
    }

    @Test
    fun `a rate that falls short is behind, with the gap costed in calories`() {
        val progress = evaluate(15.0, inDays = 84, current = 20.0, ratePerWeek = -0.15)

        assertNotNull(progress)
        assertEquals(GoalVerdict.BEHIND, progress.verdict)
        assertTrue(progress.actions.isNotEmpty())
        assertTrue(
            progress.actions.any { it.contains("kcal a day") },
            "the point of the advice is a number the user can act on tonight: $progress",
        )
    }

    @Test
    fun `moving the wrong way is named as such rather than called behind`() {
        val progress = evaluate(15.0, inDays = 84, current = 20.0, ratePerWeek = 0.3)

        assertNotNull(progress)
        assertEquals(GoalVerdict.WRONG_DIRECTION, progress.verdict)
    }

    @Test
    fun `an impossible deadline is refused rather than divided into a plan`() {
        // 30% to 12% in three weeks. Arithmetic gives 6 points a week; a person cannot.
        val progress = evaluate(12.0, inDays = 21, current = 30.0, ratePerWeek = -0.5)

        assertNotNull(progress)
        assertEquals(GoalVerdict.UNREALISTIC, progress.verdict)
        assertTrue(
            progress.actions.any { it.contains("Move the date") },
            "the honest advice here is to change the plan, not to try harder",
        )
    }

    @Test
    fun `the safe ceiling is not so tight that ordinary cuts trip it`() {
        // 25% to 20% over 16 weeks is unremarkable and must not be called unrealistic.
        val progress = evaluate(20.0, inDays = 112, current = 25.0, ratePerWeek = -0.35)

        assertNotNull(progress)
        assertTrue(
            progress.verdict != GoalVerdict.UNREALISTIC,
            "a 0.31 points-per-week cut is routine, got ${progress.verdict}",
        )
    }

    @Test
    fun `gaining is held to a much slower ceiling than losing`() {
        // Muscle accrues far more slowly than fat comes off, so the same gap over the same
        // weeks is realistic downward and not upward.
        val losing = evaluate(15.0, inDays = 56, current = 20.0, ratePerWeek = -0.6)
        val gaining = GoalPlanner.evaluate(
            target = GoalTarget(
                goal = Goal.HYPERTROPHY,
                targetBodyFatPercent = 20.0,
                targetEpochDay = today + 56,
            ),
            currentBodyFatPercent = 15.0,
            currentWeightKg = 80.0,
            actualRatePerWeek = 0.6,
            todayEpochDay = today,
            sex = Sex.MALE,
        )

        assertNotNull(losing)
        assertNotNull(gaining)
        assertTrue(losing.verdict != GoalVerdict.UNREALISTIC)
        assertEquals(GoalVerdict.UNREALISTIC, gaining.verdict)
    }

    @Test
    fun `no trend yet says so instead of guessing a direction`() {
        val progress = evaluate(15.0, inDays = 84, current = 20.0, ratePerWeek = null)

        assertNotNull(progress)
        assertEquals(GoalVerdict.TOO_EARLY, progress.verdict)
        assertTrue(progress.actions.any { it.contains("Measure") })
    }

    @Test
    fun `a passed deadline is reported rather than projected past`() {
        val progress = evaluate(15.0, inDays = -10, current = 18.0, ratePerWeek = -0.4)

        assertNotNull(progress)
        assertTrue(progress.daysRemaining < 0)
        assertTrue(progress.headline.contains("passed"))
    }

    @Test
    fun `a goal with no body-fat target produces nothing rather than a fabricated one`() {
        val progress = GoalPlanner.evaluate(
            target = GoalTarget(goal = Goal.STRENGTH, targetEpochDay = today + 84),
            currentBodyFatPercent = 20.0,
            currentWeightKg = 80.0,
            actualRatePerWeek = -0.2,
            todayEpochDay = today,
            sex = Sex.MALE,
        )

        assertEquals(null, progress)
    }

    @Test
    fun `protein advice scales with the user rather than being a slogan`() {
        val heavy = evaluate(15.0, inDays = 84, current = 20.0, ratePerWeek = -0.1, weightKg = 100.0)
        val light = evaluate(15.0, inDays = 84, current = 20.0, ratePerWeek = -0.1, weightKg = 60.0)

        assertNotNull(heavy)
        assertNotNull(light)
        assertTrue(heavy.actions.any { it.contains("160 to 220g") }, "${heavy.actions}")
        assertTrue(light.actions.any { it.contains("96 to 132g") }, "${light.actions}")
    }
}
