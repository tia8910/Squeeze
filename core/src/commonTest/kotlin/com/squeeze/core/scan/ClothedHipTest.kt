package com.squeeze.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The hip denominator, and the clothes that were being measured instead of it.
 *
 * A mirror selfie of a man with a soft midsection, in loose shorts, framed at the waistband,
 * read **6.83%**. Two faults compounded:
 *
 *  1. The hip band reached 18% of the hip-to-knee distance below the hip landmark. At torso
 *     framing the knee row is synthesised at 1.2 trunk lengths below the hip, so that band
 *     was a fifth of a trunk length — entirely inside the shorts.
 *  2. Inside it the search took the **maximum**. A maximum over a region containing clothing
 *     does not sample clothing occasionally, it selects for it: the baggiest row is by
 *     construction the widest one.
 *
 * An inflated denominator reads lean. The fix is a tight band, a median, and a check against
 * the one thing in the photograph that clothing cannot change — where the pose model put the
 * hip joints.
 */
class ClothedHipTest {

    private val rows = 400
    private val shoulderRow = 80
    private val hipRow = 280

    /** Torso framing: the knee is not in the picture, so its row is synthesised. */
    private val torsoAnchors = PoseAnchors(
        shoulderRow = shoulderRow,
        hipRow = hipRow,
        kneeRow = hipRow + ((hipRow - shoulderRow) * 1.2).toInt(),
        chinRow = 30,
    )

    /**
     * A trunk with a belly, and baggy shorts starting a little below the hip.
     *
     * The shorts are wider than the hip, which is what makes them shorts.
     */
    private fun clothedTrunk(
        bellyWidth: Double = 0.30,
        hipWidth: Double = 0.32,
        shortsWidth: Double = 0.44,
    ): WidthProfile {
        val widths = DoubleArray(rows) { 0.05 }
        val span = hipRow - shoulderRow
        for (row in shoulderRow..shoulderRow + (span * 0.20).toInt()) widths[row] = 0.40
        for (row in shoulderRow + (span * 0.56).toInt()..shoulderRow + (span * 0.78).toInt()) {
            widths[row] = bellyWidth
        }
        for (row in shoulderRow + (span * 0.80).toInt()..hipRow + (span * 0.10).toInt()) {
            widths[row] = hipWidth
        }
        // The waistband and everything below it.
        for (row in hipRow + (span * 0.10).toInt() until rows) widths[row] = shortsWidth
        return WidthProfile(widths, DoubleArray(rows), 20, 390)
    }

    /** Hip joints sit well inside the hip's own silhouette; here at a ratio of 1.6. */
    private val pelvisSpan = 0.32 / 1.6

    @Test
    fun `loose shorts no longer become the hip`() {
        val indices = SilhouetteBodyFat.indicesFrom(clothedTrunk(), torsoAnchors, pelvisSpan)

        assertNotNull(indices)
        val ratio = indices.waistToHip
        assertNotNull(ratio)
        // 0.30 / 0.32, not 0.30 / 0.44.
        assertEquals(0.94, ratio, 0.02)
    }

    @Test
    fun `and the answer moves out of the single digits because of it`() {
        val indices = SilhouetteBodyFat.indicesFrom(clothedTrunk(), torsoAnchors, pelvisSpan)
        assertNotNull(indices)

        val estimate = SilhouetteBodyFat.estimate(indices, com.squeeze.core.model.Sex.MALE)

        assertNotNull(estimate)
        // The reading this test exists for was 6.83. Anything in single digits for a trunk
        // with a belly this size is the same failure back again.
        assertTrue(estimate.percent > 12.0, "got ${estimate.percent}")
    }

    @Test
    fun `a hip far wider than the pelvis under it is refused outright`() {
        // The backstop for the case the tighter band does not catch: a waistband sitting
        // exactly at the hip line, or laundry merged into the mask beside the subject.
        val widths = DoubleArray(rows) { 0.05 }
        val span = hipRow - shoulderRow
        for (row in shoulderRow..shoulderRow + (span * 0.20).toInt()) widths[row] = 0.40
        for (row in shoulderRow + (span * 0.56).toInt()..shoulderRow + (span * 0.78).toInt()) {
            widths[row] = 0.30
        }
        for (row in shoulderRow + (span * 0.80).toInt() until rows) widths[row] = 0.60

        val indices = SilhouetteBodyFat.indicesFrom(
            WidthProfile(widths, DoubleArray(rows), 20, 390),
            torsoAnchors,
            pelvisSpan = 0.20,
        )

        assertNotNull(indices)
        // 0.60 / 0.20 is three times the pelvis. Whatever that is, it is not a hip.
        assertNull(indices.waistToHip)
    }

    @Test
    fun `an ordinary hip passes the check untouched`() {
        val clean = SilhouetteBodyFat.indicesFrom(
            clothedTrunk(shortsWidth = 0.32),
            torsoAnchors,
            pelvisSpan,
        )
        val unchecked = SilhouetteBodyFat.indicesFrom(
            clothedTrunk(shortsWidth = 0.32),
            torsoAnchors,
            pelvisSpan = null,
        )

        assertNotNull(clean)
        assertNotNull(unchecked)
        assertEquals(unchecked.waistToHip, clean.waistToHip)
        assertNotNull(clean.waistToHip)
    }

    @Test
    fun `without pose geometry nothing is vetoed`() {
        // Older scans and any path that loses the landmarks must behave as they did, rather
        // than silently losing their hip denominator.
        val indices = SilhouetteBodyFat.indicesFrom(clothedTrunk(), torsoAnchors, null)

        assertNotNull(indices)
        assertNotNull(indices.waistToHip)
    }

    @Test
    fun `the hip reading does not depend on where the knee is assumed to be`() {
        // Torso framing synthesises the knee row from the trunk length, so a band defined
        // purely as a fraction of hip-to-knee is a fraction of an assumption. Moving that
        // assumption must not move the measurement.
        val observedKnee = PoseAnchors(shoulderRow, hipRow, kneeRow = 399, chinRow = 30)

        val synthesised = SilhouetteBodyFat.indicesFrom(clothedTrunk(), torsoAnchors, pelvisSpan)
        val observed = SilhouetteBodyFat.indicesFrom(clothedTrunk(), observedKnee, pelvisSpan)

        assertNotNull(synthesised)
        assertNotNull(observed)
        assertEquals(synthesised.waistToHip, observed.waistToHip)
    }
}
