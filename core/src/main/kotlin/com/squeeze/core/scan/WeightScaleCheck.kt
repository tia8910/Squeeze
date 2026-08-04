package com.squeeze.core.scan

import com.squeeze.core.model.Circumferences
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * What the scan's own numbers imply the user should weigh, and what that says about scale.
 *
 * @param impliedWeightKg the weight a body with these circumferences would have
 * @param actualWeightKg what the user entered
 * @param correctionFactor multiply every scanned circumference by this to reconcile the two
 * @param significant true when the mismatch is larger than this crude model can explain on
 *   its own, and therefore worth acting on
 */
data class WeightScaleFinding(
    val impliedWeightKg: Double,
    val actualWeightKg: Double,
    val correctionFactor: Double,
    val significant: Boolean,
) {
    /** How far off the scan was, as a percentage of the circumferences it reported. */
    val errorPercent: Double get() = abs(1.0 - correctionFactor) * 100.0
}

/**
 * Catches a wrong scale by asking whether the measured body could weigh what the user does.
 *
 * A photo scan's dominant error multiplies every circumference by the same factor, because
 * they all come from one stature reference. That is exactly what makes it invisible from
 * inside the scan: a waist of 99 cm is perfectly plausible, a chest of 123 cm is plausible,
 * a hip of 107 cm is plausible, and every plausibility gate in this codebase passes them one
 * at a time. The scan reports a coherent, confident set of numbers describing somebody else.
 *
 * Weight breaks that open, and it is the one number in the app measured by an instrument
 * rather than inferred. A body's volume follows from its circumferences and its height, and
 * its mass follows from its volume, because human density sits in a narrow band. So the
 * circumferences predict a weight — and if that prediction misses the bathroom scale by
 * fifty per cent, the circumferences are wrong, whatever each of them looks like alone.
 *
 * The correction is exact rather than fudged, because of how the error propagates.
 * Circumferences scale linearly with the stature reference while segment *lengths* come from
 * the user's real height and do not move at all. Cross-sectional area therefore scales with
 * the square of the error, and so does volume:
 *
 * ```
 *   V(s) = s² · V_measured + V_fixed
 * ```
 *
 * Setting that equal to the volume the user's weight demands and solving for `s` gives the
 * factor directly. `V_fixed` holds any segment whose circumference was inferred from height
 * and weight rather than from the photograph — those did not inherit the scan's error, so
 * they must not be corrected for it.
 *
 * The volume model below is crude, worth perhaps ten to fifteen per cent. That is ample: it
 * is not being asked to measure anything, only to distinguish a body from one half again its
 * size.
 */
object WeightScaleCheck {

    /**
     * Whole-body density in kg per litre.
     *
     * Fat is near 0.90 and fat-free mass near 1.10, so every human body lands between them,
     * and the range that spans a very lean adult to an obese one is only about four per cent
     * either side of this. One of the more reliable numbers in the whole pipeline.
     */
    const val BODY_DENSITY_KG_PER_LITRE = 1.05

    /**
     * Below this the model cannot tell a real mismatch from its own coarseness.
     *
     * Set well above the model's own error so that reporting a correction means something.
     * A scan that is out by eight per cent will not be flagged; that is the right trade,
     * because a false correction is worse than an uncorrected small error — the user has no
     * way to tell which one they are looking at.
     */
    const val SIGNIFICANT_ERROR = 0.15

    /** Segment lengths as fractions of stature, from the standard body-segment tables. */
    private const val TORSO_LENGTH = 0.30
    private const val THIGH_LENGTH = 0.245
    private const val SHANK_LENGTH = 0.246
    private const val ARM_LENGTH = 0.36
    private const val HEAD_NECK_LENGTH = 0.13

    /** Calf circumference as a fraction of thigh, when only the thigh was measured. */
    private const val SHANK_FROM_THIGH = 0.63

    /** Upper-arm circumference as a fraction of chest, when the arm was not measured. */
    private const val ARM_FROM_CHEST = 0.33

    /**
     * Corrects the circular cross-section assumption.
     *
     * Treating a circumference as a circle overstates its area, because real segments are
     * elliptical and taper along their length. Fitted once, against the height, weight and
     * girths of a mid-range adult — a single constant, not a per-site fudge.
     */
    private const val SHAPE_FACTOR = 0.97

    /**
     * @param measured circumferences that came from the photograph, and so share its scale
     * @param inferredNeckCm a neck estimated from height and weight rather than measured.
     *   Held out of the correction because it did not inherit the scan's error.
     * @return null when there is too little to work with — no weight, or no torso girth
     */
    fun evaluate(
        measured: Circumferences,
        heightCm: Double,
        weightKg: Double?,
        inferredNeckCm: Double? = null,
    ): WeightScaleFinding? {
        if (weightKg == null || weightKg <= 0.0) return null
        if (heightCm !in 100.0..250.0) return null

        val scaledVolume = photographedVolume(measured, heightCm)
        if (scaledVolume <= 0.0) return null

        val fixedVolume = inferredNeckCm
            ?.let { cylinder(it, HEAD_NECK_LENGTH * heightCm) }
            ?: 0.0

        // Litres. A cubic centimetre is a millilitre, hence the thousand.
        val targetVolume = weightKg / BODY_DENSITY_KG_PER_LITRE * 1000.0

        val correctable = targetVolume - fixedVolume
        if (correctable <= 0.0) return null

        val correction = sqrt(correctable / scaledVolume)
        val implied = (scaledVolume + fixedVolume) * BODY_DENSITY_KG_PER_LITRE / 1000.0

        return WeightScaleFinding(
            impliedWeightKg = implied,
            actualWeightKg = weightKg,
            correctionFactor = correction,
            significant = abs(1.0 - correction) > SIGNIFICANT_ERROR,
        )
    }

    /** Applies a correction to every circumference that came from the photograph. */
    fun apply(measured: Circumferences, factor: Double): Circumferences = Circumferences(
        neckCm = measured.neckCm?.times(factor),
        waistCm = measured.waistCm?.times(factor),
        hipCm = measured.hipCm?.times(factor),
        chestCm = measured.chestCm?.times(factor),
        thighCm = measured.thighCm?.times(factor),
        armCm = measured.armCm?.times(factor),
        calfCm = measured.calfCm?.times(factor),
    )

    /**
     * Volume in cubic centimetres of everything the photograph measured.
     *
     * Segments whose girth is absent are skipped rather than guessed. That biases the implied
     * weight low, which is the safe direction: it can only ever make the check *less* likely
     * to claim the scan was inflated.
     */
    private fun photographedVolume(c: Circumferences, heightCm: Double): Double {
        var volume = 0.0

        val torsoGirths = listOfNotNull(c.chestCm, c.waistCm, c.hipCm)
        if (torsoGirths.isEmpty()) return 0.0
        volume += cylinder(torsoGirths.average(), TORSO_LENGTH * heightCm)

        c.thighCm?.let { thigh ->
            volume += 2.0 * cylinder(thigh, THIGH_LENGTH * heightCm)
            val shank = c.calfCm ?: (thigh * SHANK_FROM_THIGH)
            volume += 2.0 * cylinder(shank, SHANK_LENGTH * heightCm)
        }

        val arm = c.armCm ?: c.chestCm?.times(ARM_FROM_CHEST)
        arm?.let { volume += 2.0 * cylinder(it, ARM_LENGTH * heightCm) }

        // Only a measured neck belongs here. An inferred one is the caller's to hold fixed.
        c.neckCm?.let { volume += cylinder(it, HEAD_NECK_LENGTH * heightCm) }

        return volume
    }

    /** Volume of a segment of circumference [girthCm] and length [lengthCm]. */
    private fun cylinder(girthCm: Double, lengthCm: Double): Double =
        girthCm * girthCm / (4.0 * PI) * lengthCm * SHAPE_FACTOR
}
