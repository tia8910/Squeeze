package com.squeeze.core.scan

import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Why the hip is the denominator and the shoulder is not.
 *
 * Shoulder width from a front silhouette is not a torso width and cannot be made into one.
 * At the shoulder line the arms are attached, so the mask has a single run spanning deltoid
 * to deltoid; a few centimetres lower the arms separate and the run is the trunk alone.
 * Measured on a real scan: **495 pixels at the shoulder band against 304 immediately below
 * it**, same body, same photograph.
 *
 * So the shoulder ratio divides a numerator with the arms excluded by a denominator with
 * them included. On that scan it gave 0.687 → six per cent, for a body with no abdominal
 * definition at all. The same waist over the hip gave 0.87 → fifteen.
 */
class HipDenominatorTest {

    @Test
    fun `the hip decides the answer when both ratios are present`() {
        // The shoulder ratio here is deflated exactly as a real one is by arm contamination.
        // It must not drag the answer down.
        val indices = ShapeIndices(waistToShoulder = 0.687, waistToHip = 0.87)

        val estimate = SilhouetteBodyFat.estimate(indices, Sex.MALE)

        assertNotNull(estimate)
        assertEquals(15.3, estimate.percent, 0.5)
    }

    @Test
    fun `a contaminated shoulder ratio no longer reaches the answer at all`() {
        // Two scans of the same body, differing only in how much arm the shoulder band
        // happened to catch. They must agree, because nothing about the body differed.
        val armsIn = ShapeIndices(waistToShoulder = 0.687, waistToHip = 0.87)
        val armsClear = ShapeIndices(waistToShoulder = 0.90, waistToHip = 0.87)

        val a = SilhouetteBodyFat.estimate(armsIn, Sex.MALE)
        val b = SilhouetteBodyFat.estimate(armsClear, Sex.MALE)

        assertNotNull(a)
        assertNotNull(b)
        assertEquals(a.percent, b.percent, 1e-9)
    }

    @Test
    fun `the real scan that read six per cent now reads the high teens`() {
        // Reconstructed from the photograph: waist 340px, shoulder run 495px with both arms
        // merged into it, hip 390px.
        val indices = ShapeIndices(
            waistToShoulder = 340.0 / 495.0,
            waistToHip = 340.0 / 390.0,
        )

        val estimate = SilhouetteBodyFat.estimate(indices, Sex.MALE)

        assertNotNull(estimate)
        assertTrue(estimate.percent > 13.0, "got ${estimate.percent}")
    }

    @Test
    fun `a lean waist-to-hip still reads lean`() {
        // The fix must not simply push everything upward. A genuinely narrow waist against a
        // genuinely wide hip is a lean build and has to keep reading as one.
        val estimate = SilhouetteBodyFat.estimate(
            ShapeIndices(waistToShoulder = 0.70, waistToHip = 0.78),
            Sex.MALE,
        )

        assertNotNull(estimate)
        assertTrue(estimate.percent < 10.0, "got ${estimate.percent}")
    }

    @Test
    fun `the hip mapping is monotonic`() {
        val percents = listOf(0.78, 0.85, 0.92, 1.00).map { hip ->
            SilhouetteBodyFat.estimate(ShapeIndices(0.80, hip), Sex.MALE)?.percent
        }.filterNotNull()

        assertEquals(4, percents.size)
        assertTrue(percents.zipWithNext().all { (a, b) -> b > a }, "$percents")
    }

    @Test
    fun `without a hip the shoulder stands in, carrying a wider interval`() {
        // A photograph cropped below the hips has nothing else. The number is still given,
        // and the interval says how much less it is worth.
        val withHip = SilhouetteBodyFat.estimate(ShapeIndices(0.90, 0.87), Sex.MALE)
        val without = SilhouetteBodyFat.estimate(ShapeIndices(0.90, null), Sex.MALE)

        assertNotNull(withHip)
        assertNotNull(without)
        assertTrue(
            without.standardErrorPercent > withHip.standardErrorPercent,
            "${without.standardErrorPercent} vs ${withHip.standardErrorPercent}",
        )
    }

    @Test
    fun `the shoulder plateau survives, but only on the shoulder path`() {
        // The flatness was measured on the waist-to-shoulder ratio off the reference charts,
        // so it belongs to that ratio and nowhere else. A hip-based reading in the same
        // numeric region must not inherit a plateau it was never shown to have.
        val shoulderOnly = SilhouetteBodyFat.estimate(ShapeIndices(0.70, null), Sex.MALE)
        val hipBased = SilhouetteBodyFat.estimate(ShapeIndices(0.70, 0.87), Sex.MALE)

        assertNotNull(shoulderOnly)
        assertNotNull(hipBased)
        assertEquals(
            SilhouetteBodyFat.PLATEAU_ERROR_PERCENT,
            shoulderOnly.standardErrorPercent,
            1e-9,
        )
        assertTrue(hipBased.standardErrorPercent < SilhouetteBodyFat.PLATEAU_ERROR_PERCENT)
    }

    @Test
    fun `an implausible hip falls back rather than producing an extreme`() {
        // A hip search that landed on a chair or a shadow. Better the contaminated shoulder
        // than a ratio that is not a body.
        val estimate = SilhouetteBodyFat.estimate(
            ShapeIndices(waistToShoulder = 0.90, waistToHip = 3.0),
            Sex.MALE,
        )

        assertNotNull(estimate)
        assertEquals(
            SilhouetteBodyFat.estimate(ShapeIndices(0.90, null), Sex.MALE)!!.percent,
            estimate.percent,
            1e-9,
        )
    }

    @Test
    fun `women map differently on the hip too`() {
        val indices = ShapeIndices(waistToShoulder = 0.80, waistToHip = 0.85)

        val male = SilhouetteBodyFat.estimate(indices, Sex.MALE)
        val female = SilhouetteBodyFat.estimate(indices, Sex.FEMALE)

        assertNotNull(male)
        assertNotNull(female)
        assertTrue(female.percent > male.percent, "${female.percent} vs ${male.percent}")
    }
}
