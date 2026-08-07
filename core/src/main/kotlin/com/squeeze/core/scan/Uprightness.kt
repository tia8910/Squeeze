package com.squeeze.core.scan

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Which way up the subject is in the frame, read from the body itself.
 *
 * Photographs arrive at any rotation and the file often does not say so. A camera usually
 * records rotation in EXIF rather than rotating the pixels, but that tag is stripped by
 * screenshotting, by most share sheets, by some messaging apps, and by anything that
 * re-encodes — so a photo of a standing person can land here with the body running left to
 * right and nothing in the file admitting it.
 *
 * The app used to take that at face value. Every row index in this pipeline is a horizontal
 * slice, so on a sideways photograph the "widths" are slices along the body's *length*: the
 * waist band lands on a thigh, the shoulder band on a forearm, and the ratio between them is
 * a number with no meaning that is nonetheless printed to one decimal place. One such scan
 * reported 5.0%.
 *
 * The fix is not to ask the user to rotate anything. The rotation is recoverable from the
 * landmarks the pose model already returns: **a standing person's shoulders sit above their
 * hips**, so the shoulder-to-hip vector points down the image when the image is upright, and
 * points wherever the rotation put it when it is not. That is a property of human anatomy
 * rather than of any file format, so it survives every pipeline that strips metadata.
 */
object Uprightness {

    /**
     * How far the trunk may lean from vertical before the frame is treated as rotated.
     *
     * Generous, because it is not a posture check. Real photographs of upright people lean by
     * ten or fifteen degrees from camera tilt and from standing with weight on one leg, and
     * none of that should trigger a rotation. What it separates is a leaning person from a
     * horizontal one, and those differ by nearer ninety degrees than by twenty.
     */
    const val MAX_LEAN_DEGREES = 45.0

    /**
     * The trunk's lean from straight down, in degrees, signed.
     *
     * Zero when the hips sit directly below the shoulders. Positive when the hips are to the
     * right of them, which is the direction a clockwise rotation of the image produces.
     */
    fun leanDegrees(geometry: FrontPoseGeometry): Double {
        val shoulderX = (geometry.shoulderLeft.x + geometry.shoulderRight.x) / 2.0
        val shoulderY = (geometry.shoulderLeft.y + geometry.shoulderRight.y) / 2.0
        val hipX = (geometry.hipLeft.x + geometry.hipRight.x) / 2.0
        val hipY = (geometry.hipLeft.y + geometry.hipRight.y) / 2.0

        // Image coordinates: y grows downward, so an upright trunk has a positive dy and the
        // reference direction is straight down rather than straight up.
        return Math.toDegrees(atan2(hipX - shoulderX, hipY - shoulderY))
    }

    /** Whether the subject stands upright enough in this frame to measure rows across. */
    fun isUpright(geometry: FrontPoseGeometry): Boolean =
        abs(leanDegrees(geometry)) <= MAX_LEAN_DEGREES

    /**
     * Quarter-turns clockwise that would stand this subject up.
     *
     * Zero when the frame is already upright, so the common case costs one comparison and no
     * pixels are touched.
     *
     * @return 0, 1, 2 or 3
     */
    fun quarterTurnsClockwise(geometry: FrontPoseGeometry): Int {
        val lean = leanDegrees(geometry)
        return when {
            abs(lean) <= MAX_LEAN_DEGREES -> 0
            // Hips to the right of the shoulders: the subject's head is to the image's left,
            // and turning the picture clockwise brings it up.
            lean > 0.0 && lean <= 135.0 -> 1
            lean < 0.0 && lean >= -135.0 -> 3
            // Past 135 either way the subject is inverted.
            else -> 2
        }
    }

    /**
     * Every rotation worth trying when the pose model finds nothing at all.
     *
     * In upright-first order, because a sideways body is far more common than an inverted
     * one, and because the first hit ends the search. Included so a photograph the model
     * cannot recognise at one rotation is not reported as "no person detected" when the
     * person is plainly there and merely lying down in the frame.
     */
    val SEARCH_ORDER = listOf(0, 1, 3, 2)
}
