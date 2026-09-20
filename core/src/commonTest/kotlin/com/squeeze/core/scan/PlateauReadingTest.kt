package com.squeeze.core.scan

import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a plateau reading is, and what it is therefore not allowed to do.
 *
 * Below [SilhouetteBodyFat.LEAN_PLATEAU_RATIO] the outline stops carrying information about
 * adiposity — measured, not assumed: waist-to-shoulder reads 0.586 at eight per cent, 0.592
 * at twelve and 0.580 at fifteen. The method widens its interval to ±9 points to say so, and
 * reports the middle of what that leaves rather than its leanest edge — see
 * [SilhouetteBodyFat.plateauMidpointPercent], which exists because it used to report the edge.
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
    fun `a ratio on the plateau reports the middle of the range, not its lean end`() {
        // This used to assert the opposite — that a plateau figure sits at or below the
        // ceiling — on the reasoning that a caller holding nothing but the number could then
        // recognise a bound. The number it recognised was the leanest value the method
        // admitted, published as the answer on every unresolved scan ever taken. A user
        // photographed at a midsection a coach reads around sixteen per cent was shown 11.6,
        // over a sentence saying he was no leaner than that: the card disagreeing with itself.
        //
        // Recognition was never really doing that work anyway — it reads the stored interval,
        // and has since intervals were stored. The value is free to be the honest one.
        val floor = SilhouetteBodyFat.leanestClaimable(Sex.MALE)
        val middle = SilhouetteBodyFat.plateauMidpointPercent(Sex.MALE)

        listOf(0.58, 0.62, 0.70, 0.7599).forEach { ratio ->
            val estimate = SilhouetteBodyFat.estimate(ShapeIndices(ratio, null), Sex.MALE)

            assertNotNull(estimate, "ratio $ratio")
            assertEquals(middle, estimate.percent, 1e-9, "ratio $ratio")
            // The floor is still asserted, and now travels with the reading so the interval
            // can start at it instead of nine points below it.
            assertEquals(floor, estimate.floorPercent, "ratio $ratio")
            assertTrue(estimate.percent > floor, "ratio $ratio gave ${estimate.percent}")
        }
    }

    @Test
    fun `the interval a bounded reading implies never reaches below its floor`() {
        // The contradiction that prompted the change, asserted as arithmetic rather than as
        // copy: percent minus its own error used to land at 2.6 for a man, drawn as "most
        // likely 3-21%" under a sentence promising he was no leaner than 11.6.
        Sex.entries.forEach { sex ->
            val estimate = SilhouetteBodyFat.estimate(ShapeIndices(0.65, null), sex)

            assertNotNull(estimate, "$sex")
            val floor = estimate.floorPercent
            assertNotNull(floor, "$sex lost its floor")
            assertTrue(
                estimate.percent - estimate.standardErrorPercent <= floor,
                "$sex: the floor must be doing work, not sitting above the interval",
            )
            assertEquals(
                SilhouetteBodyFat.PLATEAU_ERROR_PERCENT,
                estimate.standardErrorPercent,
                1e-9,
                "$sex: centring the figure must not narrow what the method admits",
            )
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
