package com.squeeze.core.trend

import kotlin.math.sqrt

/**
 * One filtered point on the trend line.
 *
 * @param epochDay the day this estimate refers to
 * @param level filtered value, e.g. body fat percentage or bodyweight
 * @param weeklyChange fitted rate of change per week; the number a user actually acts on
 * @param levelStdDev standard deviation of [level], for the confidence band
 * @param weeklyChangeStdDev standard deviation of [weeklyChange]
 * @param raw the unfiltered observation that produced this point
 */
data class TrendPoint(
    val epochDay: Long,
    val level: Double,
    val weeklyChange: Double,
    val levelStdDev: Double,
    val weeklyChangeStdDev: Double,
    val raw: Double,
) {
    /** Half-width of the 95% confidence interval around [level]. */
    val levelConfidence95: Double get() = 1.96 * levelStdDev

    /**
     * True when the fitted rate of change is distinguishable from zero at 95% confidence.
     *
     * This is the honest answer to "is my cut working?". Until it returns true the app
     * should say the trend is not yet separable from measurement noise, rather than
     * drawing a confident arrow through scatter.
     */
    val isChangeSignificant: Boolean
        get() = weeklyChangeStdDev > 0 && kotlin.math.abs(weeklyChange) > 1.96 * weeklyChangeStdDev
}

/** A single observation to feed the filter. */
data class Observation(
    val epochDay: Long,
    val value: Double,
    /**
     * Random measurement scatter for this observation, i.e. the method's
     * [com.squeeze.core.model.EstimationMethod.repeatabilityPercent] — **not** its
     * standard error of estimate.
     *
     * The filter models this as independent noise around a smoothly moving true value.
     * An equation's accuracy error is mostly a fixed personal offset, which shifts the
     * whole series equally and therefore does not obscure its shape; passing it here
     * would tell the filter the data is far noisier than it is and suppress trends the
     * user can genuinely see.
     */
    val standardError: Double,
)

/**
 * A local linear trend (constant-velocity Kalman) filter over an irregular time axis.
 *
 * Body composition apps usually draw a line through raw readings, which is misleading:
 * day-to-day scatter from hydration, food volume and measurement technique routinely
 * exceeds the real weekly change. A user comparing two raw readings a week apart is
 * mostly comparing noise.
 *
 * This filter separates the underlying level and its rate of change from that scatter,
 * and reports the uncertainty of both, so the UI can show a confidence band and stay
 * silent about direction until the data supports one. Measurements arrive at irregular
 * intervals, so the transition and process noise are both parameterised by elapsed days.
 *
 * @param processNoisePerDay how fast the underlying trend is allowed to change, in
 *   (units^2)/day^3. Larger values track sharp changes but admit more noise. The default
 *   is tuned for body fat percentage, where genuine change is on the order of tenths of a
 *   point per week.
 */
class TrendEngine(private val processNoisePerDay: Double = DEFAULT_PROCESS_NOISE) {

    /**
     * Filters observations in time order.
     *
     * @param observations need not be sorted; duplicates on the same day are kept and
     *   handled as repeat measurements, which correctly shrinks the level's variance.
     * @return one [TrendPoint] per observation, in chronological order
     */
    fun filter(observations: List<Observation>): List<TrendPoint> {
        if (observations.isEmpty()) return emptyList()
        val sorted = observations.sortedBy { it.epochDay }

        val first = sorted.first()

        // State: [level, velocity-per-day]. Velocity starts at zero with wide variance so
        // the filter is free to discover any initial direction from the data.
        var level = first.value
        var velocity = 0.0

        // Covariance P, symmetric 2x2 stored as three scalars.
        var pLevel = first.standardError * first.standardError
        var pCross = 0.0
        var pVelocity = INITIAL_VELOCITY_VARIANCE

        val out = ArrayList<TrendPoint>(sorted.size)
        out += TrendPoint(
            epochDay = first.epochDay,
            level = level,
            weeklyChange = 0.0,
            levelStdDev = sqrt(pLevel),
            weeklyChangeStdDev = sqrt(pVelocity) * DAYS_PER_WEEK,
            raw = first.value,
        )

        var previousDay = first.epochDay

        for (obs in sorted.drop(1)) {
            val dt = (obs.epochDay - previousDay).toDouble().coerceAtLeast(0.0)

            if (dt > 0.0) {
                // --- Predict: x = F x, P = F P F^T + Q ---
                level += velocity * dt

                // F P F^T for F = [[1, dt], [0, 1]]
                val predLevel = pLevel + 2.0 * dt * pCross + dt * dt * pVelocity
                val predCross = pCross + dt * pVelocity
                val predVelocity = pVelocity

                // Continuous white-noise acceleration process noise.
                val q = processNoisePerDay
                pLevel = predLevel + q * dt * dt * dt / 3.0
                pCross = predCross + q * dt * dt / 2.0
                pVelocity = predVelocity + q * dt
            }

            // --- Update with H = [1, 0] ---
            val r = obs.standardError * obs.standardError
            val innovation = obs.value - level
            val innovationVariance = pLevel + r

            // Guard against a zero-variance observation making the gain singular.
            if (innovationVariance > VARIANCE_EPSILON) {
                val kLevel = pLevel / innovationVariance
                val kVelocity = pCross / innovationVariance

                level += kLevel * innovation
                velocity += kVelocity * innovation

                // P = (I - K H) P, expanded for the 2x2 case.
                val newLevel = (1.0 - kLevel) * pLevel
                val newCross = (1.0 - kLevel) * pCross
                val newVelocity = pVelocity - kVelocity * pCross

                pLevel = newLevel
                pCross = newCross
                pVelocity = newVelocity.coerceAtLeast(0.0)
            }

            out += TrendPoint(
                epochDay = obs.epochDay,
                level = level,
                weeklyChange = velocity * DAYS_PER_WEEK,
                levelStdDev = sqrt(pLevel.coerceAtLeast(0.0)),
                weeklyChangeStdDev = sqrt(pVelocity) * DAYS_PER_WEEK,
                raw = obs.value,
            )
            previousDay = obs.epochDay
        }

        return out
    }

    companion object {
        private const val DAYS_PER_WEEK = 7.0
        private const val INITIAL_VELOCITY_VARIANCE = 1.0
        private const val VARIANCE_EPSILON = 1e-12

        /**
         * Process noise for body fat percentage, in (percentage points)^2 per day^3.
         *
         * This value sets a floor on how confident the filter can ever become about the
         * rate of change, so it has to be matched to how fast a real trend can turn. At
         * 1e-6 the velocity's standard deviation grows by about 0.07 %/week over three
         * months, which comfortably covers a lifter switching from a cut to a bulk while
         * still letting a genuine 0.3 %/week trend clear the significance threshold after
         * roughly eight to ten weekly measurements.
         *
         * Larger values were tried and rejected: at 1e-4 the noise floor alone keeps the
         * weekly-change standard deviation near 0.26, so a real cut never reads as
         * significant no matter how long it runs.
         */
        const val DEFAULT_PROCESS_NOISE = 1e-6

        /**
         * Bodyweight turns faster than body composition does — water, food volume and
         * glycogen move it within days — so its underlying trend is allowed to change an
         * order of magnitude more freely.
         */
        const val BODYWEIGHT_PROCESS_NOISE = 1e-5
    }
}
