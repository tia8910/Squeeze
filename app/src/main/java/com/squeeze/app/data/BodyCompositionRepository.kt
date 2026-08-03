package com.squeeze.app.data

import com.squeeze.app.data.db.MeasurementDao
import com.squeeze.app.data.db.MeasurementEntity
import com.squeeze.core.bodycomp.BodyFatCalculator
import com.squeeze.core.bodycomp.CalibrationPoint
import com.squeeze.core.bodycomp.PersonalCalibration
import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.MeasurementSource
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Skinfolds
import com.squeeze.core.trend.Observation
import com.squeeze.core.trend.Repeatability
import com.squeeze.core.trend.RepeatabilityScore
import com.squeeze.core.trend.TrendEngine
import com.squeeze.core.trend.TrendPoint
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the dashboard needs about the user's composition, in one place.
 *
 * @param bodyFatTrend filtered body fat over time
 * @param leanMassTrend filtered fat-free mass in kg, present only when weight was recorded
 * @param repeatability how precisely the user reproduces their own measurement, null until
 *   they have taken repeat measurements in a single session
 * @param calibration the personal correction currently in force
 */
data class CompositionSnapshot(
    val bodyFatTrend: List<TrendPoint>,
    val leanMassTrend: List<TrendPoint>,
    val repeatability: RepeatabilityScore?,
    val calibration: PersonalCalibration,
) {
    val latest: TrendPoint? get() = bodyFatTrend.lastOrNull()
}

/**
 * Turns stored measurements into calibrated, filtered body composition.
 *
 * The order of operations matters and is the reason this lives in one class rather than
 * being spread across view models:
 *
 *  1. estimate body fat from each measurement using the best equation its inputs support
 *  2. apply personal calibration, fitted from whatever reference scans exist
 *  3. filter the calibrated series for trend, weighting by each method's *precision*
 *
 * Calibrating before filtering is deliberate. Calibration removes a systematic offset that
 * can differ between methods, so applying it first is what makes a tape reading and a
 * photo estimate comparable enough to sit on the same trend line.
 */
@Singleton
class BodyCompositionRepository @Inject constructor(
    private val measurementDao: MeasurementDao,
) {

    private val trendEngine = TrendEngine()

    suspend fun snapshot(profile: Profile): CompositionSnapshot {
        val measurements = measurementDao.since(EPOCH_DAY_ALL)
        val calibration = fitCalibration(profile, measurements)

        val estimates = measurements.mapNotNull { entity ->
            val estimate = estimate(profile, entity) ?: return@mapNotNull null
            entity.epochDay to calibration.apply(estimate)
        }

        // Trend uses repeatability, not standard error: a systematic offset shifts the whole
        // series equally and so does not obscure its shape. See EstimationMethod.
        val fatObservations = estimates.map { (day, estimate) ->
            Observation(
                epochDay = day,
                value = estimate.percent,
                standardError = estimate.method.repeatabilityPercent,
            )
        }

        val leanObservations = measurements.mapNotNull { entity ->
            val weight = entity.weightKg ?: return@mapNotNull null
            val estimate = estimates.firstOrNull { it.first == entity.epochDay }?.second
                ?: return@mapNotNull null
            val partition = BodyFatCalculator.partition(weight, estimate.percent)
            Observation(
                epochDay = entity.epochDay,
                value = partition.leanMassKg,
                // Lean mass inherits uncertainty from the body fat estimate it was derived
                // from, scaled by bodyweight to convert percentage points into kilograms.
                standardError = weight * estimate.method.repeatabilityPercent / 100.0,
            )
        }

        return CompositionSnapshot(
            bodyFatTrend = trendEngine.filter(fatObservations),
            leanMassTrend = TrendEngine(TrendEngine.BODYWEIGHT_PROCESS_NOISE).filter(leanObservations),
            repeatability = Repeatability.score(fatObservations),
            calibration = calibration,
        )
    }

    /**
     * Picks the strongest equation the measurement's inputs support.
     *
     * Skinfolds beat circumferences when present, and both beat the BMI fallback. The
     * fallback is only reached when the user has recorded nothing but a weight, and it is
     * labelled as an estimate in the UI precisely because it cannot see muscle.
     */
    private fun estimate(profile: Profile, entity: MeasurementEntity): BodyFatEstimate? {
        val age = profile.ageAt(LocalDate.now().year)

        entity.referenceBodyFatPercent?.let { reference ->
            return BodyFatEstimate(
                percent = reference,
                method = EstimationMethod.REFERENCE_SCAN,
                standardErrorPercent = EstimationMethod.REFERENCE_SCAN.standardErrorPercent,
            )
        }

        BodyFatCalculator.jacksonPollock3(profile, entity.toSkinfolds(), age)?.let { return it }

        BodyFatCalculator.navy(profile, entity.toCircumferences())?.let { navy ->
            // A photo-derived circumference set runs the same equation but scatters more,
            // because scale recovery adds error the tape does not have.
            return when (entity.source) {
                MeasurementSource.PHOTO.name -> navy.copy(
                    method = EstimationMethod.PHOTO_SILHOUETTE,
                    standardErrorPercent = EstimationMethod.PHOTO_SILHOUETTE.standardErrorPercent,
                )

                MeasurementSource.PHOTO_FRONT_ONLY.name -> navy.copy(
                    method = EstimationMethod.PHOTO_FRONT_ONLY,
                    standardErrorPercent = EstimationMethod.PHOTO_FRONT_ONLY.standardErrorPercent,
                )

                else -> navy
            }
        }

        return entity.weightKg?.let { BodyFatCalculator.deurenbergBmi(profile, it, age) }
    }

    /**
     * Fits calibration from reference scans paired with what the equations said at the time.
     *
     * Points are grouped by method: a scan tells you how far *that* equation sits from the
     * truth for this person, and pooling methods would fit the difference between equations
     * instead. In practice tape is the dominant method, so this is usually a single group.
     */
    private fun fitCalibration(
        profile: Profile,
        measurements: List<MeasurementEntity>,
    ): PersonalCalibration {
        val scans = measurements.filter { it.referenceBodyFatPercent != null }
        if (scans.isEmpty()) return PersonalCalibration.none()

        val points = scans.mapNotNull { scan ->
            val reference = scan.referenceBodyFatPercent ?: return@mapNotNull null

            // The equation's reading on the same day as the scan. Anything further away is
            // comparing two different bodies, so a scan with no nearby measurement is skipped.
            val sameDay = measurements.firstOrNull {
                it.referenceBodyFatPercent == null &&
                    kotlin.math.abs(it.epochDay - scan.epochDay) <= SCAN_PAIRING_WINDOW_DAYS
            } ?: return@mapNotNull null

            val estimated = estimate(profile, sameDay) ?: return@mapNotNull null
            if (estimated.method != EstimationMethod.NAVY_CIRCUMFERENCE) return@mapNotNull null

            CalibrationPoint(estimatedPercent = estimated.percent, referencePercent = reference)
        }

        return PersonalCalibration.fit(points)
    }

    suspend fun insert(entity: MeasurementEntity): Long = measurementDao.insert(entity)

    private companion object {
        const val EPOCH_DAY_ALL = Long.MIN_VALUE

        /** A scan and a tape reading within three days describe the same body. */
        const val SCAN_PAIRING_WINDOW_DAYS = 3L
    }
}

private fun MeasurementEntity.toCircumferences() = Circumferences(
    neckCm = neckCm,
    waistCm = waistCm,
    hipCm = hipCm,
    chestCm = chestCm,
    thighCm = thighCm,
    armCm = armCm,
    calfCm = calfCm,
)

private fun MeasurementEntity.toSkinfolds() = Skinfolds(
    chestMm = chestMm,
    abdomenMm = abdomenMm,
    thighMm = thighMm,
    tricepsMm = tricepsMm,
    suprailiacMm = suprailiacMm,
)
