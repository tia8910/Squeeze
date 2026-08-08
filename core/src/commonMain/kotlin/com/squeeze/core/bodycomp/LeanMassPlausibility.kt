package com.squeeze.core.bodycomp

import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex

/**
 * What a body fat percentage implies about the lean mass underneath it, and when that implied
 * lean mass is not a person.
 *
 * Every estimate in this app measures fat. None of them looks at what is left, which is how a
 * scan of a normally-built man came back at 36.6% — from a weight of 68 kg at 1.75 m that is
 * 43.1 kg of fat-free mass, a fat-free mass index of 14.1. FFMI 14 is a frail elderly patient
 * or someone recovering from a long illness. It is not a man with visible abdominal
 * definition, and the app had already worked the number out and printed it two rows below the
 * figure it contradicted.
 *
 * The check is worth having because FFMI has a far narrower range than body fat does. Adult
 * body fat spans 4% to 50%, so almost no reading is impossible on its own. Fat-free mass
 * index spans about ten units across everyone from the untrained to the drug-free elite, and
 * that tight range is exactly what makes it a constraint: given a height and a weight, most
 * of the body-fat axis implies an FFMI nobody has, and can be ruled out without knowing
 * anything about the person beyond those two numbers.
 *
 * That independence is the point. Every failure this pipeline has had — a clipped arm, a
 * waist read at the rib notch, a hip band landing on a shadow — corrupts the silhouette. This
 * check never looks at a silhouette, so it survives all of them.
 *
 * It is a gate and not a correction. Passing it does not make an estimate right; failing it
 * makes an estimate impossible, which is a much stronger statement and the only one being
 * made here.
 */
object LeanMassPlausibility {

    /**
     * The least fat-free mass index seen in an ambulatory adult who is not ill.
     *
     * Kyle et al. (2001) measured FFMI by bioimpedance in 5,635 healthy Swiss adults: the
     * fifth percentile sits near 16.7 for men and 13.9 for women, and the values below that
     * come from wasting rather than from being lightly built. Set a little under the fifth
     * percentile, because the aim is to rule out the impossible rather than the unusual —
     * a gate that fires on a genuinely slight person is worse than one that misses a few
     * bad scans.
     */
    private const val MIN_FFMI_MALE = 16.0
    private const val MIN_FFMI_FEMALE = 13.0

    /**
     * The most fat-free mass index seen without pharmacological help.
     *
     * Kouri et al. (1995) put the drug-free ceiling near 25 for men, and the app's own
     * reference bands already say so. Set a point above it: this end of the gate exists to
     * catch a scan reading someone as near-zero body fat, not to adjudicate anyone's
     * training history.
     */
    private const val MAX_FFMI_MALE = 26.0
    private const val MAX_FFMI_FEMALE = 22.0

    /**
     * How wide the interval becomes when an estimate has to be moved to a bound.
     *
     * Deliberately large. A clamped figure is not a measurement of this body — it is the
     * closest thing to a measurement that is not physically ruled out, which is a much weaker
     * claim and has to be shown as one. Anything narrower would let a bound outvote a real
     * method in the fusion, and a bound knows nothing about the person.
     */
    const val BOUND_ERROR_PERCENT = 8.0

    private fun minFfmi(sex: Sex) = if (sex == Sex.MALE) MIN_FFMI_MALE else MIN_FFMI_FEMALE

    private fun maxFfmi(sex: Sex) = if (sex == Sex.MALE) MAX_FFMI_MALE else MAX_FFMI_FEMALE

    /**
     * The body fat percentages this height and weight can physically carry.
     *
     * Inverts `FFMI = weight × (1 − fat/100) / height²` at each end. A high FFMI bound gives
     * the *lower* body-fat bound and vice versa, because for a fixed weight every kilogram
     * that is not fat is lean.
     *
     * @return null when there is no weight to reason from, or when the bounds cross — which
     *   happens for a genuinely tiny weight at a tall stature, where no body fat percentage
     *   produces a plausible lean mass and the right move is to conclude nothing rather than
     *   to clamp everything to a boundary the person is nowhere near.
     */
    fun plausibleRange(profile: Profile, weightKg: Double?): ClosedFloatingPointRange<Double>? {
        if (weightKg == null || weightKg <= 0.0) return null

        val heightM = profile.heightCm / 100.0
        if (heightM <= 0.0) return null
        val heightSquared = heightM * heightM

        // fat% = 100 × (1 − FFMI × h² / weight)
        val lower = 100.0 * (1.0 - maxFfmi(profile.sex) * heightSquared / weightKg)
        val upper = 100.0 * (1.0 - minFfmi(profile.sex) * heightSquared / weightKg)

        // Never wider than the body-fat axis itself, so the gate can only ever remove
        // possibilities the physiology removed.
        val floor = lower.coerceAtLeast(ABSOLUTE_MIN_PERCENT)
        val ceiling = upper.coerceAtMost(ABSOLUTE_MAX_PERCENT)

        return if (floor <= ceiling) floor..ceiling else null
    }

    /** Essential fat: nobody is below this and alive. */
    private const val ABSOLUTE_MIN_PERCENT = 3.0

    /** Beyond the range any of this app's equations were fitted on. */
    private const val ABSOLUTE_MAX_PERCENT = 60.0

    /** Whether [percent] implies a fat-free mass a person could actually have. */
    fun isPlausible(percent: Double, profile: Profile, weightKg: Double?): Boolean {
        val range = plausibleRange(profile, weightKg) ?: return true
        return percent in range
    }

    /**
     * Applies the gate to a pool of candidate estimates.
     *
     * Implausible candidates are dropped rather than down-weighted, which is how every other
     * failed plausibility check in this codebase behaves: leaving a known-impossible estimate
     * in an inverse-variance fusion does not hedge the answer, it drags it.
     *
     * When every candidate fails, none is dropped — instead the caller is told the pool is
     * unusable and should fall back to [clampToRange] on whatever the fusion produced. An
     * empty pool would show the user nothing at all, and "we cannot say" is the wrong answer
     * when "no more than 28%" is available and true.
     *
     * @return the surviving candidates, or the original list when nothing survives
     */
    fun filter(
        candidates: List<BodyFatEstimate>,
        profile: Profile,
        weightKg: Double?,
    ): List<BodyFatEstimate> {
        val range = plausibleRange(profile, weightKg) ?: return candidates
        val survivors = candidates.filter { it.percent in range }
        return survivors.ifEmpty { candidates }
    }

    /**
     * Moves an estimate onto the nearest bound when it lies outside what the body can carry,
     * and widens its interval to say that it was moved.
     *
     * Returns the estimate untouched when it is already plausible, so this is safe to apply
     * unconditionally at the end of a fusion.
     */
    fun clampToRange(
        estimate: BodyFatEstimate,
        profile: Profile,
        weightKg: Double?,
    ): BodyFatEstimate {
        val range = plausibleRange(profile, weightKg) ?: return estimate
        if (estimate.percent in range) return estimate

        return estimate.copy(
            percent = estimate.percent.coerceIn(range),
            standardErrorPercent = maxOf(estimate.standardErrorPercent, BOUND_ERROR_PERCENT),
        )
    }
}
