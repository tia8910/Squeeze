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
