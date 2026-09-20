package com.squeeze.core.scan

import com.squeeze.core.bodycomp.BodyFatCalculator
import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stature from a photograph with no feet in it, and what that costs.
 *
 * **The scan this exists for.** A user photographed his trunk — waist, shoulders and hips all
 * in shot, which is the framing the app recommends, because closer framing puts far more
 * pixels on the midsection. His ankles were outside the frame, so [LandmarkStature.frameFraction]
 * returned null, so there was no scale, so there were no circumferences, so the Navy equation
 * never ran. The only candidate left in the fusion was the outline's bound, and that bound is
 * a constant: he was shown 11.6% and told the photograph could not resolve him.
 *
 * It could. It had his waist, his neck and his hips in the picture. What it did not have was
 * the one span the app knew how to measure stature with.
 *
 * **Why paying five per cent for a scale beats refusing to scale.** The refusal is not free —
 * it costs the whole measurement and substitutes a number that is the same for every body
 * that reaches it. These tests pin the trade in both directions: the trunk span is worse than
 * the ankle span and must be labelled so, and it is still worth far more than nothing.
 */
class TrunkStatureTest {

    private val man = Profile(heightCm = 175.0, birthYear = 1993, sex = Sex.MALE)
    private val woman = Profile(heightCm = 165.0, birthYear = 1993, sex = Sex.FEMALE)

    /** Landmarks for a standing adult filling the frame, in fractions of frame height. */
    private fun nose(y: Double = 0.10) = PosePoint(0.5, y)
    private fun hip(y: Double = 0.50) = PosePoint(0.5, y)

    @Test
    fun `a trunk photograph yields a stature where the ankle span yields nothing`() {
        // The exact situation: nose and hips present, ankles outside the frame.
        assertNull(
            LandmarkStature.frameFraction(nose(), null, null),
            "the ankle span must keep refusing; it has nothing to work with",
        )

        val fromTrunk = LandmarkStature.frameFractionFromTrunk(nose(), hip(), hip())

        assertNotNull(fromTrunk)
        // 0.40 of the frame between nose and hip, over 0.395 of stature.
        assertEquals(0.40 / LandmarkStature.NOSE_TO_HIP_FRACTION, fromTrunk, 1e-9)
    }

    @Test
    fun `the two spans agree on a body that offers both`() {
        // The check that the constants are consistent rather than independently invented.
        // A figure with the nose at 0.10, hips at 0.50 and ankles at 0.996 of the frame is
        // one body; both routes have to return one stature for it.
        //
        // Nose 0.925 of stature, hip 0.530, ankle 0.039 — so for a stature spanning s, the
        // nose-to-hip span is 0.395s and the nose-to-ankle span is 0.886s. A frame where
        // those hold simultaneously is the test case.
        val stature = 1.012
        val noseY = 0.10
        val hipY = noseY + 0.395 * stature
        val ankleY = noseY + 0.886 * stature

        val viaAnkle = LandmarkStature.frameFraction(
            PosePoint(0.5, noseY), PosePoint(0.5, ankleY), PosePoint(0.5, ankleY),
        )
        val viaTrunk = LandmarkStature.frameFractionFromTrunk(
            PosePoint(0.5, noseY), PosePoint(0.5, hipY), PosePoint(0.5, hipY),
        )

        assertNotNull(viaAnkle)
        assertNotNull(viaTrunk)
        assertEquals(viaAnkle, viaTrunk, 1e-9, "the segment fractions disagree about one body")
    }

    @Test
    fun `both hips are averaged rather than one being preferred`() {
        // Unlike the ankles, where one foot forward means the lower landmark is the one on
        // the floor. Hip joints of a standing adult are level, so the pair is noise to average
        // rather than a choice to make.
        val averaged = LandmarkStature.frameFractionFromTrunk(
            nose(), PosePoint(0.5, 0.48), PosePoint(0.5, 0.52),
        )
        val level = LandmarkStature.frameFractionFromTrunk(nose(), hip(0.50), hip(0.50))

        assertNotNull(averaged)
        assertNotNull(level)
        assertEquals(level, averaged, 1e-9)
    }

    @Test
    fun `a half-present landmark set is refused rather than guessed`() {
        assertNull(LandmarkStature.frameFractionFromTrunk(null, hip(), hip()))
        assertNull(LandmarkStature.frameFractionFromTrunk(nose(), null, null))
        // Hips above the nose is not a standing adult; it is a misdetection.
        assertNull(LandmarkStature.frameFractionFromTrunk(nose(0.60), hip(0.50), hip(0.50)))
    }

    @Test
    fun `one hip still works, because half a pair is better than no scale`() {
        val single = LandmarkStature.frameFractionFromTrunk(nose(), hip(), null)

        assertNotNull(single)
        assertEquals(0.40 / LandmarkStature.NOSE_TO_HIP_FRACTION, single, 1e-9)
    }

    @Test
    fun `an implausible span is refused, as it is for the ankle route`() {
        // The pose model extrapolates landmarks past the frame edge, so a nonsensical span is
        // reachable and must not become a scale.
        assertNull(
            LandmarkStature.frameFractionFromTrunk(
                PosePoint(0.5, 0.0), PosePoint(0.5, 0.9), PosePoint(0.5, 0.9),
            ),
            "0.9 of the frame between nose and hip implies a stature over twice the frame",
        )
    }

    @Test
    fun `what a soft scale costs is computed from the equation, not asserted`() {
        // A reconstruction of the scan in the class header: 175 cm, waist about 84 cm, neck
        // about 38. The point is the shape of the answer rather than the exact figures.
        val c = Circumferences(neckCm = 38.0, waistCm = 84.0)

        val atFive = BodyFatCalculator.navyScaleSensitivityPercent(man, c, 0.05)
        val atFifteen = BodyFatCalculator.navyScaleSensitivityPercent(man, c, 0.15)

        assertNotNull(atFive)
        assertNotNull(atFifteen)

        // Measured through the density form the app uses: about 1.8 points at five per cent
        // and 5.2 at fifteen. Asserted as bands rather than to three decimals, because what
        // matters is the order of magnitude, not the third digit.
        assertTrue(atFive in 1.0..3.0, "five per cent cost $atFive points")
        assertTrue(atFifteen in 3.5..7.5, "fifteen per cent cost $atFifteen points")

        // A logarithm turns a multiplicative error into an additive one, so trebling the
        // scale error must not treble-and-then-some the cost.
        assertTrue(atFifteen < atFive * 3.6, "$atFive -> $atFifteen is worse than linear")
    }

    @Test
    fun `even the worst plausible scale error beats the constant it replaces`() {
        // **The whole argument, as one assertion.** Refusing to scale is not the safe option;
        // it substitutes the outline's bound, which carries nine points and is the same value
        // for every body that reaches it. A scan scaled from the trunk has to be better than
        // that even when the scale is much worse than expected.
        val c = Circumferences(neckCm = 38.0, waistCm = 84.0)

        val atFifteen = BodyFatCalculator.navyScaleSensitivityPercent(man, c, 0.15)
        assertNotNull(atFifteen)

        val navy = BodyFatCalculator.navy(man, c)
        assertNotNull(navy)
        val combined = kotlin.math.sqrt(
            navy.standardErrorPercent * navy.standardErrorPercent + atFifteen * atFifteen,
        )

        assertTrue(
            combined < SilhouetteBodyFat.PLATEAU_ERROR_PERCENT,
            "scaled from the trunk at 15% error gives ±$combined against the bound's " +
                "±${SilhouetteBodyFat.PLATEAU_ERROR_PERCENT}",
        )
    }

    @Test
    fun `a woman's equation is more sensitive to scale, and says so`() {
        // Her equation leans on waist plus hip minus neck, a larger girth term with a larger
        // coefficient, so the same scale error costs her nearly twice as much. An interval
        // that ignored this would understate her uncertainty and not his.
        val hers = Circumferences(neckCm = 32.0, waistCm = 76.0, hipCm = 98.0)
        val his = Circumferences(neckCm = 38.0, waistCm = 84.0)

        val her = BodyFatCalculator.navyScaleSensitivityPercent(woman, hers, 0.05)
        val him = BodyFatCalculator.navyScaleSensitivityPercent(man, his, 0.05)

        assertNotNull(her)
        assertNotNull(him)
        assertTrue(her > him * 1.4, "female $her against male $him")
    }

    @Test
    fun `no scale error costs nothing, and an unusable set costs nothing knowable`() {
        val c = Circumferences(neckCm = 38.0, waistCm = 84.0)

        assertEquals(0.0, BodyFatCalculator.navyScaleSensitivityPercent(man, c, 0.0))
        // No waist, so no equation, so no sensitivity to report — absent rather than zero.
        assertNull(
            BodyFatCalculator.navyScaleSensitivityPercent(man, Circumferences(neckCm = 38.0), 0.05),
        )
    }

    @Test
    fun `the cost grows with the error, at every size`() {
        val c = Circumferences(neckCm = 38.0, waistCm = 84.0)
        val costs = listOf(0.01, 0.02, 0.05, 0.10, 0.15, 0.20).map {
            val cost = BodyFatCalculator.navyScaleSensitivityPercent(man, c, it)
            assertNotNull(cost, "error $it")
            cost
        }

        assertTrue(
            costs.zipWithNext().all { (a, b) -> b > a },
            "a worse scale must never look cheaper: $costs",
        )
        assertTrue(costs.all { it >= 0.0 }, "$costs")
        assertTrue(abs(costs.first()) < 1.0, "one per cent should be under a point")
    }
}
