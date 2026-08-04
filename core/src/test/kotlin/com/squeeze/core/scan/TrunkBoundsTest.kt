package com.squeeze.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The arm-exclusion bound.
 *
 * These encode the failure that motivated the class: a real photograph in which the subject
 * stood with one arm against their side produced a waist of about 125 cm on a visibly lean
 * man. Arm and torso were one continuous run in the mask, so the "torso width" was the
 * torso plus an arm, and every plausibility range accepted it because 125 cm is a waist
 * some people genuinely have.
 */
class TrunkBoundsTest {

    /** Shoulders at row 100 spanning 0.35..0.65, hips at row 300 spanning 0.40..0.60. */
    private fun bounds() = TrunkBounds(
        shoulderLeftX = 0.35,
        shoulderRightX = 0.65,
        shoulderRow = 100,
        hipLeftX = 0.40,
        hipRightX = 0.60,
        hipRow = 300,
    )

    @Test
    fun `an arm touching the waist is cut off the torso run`() {
        val trunk = bounds()

        // Waist row, midway between shoulders and hips. The mask run runs from 0.30 to 0.70
        // because an arm is touching the body on each side.
        val clipped = trunk.clip(startX = 0.30, endX = 0.70, row = 200)
        assertNotNull(clipped)

        val width = clipped.endInclusive - clipped.start
        val rawWidth = 0.70 - 0.30

        assertTrue(
            width < rawWidth,
            "expected the arms to be trimmed, but width stayed at $width",
        )
        // Half-span at row 200 interpolates to 0.125, widened by the soft-tissue margin to
        // about 0.194 — so the full allowed width is roughly 0.388, not 0.40.
        assertTrue(width in 0.30..0.40, "clipped width $width is outside the expected band")
    }

    @Test
    fun `a torso well inside the bound is left exactly as measured`() {
        val trunk = bounds()

        // A genuine waist, narrower than the bound. Nothing should be trimmed.
        val clipped = trunk.clip(startX = 0.44, endX = 0.56, row = 200)
        assertNotNull(clipped)

        assertEquals(0.44, clipped.start, 1e-9)
        assertEquals(0.56, clipped.endInclusive, 1e-9)
    }

    @Test
    fun `a large waist still fits inside the margin`() {
        val trunk = bounds()

        // The margin has to be generous enough that a genuinely broad person is measured
        // rather than clipped — understating a waist is no better than overstating it.
        val half = trunk.maxHalfWidthAt(200)
        assertTrue(
            half > 0.125 * 1.4,
            "margin is too tight for a large waist: half-width $half",
        )
    }

    @Test
    fun `a run entirely outside the trunk is rejected rather than measured`() {
        val trunk = bounds()

        // A detached arm run, far to the left of the body.
        assertNull(trunk.clip(startX = 0.05, endX = 0.15, row = 200))
    }

    @Test
    fun `the bound does not collapse or invert outside the shoulder-to-hip span`() {
        val trunk = bounds()

        // Extrapolating a line through two points would shrink this toward zero above the
        // shoulders and grow it without limit below the hips. Both are held instead.
        assertEquals(trunk.maxHalfWidthAt(100), trunk.maxHalfWidthAt(10), 1e-9)
        assertEquals(trunk.maxHalfWidthAt(300), trunk.maxHalfWidthAt(900), 1e-9)

        assertTrue(trunk.maxHalfWidthAt(0) > 0.0)
        assertTrue(trunk.maxHalfWidthAt(10_000) > 0.0)
    }

    @Test
    fun `the centre follows the body when the stance is off to one side`() {
        val trunk = TrunkBounds(
            shoulderLeftX = 0.30, shoulderRightX = 0.60, shoulderRow = 100,
            hipLeftX = 0.40, hipRightX = 0.70, hipRow = 300,
        )

        assertEquals(0.45, trunk.centreAt(100), 1e-9)
        assertEquals(0.55, trunk.centreAt(300), 1e-9)
        assertEquals(0.50, trunk.centreAt(200), 1e-9)
    }

    @Test
    fun `bounds are refused when the landmarks are not anatomically ordered`() {
        val upsideDown = FrontPoseGeometry(
            shoulderLeft = PosePoint(0.35, 0.80),
            shoulderRight = PosePoint(0.65, 0.80),
            hipLeft = PosePoint(0.40, 0.20),
            hipRight = PosePoint(0.60, 0.20),
        )

        assertNull(TrunkBounds.from(upsideDown, rowCount = 400))
    }

    @Test
    fun `left and right landmarks are ordered regardless of which the model reports first`() {
        val mirrored = FrontPoseGeometry(
            // x values swapped: the model's "left" is to the right of its "right".
            shoulderLeft = PosePoint(0.65, 0.25),
            shoulderRight = PosePoint(0.35, 0.25),
            hipLeft = PosePoint(0.60, 0.75),
            hipRight = PosePoint(0.40, 0.75),
        )

        val trunk = TrunkBounds.from(mirrored, rowCount = 400)
        assertNotNull(trunk)

        assertTrue(trunk.shoulderLeftX < trunk.shoulderRightX)
        assertTrue(trunk.hipLeftX < trunk.hipRightX)
    }
}

/**
 * Rejection of photographs that cannot be measured head-on.
 *
 * A rotated body foreshortens every horizontal measurement while the depth the estimator
 * assumes stays put, so the error is invisible downstream. Catching it at capture is the
 * only place it can be caught.
 */
class FrontalityCheckTest {

    /** Shoulders about 0.23 of stature apart, which is the adult norm. */
    private fun squareOn() = FrontPoseGeometry(
        shoulderLeft = PosePoint(0.38, 0.25),
        shoulderRight = PosePoint(0.62, 0.25),
        hipLeft = PosePoint(0.43, 0.55),
        hipRight = PosePoint(0.57, 0.55),
    )

    private val bodyHeightFraction = 0.8
    private val aspect = 0.75

    @Test
    fun `a square stance passes`() {
        assertNull(FrontalityCheck.evaluate(squareOn(), bodyHeightFraction, aspect))
    }

    @Test
    fun `a body turned away from the lens is rejected`() {
        // Rotation foreshortens the shoulder span. Everything else is unchanged.
        val turned = squareOn().let {
            it.copy(
                shoulderLeft = PosePoint(0.46, 0.25),
                shoulderRight = PosePoint(0.54, 0.25),
                hipLeft = PosePoint(0.47, 0.55),
                hipRight = PosePoint(0.53, 0.55),
            )
        }

        val reason = FrontalityCheck.evaluate(turned, bodyHeightFraction, aspect)
        assertNotNull(reason, "a turned body should not be measured")
        assertTrue(reason.contains("square", ignoreCase = true))
    }

    @Test
    fun `shoulders and hips twisted against each other are rejected`() {
        val twisted = squareOn().copy(
            hipLeft = PosePoint(0.485, 0.55),
            hipRight = PosePoint(0.515, 0.55),
        )

        assertNotNull(
            FrontalityCheck.evaluate(twisted, bodyHeightFraction, aspect),
            "a twisted stance should not be measured",
        )
    }

    @Test
    fun `degenerate inputs are passed through rather than reported as bad posture`() {
        // Nothing can be concluded from these, and inventing a posture complaint would send
        // the user off correcting something that was never wrong.
        assertNull(FrontalityCheck.evaluate(squareOn(), bodyHeightFraction = 0.0, aspect))
        assertNull(FrontalityCheck.evaluate(squareOn(), bodyHeightFraction, imageAspectRatio = 0.0))

        val collapsed = squareOn().copy(shoulderRight = PosePoint(0.38, 0.25))
        assertNull(FrontalityCheck.evaluate(collapsed, bodyHeightFraction, aspect))
    }
}
