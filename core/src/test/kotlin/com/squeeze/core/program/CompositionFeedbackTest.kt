package com.squeeze.core.program

import com.squeeze.core.model.Goal
import com.squeeze.core.trend.TrendPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompositionFeedbackTest {

    /**
     * Builds a trend point with an explicit significance decision, so these tests exercise
     * the feedback rules rather than re-testing the filter.
     */
    private fun point(weeklyChange: Double, significant: Boolean, level: Double = 20.0): TrendPoint {
        // isChangeSignificant is derived: |change| > 1.96 * sd. Choose sd to force the verdict.
        val sd = if (significant) kotlin.math.abs(weeklyChange) / 3.0 else kotlin.math.abs(weeklyChange) * 3.0 + 1.0
        return TrendPoint(
            epochDay = 0,
            level = level,
            weeklyChange = weeklyChange,
            levelStdDev = 0.3,
            weeklyChangeStdDev = sd,
            raw = level,
        )
    }

    @Test
    fun `no trend data yields no adjustment`() {
        assertEquals(VolumeAdjustment.NONE, CompositionFeedback.evaluate(emptyList(), emptyList(), Goal.CUT))
    }

    @Test
    fun `an insignificant trend is never acted on`() {
        val result = CompositionFeedback.evaluate(
            bodyFatTrend = listOf(point(-0.4, significant = false)),
            leanMassTrend = listOf(point(-0.3, significant = false, level = 65.0)),
            goal = Goal.CUT,
        )

        assertEquals(
            VolumeAdjustment.NONE, result,
            "acting on noise is what makes users distrust an app",
        )
    }

    @Test
    fun `lean mass loss on a cut reduces volume`() {
        val result = CompositionFeedback.evaluate(
            bodyFatTrend = listOf(point(-0.3, significant = true)),
            leanMassTrend = listOf(point(-0.25, significant = true, level = 65.0)),
            goal = Goal.CUT,
        )

        assertTrue(result.volumeMultiplier < 1.0, "expected volume cut, got ${result.volumeMultiplier}")
        assertNotNull(result.rationale)
        assertTrue(result.rationale!!.contains("lean mass"), "rationale was ${result.rationale}")
    }

    @Test
    fun `aggressive fat loss trims volume even with lean mass holding`() {
        val result = CompositionFeedback.evaluate(
            bodyFatTrend = listOf(point(-0.8, significant = true)),
            leanMassTrend = listOf(point(-0.02, significant = false, level = 65.0)),
            goal = Goal.CUT,
        )

        assertTrue(result.volumeMultiplier < 1.0)
        assertTrue(result.rationale!!.contains("aggressive"), "rationale was ${result.rationale}")
    }

    @Test
    fun `a stalled cut points at the deficit rather than the training`() {
        val result = CompositionFeedback.evaluate(
            bodyFatTrend = listOf(point(-0.01, significant = true)),
            leanMassTrend = listOf(point(0.0, significant = false, level = 65.0)),
            goal = Goal.CUT,
        )

        assertEquals(1.0, result.volumeMultiplier, 1e-9)
        assertTrue(result.rationale!!.contains("deficit"), "rationale was ${result.rationale}")
    }

    @Test
    fun `excessive fat gain on a bulk blames the surplus not the volume`() {
        val result = CompositionFeedback.evaluate(
            bodyFatTrend = listOf(point(0.4, significant = true)),
            leanMassTrend = listOf(point(0.1, significant = true, level = 70.0)),
            goal = Goal.HYPERTROPHY,
        )

        // The lever here is food, not sets. Volume must not be inflated to chase it.
        assertEquals(1.0, result.volumeMultiplier, 1e-9)
        assertTrue(result.rationale!!.contains("surplus"), "rationale was ${result.rationale}")
    }

    @Test
    fun `a bulk gaining fat without lean mass says so explicitly`() {
        val result = CompositionFeedback.evaluate(
            bodyFatTrend = listOf(point(0.4, significant = true)),
            leanMassTrend = listOf(point(0.0, significant = false, level = 70.0)),
            goal = Goal.HYPERTROPHY,
        )

        assertTrue(
            result.rationale!!.contains("mostly becoming fat"),
            "rationale was ${result.rationale}",
        )
    }

    @Test
    fun `flat lean mass at stable body fat increases volume`() {
        val result = CompositionFeedback.evaluate(
            bodyFatTrend = listOf(point(0.02, significant = true)),
            leanMassTrend = listOf(point(0.0, significant = false, level = 70.0)),
            goal = Goal.HYPERTROPHY,
        )

        assertTrue(result.volumeMultiplier > 1.0, "stimulus is the limit here, got ${result.volumeMultiplier}")
        assertTrue(result.rationale!!.contains("Volume is increased"))
    }

    @Test
    fun `successful recomposition is left alone`() {
        val result = CompositionFeedback.evaluate(
            bodyFatTrend = listOf(point(-0.15, significant = true)),
            leanMassTrend = listOf(point(0.08, significant = true, level = 70.0)),
            goal = Goal.RECOMP,
        )

        assertEquals(1.0, result.volumeMultiplier, 1e-9)
        assertTrue(result.rationale!!.contains("working"), "rationale was ${result.rationale}")
    }

    @Test
    fun `strength goals ignore composition drift`() {
        val result = CompositionFeedback.evaluate(
            bodyFatTrend = listOf(point(0.4, significant = true)),
            leanMassTrend = listOf(point(0.1, significant = true, level = 70.0)),
            goal = Goal.STRENGTH,
        )

        assertEquals(VolumeAdjustment.NONE, result)
    }

    @Test
    fun `every non-neutral adjustment carries an explanation`() {
        val cases = listOf(
            Triple(point(-0.3, true), point(-0.25, true, 65.0), Goal.CUT),
            Triple(point(-0.8, true), point(-0.02, false, 65.0), Goal.CUT),
            Triple(point(0.4, true), point(0.1, true, 70.0), Goal.HYPERTROPHY),
            Triple(point(0.02, true), point(0.0, false, 70.0), Goal.HYPERTROPHY),
            Triple(point(0.2, true), point(0.0, false, 70.0), Goal.RECOMP),
        )

        for ((fat, lean, goal) in cases) {
            val result = CompositionFeedback.evaluate(listOf(fat), listOf(lean), goal)
            if (result != VolumeAdjustment.NONE) {
                assertNotNull(result.rationale, "$goal adjustment had no rationale")
                assertTrue(result.rationale!!.isNotBlank())
            }
        }
    }
}
