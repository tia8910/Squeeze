package com.squeeze.core.bodycomp

import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The gate that catches what the silhouette cannot.
 *
 * The case this exists for is a real one: a scan read 36.6% on a man of 68 kg at 1.75 m. That
 * leaves 43.1 kg of fat-free mass, a fat-free mass index of 14.1 — a frail patient, not a man
 * with visible abdominal definition. The app displayed the 14.1 immediately below the 36.6
 * and drew no conclusion from it.
 */
class LeanMassPlausibilityTest {

    private val man = Profile(heightCm = 175.0, birthYear = 1990, sex = Sex.MALE)
    private val woman = Profile(heightCm = 165.0, birthYear = 1990, sex = Sex.FEMALE)

    private fun estimate(percent: Double) = BodyFatEstimate(
        percent = percent,
        method = EstimationMethod.PHOTO_SHAPE,
        standardErrorPercent = EstimationMethod.PHOTO_SHAPE.standardErrorPercent,
    )

    @Test
    fun `the scan that read thirty-six per cent is ruled out`() {
        assertFalse(LeanMassPlausibility.isPlausible(36.6, man, weightKg = 68.0))
    }

    @Test
    fun `and what it is replaced with is a bound, stated as one`() {
        val clamped = LeanMassPlausibility.clampToRange(estimate(36.6), man, 68.0)
        val range = LeanMassPlausibility.plausibleRange(man, 68.0)

        assertNotNull(range)
        assertEquals(range.endInclusive, clamped.percent, 1e-9)
        // Around 27-28%: an FFMI of 16 at this height and weight.
        assertTrue(clamped.percent in 25.0..30.0, "got ${clamped.percent}")
        // And it must not pretend to be as good as a measurement.
        assertTrue(
            clamped.standardErrorPercent >= LeanMassPlausibility.BOUND_ERROR_PERCENT,
            "${clamped.standardErrorPercent}",
        )
    }

    @Test
    fun `everything a real body could be passes untouched`() {
        // The gate has to be quiet almost always, or it is not a gate — it is a second
        // opinion, and a bad one, because it knows nothing about this person.
        listOf(8.0, 12.0, 15.0, 18.0, 22.0, 25.0).forEach { percent ->
            assertTrue(
                LeanMassPlausibility.isPlausible(percent, man, 68.0),
                "$percent was ruled out for a 68 kg man at 1.75 m",
            )
        }
    }

    @Test
    fun `an implausible candidate is dropped rather than averaged in`() {
        val pool = listOf(estimate(14.0), estimate(36.6))

        val survivors = LeanMassPlausibility.filter(pool, man, 68.0)

        assertEquals(listOf(14.0), survivors.map { it.percent })
    }

    @Test
    fun `when every candidate fails, none is dropped`() {
        // Dropping them all would leave the user with no number at all, which is a worse
        // answer than a bound. The caller clamps what the fusion produces instead.
        val pool = listOf(estimate(36.6), estimate(41.0))

        assertEquals(pool, LeanMassPlausibility.filter(pool, man, 68.0))
    }

    @Test
    fun `without a weight there is nothing to reason from and nothing is ruled out`() {
        assertNull(LeanMassPlausibility.plausibleRange(man, null))
        assertTrue(LeanMassPlausibility.isPlausible(36.6, man, null))
        assertEquals(
            estimate(36.6),
            LeanMassPlausibility.clampToRange(estimate(36.6), man, null),
        )
    }

    @Test
    fun `the bound moves with the weight, because that is what makes it a constraint`() {
        // The same person heavier can carry the same fat-free mass at a much higher body fat.
        val light = LeanMassPlausibility.plausibleRange(man, 68.0)
        val heavy = LeanMassPlausibility.plausibleRange(man, 95.0)

        assertNotNull(light)
        assertNotNull(heavy)
        assertTrue(
            heavy.endInclusive > light.endInclusive,
            "${heavy.endInclusive} vs ${light.endInclusive}",
        )
        // At 95 kg, 36.6% is entirely ordinary and must not be touched.
        assertTrue(LeanMassPlausibility.isPlausible(36.6, man, 95.0))
    }

    @Test
    fun `a near-zero reading is ruled out from the other end`() {
        // The upper FFMI bound. A scan reading a 95 kg man at three per cent implies a
        // fat-free mass index past anything achieved without drugs.
        assertFalse(LeanMassPlausibility.isPlausible(3.5, man, 95.0))

        val clamped = LeanMassPlausibility.clampToRange(estimate(3.5), man, 95.0)
        assertTrue(clamped.percent > 3.5, "got ${clamped.percent}")
    }

    @Test
    fun `women are judged on their own range`() {
        // 30% on a 62 kg woman at 1.65 m is unremarkable. Run through the male bounds it
        // would be rejected, which is the failure mode this test exists to prevent.
        assertTrue(LeanMassPlausibility.isPlausible(30.0, woman, 62.0))
        assertFalse(LeanMassPlausibility.isPlausible(30.0, man, 62.0))
    }

    @Test
    fun `an impossible height and weight together conclude nothing`() {
        // No body fat percentage gives this pair a plausible lean mass. Clamping to a
        // boundary the person is nowhere near would be worse than declining to judge.
        assertNull(LeanMassPlausibility.plausibleRange(man, 35.0))
        assertEquals(
            estimate(20.0),
            LeanMassPlausibility.clampToRange(estimate(20.0), man, 35.0),
        )
    }
}
