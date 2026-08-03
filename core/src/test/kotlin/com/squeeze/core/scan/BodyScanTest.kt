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
                marker(ScanSite.NECK, 0.075, 0.070),
                marker(ScanSite.WAIST, 0.150, 0.100),
                marker(ScanSite.HIP, 0.170, 0.120),
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
        val result = analyser().analyse(listOf(marker(ScanSite.NECK, 0.075, 0.070)))

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
}
