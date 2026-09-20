package com.squeeze.core.scan

import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A lean outline and a defined abdomen are a measurement; a lean outline and a smooth one are
 * a warning.
 *
 * The outline cannot separate eight per cent from fifteen — 0.586, 0.592 and 0.580 off the
 * reference charts — so it reports a bound and the app says "not resolved by the photo". It
 * was saying that to photographs with visible abdominal separation in them.
 *
 * The surface resolves what the border cannot, and the two fail independently: every way an
 * outline is corrupted makes a denominator wider and the reading leaner, and none of those
 * puts grooves on a stomach.
 */
class PlateauCorroborationTest {

    /** A reading from the plateau: the outline said lean and could not say how lean. */
    private fun bounded() = SilhouetteBodyFat.estimate(
        ShapeIndices(waistToShoulder = 0.67, waistToHip = null),
        Sex.MALE,
    )

    /** A reading the outline resolved on its own, well above the plateau. */
    private fun resolved() = SilhouetteBodyFat.estimate(
        ShapeIndices(waistToShoulder = 0.95, waistToHip = 0.95),
        Sex.MALE,
    )

    private fun definition(score: Double, usable: Boolean = true) =
        DefinitionReading(score, usable)

    @Test
    fun `the real photograph that kept reading unresolved now resolves`() {
        // Waist 0.67 of the shoulders with visible abdominal separation — the scan that
        // prompted all of this. The outline puts it on the plateau; the abdomen scored 32.5.
        val before = bounded()
        assertNotNull(before)
        assertTrue(before.standardErrorPercent >= 9.0)

        val after = PlateauCorroboration.apply(before, definition(32.5))

        assertNotNull(after)
        assertEquals(
            EstimationMethod.PHOTO_SHAPE.standardErrorPercent,
            after.standardErrorPercent,
            1e-9,
        )
    }

    @Test
    fun `the percentage is never moved, only what is claimed about it`() {
        // The restriction that makes a threshold from three photographs safe to ship. Getting
        // the boundary wrong costs a sentence, not a figure.
        val before = bounded()
        assertNotNull(before)

        listOf(5.0, 16.5, 19.0, 21.9, 32.5, 80.0).forEach { score ->
            val after = PlateauCorroboration.apply(before, definition(score))
            assertNotNull(after, "score $score")
            assertEquals(before.percent, after.percent, 1e-12, "score $score moved the figure")
        }
    }

    @Test
    fun `a lean outline over a smooth abdomen stays a bound`() {
        // The signature of every wrong answer this project has shipped: 4.93, 6.22, 6.83,
        // 4.76, 3.00 — all on bodies with no abdominal definition at all. A smooth abdomen
        // is the surface refusing to corroborate, and the bound has to survive that.
        val before = bounded()
        assertNotNull(before)

        val after = PlateauCorroboration.apply(before, definition(16.5))

        assertNotNull(after)
        assertEquals(SilhouetteBodyFat.PLATEAU_ERROR_PERCENT, after.standardErrorPercent, 1e-9)
    }

    @Test
    fun `between the two the photograph says nothing`() {
        val before = bounded()
        assertNotNull(before)

        val after = PlateauCorroboration.apply(before, definition(19.0))

        assertNotNull(after)
        assertEquals(SilhouetteBodyFat.PLATEAU_ERROR_PERCENT, after.standardErrorPercent, 1e-9)
    }

    @Test
    fun `an unreadable crop cannot corroborate anything`() {
        // A crop too dark or too small says nothing, which is not the same as a smooth
        // abdomen and must not be treated as one in either direction.
        val before = bounded()
        assertNotNull(before)

        listOf(definition(32.5, usable = false), null).forEach { reading ->
            val after = PlateauCorroboration.apply(before, reading)
            assertNotNull(after)
            assertEquals(
                SilhouetteBodyFat.PLATEAU_ERROR_PERCENT,
                after.standardErrorPercent,
                1e-9,
            )
        }
    }

    @Test
    fun `a reading the outline resolved is left alone`() {
        // Off the plateau the outline measured the body on its own. A second opinion about
        // the surface is not needed to believe it, and narrowing an already-narrow interval
        // on the strength of a three-photograph threshold would be inventing precision.
        val before = resolved()
        assertNotNull(before)

        listOf(32.5, 16.5, 19.0).forEach { score ->
            val after = PlateauCorroboration.apply(before, definition(score))
            assertNotNull(after, "score $score")
            assertEquals(before.percent, after.percent, 1e-12)
            assertEquals(before.standardErrorPercent, after.standardErrorPercent, 1e-12)
        }
    }

    @Test
    fun `a null estimate stays null`() {
        assertNull(PlateauCorroboration.apply(null, definition(32.5)))
    }

    @Test
    fun `the verdicts sit either side of the measured readings`() {
        // The three photographs the thresholds came from, asserted as what they are: two
        // defined abdomens at 32.5 and 21.9, one smooth at 16.5.
        val defined = PlateauCorroboration.Verdict.DEFINED
        assertEquals(defined, PlateauCorroboration.verdict(definition(32.5)))
        assertEquals(defined, PlateauCorroboration.verdict(definition(21.9)))
        assertEquals(
            PlateauCorroboration.Verdict.SMOOTH,
            PlateauCorroboration.verdict(definition(16.5)),
        )
        assertEquals(
            PlateauCorroboration.Verdict.UNCERTAIN,
            PlateauCorroboration.verdict(null),
        )
    }
}
