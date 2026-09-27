package com.squeeze.core.trend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChartScaleTest {

    @Test
    fun `a wobble inside scan noise is drawn on an axis at least the noise floor tall`() {
        val axis = assertNotNull(ChartScale.axis(listOf(15.2, 15.8), minSpan = TrendFactor.BODY_FAT.minSpan))
        assertTrue(axis.max - axis.min >= 3.0)
        assertTrue(axis.min <= 15.2 && axis.max >= 15.8)
    }

    @Test
    fun `ticks are round numbers, evenly spaced, covering the data`() {
        val axis = assertNotNull(ChartScale.axis(listOf(11.7, 19.1), minSpan = 3.0))
        axis.ticks.zipWithNext { a, b -> assertEquals(axis.step, b - a, 1e-9) }
        axis.ticks.forEach { t -> assertEquals(0.0, (t / axis.step) - kotlin.math.round(t / axis.step), 1e-9) }
        assertTrue(axis.min <= 11.7 && axis.max >= 19.1)
        assertTrue(axis.ticks.size in 3..7)
    }

    @Test
    fun `labels carry only the decimals the step needs`() {
        assertEquals(0, assertNotNull(ChartScale.axis(listOf(60.0, 72.0), 2.0)).decimals)
        assertEquals(1, assertNotNull(ChartScale.axis(listOf(70.0, 70.4), 0.5)).decimals)
    }

    @Test
    fun `no data, no axis`() {
        assertNull(ChartScale.axis(emptyList(), 3.0))
        assertNull(ChartScale.axis(listOf(Double.NaN), 3.0))
    }

    @Test
    fun `change reads from the first point to the last, with the filter's verdict`() {
        fun p(day: Long, level: Double, rate: Double, rateSd: Double) =
            TrendPoint(day, level, rate, 1.0, rateSd, level)
        val quiet = assertNotNull(TrendChange.of(listOf(p(0, 15.2, 0.0, 1.0), p(4, 15.8, 0.3, 1.0))))
        assertEquals(0.6, quiet.delta, 1e-9)
        assertTrue(!quiet.significant)
        val real = assertNotNull(TrendChange.of(listOf(p(0, 18.0, 0.0, 1.0), p(60, 15.0, -0.4, 0.1))))
        assertTrue(real.significant)
        assertNull(TrendChange.of(listOf(p(0, 15.0, 0.0, 1.0))))
    }
}
