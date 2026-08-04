package com.squeeze.core.bodycomp

import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.EstimationMethod
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Combines several body-fat estimates into one.
 *
 * Running two methods and picking the "best" throws away real information. Two independent
 * routes to the same quantity constrain it better than either alone, and the standard way to
 * merge them is inverse-variance weighting: each estimate contributes in proportion to how
 * precise it is, so a tape reading outweighs a photo scan without silencing it.
 *
 * Two things stop this from being a free accuracy win, and both are handled explicitly.
 *
 * **The methods are not independent.** They measure one body, on one day, with one operator
 * and one set of habits. Treating them as independent would claim a variance reduction that
 * does not exist, and the app would report a tighter interval than it has earned — which is
 * exactly the dishonesty the rest of this codebase is built to avoid. [ASSUMED_CORRELATION]
 * puts a floor under the combined error to account for it.
 *
 * **Not every method is a peer.** The BMI fallback cannot see muscle, so on a lean, muscular
 * person it reads far too high. Averaging it against a circumference measurement does not
 * split the difference between two opinions — it drags a good estimate toward a known bias.
 * It is used only when nothing better exists.
 */
object MethodFusion {

    /**
     * Assumed correlation between two methods applied to the same body on the same day.
     *
     * Not measured, and deliberately pessimistic. Independent estimates would combine to
     * `σ/√n`; at ρ = 0.5 two equally precise methods give about `0.87σ` instead. Claiming
     * the former would understate the interval by a quarter.
     */
    const val ASSUMED_CORRELATION = 0.5

    /**
     * How far apart two methods must sit before the disagreement is worth reporting, as a
     * multiple of the combined standard error.
     */
    private const val DISAGREEMENT_SIGMA = 2.0

    /**
     * @param combined the fused estimate
     * @param contributors every method that fed into it, most precise first
     * @param disagreementPoints spread between the highest and lowest contributing estimate,
     *   or null when only one method was available
     * @param methodsDisagree true when that spread is larger than the fused uncertainty can
     *   explain, which means one of the inputs is wrong rather than merely noisy
     */
    data class FusedEstimate(
        val combined: BodyFatEstimate,
        val contributors: List<EstimationMethod>,
        val disagreementPoints: Double?,
        val methodsDisagree: Boolean,
    )

    /**
     * Fuses [estimates], or returns null when there is nothing to fuse.
     *
     * A single estimate comes back unchanged rather than being dressed up as a combination.
     */
    fun combine(estimates: List<BodyFatEstimate>): FusedEstimate? {
        val usable = estimates.filter { it.percent.isFinite() && it.standardErrorPercent > 0.0 }
        if (usable.isEmpty()) return null

        // The BMI fallback is a last resort, not a second opinion. It is blind to muscle, so
        // fusing it with a measured method moves the answer toward a bias rather than
        // averaging out noise.
        val measured = usable.filter { it.method != EstimationMethod.DEURENBERG_BMI }
        val pool = measured.ifEmpty { usable }

        if (pool.size == 1) {
            val only = pool.first()
            return FusedEstimate(
                combined = only,
                contributors = listOf(only.method),
                disagreementPoints = null,
                methodsDisagree = false,
            )
        }

        var weightSum = 0.0
        var weightedValue = 0.0
        for (estimate in pool) {
            val weight = 1.0 / (estimate.standardErrorPercent * estimate.standardErrorPercent)
            weightSum += weight
            weightedValue += weight * estimate.percent
        }

        val mean = weightedValue / weightSum
        val independentError = sqrt(1.0 / weightSum)

        // The floor. However many methods agree, the fused error cannot fall below what the
        // shared, correlated part of their error allows.
        val best = pool.minOf { it.standardErrorPercent }
        val floor = best * sqrt(ASSUMED_CORRELATION)
        val error = maxOf(independentError, floor)

        val spread = pool.maxOf { it.percent } - pool.minOf { it.percent }

        return FusedEstimate(
            combined = BodyFatEstimate(
                percent = mean,
                // Reported as whichever method carried the most weight, because the trend
                // engine keys its repeatability off the method and needs a real one.
                method = pool.minByOrNull { it.standardErrorPercent }!!.method,
                standardErrorPercent = error,
            ),
            contributors = pool.sortedBy { it.standardErrorPercent }.map { it.method },
            disagreementPoints = spread,
            methodsDisagree = abs(spread) > DISAGREEMENT_SIGMA * error,
        )
    }
}
