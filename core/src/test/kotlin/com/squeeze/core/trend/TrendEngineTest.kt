package com.squeeze.core.trend

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrendEngineTest {

    private val engine = TrendEngine()

    /**
     * @param se random scatter, defaulting to the tape method's repeatability. Note this is
     *   the *precision* figure (0.5), not the method's accuracy against DEXA (3.5): the
     *   filter is estimating change, and a systematic offset does not obscure change.
     */
    private fun series(values: List<Double>, everyDays: Long = 7, se: Double = 0.5) =
        values.mapIndexed { i, v -> Observation(epochDay = i * everyDays, value = v, standardError = se) }

    @Test
    fun `empty input produces no points`() {
        assertTrue(engine.filter(emptyList()).isEmpty())
    }

    @Test
    fun `single observation seeds the level and claims no trend`() {
        val result = engine.filter(series(listOf(20.0)))

        assertEquals(1, result.size)
        assertEquals(20.0, result.single().level, 1e-9)
        assertEquals(0.0, result.single().weeklyChange, 1e-9)
        assertFalse(result.single().isChangeSignificant)
    }

    @Test
    fun `filter smooths noise around a stable true value`() {
        // True body fat is flat at 20%; readings scatter by up to a point either side.
        val noisy = listOf(21.0, 19.0, 20.5, 19.5, 20.8, 19.2, 20.3, 19.7)
        val result = engine.filter(series(noisy))

        val finalLevel = result.last().level
        assertTrue(abs(finalLevel - 20.0) < 0.8, "filtered level should sit near 20, was $finalLevel")

        // The filtered line must vary less than the raw readings it came from.
        val rawSpread = noisy.max() - noisy.min()
        val filteredSpread = result.map { it.level }.let { it.max() - it.min() }
        assertTrue(filteredSpread < rawSpread, "filter should reduce spread: $filteredSpread vs $rawSpread")
    }

    @Test
    fun `a stable series never reports a significant change`() {
        val random = Random(42)
        val flat = List(12) { 20.0 + random.nextDouble(-0.4, 0.4) }
        val result = engine.filter(series(flat))

        assertFalse(
            result.last().isChangeSignificant,
            "flat data must not produce a confident trend, got ${result.last().weeklyChange}",
        )
    }

    @Test
    fun `a sustained decline is detected with the correct sign and rough magnitude`() {
        // A real cut: 0.3 points per week over 12 weeks, with measurement noise on top.
        val random = Random(7)
        val values = (0 until 12).map { week -> 25.0 - 0.3 * week + random.nextDouble(-0.4, 0.4) }
        val result = engine.filter(series(values))

        val last = result.last()
        assertTrue(last.weeklyChange < 0, "should detect a downward trend, got ${last.weeklyChange}")
        assertEquals(-0.3, last.weeklyChange, 0.15)
        assertTrue(last.isChangeSignificant, "a 12 week consistent trend should be significant")
    }

    @Test
    fun `confidence in the level tightens as measurements accumulate`() {
        val result = engine.filter(series(List(10) { 20.0 }))

        assertTrue(
            result.last().levelStdDev < result.first().levelStdDev,
            "repeated measurement must reduce uncertainty",
        )
    }

    @Test
    fun `unsorted input is handled in chronological order`() {
        val shuffled = listOf(
            Observation(epochDay = 21, value = 19.0, standardError = 0.5),
            Observation(epochDay = 0, value = 22.0, standardError = 0.5),
            Observation(epochDay = 14, value = 20.0, standardError = 0.5),
            Observation(epochDay = 7, value = 21.0, standardError = 0.5),
        )
        val result = engine.filter(shuffled)

        assertEquals(listOf(0L, 7L, 14L, 21L), result.map { it.epochDay })
        assertTrue(result.last().weeklyChange < 0, "series descends over time")
    }

    @Test
    fun `irregular gaps do not break the filter`() {
        // A user who measures, disappears for two months, then returns.
        val sparse = listOf(
            Observation(0, 25.0, 0.5),
            Observation(7, 24.5, 0.5),
            Observation(70, 20.0, 0.5),
            Observation(77, 19.8, 0.5),
        )
        val result = engine.filter(sparse)

        assertEquals(4, result.size)
        assertTrue(result.all { it.level.isFinite() }, "no NaN across a long gap")
        assertTrue(result.all { it.levelStdDev.isFinite() && it.levelStdDev >= 0 })
    }

    @Test
    fun `a precise measurement moves the level more than a noisy one`() {
        val start = Observation(0, 20.0, 0.5)

        val afterNoisy = engine.filter(listOf(start, Observation(7, 25.0, 1.5))).last().level
        val afterPrecise = engine.filter(listOf(start, Observation(7, 25.0, 0.2))).last().level

        assertTrue(
            afterPrecise > afterNoisy,
            "the Kalman gain must scale with how much the observation can be trusted",
        )
    }

    @Test
    fun `trend detection depends on precision not on accuracy`() {
        // A real cut of 0.3 points per week over 12 weeks, measured cleanly.
        val values = (0 until 12).map { week -> 25.0 - 0.3 * week }

        // Fed the tape method's repeatability, the trend is unmistakable.
        val byPrecision = engine.filter(series(values, se = 0.5)).last()
        assertTrue(byPrecision.isChangeSignificant, "a clean 12 week trend must be detectable")

        // Fed its accuracy against DEXA instead, the same data looks like noise. The offset
        // that figure describes is systematic and cancels out across a series, so using it
        // here would hide changes the user can see in the mirror.
        val byAccuracy = engine.filter(series(values, se = 3.5)).last()
        assertFalse(
            byAccuracy.isChangeSignificant,
            "documents why standardErrorPercent must never be passed as measurement noise",
        )
    }

    @Test
    fun `same day replicates reduce uncertainty without inventing a trend`() {
        val replicates = listOf(
            Observation(0, 20.0, 0.5),
            Observation(0, 20.2, 0.5),
            Observation(0, 19.8, 0.5),
        )
        val result = engine.filter(replicates)

        assertTrue(result.last().levelStdDev < result.first().levelStdDev)
        assertFalse(result.last().isChangeSignificant, "replicates on one day are not a trend")
    }
}
