package com.squeeze.core.bodycomp

import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.Profile
import com.squeeze.core.scan.SilhouetteBodyFat

/**
 * What to report when the outline has admitted it cannot tell.
 *
 * [SilhouetteBodyFat] stops extrapolating below its plateau and returns the leanest figure
 * the outline could support — 11.6% for a man, 21.2% for a woman. That fixed a genuine harm:
 * the app no longer tells someone with a soft midsection that they are 4.93%. It did not
 * produce a right answer. A man of 1.75 m and 68 kg with no abdominal definition is nearer
 * eighteen per cent than twelve, and 11.6 was still six points of nonsense, merely a safer
 * kind.
 *
 * The reason it was still wrong is structural, and naming it is the whole of this file. On
 * the plateau the outline carries no information about adiposity — that is what "plateau"
 * means. So the plateau ceiling is not an estimate of the person; it is a property of the
 * *method*, the same number for every body that lands there. Printing a constant as though it
 * were a measurement is the exact failure this codebase keeps rediscovering: an 8.00% reading
 * from a texture score pinned at its lean end, a 36.6% reading nobody sanity-checked against
 * lean mass, and now a plateau ceiling shown as a percentage of a specific man.
 *
 * **When one instrument says nothing, the answer is the instrument that says something — not
 * that instrument's floor.** Height, weight, age and sex are known, measured to a precision no
 * silhouette approaches, and completely untouched by every failure the outline has: a clipped
 * arm, a waistband inside the hip band, a sideways frame, laundry joined to the mask. Through
 * the Deurenberg equation they give a figure for *this* body rather than for this method.
 *
 * For 68 kg at 1.75 m that is a BMI of 22.2 and, at thirty, 17.4% — which is what a coach
 * standing in front of that body says, and six points from what the outline was reporting.
 *
 * **What this is not.** It is not a correction applied to a working measurement. Off the
 * plateau the outline is measuring something real, and BMI is blind to muscle — fold it into
 * a good reading and a trained user is dragged upward toward a known bias, which is precisely
 * why [MethodFusion] excludes [com.squeeze.core.model.EstimationMethod.DEURENBERG_BMI] from
 * any pool containing a measured method. So this substitutes only where there is nothing to
 * damage: readings the outline has already declared uninformative.
 *
 * **The substituted figure keeps the plateau's crippled interval**, and that is load-bearing
 * rather than cautious. Deurenberg's own published error is nearer 4.5 points, so narrowing to
 * it would be defensible on paper and wrong here for two reasons. It was fitted on general
 * adults and overestimates trained ones, who are this app's users; and at ±4.5 a figure
 * derived from height and weight would start outweighing a tape measurement in
 * [MethodFusion]'s inverse-variance pool. Nothing that never looked at the body should ever
 * outvote something that did. Anything between 4.5 and 9 would be a constant picked by
 * argument, which this file has enough of, so the interval that was already there is kept.
 */
object PlateauPrior {

    /**
     * Rounding slack when recognising a figure that was set to a bound.
     *
     * The bound is produced by the same functions that check for it, so the comparison is
     * exact in principle. It is stored as a `REAL` and read back, and a plateau reading that
     * failed to be recognised would silently regain full precision in the fusion, so the
     * check is made robust rather than clever.
     */
    private const val TOLERANCE = 1e-6

    /**
     * Body fat implied by build alone: height, weight, age, sex. Null without a weight.
     *
     * Bounded by [LeanMassPlausibility] before it is returned, so the substitution can never
     * introduce a figure the body could not physically carry — the gate that caught 36.6%
     * applies to this route too, and for the same reason.
     */
    fun buildPercent(profile: Profile, weightKg: Double?, age: Int): Double? {
        val weight = weightKg?.takeIf { it > 0.0 } ?: return null
        val percent = BodyFatCalculator.deurenbergBmi(profile, weight, age)?.percent ?: return null
        val range = LeanMassPlausibility.plausibleRange(profile, weight) ?: return percent
        return percent.coerceIn(range)
    }

    /**
     * The figure a plateau reading resolves to for this body, and the floor under every
     * silhouette reading of it.
     *
     * The larger of the two, never the average. They are not two opinions to be split: the
     * outline's contribution on the plateau is a *bound* — "no leaner than this, and I cannot
     * say how much fatter" — and a bound combined with an estimate is whichever is more
     * restrictive. Taking the maximum also preserves the property
     * [SilhouetteBodyFat.leanestClaimable] exists to guarantee, that no photograph of any body
     * produces a single-digit figure, because it can only ever move a reading upward.
     *
     * Falls back to the outline's own ceiling when there is no weight to reason from, which
     * is the behaviour that shipped before this file existed.
     */
    fun ceiling(profile: Profile, weightKg: Double?, age: Int): Double {
        val outline = SilhouetteBodyFat.leanestClaimable(profile.sex)
        val build = buildPercent(profile, weightKg, age) ?: return outline
        return maxOf(outline, build)
    }

    /**
     * Resolves a fresh silhouette estimate against what the body's build implies.
     *
     * Only readings the outline has already bounded are touched. Those are recognisable
     * without any extra state: [SilhouetteBodyFat] floors every path at
     * [SilhouetteBodyFat.leanestClaimable], so a reading sitting on that value is one the
     * outline could not resolve, and every reading above it is one it could.
     *
     * @return the estimate unchanged when the outline resolved something, the build-implied
     *   figure when it did not, and null for null
     */
    fun resolve(
        estimate: BodyFatEstimate?,
        profile: Profile,
        weightKg: Double?,
        age: Int,
    ): BodyFatEstimate? {
        if (estimate == null) return null
        if (estimate.percent > SilhouetteBodyFat.leanestClaimable(profile.sex) + TOLERANCE) {
            return estimate
        }

        return estimate.copy(
            percent = ceiling(profile, weightKg, age),
            // Never narrower than the plateau's own interval. The point became specific to
            // this body; the amount that was actually measured did not change.
            standardErrorPercent = maxOf(
                estimate.standardErrorPercent,
                SilhouetteBodyFat.PLATEAU_ERROR_PERCENT,
            ),
        )
    }

    /**
     * Whether a stored figure is a bound rather than a measurement of adiposity.
     *
     * Prefers the stored interval, which is what actually distinguishes the two and is written
     * alongside the figure by every scan since this file shipped. Rows recorded before that
     * have no interval, so they fall back to comparing against the bound the same inputs would
     * produce — sound because [resolve] maps every unresolved reading onto exactly that value.
     *
     * Callers use this to decide what a shape figure is allowed to *do*, not what it says: a
     * bound may not veto another method's measurement, and it must re-enter the fusion at the
     * width it was recorded with rather than at [SilhouetteBodyFat]'s ordinary error.
     */
    fun isBounded(
        percent: Double,
        standardErrorPercent: Double?,
        profile: Profile,
        weightKg: Double?,
        age: Int,
    ): Boolean {
        standardErrorPercent?.let {
            return it >= SilhouetteBodyFat.PLATEAU_ERROR_PERCENT - TOLERANCE
        }
        return percent <= ceiling(profile, weightKg, age) + TOLERANCE
    }
}
