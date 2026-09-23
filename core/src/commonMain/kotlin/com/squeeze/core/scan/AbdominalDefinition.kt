package com.squeeze.core.scan

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * How much structure is visible on the abdomen, and how much to trust the reading.
 *
 * @param score structure at the scale of abdominal separation, relative to the light falling
 *   across the crop. Zero means no structure — see [AbdominalDefinition], where the previous
 *   version of this number had no zero and that was the whole problem.
 * @param usable false when the crop was too dark, too small or too evenly lit to say
 *   anything. Absent rather than zero: no definition and no information look identical in a
 *   number and mean opposite things.
 */
data class DefinitionReading(
    val score: Double,
    val usable: Boolean,
)

/**
 * Reads abdominal definition from a close-up of the midsection.
 *
 * **Why this exists.** [SilhouetteBodyFat] measures the body's outline, and the outline stops
 * changing below about fifteen per cent — measured, not assumed: 0.586 at eight per cent,
 * 0.592 at twelve, 0.580 at fifteen. What separates those bodies is not their shape, it is
 * whether the muscle underneath is visible through the skin, and an outline discards that by
 * construction. It knows where the body ends and nothing about the surface inside.
 *
 * **The version before this one measured image noise, and shipped.** It normalised each pixel
 * by its local contrast — subtract a local mean, divide by a local standard deviation — which
 * is a sound way to remove illumination and an unsound way to end, because in a region with no
 * structure the numerator and the denominator are both sensor noise and the quotient is
 * full-scale. A constant floor of three grey levels was added to the denominator to stop that,
 * and it did not, because a floor bounds a division without ever subtracting a baseline. The
 * consequence, measured on a featureless patch:
 *
 * | noise (grey levels) | 160 px crop | 320 px | 640 px |
 * |---|---|---|---|
 * | 0.5 | 8.1 | 6.2 | 4.0 |
 * | 2.0 | 26.0 | 20.2 | 13.1 |
 * | 5.0 | 53.5 | 45.3 | 31.3 |
 * | 8.0 | 67.3 | 63.1 | 47.2 |
 *
 * Against a threshold of twenty, *nothing at all* read as a defined abdomen at ordinary phone
 * noise levels. On a real scan photograph a patch of blank painted wall scored **21.3**. The
 * same photograph's abdomen — smooth, a navel and no separation anywhere in the crop — scored
 * **37.9**, the highest reading this project has ever recorded, and the app told its user the
 * figure was read from his photo.
 *
 * Worse than a wrong threshold: the ordering was inverted. Real corrugated structure at a
 * thousand pixels and low noise scored 9.8, while a blank field at 160 pixels and two levels
 * of noise scored 26.0. The score was a reading of the *camera*, so the three photographs its
 * thresholds came from — 32.5, 21.9, 16.5 — were ordered by their resolution and their grain
 * rather than by the bodies in them.
 *
 * **What replaces it.** Three changes, each of which is separately checkable:
 *
 * 1. **A band-pass instead of a per-pixel normalisation.** Structure at the scale of abdominal
 *    separation is what is wanted, so take exactly that band — [GROOVE_RADIUS] to
 *    [SHADING_RADIUS] — and let the illumination fall out as the low end of it. No division by
 *    a local spread, so no dividing noise by noise.
 * 2. **The noise is estimated and subtracted.** [noiseLevel] measures the crop's own sensor
 *    noise, the band-pass attenuates it by a known factor, and that much variance comes off in
 *    quadrature. What is left is zero when the input is featureless, at every noise level and
 *    every crop size — which is the property the old metric never had and could not be given.
 * 3. **A median rather than a mean.** Abdominal separation covers the abdomen; a navel is one
 *    small blob, and on the photograph above it accounted for the entire reading (5.75 grey
 *    levels of structure over the whole crop, 1.67 with the midline excluded). A median is
 *    unmoved by a feature occupying a few per cent of the area. A synthetic navel on an
 *    otherwise flat field scores **1.1** here and scored **19.8** before.
 *
 * Measured on the same synthetics, this version reads 16.8–18.5 on real structure across every
 * combination of size and noise, and never above 6.5 on anything else.
 *
 * **What it still cannot do, and this has not changed.** The score has no meaning across
 * *people*. Skin tone, body hair, camera and proportions all differ between two people and
 * none of them differ between one person's Tuesday and their Friday. So this is a
 * **within-person** instrument: a change in this score across one user's own scans is a change
 * in that user, and the absolute value is not comparable to anybody else's.
 *
 * That is why there is no threshold in this file and why [SilhouetteBodyFat]'s plateau is not
 * settled from it. Having a true zero makes this a measurement; it does not make it a
 * calibrated one, and a metric that is honest about noise can still be read by nobody.
 *
 * Operating on a plain luminance grid rather than a platform bitmap keeps this in `:core`,
 * where it is testable without a device.
 */
object AbdominalDefinition {

    /**
     * Width every crop is resampled to before measuring.
     *
     * Load-bearing for comparability rather than for speed. Everything below is measured in
     * pixels of this grid, so a fixed width is what makes a feature's size relative to the
     * grid the same whatever the camera did. Without it the same abdomen at four times the
     * resolution read 16.2 against 30.4.
     */
    const val MEASURE_WIDTH = 128

    /**
     * Lower edge of the band, as a radius on the [MEASURE_WIDTH] grid.
     *
     * Everything finer than this is removed before anything is measured: skin grain, body
     * hair, JPEG blocking and sensor noise all live below it and none of them is adiposity.
     * Three rather than one because the attenuation of noise goes as the window's area — a
     * radius of three averages forty-nine samples against nine — and the residual on a
     * featureless crop falls with it, from 3.8 to 1.6 at two grey levels of noise.
     */
    const val GROOVE_RADIUS = 3

    /**
     * Upper edge of the band, as a radius on the [MEASURE_WIDTH] grid.
     *
     * Everything coarser than this is the light and the body's own curvature rather than its
     * surface, and subtracting it is what makes the reading independent of how the room was
     * lit. A tenth of the crop is roughly two centimetres on an adult, which is over the
     * spacing of the tendinous intersections and under the width of the abdomen.
     */
    const val SHADING_RADIUS = 10

    /** Below this mean luminance the crop is too dark for contrast to mean anything. */
    private const val MIN_MEAN_LUMINANCE = 25.0

    /** Fewer samples than this and the score is noise. */
    private const val MIN_SAMPLES = 64

    /**
     * Least variation in the coarse image, in grey levels, for the crop to be a lit surface.
     *
     * The divisor of the score, so a crop with none of it divides by nothing — which is
     * exactly what happened to the blank wall in the class header: 0.30 grey levels of
     * structure over 1.36 of shading came out as 21.8 and read as a defined abdomen. A body
     * under any light at all has several times this much shading across it; a flat wall, a
     * blown-out highlight and a crop that missed the subject do not, and all three should say
     * nothing rather than divide.
     */
    private const val MIN_SHADING_CONTRAST = 4.0

    /**
     * Scale factor, chosen so ordinary readings land in the tens rather than near zero.
     *
     * Cosmetic and deliberately arbitrary: this score is a ratio and its absolute value means
     * nothing on its own, so the only requirement is that a person comparing two of their own
     * scans sees a difference they can read.
     */
    private const val DISPLAY_SCALE = 100.0

    /** Ratio of a normal distribution's median absolute value to its standard deviation. */
    private const val MAD_TO_SD = 0.6745

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
        // The border is dropped below, and a crop that is all border has nothing left.
        if (w < 2 * SHADING_RADIUS + 4 || h < 2 * SHADING_RADIUS + 4) {
            return DefinitionReading(0.0, false)
        }

        val sigma = noiseLevel(grid, w, h)
        val groove = boxBlur(grid, w, h, GROOVE_RADIUS)
        val shading = boxBlur(grid, w, h, SHADING_RADIUS)

        // The band itself: fine enough to be a groove, coarse enough not to be grain, with
        // the illumination subtracted off as the low end rather than divided out.
        //
        // The margin drops a border of SHADING_RADIUS, where both windows are clamped and the
        // difference of two differently-clamped means is an edge artefact rather than a
        // feature. It is worth the pixels: with the border in, a flat gradient — no anatomy
        // at all — left a residual of 0.47 grey levels that no amount of noise subtraction
        // removed, because it was not noise.
        val margin = SHADING_RADIUS
        val band = ArrayList<Double>((w - 2 * margin) * (h - 2 * margin))
        var shadingSum = 0.0
        var shadingSumSq = 0.0
        var shadingCount = 0
        for (y in margin until h - margin) {
            for (x in margin until w - margin) {
                val i = y * w + x
                band += groove[i] - shading[i]
                shadingSum += shading[i]
                shadingSumSq += shading[i] * shading[i]
                shadingCount++
            }
        }
        if (band.isEmpty()) return DefinitionReading(0.0, false)

        // How much of the band is the sensor. A box mean over n samples divides noise variance
        // by n, and the band is the difference of two such means over independent windows, so
        // what survives into it is known rather than guessed at.
        val grooveWindow = (2 * GROOVE_RADIUS + 1) * (2 * GROOVE_RADIUS + 1)
        val shadingWindow = (2 * SHADING_RADIUS + 1) * (2 * SHADING_RADIUS + 1)
        val noiseVariance = sigma * sigma * (1.0 / grooveWindow + 1.0 / shadingWindow)

        // Median of the absolute band, rescaled to the spread it implies. A mean would be
        // moved by a navel; this is not.
        val absolute = band.map { abs(it) }.sorted()
        val spread = absolute[absolute.size / 2] / MAD_TO_SD

        val structure = sqrt(maxOf(0.0, spread * spread - noiseVariance))

        val shadingMean = shadingSum / shadingCount
        val contrast = sqrt(
            maxOf(0.0, shadingSumSq / shadingCount - shadingMean * shadingMean),
        )
        if (contrast < MIN_SHADING_CONTRAST) return DefinitionReading(0.0, false)

        return DefinitionReading(score = structure / contrast * DISPLAY_SCALE, usable = true)
    }

    /**
     * The crop's own sensor noise, in grey levels.
     *
     * Immerkær's estimator: convolve with a mask that is zero on any locally linear or
     * quadratic surface, so skin, shading and grooves all cancel and what is left is the
     * camera. The alternative — assuming a fixed noise level, as the previous version did with
     * its floor of three — is what let a noisy crop read as a defined abdomen, because a
     * constant cannot know that this photograph was taken at ISO 3200.
     *
     * The mask's norm is six and the mean absolute value of a normal variable is its standard
     * deviation times the square root of two over pi; both appear in the divisor.
     */
    fun noiseLevel(grid: DoubleArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3) return 0.0
        var total = 0.0
        var count = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val response =
                    grid[(y - 1) * w + x - 1] - 2 * grid[(y - 1) * w + x] +
                        grid[(y - 1) * w + x + 1] -
                        2 * grid[y * w + x - 1] + 4 * grid[y * w + x] -
                        2 * grid[y * w + x + 1] +
                        grid[(y + 1) * w + x - 1] - 2 * grid[(y + 1) * w + x] +
                        grid[(y + 1) * w + x + 1]
                total += abs(response)
                count++
            }
        }
        if (count == 0) return 0.0
        return total / count / (6.0 * sqrt(2.0 / kotlin.math.PI))
    }

    /**
     * Area-averaged resample to exactly [MEASURE_WIDTH], upward as well as downward.
     *
     * Both directions, and that is the fix rather than an extra. Resampling only when the crop
     * was too large left small crops measured at their own scale, so the band spacing relative
     * to the pixel grid still depended on how close the phone was — the same abdomen at four
     * times the resolution read 16.2 against 30.4. Sending every crop to one width makes the
     * feature size relative to the grid identical whatever the camera did.
     *
     * Note what it does *not* fix, which cost this file a release: averaging blocks of source
     * pixels also averages their noise down, so a large crop arrives quieter than a small one.
     * Feature scale is made comparable here; noise amplitude is made comparable by measuring
     * and subtracting it in [measure], and the previous version did only the first and
     * believed it had done both.
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
     * Clamped rather than zero-padded: padding with zeros would pull the mean down around the
     * border and manufacture contrast exactly where the crop meets the flanks. The clamping
     * still distorts the border, which is why [measure] drops it rather than trusting it.
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
    // A `placeWithinPlateau` used to sit at this point, turning the score into a figure between
    // the lean end of the plateau and its top through two thresholds. It is gone, and giving
    // this metric a true zero is not grounds to bring it back: what such a function needs is a
    // score that means the same on two different bodies, and nothing here supplies that.
    //
    // What can legitimately turn this into a percentage is an anchor from the person whose
    // abdomen it is — one reference figure, through PersonalCalibration, which is exactly the
    // machinery already built for the tape equations. Until that anchor exists the honest
    // output is the change between one user's own scans, and nothing else.
}
