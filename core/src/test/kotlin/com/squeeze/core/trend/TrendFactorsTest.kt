package com.squeeze.core.trend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrendFactorsTest {

    private fun point(level: Double) = TrendPoint(
        epochDay = 20_000L,
        level = level,
        weeklyChange = 0.0,
        levelStdDev = 0.3,
        weeklyChangeStdDev = 0.2,
        raw = level,
    )

    private val one = listOf(point(20.0))
    private val none = emptyList<TrendPoint>()

    @Test
    fun `a scan-only user is offered body fat alone`() {
        // No weight entered means no weight series and no muscle series — muscle is derived
        // from both. Offering those chips would promise charts that cannot be drawn.
        val available = TrendFactors.available(bodyFat = one, weight = none, muscle = none)

        assertEquals(listOf(TrendFactor.BODY_FAT), available)
    }

    @Test
    fun `all three appear once weight has been recorded`() {
        val available = TrendFactors.available(bodyFat = one, weight = one, muscle = one)

        assertEquals(
            listOf(TrendFactor.BODY_FAT, TrendFactor.WEIGHT, TrendFactor.MUSCLE),
            available,
        )
    }

    @Test
    fun `order is fixed, not data-dependent`() {
        // The chips must not reorder as data arrives; a control that moves under the finger
        // is worse than one option fewer.
        val available = TrendFactors.available(bodyFat = none, weight = one, muscle = one)

        assertEquals(listOf(TrendFactor.WEIGHT, TrendFactor.MUSCLE), available)
    }

    @Test
    fun `a single point still counts as available`() {
        // The chart says "two measurements are needed" in words. That is a better answer
        // than a chip vanishing, which reads as the app having lost the entry.
        assertEquals(
            listOf(TrendFactor.BODY_FAT),
            TrendFactors.available(bodyFat = one, weight = none, muscle = none),
        )
    }

    @Test
    fun `a live selection is kept`() {
        val resolved = TrendFactors.resolve(
            TrendFactor.MUSCLE,
            listOf(TrendFactor.BODY_FAT, TrendFactor.WEIGHT, TrendFactor.MUSCLE),
        )

        assertEquals(TrendFactor.MUSCLE, resolved)
    }

    @Test
    fun `a selection whose data was deleted falls back instead of showing nothing`() {
        // Deleting the only entry carrying a weight while Weight is selected. Without this
        // the chart points at an empty series and the screen looks broken.
        val resolved = TrendFactors.resolve(TrendFactor.WEIGHT, listOf(TrendFactor.BODY_FAT))

        assertEquals(TrendFactor.BODY_FAT, resolved)
    }

    @Test
    fun `no selection yet means the first available factor`() {
        assertEquals(
            TrendFactor.BODY_FAT,
            TrendFactors.resolve(null, listOf(TrendFactor.BODY_FAT, TrendFactor.WEIGHT)),
        )
    }

    @Test
    fun `no data at all resolves to nothing`() {
        assertNull(TrendFactors.resolve(TrendFactor.BODY_FAT, emptyList()))
    }

    @Test
    fun `units are right for each factor, since the chart prints them`() {
        assertEquals("%", TrendFactor.BODY_FAT.unitSuffix)
        assertEquals(" kg", TrendFactor.WEIGHT.unitSuffix)
        assertEquals(" kg", TrendFactor.MUSCLE.unitSuffix)
    }
}
