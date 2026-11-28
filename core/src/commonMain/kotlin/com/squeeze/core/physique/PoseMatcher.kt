package com.squeeze.core.physique

import com.squeeze.core.form.Joint
import kotlin.math.abs

/** The poses of a physique check, in the order they are taken. */
enum class PhysiquePose(val label: String, val intro: String) {
    FRONT("Front", "Face the camera, arms a little away from your sides."),
    QUARTER("Quarter turn", "Turn a quarter to your right, about halfway to side on."),
    SIDE("Side", "Turn side on to the camera, arms relaxed."),
    BACK("Back", "Turn your back to the camera, arms a little away from your sides."),
}

/**
 * How the latest frame compares with the pose asked for.
 *
 * @param cue what to say, when something needs fixing; null when the pose is right
 * @param fix the landmark to mark on screen, where the fix is needed
 */
data class PoseMatch(val ok: Boolean, val cue: String?, val fix: Int?)

/**
 * Checks a body against one of the physique check poses, from pose landmarks.
 *
 * The same pose, the same distance and the same framing every week is what makes two photos
 * comparable, so this is strict about framing and lenient about everything the camera cannot
 * judge well. It returns the first problem only: one instruction at a time is easier to follow
 * from three metres away than a list.
 *
 * Which way the body faces comes from how wide the shoulders look against the torso: square on
 * (front or back) they look wide, side on they nearly overlap. Front and back look alike to the
 * model, so those two are told apart by the order of the check, not by the picture.
 *
 * Landmarks follow the 33 point pose model, with x and y as fractions of an upright frame.
 */
object PoseMatcher {

    fun match(pose: PhysiquePose, j: Map<Int, Joint>): PoseMatch {
        fun seen(i: Int) = (j[i]?.visibility ?: 0.0) >= MIN_VISIBILITY

        if (!seen(L_SHOULDER) && !seen(R_SHOULDER)) {
            return PoseMatch(false, "Step into the picture so I can see you.", null)
        }
        val ls = j[L_SHOULDER]
        val rs = j[R_SHOULDER]
        val lh = j[L_HIP]
        val rh = j[R_HIP]
        if (ls == null || rs == null || lh == null || rh == null || !seen(L_HIP) && !seen(R_HIP)) {
            return PoseMatch(false, "Step back so I can see your whole body.", null)
        }
        // Feet in the picture: one ankle is enough when side on, where the far one is hidden.
        val ankles = listOfNotNull(j[L_ANKLE]?.takeIf { seen(L_ANKLE) }, j[R_ANKLE]?.takeIf { seen(R_ANKLE) })
        if (ankles.isEmpty() || ankles.any { it.y > BOTTOM_EDGE }) {
            return PoseMatch(false, "Step back a little so I can see your feet.", if (seen(L_ANKLE)) L_ANKLE else R_ANKLE)
        }
        val head = j[NOSE]?.takeIf { seen(NOSE) }
        val topY = minOf(head?.y ?: 1.0, ls.y, rs.y)
        if (topY < TOP_EDGE) {
            return PoseMatch(false, "Step back a little so your head is in the picture.", NOSE)
        }

        val shoulderY = (ls.y + rs.y) / 2
        val hipY = (lh.y + rh.y) / 2
        val ankleY = ankles.maxOf { it.y }
        val bodyHeight = ankleY - minOf(topY, shoulderY)
        if (bodyHeight < MIN_BODY_HEIGHT) {
            return PoseMatch(false, "Come a little closer to the camera.", null)
        }

        val centreX = (lh.x + rh.x) / 2
        if (centreX < CENTRE_MIN || centreX > CENTRE_MAX) {
            return PoseMatch(false, "Move to the middle of the picture.", null)
        }

        val torso = (hipY - shoulderY).coerceAtLeast(1e-6)
        val width = abs(ls.x - rs.x) / torso
        when (pose) {
            PhysiquePose.FRONT, PhysiquePose.BACK -> if (width < SQUARE_MIN) {
                val cue = if (pose == PhysiquePose.FRONT) "Turn to face the camera." else "Turn your back square to the camera."
                return PoseMatch(false, cue, null)
            }
            PhysiquePose.QUARTER -> {
                if (width > QUARTER_MAX) return PoseMatch(false, "Turn a little more, about halfway to side on.", null)
                if (width < QUARTER_MIN) return PoseMatch(false, "Turn back a little toward the camera.", null)
            }
            PhysiquePose.SIDE -> if (width > SIDE_MAX) {
                return PoseMatch(false, "Turn a little more, side on to the camera.", null)
            }
        }

        // Arms clear of the body, so the waist line can be seen.
        if (pose == PhysiquePose.FRONT || pose == PhysiquePose.BACK) {
            val shoulderWidth = abs(ls.x - rs.x)
            for ((wrist, hip, other) in listOf(Triple(L_WRIST, lh, rh), Triple(R_WRIST, rh, lh))) {
                val w = j[wrist]?.takeIf { seen(wrist) } ?: continue
                // Outward is away from the other hip.
                val outward = if (hip.x >= other.x) w.x - hip.x else hip.x - w.x
                if (outward < ARM_CLEARANCE * shoulderWidth) {
                    return PoseMatch(false, "Arms a little away from your sides.", wrist)
                }
            }
        }

        return PoseMatch(true, null, null)
    }

    const val MIN_VISIBILITY = 0.5
    const val TOP_EDGE = 0.03
    const val BOTTOM_EDGE = 0.97
    const val MIN_BODY_HEIGHT = 0.5
    const val CENTRE_MIN = 0.3
    const val CENTRE_MAX = 0.7
    const val SQUARE_MIN = 0.5
    const val QUARTER_MIN = 0.22
    const val QUARTER_MAX = 0.5
    const val SIDE_MAX = 0.22
    const val ARM_CLEARANCE = 0.08

    const val NOSE = 0
    const val L_SHOULDER = 11
    const val R_SHOULDER = 12
    const val L_WRIST = 15
    const val R_WRIST = 16
    const val L_HIP = 23
    const val R_HIP = 24
    const val L_ANKLE = 27
    const val R_ANKLE = 28
}
