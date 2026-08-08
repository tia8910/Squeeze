package com.squeeze.core.bodycomp

import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun estimate(method: EstimationMethod, percent: Double) = BodyFatEstimate(
    percent = percent,
    method = method,
    standardErrorPercent = method.standardErrorPercent,
)

class MethodFusionTest {

    @Test
    fun `nothing to fuse returns nothing`() {
        assertNull(MethodFusion.combine(emptyList()))
    }

    @Test
    fun `one estimate comes back unchanged rather than dressed up as a combination`() {
        val single = estimate(EstimationMethod.NAVY_CIRCUMFERENCE, 17.0)
        val fused = MethodFusion.combine(listOf(single))

        assertNotNull(fused)
        assertEquals(17.0, fused.combined.percent, 1e-9)
        assertEquals(single.standardErrorPercent, fused.combined.standardErrorPercent, 1e-9)
        assertNull(fused.disagreementPoints)
        assertTrue(!fused.methodsDisagree)
    }

    @Test
    fun `the more precise method pulls the answer toward itself`() {
        // A reference scan is far more precise than a photo, so the fused value must sit
        // much nearer the scan than a plain average would.
        val fused = MethodFusion.combine(
            listOf(
                estimate(EstimationMethod.REFERENCE_SCAN, 12.0),
                estimate(EstimationMethod.PHOTO_SILHOUETTE, 20.0),
            ),
        )

        assertNotNull(fused)
        assertTrue(
            fused.combined.percent < 14.0,
            "expected the precise method to dominate, got ${fused.combined.percent}",
        )
    }

    @Test
    fun `combining reduces uncertainty but never below the correlation floor`() {
        val navy = estimate(EstimationMethod.NAVY_CIRCUMFERENCE, 18.0)
        val skinfold = estimate(EstimationMethod.JACKSON_POLLOCK_3, 18.0)

        val fused = MethodFusion.combine(listOf(navy, skinfold))
        assertNotNull(fused)

        val best = minOf(navy.standardErrorPercent, skinfold.standardErrorPercent)

        assertTrue(
            fused.combined.standardErrorPercent < best,
            "two methods should be more certain than one",
        )
        // Independence would give best/sqrt(2) = 0.707*best. These methods share a body, a
        // day and an operator, so claiming that would understate the interval.
        assertTrue(
            fused.combined.standardErrorPercent >= best * 0.7,
            "the fused error ${fused.combined.standardErrorPercent} claims independence " +
                "the methods do not have",
        )
    }

    @Test
    fun `the BMI fallback is not treated as a second opinion`() {
        // Deurenberg is blind to muscle, so on a lean, muscular person it reads far high.
        // Averaging it in would drag a good estimate toward a known bias rather than
        // cancelling noise.
        val fused = MethodFusion.combine(
            listOf(
                estimate(EstimationMethod.NAVY_CIRCUMFERENCE, 10.0),
                estimate(EstimationMethod.DEURENBERG_BMI, 24.0),
            ),
        )

        assertNotNull(fused)
        assertEquals(10.0, fused.combined.percent, 1e-9)
        assertTrue(EstimationMethod.DEURENBERG_BMI !in fused.contributors)
    }

    @Test
    fun `BMI is still used when it is all there is`() {
        val fused = MethodFusion.combine(listOf(estimate(EstimationMethod.DEURENBERG_BMI, 24.0)))

        assertNotNull(fused)
        assertEquals(24.0, fused.combined.percent, 1e-9)
        assertEquals(listOf(EstimationMethod.DEURENBERG_BMI), fused.contributors)
    }

    @Test
    fun `methods that agree are not flagged, and methods that cannot both be right are`() {
        val agreeing = MethodFusion.combine(
            listOf(
                estimate(EstimationMethod.NAVY_CIRCUMFERENCE, 18.0),
                estimate(EstimationMethod.JACKSON_POLLOCK_3, 18.6),
            ),
        )
        assertNotNull(agreeing)
        assertTrue(!agreeing.methodsDisagree, "ordinary scatter was reported as a conflict")

        val conflicting = MethodFusion.combine(
            listOf(
                estimate(EstimationMethod.NAVY_CIRCUMFERENCE, 11.0),
                estimate(EstimationMethod.JACKSON_POLLOCK_3, 29.0),
            ),
        )
        assertNotNull(conflicting)
        assertTrue(
            conflicting.methodsDisagree,
            "an eighteen-point gap between methods should be reported",
        )
        assertEquals(18.0, conflicting.disagreementPoints!!, 1e-9)
    }

    @Test
    fun `estimates that cannot contribute are ignored rather than poisoning the result`() {
        val fused = MethodFusion.combine(
            listOf(
                estimate(EstimationMethod.NAVY_CIRCUMFERENCE, 18.0),
                BodyFatEstimate(Double.NaN, EstimationMethod.PHOTO_SILHOUETTE, 4.0),
                BodyFatEstimate(20.0, EstimationMethod.PHOTO_SILHOUETTE, 0.0),
            ),
        )

        assertNotNull(fused)
        assertEquals(18.0, fused.combined.percent, 1e-9)
    }
}

class ReferenceBandsTest {

    @Test
    fun `body fat categories follow the published boundaries for men`() {
        assertEquals("Athletic", ReferenceBands.bodyFat(10.0, Sex.MALE, 30).label)
        assertEquals("Fitness", ReferenceBands.bodyFat(16.0, Sex.MALE, 30).label)
        assertEquals("Average", ReferenceBands.bodyFat(22.0, Sex.MALE, 30).label)
        assertEquals(BandPosition.HIGH, ReferenceBands.bodyFat(30.0, Sex.MALE, 30).position)
    }

    @Test
    fun `women's boundaries sit higher than men's, as the physiology requires`() {
        // The same percentage is lean on a woman and ordinary on a man.
        assertEquals("Athletic", ReferenceBands.bodyFat(19.0, Sex.FEMALE, 30).label)
        assertEquals("Average", ReferenceBands.bodyFat(19.0, Sex.MALE, 30).label)
    }

    @Test
    fun `the boundaries drift with age rather than holding one table for every decade`() {
        // 18% is Above average for a 25-year-old man and merely Fitness at 60, because body
        // fat rises with age at constant habits.
        val young = ReferenceBands.bodyFat(18.0, Sex.MALE, 25)
        val older = ReferenceBands.bodyFat(18.0, Sex.MALE, 60)

        assertEquals("Average", young.label)
        assertEquals("Fitness", older.label)
    }

    @Test
    fun `an implausibly lean reading is named rather than praised`() {
        val band = ReferenceBands.bodyFat(3.0, Sex.MALE, 30)
        assertEquals(BandPosition.LOW, band.position)
        assertTrue(band.label.contains("essential", ignoreCase = true))
    }

    @Test
    fun `FFMI flags a figure above the drug-free ceiling as an input problem`() {
        val band = ReferenceBands.ffmi(27.0, Sex.MALE)
        assertEquals(BandPosition.HIGH, band.position)
        assertTrue(
            band.detail.contains("body fat", ignoreCase = true),
            "the band should point at the input most likely to be wrong",
        )
    }

    @Test
    fun `waist to height turns at exactly one half`() {
        assertEquals(BandPosition.NORMAL, ReferenceBands.waistToHeight(0.499).position)
        assertEquals(BandPosition.HIGH, ReferenceBands.waistToHeight(0.501).position)
    }

    @Test
    fun `waist to hip thresholds differ by sex`() {
        assertEquals(BandPosition.NORMAL, ReferenceBands.waistToHip(0.88, Sex.MALE).position)
        assertEquals(BandPosition.HIGH, ReferenceBands.waistToHip(0.88, Sex.FEMALE).position)
    }

    @Test
    fun `BMI says out loud that it cannot see muscle`() {
        val band = ReferenceBands.bmi(27.0)
        assertEquals(BandPosition.HIGH, band.position)
        assertTrue(band.detail.contains("muscle", ignoreCase = true))
    }
}
