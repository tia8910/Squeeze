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
            // Either side is enough. An arm on one side only — the common case, since one
            // hand is usually holding the phone — corrupts the width just as thoroughly.
            cut = low > startX + CLIP_TOLERANCE || high < endX - CLIP_TOLERANCE,
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
 * @param cut true when the bound moved either edge, meaning the width is the allowance
 *   rather than the body
 */
data class ClippedSpan(
    val start: Double,
    val endInclusive: Double,
    val cut: Boolean,
) {
    val width: Double get() = endInclusive - start
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

    /** Shoulder span divided by hip span, at the extremes of the adult range. */
    private const val MIN_SHOULDER_TO_HIP = 0.95
    private const val MAX_SHOULDER_TO_HIP = 2.2

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

        val shoulderToHip = shoulderSpan / hipSpan
        if (shoulderToHip < MIN_SHOULDER_TO_HIP || shoulderToHip > MAX_SHOULDER_TO_HIP) {
            return "Your upper and lower body look twisted relative to each other. Stand " +
                "square, feet level, and keep your hips facing the camera."
        }

        return null
    }
}
