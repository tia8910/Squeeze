package com.squeeze.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bug these cover, stated once.
 *
 * When an arm rests against the torso the mask has one run, not three, so [TrunkBounds] cuts
 * it back to where the skeleton says the trunk can reach. That is right. What was wrong is
 * what happened next: the cut width was recorded as a measurement, and the cut width is a
 * function of the landmark spans and two margin constants — it narrows smoothly from the
 * shoulders to the hips for every human being.
 *
 * So the waist search, which looks for the narrowest row, always landed at the bottom of the
 * band; the hip search always landed on the bound at its most generous; and the resulting
 * ratios put nearly every adult male in the high teens whatever their actual body fat. The
 * app reported nineteen per cent to a body near ten, twice, from different photographs.
 */
class ArmClearanceTest {

    private val rows = 200
    private val shoulderRow = 50
    private val hipRow = 110
    private val kneeRow = 150

    private val anchors = PoseAnchors(
        shoulderRow = shoulderRow,
        hipRow = hipRow,
        kneeRow = kneeRow,
        chinRow = 35,
    )

    /**
     * A profile whose trunk band is entirely the bound's own shape.
     *
     * The widths are what [TrunkBounds.maxHalfWidthAt] produces for a typical male frame, so
     * this is not a hypothetical: it is what the pipeline actually recorded for a photograph
     * taken with the arms down.
     */
    private fun clippedTrunk(): WidthProfile {
        val widths = DoubleArray(rows) { 0.2 }
        val clipped = BooleanArray(rows)
        val bounds = TrunkBounds(
            shoulderLeftX = 0.34,
            shoulderRightX = 0.66,
            shoulderRow = shoulderRow,
            hipLeftX = 0.41,
            hipRightX = 0.59,
            hipRow = hipRow,
        )
        for (row in shoulderRow..hipRow) {
            widths[row] = 2.0 * bounds.maxHalfWidthAt(row)
            clipped[row] = true
        }
        // Below the hips the bound holds its hip value, and the hip search reads there.
        for (row in hipRow..kneeRow) {
            widths[row] = 2.0 * bounds.maxHalfWidthAt(row)
            clipped[row] = true
        }
        return WidthProfile(widths, DoubleArray(rows), 10, 190, clipped)
    }

    @Test
    fun `the trunk bound narrows monotonically, which is why it faked a waist`() {
        // The mechanism, asserted directly. Any band of fully clipped rows has its minimum at
        // the bottom, so "narrowest row between shoulders and hips" returns the hip line for
        // every person alive — a waist reading that cannot vary with the body.
        val bounds = TrunkBounds(0.34, 0.66, shoulderRow, 0.41, 0.59, hipRow)

        val widths = (shoulderRow..hipRow).map { bounds.maxHalfWidthAt(it) }

        assertTrue(
            widths.zipWithNext().all { (above, below) -> below <= above },
            "bound must narrow downward for this failure mode to be the one described",
        )
        assertEquals(widths.min(), widths.last(), 1e-9, "minimum sits at the hip line")
    }

    @Test
    fun `a fully clipped trunk yields no waist at all`() {
        // The fix. Rather than measuring the bound, the search finds nothing and the caller
        // is left with no shape estimate — which is the truthful output for a photograph
        // that does not contain a visible waist.
        val row = AnatomicalLevelFinder.narrowestBetween(clippedTrunk(), shoulderRow, hipRow)

        assertNull(row)
    }

    @Test
    fun `and therefore no shape indices, rather than the same answer for everybody`() {
        assertNull(SilhouetteBodyFat.indicesFrom(clippedTrunk(), anchors))
    }

    @Test
    fun `a clean silhouette is measured exactly as before`() {
        // The guard must not cost anything on a good photograph. Arms held clear leave the
        // torso as its own run, nothing is cut, and every row stays eligible.
        val widths = DoubleArray(rows) { 0.30 }
        for (row in shoulderRow..hipRow) widths[row] = 0.22
        widths[80] = 0.18

        val profile = WidthProfile(widths, DoubleArray(rows), 10, 190)

        assertEquals(80, AnatomicalLevelFinder.narrowestBetween(profile, shoulderRow, hipRow))
    }

    @Test
    fun `a partly clipped trunk measures the rows that survived`() {
        // A hand brushing a hip clips the bottom of the band and leaves the waist itself
        // intact. Refusing the whole scan for that would be as wrong as trusting all of it.
        val widths = DoubleArray(rows) { 0.30 }
        for (row in shoulderRow..hipRow) widths[row] = 0.22
        widths[70] = 0.19

        val clipped = BooleanArray(rows)
        for (row in 100..hipRow) clipped[row] = true
        // The clipped rows are narrower than the real waist, so without the skip they would
        // win the search outright.
        for (row in 100..hipRow) widths[row] = 0.15

        val profile = WidthProfile(widths, DoubleArray(rows), 10, 190, clipped)

        assertEquals(70, AnatomicalLevelFinder.narrowestBetween(profile, shoulderRow, hipRow))
    }

    @Test
    fun `the widest search skips clipped rows too`() {
        // The hip reading is the other half of the ratio, and the bound is at its most
        // generous exactly there — the margin doubles at the hip to clear the glutes. A
        // clipped row would win a widest-search on nearly every scan.
        val widths = DoubleArray(rows) { 0.20 }
        widths[120] = 0.40
        val clipped = BooleanArray(rows)
        clipped[120] = true

        val profile = WidthProfile(widths, DoubleArray(rows), 10, 190, clipped)

        val row = AnatomicalLevelFinder.widestBetween(profile, hipRow, kneeRow)

        assertTrue(row != 120, "the bound's own width must never be reported as a hip")
    }

    @Test
    fun `leg widths are not skipped, because the bound never touches them`() {
        // The trunk bound is only applied to the torso run. Extending the skip to legs would
        // discard thigh readings for no reason.
        val widths = DoubleArray(rows) { 0.20 }
        val legs = DoubleArray(rows)
        legs[130] = 0.12
        val clipped = BooleanArray(rows)
        clipped[130] = true

        val profile = WidthProfile(widths, legs, 10, 190, clipped)

        assertEquals(
            130,
            AnatomicalLevelFinder.widestBetween(profile, hipRow, kneeRow, useLegWidth = true),
        )
    }

    @Test
    fun `the user is told, because this one is fixed by standing differently`() {
        val advice = ArmClearance.verdict(clippedTrunk(), anchors)

        assertNotNull(advice)
        assertTrue(advice.contains("arms"), advice)
        assertTrue(advice.contains("Both"), advice)
    }

    @Test
    fun `a clean scan is not nagged`() {
        val profile = WidthProfile(DoubleArray(rows) { 0.25 }, DoubleArray(rows), 10, 190)

        assertNull(ArmClearance.verdict(profile, anchors))
    }

    @Test
    fun `a few clipped rows are tolerated silently`() {
        // A hand touching a hip is not worth a warning, and warning about it is how a user
        // learns to dismiss the warning that matters.
        val clipped = BooleanArray(rows)
        for (row in 105..hipRow) clipped[row] = true

        val profile = WidthProfile(DoubleArray(rows) { 0.25 }, DoubleArray(rows), 10, 190, clipped)

        assertNull(ArmClearance.verdict(profile, anchors))
    }
}

class ClipRunTest {

    private val bounds = TrunkBounds(
        shoulderLeftX = 0.35,
        shoulderRightX = 0.65,
        shoulderRow = 40,
        hipLeftX = 0.42,
        hipRightX = 0.58,
        hipRow = 100,
    )

    @Test
    fun `a run inside the trunk is not reported as cut`() {
        val span = bounds.clipRun(startX = 0.45, endX = 0.55, row = 70)

        assertNotNull(span)
        assertTrue(!span.cut)
        assertEquals(0.10, span.width, 1e-9)
    }

    @Test
    fun `a run with an arm on it is reported as cut`() {
        val span = bounds.clipRun(startX = 0.20, endX = 0.80, row = 70)

        assertNotNull(span)
        assertTrue(span.cut, "the whole point of the flag")
    }

    @Test
    fun `one arm is enough`() {
        // The usual case, since one hand is holding the phone. A single contaminated edge
        // corrupts the width just as thoroughly as two.
        val span = bounds.clipRun(startX = 0.20, endX = 0.55, row = 70)

        assertNotNull(span)
        assertTrue(span.cut)
    }

    @Test
    fun `pixel quantisation alone does not count as a cut`() {
        // Mask runs are whole pixels and the bound is continuous, so the two never coincide
        // exactly. Without a tolerance every row would be flagged and no scan would measure.
        val centre = bounds.centreAt(70)
        val half = bounds.maxHalfWidthAt(70)
        val sliver = TrunkBounds.CLIP_TOLERANCE / 2.0

        val span = bounds.clipRun(centre - half - sliver, centre + half + sliver, 70)

        assertNotNull(span)
        assertTrue(!span.cut)
    }

    @Test
    fun `a run entirely outside the trunk is still nothing`() {
        assertNull(bounds.clipRun(startX = 0.90, endX = 0.98, row = 70))
    }

    @Test
    fun `the old range API still agrees with the new one`() {
        val range = bounds.clip(0.20, 0.80, 70)
        val span = bounds.clipRun(0.20, 0.80, 70)

        assertNotNull(range)
        assertNotNull(span)
        assertEquals(range.start, span.start, 1e-12)
        assertEquals(range.endInclusive, span.endInclusive, 1e-12)
    }
}
