package com.squeeze.core.coach

import com.squeeze.core.model.Goal
import com.squeeze.core.nutrition.Macros
import com.squeeze.core.nutrition.MealBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TipsTest {

    @Test
    fun `a night trainer is told about caffeine on a training day`() {
        val tips = CoachTips.today(TipContext(Goal.HYPERTROPHY, TrainingTime.NIGHT, trainingToday = true, epochDay = 1))
        assertTrue(tips.first().contains("caffeine"))
    }

    @Test
    fun `the goal tip rotates day to day`() {
        val a = CoachTips.today(TipContext(Goal.CUT, TrainingTime.MORNING, false, epochDay = 1)).last()
        val b = CoachTips.today(TipContext(Goal.CUT, TrainingTime.MORNING, false, epochDay = 2)).last()
        assertNotEquals(a, b)
    }

    @Test
    fun `weak points and gaps are named`() {
        val tips = CoachTips.today(
            TipContext(Goal.RECOMP, TrainingTime.EVENING, true, weakPoints = listOf("Shoulders"), epochDay = 3),
        )
        assertTrue(tips.any { "shoulders" in it })
    }

    @Test
    fun `the around-training meal moves with the training time`() {
        val day = MealBuilder.build(Macros(2500, 180, 280, 70))
        assertEquals("Around training", MealBuilder.order(day, TrainingTime.EARLY_MORNING).first().name)
        assertEquals("Around training", MealBuilder.order(day, TrainingTime.NIGHT).last().name)
        assertEquals(day.map { it.name }, MealBuilder.order(day, null).map { it.name })
    }
}
