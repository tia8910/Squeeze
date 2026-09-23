package com.squeeze.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Where a trunk-framed scan puts the chin, and why it matters more than it looks.
 *
 * **The scan this exists for.** A user's trunk photograph measured a waist, a chest and a
 * waist-to-height of 0.41 — and still reported the outline's constant under the words "not
 * resolved by the photo". The tape equation needs a waist *and a neck*, and the neck was
 * never usable: [TorsoFraming] had no chin landmark, so it placed the chin a quarter of the
 * trunk span above the shoulders, and on a real body that sits below the jaw.
 * [AnatomicalLevelFinder] then searched between 0.35 and 0.80 of chin-to-shoulder, which put
 * the whole band on the trapezius.
 *
 * That file's own comment says what follows: the trapezius "is how a 175 cm man ends up with
 * a 52 cm neck — which drives the Navy equation to a negative body fat and so produces no
 * estimate at all". It was describing the bug before anyone hit it.
 *
 * The full-body path always used the mouth landmarks. The trunk path simply could not reach
 * them, so they now travel on [FrontPoseGeometry].
 */
class ChinRowTest {

    private fun geometry(
        shoulderY: Double,
        hipY: Double,
        mouthY: Double? = null,
    ) = FrontPoseGeometry(
        shoulderLeft = PosePoint(0.34, shoulderY),
        shoulderRight = PosePoint(0.66, shoulderY),
        hipLeft = PosePoint(0.41, hipY),
        hipRight = PosePoint(0.59, hipY),
        mouth = mouthY?.let { PosePoint(0.5, it) },
    )

    @Test
    fun `the mouth is used when the photograph has one`() {
        val anchors = TorsoFraming.anchorsFor(
            geometry(shoulderY = 0.40, hipY = 0.60, mouthY = 0.30),
            rowCount = 1000,
        )

        assertNotNull(anchors)
        assertEquals(300, anchors.chinRow)
    }

    @Test
    fun `without a mouth it still falls back, so nothing that worked stops working`() {
        val anchors = TorsoFraming.anchorsFor(
            geometry(shoulderY = 0.40, hipY = 0.60),
            rowCount = 1000,
        )

        assertNotNull(anchors)
        // A quarter of the 200-row trunk above the shoulders.
        assertEquals(350, anchors.chinRow)
    }

    @Test
    fun `a mouth below the shoulders is refused rather than believed`() {
        // A misdetection, or a person bent forward. Either way it would invert the anchors
        // and PoseAnchors would reject the lot, losing the scan rather than the landmark.
        val anchors = TorsoFraming.anchorsFor(
            geometry(shoulderY = 0.40, hipY = 0.60, mouthY = 0.55),
            rowCount = 1000,
        )

        assertNotNull(anchors)
        assertEquals(350, anchors.chinRow, "should have fallen back to the trunk fraction")
    }

    @Test
    fun `on the real photograph the neck band moves off the trapezius`() {
        // Reconstructed from the scan in the class header, in a frame 1994 rows tall:
        // shoulders near 1200, hips near 1517, mouth near 990.
        val rows = 1994
        val withMouth = TorsoFraming.anchorsFor(
            geometry(1200.0 / rows, 1517.0 / rows, mouthY = 990.0 / rows),
            rowCount = rows,
        )
        val without = TorsoFraming.anchorsFor(
            geometry(1200.0 / rows, 1517.0 / rows),
            rowCount = rows,
        )

        assertNotNull(withMouth)
        assertNotNull(without)

        // AnatomicalLevelFinder searches 0.35 to 0.80 of chin-to-shoulder for the narrowest
        // row. Asserted here as the band's own bounds, because that is the thing that was
        // landing in the wrong place.
        fun bandOf(a: PoseAnchors): IntRange {
            val span = a.shoulderRow - a.chinRow
            return (a.chinRow + (span * 0.35).toInt())..(a.chinRow + (span * 0.80).toInt())
        }

        val fixed = bandOf(withMouth)
        val broken = bandOf(without)

        // The old band sat within thirty rows of the shoulder line — trapezius, not neck.
        assertTrue(
            broken.first > 1140 && broken.last > 1170,
            "the fallback band $broken should be the one that hugs the shoulders",
        )
        // The new band spans the actual neck and clears the shoulder line by a wide margin.
        assertTrue(
            fixed.first < 1080 && fixed.last < 1170,
            "the mouth-derived band $fixed should sit on the neck",
        )
        assertTrue(
            fixed.first < broken.first,
            "using the mouth must move the search upward, not merely change it",
        )
    }
}
