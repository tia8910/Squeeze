package com.squeeze.core.scan

import kotlin.math.abs

/**
 * Talks a person into the position that gives the most accurate scan, one instruction at a time.
 *
 * Detection already refuses photos it cannot use at all. This goes further, for the live camera:
 * a photo can be usable and still cost accuracy, and every one of those costs is a position the
 * person can fix in a second if told. Head to feet in frame gives centimetres, a centred body
 * keeps lens distortion even on both sides, arms a hand's width out keep them out of the waist,
 * relaxed arms keep a flex out of the chest, level shoulders keep both sides at one height.
 *
 * One instruction at a time, the most important first, phrased so it can be spoken: the person
 * is three metres from the screen and listening, not reading.
 */
object PositionCoach {

    /** Where the body's centre may sit, as a share of the frame width. */
    private const val CENTRE_MIN = 0.36
    private const val CENTRE_MAX = 0.64

    /** Nose to ankle as a share of frame height: below this the person is too far away. */
    private const val MIN_BODY_SPAN = 0.55

    /** How close to the frame's top or bottom edge the head or feet may come. */
    private const val EDGE_MARGIN = 0.03

    /** A wrist closer than this to the hip line, in shoulder widths, is touching the side. */
    private const val SIDE_GAP = 0.35

    /** Shoulder heights differing by more than this share of the torso read as a tilt. */
    private const val TILT_SHARE = 0.12

    /**
     * @param pose the frame's landmarks
     * @param framing how much of the body is in frame
     * @param aspect the frame's width over its height
     * @param mirrored true for the front camera, whose picture is a mirror of the person's view,
     *   so left and right instructions are swapped to stay correct from where they stand
     * @param side true for the side-on photo, where arm and centring checks do not apply
     * @return the single most important instruction, or null when the position is right
     */
    fun advice(
        pose: FrontPoseGeometry,
        framing: ScanFraming,
        aspect: Double,
        mirrored: Boolean,
        side: Boolean = false,
    ): String? {
        val shoulderY = (pose.shoulderLeft.y + pose.shoulderRight.y) / 2.0
        val hipY = (pose.hipLeft.y + pose.hipRight.y) / 2.0
        val torso = hipY - shoulderY
        if (torso <= 0.0) return "Stand upright, facing the camera."

        // 1. Distance. Head to feet in frame is what turns a scan into centimetres.
        if (framing != ScanFraming.FULL_BODY) {
            return if (torso > 0.45) "Step back. I need to see you from head to feet." else "Step back a little, so your feet are in the picture."
        }
        val top = pose.nose?.y
        val bottom = listOfNotNull(pose.ankleLeft?.y, pose.ankleRight?.y).maxOrNull()
        if ((top != null && top < EDGE_MARGIN) || (bottom != null && bottom > 1.0 - EDGE_MARGIN)) {
            return "Step back a little. You are right at the edge."
        }
        if (top != null && bottom != null && bottom - top < MIN_BODY_SPAN) {
            return "Come one step closer."
        }

        if (side) return null

        // 2. Centred, so the lens treats both sides alike.
        val centre = (pose.shoulderLeft.x + pose.shoulderRight.x + pose.hipLeft.x + pose.hipRight.x) / 4.0
        if (centre < CENTRE_MIN || centre > CENTRE_MAX) {
            // Body on the picture's left. Seen from the rear camera, that is the person's right,
            // so they move to their left to reach the middle; the front camera is the mirror.
            val towardOwnLeft = (centre < CENTRE_MIN) != mirrored
            return if (towardOwnLeft) "Take a small step to your left." else "Take a small step to your right."
        }

        // 3. Arms: relaxed, not flexed or raised, and clear of the waist.
        if (PhotoGirthCheck.armRaised(pose, aspect)) return "Relax your arms and lower them. No flexing."
        if (PhotoGirthCheck.armAcrossBody(pose)) return "Move your arms away from the front of your body."
        val shoulderWidth = abs(pose.shoulderLeft.x - pose.shoulderRight.x)
        if (shoulderWidth > 0.0) {
            val hipLeftX = minOf(pose.hipLeft.x, pose.hipRight.x)
            val hipRightX = maxOf(pose.hipLeft.x, pose.hipRight.x)
            val touching = listOfNotNull(pose.wristLeft, pose.wristRight).any { w ->
                val gap = if (w.x < (hipLeftX + hipRightX) / 2) hipLeftX - w.x else w.x - hipRightX
                gap < SIDE_GAP * shoulderWidth
            }
            if (touching) return "Lift your arms a hand's width away from your sides."
        }

        // 4. Level shoulders.
        if (abs(pose.shoulderLeft.y - pose.shoulderRight.y) > TILT_SHARE * torso) {
            return "Stand straight, with your shoulders level."
        }
        return null
    }
}
