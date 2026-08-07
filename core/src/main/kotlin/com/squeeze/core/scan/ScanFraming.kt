package com.squeeze.core.scan

/**
 * How much of the body a photograph contains, and therefore what it can be asked.
 *
 * The scan used to accept one framing and reject everything else: head and feet both in
 * shot, or nothing. That requirement was never about seeing the whole body — it was about
 * one number. Converting pixels to centimetres needs the subject's stature in pixels, and
 * stature is only in the picture when the crown and the feet both are.
 *
 * But the app's primary figure no longer goes through centimetres. [SilhouetteBodyFat]
 * divides one silhouette width by another in the same image, and that cancels scale exactly
 * — it never asks how tall anything is. Every ratio the scan reports is the same. So a
 * photograph framed on the trunk can answer the questions that matter, and forcing the user
 * two metres back to satisfy an equation they are no longer relying on costs them the thing
 * they actually need: pixels on the midsection.
 *
 * A trunk-framed photo puts three to four times as many pixels across the waist as a
 * full-length one at the same resolution. It is also far easier to hold the arms clear of
 * the body inside a closer frame, which is the largest error this pipeline has.
 */
enum class ScanFraming {
    /**
     * Crown and feet both in shot.
     *
     * Everything: circumferences in centimetres, every ratio, and the shape figure.
     */
    FULL_BODY,

    /**
     * Shoulders and hips in shot; head and feet may not be.
     *
     * Ratios and the shape figure only. No centimetres, because the stature they would be
     * scaled from is not in the photograph — and inventing one from a cropped silhouette is
     * precisely the failure the full-body rule existed to prevent. The rule is kept; what
     * changes is that failing it no longer rejects the photo, it narrows what is claimed
     * from it.
     */
    TORSO,
    ;

    /** Whether a scan at this framing can produce measurements in centimetres. */
    val yieldsCentimetres: Boolean get() = this == FULL_BODY
}

/**
 * Whether a photograph is framed tightly enough to measure the trunk, and where its
 * landmarks sit once it is.
 *
 * Deliberately stricter about the trunk than the full-body path is about the body. A
 * full-length photo that clips an ankle still has most of a person in it; a trunk photo that
 * clips a hip has lost one end of every measurement it was going to take.
 */
object TorsoFraming {

    /**
     * How far from the frame edge the shoulders and hips must sit.
     *
     * Larger than the full-body margin. Pose landmarks are joint centres, and the body
     * extends past them — a hip landmark exactly on the edge means the actual hip is outside
     * the picture, and the silhouette there is a straight line where the crop is rather than
     * where the person is.
     */
    const val EDGE_MARGIN = 0.04

    /**
     * The least of the frame's height the shoulder-to-hip span may occupy.
     *
     * Below this the subject is far enough away that the framing is really a full-body shot
     * that failed for some other reason, and accepting it as a trunk scan would hand back a
     * worse measurement than the rejection it replaced.
     */
    const val MIN_TRUNK_HEIGHT_FRACTION = 0.14

    /**
     * Where the chin sits above the shoulders, as a fraction of the trunk span.
     *
     * Synthesised because in a trunk photo the head is usually out of shot. It exists only
     * to satisfy [PoseAnchors]' ordering requirement — the neck search that would use it is
     * not run at this framing, because a neck cannot be measured without a scale to convert
     * it and the landmark is not in the picture in any case.
     */
    private const val CHIN_ABOVE_SHOULDER = 0.25

    /**
     * Where the knees sit below the hips, as a fraction of the trunk span.
     *
     * Also synthesised, and unlike the chin this one is load-bearing: the hip width is read
     * from a band whose depth is a fraction of the hip-to-knee distance. Hip-to-knee runs a
     * little longer than shoulder-to-hip across adults, and because only 18% of it is used
     * the band is shallow enough that being wrong by a tenth moves nothing.
     */
    private const val KNEE_BELOW_HIP = 1.2

    /**
     * @return true when both shoulders and both hips are inside the frame with margin, and
     *   the trunk is large enough in it to measure
     */
    fun supports(geometry: FrontPoseGeometry): Boolean {
        val ys = listOf(
            geometry.shoulderLeft.y,
            geometry.shoulderRight.y,
            geometry.hipLeft.y,
            geometry.hipRight.y,
        )
        val xs = listOf(
            geometry.shoulderLeft.x,
            geometry.shoulderRight.x,
            geometry.hipLeft.x,
            geometry.hipRight.x,
        )
        if (ys.any { it < EDGE_MARGIN || it > 1.0 - EDGE_MARGIN }) return false
        if (xs.any { it < EDGE_MARGIN || it > 1.0 - EDGE_MARGIN }) return false

        val shoulder = (geometry.shoulderLeft.y + geometry.shoulderRight.y) / 2.0
        val hip = (geometry.hipLeft.y + geometry.hipRight.y) / 2.0
        return hip - shoulder >= MIN_TRUNK_HEIGHT_FRACTION
    }

    /**
     * Anchors for a trunk-framed photograph, with the chin and knees inferred.
     *
     * @param rowCount height of the coordinate space the rows are expressed in
     * @return null when the shoulders sit on the very top row, leaving nowhere to put a
     *   chin above them
     */
    fun anchorsFor(geometry: FrontPoseGeometry, rowCount: Int): PoseAnchors? {
        if (rowCount <= 0) return null

        val shoulderRow = ((geometry.shoulderLeft.y + geometry.shoulderRight.y) / 2.0 * rowCount)
            .toInt()
        val hipRow = ((geometry.hipLeft.y + geometry.hipRight.y) / 2.0 * rowCount).toInt()
        val trunk = hipRow - shoulderRow
        if (trunk <= 0 || shoulderRow <= 0) return null

        val chinRow = (shoulderRow - maxOf(1, (trunk * CHIN_ABOVE_SHOULDER).toInt()))
            .coerceAtLeast(0)

        // Allowed to fall past the bottom of the picture, and that is not an oversight. The
        // knee is never read as a row — it only sets how deep below the hip the hip-width
        // band reaches, and the searches clamp their range to the silhouette's own extent.
        // Refusing here would reject almost every trunk photo, since a frame that ends at
        // the hips has no room below them by construction.
        val kneeRow = hipRow + maxOf(1, (trunk * KNEE_BELOW_HIP).toInt())

        return runCatching {
            PoseAnchors(
                shoulderRow = shoulderRow,
                hipRow = hipRow,
                kneeRow = kneeRow,
                chinRow = chinRow,
            )
        }.getOrNull()
    }
}
