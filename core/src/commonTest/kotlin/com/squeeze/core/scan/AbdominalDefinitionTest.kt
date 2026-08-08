package com.squeeze.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AbdominalDefinitionTest {

    private val width = 60
    private val height = 60

    /** A smooth abdomen: even tone, no structure. */
    private fun flat(level: Int = 140) = IntArray(width * height) { level }

    /**
     * A defined abdomen: alternating bands at the scale muscle separation actually has,
     * running both ways, so the metric sees ridges rather than a single midline.
     */
    private fun defined(level: Int = 140, contrast: Int = 40): IntArray {
        val out = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val band = ((y / 9) + (x / 12)) % 2
                out[y * width + x] = level + if (band == 0) contrast else -contrast
            }
        }
        return out
    }

    @Test
    fun `a defined abdomen scores well above a smooth one`() {
        val smooth = AbdominalDefinition.measure(flat(), width)
        val ridged = AbdominalDefinition.measure(defined(), width)

        assertTrue(smooth.usable && ridged.usable)
        assertTrue(
            ridged.score > smooth.score + 3.0,
            "smooth ${smooth.score}, defined ${ridged.score}",
        )
    }

    @Test
    fun `exposure is normalised away`() {
        // The same abdomen photographed brighter must not read as more defined. This is the
        // half of the lighting problem that can actually be corrected; direction cannot be,
        // which is why the copy asks for consistency rather than merely enough light.
        val dim = AbdominalDefinition.measure(defined(level = 90, contrast = 26), width)
        val bright = AbdominalDefinition.measure(defined(level = 180, contrast = 52), width)

        assertTrue(dim.usable && bright.usable)
        assertTrue(
            kotlin.math.abs(dim.score - bright.score) < 1.0,
            "same contrast ratio, different exposure: ${dim.score} vs ${bright.score}",
        )
    }

    @Test
    fun `a dark crop is refused rather than scored zero`() {
        // No definition and no information look identical in a number and mean the opposite.
        val reading = AbdominalDefinition.measure(flat(level = 8), width)

        assertTrue(!reading.usable)
    }

    @Test
    fun `a crop too small to mean anything is refused`() {
        assertTrue(!AbdominalDefinition.measure(IntArray(12) { 140 }, 4).usable)
        assertTrue(!AbdominalDefinition.measure(IntArray(0), 0).usable)
    }

    @Test
    fun `clear definition places the body at the lean end of the plateau`() {
        val ridged = AbdominalDefinition.measure(defined(contrast = 60), width)

        val placed = AbdominalDefinition.placeWithinPlateau(ridged, leanEnd = 8.0, plateauEnd = 15.0)

        assertNotNull(placed)
        assertTrue(placed < 10.0, "visible separation is the lean end, got $placed")
    }

    @Test
    fun `a smooth abdomen places at the top of the plateau`() {
        val smooth = AbdominalDefinition.measure(flat(), width)

        val placed = AbdominalDefinition.placeWithinPlateau(smooth, leanEnd = 8.0, plateauEnd = 15.0)

        assertNotNull(placed)
        assertEquals(15.0, placed, 0.01)
    }

    @Test
    fun `an unusable reading places nothing`() {
        val dark = AbdominalDefinition.measure(flat(level = 5), width)

        assertNull(AbdominalDefinition.placeWithinPlateau(dark, 8.0, 15.0))
    }

    @Test
    fun `a degenerate plateau is refused rather than inverted`() {
        val ridged = AbdominalDefinition.measure(defined(), width)

        assertNull(AbdominalDefinition.placeWithinPlateau(ridged, leanEnd = 15.0, plateauEnd = 8.0))
    }
}
