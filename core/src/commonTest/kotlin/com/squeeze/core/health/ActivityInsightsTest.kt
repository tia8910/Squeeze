package com.squeeze.core.health

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityInsightsTest {

    @Test
    fun `the steps gap is said as a walk, rounded to five minutes`() {
        assertEquals(40, ActivityInsights.walkMinutesToGoal(4_200))
        assertEquals(0, ActivityInsights.walkMinutesToGoal(9_000))
        assertTrue(ActivityInsights.stepsLine(4_200).contains("40-minute walk"))
        assertTrue(ActivityInsights.stepsLine(8_000).contains("reached"))
    }

    @Test
    fun `a short auto-detected walk is not a workout, a real session is`() {
        assertFalse(ActivityInsights.watchWorkoutDone(DailyActivity(0, exerciseMinutes = 8)))
        assertTrue(ActivityInsights.watchWorkoutDone(DailyActivity(0, exerciseMinutes = 45)))
        assertFalse(ActivityInsights.watchWorkoutDone(null))
    }

    @Test
    fun `no food log means no intake card, not zero eaten`() {
        assertNull(ActivityInsights.intake(DailyActivity(0, steps = 3000), 2800, 150))
        val p = assertNotNull(ActivityInsights.intake(DailyActivity(0, eatenKcal = 1900.4, proteinG = 110.0), 2810, 147))
        assertEquals(910, p.remainingKcal)
        assertEquals("910 kcal left · 37 g protein to go", ActivityInsights.intakeLine(p))
    }

    @Test
    fun `recovery speaks only when something stands out`() {
        assertNull(ActivityInsights.recoveryNote(DailyActivity(0, sleepMinutes = 450, restingHeartRate = 58), 57))
        assertNotNull(ActivityInsights.recoveryNote(DailyActivity(0, sleepMinutes = 320), 57))
        val both = assertNotNull(ActivityInsights.recoveryNote(DailyActivity(0, sleepMinutes = 320, restingHeartRate = 66), 58))
        assertTrue(both.contains("8 bpm"))
    }

    @Test
    fun `a resting baseline needs enough days`() {
        assertNull(ActivityInsights.baseline(listOf(60, 58, null, 59)))
        assertEquals(59, ActivityInsights.baseline(listOf(60, 58, 59, 61, 57, null)))
    }
}
