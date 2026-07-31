package com.squeeze.core.bodycomp

import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.EstimationMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PersonalCalibrationTest {

    private fun estimate(percent: Double) = BodyFatEstimate(
        percent = percent,
        method = EstimationMethod.NAVY_CIRCUMFERENCE,
        standardErrorPercent = EstimationMethod.NAVY_CIRCUMFERENCE.standardErrorPercent,
    )

    @Test
    fun `no calibration leaves the estimate untouched`() {
        val calibration = PersonalCalibration.none()
        val input = estimate(18.0)
        val output = calibration.apply(input)

        assertFalse(calibration.isActive)
        assertEquals(input, output)
    }

    @Test
    fun `a single reference scan applies a constant offset`() {
        // The equation reads 18% but DEXA says 21%: a 3 point personal offset.
        val calibration = PersonalCalibration.fit(
            listOf(CalibrationPoint(estimatedPercent = 18.0, referencePercent = 21.0)),
        )

        assertTrue(calibration.isActive)
        assertEquals(1, calibration.pointCount)
        assertEquals(21.0, calibration.apply(estimate(18.0)).percent, 1e-9)
        // The offset carries to other readings unchanged, since slope is not identifiable.
        assertEquals(18.0, calibration.apply(estimate(15.0)).percent, 1e-9)
    }

    @Test
    fun `calibration marks the estimate and tightens its error`() {
        val calibration = PersonalCalibration.fit(
            listOf(CalibrationPoint(20.0, 22.0)),
        )
        val result = calibration.apply(estimate(20.0))

        assertTrue(result.calibrated)
        assertTrue(
            result.standardErrorPercent < EstimationMethod.NAVY_CIRCUMFERENCE.standardErrorPercent,
            "calibrated estimates should report tighter error, got ${result.standardErrorPercent}",
        )
    }

    @Test
    fun `three consistent points recover a linear relationship`() {
        // Reference sits consistently 2 points above the estimate across the range.
        val calibration = PersonalCalibration.fit(
            listOf(
                CalibrationPoint(12.0, 14.0),
                CalibrationPoint(16.0, 18.0),
                CalibrationPoint(20.0, 22.0),
            ),
        )

        assertEquals(3, calibration.pointCount)
        assertEquals(16.0, calibration.apply(estimate(14.0)).percent, 0.01)
        assertEquals(26.0, calibration.apply(estimate(24.0)).percent, 0.01)
    }

    @Test
    fun `a genuine scale difference is recovered as slope`() {
        // Reference changes twice as fast as the estimate over the observed range, but a
        // slope of 2.0 is outside the plausible band, so the fit must degrade to an offset
        // rather than amplify the estimate.
        val calibration = PersonalCalibration.fit(
            listOf(
                CalibrationPoint(10.0, 10.0),
                CalibrationPoint(15.0, 20.0),
                CalibrationPoint(20.0, 30.0),
            ),
        )

        // Mean estimate 15, mean reference 20 -> offset of 5.
        assertEquals(25.0, calibration.apply(estimate(20.0)).percent, 0.01)
    }

    @Test
    fun `scans at an identical estimate fall back to offset without dividing by zero`() {
        val calibration = PersonalCalibration.fit(
            listOf(
                CalibrationPoint(18.0, 20.0),
                CalibrationPoint(18.0, 21.0),
                CalibrationPoint(18.0, 22.0),
            ),
        )

        // Mean reference is 21 against a constant estimate of 18.
        assertEquals(21.0, calibration.apply(estimate(18.0)).percent, 0.01)
    }

    @Test
    fun `residual error is reported when the fit has spare degrees of freedom`() {
        val calibration = PersonalCalibration.fit(
            listOf(
                CalibrationPoint(12.0, 14.2),
                CalibrationPoint(16.0, 17.8),
                CalibrationPoint(20.0, 22.1),
            ),
        )

        val rse = calibration.residualStandardError
        assertNotNull(rse)
        assertTrue(rse > 0.0 && rse < 1.0, "expected small residual error, got $rse")
    }

    @Test
    fun `corrected values stay inside physiological bounds`() {
        // A large negative offset must not drive the result below the survivable floor.
        val calibration = PersonalCalibration.fit(
            listOf(CalibrationPoint(estimatedPercent = 30.0, referencePercent = 5.0)),
        )
        val result = calibration.apply(estimate(6.0))

        assertTrue(result.percent >= 2.0, "clamped to physiological floor, got ${result.percent}")
    }

    @Test
    fun `empty input yields an inactive calibration`() {
        assertFalse(PersonalCalibration.fit(emptyList()).isActive)
    }
}
