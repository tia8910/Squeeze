package com.squeeze.core.trend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepeatabilityTest {

    @Test
    fun `single measurements cannot estimate precision`() {
        assertNull(Repeatability.score(listOf(Observation(0, 20.0, 3.5))))
        // Measurements a week apart are separate sessions, not replicates.
        val weekly = listOf(Observation(0, 20.0, 3.5), Observation(7, 20.4, 3.5))
        assertNull(Repeatability.score(weekly))
    }

    @Test
    fun `replicates on one day yield a precision estimate`() {
        val replicates = listOf(
            Observation(0, 20.0, 3.5),
            Observation(0, 20.4, 3.5),
            Observation(0, 19.6, 3.5),
        )
        val score = Repeatability.score(replicates)

        assertNotNull(score)
        assertEquals(3, score.replicateCount)
        assertEquals(1, score.sessionCount)
        // Sample standard deviation of 20.0, 20.4, 19.6 is 0.4.
        assertEquals(0.4, score.withinSessionStdDev, 0.01)
    }

    @Test
    fun `repeatability coefficient follows the Bland-Altman definition`() {
        val score = Repeatability.score(
            listOf(Observation(0, 20.0, 3.5), Observation(0, 21.0, 3.5)),
        )
        assertNotNull(score)

        // 1.96 * sqrt(2) * sd, with sd = 0.7071 for a pair one point apart.
        assertEquals(1.96 * Math.sqrt(2.0) * score.withinSessionStdDev, score.repeatabilityCoefficient, 1e-9)
    }

    @Test
    fun `variance is pooled across multiple sessions`() {
        val twoSessions = listOf(
            Observation(0, 20.0, 3.5),
            Observation(0, 20.4, 3.5),
            Observation(30, 18.0, 3.5),
            Observation(30, 18.4, 3.5),
        )
        val score = Repeatability.score(twoSessions)

        assertNotNull(score)
        assertEquals(2, score.sessionCount)
        assertEquals(4, score.replicateCount)
        // Both sessions scatter by the same amount, so the pooled figure matches either one
        // and is not inflated by the 2 point gap between session means.
        assertEquals(0.283, score.withinSessionStdDev, 0.01)
    }

    @Test
    fun `tight replicates grade well and loose ones grade poorly`() {
        val tight = Repeatability.score(
            listOf(Observation(0, 20.0, 3.5), Observation(0, 20.2, 3.5)),
        )!!
        assertEquals(RepeatabilityScore.Grade.EXCELLENT, tight.grade)

        val loose = Repeatability.score(
            listOf(Observation(0, 18.0, 3.5), Observation(0, 22.0, 3.5)),
        )!!
        assertEquals(RepeatabilityScore.Grade.POOR, loose.grade)
    }

    @Test
    fun `session window is configurable`() {
        // Two measurements a day apart: same session by default, separate at a zero-day window.
        val nextDay = listOf(Observation(0, 20.0, 3.5), Observation(1, 20.4, 3.5))

        assertNotNull(Repeatability.score(nextDay))
        assertNull(Repeatability.score(nextDay, sessionWindowDays = 0))
    }
}
