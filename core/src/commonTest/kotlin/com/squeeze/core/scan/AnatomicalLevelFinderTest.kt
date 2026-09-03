package com.squeeze.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests build synthetic silhouettes with a body whose anatomy is known exactly, so the
 * finder can be checked against ground truth. A real segmentation mask cannot do this: it
 * has no labelled waist to compare against.
 */
class AnatomicalLevelFinderTest {

    /**
     * A 200-row figure with deliberate anatomy:
     *   rows   0..19   head, narrow
     *   rows  20..29   neck, narrowest point of the upper body
     *   rows  30..49   shoulders and chest, widest point of the torso
     *   rows  50..99   torso tapering to the waist at row 80
     *   rows 100..119  hips, widening to a gluteal maximum at row 110
     *   rows 120..199  legs
     */
    private fun humanFigure(): WidthProfile {
        val widths = DoubleArray(200)
        for (row in 0..19) widths[row] = 0.10
        for (row in 20..29) widths[row] = 0.06
        for (row in 30..49) widths[row] = 0.26
        for (row in 50..99) {
            // Taper from the chest down to the narrowest point at row 80, then flare out.
            widths[row] = if (row <= 80) 0.26 - (row - 50) * 0.002 else 0.20 + (row - 80) * 0.002
        }
        for (row in 100..119) {
            widths[row] = if (row <= 110) 0.24 + (row - 100) * 0.004 else 0.28 - (row - 110) * 0.004
        }
        for (row in 120..199) widths[row] = 0.16

        return WidthProfile.torsoOnly(widths, topRow = 0, bottomRow = 199)
    }

    private fun anchors() = PoseAnchors(
        chinRow = 18,
        shoulderRow = 35,
        hipRow = 100,
        kneeRow = 160,
    )

    @Test
    fun `neck is found at the narrowest point between chin and shoulders`() {
        val sites = AnatomicalLevelFinder.detectSites(humanFigure(), anchors())

        val neck = sites[ScanSite.NECK]
        assertNotNull(neck)
        assertTrue(neck in 20..29, "expected neck in the narrow band, got row $neck")
    }

    @Test
    fun `waist is found at the true narrowest point rather than a fixed proportion`() {
        val sites = AnatomicalLevelFinder.detectSites(humanFigure(), anchors())

        val waist = sites[ScanSite.WAIST]
        assertNotNull(waist)
        assertEquals(80, waist, "the constructed figure's narrowest torso row is 80")
    }

    @Test
    fun `waist tracks the individual rather than assuming average proportions`() {
        // A long-torsoed figure whose narrowest point sits much lower relative to the hips.
        // A fixed-fraction approach would land on the ribs here; the search must not.
        val widths = DoubleArray(200)
        for (row in 0..19) widths[row] = 0.10
        for (row in 20..29) widths[row] = 0.06
        for (row in 30..94) widths[row] = 0.26
        for (row in 95..99) widths[row] = 0.18
        for (row in 100..199) widths[row] = 0.24

        val sites = AnatomicalLevelFinder.detectSites(
            WidthProfile.torsoOnly(widths, topRow = 0, bottomRow = 199),
            anchors(),
        )

        val waist = sites[ScanSite.WAIST]
        assertNotNull(waist)
        assertTrue(waist in 95..99, "waist should follow the silhouette, got row $waist")
    }

    @Test
    fun `hip is the widest point at or below the hip joint`() {
        val sites = AnatomicalLevelFinder.detectSites(humanFigure(), anchors())

        val hip = sites[ScanSite.HIP]
        assertNotNull(hip)
        assertEquals(110, hip, "gluteal maximum was constructed at row 110")
    }

    @Test
    fun `chest is the widest point above the waist`() {
        val sites = AnatomicalLevelFinder.detectSites(humanFigure(), anchors())

        val chest = sites[ScanSite.CHEST]
        assertNotNull(chest)
        assertTrue(chest in 35..49, "expected chest in the shoulder band, got row $chest")
    }

    @Test
    fun `a hole in the mask never wins the narrowest search`() {
        // Segmentation failures leave zero-width rows. Treating one as an infinitely narrow
        // waist would produce a confident, absurd measurement.
        val widths = humanFigure().torsoWidths.copyOf()
        widths[65] = 0.0

        val sites = AnatomicalLevelFinder.detectSites(
            WidthProfile.torsoOnly(widths, topRow = 0, bottomRow = 199),
            anchors(),
        )

        assertEquals(80, sites[ScanSite.WAIST], "a mask hole must not be read as the waist")
    }

    @Test
    fun `a degenerate search band yields no site rather than a guess`() {
        val profile = humanFigure()

        assertNull(AnatomicalLevelFinder.narrowestBetween(profile, 100, 50))
        assertNull(AnatomicalLevelFinder.widestBetween(profile, 100, 50))
    }

    @Test
    fun `search is clamped to the body rather than the frame`() {
        val widths = DoubleArray(200)
        for (row in 50..150) widths[row] = 0.20
        val profile = WidthProfile.torsoOnly(widths, topRow = 50, bottomRow = 150)

        // Asking above the body must not return an empty row from the padding.
        val found = AnatomicalLevelFinder.widestBetween(profile, 0, 100)
        assertNotNull(found)
        assertTrue(found >= 50, "search must clamp to the body, got $found")
    }

    @Test
    fun `body height fraction reflects how much of the frame is filled`() {
        val widths = DoubleArray(200) { 0.2 }
        assertEquals(0.5, WidthProfile.torsoOnly(widths, 50, 150).bodyHeightFraction, 1e-9)
        assertEquals(0.9, WidthProfile.torsoOnly(widths, 10, 190).bodyHeightFraction, 1e-9)
    }

    @Test
    fun `anchors must be anatomically ordered`() {
        // Catches a pose model returning landmarks for an upside-down or mis-detected body,
        // which would otherwise search nonsensical bands and return plausible-looking rows.
        assertTrue(
            runCatching { PoseAnchors(chinRow = 100, shoulderRow = 35, hipRow = 90, kneeRow = 160) }
                .isFailure,
        )
        assertTrue(
            runCatching { PoseAnchors(chinRow = 18, shoulderRow = 120, hipRow = 100, kneeRow = 160) }
                .isFailure,
        )
    }

    @Test
    fun `profiles with equal content compare equal`() {
        // DoubleArray defaults to identity comparison in a data class, which would silently
        // break any caching or equality check on a profile.
        val a = WidthProfile.torsoOnly(DoubleArray(10) { 0.2 }, 1, 8)
        val b = WidthProfile.torsoOnly(DoubleArray(10) { 0.2 }, 1, 8)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    /**
     * A figure with arm and leg data, to test ARM and CALF detection.
     *
     *   rows   0..19   head
     *   rows  20..29   neck, narrowest
     *   rows  30..49   shoulders + chest (torso 0.26, arm 0.08)
     *   rows  50..99   torso tapering (arm present at 0.07)
     *   rows 100..119  hips (torso 0.24)
     *   rows 120..159  thighs (leg 0.09)
     *   rows 160..199  calves (leg 0.06)
     */
    private fun figureWithLimbs(): WidthProfile {
        val torso = DoubleArray(200)
        val leg = DoubleArray(200)
        for (row in 0..19) torso[row] = 0.10
        for (row in 20..29) torso[row] = 0.06
        for (row in 30..49) { torso[row] = 0.26; leg[row] = 0.08 }
        for (row in 50..99) {
            torso[row] = if (row <= 80) 0.26 - (row - 50) * 0.002
                else 0.20 + (row - 80) * 0.002
            leg[row] = 0.07
        }
        for (row in 100..119) torso[row] = 0.24
        for (row in 120..159) leg[row] = 0.09
        for (row in 160..199) leg[row] = 0.06
        return WidthProfile(torso, leg, topRow = 0, bottomRow = 199)
    }

    @Test
    fun `arm is detected at the widest non-torso run in the upper arm region`() {
        val sites = AnatomicalLevelFinder.detectSites(figureWithLimbs(), anchors())

        val arm = sites[ScanSite.ARM]
        assertNotNull(arm, "arm should be detected when leg data is present")
        assertTrue(arm in 36..62, "arm should be in the upper arm band, got row $arm")
        // The arm run width at that row must be less than the torso width.
        assertTrue(
            figureWithLimbs().legWidthAt(arm) < figureWithLimbs().torsoWidthAt(arm),
            "arm must be narrower than the torso",
        )
    }

    @Test
    fun `calf is detected at the widest single-leg row below the knee`() {
        val sites = AnatomicalLevelFinder.detectSites(figureWithLimbs(), anchors())

        val calf = sites[ScanSite.CALF]
        assertNotNull(calf, "calf should be detected when leg data is present")
        assertTrue(calf > 160, "calf should be below the knee, got row $calf")
    }

    @Test
    fun `arm is not detected when leg data is all zeros`() {
        val sites = AnatomicalLevelFinder.detectSites(humanFigure(), anchors())
        assertNull(sites[ScanSite.ARM], "no arm without leg data")
    }

    @Test
    fun `hipsInFrame skips hip and thigh detection`() {
        val sites = AnatomicalLevelFinder.detectSites(
            humanFigure(), anchors(), hipsInFrame = false,
        )

        assertNull(sites[ScanSite.HIP], "hip must not be detected when out of frame")
        assertNull(sites[ScanSite.THIGH], "thigh must not be detected when hips are out")
        // Neck, waist, chest, arm, calf are unaffected.
        assertNotNull(sites[ScanSite.NECK])
        assertNotNull(sites[ScanSite.WAIST])
        assertNotNull(sites[ScanSite.CHEST])
    }
}
