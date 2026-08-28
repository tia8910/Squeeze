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
 * **The first version of this measured the lighting, and the numbers are worth keeping.**
 * It normalised the mean gradient by mean *brightness*, which corrects how bright a
 * photograph is and not how hard the light was. Against labelled references it ran 5.76 at
 * eight per cent, 6.15 at ten, 7.44 at fifteen, 6.03 at twenty, 4.51 at thirty-five — not
 * weakly ordered, not ordered at all. On three real user photographs it scored a soft belly
 * at 4.83 and the same man with visible abdominal separation at **2.08**: his most defined
 * body came last of three, because that photograph was overexposed.
 *
 * **The fix is to normalise by local contrast rather than by brightness.** Subtract a local
 * mean, divide by a local standard deviation, and what survives is the *structure* — the
 * grooves — with the illumination divided out. On the same three photographs that turns
 * 2.08/5.69/4.83 into 20.07/14.11/13.03: the man's defined photograph now beats his own soft
 * one by 54%.
 *
 * **What it still cannot do, and this is measured too.** Across *different people* it barely
 * separates: the model's visibly sharper abdomen scored 14.11 against 13.03 for the other
 * man's soft one, an eight per cent margin that is noise. Skin tone, body hair, camera and
 * proportions all differ between people and none of them differ between one person's Tuesday
 * and their Friday.
 *
 * So this is a **within-person** instrument. A change in this score across one user's own
 * scans is a change in that user. The absolute value is not comparable to anybody else's and
 * must never be mapped to a percentage without an anchor from the person themselves. That is
 * the same distinction the whole app rests on: accuracy is a systematic offset that cancels
 * in self-comparison, precision is what carries a trend.
 *
 * Operating on a plain luminance grid rather than a platform bitmap keeps this in `:core`,
 * where it is testable without a device.
 */
object AbdominalDefinition {

    /**
     * Width every crop is resampled to before measuring.
     *
     * Load-bearing for comparability rather than for speed. The score is built from
     * differences between neighbouring pixels, so at higher resolution those neighbours are
     * physically closer together and every difference shrinks — the same abdomen photographed
     * at 12 megapixels would score lower than at 5, and a user changing phones would read as
     * having lost definition. Resampling to a fixed width puts every scan at the same
     * pixels-per-centimetre so the numbers can be compared at all.
     *
     * Also does what the old subsampling step did: definition lives at the scale of
     * centimetres, and
     * at native resolution the gradient is dominated by sensor noise, skin grain and body
     * hair, none of which is adiposity.
     */
    const val MEASURE_WIDTH = 128

    /**
     * Radius of the neighbourhood the local mean and contrast are taken over, as a fraction
     * of [MEASURE_WIDTH].
     *
     * It has to sit between the two scales in the picture. Smaller than the grooves and the
     * normalisation removes the grooves along with the lighting; larger than the abdomen and
     * it stops being local and becomes the global mean this replaced. A twelfth of the crop
     * is roughly two centimetres on an adult, which is under the spacing of the tendinous
     * intersections and far over the width of a skin pore.
     */
    private const val NEIGHBOURHOOD = 12

    /** Below this mean luminance the crop is too dark for contrast to mean anything. */
    private const val MIN_MEAN_LUMINANCE = 25.0

    /** Fewer samples than this and the score is noise. */
    private const val MIN_SAMPLES = 64

    /**
     * Smallest local contrast, in grey levels, that counts as structure rather than sensor.
     *
     * Dividing by the local spread is what removes the lighting, and taken literally it also
     * divides noise by noise: in a genuinely flat region the numerator and denominator are
     * both quantisation, and the quotient is full-scale garbage. A first version had no floor
     * and scored a smooth ramp — one linear gradient, no anatomy at all — at twice a truly
     * defined abdomen.
     *
     * Three levels is under JPEG noise in a smooth patch and far under real abdominal shading,
     * which runs to tens of levels. Regions below it stay flat; regions above it normalise.
     */
    private const val NOISE_FLOOR = 3.0

    /**
     * Scale factor, chosen so ordinary readings land in the tens rather than near zero.
     *
     * Cosmetic and deliberately arbitrary: this score has no units and its absolute value
     * means nothing on its own, so the only requirement is that a person comparing two of
     * their own scans sees a difference they can read.
     */
    private const val DISPLAY_SCALE = 50.0

    /**
     * @param luminance row-major luminance values, 0..255
     * @param width row length in samples
     * @return the reading, always non-null so the caller can distinguish "no definition"
     *   from "could not tell" via [DefinitionReading.usable]
     */
    fun measure(luminance: IntArray, width: Int): DefinitionReading {
        if (width <= 1 || luminance.size < width * 2) return DefinitionReading(0.0, false)
        if (luminance.size < MIN_SAMPLES) return DefinitionReading(0.0, false)

        val height = luminance.size / width
        if (luminance.take(width * height).average() < MIN_MEAN_LUMINANCE) {
            return DefinitionReading(0.0, false)
        }

        val (grid, w, h) = resample(luminance, width, height)
        if (w < 4 || h < 4) return DefinitionReading(0.0, false)

        val radius = maxOf(2, w / NEIGHBOURHOOD)

        // Local contrast normalisation. `high` is what is left after the local illumination
        // is subtracted; dividing it by the local spread rescales every part of the crop to
        // the same contrast, so a shadowed flank and a lit one contribute equally and a hard
        // lamp stops inflating the whole reading.
        val localMean = boxBlur(grid, w, h, radius)
        val high = DoubleArray(grid.size) { grid[it] - localMean[it] }
        val localVariance = boxBlur(DoubleArray(high.size) { high[it] * high[it] }, w, h, radius)
        val normalised = DoubleArray(high.size) {
            high[it] / kotlin.math.sqrt(localVariance[it] + NOISE_FLOOR * NOISE_FLOOR)
        }

        // Both axes, because abdominal separation runs both ways: the linea alba down the
        // middle and the tendinous intersections across it. One axis alone would read a
        // flexed midline as definition and miss everything else.
        var vertical = 0.0
        var verticalPairs = 0
        var horizontal = 0.0
        var horizontalPairs = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (x + 1 < w) {
                    horizontal += abs(normalised[y * w + x] - normalised[y * w + x + 1])
                    horizontalPairs++
                }
                if (y + 1 < h) {
                    vertical += abs(normalised[y * w + x] - normalised[(y + 1) * w + x])
                    verticalPairs++
                }
            }
        }
        if (verticalPairs == 0 || horizontalPairs == 0) return DefinitionReading(0.0, false)

        val score = (vertical / verticalPairs + horizontal / horizontalPairs) * DISPLAY_SCALE
        return DefinitionReading(score = score, usable = true)
    }

    /**
     * Area-averaged resample to exactly [MEASURE_WIDTH], upward as well as downward.
     *
     * Both directions, and that is the fix rather than an extra. Resampling only when the
     * crop was too large left small crops measured at their own scale, so the band spacing
     * relative to the pixel grid still depended on how close the phone was — the same
     * abdomen at four times the resolution read 16.2 against 30.4. Sending every crop to one
     * width makes the feature size relative to the grid identical whatever the camera did.
     */
    private fun resample(
        luminance: IntArray,
        width: Int,
        height: Int,
    ): Triple<DoubleArray, Int, Int> {
        val outWidth = MEASURE_WIDTH
        val outHeight = maxOf(4, height * MEASURE_WIDTH / width)
        val out = DoubleArray(outWidth * outHeight)
        val scaleX = width.toDouble() / outWidth
        val scaleY = height.toDouble() / outHeight

        for (y in 0 until outHeight) {
            val top = (y * scaleY).toInt()
            val bottom = minOf(height, maxOf(top + 1, kotlin.math.ceil((y + 1) * scaleY).toInt()))
            for (x in 0 until outWidth) {
                val left = (x * scaleX).toInt()
                val right = minOf(
                    width,
                    maxOf(left + 1, kotlin.math.ceil((x + 1) * scaleX).toInt()),
                )
                var sum = 0.0
                var count = 0
                for (sy in top until bottom) {
                    for (sx in left until right) {
                        sum += luminance[sy * width + sx]
                        count++
                    }
                }
                out[y * outWidth + x] = if (count > 0) sum / count else 0.0
            }
        }
        return Triple(out, outWidth, outHeight)
    }

    /**
     * Separable box mean over a square neighbourhood, with the window clamped at the edges.
     *
     * Clamped rather than zero-padded: padding with zeros would pull the local mean down
     * around the border and manufacture contrast exactly where the crop meets the flanks,
     * which is where an oblique already lives.
     */
    private fun boxBlur(source: DoubleArray, w: Int, h: Int, r: Int): DoubleArray {
        val horizontal = DoubleArray(source.size)
        for (y in 0 until h) {
            val row = y * w
            // A prefix sum rather than a slid window, so the clamped neighbourhood at the
            // edges is exact rather than approximately right.
            val prefix = DoubleArray(w + 1)
            for (x in 0 until w) prefix[x + 1] = prefix[x] + source[row + x]
            for (x in 0 until w) {
                val lo = maxOf(0, x - r)
                val hi = minOf(w - 1, x + r)
                horizontal[row + x] = (prefix[hi + 1] - prefix[lo]) / (hi - lo + 1)
            }
        }

        val out = DoubleArray(source.size)
        for (x in 0 until w) {
            val prefix = DoubleArray(h + 1)
            for (y in 0 until h) prefix[y + 1] = prefix[y] + horizontal[y * w + x]
            for (y in 0 until h) {
                val lo = maxOf(0, y - r)
                val hi = minOf(h - 1, y + r)
                out[y * w + x] = (prefix[hi + 1] - prefix[lo]) / (hi - lo + 1)
            }
        }
        return out
    }

    // **There is no score-to-percentage mapping here, and there must not be one.**
    //
    // A `placeWithinPlateau` used to sit at this point, turning the score into a figure
    // between the lean end of the plateau and its top through two thresholds. It was already
    // unwired, on the evidence that the old metric was not ordered at all. The new metric is
    // ordered — within one person — and that is still not enough to restore it, because the
    // thing such a function needs is a score that means the same on two different bodies, and
    // this one does not: a visibly sharper abdomen scored 14.11 against 13.03 for another
    // man's soft one.
    //
    // What can legitimately turn this into a percentage is an anchor from the person whose
    // abdomen it is — one reference figure, through PersonalCalibration, which is exactly the
    // machinery already built for the tape equations. Until that anchor exists the honest
    // output is the change between one user's own scans, and nothing else.
}
