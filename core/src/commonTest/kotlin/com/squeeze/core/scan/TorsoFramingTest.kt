package com.squeeze.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A photograph framed on the trunk is a first-class scan, not a degraded one.
 *
 * The full-body rule was only ever protecting one thing: the stature that turns pixels into
 * centimetres. Every figure the app now leads with — the shape reading and each ratio —
 * divides one width by another in the same image, so none of them ever needed it. What this
 * framing gives up is centimetres; what it buys is three or four times as many pixels across
 * the waist, and a frame it is much easier to hold the arms clear inside.
 */
class TorsoFramingTest {

    private fun geometry(
        shoulderY: Double,
        hipY: Double,
        shoulderHalf: Double = 0.16,
        hipHalf: Double = 0.09,
        centre: Double = 0.5,
    ) = FrontPoseGeometry(
        shoulderLeft = PosePoint(centre - shoulderHalf, shoulderY),
        shoulderRight = PosePoint(centre + shoulderHalf, shoulderY),
        hipLeft = PosePoint(centre - hipHalf, hipY),
        hipRight = PosePoint(centre + hipHalf, hipY),
        ankleLeft = null,
        ankleRight = null,
        nose = null,
    )

    @Test
    fun `a trunk filling the frame is measurable`() {
        // The framing the user is actually being asked for: shoulders near the top, hips
        // near the bottom, no head and no feet anywhere in shot.
        assertTrue(TorsoFraming.supports(geometry(shoulderY = 0.15, hipY = 0.85)))
    }

    @Test
    fun `a full-length photo also passes, since torso is only ever a fallback`() {
        // Shoulders and hips of a full-length subject sit around here. Nothing about this
        // framing should be rejected by the trunk path — it simply never reaches it, because
        // the scale path accepts it first.
        assertTrue(TorsoFraming.supports(geometry(shoulderY = 0.28, hipY = 0.52)))
    }

    @Test
    fun `a hip on the frame edge is refused`() {
        // A hip landmark is a joint centre and the body extends past it, so a hip on the
        // boundary means the widest part of the hip is outside the picture. The silhouette
        // there traces the crop rather than the person.
        assertTrue(!TorsoFraming.supports(geometry(shoulderY = 0.30, hipY = 0.99)))
    }

    @Test
    fun `a shoulder on the frame edge is refused for the same reason`() {
        assertTrue(!TorsoFraming.supports(geometry(shoulderY = 0.01, hipY = 0.60)))
    }

    @Test
    fun `a subject standing too far off to one side is refused`() {
        // Horizontal cropping matters as much as vertical: half a trunk gives half a waist.
        assertTrue(!TorsoFraming.supports(geometry(0.2, 0.8, centre = 0.02)))
    }

    @Test
    fun `a trunk too small in the frame is refused`() {
        // At this size the photo is a distant full-body shot that failed for some other
        // reason, and accepting it as a trunk scan would hand back a worse measurement than
        // the rejection it replaced.
        assertTrue(!TorsoFraming.supports(geometry(shoulderY = 0.45, hipY = 0.52)))
    }

    @Test
    fun `anchors put the shoulders and hips where the landmarks are`() {
        val anchors = TorsoFraming.anchorsFor(geometry(0.2, 0.8), rowCount = 500)

        assertNotNull(anchors)
        assertEquals(100, anchors.shoulderRow)
        assertEquals(400, anchors.hipRow)
    }

    @Test
    fun `the inferred knee is allowed to fall outside the picture`() {
        // Load-bearing. A frame that ends at the hips has no room below them by
        // construction, and the knee is never read as a row — it only sets how deep the
        // hip-width band reaches, and the searches clamp to the silhouette's own extent.
        // Refusing here would reject essentially every trunk photo.
        val anchors = TorsoFraming.anchorsFor(geometry(0.2, 0.85), rowCount = 500)

        assertNotNull(anchors)
        assertTrue(anchors.kneeRow > 500, "knee at ${anchors.kneeRow} should be off-frame")
    }

    @Test
    fun `the inferred chin still sits above the shoulders`() {
        // PoseAnchors enforces the ordering, so a chin that did not would throw rather than
        // produce anchors. This pins that the synthesis satisfies it even when the shoulders
        // are close to the top of the frame.
        val anchors = TorsoFraming.anchorsFor(geometry(0.06, 0.9), rowCount = 400)

        assertNotNull(anchors)
        assertTrue(anchors.chinRow < anchors.shoulderRow)
        assertTrue(anchors.chinRow >= 0)
    }

    @Test
    fun `shoulders on the very top row leave nowhere for a chin`() {
        assertNull(TorsoFraming.anchorsFor(geometry(0.0, 0.8), rowCount = 400))
    }

    @Test
    fun `hips above shoulders are not a body`() {
        assertNull(TorsoFraming.anchorsFor(geometry(0.8, 0.2), rowCount = 400))
    }

    @Test
    fun `the shape method reads a trunk-framed silhouette`() {
        // The whole point, end to end: anchors synthesised from a trunk photo feed the same
        // scale-free search the full-body path uses, and it produces indices.
        val rows = 500
        val anchors = TorsoFraming.anchorsFor(geometry(0.2, 0.8), rows)
        assertNotNull(anchors)

        val widths = DoubleArray(rows) { 0.30 }
        for (row in 100..400) widths[row] = 0.24
        widths[250] = 0.20
        val profile = WidthProfile(widths, DoubleArray(rows), 20, 480)

        val indices = SilhouetteBodyFat.indicesFrom(profile, anchors)

        assertNotNull(indices)
        assertTrue(indices.waistToShoulder > 0.0)
    }

    @Test
    fun `only full-body framing yields centimetres`() {
        assertTrue(ScanFraming.FULL_BODY.yieldsCentimetres)
        assertTrue(!ScanFraming.TORSO.yieldsCentimetres)
    }
}

class TorsoFrontalityTest {

    private fun geometry(shoulderHalf: Double, hipHalf: Double) = FrontPoseGeometry(
        shoulderLeft = PosePoint(0.5 - shoulderHalf, 0.2),
        shoulderRight = PosePoint(0.5 + shoulderHalf, 0.2),
        hipLeft = PosePoint(0.5 - hipHalf, 0.8),
        hipRight = PosePoint(0.5 + hipHalf, 0.8),
        ankleLeft = null,
        ankleRight = null,
        nose = null,
    )

    @Test
    fun `a square trunk passes`() {
        assertNull(FrontalityCheck.evaluateTorso(geometry(0.16, 0.10)))
    }

    @Test
    fun `a twisted trunk is still caught without a stature`() {
        // The reason this exists as its own function. Reaching the scale-free half by
        // passing a zero height would have skipped both tests and let a twisted stance
        // through unremarked on every trunk scan.
        val advice = FrontalityCheck.evaluateTorso(geometry(0.08, 0.16))

        assertNotNull(advice)
        assertTrue(advice.contains("twisted"), advice)
    }

    @Test
    fun `an ordinary adult is not told they are twisted`() {
        // The regression the upper bound used to be. Both numbers here are landmark spans,
        // not body breadths: the model's shoulder points sit near the acromion, so the
        // numerator is biacromial breadth at about 0.23 of stature, while its hip points are
        // joint centres around 0.10 apart — not the 0.16 that bi-iliac breadth runs at. A
        // square, unremarkable adult therefore lands near 2.3, and a bound at 2.2 refused
        // them while telling them to stand square.
        assertNull(FrontalityCheck.evaluateTorso(geometry(0.115, 0.05)))
    }

    @Test
    fun `a genuinely twisted trunk is still refused after the widening`() {
        assertNotNull(FrontalityCheck.evaluateTorso(geometry(0.16, 0.045)))
    }

    @Test
    fun `degenerate spans say nothing rather than guessing`() {
        assertNull(FrontalityCheck.evaluateTorso(geometry(0.0, 0.10)))
    }
}
