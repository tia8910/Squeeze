package com.squeeze.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The row ranges each trunk measurement is read over.
 *
 * These were computed inside the estimator and discarded the moment a width came out of them,
 * which made them the least inspectable quantity in the pipeline and, not coincidentally, the
 * source of nearly every wrong answer it produced. The important test here is the last one:
 * that the bands this object reports are the bands the estimator actually measures. A lab
 * drawing bands that merely resemble the measured ones would be worth nothing.
 */
class MeasurementBandsTest {

    private val shoulderRow = 100
    private val hipRow = 300
    private val trunk = hipRow - shoulderRow

    /** Torso framing: the knee is not in shot, so its row is synthesised far below. */
    private val synthesisedKnee = PoseAnchors(
        shoulderRow = shoulderRow,
        hipRow = hipRow,
        kneeRow = hipRow + (trunk * 1.2).toInt(),
        chinRow = 40,
    )

    private val observedKnee = PoseAnchors(shoulderRow, hipRow, kneeRow = 380, chinRow = 40)

    @Test
    fun `a trunk with no height has no bands`() {
        // A cropped or mis-detected pose can put the hips at or above the shoulders. Better
        // no bands than three degenerate ones that still produce a number.
        assertNull(TrunkBands.from(PoseAnchors(200, 200, 300, 100)))
        assertNull(TrunkBands.from(PoseAnchors(250, 200, 300, 100)))
    }

    @Test
    fun `the waist band is at the navel, below the ribs and above the hip`() {
        val bands = TrunkBands.from(synthesisedKnee)
        assertNotNull(bands)

        // 58% to 74% of the way down the trunk. 115 rather than 116 because 0.58 has no
        // exact double and 200 * 0.58 lands a hair under 116; truncation is the behaviour
        // being pinned here, not an accident to be rounded away.
        assertEquals(shoulderRow + 115, bands.waist.fromRow)
        assertEquals(shoulderRow + 148, bands.waist.toRowInclusive)
        assertTrue(bands.waist.fromRow > bands.shoulder.toRowInclusive)
        assertTrue(bands.waist.toRowInclusive < bands.hip.fromRow)
    }

    @Test
    fun `the shoulder band starts at the joint line and reaches the deltoid`() {
        val bands = TrunkBands.from(synthesisedKnee)
        assertNotNull(bands)

        assertEquals(shoulderRow, bands.shoulder.fromRow)
        assertEquals(shoulderRow + 40, bands.shoulder.toRowInclusive)
    }

    @Test
    fun `the hip band is shallow, and no deeper when the knee is only assumed`() {
        // The 6.83% failure: at torso framing the knee row is synthesised from the trunk, so
        // a band defined purely as a fraction of hip-to-knee is a fraction of an assumption
        // and reaches wherever that assumption puts it — in that case, into the shorts.
        val synthesised = TrunkBands.from(synthesisedKnee)
        val observed = TrunkBands.from(observedKnee)

        assertNotNull(synthesised)
        assertNotNull(observed)

        assertEquals(hipRow, synthesised.hip.fromRow)
        // min(10% of hip-to-knee, 12% of trunk) — the trunk cap wins on the synthesised one.
        assertEquals(24, synthesised.hip.rowCount - 1)
        assertEquals(8, observed.hip.rowCount - 1)
        assertTrue(synthesised.hip.rowCount <= (trunk * 0.12).toInt() + 1)
    }

    @Test
    fun `a band always has at least one row`() {
        // A hip band of zero depth would make medianBetween look at nothing and the hip
        // denominator vanish, silently, on any pose whose knee sits almost at the hip.
        val flat = TrunkBands.from(PoseAnchors(shoulderRow, hipRow, hipRow + 1, 40))

        assertNotNull(flat)
        assertTrue(flat.hip.rowCount >= 1)
        assertTrue(flat.waist.rowCount >= 1)
        assertTrue(flat.shoulder.rowCount >= 1)
    }

    @Test
    fun `the estimator measures the bands this reports`() {
        // The anti-drift test, and the reason this object exists. A profile is built so that
        // each band covers exactly one distinctive width and everything outside the three
        // bands is absurd. If the estimator read anywhere else, the ratios would show it.
        val bands = TrunkBands.from(synthesisedKnee)
        assertNotNull(bands)

        val rows = 500
        val widths = DoubleArray(rows) { 0.99 }
        for (row in bands.shoulder.fromRow..bands.shoulder.toRowInclusive) widths[row] = 0.40
        for (row in bands.waist.fromRow..bands.waist.toRowInclusive) widths[row] = 0.30
        for (row in bands.hip.fromRow..bands.hip.toRowInclusive) widths[row] = 0.32

        val indices = SilhouetteBodyFat.indicesFrom(
            WidthProfile(widths, DoubleArray(rows), 20, 480),
            synthesisedKnee,
            pelvisSpan = 0.32 / 1.6,
        )

        assertNotNull(indices)
        assertEquals(0.30 / 0.40, indices.waistToShoulder, 1e-9)
        assertEquals(0.30 / 0.32, indices.waistToHip!!, 1e-9)
    }

    @Test
    fun `a band cannot end above where it starts`() {
        assertFailsWith<IllegalArgumentException> {
            MeasurementBand(fromRow = 10, toRowInclusive = 9)
        }
    }
}
