package com.squeeze.core.scan

import com.squeeze.core.bodycomp.BodyFatCalculator
import com.squeeze.core.bodycomp.NeckEstimator
import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NeckEstimatorTest {

    @Test
    fun `lands close to a real neck for a mid-range adult`() {
        val estimate = NeckEstimator.estimate(heightCm = 175.0, weightKg = 70.0, sex = Sex.MALE)

        assertNotNull(estimate)
        // A 175 cm, 70 kg man measures somewhere near 37 cm at the neck.
        assertTrue(
            estimate.centimetres in 36.0..39.0,
            "estimated ${estimate.centimetres}, which is not a neck for this body",
        )
    }

    @Test
    fun `women estimate narrower than men at the same size`() {
        val male = NeckEstimator.estimate(170.0, 65.0, Sex.MALE)!!
        val female = NeckEstimator.estimate(170.0, 65.0, Sex.FEMALE)!!

        assertTrue(female.centimetres < male.centimetres)
    }

    @Test
    fun `carries an error large enough to matter`() {
        val estimate = NeckEstimator.estimate(180.0, 80.0, Sex.MALE)!!

        // The point of asserting this is that the number must never be shown as if it were
        // measured: two centimetres of neck is roughly two points of body fat.
        assertTrue(estimate.standardErrorCm >= 2.0)
        assertTrue(estimate.standardErrorCm * NeckEstimator.BODY_FAT_POINTS_PER_CM > 1.5)
    }

    @Test
    fun `is absent rather than defaulted without a weight`() {
        assertNull(NeckEstimator.estimate(175.0, null, Sex.MALE))
    }

    @Test
    fun `does not inherit a scan's inflated chest`() {
        // The reason this estimator refuses to look at the scan's own numbers. A real scan
        // reported a chest of 123.4 cm on a body whose chest is about 95. Anything derived
        // from that chest arrives inflated by the same third.
        val fromInflatedChest = 123.4 * 0.385
        val estimate = NeckEstimator.estimate(175.0, 70.0, Sex.MALE)!!

        assertTrue(fromInflatedChest > 45.0, "sanity: a chest-derived neck would be absurd")
        assertTrue(estimate.centimetres < 40.0)
    }
}

class WeightScaleCheckTest {

    /** The scan from the device screenshot: every site inflated by about a third. */
    private val inflated = Circumferences(
        chestCm = 123.4,
        waistCm = 98.9,
        hipCm = 106.7,
        thighCm = 69.4,
    )

    private val height = 175.0
    private val weight = 70.0

    @Test
    fun `a body of a plausible size is not flagged`() {
        val honest = Circumferences(
            chestCm = 95.0,
            waistCm = 76.0,
            hipCm = 82.0,
            thighCm = 53.0,
            neckCm = 37.5,
        )

        val finding = WeightScaleCheck.evaluate(honest, height, weight)

        assertNotNull(finding)
        assertTrue(
            !finding.significant,
            "implied ${finding.impliedWeightKg} kg against an actual $weight kg — the volume " +
                "model should agree with itself on a body that adds up",
        )
    }

    @Test
    fun `catches the inflated scan the device produced`() {
        val finding = WeightScaleCheck.evaluate(inflated, height, weight)

        assertNotNull(finding)
        assertTrue(finding.significant)
        assertTrue(
            finding.impliedWeightKg > 100.0,
            "these girths describe a much heavier person than 70 kg, and the check must say " +
                "so — implied ${finding.impliedWeightKg}",
        )
        assertTrue(finding.correctionFactor < 0.85)
    }

    @Test
    fun `the correction recovers measurements that add up`() {
        val finding = WeightScaleCheck.evaluate(inflated, height, weight)!!
        val corrected = WeightScaleCheck.apply(inflated, finding.correctionFactor)

        // Not asserted against the original numbers, because the truth is not known to a
        // centimetre. Asserted against anatomy: a lean 70 kg man's waist is in the seventies
        // and his chest is somewhere near a metre.
        assertTrue(
            corrected.waistCm!! in 70.0..82.0,
            "corrected waist ${corrected.waistCm}",
        )
        assertTrue(corrected.chestCm!! in 88.0..102.0, "corrected chest ${corrected.chestCm}")
        assertTrue(corrected.thighCm!! in 46.0..58.0, "corrected thigh ${corrected.thighCm}")

        // And the correction must be self-consistent: re-running the check on the corrected
        // numbers has to come back clean, or it would just flag itself forever.
        val recheck = WeightScaleCheck.evaluate(corrected, height, weight)!!
        assertTrue(!recheck.significant)
        assertEquals(weight, recheck.impliedWeightKg, 3.0)
    }

    @Test
    fun `an inferred neck is held out of the correction`() {
        val neck = NeckEstimator.estimate(height, weight, Sex.MALE)!!.centimetres

        val withInferred =
            WeightScaleCheck.evaluate(inflated, height, weight, inferredNeckCm = neck)!!
        val withNothing = WeightScaleCheck.evaluate(inflated, height, weight)!!

        // The head-and-neck segment is already the right size, so correcting it too would
        // shrink it below life size. Holding it fixed leaves less volume for the rest, so
        // the factor applied to the photographed sites must come out slightly smaller.
        assertTrue(
            withInferred.correctionFactor < withNothing.correctionFactor,
            "inferred ${withInferred.correctionFactor} vs plain ${withNothing.correctionFactor}",
        )
    }

    @Test
    fun `the whole chain turns an unusable scan into a believable body fat`() {
        // The end-to-end claim, on the exact numbers the device produced. Before this work
        // the scan yielded no percentage at all; a neck estimated without the scale fix
        // would have yielded a badly wrong one. Both halves are needed.
        val profile = Profile(heightCm = height, birthYear = 1995, sex = Sex.MALE)
        val neck = NeckEstimator.estimate(height, weight, Sex.MALE)!!

        val naive = BodyFatCalculator.navy(
            profile,
            inflated.copy(neckCm = neck.centimetres),
        )
        assertNotNull(naive)
        assertTrue(
            naive.percent > 22.0,
            "an estimated neck on an uncorrected waist should be visibly wrong, was " +
                "${naive.percent} — if this ever drops into a believable range the test has " +
                "stopped guarding anything",
        )

        val finding = WeightScaleCheck.evaluate(inflated, height, weight, neck.centimetres)!!
        val corrected = WeightScaleCheck
            .apply(inflated, finding.correctionFactor)
            .copy(neckCm = neck.centimetres)

        val fixed = BodyFatCalculator.navy(profile, corrected)
        assertNotNull(fixed)

        // The user reports this body is about 10%. The claim here is deliberately weak — the
        // neck alone carries a couple of points — but it must at least be in the right half
        // of the scale, which the uncorrected version is not.
        assertTrue(
            abs(fixed.percent - 10.0) < 6.0,
            "corrected scan gave ${fixed.percent}% against a stated 10%",
        )
        assertTrue(fixed.percent < naive.percent - 10.0)
    }

    @Test
    fun `is absent without a weight to check against`() {
        assertNull(WeightScaleCheck.evaluate(inflated, height, weightKg = null))
    }

    @Test
    fun `is absent without any torso girth`() {
        assertNull(
            WeightScaleCheck.evaluate(Circumferences(thighCm = 55.0), height, weight),
        )
    }
}
