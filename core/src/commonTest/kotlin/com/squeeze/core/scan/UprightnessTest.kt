package com.squeeze.core.scan

import com.squeeze.core.math.toRadians
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Standing the photograph up.
 *
 * A scan of a sideways photograph reported **5.0%**. Every row index in this pipeline is a
 * horizontal slice, so on a frame where the body runs left to right the waist band lands on a
 * thigh, the shoulder band on a forearm, and their ratio is a number about nothing.
 *
 * The rotation cannot come from the file — EXIF orientation is stripped by screenshots, share
 * sheets and any re-encode — so it comes from the body. A standing person's shoulders are
 * above their hips, which is true of anatomy rather than of a container format.
 */
class UprightnessTest {

    /**
     * A trunk whose shoulder-to-hip vector points in [direction] degrees clockwise from
     * straight down, at the centre of the frame.
     */
    private fun trunk(direction: Double): FrontPoseGeometry {
        val radians = direction.toRadians()
        val dx = kotlin.math.sin(radians) * 0.2
        val dy = kotlin.math.cos(radians) * 0.2
        return FrontPoseGeometry(
            shoulderLeft = PosePoint(0.5 - 0.1, 0.5),
            shoulderRight = PosePoint(0.5 + 0.1, 0.5),
            hipLeft = PosePoint(0.5 + dx - 0.08, 0.5 + dy),
            hipRight = PosePoint(0.5 + dx + 0.08, 0.5 + dy),
        )
    }

    @Test
    fun `a standing subject needs no rotation`() {
        assertTrue(Uprightness.isUpright(trunk(0.0)))
        assertEquals(0, Uprightness.quarterTurnsClockwise(trunk(0.0)))
    }

    @Test
    fun `camera tilt and standing on one leg do not trigger a rotation`() {
        // The reason the threshold is generous. Real photographs of upright people lean, and
        // rotating one of those would be far worse than the problem being solved.
        listOf(-30.0, -15.0, 12.0, 28.0).forEach { lean ->
            assertEquals(0, Uprightness.quarterTurnsClockwise(trunk(lean)), "lean $lean")
        }
    }

    @Test
    fun `a subject whose head is to the left is turned clockwise`() {
        // Hips to the right of the shoulders. Turning the picture clockwise stands it up.
        val geometry = trunk(90.0)

        assertFalse(Uprightness.isUpright(geometry))
        assertEquals(1, Uprightness.quarterTurnsClockwise(geometry))
    }

    @Test
    fun `a subject whose head is to the right is turned the other way`() {
        // The photograph this whole change exists for: head to the image's right, legs to
        // the left, hips therefore left of the shoulders.
        val geometry = trunk(-90.0)

        assertFalse(Uprightness.isUpright(geometry))
        assertEquals(3, Uprightness.quarterTurnsClockwise(geometry))
    }

    @Test
    fun `an inverted subject is turned twice`() {
        assertEquals(2, Uprightness.quarterTurnsClockwise(trunk(180.0)))
        assertEquals(2, Uprightness.quarterTurnsClockwise(trunk(-170.0)))
        assertEquals(2, Uprightness.quarterTurnsClockwise(trunk(160.0)))
    }

    @Test
    fun `the lean is signed the way the rotation is`() {
        assertEquals(0.0, Uprightness.leanDegrees(trunk(0.0)), 1e-6)
        assertEquals(90.0, Uprightness.leanDegrees(trunk(90.0)), 1e-6)
        assertEquals(-90.0, Uprightness.leanDegrees(trunk(-90.0)), 1e-6)
    }

    @Test
    fun `applying the rotation makes the subject upright`() {
        // The property that matters, checked end to end: whatever the frame's rotation, the
        // number of turns this returns leaves the trunk pointing down.
        listOf(0.0, 45.1, 90.0, 135.0, 179.0, -45.1, -90.0, -135.0).forEach { direction ->
            val turns = Uprightness.quarterTurnsClockwise(trunk(direction))
            val corrected = trunk(direction - 90.0 * turns)

            assertTrue(
                Uprightness.isUpright(corrected),
                "$direction with $turns turns left ${Uprightness.leanDegrees(corrected)}",
            )
        }
    }

    @Test
    fun `every rotation is searched, upright first`() {
        assertEquals(listOf(0, 1, 3, 2), Uprightness.SEARCH_ORDER)
        assertEquals(setOf(0, 1, 2, 3), Uprightness.SEARCH_ORDER.toSet())
    }
}
