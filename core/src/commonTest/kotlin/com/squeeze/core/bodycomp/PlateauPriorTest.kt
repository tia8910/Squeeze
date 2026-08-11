package com.squeeze.core.bodycomp

import com.squeeze.core.model.BodyFatEstimate
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
 * What the app says about a body its outline could not resolve.
 *
 * The scan that motivated this reported 4.93%, then — once every path was floored — 11.6%, for
 * a 1.75 m, 68 kg man with no abdominal definition, soft through the midsection, real mass on
 * the arms and shoulders. A coach looking at that body says eighteen to twenty-one. Eleven-six
 * was no longer harmful and still six points wrong, because it was not a figure about him at
 * all: it is the same number the method returns for every body that lands on its plateau.
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
    fun `the scan that reported eleven-six now reports what the body implies`() {
        val resolved = PlateauPrior.resolve(scan(), man, weightKg = 68.0, age = 33)

        assertNotNull(resolved)
        // 1.20 x 22.20 + 0.23 x 33 - 10.8 - 5.4 = 18.0, less the 1.5 the equation is known
        // to overread by on trained subjects.
        assertEquals(16.5, resolved.percent, 0.2, "got ${resolved.percent}")
    }

    @Test
    fun `the trained-population correction is applied once, and downward`() {
        // Pinned as its own test so the offset cannot be quietly retuned to make some future
        // screenshot land: changing it has to change a stated expectation here.
        val raw = 1.20 * (68.0 / (1.75 * 1.75)) + 0.23 * 33 - 10.8 - 5.4
        val implied = PlateauPrior.buildPercent(man, weightKg = 68.0, age = 33)

        assertNotNull(implied)
        assertEquals(raw - PlateauPrior.TRAINED_POPULATION_OFFSET, implied, 1e-9)
        assertTrue(implied < raw, "the correction has to run downward")
    }

    @Test
    fun `the correction can never push a reading below the floor`() {
        // It is subtracted before the plausibility bound and before the floor, so both still
        // have the last word. A correction that could produce a single-digit figure would
        // undo the one thing this whole area of the codebase exists to guarantee.
        val resolved = PlateauPrior.resolve(scan(), man, weightKg = 58.0, age = 20)

        assertNotNull(resolved)
        assertTrue(
            resolved.percent >= SilhouetteBodyFat.leanestClaimable(Sex.MALE),
            "got ${resolved.percent}",
        )
    }

    @Test
    fun `it is not a constant — a heavier man at the same height reads higher`() {
        // The complaint the plateau ceiling could never answer: two different bodies, one
        // number. Every input below is measured to a precision no silhouette approaches.
        val light = PlateauPrior.resolve(scan(), man, weightKg = 62.0, age = 33)
        val heavy = PlateauPrior.resolve(scan(), man, weightKg = 88.0, age = 33)

        assertNotNull(light)
        assertNotNull(heavy)
        assertTrue(heavy.percent > light.percent + 5.0, "${light.percent} vs ${heavy.percent}")
    }

    @Test
    fun `age moves it, because the same weight carries less muscle later`() {
        val young = PlateauPrior.resolve(scan(), man, weightKg = 68.0, age = 20)
        val older = PlateauPrior.resolve(scan(), man, weightKg = 68.0, age = 50)

        assertNotNull(young)
        assertNotNull(older)
        assertTrue(older.percent > young.percent, "${young.percent} vs ${older.percent}")
    }

    @Test
    fun `it never moves a reading downward`() {
        // The floor property PlateauFloorTest guarantees has to survive this file. A body
        // whose build implies less than the outline's own bound keeps the bound: the two
        // agree the subject is lean, and the outline's statement is the more restrictive.
        val slight = PlateauPrior.resolve(scan(), man, weightKg = 52.0, age = 20)

        assertNotNull(slight)
        assertTrue(
            slight.percent >= SilhouetteBodyFat.leanestClaimable(Sex.MALE),
            "got ${slight.percent}",
        )
    }

    @Test
    fun `with no weight recorded it says exactly what it said before`() {
        val resolved = PlateauPrior.resolve(scan(), man, weightKg = null, age = 33)

        assertNotNull(resolved)
        assertEquals(SilhouetteBodyFat.leanestClaimable(Sex.MALE), resolved.percent, 1e-9)
    }

    @Test
    fun `a reading the outline resolved is left alone`() {
        // The guard that keeps this from being a BMI correction applied to everything. A
        // trained man reads lean from a clean hip ratio and BMI, blind to muscle, would drag
        // him upward — the exact bias MethodFusion refuses to average into a measured method.
        val measured = SilhouetteBodyFat.estimate(
            ShapeIndices(waistToShoulder = 0.90, waistToHip = 0.87),
            Sex.MALE,
        )
        assertNotNull(measured)

        val resolved = PlateauPrior.resolve(measured, man, weightKg = 95.0, age = 33)

        assertNotNull(resolved)
        assertEquals(measured.percent, resolved.percent, 1e-9)
        assertEquals(measured.standardErrorPercent, resolved.standardErrorPercent, 1e-9)
    }

    @Test
    fun `the substituted figure keeps the plateau's interval`() {
        val resolved = PlateauPrior.resolve(scan(), man, weightKg = 68.0, age = 33)

        assertNotNull(resolved)
        assertEquals(SilhouetteBodyFat.PLATEAU_ERROR_PERCENT, resolved.standardErrorPercent, 1e-9)
        // And it is still the outline's method, because the outline is what produced the
        // bound this replaced. Relabelling it DEURENBERG_BMI would drop it from every fusion
        // containing a measured method, which is the opposite of what a bound should do.
        assertEquals(EstimationMethod.PHOTO_SHAPE, resolved.method)
    }

    @Test
    fun `women resolve on the female form of the equation`() {
        val resolved = PlateauPrior.resolve(scan(Sex.FEMALE), woman, weightKg = 62.0, age = 33)
        val asMan = PlateauPrior.resolve(scan(), man.copy(heightCm = 165.0), 62.0, 33)

        assertNotNull(resolved)
        assertNotNull(asMan)
        // The sex term is 10.8 points. Same height, same weight, same age.
        assertTrue(resolved.percent > asMan.percent + 9.0, "${asMan.percent} vs ${resolved.percent}")
    }

    @Test
    fun `an impossible implied lean mass is bounded, not printed`() {
        // BMI is blind to stature in a way that bites at the extremes: 65 kg at 1.90 m gives
        // a Deurenberg figure near 13%, 11.5 after the trained correction, which would still
        // leave under 57.6 kg of fat-free mass — a fat-free mass index of 15.9, below
        // anything measured in an ambulatory adult. The gate that caught 36.6% applies to
        // this route too, and clamps it to 11.14.
        val tall = Profile(heightCm = 190.0, birthYear = 1993, sex = Sex.MALE)
        val implied = PlateauPrior.buildPercent(tall, weightKg = 65.0, age = 33)
        val range = LeanMassPlausibility.plausibleRange(tall, 65.0)

        assertNotNull(implied)
        assertNotNull(range)
        assertTrue(implied in range, "got $implied for $range")
        assertEquals(range.endInclusive, implied, 1e-9)
    }

    @Test
    fun `a stored bound is recognised by its interval`() {
        assertTrue(
            PlateauPrior.isBounded(18.0, SilhouetteBodyFat.PLATEAU_ERROR_PERCENT, man, 68.0, 33),
        )
        assertFalse(
            PlateauPrior.isBounded(
                18.0,
                EstimationMethod.PHOTO_SHAPE.standardErrorPercent,
                man,
                68.0,
                33,
            ),
        )
    }

    @Test
    fun `a row recorded before intervals were stored still resolves correctly`() {
        // Legacy rows have no interval. They fall back to the value comparison, which is
        // sound because resolve maps every unresolved reading onto exactly the bound.
        val bound = PlateauPrior.ceiling(man, 68.0, 33)

        assertTrue(PlateauPrior.isBounded(bound, null, man, 68.0, 33))
        assertFalse(PlateauPrior.isBounded(bound + 4.0, null, man, 68.0, 33))
    }

    @Test
    fun `no resolved reading is ever a single-digit figure`() {
        // PlateauFloorTest's property, re-asserted through this layer, over the weights and
        // ages a real user can have.
        val weights = listOf(45.0, 55.0, 68.0, 80.0, 95.0, 120.0)
        val ages = listOf(18, 30, 45, 70)
        val ratios = listOf(0.40, 0.55, 0.65, 0.70, 0.75, 0.80, 0.90, 1.00, 1.20)

        for (weight in weights) {
            for (age in ages) {
                for (ratio in ratios) {
                    Sex.entries.forEach { sex ->
                        val profile = if (sex == Sex.MALE) man else woman
                        val raw = SilhouetteBodyFat.estimate(ShapeIndices(ratio, null), sex)
                            ?: return@forEach
                        val resolved = PlateauPrior.resolve(raw, profile, weight, age)

                        assertNotNull(resolved)
                        assertTrue(
                            resolved.percent >= SilhouetteBodyFat.leanestClaimable(sex),
                            "$sex $weight kg age $age ratio $ratio gave ${resolved.percent}",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a null estimate stays null`() {
        assertEquals(
            null,
            PlateauPrior.resolve(null as BodyFatEstimate?, man, 68.0, 33),
        )
    }
}
