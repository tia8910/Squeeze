package com.squeeze.core.scan

import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a plateau reading is, and what it is therefore not allowed to do.
 *
 * Below [SilhouetteBodyFat.LEAN_PLATEAU_RATIO] the outline stops carrying information about
 * adiposity — measured, not assumed: waist-to-shoulder reads 0.586 at eight per cent, 0.592
 * at twelve and 0.580 at fifteen. The method reports its lean-end value there and widens its
 * interval to ±9 points to say so.
 *
 * The failure these guard against is what happens next. A figure that uncertain was being
 * treated as evidence strong enough to discard a whole competing method, and it produced a
 * scan reporting 8.00 per cent for a body with no abdominal definition at all.
 */
class PlateauReadingTest {

    @Test
    fun `the plateau ceiling is where the outline stops discriminating`() {
        // Derived from the anchors rather than hard-coded, so recalibrating them moves this
        // automatically instead of silently desynchronising it.
        val male = SilhouetteBodyFat.plateauCeilingPercent(Sex.MALE)

        assertEquals(11.6, male, 0.05)
    }

    @Test
    fun `women plateau higher, because their lean anchor is higher`() {
        val female = SilhouetteBodyFat.plateauCeilingPercent(Sex.FEMALE)

        assertEquals(21.2, female, 0.05)
    }

    @Test
    fun `a ratio on the plateau produces a figure at or under the ceiling`() {
        // The property the veto rule depends on: a stored percentage at or below the ceiling
        // can only have come from the plateau, so a caller holding nothing but the number can
        // still recognise one.
        val ceiling = SilhouetteBodyFat.plateauCeilingPercent(Sex.MALE)

        listOf(0.58, 0.62, 0.70, 0.7599).forEach { ratio ->
            val estimate = SilhouetteBodyFat.estimate(ShapeIndices(ratio, null), Sex.MALE)

            assertNotNull(estimate, "ratio $ratio")
            assertTrue(
                estimate.percent <= ceiling + 1e-9,
                "ratio $ratio gave ${estimate.percent}, above the ceiling $ceiling",
            )
        }
    }

    @Test
    fun `a bounded reading carries the floor it was clamped to`() {
        // So the card can draw an interval that starts where the method's knowledge starts.
        // It used to draw "most likely 3-21%" beneath a sentence promising the reader was no
        // leaner than 11.6 — the lower half of that range being a region the method had
        // already ruled out, under a figure that was the boundary of the exclusion.
        Sex.entries.forEach { sex ->
            val estimate = SilhouetteBodyFat.estimate(ShapeIndices(0.65, null), sex)

            assertNotNull(estimate, "$sex")
            val floor = estimate.floorPercent
            assertNotNull(floor, "$sex lost its floor")
            assertEquals(SilhouetteBodyFat.leanestClaimable(sex), floor, 1e-9, "$sex")
            assertTrue(
                estimate.percent - estimate.standardErrorPercent < floor,
                "$sex: the floor must be doing work, not sitting below the interval",
            )
        }
    }

    @Test
    fun `a resolved reading has no floor, because nothing was clamped`() {
        val estimate = SilhouetteBodyFat.estimate(ShapeIndices(0.90, null), Sex.MALE)

        assertNotNull(estimate)
        assertNull(estimate.floorPercent)
    }

    @Test
    fun `a softening body never reads leaner, across the whole range of ratios`() {
        // **The assertion that reverted a change to this file.** Reporting the floor is a
        // systematic understatement — a man whose midsection a coach reads near sixteen is
        // shown 11.6 — so the figure was moved to the middle of the range the method admits.
        // The floor truncates hard, so lifting only the truncated readings made the function
        // fall exactly where it has to rise: on the hip path, 0.853 read 16.1% and 0.860 read
        // 12.2%, a body 3.9 points leaner for having got softer.
        //
        // A bias is systematic and cancels when someone compares themselves against
        // themselves. This does not. It is precision rather than accuracy, and precision is
        // what this app is for, so the understatement stays until something can actually read
        // the surface.
        //
        // Asserted in fine steps and on both denominators, because the failure lived in a
        // hundredth of a ratio at the edge of the plateau and the coarse sweeps elsewhere in
        // this file stepped straight over it.
        Sex.entries.forEach { sex ->
            var previous = 0.0
            var ratio = 0.56
            while (ratio <= 1.39) {
                val shoulderOnly = SilhouetteBodyFat.estimate(ShapeIndices(ratio, null), sex)
                assertNotNull(shoulderOnly, "$sex shoulder $ratio")
                assertTrue(
                    shoulderOnly.percent >= previous - 1e-9,
                    "$sex: shoulder ratio $ratio read ${shoulderOnly.percent} after $previous",
                )
                previous = shoulderOnly.percent
                ratio += 0.005
            }

            previous = 0.0
            var hip = 0.56
            while (hip <= 1.44) {
                val withHip = SilhouetteBodyFat.estimate(ShapeIndices(0.80, hip), sex)
                assertNotNull(withHip, "$sex hip $hip")
                assertTrue(
                    withHip.percent >= previous - 1e-9,
                    "$sex: hip ratio $hip read ${withHip.percent} after $previous",
                )
                previous = withHip.percent
                hip += 0.005
            }
        }
    }

    @Test
    fun `a ratio off the plateau produces a figure above the ceiling`() {
        val ceiling = SilhouetteBodyFat.plateauCeilingPercent(Sex.MALE)

        val estimate = SilhouetteBodyFat.estimate(ShapeIndices(0.90, null), Sex.MALE)

        assertNotNull(estimate)
        assertTrue(estimate.percent > ceiling, "${estimate.percent} should clear $ceiling")
    }

    @Test
    fun `every plateau reading carries the widened interval`() {
        // The interval is the method's own statement that it cannot resolve the lean range.
        // If this ever narrowed, the veto rule would start firing on plateau readings again.
        val estimate = SilhouetteBodyFat.estimate(ShapeIndices(0.62, null), Sex.MALE)

        assertNotNull(estimate)
        assertEquals(
            SilhouetteBodyFat.PLATEAU_ERROR_PERCENT,
            estimate.standardErrorPercent,
            1e-9,
        )
    }

    @Test
    fun `the real reference ratios all land on the plateau`() {
        // Read off a labelled reference chart. This is the uncomfortable result and it is
        // worth pinning: across eight to thirty-five per cent, every measured waist-to-
        // shoulder ratio sits below the plateau threshold. The outline, on this evidence,
        // cannot deliver an absolute percentage for anybody — which is exactly why a plateau
        // reading must never be allowed to overrule a measured girth.
        val measured = listOf(0.586, 0.592, 0.580, 0.632, 0.681, 0.677, 0.679)

        assertTrue(
            measured.all { it < SilhouetteBodyFat.LEAN_PLATEAU_RATIO },
            "a reference ratio cleared the plateau: $measured",
        )
    }
}
