package com.squeeze.core.bodycomp

import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.text.fixed
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A paired observation: what an equation predicted, and what a reference scan actually measured.
 */
data class CalibrationPoint(
    val estimatedPercent: Double,
    val referencePercent: Double,
)

/**
 * Corrects a population-derived equation into a personal one.
 *
 * Every equation in [BodyFatCalculator] was fitted to a cohort, so for any individual it
 * carries a systematic offset — often 2-4 percentage points — that stays roughly constant
 * across their range. That offset is invisible without a reference measurement, but once
 * the user enters even one DEXA or BodPod result it can be removed, which collapses
 * absolute error towards the reference method's own precision.
 *
 * This is the app's main accuracy claim, and it is deliberately conservative:
 *
 *  - one reference point yields a constant offset only
 *  - three or more yield a linear fit, but only if the fitted slope is physically sensible
 *  - a nonsensical fit silently degrades to the offset model rather than being applied
 *
 * The residual standard error is reported back so the UI can widen the confidence band
 * when the calibration itself is uncertain.
 */
class PersonalCalibration private constructor(
    private val slope: Double,
    private val intercept: Double,
    val pointCount: Int,
    val residualStandardError: Double?,
) {

    /** True when this calibration does anything at all. */
    val isActive: Boolean get() = pointCount > 0

    /**
     * Applies the correction, widening the reported error to include calibration uncertainty.
     *
     * Errors are combined in quadrature: an equation's own scatter and the calibration's
     * residual scatter are independent sources, so their variances add.
     */
    fun apply(estimate: BodyFatEstimate): BodyFatEstimate {
        if (!isActive) return estimate

        val corrected = (slope * estimate.percent + intercept).coerceIn(2.0, 70.0)
        val combinedError = residualStandardError
            ?.let { sqrt(it * it + REFERENCE_SCAN_ERROR * REFERENCE_SCAN_ERROR) }
            ?: REFERENCE_SCAN_ERROR

        return estimate.copy(
            percent = corrected,
            // A calibrated estimate can never claim to be better than the scan that anchored it.
            standardErrorPercent = minOf(estimate.standardErrorPercent, combinedError),
            calibrated = true,
        )
    }

    companion object {
        /** Typical standard error of a DEXA scan against itself across machines. */
        const val REFERENCE_SCAN_ERROR = 1.5

        /** A personal correction should rescale an equation, not invert or explode it. */
        private val PLAUSIBLE_SLOPE = 0.5..1.5

        /** Identity calibration, used until the user enters a reference scan. */
        fun none(): PersonalCalibration = PersonalCalibration(1.0, 0.0, 0, null)

        /**
         * Fits a calibration from paired estimate/reference observations.
         *
         * Points should all come from the same [com.squeeze.core.model.EstimationMethod];
         * mixing methods fits the difference between equations rather than the user's
         * personal offset. Callers are responsible for grouping.
         */
        fun fit(points: List<CalibrationPoint>): PersonalCalibration {
            if (points.isEmpty()) return none()

            // With one or two points a slope is not identifiable in any meaningful way,
            // so correct the mean and leave the scale alone.
            if (points.size < MIN_POINTS_FOR_SLOPE) {
                val offset = points.map { it.referencePercent - it.estimatedPercent }.average()
                return PersonalCalibration(
                    slope = 1.0,
                    intercept = offset,
                    pointCount = points.size,
                    residualStandardError = null,
                )
            }

            val meanX = points.map { it.estimatedPercent }.average()
            val meanY = points.map { it.referencePercent }.average()

            var sxx = 0.0
            var sxy = 0.0
            for (p in points) {
                val dx = p.estimatedPercent - meanX
                sxx += dx * dx
                sxy += dx * (p.referencePercent - meanY)
            }

            // Degenerate when every scan happened at the same estimated body fat, which is
            // common for someone whose composition is stable. Offset is all we can learn.
            if (sxx < VARIANCE_EPSILON) {
                return PersonalCalibration(1.0, meanY - meanX, points.size, null)
            }

            val slope = sxy / sxx
            val intercept = meanY - slope * meanX

            if (slope !in PLAUSIBLE_SLOPE) {
                // Almost always means the reference scans disagree with each other, not that
                // the user's body scales strangely. Fall back rather than amplify noise.
                return PersonalCalibration(1.0, meanY - meanX, points.size, null)
            }

            // Residual standard error, with the two fitted parameters costing two degrees of freedom.
            val residualSumSquares = points.sumOf { p ->
                val predicted = slope * p.estimatedPercent + intercept
                val residual = p.referencePercent - predicted
                residual * residual
            }
            val dof = points.size - 2
            val rse = if (dof > 0) sqrt(residualSumSquares / dof) else null

            return PersonalCalibration(slope, intercept, points.size, rse)
        }

        private const val MIN_POINTS_FOR_SLOPE = 3
        private const val VARIANCE_EPSILON = 1e-6
    }

    override fun toString(): String = if (!isActive) {
        "PersonalCalibration(none)"
    } else if (abs(slope - 1.0) < 1e-9) {
        "PersonalCalibration(offset=${intercept.fixed(2)}, n=$pointCount)"
    } else {
        "PersonalCalibration(slope=${slope.fixed(3)}, " +
            "intercept=${intercept.fixed(2)}, n=$pointCount)"
    }
}
