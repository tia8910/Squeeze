package com.squeeze.core.scan

import com.squeeze.core.bodycomp.PlateauPrior
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Why a checked hip is still not allowed below the floor.
 *
 * This file argued the opposite for one release, and the argument was not silly. The plateau
 * was measured on waist-to-*shoulder* — 0.586 at eight per cent, 0.592 at twelve, 0.580 at
 * fifteen, flat — and that flatness has an anatomical cause that belongs to the shoulder: the
 * arms attach there. Nothing equivalent was ever measured on waist-to-hip. So a hip reading
 * that passed two checks — mask width at the hip verified against the distance between the hip
 * joints, and neither trunk band cut back by the bound — was allowed to stand on its own.
 *
 * Two photographs disproved it within a day:
 *
 * | photograph | waist-to-hip | reported |
 * |---|---|---|
 * | soft midsection, loose cargo trousers worn high | 0.788 | **4.76%** |
 * | bodybuilder, wide-stance front double biceps | 0.69 | **3.00%** |
 *
 * Both passed both checks. The trousers were the hip in the first; two spread thighs merged
 * into one run were the hip in the second.
 *
 * **What the argument missed.** The floor was never a claim about which ratio is flat. It is a
 * claim about which direction this method fails in: every way a width is mismeasured makes it
 * wider, a wider denominator makes the ratio smaller, and a smaller ratio reads lean. That
 * list — trousers, thighs, a towel, laundry, a shadow — is not closable by inspection, so an
 * exemption granted for the failures someone thought of is an exemption for every failure they
 * did not.
 *
 * The plateau is why the shoulder path cannot resolve leanness. The floor is why no path may
 * claim it. Two different statements, and only the first is about the shoulder.
 */
class CorroboratedHipTest {

    private val man = Profile(heightCm = 175.0, birthYear = 1993, sex = Sex.MALE)

    private fun hipReading(ratio: Double) = SilhouetteBodyFat.estimate(
        // Corroborated, which is the point: both photographs below passed every check the
        // exemption asked for, and the exemption still has to not exist.
        ShapeIndices(waistToShoulder = 0.80, waistToHip = ratio, hipCorroborated = true),
        Sex.MALE,
    )

    @Test
    fun `the trousers scan that reported four point seven six is floored`() {
        val estimate = hipReading(0.788)

        assertNotNull(estimate)
        assertEquals(SilhouetteBodyFat.leanestClaimable(Sex.MALE), estimate.percent, 1e-9)
        assertEquals(SilhouetteBodyFat.PLATEAU_ERROR_PERCENT, estimate.standardErrorPercent, 1e-9)
    }

    @Test
    fun `the wide-stance scan that reported three per cent is floored`() {
        // Three per cent is MIN_PERCENT — the reading had run off the bottom of the scale
        // entirely, which is what a denominator spanning two thighs does to a ratio.
        val estimate = hipReading(0.69)

        assertNotNull(estimate)
        assertEquals(SilhouetteBodyFat.leanestClaimable(Sex.MALE), estimate.percent, 1e-9)
    }

    @Test
    fun `and neither reaches the user as a single-digit figure`() {
        // End to end, at the weight and height both records were taken at. PlateauPrior
        // recognises the floored reading by its interval and substitutes what the build
        // implies — which is not the right answer for either body, but is not a claim that
        // a man with a soft midsection is below essential fat.
        listOf(0.788, 0.69).forEach { ratio ->
            val measured = hipReading(ratio)
            assertNotNull(measured, "ratio $ratio")

            val resolved = PlateauPrior.resolve(measured, man, weightKg = 70.0)

            assertNotNull(resolved, "ratio $ratio")
            assertTrue(resolved.percent > 10.0, "ratio $ratio gave ${resolved.percent}")
        }
    }

    @Test
    fun `corroboration cannot move any reading, at any ratio, for either sex`() {
        // The exemption is gone rather than narrowed, and this is the assertion that keeps it
        // gone: the flag is recorded, and nothing downstream of it may read it as permission.
        val ratios = listOf(0.55, 0.69, 0.72, 0.788, 0.80, 0.87, 0.95, 1.06, 1.20, 1.45)

        for (ratio in ratios) {
            Sex.entries.forEach { sex ->
                val checked = SilhouetteBodyFat.estimate(
                    ShapeIndices(0.80, ratio, hipCorroborated = true),
                    sex,
                )
                val unchecked = SilhouetteBodyFat.estimate(
                    ShapeIndices(0.80, ratio, hipCorroborated = false),
                    sex,
                )

                assertEquals(unchecked?.percent, checked?.percent, "$sex at $ratio")
                assertEquals(
                    unchecked?.standardErrorPercent,
                    checked?.standardErrorPercent,
                    "$sex at $ratio",
                )
                checked?.let {
                    assertTrue(
                        it.percent >= SilhouetteBodyFat.leanestClaimable(sex),
                        "$sex at $ratio gave ${it.percent}",
                    )
                }
            }
        }
    }

    @Test
    fun `a hip reading above the floor is still the reading, untouched`() {
        // The floor is a floor and not a flattening. The reconstruction of a real body with a
        // soft midsection reads 0.87 and lands well above it, exactly as it always has.
        val estimate = hipReading(0.87)

        assertNotNull(estimate)
        assertTrue(estimate.percent > 13.0, "got ${estimate.percent}")
        assertEquals(
            com.squeeze.core.model.EstimationMethod.PHOTO_SHAPE.standardErrorPercent,
            estimate.standardErrorPercent,
            1e-9,
        )
    }
}
