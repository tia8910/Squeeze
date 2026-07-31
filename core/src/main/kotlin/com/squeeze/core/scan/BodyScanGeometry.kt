package com.squeeze.core.scan

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A horizontal slice of the body at one anatomical level, measured from two photographs.
 *
 * Widths are in image fractions, not pixels: 0.0 to 1.0 of the image width. Keeping them
 * normalised means the geometry does not care about camera resolution, and the same numbers
 * work whether they came from a segmentation model or from a marker the user dragged.
 *
 * The two photos carry their own vertical positions rather than sharing one, because they
 * are separate images and the user marks each independently. Comparing them is the only way
 * to catch the most damaging capture error: marking the waist in one view and the ribs in
 * the other, which yields a confident and completely wrong circumference.
 *
 * @param frontWidthFraction body width at this level in the front-facing photo
 * @param sideWidthFraction body depth at the same level in the side-facing photo
 * @param frontHeightFraction where the level sits vertically in the front photo, 0.0 at top
 * @param sideHeightFraction where the level sits vertically in the side photo
 */
data class BodySlice(
    val frontWidthFraction: Double,
    val sideWidthFraction: Double,
    val frontHeightFraction: Double,
    val sideHeightFraction: Double,
) {
    init {
        require(frontWidthFraction > 0.0) { "front width must be positive" }
        require(sideWidthFraction > 0.0) { "side width must be positive" }
    }
}

/**
 * Converts image measurements into real-world centimetres.
 *
 * Scale recovery is the hard part of any photo-based measurement, and the reason most
 * attempts are inaccurate. This app uses the user's stated height as the reference: it is
 * a length they already know precisely, it spans most of the frame so relative error is
 * small, and it needs no depth sensor, fiducial marker or ARCore support.
 *
 * The two limitations are worth stating plainly, because they bound what the scan can claim:
 *
 *  - **Perspective.** A body is not flat. Parts nearer the lens measure larger. Standing
 *    further back with less zoom reduces this; [PERSPECTIVE_WARNING_RATIO] flags framing
 *    that makes it severe.
 *  - **Height honesty.** Every measurement scales linearly with the stated height, so a
 *    user who rounds 174 cm up to 176 shifts every circumference by about 1%.
 */
class ScaleRecovery(
    private val heightCm: Double,
    private val bodyHeightFraction: Double,
) {
    init {
        require(heightCm in 100.0..250.0) { "implausible height: $heightCm" }
        require(bodyHeightFraction in 0.05..1.0) {
            "body must occupy a positive fraction of the frame, was $bodyHeightFraction"
        }
    }

    /** Centimetres per unit of normalised image height. */
    private val cmPerHeightFraction: Double = heightCm / bodyHeightFraction

    /**
     * Converts a normalised width into centimetres.
     *
     * @param imageAspectRatio width divided by height in pixels. Required because widths are
     *   normalised against image *width* while the scale was derived from image *height*;
     *   skipping this is a silent error that scales every circumference by the aspect ratio.
     */
    fun widthToCm(widthFraction: Double, imageAspectRatio: Double): Double {
        require(imageAspectRatio > 0.0) { "aspect ratio must be positive" }
        return widthFraction * imageAspectRatio * cmPerHeightFraction
    }

    /**
     * True when the subject fills so much of the frame that perspective distortion is likely
     * to dominate. The capture UI should ask the user to step back rather than silently
     * returning a measurement it does not believe.
     */
    fun isFramingTooTight(): Boolean = bodyHeightFraction > PERSPECTIVE_WARNING_RATIO

    companion object {
        /**
         * Above this, the subject is close enough to the lens that near-body parts measure
         * noticeably larger than far ones. Chosen conservatively: leaving roughly a tenth of
         * the frame as margin corresponds to a camera distance where the error is small
         * relative to the method's other sources.
         */
        const val PERSPECTIVE_WARNING_RATIO = 0.9
    }
}

/**
 * Estimates a circumference from a slice's width and depth.
 *
 * A horizontal cross-section of a torso is closer to an ellipse than to a circle, so the
 * front width and the side depth become the two axes. This is the standard approach in
 * photogrammetric anthropometry and it is what makes two photographs meaningfully better
 * than one: a single front view cannot distinguish a deep torso from a flat one, and would
 * read them as the same circumference.
 */
object CircumferenceEstimator {

    /**
     * Ramanujan's second approximation to the perimeter of an ellipse.
     *
     * The exact perimeter has no closed form — it needs an elliptic integral. Ramanujan's
     * approximation is accurate to far better than measurement noise for the eccentricities
     * a human torso produces, so the error it introduces is irrelevant next to the error in
     * locating the waist.
     *
     * @param semiMajorCm half the larger axis
     * @param semiMinorCm half the smaller axis
     */
    fun ellipsePerimeter(semiMajorCm: Double, semiMinorCm: Double): Double {
        require(semiMajorCm > 0.0 && semiMinorCm > 0.0) { "semi-axes must be positive" }
        val a = maxOf(semiMajorCm, semiMinorCm)
        val b = minOf(semiMajorCm, semiMinorCm)

        val h = ((a - b) * (a - b)) / ((a + b) * (a + b))
        return PI * (a + b) * (1.0 + (3.0 * h) / (10.0 + sqrt(4.0 - 3.0 * h)))
    }

    /**
     * Circumference at one anatomical level.
     *
     * @param frontWidthCm body width facing the camera
     * @param sideDepthCm body depth from the side view
     */
    fun circumference(frontWidthCm: Double, sideDepthCm: Double): Double =
        ellipsePerimeter(frontWidthCm / 2.0, sideDepthCm / 2.0)

    /**
     * How far from circular this slice is, as a ratio of the axes.
     *
     * Reported so the UI can flag an implausible result. A torso slice normally sits
     * somewhere between 1.2 and 2.0; a value far outside that usually means a marker was
     * placed at a different height in the two photos rather than that the body is unusual.
     */
    fun eccentricityRatio(frontWidthCm: Double, sideDepthCm: Double): Double {
        val a = maxOf(frontWidthCm, sideDepthCm)
        val b = minOf(frontWidthCm, sideDepthCm)
        return if (b > 0.0) a / b else Double.POSITIVE_INFINITY
    }

    /**
     * True when the two photos disagree about where a level sits.
     *
     * A slice is only meaningful if both markers are at the same anatomical height. This
     * catches the common capture error — measuring the waist in one photo and the ribs in
     * the other — which otherwise produces a confident, wrong number.
     */
    fun isLevelMismatched(front: Double, side: Double, toleranceFraction: Double = 0.03): Boolean =
        abs(front - side) > toleranceFraction
}
