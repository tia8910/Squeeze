package com.squeeze.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Recovering a waist when one arm is against the body.
 *
 * The arm guard was right that a clipped width is the bound rather than the body, and wrong
 * to conclude that nothing could be measured. Arms hang at the sides, and on a person simply
 * standing still it is far more common for one to touch than for both. When only one does,
 * the opposite edge of the silhouette is exactly where the skin is — an untouched, real
 * measurement — and doubling that half about the trunk's centre recovers the width.
 *
 * The assumption is that a torso is roughly symmetric about its midline. Against the
 * alternatives that is a bargain: the old behaviour before the guard measured an arm as part
 * of the waist, and the behaviour after it returned no figure at all for a body standing
 * normally.
 */
class MirroredWidthTest {

    private val bounds = TrunkBounds(
        shoulderLeftX = 0.35,
        shoulderRightX = 0.65,
        shoulderRow = 40,
        hipLeftX = 0.42,
        hipRightX = 0.58,
        hipRow = 100,
    )

    private val row = 70

    @Test
    fun `an untouched run is returned as measured`() {
        val span = bounds.clipRun(startX = 0.45, endX = 0.55, row = row)

        assertNotNull(span)
        assertTrue(!span.cutStart && !span.cutEnd)
        assertEquals(0.10, span.mirroredWidth()!!, 1e-9)
    }

    @Test
    fun `one arm on the left is recovered from the right edge`() {
        val centre = bounds.centreAt(row)
        val half = bounds.maxHalfWidthAt(row)
        // The right edge sits comfortably inside the bound; the left runs far past it.
        val cleanRight = centre + half * 0.6

        val span = bounds.clipRun(startX = centre - half * 2.0, endX = cleanRight, row = row)

        assertNotNull(span)
        assertTrue(span.cutStart)
        assertTrue(!span.cutEnd)
        assertEquals(2.0 * (cleanRight - centre), span.mirroredWidth()!!, 1e-9)
    }

    @Test
    fun `one arm on the right is recovered from the left edge`() {
        val centre = bounds.centreAt(row)
        val half = bounds.maxHalfWidthAt(row)
        val cleanLeft = centre - half * 0.6

        val span = bounds.clipRun(startX = cleanLeft, endX = centre + half * 2.0, row = row)

        assertNotNull(span)
        assertTrue(span.cutEnd)
        assertTrue(!span.cutStart)
        assertEquals(2.0 * (centre - cleanLeft), span.mirroredWidth()!!, 1e-9)
    }

    @Test
    fun `both arms leave nothing to mirror`() {
        val centre = bounds.centreAt(row)
        val half = bounds.maxHalfWidthAt(row)

        val span = bounds.clipRun(centre - half * 2.0, centre + half * 2.0, row)

        assertNotNull(span)
        assertTrue(span.cutStart && span.cutEnd)
        assertNull(span.mirroredWidth())
    }

    @Test
    fun `the recovered width is all body, where the clipped width is half bound`() {
        // The distinction the guard exists to make. With one side cut, the clipped width is
        // the clean half plus the *bound's* half — a real measurement contaminated by a
        // constant. The mirrored width is twice the clean half and contains no constant at
        // all, so it moves with the body and nothing else.
        val centre = bounds.centreAt(row)
        val half = bounds.maxHalfWidthAt(row)
        val cleanHalf = half * 0.6

        val span = bounds.clipRun(centre - half * 2.0, centre + cleanHalf, row)!!

        assertEquals(half + cleanHalf, span.width, 1e-9)
        assertEquals(2.0 * cleanHalf, span.mirroredWidth()!!, 1e-9)
    }

    @Test
    fun `mirroring separates two waists that clipping brings closer together`() {
        val centre = bounds.centreAt(row)
        val half = bounds.maxHalfWidthAt(row)

        val narrow = bounds.clipRun(centre - half * 2.0, centre + half * 0.4, row)!!
        val wide = bounds.clipRun(centre - half * 2.0, centre + half * 0.8, row)!!

        // Both differences come from the same 0.4-of-a-half gap between the two bodies. The
        // clipped pair carry the bound's constant on top of it, which shrinks the relative
        // difference; the mirrored pair are twice the real gap and nothing else.
        assertTrue(
            (wide.mirroredWidth()!! - narrow.mirroredWidth()!!) > (wide.width - narrow.width),
            "mirroring must not compress the difference between two bodies",
        )
    }

    @Test
    fun `a clean half of zero width is refused rather than reported`() {
        // The run touches the centre line and nothing more. Doubling zero is not a waist.
        val centre = bounds.centreAt(row)
        val half = bounds.maxHalfWidthAt(row)

        val span = bounds.clipRun(centre - half * 2.0, centre, row)

        // Either the clip rejects it outright or the mirror does; both are correct, and
        // neither may return a number.
        assertTrue(span == null || span.mirroredWidth() == null)
    }

    @Test
    fun `cut still reports either side, for the callers that only need that`() {
        val centre = bounds.centreAt(row)
        val half = bounds.maxHalfWidthAt(row)

        val oneSide = bounds.clipRun(centre - half * 2.0, centre + half * 0.5, row)!!

        assertTrue(oneSide.cut)
    }
}
