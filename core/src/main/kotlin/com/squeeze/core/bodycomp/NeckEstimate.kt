package com.squeeze.core.bodycomp

import com.squeeze.core.model.Sex

/**
 * A neck circumference the scan could not measure, inferred instead.
 *
 * @param centimetres the estimate
 * @param standardErrorCm one standard deviation of the population scatter around it. Carried
 *   rather than dropped because on the Navy equation this is not a rounding detail: see
 *   [NeckEstimator.BODY_FAT_POINTS_PER_CM].
 */
data class NeckEstimate(
    val centimetres: Double,
    val standardErrorCm: Double,
)

/**
 * Infers a neck circumference from height, weight and sex.
 *
 * The neck is the site a silhouette scan gets wrong most often, and it is also the one the
 * Navy equation is most sensitive to, so a scan that finds every other site and misses this
 * one produces nothing at all. That is a poor trade when the neck is, of all the sites, the
 * least variable between people of the same size — it carries little fat and changes slowly.
 *
 * **Only height and weight are used, and that restriction is the whole point.**
 *
 * It is tempting to derive the neck from the chest, since the two correlate well and the
 * scan usually has a chest. It is also exactly wrong. A photo scan's errors are dominated by
 * scale, which multiplies every circumference by the same factor, so a neck derived from a
 * scanned chest inherits that factor and lands wrong in the same direction. On a scan whose
 * chest read 123 cm instead of 95, a chest-derived neck comes out near 47 cm — and the Navy
 * equation, fed a waist and a neck inflated together, returns a number that looks reasonable
 * and is not.
 *
 * Height and weight come from the user, not from the photograph. They cannot inherit the
 * scan's error, which makes them the only safe basis for a value the scan will be checked
 * against. See [com.squeeze.core.scan.WeightScaleCheck], which exploits the same asymmetry
 * in the opposite direction.
 */
object NeckEstimator {

    /**
     * Neck circumference as a fraction of stature, at a mid-range body weight.
     *
     * The neck scales with the skeleton more than with anything else, which is why stature
     * carries most of the estimate and body mass only adjusts it.
     */
    private const val MALE_HEIGHT_COEFFICIENT = 0.215
    private const val FEMALE_HEIGHT_COEFFICIENT = 0.195

    /**
     * Centimetres of neck per unit of BMI above [REFERENCE_BMI].
     *
     * Read off the group means in the neck-circumference screening literature, where adults
     * in the normal, overweight and obese BMI bands differ by roughly two and a half
     * centimetres per band. Modest, because the neck is not a fat depot in the way the waist
     * is — which is precisely what makes it a useful denominator for the waist.
     */
    private const val MALE_BMI_COEFFICIENT = 0.55
    private const val FEMALE_BMI_COEFFICIENT = 0.47

    /** BMI at which the stature term alone gives the answer. */
    private const val REFERENCE_BMI = 23.0

    /**
     * Population scatter around the estimate, one standard deviation.
     *
     * Deliberately not shaved down. Two people of identical height and weight genuinely
     * differ here by this much, and no amount of arithmetic on the other numbers recovers
     * which of the two the user is.
     */
    private const val STANDARD_ERROR_CM = 2.2

    /**
     * Roughly how much a body-fat estimate moves per centimetre of neck error.
     *
     * Worth stating as a constant because it is the number that decides how this estimate may
     * be presented. On a lean adult, one centimetre of neck is close to a full point of body
     * fat, so [STANDARD_ERROR_CM] of scatter is worth about two and a half points — several
     * times the repeatability the app claims for a real measurement.
     *
     * An estimated neck therefore produces an *indication*, never a tracked figure. A trend
     * built from it would be a trend in the user's weight, wearing a body-fat label.
     */
    const val BODY_FAT_POINTS_PER_CM = 0.9

    /**
     * @return the estimate, or null when height or weight is missing or implausible. Null
     *   rather than a default: the reason the neck is being estimated at all is that a
     *   fabricated one already cost this app a scan.
     */
    fun estimate(heightCm: Double, weightKg: Double?, sex: Sex): NeckEstimate? {
        if (weightKg == null) return null
        if (heightCm !in 100.0..250.0 || weightKg !in 30.0..300.0) return null

        val metres = heightCm / 100.0
        val bmi = weightKg / (metres * metres)

        val heightCoefficient =
            if (sex == Sex.FEMALE) FEMALE_HEIGHT_COEFFICIENT else MALE_HEIGHT_COEFFICIENT
        val bmiCoefficient =
            if (sex == Sex.FEMALE) FEMALE_BMI_COEFFICIENT else MALE_BMI_COEFFICIENT

        val centimetres = heightCoefficient * heightCm + bmiCoefficient * (bmi - REFERENCE_BMI)

        return NeckEstimate(centimetres, STANDARD_ERROR_CM)
    }
}
