package com.squeeze.core.bodycomp

import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import com.squeeze.core.scan.ShapeIndices
import com.squeeze.core.scan.SilhouetteBodyFat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Why a photo scan no longer answers from height and weight, and what is left here.
 *
 * This file used to substitute a Deurenberg figure for any reading the outline had bounded.
 * The case for it was real: on the plateau the outline carries no information, so the 11.6%
 * it reports is a property of the method rather than of the person, and printing a constant
 * as a measurement is the failure this project keeps rediscovering. Height, weight, age and
 * sex are measured to a precision no silhouette approaches, and through Deurenberg they gave
 * 16.5% for the 68 kg body that had been reading 11.6 — which matched a coach's assessment,
 * which is why it looked like progress.
 *
 * **The substitute was a constant too.** Deurenberg's inputs do not include the photograph, so
 * for one person at one weight it returns one number. Three photographs of the same man at
 * 70 kg and 1.75 m all came back at 17.3%:
 *
 * | photograph | true level | reported |
 * |---|---|---|
 * | soft midsection, loose cargo trousers | ~19% | 17.3% |
 * | mirror selfie, some structure | ~15% | 17.3% |
 * | visible abdominal separation, vascular forearms | ~12% | 17.3% |
 *
 * Not approximately — identically, because every input was identical. And the value was
 * written to the row under [EstimationMethod.PHOTO_SHAPE], so [MethodFusion]'s rule that the
 * BMI fallback never outvotes a measurement could not see it: a height-and-weight number was
 * laundered through a photo method's name and shown as "Best estimate".
 *
 * What remains is [PlateauPrior.isBounded], which lets the rest of the app recognise a bound
 * and refuse to let one act like a measurement. The tests below pin that, and pin that nothing
 * here puts a figure on a body the photograph failed to read.
 */
class PlateauPriorTest {

    private val man = Profile(heightCm = 175.0, birthYear = 1993, sex = Sex.MALE)
    private val woman = Profile(heightCm = 165.0, birthYear = 1993, sex = Sex.FEMALE)

    /** The contaminated shoulder ratio from the real scan: both arms inside the band. */
    private fun scan(sex: Sex = Sex.MALE) = SilhouetteBodyFat.estimate(
        ShapeIndices(waistToShoulder = 0.686, waistToHip = null),
        sex,
    )

    @Test
    fun `a bounded reading is recognisable as one, by its interval`() {
        // The whole remaining job. A bound may not veto another method's measurement and must
        // re-enter a fusion at the width it was recorded with, and both of those need it to
        // be identifiable from what the row stores.
        val bounded = scan()
        assertNotNull(bounded)

        assertTrue(
            PlateauPrior.isBounded(bounded.percent, bounded.standardErrorPercent, man, 68.0),
        )
        assertEquals(SilhouetteBodyFat.PLATEAU_ERROR_PERCENT, bounded.standardErrorPercent, 1e-9)
    }

    @Test
    fun `a reading the outline resolved is not mistaken for a bound`() {
        // The guard that keeps the bound machinery away from working measurements. A clean hip
        // ratio well above the floor is a measurement and carries the method's own interval.
        val measured = SilhouetteBodyFat.estimate(
            ShapeIndices(waistToShoulder = 0.95, waistToHip = 0.95),
            Sex.MALE,
        )
        assertNotNull(measured)

        assertFalse(
            PlateauPrior.isBounded(measured.percent, measured.standardErrorPercent, man, 95.0),
        )
        assertEquals(
            EstimationMethod.PHOTO_SHAPE.standardErrorPercent,
            measured.standardErrorPercent,
            1e-9,
        )
    }

    @Test
    fun `the interval decides, not the value`() {
        // Same percentage, different widths, opposite answers. The value merely coincides with
        // the bound; the width is the estimator saying it bounded something.
        assertTrue(
            PlateauPrior.isBounded(18.0, SilhouetteBodyFat.PLATEAU_ERROR_PERCENT, man, 68.0),
        )
        assertFalse(
            PlateauPrior.isBounded(
                18.0,
                EstimationMethod.PHOTO_SHAPE.standardErrorPercent,
                man,
                68.0,
            ),
        )
    }

    @Test
    fun `a row recorded before intervals were stored still resolves correctly`() {
        // Legacy rows have no interval, so they fall back to comparing against the bound the
        // old code would have written. That comparison is sound for exactly those rows and no
        // others, which is the only reason buildPercent and ceiling still exist.
        val bound = PlateauPrior.ceiling(man, 68.0)

        assertTrue(PlateauPrior.isBounded(bound, null, man, 68.0))
        assertFalse(PlateauPrior.isBounded(bound + 4.0, null, man, 68.0))
    }

    @Test
    fun `the build figure is no longer anything a scan can report`() {
        // The regression that matters. Deurenberg at 70 kg and 1.75 m is 17.3, and the three
        // photographs in this file's header all landed on it. Whatever the outline gives back
        // now, it is the outline's own figure — so it cannot equal a number derived from
        // inputs the photograph never touched.
        val implied = PlateauPrior.buildPercent(man, weightKg = 70.0)
        assertNotNull(implied)
        assertEquals(17.3, implied, 0.1, "the substitute was $implied")

        val fromOutline = scan()
        assertNotNull(fromOutline)
        assertEquals(SilhouetteBodyFat.leanestClaimable(Sex.MALE), fromOutline.percent, 1e-9)
        assertTrue(
            fromOutline.percent < implied - 4.0,
            "the outline's bound must not have become the build figure: ${fromOutline.percent}",
        )
    }

    @Test
    fun `the outline answers the photograph and the build route cannot`() {
        // The reason the route was removed, as a property rather than as anecdote. Three
        // different photographs of one body at one weight: the outline gives three different
        // readings because the picture is its only input, and the build figure gives one
        // because the picture is not an input to it at all.
        val outline = listOf(0.686, 0.80, 0.95)
            .mapNotNull { SilhouetteBodyFat.estimate(ShapeIndices(it, null), Sex.MALE)?.percent }
            .distinct()

        assertEquals(3, outline.size, "the outline has to respond to the picture: $outline")
        assertNotNull(PlateauPrior.buildPercent(man, 70.0))
    }

    @Test
    fun `the trained-population correction is applied once, and downward`() {
        // Pinned because buildPercent still serves isBounded on legacy rows, so its arithmetic
        // still has to be the arithmetic those rows were written with.
        val raw = 1.20 * (68.0 / (1.75 * 1.75)) +
            0.23 * PlateauPrior.REFERENCE_AGE - 10.8 - 5.4
        val implied = PlateauPrior.buildPercent(man, weightKg = 68.0)

        assertNotNull(implied)
        assertEquals(raw - PlateauPrior.TRAINED_POPULATION_OFFSET, implied, 1e-9)
        assertTrue(implied < raw, "the correction has to run downward")
    }

    @Test
    fun `age cannot move it, because a birthday is not a measurement of fat`() {
        // Kept from when this route was displayed. The repository recomputed historical rows
        // at today's age, so a two-year-old scan silently read half a point higher than the
        // day it was taken and the trend engine read that calendar creep as a real gain.
        // buildPercent takes no age to pass, and this is what keeps it that way.
        val older = PlateauPrior.buildPercent(man.copy(birthYear = 1976), 68.0)
        val younger = PlateauPrior.buildPercent(man.copy(birthYear = 2006), 68.0)

        assertNotNull(older)
        assertNotNull(younger)
        assertEquals(younger, older, 1e-9)
    }

    @Test
    fun `an impossible implied lean mass is bounded, not printed`() {
        // BMI is blind to stature at the extremes: 65 kg at 1.90 m gives a Deurenberg figure
        // near 13%, 11.5 after the correction, which would leave a fat-free mass index of
        // 15.9 — below anything measured in an ambulatory adult.
        val tall = Profile(heightCm = 190.0, birthYear = 1993, sex = Sex.MALE)
        val implied = PlateauPrior.buildPercent(tall, weightKg = 65.0)
        val range = LeanMassPlausibility.plausibleRange(tall, 65.0)

        assertNotNull(implied)
        assertNotNull(range)
        assertTrue(implied in range, "got $implied for $range")
        assertEquals(range.endInclusive, implied, 1e-9)
    }

    @Test
    fun `women resolve on the female form of the equation`() {
        val her = PlateauPrior.buildPercent(woman, 62.0)
        val him = PlateauPrior.buildPercent(man.copy(heightCm = 165.0), 62.0)

        assertNotNull(her)
        assertNotNull(him)
        // The sex term is 10.8 points. Same height, same weight, same age.
        assertTrue(her > him + 9.0, "$him vs $her")
    }

}
