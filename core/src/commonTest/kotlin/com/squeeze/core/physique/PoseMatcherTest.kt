package com.squeeze.core.physique

import com.squeeze.core.form.Joint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PoseMatcherTest {

    /** A person standing square on, centred, arms a little out, whole body in the frame. */
    private fun square(
        shoulderHalf: Double = 0.1,
        wristOut: Double = 0.07,
        ankleY: Double = 0.9,
        noseY: Double = 0.15,
        shift: Double = 0.0,
    ): MutableMap<Int, Joint> = mutableMapOf(
        0 to Joint(0.5 + shift, noseY),
        11 to Joint(0.5 - shoulderHalf + shift, 0.3),
        12 to Joint(0.5 + shoulderHalf + shift, 0.3),
        15 to Joint(0.43 - wristOut + shift, 0.55),
        16 to Joint(0.57 + wristOut + shift, 0.55),
        23 to Joint(0.43 + shift, 0.55),
        24 to Joint(0.57 + shift, 0.55),
        27 to Joint(0.45 + shift, ankleY),
        28 to Joint(0.55 + shift, ankleY),
    )

    @Test
    fun a_good_front_pose_matches() {
        val m = PoseMatcher.match(PhysiquePose.FRONT, square())
        assertTrue(m.ok)
        assertEquals(null, m.cue)
    }

    @Test
    fun nobody_in_the_frame_asks_to_step_in() {
        val m = PoseMatcher.match(PhysiquePose.FRONT, emptyMap())
        assertFalse(m.ok)
        assertEquals("Step into the picture so I can see you.", m.cue)
    }

    @Test
    fun feet_cut_off_asks_to_step_back() {
        val m = PoseMatcher.match(PhysiquePose.FRONT, square(ankleY = 0.99))
        assertEquals("Step back a little so I can see your feet.", m.cue)
        assertEquals(27, m.fix)
    }

    @Test
    fun hidden_ankles_ask_to_step_back() {
        val j = square()
        j[27] = Joint(0.45, 0.9, visibility = 0.1)
        j[28] = Joint(0.55, 0.9, visibility = 0.1)
        assertEquals("Step back a little so I can see your feet.", PoseMatcher.match(PhysiquePose.FRONT, j).cue)
    }

    @Test
    fun head_cut_off_asks_to_step_back() {
        assertEquals(
            "Step back a little so your head is in the picture.",
            PoseMatcher.match(PhysiquePose.FRONT, square(noseY = 0.01)).cue,
        )
    }

    @Test
    fun too_far_away_asks_to_come_closer() {
        val j = square(ankleY = 0.6, noseY = 0.25)
        assertEquals("Come a little closer to the camera.", PoseMatcher.match(PhysiquePose.FRONT, j).cue)
    }

    @Test
    fun off_centre_asks_to_move_to_the_middle() {
        assertEquals(
            "Move to the middle of the picture.",
            PoseMatcher.match(PhysiquePose.FRONT, square(shift = 0.3)).cue,
        )
    }

    @Test
    fun arms_against_the_body_are_called_out() {
        val m = PoseMatcher.match(PhysiquePose.FRONT, square(wristOut = 0.0))
        assertEquals("Arms a little away from your sides.", m.cue)
        assertEquals(15, m.fix)
    }

    @Test
    fun front_pose_turned_away_asks_to_face_the_camera() {
        val m = PoseMatcher.match(PhysiquePose.FRONT, square(shoulderHalf = 0.04))
        assertEquals("Turn to face the camera.", m.cue)
    }

    @Test
    fun quarter_turn_checks_the_angle_both_ways() {
        assertEquals(
            "Turn a little more, about halfway to side on.",
            PoseMatcher.match(PhysiquePose.QUARTER, square()).cue,
        )
        assertEquals(
            "Turn back a little toward the camera.",
            PoseMatcher.match(PhysiquePose.QUARTER, square(shoulderHalf = 0.01)).cue,
        )
        assertTrue(PoseMatcher.match(PhysiquePose.QUARTER, square(shoulderHalf = 0.05)).ok)
    }

    @Test
    fun side_pose_needs_the_shoulders_nearly_in_line() {
        assertEquals(
            "Turn a little more, side on to the camera.",
            PoseMatcher.match(PhysiquePose.SIDE, square(shoulderHalf = 0.05)).cue,
        )
        // Side on, the far ankle is often hidden: one is enough.
        val j = square(shoulderHalf = 0.01)
        j[28] = Joint(0.55, 0.9, visibility = 0.1)
        assertTrue(PoseMatcher.match(PhysiquePose.SIDE, j).ok)
    }

    @Test
    fun back_pose_needs_arms_clear_too() {
        assertTrue(PoseMatcher.match(PhysiquePose.BACK, square()).ok)
        assertEquals(
            "Arms a little away from your sides.",
            PoseMatcher.match(PhysiquePose.BACK, square(wristOut = 0.0)).cue,
        )
    }
}
