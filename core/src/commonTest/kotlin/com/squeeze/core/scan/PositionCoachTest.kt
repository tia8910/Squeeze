package com.squeeze.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PositionCoachTest {

    // Head to feet, centred, arms a hand's width out, shoulders level.
    private val ideal = FrontPoseGeometry(
        shoulderLeft = PosePoint(0.42, 0.25),
        shoulderRight = PosePoint(0.58, 0.25),
        hipLeft = PosePoint(0.45, 0.52),
        hipRight = PosePoint(0.55, 0.52),
        ankleLeft = PosePoint(0.46, 0.92),
        ankleRight = PosePoint(0.54, 0.92),
        nose = PosePoint(0.50, 0.12),
        elbowLeft = PosePoint(0.37, 0.40),
        elbowRight = PosePoint(0.63, 0.40),
        wristLeft = PosePoint(0.33, 0.53),
        wristRight = PosePoint(0.67, 0.53),
    )

    private fun advise(pose: FrontPoseGeometry, framing: ScanFraming = ScanFraming.FULL_BODY, mirrored: Boolean = false) =
        PositionCoach.advice(pose, framing, aspect = 0.56, mirrored = mirrored)

    @Test
    fun `the ideal position needs no instruction`() {
        assertNull(advise(ideal))
    }

    @Test
    fun `a waist-up frame is asked to step back`() {
        assertTrue(advise(ideal, ScanFraming.TORSO)!!.startsWith("Step back"))
    }

    @Test
    fun `a body far off to one side is sent toward the middle, mirrored for the front camera`() {
        val left = ideal.copy(
            shoulderLeft = PosePoint(0.17, 0.25), shoulderRight = PosePoint(0.33, 0.25),
            hipLeft = PosePoint(0.20, 0.52), hipRight = PosePoint(0.30, 0.52),
            elbowLeft = PosePoint(0.12, 0.40), elbowRight = PosePoint(0.38, 0.40),
            wristLeft = PosePoint(0.08, 0.53), wristRight = PosePoint(0.42, 0.53),
        )
        assertEquals("Take a small step to your left.", advise(left))
        assertEquals("Take a small step to your right.", advise(left, mirrored = true))
    }

    @Test
    fun `a flex is asked to relax`() {
        val flex = ideal.copy(elbowRight = PosePoint(0.78, 0.27), wristRight = PosePoint(0.66, 0.17))
        assertEquals("Relax your arms and lower them. No flexing.", advise(flex))
    }

    @Test
    fun `arms resting on the hips are asked to lift away`() {
        val resting = ideal.copy(wristLeft = PosePoint(0.43, 0.53), wristRight = PosePoint(0.57, 0.53))
        assertEquals("Lift your arms a hand's width away from your sides.", advise(resting))
    }
}
