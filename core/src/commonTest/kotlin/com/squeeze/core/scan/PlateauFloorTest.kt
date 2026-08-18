package com.squeeze.core.scan

import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the shoulder path reports when the ratio is below its own data.
 *
 * The mapping is a two-point anchor fitted between 0.72 and 1.02. Below 0.72 it is an
 * extrapolation of a line through a region the reference charts show to be **flat**: the
 * waist-to-shoulder ratio reads 0.586 at eight per cent, 0.592 at twelve and 0.580 at
 * fifteen. Flat means the ratio does not distinguish those bodies at all.
 *
 * Running the line on anyway converts "cannot tell" into a specific small number, and the
 * further below the anchor the ratio sits, the more confident the falsehood gets.
 *
 * A real scan made that concrete. Both arms merged into the shoulder run and gave 0.686 —
 * which is not a lean body, it is a contaminated denominator — and the app reported **4.93%**
 * under a "below essential" label, for a man with no abdominal definition whatsoever.
 */
class PlateauFloorTest {

    private fun shoulderOnly(ratio: Double, sex: Sex = Sex.MALE) =
        SilhouetteBodyFat.estimate(ShapeIndices(waistToShoulder = ratio, waistToHip = null), sex)

    @Test
    fun `the scan that reported below essential now reports the plateau`() {
        val estimate = shoulderOnly(0.686)

        assertNotNull(estimate)
        assertEquals(SilhouetteBodyFat.plateauCeilingPercent(Sex.MALE), estimate.percent, 1e-9)
        // The old extrapolation gave 4.93. Anything under the essential-fat line is a claim
        // this method has never been able to support.
        assertTrue(estimate.percent > 8.0, "got ${estimate.percent}")
    }

    @Test
    fun `nothing on the plateau reads below essential fat, however low the ratio goes`() {
        // The failure scaled with the contamination: the more arm in the shoulder band, the
        // smaller the ratio and the more alarming the number. Now it cannot move at all.
        listOf(0.75, 0.70, 0.65, 0.60, 0.55).forEach { ratio ->
            val estimate = shoulderOnly(ratio)
            assertNotNull(estimate, "ratio $ratio")
            assertEquals(
                SilhouetteBodyFat.plateauCeilingPercent(Sex.MALE),
                estimate.percent,
                1e-9,
                "ratio $ratio",
            )
        }
    }

    @Test
    fun `the plateau still says how little it knows`() {
        val estimate = shoulderOnly(0.686)

        assertNotNull(estimate)
        assertEquals(SilhouetteBodyFat.PLATEAU_ERROR_PERCENT, estimate.standardErrorPercent, 1e-9)
    }

    @Test
    fun `above the plateau the mapping is unchanged`() {
        // The fix must not flatten the region where the ratio was actually measured to
        // carry information, or the method would stop responding to real change.
        val percents = listOf(0.80, 0.88, 0.96, 1.02).map {
            val estimate = shoulderOnly(it)
            assertNotNull(estimate, "ratio $it")
            estimate.percent
        }

        assertTrue(percents.zipWithNext().all { (a, b) -> b > a }, "$percents")
        assertTrue(percents.first() > SilhouetteBodyFat.plateauCeilingPercent(Sex.MALE))
        // The top anchor, less the observed offset every reading now carries.
        assertEquals(
            35.0 - SilhouetteBodyFat.OBSERVED_OFFSET_PERCENT,
            percents.last(),
            0.5,
        )
    }

    @Test
    fun `women get their own floor`() {
        val estimate = shoulderOnly(0.60, Sex.FEMALE)

        assertNotNull(estimate)
        assertEquals(SilhouetteBodyFat.plateauCeilingPercent(Sex.FEMALE), estimate.percent, 1e-9)
        assertTrue(
            estimate.percent > SilhouetteBodyFat.plateauCeilingPercent(Sex.MALE),
            "a woman's floor cannot sit at a man's",
        )
    }

    @Test
    fun `the hip path is floored too, because it fails the same way`() {
        // Written three times now, and back where it started, which is worth recording.
        // It began as "a hip-based reading is untouched by any of this", on the reasoning that
        // the flatness was measured on the shoulder ratio and belonged to it. That was widened
        // to cover every path. It was then narrowed again to exempt a hip the scan had
        // checked — and that exemption shipped and produced 4.76% for a soft midsection in
        // loose trousers and 3.00% for a bodybuilder whose hip band spanned two spread thighs.
        //
        // The floor is not a claim about which ratio is flat. It is a claim about which
        // direction this method fails in, and that direction is the same on every path: a
        // mismeasured width is always wider, and a wider denominator always reads lean. See
        // CorroboratedHipTest.
        //
        // Left as a hip reading of 0.78 that used to produce 5.92%.
        val estimate = SilhouetteBodyFat.estimate(
            ShapeIndices(waistToShoulder = 0.70, waistToHip = 0.78),
            Sex.MALE,
        )

        assertNotNull(estimate)
        assertEquals(SilhouetteBodyFat.leanestClaimable(Sex.MALE), estimate.percent, 1e-9)
    }

    @Test
    fun `no photograph of any body produces a single-digit figure`() {
        // The property the user asked for, stated directly: whatever the ratios, however
        // contaminated, the outline alone never tells someone with no visible condition that
        // they are six per cent.
        val ratios = listOf(0.40, 0.55, 0.65, 0.70, 0.75, 0.80, 0.90, 1.00, 1.20)

        for (shoulder in ratios) {
            for (hip in ratios + listOf(null)) {
                Sex.entries.forEach { sex ->
                    val estimate = SilhouetteBodyFat.estimate(
                        ShapeIndices(shoulder, hip),
                        sex,
                    ) ?: return@forEach

                    assertTrue(
                        estimate.percent >= SilhouetteBodyFat.leanestClaimable(sex),
                        "shoulder=$shoulder hip=$hip $sex gave ${estimate.percent}",
                    )
                }
            }
        }
    }
}
