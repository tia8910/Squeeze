package com.squeeze.core.trend

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * A chart's vertical axis: its range and the round values it is labelled at.
 *
 * @param ticks from [min] to [max] inclusive, evenly spaced by [step]
 */
data class ChartAxis(val min: Double, val max: Double, val step: Double, val ticks: List<Double>) {
    /** How many decimals the tick labels need so that no two read the same. */
    val decimals: Int get() = if (step >= 1.0 - 1e-9) 0 else if (step >= 0.1 - 1e-9) 1 else 2
}

/**
 * Picks an honest vertical axis for a trend.
 *
 * Two things the old axis got wrong. It ran from the lowest to the highest edge of the
 * confidence band, so the tick labels were whatever those happened to be — 11.7, 15.4, 19.1
 * — which nobody reads at a glance. And it never had a floor, so any wobble, however small,
 * filled the chart. Ticks here are round (1, 2, 2.5 or 5 times a power of ten) and the range
 * is at least [TrendFactor.minSpan], centred on the data.
 */
object ChartScale {

    fun axis(values: List<Double>, minSpan: Double, targetTicks: Int = 4): ChartAxis? {
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty() || targetTicks < 2) return null
        val lo = finite.min()
        val hi = finite.max()
        val centre = (lo + hi) / 2.0
        // A little headroom so the highest and lowest points do not sit on the frame.
        val span = maxOf((hi - lo) * 1.15, minSpan, 1e-6)
        val step = niceStep(span / (targetTicks - 1))
        val min = floor((centre - span / 2.0) / step + 1e-9) * step
        val max = ceil((centre + span / 2.0) / step - 1e-9) * step
        val count = ((max - min) / step).let { kotlin.math.round(it).toInt() }
        val ticks = (0..count).map { clean(min + it * step) }
        return ChartAxis(clean(min), clean(max), step, ticks)
    }

    private fun niceStep(raw: Double): Double {
        val magnitude = 10.0.pow(floor(log10(raw)))
        val fraction = raw / magnitude
        val nice = when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 2.5 -> 2.5
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }
        return nice * magnitude
    }

    /** Removes the 15.000000000002 that repeated addition leaves behind. */
    private fun clean(v: Double): Double {
        val r = kotlin.math.round(v * 1e6) / 1e6
        return if (abs(r) < 1e-9) 0.0 else r
    }
}

/**
 * What a series did between its first and last point, said the way the chart should say it.
 *
 * @param significant the filter's own verdict ([TrendPoint.isChangeSignificant]) on the
 *   latest point — whether the rate is distinguishable from measurement noise
 */
data class TrendChange(
    val from: Double,
    val to: Double,
    val fromDay: Long,
    val toDay: Long,
    val weeklyChange: Double,
    val significant: Boolean,
) {
    val delta: Double get() = to - from

    companion object {
        fun of(points: List<TrendPoint>): TrendChange? {
            if (points.size < 2) return null
            val first = points.first()
            val last = points.last()
            return TrendChange(
                from = first.level,
                to = last.level,
                fromDay = first.epochDay,
                toDay = last.epochDay,
                weeklyChange = last.weeklyChange,
                significant = last.isChangeSignificant,
            )
        }
    }
}
