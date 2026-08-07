package com.squeeze.core.scan

import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.Sex

/**
 * The abdomen measured side-on, which is the axis abdominal fat actually moves along.
 *
 * **Why the front view kept failing.** Four separate attempts in this project to read
 * adiposity from a front photograph have come back flat or non-monotonic:
 *
 *  - waist-to-shoulder off a labelled chart: 0.586 at 8%, 0.592 at 12%, 0.580 at 15% — no
 *    movement at all across the lean range, and no clean ordering above 25% either
 *  - abdominal texture: 5.76 at 8%, 7.44 at 15%, 6.03 at 20%, 4.51 at 35% — it tracks how
 *    hard the light was, not the body
 *  - belly-to-chest *width* off the same chart: 1.11 at 8% against 1.06 at 35%, with three
 *    of eight reference panels unmeasurable because the arms merged into the trunk
 *
 * The common cause is stated in this codebase's own [DepthRatios]: **abdominal fat
 * accumulates more in depth than in width.** A front photograph measures the coronal axis —
 * the one that moves least. It was never going to work, and no cleverer front-view formula
 * fixes that, because the information is not in the picture.
 *
 * Sagittal abdominal diameter is the axis that does move: roughly 19 cm in a lean adult male
 * and 30 in an obese one. Chest depth over the same range moves very little, which makes it
 * a stable denominator sitting in the same photograph.
 *
 * **The arm problem cannot occur here.** Arms hang at the sides of the body, so edge-on they
 * lie inside the torso's own front-to-back extent rather than extending it. The failure that
 * has dominated this pipeline — an arm merging into the trunk and being measured as part of
 * it — is impossible along this axis. That is not a property of this implementation, it is
 * where arms are.
 *
 * **Scale never enters.** Belly depth over chest depth divides two measurements from one
 * image, exactly as [SilhouetteBodyFat] does across the other axis, so the stature that every
 * centimetre in this app is hostage to is not involved.
 *
 * **What it still cannot do.** The anchors below are reasoned from published sagittal
 * diameters, not fitted to a labelled set — this project has none, and inventing one from
 * eight chart photographs would be worse than admitting it. So the number carries a
 * person-specific offset, and only calibration against a known value removes it. What it does
 * not carry is the flatness: unlike every front-view index tried here, this one moves.
 */
data class AbdominalDepths(
    /** Greatest front-to-back extent of the abdomen, as a fraction of image width. */
    val bellyDepth: Double,
    /** The same at chest height, the stable reference the belly is judged against. */
    val chestDepth: Double,
) {
    /**
     * Belly depth over chest depth.
     *
     * Below one the abdomen sits shallower than the ribcage, which is what a lean torso looks
     * like from the side. Above one it protrudes past it.
     */
    val bellyToChest: Double get() = bellyDepth / chestDepth
}

object AbdominalProfile {

    /**
     * Where the abdomen is, as fractions of the shoulder-to-hip span.
     *
     * Starts below the sternum, because the ribcage has a depth of its own that is skeleton
     * rather than fat, and stops above the waistband. The band is generous at the lower end:
     * on a heavier body the greatest protrusion sits low, near the navel and below it, and a
     * band that stopped at the natural waist would miss precisely the bodies it most needs
     * to separate.
     */
    private const val BELLY_BAND_START = 0.38
    private const val BELLY_BAND_END = 0.88

    /**
     * And where the chest is, on the same scale.
     *
     * Kept clear of the shoulder line, where the deltoid adds depth that is not ribcage.
     */
    private const val CHEST_BAND_START = 0.08
    private const val CHEST_BAND_END = 0.34

    /**
     * Reads both depths from a side-on silhouette.
     *
     * @param profile widths from a **side** photograph, where a row's width is the body's
     *   sagittal depth at that height
     * @return null when the bands are degenerate or the silhouette carries no depth there,
     *   rather than substituting a default — a fabricated depth is indistinguishable from a
     *   measured one once it is a number
     */
    fun depthsFrom(profile: WidthProfile, anchors: PoseAnchors): AbdominalDepths? {
        val trunk = anchors.hipRow - anchors.shoulderRow
        if (trunk <= 0) return null

        fun deepestIn(startFraction: Double, endFraction: Double): Double? {
            val from = anchors.shoulderRow + (trunk * startFraction).toInt()
            val to = anchors.shoulderRow + (trunk * endFraction).toInt()
            return AnatomicalLevelFinder.widestBetween(profile, from, to)
                ?.let { profile.torsoWidthAt(it) }
                ?.takeIf { it > 0.0 }
        }

        val chest = deepestIn(CHEST_BAND_START, CHEST_BAND_END) ?: return null
        val belly = deepestIn(BELLY_BAND_START, BELLY_BAND_END) ?: return null

        return AbdominalDepths(bellyDepth = belly, chestDepth = chest)
    }

    /**
     * Belly-to-chest depth at the lean and heavy anchors, and the body fat each corresponds
     * to.
     *
     * Reasoned from sagittal abdominal diameter against chest depth rather than fitted. A
     * lean adult male carries an abdomen distinctly shallower than his ribcage; by the time
     * the abdomen is half again as deep as the chest, the body fat is in the high thirties.
     *
     * Women sit higher on this axis at the same body fat, because a greater share of female
     * fat is subcutaneous and gluteofemoral rather than visceral, and because the chest
     * denominator itself differs.
     */
    private const val MALE_LEAN_RATIO = 0.82
    private const val MALE_LEAN_PERCENT = 10.0
    private const val MALE_HIGH_RATIO = 1.45
    private const val MALE_HIGH_PERCENT = 38.0

    private const val FEMALE_LEAN_RATIO = 0.86
    private const val FEMALE_LEAN_PERCENT = 18.0
    private const val FEMALE_HIGH_RATIO = 1.42
    private const val FEMALE_HIGH_PERCENT = 45.0

    /**
     * Outside this the silhouette is not a standing human torso seen from the side.
     *
     * An abdomen half the depth of the chest, or two and a half times it, means the bands
     * landed on something other than a body — a chair back, a mirror frame, a subject facing
     * the wrong way — and the right response is no answer rather than an extreme one.
     */
    private val PLAUSIBLE_RATIO = 0.60..2.20

    private const val MIN_PERCENT = 3.0
    private const val MAX_PERCENT = 60.0

    /**
     * @return the estimate, or null when the ratio is outside anything a side-on torso
     *   produces
     */
    fun estimate(depths: AbdominalDepths, sex: Sex): BodyFatEstimate? {
        val ratio = depths.bellyToChest
        if (ratio !in PLAUSIBLE_RATIO) return null

        val female = sex == Sex.FEMALE
        val leanRatio = if (female) FEMALE_LEAN_RATIO else MALE_LEAN_RATIO
        val highRatio = if (female) FEMALE_HIGH_RATIO else MALE_HIGH_RATIO
        val leanPercent = if (female) FEMALE_LEAN_PERCENT else MALE_LEAN_PERCENT
        val highPercent = if (female) FEMALE_HIGH_PERCENT else MALE_HIGH_PERCENT

        // Extrapolated past the anchors rather than clamped, then bounded. Clamping would
        // make every very lean body report exactly the lean anchor, which reads as confidence
        // where there is none — the same defect that made a plateau reading look like a
        // measurement elsewhere in this codebase.
        val t = (ratio - leanRatio) / (highRatio - leanRatio)
        val percent = leanPercent + t * (highPercent - leanPercent)

        return BodyFatEstimate(
            percent = percent.coerceIn(MIN_PERCENT, MAX_PERCENT),
            method = EstimationMethod.PHOTO_ABDOMINAL_PROFILE,
            standardErrorPercent =
                EstimationMethod.PHOTO_ABDOMINAL_PROFILE.standardErrorPercent,
        )
    }
}
