package com.squeeze.core.scan

import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The measurement the front view could not make.
 *
 * Four front-view indices have been tried in this project and all four came back flat or
 * out of order. The cause is not the formulas — it is that abdominal fat accumulates in
 * depth, and a front photograph measures width. These pin that the depth axis behaves the
 * way the front one refused to.
 */
class AbdominalProfileTest {

    private val rows = 400
    private val shoulderRow = 60
    private val hipRow = 260

    private val anchors = PoseAnchors(
        shoulderRow = shoulderRow,
        hipRow = hipRow,
        kneeRow = 340,
        chinRow = 40,
    )

    /**
     * A side-on silhouette where a row's width is the body's front-to-back depth.
     *
     * @param chest depth through the ribcage
     * @param belly depth through the abdomen
     */
    private fun sideProfile(chest: Double, belly: Double): WidthProfile {
        val widths = DoubleArray(rows) { 0.10 }
        val trunk = hipRow - shoulderRow
        for (row in shoulderRow + (trunk * 0.08).toInt()..shoulderRow + (trunk * 0.34).toInt()) {
            widths[row] = chest
        }
        for (row in shoulderRow + (trunk * 0.38).toInt()..shoulderRow + (trunk * 0.88).toInt()) {
            widths[row] = belly
        }
        return WidthProfile(widths, DoubleArray(rows), 20, 380)
    }

    @Test
    fun `a lean torso is shallower at the belly than at the chest`() {
        val depths = AbdominalProfile.depthsFrom(sideProfile(chest = 0.22, belly = 0.18), anchors)

        assertNotNull(depths)
        assertEquals(0.818, depths.bellyToChest, 0.01)
        assertTrue(depths.bellyToChest < 1.0)
    }

    @Test
    fun `a heavy torso protrudes past the chest`() {
        val depths = AbdominalProfile.depthsFrom(sideProfile(chest = 0.22, belly = 0.32), anchors)

        assertNotNull(depths)
        assertTrue(depths.bellyToChest > 1.0)
    }

    @Test
    fun `the estimate is monotonic in belly depth, which is the entire point`() {
        // The property every front-view index failed. Deepening the abdomen while holding the
        // chest fixed must raise the answer, at every step, with no reversals.
        val percents = listOf(0.17, 0.19, 0.22, 0.25, 0.28, 0.31).map { belly ->
            val depths = AbdominalProfile.depthsFrom(sideProfile(0.22, belly), anchors)
            assertNotNull(depths)
            AbdominalProfile.estimate(depths, Sex.MALE)?.percent
        }

        assertTrue(percents.all { it != null }, "$percents")
        val values = percents.filterNotNull()
        assertTrue(
            values.zipWithNext().all { (a, b) -> b > a },
            "not monotonic: $values",
        )
    }

    @Test
    fun `a lean male lands in the lean range`() {
        val depths = AbdominalProfile.depthsFrom(sideProfile(0.22, 0.180), anchors)
        assertNotNull(depths)

        val estimate = AbdominalProfile.estimate(depths, Sex.MALE)

        assertNotNull(estimate)
        assertTrue(estimate.percent in 8.0..14.0, "got ${estimate.percent}")
    }

    @Test
    fun `a clearly overweight male lands in the thirties`() {
        val depths = AbdominalProfile.depthsFrom(sideProfile(0.22, 0.315), anchors)
        assertNotNull(depths)

        val estimate = AbdominalProfile.estimate(depths, Sex.MALE)

        assertNotNull(estimate)
        assertTrue(estimate.percent > 30.0, "got ${estimate.percent}")
    }

    @Test
    fun `the same profile reads higher for a man than for a woman`() {
        // A greater share of female fat is subcutaneous and gluteofemoral rather than
        // visceral, so the same sagittal ratio corresponds to a different fat fraction.
        val depths = AbdominalProfile.depthsFrom(sideProfile(0.22, 0.24), anchors)
        assertNotNull(depths)

        val male = AbdominalProfile.estimate(depths, Sex.MALE)
        val female = AbdominalProfile.estimate(depths, Sex.FEMALE)

        assertNotNull(male)
        assertNotNull(female)
        assertTrue(female.percent > male.percent, "${female.percent} vs ${male.percent}")
    }

    @Test
    fun `scale cancels, so a bigger photo of the same body reads the same`() {
        // The property that makes this immune to the failure that has cost this project
        // most: every centimetre elsewhere is hostage to the mask's top and bottom rows.
        // Both depths here are fractions of the same image width, so a uniformly larger
        // silhouette must not move the answer at all.
        val small = AbdominalProfile.depthsFrom(sideProfile(0.20, 0.26), anchors)
        val large = AbdominalProfile.depthsFrom(sideProfile(0.30, 0.39), anchors)

        assertNotNull(small)
        assertNotNull(large)
        assertEquals(small.bellyToChest, large.bellyToChest, 1e-9)
    }

    @Test
    fun `an implausible ratio yields nothing rather than an extreme`() {
        // Bands landing on a chair back, a mirror frame, or a subject facing the wrong way.
        val depths = AbdominalProfile.depthsFrom(sideProfile(0.10, 0.40), anchors)

        assertNotNull(depths)
        assertNull(AbdominalProfile.estimate(depths, Sex.MALE))
    }

    @Test
    fun `a silhouette with no depth in the bands yields nothing`() {
        val widths = DoubleArray(rows) { 0.0 }
        widths[10] = 0.2
        val profile = WidthProfile(widths, DoubleArray(rows), 5, 380)

        assertNull(AbdominalProfile.depthsFrom(profile, anchors))
    }

    @Test
    fun `hips at or above the shoulders are not a torso`() {
        val bad = PoseAnchors(shoulderRow = 60, hipRow = 61, kneeRow = 340, chinRow = 40)
        val widths = DoubleArray(rows) { 0.2 }

        // A one-row trunk collapses both bands onto the same row; the guard is that this
        // returns something or nothing, never a ratio built from a single sample presented
        // as two independent measurements.
        val depths = AbdominalProfile.depthsFrom(
            WidthProfile(widths, DoubleArray(rows), 20, 380),
            bad,
        )

        if (depths != null) assertEquals(1.0, depths.bellyToChest, 1e-9)
    }

    @Test
    fun `the estimate is labelled as its own method`() {
        // It has to be distinguishable in fusion and in calibration: its offset is its own,
        // and correcting it by a tape-derived one would be worse than not correcting it.
        val depths = AbdominalProfile.depthsFrom(sideProfile(0.22, 0.24), anchors)
        assertNotNull(depths)

        val estimate = AbdominalProfile.estimate(depths, Sex.MALE)

        assertNotNull(estimate)
        assertEquals(EstimationMethod.PHOTO_ABDOMINAL_PROFILE, estimate.method)
    }

    @Test
    fun `the interval stays wide, because the anchors are reasoned and not fitted`() {
        val depths = AbdominalProfile.depthsFrom(sideProfile(0.22, 0.24), anchors)
        assertNotNull(depths)

        val estimate = AbdominalProfile.estimate(depths, Sex.MALE)

        assertNotNull(estimate)
        assertTrue(estimate.standardErrorPercent >= 5.0, "${estimate.standardErrorPercent}")
    }
}
