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

        return WidthProfile(widths, topRow = 0, bottomRow = 199)
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
            WidthProfile(widths, topRow = 0, bottomRow = 199),
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
        val widths = humanFigure().widths.copyOf()
        widths[65] = 0.0

        val sites = AnatomicalLevelFinder.detectSites(
            WidthProfile(widths, topRow = 0, bottomRow = 199),
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
        val profile = WidthProfile(widths, topRow = 50, bottomRow = 150)

        // Asking above the body must not return an empty row from the padding.
        val found = AnatomicalLevelFinder.widestBetween(profile, 0, 100)
        assertNotNull(found)
        assertTrue(found >= 50, "search must clamp to the body, got $found")
    }

    @Test
    fun `body height fraction reflects how much of the frame is filled`() {
        val widths = DoubleArray(200) { 0.2 }
        assertEquals(0.5, WidthProfile(widths, 50, 150).bodyHeightFraction, 1e-9)
        assertEquals(0.9, WidthProfile(widths, 10, 190).bodyHeightFraction, 1e-9)
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
        val a = WidthProfile(DoubleArray(10) { 0.2 }, 1, 8)
        val b = WidthProfile(DoubleArray(10) { 0.2 }, 1, 8)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
