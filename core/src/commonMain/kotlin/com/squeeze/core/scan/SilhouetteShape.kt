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
 * @param waistToShoulder abdominal width over shoulder width. A fallback only, and a poor
 *   one: at the shoulder line the arms are attached, so the silhouette's run there spans
 *   deltoid to deltoid and is not a torso width at all. Kept because a photograph cropped
 *   below the hips has nothing else to offer.
 * @param waistToHip the same waist over the widest width below the hip landmark, and the
 *   ratio the estimate is actually built on. The hip sits below the arms, is skeletal
 *   rather than muscular, and is measured by the same rule as the waist.
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
     * The same anchors for waist over hip width — **the primary mapping**.
     *
     * Promoted from a tempering second opinion after a real scan showed why. The hip is
     * pelvic breadth: skeletal, so it does not move with training; below the arms, so no
     * limb can be counted into it; and read by exactly the same torso-run rule as the waist,
     * so numerator and denominator mean the same thing. None of those is true of the
     * shoulder.
     */
    private const val MALE_LEAN_HIP_RATIO = 0.80
    private const val MALE_HIGH_HIP_RATIO = 1.06
    private const val FEMALE_LEAN_HIP_RATIO = 0.72
    private const val FEMALE_HIGH_HIP_RATIO = 0.98

    /**
     * The interval carried when only the shoulder ratio was available.
     *
     * Wider than the method's ordinary error, because a shoulder denominator is contaminated
     * by however much of the arms the band caught, and the silhouette gives no way to know
     * how much that was.
     */
    private const val SHOULDER_ONLY_ERROR_PERCENT = 8.0

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
    fun indicesFrom(
        profile: WidthProfile,
        anchors: PoseAnchors,
        /**
         * Distance between the two hip landmarks, as a fraction of image width — the same
         * unit the width profile is in, so the two are directly comparable and the image's
         * scale cancels.
         *
         * Null when no pose geometry reached here, in which case the hip is trusted as
         * before. See [hipIsSkin] for what it is used for, and for why it is deliberately not
         * used as a measurement.
         */
        pelvisSpan: Double? = null,
        /**
         * Whether the pelvis is inside the photograph at all.
         *
         * False at [ScanFraming.UPPER_BODY], and it has to be asked rather than discovered.
         * Every search in this file clamps its band to the silhouette's extent, which is
         * exactly the wrong behaviour when the band is outside the picture: the hip band
         * would collapse onto the last rows of a cropped photograph and return the width of
         * the abdomen at the crop line. That is a real measurement of a real body part — it
         * is simply not the hip, and it sits near the waist's own width. Waist over waist is
         * 1.0, and 1.0 through the hip anchors is about thirty-five per cent, reported with
         * the narrow interval a measured hip earns.
         *
         * So the hip is dropped, the shoulder denominator carries the reading, and the
         * interval widens to the eight points a shoulder denominator has always carried.
         */
        hipInFrame: Boolean = true,
    ): ShapeIndices? {
        val stature = (profile.bottomRow - profile.topRow).toDouble()
        if (stature <= 0.0) return null

        // The waist is read at a fixed anatomical level, not at the narrowest row.
        //
        // This one change is the difference between an answer and a floor value, and the
        // reason is anatomical rather than numerical: **the narrowest row is where the fat
        // is not.** On a lean torso the minimum sits high, under the ribs and above the
        // abdomen. On a heavy one it sits in almost the same place, and the belly that
        // accumulated below it is never measured at all. Searching for a minimum therefore
        // measures the one part of the trunk that adiposity moves least, which is why the
        // ratio came back flat across the whole labelled range.
        //
        // It is also a selected extremum, and an extremum is chosen by whichever row happens
        // to be most extreme on the day — a different row on the next scan, and a different
        // row on two people of the same build. A median over a band fixed to the landmarks is
        // decided by the landmarks, and they do not move. For an app whose whole thesis is
        // that precision matters more than accuracy, that is not a detail.
        //
        // Measured on a real scan: the narrowest row gave 0.552 and the navel band 0.759 on
        // the same photograph of the same body. Through the anchors below those are −7% and
        // 11.5%, and 11.5 is the one that matched.
        //
        // The three row ranges come from [TrunkBands] rather than being computed here, so the
        // measurement lab can draw exactly the bands that were measured. Two copies of this
        // arithmetic would make the overlay a picture of something similar, which is worth
        // nothing: the entire value of drawing a band is that it is the one used.
        val bands = TrunkBands.from(anchors)

        val waist = AnatomicalLevelFinder
            .medianBetween(profile, bands.waist.fromRow, bands.waist.toRowInclusive)
            ?: return null
        if (waist <= 0.0) return null

        // Shoulder width is taken from the silhouette a little below the joint line, where
        // the deltoid is widest, rather than at the landmark row itself.
        val shoulder = AnatomicalLevelFinder
            .widestBetween(profile, bands.shoulder.fromRow, bands.shoulder.toRowInclusive)
            ?.let { profile.torsoWidthAt(it) }
            ?: return null
        if (shoulder <= 0.0) return null

        // The hip, read narrowly and by median, and then checked against the skeleton.
        //
        // The previous version searched for the **widest** row over a band reaching 18% of
        // the hip-to-knee distance below the hip landmark. Both halves of that were wrong on
        // a clothed subject, and they compounded:
        //
        //  - At [ScanFraming.TORSO] the knee row is synthesised at 1.2 trunk-lengths below
        //    the hip, so "18% of hip-to-knee" is a fifth of a trunk length — deep into the
        //    shorts on any photograph framed at the waistband.
        //  - Inside that band it took the maximum. A maximum over a region containing
        //    clothing does not sample clothing occasionally; it selects for it, because the
        //    baggiest row is by construction the widest one.
        //
        // On a real scan of a man in loose shorts this returned the waistband rather than the
        // hip, inflating the denominator and reading **6.83%** for a body with a soft
        // midsection. A tight band and a median fix the sampling; the check below fixes the
        // rest.
        val hipWidth = if (!hipInFrame) {
            null
        } else {
            AnatomicalLevelFinder
                .medianBetween(profile, bands.hip.fromRow, bands.hip.toRowInclusive)
                ?.takeIf { it > 0.0 }
        }

        return ShapeIndices(
            waistToShoulder = waist / shoulder,
            waistToHip = hipWidth?.takeIf { hipIsSkin(it, pelvisSpan) }?.let { waist / it },
        )
    }

    /**
     * Whether a hip width measured off the mask is the body, or the clothes on it.
     *
     * The pose landmarks put the two hip joint centres somewhere, and the distance between
     * them is skeletal: it is not changed by a waistband, a towel, laundry hanging behind the
     * subject, or how baggy a pair of shorts is. The mask width at the same level is changed
     * by all four.
     *
     * So the landmarks are used here as a **veto and not as a denominator**. That distinction
     * is the whole point. Turning the landmark span into a measurement would mean knowing
     * what fraction of stature it represents, and every time this pipeline has assumed an
     * anatomical constant it has been wrong by enough to move the answer ten points. Knowing
     * only that skin cannot be three times the width of the pelvis it covers requires no such
     * constant, and it is enough to catch the failure that actually happened.
     *
     * The bounds are correspondingly loose. This fires on gross contamination — which is what
     * loose clothing is — and stays quiet on everything else.
     */
    private fun hipIsSkin(hipWidth: Double, pelvisSpan: Double?): Boolean {
        if (pelvisSpan == null || pelvisSpan <= 0.0) return true
        return hipWidth / pelvisSpan in PLAUSIBLE_HIP_TO_PELVIS
    }

    /**
     * The leanest figure the silhouette alone is allowed to claim, for either sex.
     *
     * **Every way this measurement fails makes the body look leaner.** That is not a
     * generalisation from a small sample, it is a property of the mechanism: contamination
     * adds pixels to a denominator and never removes them. Arms merging into the shoulder
     * run, a waistband inside the hip band, laundry joined to the mask — each one widens the
     * denominator, which shrinks the ratio, which reads as lean.
     *
     * Five wrong answers on one body bear it out. Four were absurdly low — 8.00, 6.22, 6.83,
     * 4.93 — from four different causes, and each was reported with a confident band label
     * on a body with no abdominal definition at all.
     *
     * So a low reading is precisely the reading this method has not earned. Below the plateau
     * the ratio is flat, which means the outline cannot separate six per cent from twelve; it
     * follows that a figure below the plateau is never something the outline established, only
     * something a corrupted denominator produced.
     *
     * The floor does not say the subject is not lean. It says **the silhouette cannot be the
     * thing that decides they are.** A tape measurement, a reference scan, or the user
     * matching themselves against the appearance bands can all pull the fused figure below
     * it, because none of those inherits the failure — which is exactly what
     * [com.squeeze.core.bodycomp.MethodFusion] is for.
     */
    fun leanestClaimable(sex: Sex): Double = plateauCeilingPercent(sex)

    private fun floored(percent: Double, sex: Sex): Double =
        percent.coerceIn(leanestClaimable(sex), MAX_PERCENT)

    /**
     * There is deliberately no rule here that widens the interval when the two denominators
     * disagree.
     *
     * One was written and removed the same day. The reasoning sounded right — both ratios
     * read the same trunk in the same photograph, so a wide gap means one of them is
     * measuring something that is not the body — but the threshold had to be invented, and
     * eight points turned out to fire on ordinary input: a clean 0.90 shoulder against a 0.87
     * hip reads 24.2 and 15.3, which is 8.9 apart.
     *
     * That gap is not evidence of contamination, it is the *expected* offset between the two.
     * The shoulder ratio is deflated by arm pixels on essentially every photograph, so it
     * reads systematically leaner than the hip. Treating the normal offset as a fault made
     * a reading that had a hip *less* trusted than one that had none, which inverts the
     * reason the hip is preferred at all.
     *
     * The gap probably does carry information about contamination. Nothing here knows how
     * much, and this file has enough constants chosen by reasoning already.
     */

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
     * The highest percentage a plateau reading can produce, for a given sex.
     *
     * Derived from [LEAN_PLATEAU_RATIO] through the same anchors [estimate] uses, so it moves
     * automatically if those are ever recalibrated. About 11.6 for men and 21.2 for women.
     *
     * Exists so callers can recognise a plateau reading from the percentage alone, without
     * needing the ratio that produced it. That matters because the percentage is what gets
     * stored: a saved figure at or below this line is not a measurement of adiposity, it is
     * the outline saying "somewhere in the lean range, I cannot narrow it further", and it
     * must not be treated as evidence about anything else.
     */
    fun plateauCeilingPercent(sex: Sex): Double {
        val female = sex == Sex.FEMALE
        return interpolate(
            LEAN_PLATEAU_RATIO,
            if (female) FEMALE_LEAN_RATIO else MALE_LEAN_RATIO,
            if (female) FEMALE_HIGH_RATIO else MALE_HIGH_RATIO,
            if (female) FEMALE_LEAN_PERCENT else MALE_LEAN_PERCENT,
            if (female) FEMALE_HIGH_PERCENT else MALE_HIGH_PERCENT,
        )
    }

    /**
     * @return the estimate, or null when the indices fall outside anything a standing adult
     *   silhouette produces — which means the outline is not a body, not that the body is
     *   unusual
     */
    /**
     * Points taken off every silhouette reading, from comparison against independent reads.
     *
     * Two scans of the same body, checked against a coach's eye and against a vision model
     * reading the photograph, both put this method about two points high: 18 where the eye
     * said 15-16, and 17 where it said 14-16. Same direction both times.
     *
     * **Two observations is not a fit, and this is not one.** It is an offset the size of the
     * disagreement that has actually been seen, applied because leaving a known bias in place
     * so the constant can stay theoretically pure serves nobody using the app. It is named
     * here rather than folded into the anchors so it can be deleted in one line when the
     * labelled corpus makes the anchors fittable, which is what should replace it.
     *
     * It is also probably standing in for something else. This body's waist reads about five
     * centimetres wide, and a waist that is too large inflates the estimate through every
     * route at once — fix the band placement and this offset will over-correct. That is the
     * argument for keeping it small, visible and easy to remove rather than for not having it.
     *
     * Subtracted before the floor, so no reading can be pushed below what the outline is
     * allowed to claim.
     */
    const val OBSERVED_OFFSET_PERCENT = 2.0

    fun estimate(indices: ShapeIndices, sex: Sex): BodyFatEstimate? {
        val female = sex == Sex.FEMALE

        val leanRatio = if (female) FEMALE_LEAN_RATIO else MALE_LEAN_RATIO
        val highRatio = if (female) FEMALE_HIGH_RATIO else MALE_HIGH_RATIO
        val leanPercent = if (female) FEMALE_LEAN_PERCENT else MALE_LEAN_PERCENT
        val highPercent = if (female) FEMALE_HIGH_PERCENT else MALE_HIGH_PERCENT

        if (indices.waistToShoulder !in PLAUSIBLE_SHOULDER_RATIO) return null

        val fromShoulder = interpolate(
            indices.waistToShoulder, leanRatio, highRatio, leanPercent, highPercent,
        ) - OBSERVED_OFFSET_PERCENT

        // **The hip is the denominator, whenever there is one.**
        //
        // Shoulder width from a front silhouette is not a torso width and cannot be made
        // into one. At the shoulder line the arms are attached, so the mask has a single run
        // spanning deltoid to deltoid; a few centimetres lower the arms separate and the run
        // is the trunk alone. Measured on a real scan: 495 pixels at the shoulder band
        // against 304 immediately below it, on the same body in the same photograph.
        //
        // That makes the shoulder ratio a comparison between two things measured different
        // ways — a numerator with the arms excluded over a denominator with them included —
        // and it deflates the ratio by however much arm happened to be in the band. On that
        // scan it produced 0.687, six per cent, for a body with no abdominal definition at
        // all. The same waist over the hip gave 0.87 and fifteen.
        //
        // The hip has none of that. It sits below the arms entirely, it is pelvic breadth
        // rather than muscle so it does not move with training or with how someone is
        // standing, and it is measured by exactly the same torso-run rule as the waist.
        // Waist over hip is also the ratio the anthropometric literature actually uses,
        // which is not a coincidence — it is the pairing that survives contact with real
        // photographs of real people.
        val fromHip = indices.waistToHip
            ?.takeIf { it in PLAUSIBLE_HIP_RATIO }
            ?.let {
                interpolate(
                    it,
                    if (female) FEMALE_LEAN_HIP_RATIO else MALE_LEAN_HIP_RATIO,
                    if (female) FEMALE_HIGH_HIP_RATIO else MALE_HIGH_HIP_RATIO,
                    leanPercent,
                    highPercent,
                ) - OBSERVED_OFFSET_PERCENT
            }

        if (fromHip != null) {
            return BodyFatEstimate(
                percent = floored(fromHip, sex),
                method = EstimationMethod.PHOTO_SHAPE,
                // A floored reading is a bound rather than a measurement, and carries the
                // interval that says so.
                standardErrorPercent = if (fromHip < leanestClaimable(sex)) {
                    PLATEAU_ERROR_PERCENT
                } else {
                    EstimationMethod.PHOTO_SHAPE.standardErrorPercent
                },
            )
        }

        // No hip in the silhouette. The shoulder ratio is all that is left, and it keeps its
        // plateau behaviour — the flatness measured on the reference charts was measured on
        // this ratio, so it belongs here and nowhere else.
        val onPlateau = indices.waistToShoulder < LEAN_PLATEAU_RATIO

        // **On the plateau, report the plateau — not the line extended past its own data.**
        //
        // Below [LEAN_PLATEAU_RATIO] the mapping is an extrapolation of a fit through a
        // region where the ratio was measured to be flat: 0.586 at eight per cent, 0.592 at
        // twelve, 0.580 at fifteen. Flat means the ratio does not distinguish those bodies.
        // Running the line on anyway turns "cannot tell" into a specific small number, and
        // the smaller the ratio the more confident the falsehood.
        //
        // A real scan made that concrete. Arms merged into the shoulder run gave 0.686 —
        // which is not a lean body, it is a contaminated denominator — and the extrapolation
        // reported **4.93%**, labelled "below essential", for a man with no abdominal
        // definition at all. The plateau ceiling with a nine-point interval says the same
        // thing the data supports: somewhere in the lean region, and this method cannot say
        // where.
        val percent = if (onPlateau) plateauCeilingPercent(sex) else floored(fromShoulder, sex)

        val error = if (onPlateau) {
            PLATEAU_ERROR_PERCENT
        } else {
            // Widened even off the plateau, because the denominator is contaminated by
            // whatever share of the arms the shoulder band caught, and that share is not
            // knowable from the silhouette.
            SHOULDER_ONLY_ERROR_PERCENT
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
    const val SHOULDER_BAND = 0.20

    /**
     * The abdominal band, as fractions of the shoulder-to-hip span.
     *
     * Centred on the navel, which is where abdominal fat sits and where every anthropometric
     * standard puts the waist for exactly that reason. Wide enough that the median has
     * something to average over, narrow enough that it cannot reach the ribcage above or the
     * hip flare below.
     */
    const val WAIST_BAND_START = 0.58
    const val WAIST_BAND_END = 0.74

    /** Outside these, the outline is not a standing human torso. */
    private val PLAUSIBLE_SHOULDER_RATIO = 0.55..1.40
    private val PLAUSIBLE_HIP_RATIO = 0.55..1.45

    /**
     * How wide the mask at the hip may be relative to the distance between the hip joints.
     *
     * Hip breadth over the greater trochanters runs near 0.19 of stature; the landmark span
     * is nearer 0.10 to 0.12, so a clean silhouette lands somewhere around 1.6 to 1.9. The
     * bounds here sit well outside that on both sides, because the quantity being ruled out
     * is not a slightly odd hip — it is a measurement of something that is not a hip.
     */
    private val PLAUSIBLE_HIP_TO_PELVIS = 1.15..2.40

    /**
     * How far below the hip landmark the hip width is read, as a fraction of hip-to-knee.
     *
     * A tenth rather than the fifth it was. The hip is at its widest within a few centimetres
     * of the joint line, and everything gained by reaching further down is trouser.
     */
    const val HIP_BAND = 0.10

    /**
     * The same band, capped against the trunk instead.
     *
     * Load-bearing at [ScanFraming.TORSO], where the knee row is not observed but synthesised
     * from the trunk length — so a band defined only as a fraction of hip-to-knee is really a
     * fraction of an assumption, and reaches wherever that assumption puts it.
     */
    const val HIP_BAND_TRUNK_CAP = 0.12

    private const val MIN_PERCENT = 3.0
    private const val MAX_PERCENT = 60.0
}
