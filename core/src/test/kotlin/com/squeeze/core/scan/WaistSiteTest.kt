package com.squeeze.core.scan

import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where the waist is read, and why it is no longer read at the narrowest row.
 *
 * The narrowest row is where the fat is not. On a lean torso the minimum sits high, under the
 * ribs and above the abdomen; on a heavy one it sits in nearly the same place, and the belly
 * that accumulated below it is never measured. Searching for a minimum measures the part of
 * the trunk adiposity moves least — which is why the ratio read flat across a labelled range
 * from 8% to 35%, and why a real scan produced a floor value of 8.00%.
 *
 * On one real photograph the two sites gave 0.552 and 0.759. Through the same anchors that is
 * −7% and 11.5%.
 */
class WaistSiteTest {

    private val rows = 400
    private val shoulderRow = 80
    private val hipRow = 280
    private val anchors = PoseAnchors(shoulderRow, hipRow, kneeRow = 360, chinRow = 60)

    /**
     * A torso whose narrowest point sits under the ribs, with a belly below it.
     *
     * The shape of every real midsection that carries fat, and the one the old search could
     * not see: it would find [ribWidth] and never look at [bellyWidth].
     */
    private fun trunk(
        shoulderWidth: Double,
        ribWidth: Double,
        bellyWidth: Double,
        hipWidth: Double = 0.34,
    ): WidthProfile {
        val widths = DoubleArray(rows) { 0.05 }
        val span = hipRow - shoulderRow
        for (row in shoulderRow..shoulderRow + (span * 0.20).toInt()) widths[row] = shoulderWidth
        // The rib notch: high, narrow, and irrelevant to adiposity.
        for (row in shoulderRow + (span * 0.28).toInt()..shoulderRow + (span * 0.44).toInt()) {
            widths[row] = ribWidth
        }
        // The abdomen, at the navel.
        for (row in shoulderRow + (span * 0.56).toInt()..shoulderRow + (span * 0.78).toInt()) {
            widths[row] = bellyWidth
        }
        for (row in shoulderRow + (span * 0.80).toInt() until rows) widths[row] = hipWidth
        return WidthProfile(widths, DoubleArray(rows), 20, 390)
    }

    @Test
    fun `the waist is read at the belly, not at the rib notch`() {
        val indices = SilhouetteBodyFat.indicesFrom(
            trunk(shoulderWidth = 0.40, ribWidth = 0.22, bellyWidth = 0.30),
            anchors,
        )

        assertNotNull(indices)
        // 0.30 / 0.40, not 0.22 / 0.40.
        assertEquals(0.75, indices.waistToShoulder, 0.01)
    }

    @Test
    fun `a belly that grows moves the answer, which the old site could not`() {
        // The property the whole change exists for. The rib notch is held fixed across all
        // three bodies — as it is in life — and only the abdomen differs. Under a
        // narrowest-row search all three would read identically.
        val percents = listOf(0.26, 0.32, 0.38).map { belly ->
            val indices = SilhouetteBodyFat.indicesFrom(
                trunk(shoulderWidth = 0.40, ribWidth = 0.22, bellyWidth = belly),
                anchors,
            )
            assertNotNull(indices)
            SilhouetteBodyFat.estimate(indices, Sex.MALE)?.percent
        }.filterNotNull()

        assertEquals(3, percents.size)
        assertTrue(percents.zipWithNext().all { (a, b) -> b > a }, "$percents")
    }

    @Test
    fun `the rib notch no longer reaches the answer at all`() {
        // Two bodies with the same abdomen and very different ribcages must read the same.
        // Under the old search they differed by everything.
        val deepNotch = SilhouetteBodyFat.indicesFrom(trunk(0.40, 0.18, 0.30), anchors)
        val shallowNotch = SilhouetteBodyFat.indicesFrom(trunk(0.40, 0.28, 0.30), anchors)

        assertNotNull(deepNotch)
        assertNotNull(shallowNotch)
        assertEquals(deepNotch.waistToShoulder, shallowNotch.waistToShoulder, 1e-9)
    }

    @Test
    fun `the real scan that produced 8 percent now lands where it should`() {
        // Reconstructed from the measured photograph: a shoulder of 0.40, a rib notch that a
        // narrowest-row search returned as 0.552 of it, and an abdomen at 0.759 of it.
        val indices = SilhouetteBodyFat.indicesFrom(
            trunk(shoulderWidth = 0.40, ribWidth = 0.221, bellyWidth = 0.304),
            anchors,
        )
        assertNotNull(indices)

        val estimate = SilhouetteBodyFat.estimate(indices, Sex.MALE)

        assertNotNull(estimate)
        // The hip ratio pulls the fused figure a little above the shoulder-derived 11.6.
        assertTrue(estimate.percent in 9.0..17.0, "got ${estimate.percent}")
    }

    @Test
    fun `a median is used, so one bad row cannot decide the waist`() {
        // Segmentation drops a row now and then. An extremum search hands that row the whole
        // measurement; a median absorbs it.
        val widths = DoubleArray(rows) { 0.05 }
        val span = hipRow - shoulderRow
        for (row in shoulderRow..shoulderRow + (span * 0.20).toInt()) widths[row] = 0.40
        for (row in shoulderRow + (span * 0.56).toInt()..shoulderRow + (span * 0.78).toInt()) {
            widths[row] = 0.30
        }
        widths[shoulderRow + (span * 0.65).toInt()] = 0.60 // a mask blowout

        val indices = SilhouetteBodyFat.indicesFrom(
            WidthProfile(widths, DoubleArray(rows), 20, 390),
            anchors,
        )

        assertNotNull(indices)
        assertEquals(0.75, indices.waistToShoulder, 0.01)
    }

    @Test
    fun `an entirely clipped abdomen still yields nothing`() {
        // The arm guard has to survive the site change: a trunk whose abdominal band was cut
        // back by the pose bound has no waist, and must not fall through to a median of the
        // bound's own widths.
        val widths = DoubleArray(rows) { 0.30 }
        val clipped = BooleanArray(rows)
        val span = hipRow - shoulderRow
        for (row in shoulderRow + (span * 0.55).toInt()..shoulderRow + (span * 0.80).toInt()) {
            clipped[row] = true
        }

        assertNull(
            SilhouetteBodyFat.indicesFrom(
                WidthProfile(widths, DoubleArray(rows), 20, 390, clipped),
                anchors,
            ),
        )
    }

    @Test
    fun `medianBetween ignores clipped and empty rows`() {
        val widths = doubleArrayOf(0.0, 0.10, 0.20, 0.30, 0.40, 0.0, 0.0, 0.0, 0.0, 0.0)
        val clipped = BooleanArray(10)
        clipped[4] = true
        val profile = WidthProfile(widths, DoubleArray(10), 1, 9, clipped)

        // 0.10, 0.20, 0.30 survive; the median of three is the middle one.
        assertEquals(0.20, AnatomicalLevelFinder.medianBetween(profile, 0, 5)!!, 1e-9)
    }

    @Test
    fun `medianBetween returns nothing when the band is empty`() {
        val profile = WidthProfile(DoubleArray(10), DoubleArray(10), 1, 9)

        assertNull(AnatomicalLevelFinder.medianBetween(profile, 2, 6))
    }
}
