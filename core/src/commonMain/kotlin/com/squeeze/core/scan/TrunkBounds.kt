package com.squeeze.core.scan

import kotlin.math.abs

/**
 * The horizontal extent of the trunk, row by row, inferred from pose landmarks.
 *
 * This exists to solve the single largest source of error in a front-photo measurement:
 * **an arm resting against the body**.
 *
 * The silhouette extractor already splits each mask row into runs and takes the one
 * containing the midline, which excludes the arms whenever a visible gap separates them
 * from the trunk. In a real photograph that gap frequently does not exist. Arms hang at the
 * sides, hands rest on hips, one arm holds the phone. Where the arm touches the body, arm
 * and trunk are one continuous run, and the measured "waist" silently becomes waist plus
 * two arm widths — an error of fifty per cent or more that lands inside every plausibility
 * range and is therefore reported as fact.
 *
 * Pose landmarks do not have that problem: the shoulder and hip joints are located
 * independently of the silhouette, and an arm pressed against the waist does not move them.
 * Interpolating between the two gives a bound on where the trunk can be at any height, and
 * anything outside it is not trunk.
 *
 * The bound is deliberately loose. Landmarks sit at joint centres, not at the skin, so the
 * real body extends past them — by the deltoid at the shoulder, by soft tissue at the
 * waist. The margin is level-dependent — see [SHOULDER_MARGIN] and [HIP_MARGIN] — because
 * the two landmark pairs sit at very different depths, and it is sized to contain a large
 * person's waist while still excluding a limb: clipping a genuine waist would understate it,
 * and understating is no better than overstating.
 *
 * All coordinates are 0..1 fractions of image width, matching the pose model's own output.
 */
data class TrunkBounds(
    val shoulderLeftX: Double,
    val shoulderRightX: Double,
    val shoulderRow: Int,
    val hipLeftX: Double,
    val hipRightX: Double,
    val hipRow: Int,
) {
    init {
        require(shoulderRow < hipRow) { "shoulders must sit above the hips" }
    }

    private val shoulderCentre = (shoulderLeftX + shoulderRightX) / 2.0
    private val hipCentre = (hipLeftX + hipRightX) / 2.0
    private val shoulderHalfSpan = abs(shoulderRightX - shoulderLeftX) / 2.0
    private val hipHalfSpan = abs(hipRightX - hipLeftX) / 2.0

    /**
     * Widest the trunk may be at [row], as a fraction of image width.
     *
     * Between the shoulders and the hips this interpolates the two measured spans. Outside
     * that range it holds the nearer span rather than extrapolating: a line drawn through
     * two points keeps going, and continuing it upward would shrink the allowed width to
     * nothing around the neck and then invert, while continuing it downward would let it
     * grow without limit down the legs.
     */
    fun maxHalfWidthAt(row: Int): Double {
        val fraction = when {
            row <= shoulderRow -> 0.0
            row >= hipRow -> 1.0
            else -> (row - shoulderRow).toDouble() / (hipRow - shoulderRow).toDouble()
        }

        val span = shoulderHalfSpan + fraction * (hipHalfSpan - shoulderHalfSpan)

        // The margin is interpolated too, and that is the point. A single margin cannot
        // serve both ends, because the two landmark pairs sit at very different depths: the
        // acromion is just under the skin, so the shoulder needs barely more than the
        // deltoid, while the hip landmarks are joint centres buried inside the pelvis and
        // the body extends far past them across the glutes and trochanters.
        //
        // Using the shoulder's margin at the hip is what clipped a real hip down below the
        // waist measured on the same body — anatomically impossible, and produced by the
        // bound rather than by the person.
        //
        // The ramp is quadratic rather than linear, and that matters. The waist sits in the
        // middle of this span and is exactly where an arm at the side has to be cut off, so
        // the margin has to stay tight through the middle and open up only as it approaches
        // the hip. A linear ramp loosens the waist by half the hip's allowance and lets the
        // arm back in — which a test caught.
        val eased = fraction * fraction
        val margin = SHOULDER_MARGIN + eased * (HIP_MARGIN - SHOULDER_MARGIN)
        return span * (1.0 + margin)
    }

    /** Where the trunk's centre sits at [row], interpolated the same way. */
    fun centreAt(row: Int): Double = when {
        row <= shoulderRow -> shoulderCentre
        row >= hipRow -> hipCentre
        else -> {
            val t = (row - shoulderRow).toDouble() / (hipRow - shoulderRow).toDouble()
            shoulderCentre + t * (hipCentre - shoulderCentre)
        }
    }

    /**
     * Clips [startX]..[endX] to the trunk's allowed extent at [row].
     *
     * Returns null when the run lies entirely outside the trunk — that is an arm, measured
     * on its own, and it is not a torso width.
     */
    fun clip(startX: Double, endX: Double, row: Int): ClosedFloatingPointRange<Double>? =
        clipRun(startX, endX, row)?.let { it.start..it.endInclusive }

    /**
     * The same clip, but reporting whether it actually cut anything.
     *
     * That flag is the important half. When the bound bites, the number that comes back is
     * the bound — a function of the landmark spans and the margin constants — and not a
     * measurement of the body at all. Two different men produce nearly the same clipped
     * profile, so the ratios read off it produce nearly the same body fat, which is exactly
     * the failure this reports: a scan that returned nineteen per cent for everyone because
     * every width in the trunk band had been replaced by the allowance.
     *
     * A cut is not an error in itself. It is doing its job, cutting an arm away from a
     * waist. The error is treating what remains as measured, so callers are told.
     */
    fun clipRun(startX: Double, endX: Double, row: Int): ClippedSpan? {
        val centre = centreAt(row)
        val half = maxHalfWidthAt(row)
        val low = maxOf(startX, centre - half)
        val high = minOf(endX, centre + half)
        if (low >= high) return null

        return ClippedSpan(
            start = low,
            endInclusive = high,
            centre = centre,
            // Reported per side, because which sides were cut decides what can still be
            // measured. One arm against the body leaves the other edge untouched, and an
            // untouched edge is a real measurement of a real torso.
            cutStart = low > startX + CLIP_TOLERANCE,
            cutEnd = high < endX - CLIP_TOLERANCE,
        )
    }

    companion object {

        /**
         * How far the bound may cut before it counts as having cut, in fractions of image
         * width.
         *
         * Mask runs are whole pixels while the bound is continuous, so the two almost never
         * coincide exactly and a strict comparison would flag every row. Two thousandths of
         * the width is about one pixel on a typical mask — below anything that could change
         * a measurement, above the quantisation.
         */
        const val CLIP_TOLERANCE = 0.002

        /**
         * How far past the acromion the shoulder may extend, as a fraction of its half-span.
         *
         * Small, because the acromion sits just under the skin — what lies outside it is the
         * deltoid and not much else.
         */
        const val SHOULDER_MARGIN = 0.40

        /**
         * The same at the hip, and much larger for a physical reason.
         *
         * The hip landmarks are joint centres buried inside the pelvis, roughly 18 to 20 cm
         * apart on an adult, while the body across the glutes and trochanters is more like
         * 34 cm. The skin is therefore nearly twice the half-span from the midline, and a
         * margin sized for the shoulder clips a real hip badly — in testing it returned a
         * hip narrower than the waist measured on the same body, which cannot happen.
         */
        const val HIP_MARGIN = 1.00

        /**
         * Builds bounds from front-facing pose geometry.
         *
         * @param rowCount height of the coordinate space the rows are expressed in, so the
         *   normalised landmark y-values can be projected into the same rows the silhouette
         *   profile uses.
         */
        fun from(geometry: FrontPoseGeometry, rowCount: Int): TrunkBounds? {
            if (rowCount <= 0) return null

            val shoulderRow =
                ((geometry.shoulderLeft.y + geometry.shoulderRight.y) / 2.0 * rowCount).toInt()
            val hipRow = ((geometry.hipLeft.y + geometry.hipRight.y) / 2.0 * rowCount).toInt()
            if (shoulderRow >= hipRow) return null

            return TrunkBounds(
                shoulderLeftX = minOf(geometry.shoulderLeft.x, geometry.shoulderRight.x),
                shoulderRightX = maxOf(geometry.shoulderLeft.x, geometry.shoulderRight.x),
                shoulderRow = shoulderRow,
                hipLeftX = minOf(geometry.hipLeft.x, geometry.hipRight.x),
                hipRightX = maxOf(geometry.hipLeft.x, geometry.hipRight.x),
                hipRow = hipRow,
            )
        }
    }
}

/**
 * A torso run after the trunk bound has been applied to it.
 *
 * @param centre where the bound put the trunk's midline at this row
 * @param cutStart true when the bound pulled the left edge inward, i.e. something extended
 *   further left than a trunk can reach
 * @param cutEnd the same on the right
 */
data class ClippedSpan(
    val start: Double,
    val endInclusive: Double,
    val centre: Double,
    val cutStart: Boolean,
    val cutEnd: Boolean,
) {
    val width: Double get() = endInclusive - start

    /** True when the bound moved either edge. */
    val cut: Boolean get() = cutStart || cutEnd

    /**
     * The torso's width inferred from whichever edge the bound did not have to touch.
     *
     * One arm against the body is the ordinary case — usually the one not holding the phone —
     * and it corrupts one edge while leaving the other exactly where the skin is. Doubling the
     * clean half about the trunk's centre recovers the width from the side that was never in
     * doubt.
     *
     * The assumption is that a torso is roughly symmetric about its midline. That is a far
     * weaker claim than the alternatives on offer: measuring an arm as part of the waist, or
     * refusing to measure at all. Refusing was the previous behaviour, and on a body that
     * simply stands with its arms down it refused every time.
     *
     * @return null when both edges were cut, which means both sides are contaminated and
     *   there is no clean half left to mirror
     */
    fun mirroredWidth(): Double? {
        if (cutStart && cutEnd) return null
        if (!cutStart && !cutEnd) return width

        val cleanHalf = if (cutStart) endInclusive - centre else centre - start
        return if (cleanHalf > 0.0) 2.0 * cleanHalf else null
    }
}

/**
 * Whether a photograph is square enough to the camera to measure from.
 *
 * A front-on width is only a width if the subject is facing the lens. Rotate them and the
 * shoulders foreshorten while the depth the estimator assumes stays the same, so every
 * circumference drifts without anything looking wrong. Mirror selfies are the common case
 * and the worst one, because the phone is held out to one side and the body turns with it.
 *
 * Two signals are available without any extra inference, both from landmarks the scan
 * already computes:
 *
 *  - **Shoulder span against stature.** Biacromial breadth is one of the most stable ratios
 *    in anthropometry, sitting near 0.23 of height across adults. Rotation shrinks the
 *    projected span; nothing else plausibly does.
 *  - **Shoulder span against hip span.** Both foreshorten together under rotation, so the
 *    ratio is preserved — but a wildly asymmetric value indicates one girdle is turned
 *    relative to the other, which is a twisted stance rather than a measurable pose.
 */
object FrontalityCheck {

    /** Biacromial breadth as a fraction of stature, at the extremes of the adult range. */
    private const val MIN_SHOULDER_TO_HEIGHT = 0.17
    private const val MAX_SHOULDER_TO_HEIGHT = 0.32

    /**
     * Shoulder span divided by hip span, at the extremes of the adult range.
     *
     * **These are landmark spans, not body breadths, and the upper bound was set as though
     * they were the same thing.** The pose model's shoulder points sit near the acromion, so
     * the numerator is roughly biacromial breadth — about 0.23 of stature. Its hip points are
     * *joint centres*, buried inside the pelvis and around 0.10 of stature apart, not the
     * 0.16 that bi-iliac breadth runs at. The ratio a square, ordinary adult produces is
     * therefore near 2.3, and 2.2 refused them: the check fired on exactly the photographs it
     * was meant to pass, and told the user to stand square while they were standing square.
     *
     * The same arithmetic is why the lower bound stays where it is. A ratio under 1 means the
     * shoulders came back narrower than a span that should be less than half of them, which
     * no framing of an untwisted adult produces.
     *
     * [TrunkBounds.HIP_MARGIN] carries the same fact, arrived at the same way: a margin sized
     * for the shoulder clipped a real hip because the landmarks there are nowhere near the
     * skin.
     */
    private const val MIN_SHOULDER_TO_HIP = 0.95
    private const val MAX_SHOULDER_TO_HIP = 2.6

    /**
     * The same bounds when the hips are outside the picture, and much looser.
     *
     * Not a decision to care less about a twisted stance. It is that at
     * [ScanFraming.UPPER_BODY] the hip landmarks are extrapolated — the model is reporting
     * where the hips *would* be, from the shoulder line and the visible trunk — so a hip span
     * that disagrees with the shoulders is describing the pose prior rather than the person.
     * Refusing a photograph on that evidence rejects good pictures for a reason the check
     * cannot actually see, which is worse than not checking.
     *
     * Kept at all because a gross value is still worth catching: a shoulder span three times
     * an inferred hip span is not a turned body, it is a misdetection, and measuring a
     * misdetection is how this app has produced every wrong number it has produced.
     */
    private const val MIN_INFERRED_SHOULDER_TO_HIP = 0.70
    private const val MAX_INFERRED_SHOULDER_TO_HIP = 3.2

    /**
     * @param bodyHeightFraction how much of the frame the subject spans vertically
     * @param imageAspectRatio image width divided by height, needed because the spans are
     *   fractions of width while the stature is a fraction of height
     * @return a reason the photo is not usable head-on, or null when it looks square
     */
    fun evaluate(
        geometry: FrontPoseGeometry,
        bodyHeightFraction: Double,
        imageAspectRatio: Double,
    ): String? {
        if (bodyHeightFraction <= 0.0 || imageAspectRatio <= 0.0) return null

        val shoulderSpan = abs(geometry.shoulderRight.x - geometry.shoulderLeft.x)
        val hipSpan = abs(geometry.hipRight.x - geometry.hipLeft.x)
        if (shoulderSpan <= 0.0 || hipSpan <= 0.0) return null

        // Both converted into the same units — fractions of the subject's own height — so
        // the comparison holds whatever the framing.
        val shoulderToHeight = shoulderSpan * imageAspectRatio / bodyHeightFraction

        if (shoulderToHeight < MIN_SHOULDER_TO_HEIGHT) {
            return "You look turned away from the camera — your shoulders appear narrower " +
                "than they should for your height. Stand square to the lens, with both " +
                "shoulders the same distance from it."
        }
        if (shoulderToHeight > MAX_SHOULDER_TO_HEIGHT) {
            return "Your shoulders appear unusually wide for your height, which usually " +
                "means your arms were counted as part of them. Let your arms hang clear of " +
                "your sides."
        }

        return evaluateTorso(geometry)
    }

    /**
     * The half of the check that does not need the subject's stature.
     *
     * Used for a trunk-framed photograph, where the crown and feet are out of shot and
     * [evaluate]'s shoulder-against-height test therefore has nothing to work with. The
     * shoulder-against-hip test does not care: both spans are fractions of the same image
     * width, so it holds at any framing.
     *
     * Split out rather than reached by passing a zero height, which would silently skip both
     * tests and leave a twisted trunk unremarked.
     */
    fun evaluateTorso(geometry: FrontPoseGeometry): String? =
        evaluateAgainstHips(geometry, MIN_SHOULDER_TO_HIP, MAX_SHOULDER_TO_HIP)

    /**
     * The same test again, on the only evidence an upper-body photograph carries.
     *
     * At [ScanFraming.UPPER_BODY] there is no stature and no observed pelvis, so both of the
     * signals this object was built on are gone — one because the crown and feet are out of
     * shot, the other because the hips are. What remains is an inferred hip span, and it is
     * checked only for values no real detection produces. See
     * [MIN_INFERRED_SHOULDER_TO_HIP].
     *
     * That is a real loss of protection and it is written here rather than hidden: a body
     * turned thirty degrees in an upper-body photograph will be measured, and measured a
     * little lean, because rotation foreshortens the shoulder denominator less than it
     * foreshortens the waist. The shoulder-only interval is eight points wide, which is
     * where that uncertainty is already accounted for.
     */
    fun evaluateUpperBody(geometry: FrontPoseGeometry): String? = evaluateAgainstHips(
        geometry,
        MIN_INFERRED_SHOULDER_TO_HIP,
        MAX_INFERRED_SHOULDER_TO_HIP,
    )

    private fun evaluateAgainstHips(
        geometry: FrontPoseGeometry,
        min: Double,
        max: Double,
    ): String? {
        val shoulderSpan = abs(geometry.shoulderRight.x - geometry.shoulderLeft.x)
        val hipSpan = abs(geometry.hipRight.x - geometry.hipLeft.x)
        if (shoulderSpan <= 0.0 || hipSpan <= 0.0) return null

        val shoulderToHip = shoulderSpan / hipSpan
        if (shoulderToHip < min || shoulderToHip > max) {
            return "Your upper and lower body look twisted relative to each other. Stand " +
                "square, feet level, and keep your hips facing the camera."
        }

        return null
    }
}
