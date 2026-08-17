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

    /**
     * Shoulders and waist in shot; the hips are not.
     *
     * The shape figure only, and read off the shoulder denominator — which is the weaker of
     * the two, for the reason [SilhouetteBodyFat] spends a paragraph on: at the shoulder line
     * the arms are attached, so the run there spans deltoid to deltoid and is not a torso
     * width. That path already existed and already carries its own wider interval; what is
     * new is that a photograph can now reach it.
     *
     * The alternative was refusing the photograph, and refusing was the behaviour that
     * prompted this: a picture with the whole upper body plainly in it came back "your
     * shoulders and hips were not both in the picture, so there is no waist to measure",
     * which is not true. The waist is in the picture. Only the hip is not, and the hip is a
     * denominator rather than the measurement.
     *
     * **No centimetres, and no hip.** The stature is not in shot, and neither is the pelvis —
     * see [UpperBodyFraming] for why the hip must be dropped rather than clamped back into
     * frame.
     */
    UPPER_BODY,
    ;

    /** Whether a scan at this framing can produce measurements in centimetres. */
    val yieldsCentimetres: Boolean get() = this == FULL_BODY

    /**
     * Whether the pelvis is in the picture.
     *
     * The gate on everything read from the hips — the hip width the shape ratio prefers, and
     * the hip line the posture findings report. At [UPPER_BODY] the hip landmarks exist but
     * were inferred, and inferred landmarks are usable for placing a band and for nothing
     * else.
     *
     * Asked rather than inferred from the enum at each call site, because getting it wrong is
     * silent both ways. The hip band would clamp back to the bottom row of a cropped
     * photograph and return the width of the abdomen at the crop — waist over waist is 1.0,
     * and 1.0 through the hip anchors is about thirty-five per cent. The hip *line* would
     * come back perfectly level, because a pose prior is level by construction.
     */
    val hipsInShot: Boolean get() = this != UPPER_BODY
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

/**
 * The last framing the scan will accept: shoulders and waist in shot, hips out of it.
 *
 * **Why this exists.** A pose model does not stop at the edge of the picture. MediaPipe's
 * landmarks are normalised coordinates and it happily reports a hip at y = 1.15 — below the
 * bottom row — when the hip is out of frame but the body's geometry implies where it went.
 * [TorsoFraming] reads that as a cropped hip and refuses, which is right for what it is
 * guarding: at that framing the hip is a *measurement*, and measuring a joint that is not in
 * the picture is exactly the failure this pipeline keeps having.
 *
 * But the hip is not the only thing the shape figure can be read from. [SilhouetteBodyFat]
 * has always had a shoulder denominator, has always carried a wider interval for it, and
 * needs nothing below the ribs. So the honest position on a photograph framed from the head
 * to the navel is not "there is no waist to measure" — there plainly is one — it is "there is
 * a waist, no pelvis, and therefore the weaker of the two readings".
 *
 * **What is not relaxed.** The extrapolated hip is used to place the bands and for nothing
 * else. It never becomes a width: [ScanFraming.hipsInShot] is false here, and dropping
 * the hip is the point rather than an omission. Left in, the hip band would clamp back to
 * the bottom rows of the photograph — the crop, not the pelvis — and those rows are abdomen,
 * a width near the waist's own. A waist-to-hip near 1.0 reads about thirty-five per cent, so
 * the failure would not look like a failure. It would look like a fat man.
 */
object UpperBodyFraming {

    /**
     * How far from the frame edge the shoulders must sit.
     *
     * The same margin [TorsoFraming] applies, and applied to the shoulders for the same
     * reason: the landmark is the joint centre and the deltoid is outside it, so a shoulder
     * on the edge means the widest part of the shoulder is not in the picture — and the
     * shoulder is the denominator of everything this framing can report.
     */
    const val EDGE_MARGIN = TorsoFraming.EDGE_MARGIN

    /**
     * @return true when the shoulders are properly in shot and the waist band the shape
     *   reading is taken from falls inside the picture
     */
    fun supports(geometry: FrontPoseGeometry): Boolean {
        val shoulderYs = listOf(geometry.shoulderLeft.y, geometry.shoulderRight.y)
        val shoulderXs = listOf(geometry.shoulderLeft.x, geometry.shoulderRight.x)
        if (shoulderYs.any { it < EDGE_MARGIN || it > 1.0 - EDGE_MARGIN }) return false
        if (shoulderXs.any { it < EDGE_MARGIN || it > 1.0 - EDGE_MARGIN }) return false

        val shoulder = (geometry.shoulderLeft.y + geometry.shoulderRight.y) / 2.0
        val hip = (geometry.hipLeft.y + geometry.hipRight.y) / 2.0
        val trunk = hip - shoulder
        if (trunk < TorsoFraming.MIN_TRUNK_HEIGHT_FRACTION) return false

        // **The one condition, and it is the literal question.** Is the waist in the
        // photograph. The band comes from the same constants the measurement reads it from,
        // so this cannot drift out of agreement with what is later measured — move the band
        // and this moves with it.
        //
        // It doubles as the bound on how far the hip may be extrapolated, which is why there
        // is no separate constant for that. The waist band ends at
        // [SilhouetteBodyFat.WAIST_BAND_END] of the trunk, so requiring it inside the picture
        // requires the visible part of the trunk to be at least that fraction of the whole —
        // which caps the hip at about a quarter of a trunk outside the frame, and tighter on
        // a shorter trunk. Past that the photograph is a chest shot, and a chest shot has no
        // waist in it whatever the landmarks claim.
        val waistBottom = shoulder + trunk * SilhouetteBodyFat.WAIST_BAND_END
        return waistBottom <= 1.0 - EDGE_MARGIN
    }

    /**
     * Anchors for an upper-body photograph.
     *
     * [TorsoFraming.anchorsFor] unchanged, and deliberately so — the arithmetic that turns
     * landmarks into rows does not care whether the hip row lands inside the picture, and
     * [PoseAnchors] only requires the rows be in anatomical order. A second copy of it here
     * would be two things to keep in step for no difference in behaviour.
     *
     * The hip row it returns may sit below the last row of the mask. Every search clamps to
     * the silhouette's own extent, so a band that starts outside the picture simply finds
     * nothing, which is the correct answer.
     */
    fun anchorsFor(geometry: FrontPoseGeometry, rowCount: Int): PoseAnchors? =
        TorsoFraming.anchorsFor(geometry, rowCount)
}
