package com.squeeze.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regressions for defects that reached a real device and produced visibly wrong numbers.
 *
 * A user's scan reported a 201 cm chest, and a hip and thigh identical to the tenth of a
 * centimetre. Both were structural rather than noisy, and both are pinned here.
 */
class ScanRegressionTest {

    private fun anchors() = PoseAnchors(chinRow = 18, shoulderRow = 35, hipRow = 100, kneeRow = 160)

    /**
     * A figure whose widest lower-body row sits inside the old overlapping band, which is
     * what made hip and thigh collide.
     */
    private fun lowerBodyFigure(): WidthProfile {
        val torso = DoubleArray(200)
        val leg = DoubleArray(200)

        for (row in 0..19) torso[row] = 0.10
        for (row in 20..29) torso[row] = 0.06
        for (row in 30..99) torso[row] = 0.26
        torso[80] = 0.15

        // Hips flare to a maximum at row 108, well inside the old hip band.
        for (row in 100..130) torso[row] = 0.24 + (if (row <= 108) (row - 100) * 0.005 else 0.0)
        for (row in 131..199) torso[row] = 0.18

        // A single leg is much narrower than the two-leg silhouette above it.
        for (row in 120..199) leg[row] = 0.09

        return WidthProfile(torso, leg, topRow = 0, bottomRow = 199)
    }

    @Test
    fun `hip and thigh are never the same row`() {
        // The bands used to overlap, so one widest row satisfied both searches and the two
        // sites came back byte-identical — 136.6 cm each on the device that surfaced this.
        val sites = AnatomicalLevelFinder.detectSites(lowerBodyFigure(), anchors())

        val hip = sites[ScanSite.HIP]
        val thigh = sites[ScanSite.THIGH]
        assertNotNull(hip)
        assertNotNull(thigh)
        assertNotEquals(hip, thigh, "hip and thigh must be searched in disjoint bands")
        assertTrue(thigh > hip, "thigh sits below the hip, got thigh=$thigh hip=$hip")
    }

    @Test
    fun `thigh is measured on one leg not the whole silhouette`() {
        val profile = lowerBodyFigure()
        val sites = AnatomicalLevelFinder.detectSites(profile, anchors())
        val thighRow = sites.getValue(ScanSite.THIGH)

        // The silhouette at that row spans both legs; the leg width is roughly half of it.
        // Measuring the span would report a thigh about twice its real size.
        assertTrue(
            profile.legWidthAt(thighRow) < profile.torsoWidthAt(thighRow),
            "thigh must use the single-leg width",
        )
    }

    @Test
    fun `a site with no leg data yields no thigh rather than a wrong one`() {
        // A side-on view cannot isolate a leg. Falling back to the full silhouette there is
        // what produced a thigh equal to the hip.
        val torsoOnly = WidthProfile.torsoOnly(
            DoubleArray(200) { if (it in 100..199) 0.2 else 0.15 },
            topRow = 0,
            bottomRow = 199,
        )

        val sites = AnatomicalLevelFinder.detectSites(torsoOnly, anchors())
        assertNull(sites[ScanSite.THIGH], "no leg data must mean no thigh measurement")
    }

    @Test
    fun `an impossible chest is discarded rather than stored`() {
        // The reported 201 cm chest came from measuring shoulders-and-arms as a chest
        // diameter. Whatever the cause, a number outside human limits must not reach the
        // trend: it would bend the user's history permanently and silently.
        val analyser = BodyScanAnalyser(
            scale = ScaleRecovery(heightCm = 180.0, bodyHeightFraction = 0.8),
            imageAspectRatio = 1.0,
        )

        val result = analyser.analyse(
            listOf(
                ScanMarker(
                    ScanSite.CHEST,
                    BodySlice(
                        frontWidthFraction = 0.40,
                        sideWidthFraction = 0.30,
                        frontHeightFraction = 0.3,
                        sideHeightFraction = 0.3,
                    ),
                ),
            ),
        )

        assertNull(result.circumferences.chestCm, "an impossible chest must not be stored")
        assertTrue(
            result.warnings.any { it is ScanWarning.ImplausibleMeasurement },
            "the user must be told the measurement was rejected, not just shown fewer rows",
        )
    }

    @Test
    fun `a plausible measurement passes the gate untouched`() {
        val analyser = BodyScanAnalyser(
            scale = ScaleRecovery(heightCm = 180.0, bodyHeightFraction = 0.8),
            imageAspectRatio = 1.0,
        )

        val result = analyser.analyse(
            listOf(
                ScanMarker(
                    ScanSite.WAIST,
                    BodySlice(0.15, 0.10, 0.5, 0.5),
                ),
            ),
        )

        val waist = result.circumferences.waistCm
        assertNotNull(waist)
        assertTrue(waist in 45.0..200.0)
        assertTrue(result.warnings.none { it is ScanWarning.ImplausibleMeasurement })
    }

    @Test
    fun `a chest narrower than the neck is rejected as internally inconsistent`() {
        // Each number can sit inside its own bounds while the set is impossible, which means
        // the searches landed on the wrong rows.
        val inconsistent = PlausibleRanges.inconsistentSites(
            mapOf(ScanSite.NECK to 40.0, ScanSite.CHEST to 38.0),
        )
        assertTrue(ScanSite.CHEST in inconsistent)
    }

    @Test
    fun `plausible ranges admit unusual but real bodies`() {
        // The gate exists to reject the physically impossible, not to second-guess an
        // unusual body — a bound that excludes real people is its own kind of bug.
        assertTrue(PlausibleRanges.isPlausible(ScanSite.WAIST, 62.0))
        assertTrue(PlausibleRanges.isPlausible(ScanSite.WAIST, 165.0))
        assertTrue(PlausibleRanges.isPlausible(ScanSite.NECK, 30.0))
        assertTrue(PlausibleRanges.isPlausible(ScanSite.CHEST, 145.0))

        assertEquals(false, PlausibleRanges.isPlausible(ScanSite.CHEST, 201.4))
        assertEquals(false, PlausibleRanges.isPlausible(ScanSite.NECK, 53.8 * 2))
    }
}
