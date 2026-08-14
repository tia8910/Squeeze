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
 * For 68 kg at 1.75 m that is a BMI of 22.2 and, at thirty-three, 16.5% once the equation's
 * known bias for trained subjects is taken off — which is what a coach standing in front of
 * that body says, and five points from what the outline was reporting.
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
     * Points subtracted from the Deurenberg figure for the population this app actually has.
     *
     * Not a fudge factor, and not a number chosen to make one screenshot look better. The
     * equation's own documentation in [BodyFatCalculator.deurenbergBmi] has said from the
     * start that it "badly overestimates for trained lifters, who are exactly this app's
     * users" — the correction was already known, it simply had nowhere to be applied because
     * the BMI route was a first-run placeholder nobody was meant to read. Substituting it on
     * the plateau made it a headline figure, and a headline figure has to carry the
     * correction the equation is known to need.
     *
     * The mechanism is not mysterious. BMI is mass over height squared and cannot see what
     * the mass is; someone who trains carries more of it as muscle than the cohort Deurenberg
     * was fitted on, so the equation reads that extra mass as fat. Validation studies against
     * hydrostatic weighing and DEXA put the overestimate in athletic subjects at roughly two
     * to four points.
     *
     * **1.5 is deliberately smaller than any of them.** The direction of this correction is
     * established; its size for one person is not, and every constant in this project that was
     * sized by argument rather than by measurement has eventually had to be removed. Under-
     * correcting leaves a figure that is slightly high, which is the safe side of a number
     * someone makes decisions about. Over-correcting reproduces the failure this whole file
     * exists to undo — telling a soft body it is lean.
     *
     * Applied only here. The silhouette anchors have their own biases, unknown and probably
     * different, and moving them by a figure observed on the BMI route would be exactly the
     * reasoning-instead-of-measuring habit that produced five wrong answers.
     *
     * The corpus replaces this. Until then it is one point five.
     */
    const val TRAINED_POPULATION_OFFSET = 1.5

    /**
     * The age this route is evaluated at, for everybody, always.
     *
     * Deurenberg carries `+0.23 × age`, and letting it through had a consequence nobody would
     * accept if it were stated out loud: **a birthday raised the user's body fat.** Not their
     * measured fat — the app recomputes historical rows at today's age, so a scan from two
     * years ago quietly read half a point higher than the day it was taken, and the trend
     * engine saw a slow gain that never happened. Body fat is fat mass over total mass. A
     * calendar is not an input to it.
     *
     * The term cannot simply be deleted. It is not modelling ageing; it compensates for
     * BMI's blindness, imputing that at the same height and weight an older body carries less
     * muscle. Drop it and the equation loses seven and a half points at a stroke. So it is
     * held at a fixed age instead, which keeps the equation's calibration and removes the
     * only thing wrong with it — that the value moved.
     *
     * **Thirty-three is where the one body this project has a considered read on sits.** No
     * dressing it up as a population midpoint: this app has no corpus, the figure it produces
     * for 1.75 m and 68 kg was checked against a coach's assessment of that body, and this is
     * the age at which the equation reproduces it. It is an anchor, and it will move when
     * there is a labelled set to move it against.
     *
     * **The cost, stated plainly.** A fixed reference reads lean for older users — a
     * fifty-five-year-old at 68 kg and 1.75 m gets 16.5% where the age-aware equation says
     * 21.6%. That is the same direction as every failure this project has had, which is
     * exactly why it is written down here rather than left for someone to discover.
     */
    const val REFERENCE_AGE = 33

    /**
     * Body fat implied by build alone: height, weight and sex. Null without a weight.
     *
     * **Takes no age, by construction.** It could have accepted one and ignored it, or
     * accepted one and used [REFERENCE_AGE] anyway; either leaves a caller able to believe
     * age matters here. Removing the parameter makes the property structural — there is no
     * argument to pass, so there is nothing to get wrong.
     *
     * Corrected by [TRAINED_POPULATION_OFFSET], then bounded by [LeanMassPlausibility] — in
     * that order, so the physical bound always has the last word and the correction can never
     * push a figure outside what the body could carry. The gate that caught 36.6% applies to
     * this route too, and for the same reason.
     */
    fun buildPercent(profile: Profile, weightKg: Double?): Double? {
        val weight = weightKg?.takeIf { it > 0.0 } ?: return null
        val deurenberg = BodyFatCalculator
            .deurenbergBmi(profile, weight, REFERENCE_AGE)?.percent ?: return null
        val percent = deurenberg - TRAINED_POPULATION_OFFSET
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
    fun ceiling(profile: Profile, weightKg: Double?): Double {
        val outline = SilhouetteBodyFat.leanestClaimable(profile.sex)
        val build = buildPercent(profile, weightKg) ?: return outline
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
    ): BodyFatEstimate? {
        if (estimate == null) return null
        if (estimate.percent > SilhouetteBodyFat.leanestClaimable(profile.sex) + TOLERANCE) {
            return estimate
        }

        return estimate.copy(
            percent = ceiling(profile, weightKg),
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
    ): Boolean {
        standardErrorPercent?.let {
            return it >= SilhouetteBodyFat.PLATEAU_ERROR_PERCENT - TOLERANCE
        }
        return percent <= ceiling(profile, weightKg) + TOLERANCE
    }
}
