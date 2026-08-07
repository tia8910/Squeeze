package com.squeeze.core.scan

import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SilhouetteShapeTest {

    /**
     * A silhouette with a genuine taper: broad at the deltoids, narrow at the waist, wider
     * again at the hip. Rows run head to foot over 200 slots.
     */
    private fun profile(
        shoulderWidth: Double,
        waistWidth: Double,
        hipWidth: Double,
        rows: Int = 200,
    ): WidthProfile {
        val torso = DoubleArray(rows)
        for (row in 20 until 180) {
            torso[row] = when {
                row < 50 -> 0.10
                row < 70 -> shoulderWidth
                row < 100 -> shoulderWidth - (shoulderWidth - waistWidth) *
                    ((row - 70) / 30.0)

                row < 110 -> waistWidth
                row < 130 -> hipWidth
                else -> hipWidth * 0.45
            }
        }
        return WidthProfile.torsoOnly(torso, topRow = 20, bottomRow = 179)
    }

    private val anchors = PoseAnchors(
        shoulderRow = 62,
        hipRow = 118,
        kneeRow = 150,
        chinRow = 45,
    )

    @Test
    fun `a lean tapered silhouette reads lean`() {
        val indices = SilhouetteBodyFat
            .indicesFrom(profile(shoulderWidth = 0.30, waistWidth = 0.21, hipWidth = 0.25), anchors)

        assertNotNull(indices)
        val estimate = SilhouetteBodyFat.estimate(indices, Sex.MALE)

        assertNotNull(estimate)
        assertTrue(
            estimate.percent < 16.0,
            "a waist seven-tenths of the shoulders is a lean build, got ${estimate.percent}",
        )
    }

    @Test
    fun `a straight-sided silhouette reads high`() {
        val indices = SilhouetteBodyFat
            .indicesFrom(profile(shoulderWidth = 0.30, waistWidth = 0.30, hipWidth = 0.30), anchors)

        assertNotNull(indices)
        val estimate = SilhouetteBodyFat.estimate(indices, Sex.MALE)

        assertNotNull(estimate)
        assertTrue(estimate.percent > 25.0, "got ${estimate.percent}")
    }

    @Test
    fun `the estimate is unchanged when the whole mask is inflated`() {
        // The property this method exists for, and the failure it was written to survive.
        // A mask that catches a mirror frame reports every width larger; a tape-equation
        // estimate built on those widths moves a long way, and this one must not move at all.
        val honest = profile(shoulderWidth = 0.30, waistWidth = 0.21, hipWidth = 0.25)
        val inflated = profile(
            shoulderWidth = 0.30 * 1.30,
            waistWidth = 0.21 * 1.30,
            hipWidth = 0.25 * 1.30,
        )

        val a = SilhouetteBodyFat.estimate(
            SilhouetteBodyFat.indicesFrom(honest, anchors)!!, Sex.MALE,
        )!!
        val b = SilhouetteBodyFat.estimate(
            SilhouetteBodyFat.indicesFrom(inflated, anchors)!!, Sex.MALE,
        )!!

        assertTrue(
            kotlin.math.abs(a.percent - b.percent) < 3.0,
            "scale-free means scale-free: ${a.percent} vs ${b.percent}",
        )
    }

    @Test
    fun `the female mapping is not the male one`() {
        val indices = SilhouetteBodyFat
            .indicesFrom(profile(shoulderWidth = 0.30, waistWidth = 0.23, hipWidth = 0.28), anchors)!!

        val male = SilhouetteBodyFat.estimate(indices, Sex.MALE)!!
        val female = SilhouetteBodyFat.estimate(indices, Sex.FEMALE)!!

        assertTrue(
            female.percent > male.percent + 4.0,
            "the same outline is worth more body fat on a woman: ${female.percent} vs " +
                "${male.percent}",
        )
    }

    @Test
    fun `an outline that is not a torso is refused rather than mapped`() {
        val absurd = ShapeIndices(waistToShoulder = 2.4, waistToHip = 1.0)

        assertNull(SilhouetteBodyFat.estimate(absurd, Sex.MALE))
    }

    @Test
    fun `it carries a wide interval so it cannot outvote a tape`() {
        val shape = EstimationMethod.PHOTO_SHAPE

        assertTrue(
            shape.standardErrorPercent > EstimationMethod.NAVY_CIRCUMFERENCE.standardErrorPercent,
            "a silhouette is coarser than a measured girth and must be weighted as such",
        )
        assertTrue(
            shape.repeatabilityPercent < shape.standardErrorPercent,
            "the same outline photographed twice gives the same ratio, which is what makes " +
                "this usable for tracking even though the absolute number is soft",
        )
    }

    @Test
    fun `indices are absent rather than guessed when the silhouette cannot support them`() {
        val empty = WidthProfile.torsoOnly(DoubleArray(200), topRow = 20, bottomRow = 179)

        assertNull(SilhouetteBodyFat.indicesFrom(empty, anchors))
    }

    @Test
    fun `a very lean outline admits it cannot resolve how lean`() {
        // Read off a labelled reference chart, waist-to-shoulder is 0.586 at eight per cent,
        // 0.592 at twelve and 0.580 at fifteen — flat, inside its own measurement noise. A
        // silhouette knows where the body ends and nothing about the surface inside it, so
        // it genuinely cannot separate those. Reporting a confident number there would be
        // the app's original sin repeated in a new place.
        val lean = SilhouetteBodyFat.estimate(
            ShapeIndices(waistToShoulder = 0.68, waistToHip = 0.78), Sex.MALE,
        )
        val midRange = SilhouetteBodyFat.estimate(
            ShapeIndices(waistToShoulder = 0.90, waistToHip = 0.95), Sex.MALE,
        )

        assertNotNull(lean)
        assertNotNull(midRange)
        assertTrue(
            lean.standardErrorPercent > midRange.standardErrorPercent + 2.0,
            "the plateau has to widen the interval: ${lean.standardErrorPercent} vs " +
                "${midRange.standardErrorPercent}",
        )
        assertTrue(lean.percent < 16.0, "it should still say 'lean': ${lean.percent}")
    }

    @Test
    fun `the waist is read at the navel band, not at the narrowest point`() {
        // This test previously asserted 0.20 / 0.32 — the profile's narrowest row — and it
        // was asserting the bug. That row sits below the navel on this silhouette, and the
        // narrowest row is where the fat is not: on a lean torso the minimum is under the
        // ribs, on a heavy one it is in nearly the same place, and the belly between them is
        // never measured. Reading a fixed band instead is the whole of the change.
        val indices = SilhouetteBodyFat
            .indicesFrom(profile(shoulderWidth = 0.32, waistWidth = 0.20, hipWidth = 0.28), anchors)

        assertNotNull(indices)
        assertTrue(
            indices.waistToShoulder > 0.20 / 0.32,
            "the navel band must read wider than the narrowest row, got " +
                "${indices.waistToShoulder}",
        )
        assertEquals(0.65, indices.waistToShoulder, 0.02)
    }
}
