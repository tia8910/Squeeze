package com.squeeze.app.data

import com.squeeze.app.data.db.MeasurementDao
import com.squeeze.app.data.db.MeasurementEntity
import com.squeeze.core.bodycomp.BodyFatCalculator
import com.squeeze.core.bodycomp.VisualAssessment
import com.squeeze.core.bodycomp.CalibrationPoint
import com.squeeze.core.bodycomp.MethodFusion
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
            val fitted = calibration[estimate.method] ?: PersonalCalibration.none()
            entity.epochDay to fitted.apply(estimate)
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
            // Surfaced for the UI's "uncalibrated" notice. Any active fit means the user
            // has anchored at least one method, which is what that notice is asking for.
            calibration = calibration.values.firstOrNull { it.isActive }
                ?: PersonalCalibration.none(),
        )
    }

    /**
     * Picks the strongest equation the measurement's inputs support.
     *
     * Skinfolds beat circumferences when present, and both beat the BMI fallback. The
     * fallback is only reached when the user has recorded nothing but a weight, and it is
     * labelled as an estimate in the UI precisely because it cannot see muscle.
     *
     * Public because a single record's analysis needs the estimate for *that* record, not the
     * trend's current level. The trend is a filtered view of every entry and is the right
     * number for "where am I now"; it is the wrong number to print inside a row dated three
     * weeks ago, which should say what that day's measurements said.
     */
    fun estimate(
        profile: Profile,
        entity: MeasurementEntity,
        includeReference: Boolean = true,
    ): BodyFatEstimate? {
        val age = profile.ageAt(LocalDate.now().year)
        val candidates = mutableListOf<BodyFatEstimate>()

        // A reference scan is not an equation applied to this entry, it is a measurement of
        // the thing itself, so it enters the pool at its own much tighter error.
        //
        // Excluded when fitting calibration, and that exclusion is load-bearing: the whole
        // point is to compare what the equations said against what the truth was, and a
        // reference that had already been folded into the estimate would compare the truth
        // against itself and fit an offset of zero.
        entity.referenceBodyFatPercent?.takeIf { includeReference }?.let { reference ->
            candidates += BodyFatEstimate(
                percent = reference,
                method = EstimationMethod.REFERENCE_SCAN,
                standardErrorPercent = EstimationMethod.REFERENCE_SCAN.standardErrorPercent,
            )
        }

        BodyFatCalculator.jacksonPollock3(profile, entity.toSkinfolds(), age)
            ?.let { candidates += it }

        BodyFatCalculator.navy(profile, entity.toCircumferences())?.let { navy ->
            // A photo-derived circumference set runs the same equation but scatters more,
            // because scale recovery adds error the tape does not have.
            candidates += when (entity.source) {
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

        entity.weightKg
            ?.let { BodyFatCalculator.deurenbergBmi(profile, it, age) }
            ?.let { candidates += it }

        // The one candidate that does not run on circumferences. Every other method here
        // shares the scan's scale error, so fusing them alone narrows the interval around a
        // number that may be confidently wrong; appearance cannot inherit that error, which
        // is worth more to the combination than its own modest accuracy.
        entity.visualBodyFatPercent
            ?.let { VisualAssessment.estimate(it, profile.sex) }
            ?.let { candidates += it }

        // Read from the silhouette's proportions rather than from its converted widths, so
        // it is the only photo-derived estimate that scale recovery cannot corrupt. When it
        // disagrees loudly with the circumference route, that disagreement is evidence about
        // the scan rather than noise to be averaged away.
        entity.shapeBodyFatPercent?.let { shape ->
            candidates += BodyFatEstimate(
                percent = shape,
                method = EstimationMethod.PHOTO_SHAPE,
                standardErrorPercent = EstimationMethod.PHOTO_SHAPE.standardErrorPercent,
            )
        }

        // Every method that could run, merged rather than ranked. Two routes to the same
        // quantity constrain it better than the better one alone, and MethodFusion handles
        // the two things that would otherwise make that a false economy: the methods are
        // correlated, and the BMI fallback is not a peer.
        return MethodFusion.combine(candidates)?.combined
    }

    /**
     * Fits calibration from reference scans paired with what the equations said at the time.
     *
     * Points are grouped by method: a scan tells you how far *that* equation sits from the
     * truth for this person, and pooling methods would fit the difference between equations
     * instead. In practice tape is the dominant method, so this is usually a single group.
     */
    /**
     * One calibration per estimation method.
     *
     * A single shared fit would be wrong: a tape reading, a photo scan and a BMI estimate sit
     * at quite different distances from the truth for the same person, and averaging those
     * offsets corrects none of them.
     */
    private fun fitCalibration(
        profile: Profile,
        measurements: List<MeasurementEntity>,
    ): Map<EstimationMethod, PersonalCalibration> {
        val scans = measurements.filter { it.referenceBodyFatPercent != null }
        if (scans.isEmpty()) return emptyMap()

        val points = scans.mapNotNull { scan ->
            val reference = scan.referenceBodyFatPercent ?: return@mapNotNull null

            // The reference may sit on the scan's own row, which is the case that matters:
            // a user who looks at a wrong number and types what they actually are gets their
            // offset fitted from that one action. Failing that, a measurement within a few
            // days describes the same body and will do.
            val paired = scan.takeIf { it.hasMeasurableInputs() }
                ?: measurements.firstOrNull {
                    it.referenceBodyFatPercent == null &&
                        kotlin.math.abs(it.epochDay - scan.epochDay) <= SCAN_PAIRING_WINDOW_DAYS
                }
                ?: return@mapNotNull null

            val estimated = estimate(profile, paired, includeReference = false)
                ?: return@mapNotNull null

            // Previously this accepted only NAVY_CIRCUMFERENCE, which meant a photo scan
            // could never be calibrated at all — the one thing in this app that most needs
            // it, since its offset is the largest and the most person-specific. Every method
            // now gets its own fit, because each carries a different systematic error and
            // correcting a photo scan by a tape-derived offset would be worse than not
            // correcting it.
            estimated.method to
                CalibrationPoint(estimated.percent, referencePercent = reference)
        }

        return points
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, group) -> PersonalCalibration.fit(group) }
    }

    /** Whether the row carries anything an equation can run on, ignoring the reference. */
    private fun MeasurementEntity.hasMeasurableInputs(): Boolean =
        waistCm != null || chestMm != null || weightKg != null || shapeBodyFatPercent != null

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
