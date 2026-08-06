package com.squeeze.core.scan

import kotlin.math.abs

/**
 * How much structure is visible on the abdomen, and how much to trust the reading.
 *
 * @param score normalised local contrast, higher meaning more visible definition
 * @param usable false when the crop was too dark, too flat or too small to say anything.
 *   Absent rather than zero: no definition and no information look identical in a number
 *   and mean opposite things.
 */
data class DefinitionReading(
    val score: Double,
    val usable: Boolean,
)

/**
 * Reads abdominal definition from a close-up of the midsection.
 *
 * **Why this exists.** [SilhouetteBodyFat] measures the body's outline, and the outline
 * stops changing below about fifteen per cent — measured, not assumed: 0.586 at eight per
 * cent, 0.592 at twelve, 0.580 at fifteen. What separates those bodies is not their shape,
 * it is whether the muscle underneath is visible through the skin, and an outline discards
 * that by construction. It knows where the body ends and nothing about the surface inside.
 *
 * Definition is that surface. Visible abdominal separation produces strong local contrast at
 * the scale of a few centimetres; a smooth stomach produces almost none.
 *
 * **What it cannot do, stated plainly because an earlier attempt failed here.** Measured
 * against labelled reference photographs, raw texture did not order the bands: the 15% and
 * 25% figures read as *more* defined than the 8% one, because their photographs were lit
 * harder. Across different people in different rooms, this measures the lighting.
 *
 * **What it can do.** Across one person photographing the same abdomen in the same place, a
 * change in this score is a change in them. That is the distinction the whole app is built
 * on — accuracy is a systematic offset that cancels in self-comparison, precision is what
 * carries a trend — and it is why this is used to place a body *within* the plateau rather
 * than to produce a percentage of its own.
 *
 * Operating on a plain luminance grid rather than a platform bitmap keeps this in `:core`,
 * where it is testable without a device.
 */
object AbdominalDefinition {

    /**
     * Downsampling factor applied before measuring.
     *
     * Definition lives at the scale of a few centimetres. At native resolution the gradient
     * is dominated by sensor noise, skin grain and body hair, none of which is adiposity, so
     * measuring there returns the camera's characteristics rather than the user's.
     */
    const val SAMPLE_STEP = 3

    /** Below this mean luminance the crop is too dark for contrast to mean anything. */
    private const val MIN_MEAN_LUMINANCE = 25.0

    /** Fewer samples than this and the score is noise. */
    private const val MIN_SAMPLES = 64

    /**
     * @param luminance row-major luminance values, 0..255
     * @param width row length in samples
     * @return the reading, always non-null so the caller can distinguish "no definition"
     *   from "could not tell" via [DefinitionReading.usable]
     */
    fun measure(luminance: IntArray, width: Int): DefinitionReading {
        if (width <= 1 || luminance.size < width * 2) return DefinitionReading(0.0, false)
        val height = luminance.size / width

        var sum = 0.0
        var count = 0
        var gradient = 0.0
        var pairs = 0

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val here = luminance[y * width + x]
                sum += here
                count++

                // Horizontal and vertical neighbours a sample-step away. Abdominal
                // separation runs both ways — the linea alba vertically, the tendinous
                // intersections horizontally — so measuring one axis alone would read a
                // flexed midline as definition and miss the rest.
                if (x + SAMPLE_STEP < width) {
                    gradient += abs(here - luminance[y * width + x + SAMPLE_STEP])
                    pairs++
                }
                if (y + SAMPLE_STEP < height) {
                    gradient += abs(here - luminance[(y + SAMPLE_STEP) * width + x])
                    pairs++
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        if (count < MIN_SAMPLES || pairs == 0) return DefinitionReading(0.0, false)

        val mean = sum / count
        if (mean < MIN_MEAN_LUMINANCE) return DefinitionReading(0.0, false)

        // Divided by mean brightness so a well-lit photograph does not score higher than a
        // dim one for that reason alone. This corrects exposure, not lighting *direction* —
        // a hard side light still exaggerates every ridge, which is why the copy asks the
        // user to keep their lighting consistent rather than merely adequate.
        return DefinitionReading(score = gradient / pairs / mean * 100.0, usable = true)
    }

    /**
     * Places a body inside the plateau that the outline cannot resolve.
     *
     * The outline says "lean" and stops. This decides where in that band to sit, and it is
     * the only signal available that can: a body at eight per cent and one at fifteen have
     * nearly the same silhouette and visibly different abdomens.
     *
     * Deliberately coarse — three positions, not a continuous mapping. Across people the
     * absolute score is contaminated by lighting, so claiming more resolution than "clearly
     * defined / somewhat / smooth" would be inventing precision that a later scan under a
     * different lamp would contradict.
     *
     * @param leanEnd the lowest body fat the plateau covers
     * @param plateauEnd the highest
     * @return a percentage inside that range, or null when the reading was unusable, in
     *   which case the caller should keep the plateau's own midpoint and wide interval
     */
    fun placeWithinPlateau(
        reading: DefinitionReading,
        leanEnd: Double,
        plateauEnd: Double,
    ): Double? {
        if (!reading.usable) return null
        if (plateauEnd <= leanEnd) return null

        val fraction = when {
            reading.score >= CLEARLY_DEFINED -> 0.0
            reading.score >= SOMEWHAT_DEFINED -> 0.5
            else -> 1.0
        }
        return leanEnd + fraction * (plateauEnd - leanEnd)
    }

    /**
     * Thresholds on the normalised contrast score.
     *
     * Set from the range the metric produces on real midsections rather than fitted to a
     * dataset, and spaced widely because the between-person spread is large. They separate
     * an abdomen with visible separation from a flat one; they do not pretend to separate
     * eight per cent from ten.
     */
    const val CLEARLY_DEFINED = 6.5
    const val SOMEWHAT_DEFINED = 4.5
}
