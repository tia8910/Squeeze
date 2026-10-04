package com.squeeze.core.scan

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CircumferenceEstimatorTest {

    @Test
    fun `a circular slice matches the exact circle circumference`() {
        // Ramanujan's approximation is exact when the axes are equal, so this pins the
        // formula rather than merely checking it is close.
        val radius = 15.0
        val perimeter = CircumferenceEstimator.ellipsePerimeter(radius, radius)

        assertEquals(2.0 * PI * radius, perimeter, 1e-9)
    }

    @Test
    fun `elliptical perimeter sits between the inscribed and circumscribed circles`() {
        val a = 20.0
        val b = 12.0
        val perimeter = CircumferenceEstimator.ellipsePerimeter(a, b)

        assertTrue(perimeter > 2.0 * PI * b, "must exceed the circle on the minor axis")
        assertTrue(perimeter < 2.0 * PI * a, "must fall short of the circle on the major axis")
    }

    @Test
    fun `axis order does not change the result`() {
        assertEquals(
            CircumferenceEstimator.ellipsePerimeter(18.0, 11.0),
            CircumferenceEstimator.ellipsePerimeter(11.0, 18.0),
            1e-12,
        )
    }

    @Test
    fun `circumference uses width and depth as full axes not radii`() {
        // A 30 cm wide, 30 cm deep slice is a 30 cm diameter circle: circumference 94.2 cm.
        assertEquals(PI * 30.0, CircumferenceEstimator.circumference(30.0, 30.0), 1e-9)
    }

    @Test
    fun `depth genuinely changes the estimate`() {
        // The point of two photographs: a flat torso and a deep one share a front width but
        // are not the same size. A front-only method reads them identically.
        val flat = CircumferenceEstimator.circumference(frontWidthCm = 32.0, sideDepthCm = 18.0)
        val deep = CircumferenceEstimator.circumference(frontWidthCm = 32.0, sideDepthCm = 26.0)

        assertTrue(deep > flat + 5.0, "depth must move the result materially: $flat vs $deep")
    }

    @Test
    fun `a realistic waist lands in a believable range`() {
        // 32 cm across, 21 cm deep is a roughly 85 cm waist.
        val waist = CircumferenceEstimator.circumference(32.0, 21.0)
        assertEquals(85.0, waist, 3.0)
    }

    @Test
    fun `level mismatch is detected beyond the tolerance`() {
        assertFalse(CircumferenceEstimator.isLevelMismatched(0.50, 0.52))
        assertTrue(CircumferenceEstimator.isLevelMismatched(0.50, 0.58))
    }
}

class ScaleRecoveryTest {

    @Test
    fun `width converts using height as the reference length`() {
        // A 180 cm person filling half the frame height. In a square image, a width of 0.1
        // frame widths is 0.1 * 360 = 36 cm.
        val scale = ScaleRecovery(heightCm = 180.0, bodyHeightFraction = 0.5)

        assertEquals(36.0, scale.widthToCm(0.1, imageAspectRatio = 1.0), 1e-9)
    }

    @Test
    fun `aspect ratio is applied so portrait images are not silently wrong`() {
        val scale = ScaleRecovery(heightCm = 180.0, bodyHeightFraction = 0.9)

        val square = scale.widthToCm(0.1, imageAspectRatio = 1.0)
        val portrait = scale.widthToCm(0.1, imageAspectRatio = 0.75)

        // Widths are normalised against image width but scale comes from image height, so a
        // portrait frame's normalised width covers fewer centimetres. Omitting this scales
        // every circumference by the aspect ratio, which looks plausible and is not.
        assertEquals(square * 0.75, portrait, 1e-9)
    }

    @Test
    fun `measurements scale linearly with the stated height`() {
        val shorter = ScaleRecovery(160.0, 0.8).widthToCm(0.1, 1.0)
        val taller = ScaleRecovery(180.0, 0.8).widthToCm(0.1, 1.0)

        // Documents the method's dependence on an honest height: rounding 174 up to 176
        // shifts every circumference by about 1%.
        assertEquals(180.0 / 160.0, taller / shorter, 1e-9)
    }

    @Test
    fun `tight framing is flagged`() {
        assertFalse(ScaleRecovery(180.0, 0.75).isFramingTooTight())
        assertTrue(ScaleRecovery(180.0, 0.97).isFramingTooTight())
    }

    @Test
    fun `implausible inputs are rejected`() {
        assertTrue(runCatching { ScaleRecovery(heightCm = 40.0, bodyHeightFraction = 0.8) }.isFailure)
        assertTrue(runCatching { ScaleRecovery(heightCm = 180.0, bodyHeightFraction = 0.0) }.isFailure)
    }
}

class BodyScanAnalyserTest {

    private fun analyser(bodyHeightFraction: Double = 0.8) = BodyScanAnalyser(
        scale = ScaleRecovery(heightCm = 180.0, bodyHeightFraction = bodyHeightFraction),
        imageAspectRatio = 1.0,
    )

    private fun marker(
        site: ScanSite,
        front: Double,
        side: Double,
        frontHeight: Double = 0.5,
        sideHeight: Double = 0.5,
    ) = ScanMarker(
        site,
        BodySlice(
            frontWidthFraction = front,
            sideWidthFraction = side,
            frontHeightFraction = frontHeight,
            sideHeightFraction = sideHeight,
        ),
    )

    @Test
    fun `a complete scan produces circumferences usable for body fat`() {
        val result = analyser().analyse(
            listOf(
                marker(ScanSite.NECK, 0.0551, 0.0524),
                marker(ScanSite.WAIST, 0.1307, 0.0941),
                marker(ScanSite.HIP, 0.1520, 0.1155),
            ),
        )

        assertTrue(result.usableForBodyFat)
        assertNotNull(result.circumferences.neckCm)
        assertNotNull(result.circumferences.waistCm)
        assertNotNull(result.circumferences.hipCm)
        assertTrue(result.warnings.none { it is ScanWarning.LevelMismatch })
    }

    @Test
    fun `a scan missing the waist cannot produce body fat`() {
        val result = analyser().analyse(listOf(marker(ScanSite.NECK, 0.0551, 0.0524)))

        assertFalse(result.usableForBodyFat)
        assertTrue(result.warnings.any { it is ScanWarning.MissingRequiredSite })
        // The neck measurement is still returned: a partial scan is worth storing.
        assertNotNull(result.circumferences.neckCm)
    }

    @Test
    fun `levels marked at different heights in the two views are flagged`() {
        val result = analyser().analyse(
            listOf(marker(ScanSite.WAIST, 0.15, 0.10, frontHeight = 0.50, sideHeight = 0.62)),
        )

        assertTrue(
            result.warnings.any { it is ScanWarning.LevelMismatch && it.site == ScanSite.WAIST },
            "measuring the waist in one photo and the ribs in the other must be caught",
        )
    }

    @Test
    fun `an implausible cross-section is flagged`() {
        // Far wider than deep: almost certainly a misdetected side view.
        val result = analyser().analyse(listOf(marker(ScanSite.WAIST, 0.30, 0.05)))

        assertTrue(result.warnings.any { it is ScanWarning.ImplausibleShape })
    }

    @Test
    fun `tight framing is reported once for the whole scan`() {
        val result = analyser(bodyHeightFraction = 0.97).analyse(
            listOf(marker(ScanSite.NECK, 0.075, 0.07), marker(ScanSite.WAIST, 0.15, 0.10)),
        )

        assertEquals(1, result.warnings.count { it is ScanWarning.FramingTooTight })
    }

    @Test
    fun `measured circumferences are physiologically believable`() {
        // 180 cm subject filling 80% of a square frame. A waist 0.15 frame-widths across
        // and 0.10 deep should land near a real human waist rather than orders out.
        val result = analyser().analyse(listOf(marker(ScanSite.WAIST, 0.15, 0.10)))

        val waist = result.circumferences.waistCm
        assertNotNull(waist)
        assertTrue(waist in 60.0..120.0, "waist estimate outside human range: $waist")
    }
}

class AutomaticScanBuilderTest {

    private fun figure(waistRow: Int, waistWidth: Double = 0.15): WidthProfile {
        val widths = DoubleArray(200)
        for (row in 0..19) widths[row] = 0.10
        for (row in 20..29) widths[row] = 0.06
        for (row in 30..99) widths[row] = 0.26
        widths[waistRow] = waistWidth
        for (row in 100..119) widths[row] = 0.24
        widths[110] = 0.28
        for (row in 120..199) widths[row] = 0.16
        return WidthProfile.torsoOnly(widths, topRow = 0, bottomRow = 199)
    }

    private fun anchors() = PoseAnchors(chinRow = 18, shoulderRow = 35, hipRow = 100, kneeRow = 160)

    @Test
    fun `sites detected in both views become markers`() {
        val markers = AutomaticScanBuilder.build(
            frontProfile = figure(80), frontAnchors = anchors(),
            sideProfile = figure(80), sideAnchors = anchors(),
        )

        val sites = markers.map { it.site }.toSet()
        assertTrue(ScanSite.WAIST in sites)
        assertTrue(ScanSite.NECK in sites)
        assertTrue(ScanSite.HIP in sites)
    }

    @Test
    fun `each view is measured on its own silhouette`() {
        // The subject shifted between shots, so the waist sits at a different row in each
        // photo. Both must still be found; assuming alignment would mismeasure one.
        val markers = AutomaticScanBuilder.build(
            frontProfile = figure(75), frontAnchors = anchors(),
            sideProfile = figure(85), sideAnchors = anchors(),
        )

        val waist = markers.firstOrNull { it.site == ScanSite.WAIST }
        assertNotNull(waist)
        assertEquals(0.15, waist.slice.frontWidthFraction, 1e-9)
        assertEquals(0.15, waist.slice.sideWidthFraction, 1e-9)
    }

    @Test
    fun `a site missing from the side view falls back to an assumed depth`() {
        // Side profile cropped above the hips, so no hip can be found there. Dropping the
        // hip would lose a measurement the front photo genuinely supports; the fallback
        // keeps it and marks it as assumed so it is never presented as measured.
        val croppedWidths = DoubleArray(200)
        for (row in 20..29) croppedWidths[row] = 0.06
        for (row in 30..90) croppedWidths[row] = 0.26
        croppedWidths[80] = 0.15
        val cropped = WidthProfile.torsoOnly(croppedWidths, topRow = 20, bottomRow = 90)

        val markers = AutomaticScanBuilder.build(
            frontProfile = figure(80), frontAnchors = anchors(),
            sideProfile = cropped, sideAnchors = anchors(),
        )

        val hip = markers.firstOrNull { it.site == ScanSite.HIP }
        assertNotNull(hip)
        assertTrue(hip.depthAssumed, "a depth that was not photographed must say so")
        assertTrue(hip.slice.sideWidthFraction > 0.0)
    }

    @Test
    fun `a front-only scan measures every site the front photo supports`() {
        // The side photo is optional. Without one, depth comes from DepthRatios and every
        // marker is flagged, but the user still gets a usable set of measurements.
        val markers = AutomaticScanBuilder.build(
            frontProfile = figure(80),
            frontAnchors = anchors(),
        )

        assertTrue(markers.isNotEmpty())
        assertTrue(markers.all { it.depthAssumed })
        assertTrue(markers.any { it.site == ScanSite.WAIST })
    }

    @Test
    fun `a back photo is averaged with the front rather than treated as depth`() {
        // A back view measures the same axis as the front. Averaging the two halves the
        // random error; treating it as a depth would invent a measurement.
        val wider = figure(80, waistWidth = 0.19)

        val frontOnly = AutomaticScanBuilder.build(figure(80), anchors())
            .first { it.site == ScanSite.WAIST }
        val withBack = AutomaticScanBuilder.build(
            frontProfile = figure(80), frontAnchors = anchors(),
            backProfile = wider, backAnchors = anchors(),
        ).first { it.site == ScanSite.WAIST }

        assertEquals(0.15, frontOnly.slice.frontWidthFraction, 1e-9)
        assertEquals((0.15 + 0.19) / 2.0, withBack.slice.frontWidthFraction, 1e-9)
        assertTrue(withBack.depthAssumed, "a back view supplies no depth")
    }

    @Test
    fun `heights are normalised against each body so differing framing still pairs`() {
        // Same anatomy, but the side photo has the subject smaller in frame. The waist is at
        // the same point on the body, so the paired heights must agree and not trip the
        // mismatch warning.
        val front = figure(80)

        val sideWidths = DoubleArray(200)
        for (row in 25..34) sideWidths[row] = 0.06
        for (row in 35..99) sideWidths[row] = 0.26
        sideWidths[80] = 0.15
        for (row in 100..114) sideWidths[row] = 0.24
        val side = WidthProfile.torsoOnly(sideWidths, topRow = 25, bottomRow = 190)

        val markers = AutomaticScanBuilder.build(front, anchors(), side, anchors())
        val waist = markers.firstOrNull { it.site == ScanSite.WAIST }

        assertNotNull(waist)
        assertTrue(
            waist.slice.frontHeightFraction in 0.0..1.0 &&
                waist.slice.sideHeightFraction in 0.0..1.0,
            "heights must be expressed relative to the body, not the frame",
        )
    }

    /**
     * A front photo whose neck band carries no silhouette at all.
     *
     * Rows 18 to 35 are empty — a dark neck against a dark wall, a collar the segmenter
     * absorbed into the background, a chin shadow. This is not a contrived case: it is the
     * state the app was actually in when it reported the outline method's constant to a
     * photograph with a plainly visible neck in it.
     */
    private fun necklessFigure(): WidthProfile {
        val widths = DoubleArray(200)
        for (row in 0..17) widths[row] = 0.10
        for (row in 36..99) widths[row] = 0.26
        widths[80] = 0.15
        for (row in 100..119) widths[row] = 0.24
        widths[110] = 0.28
        for (row in 120..199) widths[row] = 0.16
        return WidthProfile.torsoOnly(widths, topRow = 0, bottomRow = 199)
    }

    @Test
    fun `the part model supplies a neck the silhouette never found`() {
        // **The failure this parameter exists for.** Without it the front photo yields a
        // waist and no neck, `waist - neck` cannot be formed, the Navy equation returns null
        // and the scan has no tape reading — which is how one user was shown 11.6% under the
        // words "not resolved by the photo", twice, on photographs of two different bodies.
        val withoutModel = AutomaticScanBuilder.build(necklessFigure(), anchors())
        assertTrue(
            withoutModel.none { it.site == ScanSite.NECK },
            "the silhouette must genuinely fail here or this test proves nothing",
        )

        val withModel = AutomaticScanBuilder.build(
            necklessFigure(),
            anchors(),
            neck = NeckReading(
                heightFraction = 0.125,
                widthFraction = 0.045,
                bandCoverage = 1.0,
                faceWidthFraction = 0.08,
            ),
        )

        val neck = withModel.firstOrNull { it.site == ScanSite.NECK }
        assertNotNull(neck)
        assertEquals(0.045, neck.slice.frontWidthFraction, 1e-9)
    }

    @Test
    fun `the model's neck replaces the silhouette's rather than averaging with it`() {
        // The silhouette finds 0.06 here, at row 20. The two numbers are not two readings of
        // one quantity — one is bare neck skin, the other is the narrowest row of
        // head-plus-hair-plus-collar — so splitting the difference would land on a width that
        // is neither, and do it silently.
        val silhouette = AutomaticScanBuilder.build(figure(80), anchors())
            .first { it.site == ScanSite.NECK }
        assertEquals(0.06, silhouette.slice.frontWidthFraction, 1e-9)

        val replaced = AutomaticScanBuilder.build(
            figure(80),
            anchors(),
            neck = NeckReading(
                heightFraction = 0.125,
                widthFraction = 0.045,
                bandCoverage = 1.0,
                faceWidthFraction = 0.08,
            ),
        ).first { it.site == ScanSite.NECK }

        assertEquals(0.045, replaced.slice.frontWidthFraction, 1e-9)
        // Row 25 of 200, expressed against the body span rather than the frame.
        assertEquals(25.0 / 199.0, replaced.slice.frontHeightFraction, 1e-9)
    }

    @Test
    fun `a back view is not averaged into a neck read from bare skin`() {
        // A back photo's neck is a silhouette neck, so averaging it in would put back most of
        // the error the model was brought in to remove. Every other site still averages.
        val back = figure(80, waistWidth = 0.19)

        val markers = AutomaticScanBuilder.build(
            frontProfile = figure(80), frontAnchors = anchors(),
            backProfile = back, backAnchors = anchors(),
            neck = NeckReading(
                heightFraction = 0.125,
                widthFraction = 0.045,
                bandCoverage = 1.0,
                faceWidthFraction = 0.08,
            ),
        )

        assertEquals(
            0.045,
            markers.first { it.site == ScanSite.NECK }.slice.frontWidthFraction,
            1e-9,
        )
        assertEquals(
            (0.15 + 0.19) / 2.0,
            markers.first { it.site == ScanSite.WAIST }.slice.frontWidthFraction,
            1e-9,
        )
    }

    @Test
    fun `when the model says there is no neck, the silhouette does not answer instead`() {
        // **What a fallback cost on a real photograph.** The part model refused a
        // front-double-biceps pose, the builder fell through to the silhouette, and the
        // silhouette's narrowest chin-to-shoulder row was a trapezius between two raised arms:
        // 54.3 cm, discarded downstream as impossible, leaving the scan exactly where it had
        // been. A refusal the model made deliberately must not be filled in by the measurement
        // it was brought in to replace.
        val silhouetteAnswers = AutomaticScanBuilder.build(figure(80), anchors())
        assertTrue(silhouetteAnswers.any { it.site == ScanSite.NECK })

        val modelRefused = AutomaticScanBuilder.build(
            figure(80),
            anchors(),
            neck = null,
            partMaskRead = true,
        )

        assertTrue(
            modelRefused.none { it.site == ScanSite.NECK },
            "a refused neck must stay refused",
        )
        // Only the neck. Every other site is still the silhouette's to measure.
        assertTrue(modelRefused.any { it.site == ScanSite.WAIST })
        assertTrue(modelRefused.any { it.site == ScanSite.HIP })
    }

    @Test
    fun `no part mask at all leaves the silhouette's neck where it was`() {
        // The flag distinguishes "the model says no" from "there was no model". On a device
        // where the part segmenter failed to load, the scan must behave exactly as it did
        // before the model existed rather than lose a site.
        val noModel = AutomaticScanBuilder.build(figure(80), anchors(), partMaskRead = false)

        assertTrue(noModel.any { it.site == ScanSite.NECK })
    }

    @Test
    fun `the neck arrives on the same ruler as the waist it is subtracted from`() {
        // **Why a ratio carries over and not a fraction.** The neck is measured on the part
        // mask and the waist on the silhouette, and the Navy equation subtracts one from the
        // other. Two masks are two coordinate spaces — resolution, aspect, letterboxing — and
        // nothing downstream can detect a mismatch, because both numbers are individually
        // plausible.
        //
        // Here the part mask is half the silhouette's scale: it saw the waist at 0.075 where
        // the silhouette sees 0.15. The neck it read as 0.036 is therefore 0.072 in the
        // silhouette's space, not 0.036 — and 0.036 would have been a neck half the size it
        // should be, silently.
        val markers = AutomaticScanBuilder.build(
            figure(80),
            anchors(),
            neck = NeckReading(
                heightFraction = 0.125,
                widthFraction = 0.036,
                bandCoverage = 1.0,
                faceWidthFraction = 0.04,
                waistWidthFraction = 0.075,
            ),
        )

        val neck = markers.first { it.site == ScanSite.NECK }
        val waist = markers.first { it.site == ScanSite.WAIST }

        assertEquals(0.15, waist.slice.frontWidthFraction, 1e-9)
        // 0.15 * (0.036 / 0.075)
        assertEquals(0.072, neck.slice.frontWidthFraction, 1e-9)
        // And the ratio the model saw is exactly preserved.
        assertEquals(
            0.036 / 0.075,
            neck.slice.frontWidthFraction / waist.slice.frontWidthFraction,
            1e-9,
        )
    }

    @Test
    fun `two masks that already agree leave the neck untouched`() {
        // The calibration must be a no-op when there is nothing to calibrate, which is the
        // ordinary case. A correction that fires when both masks agree would be a new source
        // of error rather than a fix for one.
        val markers = AutomaticScanBuilder.build(
            figure(80),
            anchors(),
            neck = NeckReading(
                heightFraction = 0.125,
                widthFraction = 0.045,
                bandCoverage = 1.0,
                faceWidthFraction = 0.05,
                waistWidthFraction = 0.15,
            ),
        )

        assertEquals(
            0.045,
            markers.first { it.site == ScanSite.NECK }.slice.frontWidthFraction,
            1e-9,
        )
    }

    @Test
    fun `without a waist from the model the neck is used as it came`() {
        // Previous behaviour, kept: no waist to calibrate against is not a reason to refuse a
        // neck the model did find.
        val markers = AutomaticScanBuilder.build(
            figure(80),
            anchors(),
            neck = NeckReading(
                heightFraction = 0.125,
                widthFraction = 0.045,
                bandCoverage = 1.0,
                faceWidthFraction = 0.05,
                waistWidthFraction = null,
            ),
        )

        assertEquals(
            0.045,
            markers.first { it.site == ScanSite.NECK }.slice.frontWidthFraction,
            1e-9,
        )
    }

    @Test
    fun `a position measured on another mask lands in this profile's rows`() {
        // The part segmenter emits 256 square whatever the photograph was, so its rows are
        // not this profile's rows. Fractions are what cross that boundary; treating one
        // mask's row index as another's would index the wrong band outright.
        val profile = figure(80)

        assertEquals(25, profile.rowAt(0.125))
        assertEquals(0, profile.rowAt(-1.0), "a fraction off the top clamps into the profile")
        assertEquals(199, profile.rowAt(2.0), "and off the bottom")
    }
}
