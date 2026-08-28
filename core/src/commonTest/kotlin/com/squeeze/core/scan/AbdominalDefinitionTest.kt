package com.squeeze.core.scan

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What separates abdominal structure from the light that fell on it.
 *
 * The previous metric divided the mean gradient by mean *brightness*, which corrects how
 * bright a photograph is and not how hard the light was. It failed on exactly the case it
 * existed for: a man's overexposed photograph with visible abdominal separation scored 2.08
 * while his own soft belly scored 4.83, because the soft one had harder light.
 *
 * The fix is to divide by local *contrast* instead. [hardSideLight] below is the test that
 * captures it — a smooth abdomen under a raking light has a large gradient everywhere and no
 * structure anywhere, and it is what the old score was really measuring.
 */
class AbdominalDefinitionTest {

    private val width = 60
    private val height = 60

    /** A smooth abdomen under flat light: even tone, no structure. */
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

    /**
     * A smooth abdomen lit hard from one side: a strong, smooth ramp across the crop.
     *
     * There is no anatomy in this image at all — it is one linear gradient — and it is the
     * shape of every photograph that broke the old metric.
     */
    private fun hardSideLight(): IntArray {
        val out = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                out[y * width + x] = 60 + (x * 160 / width)
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
    fun `a hard side light is not mistaken for definition`() {
        // The regression the whole rewrite is for. This crop contains one smooth ramp and no
        // structure whatsoever; under the old brightness-normalised score its constant
        // gradient read as strong definition.
        val lit = AbdominalDefinition.measure(hardSideLight(), width)
        val ridged = AbdominalDefinition.measure(defined(), width)

        assertTrue(lit.usable)
        // Measured: 16.2 for real structure against 6.6 for the ramp. Without the noise
        // floor in the normalisation the ramp scored 62.9 — four times the real abdomen —
        // because dividing a flat region's quantisation by its own quantisation returns
        // full-scale garbage.
        assertTrue(
            ridged.score > lit.score * 2.0,
            "a smooth ramp scored ${lit.score} against real structure at ${ridged.score}",
        )
    }

    @Test
    fun `contrast is normalised away, not just exposure`() {
        // The property that makes this a measure of structure rather than of lighting: the
        // same abdomen under a softer lamp has the same *pattern* at lower amplitude and
        // must read nearly the same. The old metric could only manage this when the contrast
        // happened to scale with the brightness; here neither has to.
        //
        // Nearly rather than exactly, and on purpose: NOISE_FLOOR deliberately suppresses
        // very low contrast, so a 4.6x softer pattern reads a few per cent lower. Perfect
        // invariance here would mean the floor was not doing its job.
        val soft = AbdominalDefinition.measure(defined(level = 140, contrast = 12), width)
        val harsh = AbdominalDefinition.measure(defined(level = 140, contrast = 55), width)

        assertTrue(soft.usable && harsh.usable)
        assertTrue(
            abs(soft.score - harsh.score) < harsh.score * 0.15,
            "same structure, 4.6x contrast: ${soft.score} vs ${harsh.score}",
        )
    }

    @Test
    fun `exposure is normalised away`() {
        val dim = AbdominalDefinition.measure(defined(level = 90, contrast = 26), width)
        val bright = AbdominalDefinition.measure(defined(level = 180, contrast = 52), width)

        assertTrue(dim.usable && bright.usable)
        assertTrue(
            abs(dim.score - bright.score) < 1.0,
            "same abdomen, different exposure: ${dim.score} vs ${bright.score}",
        )
    }

    @Test
    fun `the same abdomen at a different resolution reads the same`() {
        // Comparability across scans, which is the only thing this score is for. The metric
        // is built from differences between neighbouring pixels, so without the resample a
        // new phone with a bigger sensor would read as lost definition.
        val small = AbdominalDefinition.measure(defined(), width)

        val scale = 4
        val big = IntArray(width * scale * height * scale)
        val source = defined()
        for (y in 0 until height * scale) {
            for (x in 0 until width * scale) {
                big[y * width * scale + x] = source[(y / scale) * width + (x / scale)]
            }
        }
        val large = AbdominalDefinition.measure(big, width * scale)

        assertTrue(small.usable && large.usable)
        assertTrue(
            abs(small.score - large.score) < 2.0,
            "same abdomen, 4x resolution: ${small.score} vs ${large.score}",
        )
    }

    @Test
    fun `a dark crop is refused rather than scored zero`() {
        // No definition and no information look identical in a number and mean the opposite.
        assertTrue(!AbdominalDefinition.measure(flat(level = 8), width).usable)
    }

    @Test
    fun `a crop too small to mean anything is refused`() {
        assertTrue(!AbdominalDefinition.measure(IntArray(12) { 140 }, 4).usable)
        assertTrue(!AbdominalDefinition.measure(IntArray(0), 0).usable)
    }
}
