package com.squeeze.core.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JourneyTest {

    private val fresh = JourneyFacts(null, null, hasWeek = false, hasFavourites = false, todaysSession = null, loggedToday = false)

    @Test
    fun `setup runs in order, one domino at a time`() {
        assertEquals(StepId.WEIGH_IN, JourneyPlanner.plan(fresh).next?.id)
        assertEquals(StepId.AI_SCAN, JourneyPlanner.plan(fresh.copy(daysSinceWeight = 0)).next?.id)
        assertEquals(StepId.CHOOSE_SPORTS, JourneyPlanner.plan(fresh.copy(daysSinceWeight = 0, daysSinceScan = 0)).next?.id)
        assertEquals(
            StepId.PICK_FOODS,
            JourneyPlanner.plan(fresh.copy(daysSinceWeight = 0, daysSinceScan = 0, hasWeek = true)).next?.id,
        )
    }

    private val setUp = JourneyFacts(1, 1, hasWeek = true, hasFavourites = true, todaysSession = null, loggedToday = false)

    @Test
    fun `after setup, today's session comes first until it is logged`() {
        val journey = JourneyPlanner.plan(setUp.copy(todaysSession = "Gym — Push"))
        assertTrue(journey.setupComplete)
        assertEquals(StepId.TODAYS_SESSION, journey.next?.id)
        assertNull(JourneyPlanner.plan(setUp.copy(todaysSession = "Gym — Push", loggedToday = true)).next)
    }

    @Test
    fun `a stale weight, then a stale scan, come back round`() {
        assertEquals(StepId.WEEKLY_WEIGH_IN, JourneyPlanner.plan(setUp.copy(daysSinceWeight = 8)).next?.id)
        assertEquals(StepId.RESCAN, JourneyPlanner.plan(setUp.copy(daysSinceScan = 15)).next?.id)
        assertNull(JourneyPlanner.plan(setUp).next)
    }
}
