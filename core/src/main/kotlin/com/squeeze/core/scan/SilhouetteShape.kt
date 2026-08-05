package com.squeeze.core.scan

import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.Sex

/**
 * Scale-free shape descriptors read straight off the silhouette.
 *
 * Every value here is one silhouette width divided by another, measured in pixels and never
 * converted to centimetres. That is the entire point.
 *
 * The app's conversion to real units is `widthCm = (widthPixels / bodyHeightPixels) × height`.
 * It looks robust, and the arithmetic is, but it rests on `bodyHeightPixels` — the mask's top
 * and bottom rows — which is the least reliable quantity in the pipeline. A mask that catches
 * a mirror frame, loses dark hair against a wall, or merges the feet into a shadow moves it,
 * and every circumference moves with it. That single number is what turned a lean 70 kg body
 * into a set of girths describing someone of 118 kg.
 *
 * A ratio of two widths measured in the same image divides that error out exactly. If the
 * mask is 25% too generous everywhere, both widths are 25% large and the ratio is unchanged.
 * These descriptors cannot be corrupted by scale recovery because they never use it.
 *
 * **Width against width, never width against height.** That restriction is the method, not a
 * simplification of it. Two widths in one image are both fractions of the image's width, so
 * dividing them cancels it exactly and nothing about the mask's vertical extent can reach
 * the result. Comparing a width to the body's *height* looks equally scale-free and is not:
 * it needs the pixel stature, which is precisely the fragile quantity being routed around,
 * and including one here reintroduced the bug this class was written to escape.
 *
 * @param waistToShoulder narrowest torso width between shoulders and hips, over the
 *   shoulder width. The single most informative front-view shape signal there is.
 * @param waistToHip the same waist over the widest width below the hip landmark. Weaker on
 *   its own but useful as a temper, because shoulder breadth varies between frames far more
 *   than hip breadth does.
 */
data class ShapeIndices(
    val waistToShoulder: Double,
    val waistToHip: Double?,
)

/**
 * Body fat estimated from the silhouette's shape alone.
 *
 * This is the method that answers "measure me from the photo, not from a tape equation".
 * Nothing here passes through a circumference, a neck, or the Hodgdon equations. It reads
 * the outline's proportions and maps them onto adiposity directly.
 *
 * **What it buys.** Independence. Every other route to a percentage in this app is a
 * function of the same converted circumferences, so when scale recovery fails they fail
 * together and [com.squeeze.core.bodycomp.MethodFusion] tightens the interval around a
 * number that is confidently wrong. This one cannot fail that way, so a large disagreement
 * between it and the tape-equation estimate is positive evidence that scale broke — which is
 * information the app previously had no way to obtain.
 *
 * **What it costs.** Accuracy. Shape is a coarser signal than a measured girth: two people
 * with the same waist-to-shoulder ratio can differ by several points of body fat depending on
 * how much muscle sits under the outline. The interval it carries is correspondingly wide,
 * and it must stay wide — fusion weights by precision, and overstating this would let a
 * silhouette outvote a tape measurement.
 *
 * **The mapping is a two-point anchor, not a fitted regression, and the code says so rather
 * than dressing it up.** The anchors are the ratios observed at the ends of the range where
 * they are least ambiguous: a visibly lean trained body, and a clearly overweight one. A
 * straight line between them is crude in the middle. It is also monotonic, sex-specific, and
 * incapable of the failure that matters here — reporting twenty per cent on a body with
 * visible abs — because a body with that outline cannot produce that ratio.
 */
object SilhouetteBodyFat {

    /**
     * Waist-to-shoulder at the lean anchor, and the body fat it corresponds to.
     *
     * A trained male at single-digit body fat runs a waist around seven-tenths of his
     * shoulder width. Women hold more of their fat on the hips and thighs and less on the
     * waist, so the same ratio sits lower on the body-fat scale for them.
     */
    private const val MALE_LEAN_RATIO = 0.72
    private const val MALE_LEAN_PERCENT = 8.0
    private const val MALE_HIGH_RATIO = 1.02
    private const val MALE_HIGH_PERCENT = 35.0

    private const val FEMALE_LEAN_RATIO = 0.70
    private const val FEMALE_LEAN_PERCENT = 16.0
    private const val FEMALE_HIGH_RATIO = 1.00
    private const val FEMALE_HIGH_PERCENT = 42.0

    /**
     * The same anchors for waist over hip width.
     *
     * A second, weaker signal used only to temper the first. A very broad-shouldered lifter
     * posts a low waist-to-shoulder ratio at a body fat that is not especially low; the hip
     * is a steadier denominator, because pelvic breadth is skeletal and varies less with
     * training than deltoid breadth does.
     */
    private const val MALE_LEAN_HIP_RATIO = 0.80
    private const val MALE_HIGH_HIP_RATIO = 1.06
    private const val FEMALE_LEAN_HIP_RATIO = 0.72
    private const val FEMALE_HIGH_HIP_RATIO = 0.98

    /** How much of the answer comes from the shoulder ratio rather than the hip one. */
    private const val SHOULDER_WEIGHT = 0.6

    /**
     * Reads the descriptors from a profile and its pose anchors.
     *
     * Uses the same anatomical searches the rest of the scan uses, because the question is
     * the same one — where is the waist — and having two answers to it would be worse than
     * having one imperfect one.
     *
     * @return null when the silhouette does not support the ratios, rather than substituting
     *   defaults. A fabricated shape index would be indistinguishable from a measured one.
     */
    fun indicesFrom(profile: WidthProfile, anchors: PoseAnchors): ShapeIndices? {
        val stature = (profile.bottomRow - profile.topRow).toDouble()
        if (stature <= 0.0) return null

        val waistRow = AnatomicalLevelFinder
            .narrowestBetween(profile, anchors.shoulderRow, anchors.hipRow) ?: return null
        val waist = profile.torsoWidthAt(waistRow)
        if (waist <= 0.0) return null

        // Shoulder width is taken from the silhouette a little below the joint line, where
        // the deltoid is widest, rather than at the landmark row itself.
        val shoulderBand = anchors.shoulderRow +
            ((anchors.hipRow - anchors.shoulderRow) * SHOULDER_BAND).toInt()
        val shoulder = AnatomicalLevelFinder
            .widestBetween(profile, anchors.shoulderRow, shoulderBand)
            ?.let { profile.torsoWidthAt(it) }
            ?: return null
        if (shoulder <= 0.0) return null

        val hipSpan = anchors.kneeRow - anchors.hipRow
        val hip = AnatomicalLevelFinder
            .widestBetween(profile, anchors.hipRow, anchors.hipRow + (hipSpan * 0.18).toInt())
            ?.let { profile.torsoWidthAt(it) }
            ?.takeIf { it > 0.0 }

        return ShapeIndices(
            waistToShoulder = waist / shoulder,
            waistToHip = hip?.let { waist / it },
        )
    }

    /**
     * Below this waist-to-shoulder ratio the outline stops carrying information.
     *
     * Measured, not assumed. Reading the ratios off a labelled reference chart gives 0.586
     * at eight per cent, 0.592 at twelve and 0.580 at fifteen — flat, inside the noise of
     * the measurement itself. The outline only begins to move at twenty per cent and
     * flattens again above thirty.
     *
     * That is a physical fact rather than a limitation of this code. What separates a lean
     * body from a very lean one is abdominal definition and vascularity, and a silhouette
     * discards both by construction: it knows the border between body and background and
     * nothing whatever about the surface inside it. Two men at eight and fifteen per cent
     * cast nearly the same shadow.
     *
     * So in this region the method returns its lean-end value with a deliberately crippled
     * interval, which is the honest output — "you are lean, and I cannot tell you how lean".
     * [com.squeeze.core.bodycomp.VisualAssessment] is the instrument for that question,
     * because it reads the features the outline throws away, and fusion will weight it
     * accordingly without needing to be told.
     */
    const val LEAN_PLATEAU_RATIO = 0.76

    /** What the interval widens to once the outline has stopped distinguishing anything. */
    const val PLATEAU_ERROR_PERCENT = 9.0

    /**
     * @return the estimate, or null when the indices fall outside anything a standing adult
     *   silhouette produces — which means the outline is not a body, not that the body is
     *   unusual
     */
    fun estimate(indices: ShapeIndices, sex: Sex): BodyFatEstimate? {
        val female = sex == Sex.FEMALE

        val leanRatio = if (female) FEMALE_LEAN_RATIO else MALE_LEAN_RATIO
        val highRatio = if (female) FEMALE_HIGH_RATIO else MALE_HIGH_RATIO
        val leanPercent = if (female) FEMALE_LEAN_PERCENT else MALE_LEAN_PERCENT
        val highPercent = if (female) FEMALE_HIGH_PERCENT else MALE_HIGH_PERCENT

        if (indices.waistToShoulder !in PLAUSIBLE_SHOULDER_RATIO) return null

        val fromShoulder = interpolate(
            indices.waistToShoulder, leanRatio, highRatio, leanPercent, highPercent,
        )

        // The hip reading only joins in when there is one. A missing hip narrows the
        // evidence rather than invalidating it, so the shoulder ratio simply stands alone.
        val fromHip = indices.waistToHip
            ?.takeIf { it in PLAUSIBLE_HIP_RATIO }
            ?.let {
                interpolate(
                    it,
                    if (female) FEMALE_LEAN_HIP_RATIO else MALE_LEAN_HIP_RATIO,
                    if (female) FEMALE_HIGH_HIP_RATIO else MALE_HIGH_HIP_RATIO,
                    leanPercent,
                    highPercent,
                )
            }

        val percent = if (fromHip == null) {
            fromShoulder
        } else {
            SHOULDER_WEIGHT * fromShoulder + (1.0 - SHOULDER_WEIGHT) * fromHip
        }

        // On the plateau the number is still reported, because "lean" is worth saying, but
        // its interval is widened to cover the whole range the outline cannot separate.
        // Fusion weights by precision, so this makes it inform the answer without pretending
        // to settle it.
        val onPlateau = indices.waistToShoulder < LEAN_PLATEAU_RATIO
        val error = if (onPlateau) {
            PLATEAU_ERROR_PERCENT
        } else {
            EstimationMethod.PHOTO_SHAPE.standardErrorPercent
        }

        return BodyFatEstimate(
            percent = percent.coerceIn(MIN_PERCENT, MAX_PERCENT),
            method = EstimationMethod.PHOTO_SHAPE,
            standardErrorPercent = error,
        )
    }

    /**
     * Straight line through two anchors, extended past them rather than clamped.
     *
     * Clamping at the anchors would make every very lean body report exactly the lean anchor,
     * which reads as the method being confident when it is in fact off the end of its
     * evidence. Extrapolating and letting the caller bound the result keeps the number
     * honest about which direction it is failing in.
     */
    private fun interpolate(
        value: Double,
        lowInput: Double,
        highInput: Double,
        lowOutput: Double,
        highOutput: Double,
    ): Double {
        val span = highInput - lowInput
        if (span == 0.0) return lowOutput
        val t = (value - lowInput) / span
        return lowOutput + t * (highOutput - lowOutput)
    }

    /** Where the deltoid is widest, as a fraction of the shoulder-to-hip span. */
    private const val SHOULDER_BAND = 0.20

    /** Outside these, the outline is not a standing human torso. */
    private val PLAUSIBLE_SHOULDER_RATIO = 0.55..1.40
    private val PLAUSIBLE_HIP_RATIO = 0.55..1.45

    private const val MIN_PERCENT = 3.0
    private const val MAX_PERCENT = 60.0
}
