package com.squeeze.core.bodycomp

import com.squeeze.core.model.Profile
import com.squeeze.core.scan.SilhouetteBodyFat

/**
 * What a photo scan may say when the outline has admitted it cannot tell — and what it may not.
 *
 * [SilhouetteBodyFat] stops extrapolating below its plateau and returns the leanest figure the
 * outline could support: 11.6% for a man, 21.2% for a woman, carrying ±9. That is a bound, not
 * an estimate. It says "no leaner than this, and I cannot say how much fatter", and it is the
 * same number for every body that lands there — because on the plateau the outline carries no
 * information about adiposity, which is what "plateau" means.
 *
 * This file used to answer that by substituting a figure from height, weight, age and sex. The
 * argument was that when one instrument says nothing, the answer is the instrument that says
 * something rather than that instrument's floor, and every input to Deurenberg is measured to a
 * precision no silhouette approaches. It shipped, and for 68 kg at 1.75 m it produced 16.5%
 * where the outline had been saying 11.6% — closer to what a coach standing in front of that
 * body says, which is why it looked right.
 *
 * **It was a constant too.** Deurenberg runs on height, weight, age and sex, so for one person
 * at one weight it returns exactly one number regardless of what was photographed. Three
 * photographs of the same man at 70 kg and 1.75 m — soft in loose trousers, a mirror selfie in
 * the middle, and one with visible abdominal separation and vascular forearms — all came back
 * **17.3%**. Not approximately: identically, because all four inputs were identical. The trade
 * had swapped the method's constant for the body's constant, which made the figure personal
 * without making it a measurement.
 *
 * And it hid the swap. The substituted value was written to the row under
 * [com.squeeze.core.model.EstimationMethod.PHOTO_SHAPE], so [MethodFusion]'s rule that the BMI
 * fallback is never a peer — the rule written precisely to stop a number that never looked at
 * the body from outvoting one that did — could not see it. A height-and-weight figure was
 * laundered through a photo method's name and reached the user as "Best estimate".
 *
 * **So a photo scan now reports what the photograph supports.** On the plateau that is the
 * outline's own bound with its own interval: a weaker claim than the substitute, and a true
 * one. What resolves it is another photograph rather than another equation — a side view, which
 * measures abdominal depth against the ribcage and is the one axis a front view is blind to.
 * A tape measurement or a known reference figure resolve it too, and both are measurements of
 * the body.
 *
 * What survives here is [isBounded], which recognises a bound so the rest of the app can refuse
 * to let one veto a real measurement or re-enter a fusion claiming precision it never had.
 * [buildPercent] and [ceiling] survive only to serve it on rows written before intervals were
 * stored. Nothing displays them.
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
     * Whether a stored figure is a bound rather than a measurement of adiposity.
     *
     * Prefers the stored interval, which is what actually distinguishes the two and is written
     * alongside the figure by every scan since this file shipped. Rows recorded before that
     * have no interval, so they fall back to comparing against the bound the same inputs would
     * produce — sound because the code that wrote those rows mapped every unresolved
     * reading onto exactly that value.
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
