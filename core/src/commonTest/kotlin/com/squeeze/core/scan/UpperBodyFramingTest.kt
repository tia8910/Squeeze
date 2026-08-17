package com.squeeze.core.scan

import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A photograph with the upper body in it is measurable, and it used to be refused.
 *
 * The message it was refused with — "your shoulders and hips were not both in the picture, so
 * there is no waist to measure" — was not true of the photographs it was shown to. The waist
 * was in the picture. The *hip* was not, and the hip is a denominator rather than the
 * measurement: [SilhouetteBodyFat] has always had a shoulder-denominator path and has always
 * carried a wider interval for it, because the arms attach at the shoulder line.
 *
 * So this framing produces the weaker of the two readings rather than none. The tests below
 * are about the two things that have to hold for that to be honest: the waist really is in
 * the picture, and nothing is reported about the hips that was not seen.
 */
class UpperBodyFramingTest {

    private fun geometry(
        shoulderY: Double,
        hipY: Double,
        shoulderHalf: Double = 0.16,
        hipHalf: Double = 0.10,
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
    fun `a frame ending below the navel is measurable`() {
        // Shoulders a fifth of the way down, hips inferred just past the bottom edge. The
        // waist band runs 58% to 74% of the trunk, which lands at 0.72 to 0.87 — inside the
        // picture, which is the whole question.
        val upper = geometry(shoulderY = 0.20, hipY = 1.10)

        assertTrue(UpperBodyFraming.supports(upper))
        // And the framing it is a fallback for still refuses it, so the ordering in the
        // detector is what decides which one a photo gets.
        assertTrue(!TorsoFraming.supports(upper))
    }

    @Test
    fun `a chest shot is refused, because the waist is not in it`() {
        // Shoulders high, hips far below the frame: the trunk is 1.4 frames long, so the
        // waist band runs from 0.89 to well past the bottom edge. There is a shoulder width
        // here and nothing to divide by it.
        assertTrue(!UpperBodyFraming.supports(geometry(shoulderY = 0.08, hipY = 1.48)))
    }

    @Test
    fun `the same rule caps how far the hip may be inferred`() {
        // There is no separate extrapolation constant, and this is why there does not need to
        // be one. Requiring the waist band inside the picture requires the visible part of
        // the trunk to be at least WAIST_BAND_END of the whole, which caps the hip at about a
        // quarter of a trunk outside the frame.
        //
        // Both bodies below have a 0.70 trunk. The first has its hip 18% of that past the
        // edge and its waist band ends at 0.944; the second has it 33% past, and the band
        // ends at 1.049 — outside the photograph.
        val trunk = 0.70
        fun atOvershoot(k: Double) =
            geometry(shoulderY = 1.0 + trunk * k - trunk, hipY = 1.0 + trunk * k)

        assertTrue(UpperBodyFraming.supports(atOvershoot(0.18)))
        assertTrue(!UpperBodyFraming.supports(atOvershoot(0.33)))
    }

    @Test
    fun `a shoulder on the frame edge is still refused`() {
        // The shoulder is the denominator of everything this framing can report, and the
        // landmark is the joint centre — a shoulder on the boundary means the deltoid, which
        // is what the silhouette actually measures, is outside the picture.
        assertTrue(!UpperBodyFraming.supports(geometry(shoulderY = 0.01, hipY = 1.05)))
        assertTrue(!UpperBodyFraming.supports(geometry(0.20, 1.05, centre = 0.10)))
    }

    @Test
    fun `a full trunk photo also passes, since this is only ever the last fallback`() {
        assertTrue(UpperBodyFraming.supports(geometry(shoulderY = 0.15, hipY = 0.85)))
    }

    @Test
    fun `the anchors are the trunk path's, hip row and all`() {
        // Reused rather than reimplemented: the arithmetic that turns landmarks into rows
        // does not care whether the hip row lands inside the picture, and PoseAnchors only
        // requires the rows be in anatomical order.
        val upper = geometry(shoulderY = 0.20, hipY = 1.10)
        val anchors = UpperBodyFraming.anchorsFor(upper, rowCount = 500)

        assertNotNull(anchors)
        assertEquals(TorsoFraming.anchorsFor(upper, 500), anchors)
        assertEquals(100, anchors.shoulderRow)
        assertTrue(anchors.hipRow > 500, "hip at ${anchors.hipRow} should be off-frame")
    }

    @Test
    fun `the hip is dropped rather than clamped back into the picture`() {
        // The failure the parameter exists to prevent, and the reason it is a parameter
        // rather than something inferred from the profile. Every search in the shape reader
        // clamps its band to the silhouette's extent, so a hip band that runs off the bottom
        // of the photograph collapses onto the crop line — which is abdomen, a width near the
        // waist's own.
        //
        // The geometry is the case where that actually bites: a hip at 0.98, inside the
        // picture by pixels but inside the edge margin, so TorsoFraming refuses it and this
        // framing takes it. The hip band starts at row 490 of 500 and the clamp hands back
        // the ten rows of crop below it.
        //
        // The widths say the rest: 0.30 at the shoulders, 0.26 through the waist, and 0.26
        // still at the bottom row where the photograph ends. Read as a hip that is
        // 0.26/0.26 = 1.0, which is a body fat in the mid thirties, reported with the narrow
        // interval a measured hip earns.
        val rows = 500
        val cropped = geometry(shoulderY = 0.20, hipY = 0.98)
        assertTrue(UpperBodyFraming.supports(cropped))
        assertTrue(!TorsoFraming.supports(cropped))

        val anchors = UpperBodyFraming.anchorsFor(cropped, rows)
        assertNotNull(anchors)

        val widths = DoubleArray(rows) { 0.30 }
        for (row in 200 until rows) widths[row] = 0.26
        val profile = WidthProfile(widths, DoubleArray(rows), 10, rows - 1)

        val clamped = SilhouetteBodyFat.indicesFrom(profile, anchors, hipInFrame = true)
        val dropped = SilhouetteBodyFat.indicesFrom(profile, anchors, hipInFrame = false)

        assertNotNull(clamped)
        assertNotNull(dropped)
        val clampedHip = clamped.waistToHip
        assertNotNull(clampedHip, "the clamp is real, which is why it has to be shut off")
        assertEquals(1.0, clampedHip, 1e-9)
        assertNull(dropped.waistToHip)
        // The numerator is untouched — this drops a denominator, it does not change a
        // measurement. The instruction this was built to was to accept the photograph with
        // the measurement exactly as it stands.
        assertEquals(clamped.waistToShoulder, dropped.waistToShoulder, 1e-12)
    }

    @Test
    fun `an upper-body reading is the shoulder reading, unchanged`() {
        // No new arithmetic anywhere in this path. The estimate an upper-body photo produces
        // is exactly the one the existing shoulder-only branch produces for the same ratio,
        // interval included.
        val rows = 500
        val anchors = UpperBodyFraming.anchorsFor(geometry(0.20, 1.10), rows)
        assertNotNull(anchors)

        val widths = DoubleArray(rows) { 0.30 }
        for (row in 250..rows - 1) widths[row] = 0.27
        val profile = WidthProfile(widths, DoubleArray(rows), 10, rows - 1)

        val indices = SilhouetteBodyFat.indicesFrom(profile, anchors, hipInFrame = false)
        assertNotNull(indices)

        val viaPhoto = SilhouetteBodyFat.estimate(indices, Sex.MALE)
        val viaRatio = SilhouetteBodyFat.estimate(
            ShapeIndices(waistToShoulder = indices.waistToShoulder, waistToHip = null),
            Sex.MALE,
        )

        assertNotNull(viaPhoto)
        assertNotNull(viaRatio)
        assertEquals(viaRatio.percent, viaPhoto.percent, 1e-12)
        assertEquals(viaRatio.standardErrorPercent, viaPhoto.standardErrorPercent, 1e-12)
    }

    @Test
    fun `the framing knows the hips are not in it`() {
        assertTrue(ScanFraming.FULL_BODY.hipsInShot)
        assertTrue(ScanFraming.TORSO.hipsInShot)
        assertTrue(!ScanFraming.UPPER_BODY.hipsInShot)
        assertTrue(!ScanFraming.UPPER_BODY.yieldsCentimetres)
    }

    @Test
    fun `no hip level is reported from a hip that was never seen`() {
        // A pose model asked where the hips are in a picture that does not contain them
        // returns its prior, and a prior is level by construction. Printing "hips sit level"
        // off it would be the app inventing a finding about a part of the body it never
        // looked at.
        val tilted = FrontPoseGeometry(
            shoulderLeft = PosePoint(0.34, 0.20),
            shoulderRight = PosePoint(0.66, 0.24),
            hipLeft = PosePoint(0.40, 1.05),
            hipRight = PosePoint(0.60, 1.14),
        )

        val seen = PostureAnalysis.analyse(tilted, hipsObserved = true)
        val inferred = PostureAnalysis.analyse(tilted, hipsObserved = false)

        assertTrue(seen.any { it.name == "Hip level" })
        assertTrue(inferred.none { it.name == "Hip level" })
        // The shoulders were in the picture, so what was seen is still reported.
        assertTrue(inferred.any { it.name == "Shoulder level" })
    }
}

/**
 * What is left of the frontality check when both of its signals are gone.
 *
 * [FrontalityCheck] was built on two: shoulder span against stature, and shoulder span
 * against hip span. An upper-body photograph has neither — no crown and feet for the first,
 * no observed pelvis for the second. What remains is a gross-value guard, and the loss of
 * protection is real rather than papered over.
 */
class UpperBodyFrontalityTest {

    private fun geometry(shoulderHalf: Double, hipHalf: Double) = FrontPoseGeometry(
        shoulderLeft = PosePoint(0.5 - shoulderHalf, 0.2),
        shoulderRight = PosePoint(0.5 + shoulderHalf, 0.2),
        hipLeft = PosePoint(0.5 - hipHalf, 1.05),
        hipRight = PosePoint(0.5 + hipHalf, 1.05),
    )

    @Test
    fun `a stance the trunk check would refuse is accepted here`() {
        // The substance of the loosening. Against an inferred pelvis a shoulder-to-hip ratio
        // outside the adult range is evidence about the pose prior, not about the person, so
        // refusing the photograph on it rejects good pictures for a reason the check cannot
        // actually see.
        val narrow = geometry(shoulderHalf = 0.09, hipHalf = 0.11)

        assertNotNull(FrontalityCheck.evaluateTorso(narrow))
        assertNull(FrontalityCheck.evaluateUpperBody(narrow))
    }

    @Test
    fun `a gross value is still caught, because that is a misdetection`() {
        // Shoulders nearly four times an inferred pelvis is not a turned body. It is the
        // model having found something that is not a person, and measuring a misdetection is
        // how this app has produced every wrong number it has produced.
        val advice = FrontalityCheck.evaluateUpperBody(geometry(0.19, 0.05))

        assertNotNull(advice)
        assertTrue(advice.contains("twisted"), advice)
    }

    @Test
    fun `a square upper body passes`() {
        assertNull(FrontalityCheck.evaluateUpperBody(geometry(0.16, 0.10)))
    }

    @Test
    fun `degenerate spans say nothing rather than guessing`() {
        assertNull(FrontalityCheck.evaluateUpperBody(geometry(0.16, 0.0)))
    }
}
