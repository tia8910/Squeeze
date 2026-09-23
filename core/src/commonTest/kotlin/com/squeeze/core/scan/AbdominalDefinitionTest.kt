package com.squeeze.core.scan

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Whether this metric has a zero.
 *
 * It is the only question these tests exist to answer, because the version that shipped did
 * not have one and nothing else about it mattered. Its score on a featureless patch ran from
 * 2.7 to 67.3 depending purely on the crop's size and the camera's grain, against a threshold
 * of 20 for "this abdomen is visibly defined". A patch of blank painted wall in a real scan
 * photograph scored 21.3. The smooth abdomen the app was actually asked about scored 37.9 and
 * was reported to its owner as read from his photograph.
 *
 * So the assertions below are mostly about *nothing*: various pictures of no abdominal
 * structure at all, which must all come back near zero however large they are and however
 * noisy. A metric that reads high on nothing cannot be fixed by moving a threshold, because
 * its readings are not ordered by the thing it claims to measure — real structure at a
 * thousand pixels scored 9.8 under the old metric while a blank field at a hundred and sixty
 * scored 26.0.
 *
 * The fixtures are synthetic on purpose. Real photographs are how this went wrong: three of
 * them produced 32.5, 21.9 and 16.5, the gap between the lowest two became a threshold, and
 * nobody had measured what the metric returned when there was nothing to measure. Ground truth
 * here is known by construction.
 */
class AbdominalDefinitionTest {

    /**
     * A crop of abdomen, built rather than photographed.
     *
     * @param size width in pixels, which is the whole point of varying it — the score has to
     *   be the same for the same body at any camera resolution
     * @param grooveAmplitude contrast of the muscle separation, in grey levels. Zero means a
     *   featureless body: shading, noise, and no anatomy.
     * @param noise sensor noise standard deviation, in grey levels
     * @param shading amplitude of the broad light gradient across the crop. Every lit body has
     *   this; it is what the metric divides by, and a crop without it is not a body.
     * @param navel a dark blob a few per cent of the crop across, present on every human at
     *   every body fat and therefore never evidence of definition
     */
    private fun crop(
        size: Int,
        grooveAmplitude: Double = 0.0,
        noise: Double = 2.0,
        shading: Double = 25.0,
        navel: Boolean = false,
        seed: Int = 20260920,
    ): Pair<IntArray, Int> {
        val width = size
        val height = size * 6 / 5
        val out = IntArray(width * height)
        var state = seed
        // Uniform noise of the requested standard deviation. Deterministic, so a failure is
        // reproducible; the estimator in AbdominalDefinition assumes nothing about the shape
        // of the distribution beyond its spread.
        val halfRange = noise * 1.7320508

        val period = 0.22 * width
        for (y in 0 until height) {
            for (x in 0 until width) {
                var value = 150.0 + shading * (x.toDouble() / width - 0.5) * 2.0
                if (grooveAmplitude > 0.0) {
                    value += grooveAmplitude *
                        sin(2 * PI * y / period) *
                        cos(2 * PI * x / (period * 1.6))
                }
                if (navel) {
                    val radius = 0.06 * width
                    val dx = x - width * 0.5
                    val dy = y - height * 0.55
                    value -= 45.0 * exp(-(dx * dx + dy * dy) / (2 * radius * radius))
                }
                if (noise > 0.0) {
                    state = state * 1664525 + 1013904223
                    val unit = ((state ushr 8) and 0xFFFF).toDouble() / 65536.0
                    value += (unit * 2.0 - 1.0) * halfRange
                }
                out[y * width + x] = value.roundToInt().coerceIn(0, 255)
            }
        }
        return out to width
    }

    private fun score(sample: Pair<IntArray, Int>) =
        AbdominalDefinition.measure(sample.first, sample.second)

    private val sizes = listOf(160, 320, 640)
    private val noiseLevels = listOf(0.5, 2.0, 5.0, 8.0)

    @Test
    fun `nothing reads as nothing, at every resolution and every noise level`() {
        // The test the old metric could not have passed at any threshold. Its readings on
        // exactly these inputs: 8.1, 26.0, 53.5, 67.3 at 160 px; 4.0, 13.1, 31.3, 47.2 at
        // 640. Every one of them a picture of a body with no abdominal structure whatsoever.
        for (size in sizes) {
            for (noise in noiseLevels) {
                val reading = score(crop(size, grooveAmplitude = 0.0, noise = noise))
                assertTrue(reading.usable, "$size px at noise $noise was refused")
                assertTrue(
                    reading.score < 8.0,
                    "featureless at $size px, noise $noise scored ${reading.score}",
                )
            }
        }
    }

    @Test
    fun `structure reads the same whatever the camera did`() {
        // The property the resample was supposed to provide and only half provided: it made
        // feature size comparable across resolutions and left noise amplitude varying, so the
        // same abdomen still read differently on two phones. Measured on the old metric across
        // this grid: 9.8 to 61.4, a factor of six on one body.
        val readings = mutableListOf<Double>()
        for (size in sizes) {
            for (noise in noiseLevels) {
                val reading = score(crop(size, grooveAmplitude = 6.0, noise = noise))
                assertTrue(reading.usable, "$size px at noise $noise was refused")
                readings += reading.score
            }
        }

        val low = readings.min()
        val high = readings.max()
        assertTrue(low > 12.0, "structure fell to $low")
        assertTrue(
            high - low < low * 0.35,
            "one body read from $low to $high across resolutions and noise levels",
        )
    }

    @Test
    fun `structure and its absence do not overlap`() {
        // What a threshold would need, stated as the gap rather than as a constant: across
        // every combination above, the worst reading of real structure is clear of the best
        // reading of none. Under the old metric these two sets interleaved completely.
        var worstStructure = Double.MAX_VALUE
        var bestNothing = 0.0
        for (size in sizes) {
            for (noise in noiseLevels) {
                worstStructure = minOf(
                    worstStructure,
                    score(crop(size, grooveAmplitude = 6.0, noise = noise)).score,
                )
                bestNothing = maxOf(
                    bestNothing,
                    score(crop(size, grooveAmplitude = 0.0, noise = noise)).score,
                )
            }
        }

        assertTrue(
            worstStructure > bestNothing * 1.5,
            "structure bottomed out at $worstStructure, nothing topped out at $bestNothing",
        )
    }

    @Test
    fun `a navel is not a six-pack`() {
        // On the photograph that prompted this rewrite the navel was the entire reading: 5.75
        // grey levels of structure over the whole crop against 1.67 with the midline excluded.
        // A mean over the crop cannot tell one deep blob from separation across the abdomen;
        // a median can, because separation covers the abdomen and a navel is two per cent of
        // it. This same fixture scored 19.8 under the old metric, against a defined threshold
        // of 20.
        for (size in listOf(320, 640)) {
            val reading = score(crop(size, grooveAmplitude = 0.0, navel = true))
            assertTrue(reading.usable, "$size px was refused")
            assertTrue(reading.score < 8.0, "a navel alone scored ${reading.score} at $size px")
        }
    }

    @Test
    fun `how hard the light was does not change what is there`() {
        // The failure that caused the previous rewrite, kept because the new metric has to
        // survive it too: a hard lamp multiplies the grooves and the shading across the body
        // together, so the ratio between them is what belongs to the anatomy.
        val soft = score(crop(320, grooveAmplitude = 3.0, shading = 12.0))
        val harsh = score(crop(320, grooveAmplitude = 9.0, shading = 36.0))

        assertTrue(soft.usable && harsh.usable)
        assertTrue(
            abs(soft.score - harsh.score) < harsh.score * 0.2,
            "same abdomen, 3x light: ${soft.score} vs ${harsh.score}",
        )
    }

    @Test
    fun `a crop with no light across it is refused rather than divided by`() {
        // The blank wall, which scored 21.3 and would have corroborated a lean reading. It has
        // 0.30 grey levels of structure in it — correctly almost nothing — over 1.36 of
        // shading, and the old metric published the quotient.
        val wall = score(crop(320, grooveAmplitude = 0.0, shading = 0.4, noise = 1.0))
        assertTrue(!wall.usable, "a flat wall scored ${wall.score} instead of being refused")
    }

    @Test
    fun `a dark crop is refused rather than scored zero`() {
        // No definition and no information look identical in a number and mean the opposite.
        assertTrue(!AbdominalDefinition.measure(IntArray(4096) { 8 }, 64).usable)
    }

    @Test
    fun `a crop too small to mean anything is refused`() {
        assertTrue(!AbdominalDefinition.measure(IntArray(12) { 140 }, 4).usable)
        assertTrue(!AbdominalDefinition.measure(IntArray(0), 0).usable)
    }

    @Test
    fun `the noise estimate is the crop's own, not a constant`() {
        // The specific thing the old floor of three grey levels could not do. A constant
        // cannot know this photograph was taken at ISO 3200, which is why noise passed
        // straight through it and became a score.
        val grid = DoubleArray(128 * 128)
        var state = 7
        for (i in grid.indices) {
            state = state * 1664525 + 1013904223
            val unit = ((state ushr 8) and 0xFFFF).toDouble() / 65536.0
            grid[i] = 150.0 + (unit * 2.0 - 1.0) * 4.0 * 1.7320508
        }

        val estimated = AbdominalDefinition.noiseLevel(grid, 128, 128)
        assertTrue(abs(estimated - 4.0) < 0.6, "4.0 grey levels of noise estimated at $estimated")
    }
}
