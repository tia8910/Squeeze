package com.squeeze.core.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AchievementsTest {

    private fun byId(list: List<Achievement>, id: String) = list.first { it.id == id }

    @Test
    fun `a new user has every badge locked and a first step to aim for`() {
        val list = Achievements.all(AchievementInputs())
        assertTrue(list.none { it.earned })
        assertEquals(15, list.size)
        assertNull(Achievements.latest(list))
    }

    @Test
    fun `counts are earned on the day the count was reached`() {
        val list = Achievements.all(AchievementInputs(scanDays = listOf(100, 107, 114, 121, 128, 135)))
        assertEquals(100L, byId(list, "first_scan").earnedOn)
        assertEquals(128L, byId(list, "scans_5").earnedOn)
        val ten = byId(list, "scans_10")
        assertNull(ten.earnedOn)
        assertEquals(6, ten.progress)
    }

    @Test
    fun `four consecutive training weeks earn the streak, a gap resets it`() {
        // Weeks 20, 21, 22, 23 by 7-day blocks: days 140, 147, 154, 161.
        val list = Achievements.all(AchievementInputs(workoutDays = listOf(140, 147, 150, 154, 161)))
        assertEquals(161L, byId(list, "streak_4").earnedOn)

        val gap = Achievements.all(AchievementInputs(workoutDays = listOf(140, 147, 161, 168)))
        assertNull(byId(gap, "streak_4").earnedOn)
    }

    @Test
    fun `waist badges use the first reading as the start`() {
        val list = Achievements.all(
            AchievementInputs(waistByDay = listOf(10L to 90.0, 20L to 88.5, 30L to 87.9, 40L to 84.8)),
        )
        assertEquals(30L, byId(list, "waist_2").earnedOn)
        assertEquals(40L, byId(list, "waist_5").earnedOn)
    }

    @Test
    fun `the latest earned badge leads`() {
        val list = Achievements.all(AchievementInputs(scanDays = listOf(100), workoutDays = listOf(105)))
        assertEquals("first_workout", Achievements.latest(list)?.id)
    }
}
