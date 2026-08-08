package com.squeeze.core.bodycomp

import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import com.squeeze.core.model.Skinfolds
import kotlin.math.log10

/**
 * Validated body-fat equations.
 *
 * Every function returns null rather than a guess when its required inputs are missing.
 * Silently substituting a weaker equation would make the trend line jump for reasons the
 * user cannot see, which is worse than showing nothing.
 */
object BodyFatCalculator {

    /** Physiologically possible range. Values outside it indicate a mis-entered measurement. */
    private val PLAUSIBLE = 2.0..70.0

    /**
     * Hodgdon-Beckett circumference equations ("US Navy method"), metric form.
     *
     * Men need neck and waist; women additionally need hip. Accuracy degrades for very
     * lean and very obese subjects, and the equations assume the waist is measured at the
     * navel for men and at the narrowest point for women.
     *
     * @return null if a required circumference is absent or the inputs are degenerate
     */
    fun navy(profile: Profile, c: Circumferences): BodyFatEstimate? {
        val neck = c.neckCm ?: return null
        val waist = c.waistCm ?: return null
        val height = profile.heightCm

        val percent = when (profile.sex) {
            Sex.MALE -> {
                // The log argument must be positive; a waist at or below neck circumference
                // is anatomically implausible and would otherwise produce NaN.
                val girth = waist - neck
                if (girth <= 0.0) return null
                495.0 / (1.0324 - 0.19077 * log10(girth) + 0.15456 * log10(height)) - 450.0
            }

            Sex.FEMALE -> {
                val hip = c.hipCm ?: return null
                val girth = waist + hip - neck
                if (girth <= 0.0) return null
                495.0 / (1.29579 - 0.35004 * log10(girth) + 0.22100 * log10(height)) - 450.0
            }
        }

        return percent.takeIf { it in PLAUSIBLE }?.let {
            BodyFatEstimate(it, EstimationMethod.NAVY_CIRCUMFERENCE, EstimationMethod.NAVY_CIRCUMFERENCE.standardErrorPercent)
        }
    }

    /**
     * Jackson-Pollock 3-site skinfolds, converted to body fat with the Siri equation.
     *
     * Men use chest, abdomen and thigh; women use triceps, suprailiac and thigh. This is
     * the most accurate field method available but depends heavily on caliper technique,
     * so the app should only offer it once the user opts into it explicitly.
     *
     * @param age chronological age in years, required by both equations
     */
    fun jacksonPollock3(profile: Profile, s: Skinfolds, age: Int): BodyFatEstimate? {
        val sum = when (profile.sex) {
            Sex.MALE -> {
                val chest = s.chestMm ?: return null
                val abdomen = s.abdomenMm ?: return null
                val thigh = s.thighMm ?: return null
                chest + abdomen + thigh
            }

            Sex.FEMALE -> {
                val triceps = s.tricepsMm ?: return null
                val suprailiac = s.suprailiacMm ?: return null
                val thigh = s.thighMm ?: return null
                triceps + suprailiac + thigh
            }
        }
        if (sum <= 0.0) return null

        // Body density in g/cm^3, then Siri.
        val density = when (profile.sex) {
            Sex.MALE ->
                1.10938 - 0.0008267 * sum + 0.0000016 * sum * sum - 0.0002574 * age

            Sex.FEMALE ->
                1.0994921 - 0.0009929 * sum + 0.0000023 * sum * sum - 0.0001392 * age
        }
        if (density <= 0.0) return null

        val percent = siri(density)
        return percent.takeIf { it in PLAUSIBLE }?.let {
            BodyFatEstimate(it, EstimationMethod.JACKSON_POLLOCK_3, EstimationMethod.JACKSON_POLLOCK_3.standardErrorPercent)
        }
    }

    /**
     * Deurenberg BMI-based estimate.
     *
     * This is the weakest supported method: BMI cannot distinguish muscle from fat, so it
     * badly overestimates for trained lifters, who are exactly this app's users. It exists
     * only as a first-run placeholder before the user has measured anything, and the UI
     * must label it as such.
     */
    fun deurenbergBmi(profile: Profile, weightKg: Double, age: Int): BodyFatEstimate? {
        if (weightKg <= 0.0) return null
        val heightM = profile.heightCm / 100.0
        val bmi = weightKg / (heightM * heightM)
        val sexTerm = if (profile.sex == Sex.MALE) 1.0 else 0.0
        val percent = 1.20 * bmi + 0.23 * age - 10.8 * sexTerm - 5.4

        return percent.takeIf { it in PLAUSIBLE }?.let {
            BodyFatEstimate(it, EstimationMethod.DEURENBERG_BMI, EstimationMethod.DEURENBERG_BMI.standardErrorPercent)
        }
    }

    /** Siri equation: converts body density to fat percentage. */
    fun siri(densityGPerCm3: Double): Double = 495.0 / densityGPerCm3 - 450.0

    /**
     * Fat mass and fat-free mass in kilograms.
     *
     * Fat-free mass is the number that actually matters when cutting: the goal is losing
     * [fatMassKg] while holding [leanMassKg] flat.
     */
    fun partition(weightKg: Double, bodyFatPercent: Double): MassPartition {
        val fat = weightKg * bodyFatPercent / 100.0
        return MassPartition(fatMassKg = fat, leanMassKg = weightKg - fat)
    }
}

data class MassPartition(val fatMassKg: Double, val leanMassKg: Double)
