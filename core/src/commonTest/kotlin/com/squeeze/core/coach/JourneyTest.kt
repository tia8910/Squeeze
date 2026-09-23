package com.squeeze.core.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JourneyTest {

    private val fresh = JourneyFacts(null, hasWeek = false, hasFavourites = false, todaysSession = null, loggedToday = false)

    @Test
    fun `setup runs in order, one domino at a time`() {
        assertEquals(StepId.AI_SCAN, JourneyPlanner.plan(fresh).next?.id)
        assertEquals(StepId.CHOOSE_SPORTS, JourneyPlanner.plan(fresh.copy(daysSinceScan = 0)).next?.id)
        assertEquals(StepId.PICK_FOODS, JourneyPlanner.plan(fresh.copy(daysSinceScan = 0, hasWeek = true)).next?.id)
    }

    private val setUp = JourneyFacts(1, hasWeek = true, hasFavourites = true, todaysSession = null, loggedToday = false)

    @Test
    fun `after setup, today's session comes first until it is logged`() {
        val journey = JourneyPlanner.plan(setUp.copy(todaysSession = "Gym — Push"))
        assertTrue(journey.setupComplete)
        assertEquals(StepId.TODAYS_SESSION, journey.next?.id)
        assertNull(JourneyPlanner.plan(setUp.copy(todaysSession = "Gym — Push", loggedToday = true)).next)
    }

    @Test
    fun `a week-old scan brings the check-in round`() {
        assertEquals(StepId.WEEKLY_CHECK_IN, JourneyPlanner.plan(setUp.copy(daysSinceScan = 7)).next?.id)
        assertNull(JourneyPlanner.plan(setUp).next)
    }
}
