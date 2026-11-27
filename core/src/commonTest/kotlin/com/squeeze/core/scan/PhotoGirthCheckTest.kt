package com.squeeze.core.scan

import com.squeeze.core.model.Circumferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoGirthCheckTest {

    // Shoulders at y 0.25, hips at y 0.55, shoulder width 0.2, arms hanging at the sides.
    private val standing = FrontPoseGeometry(
        shoulderLeft = PosePoint(0.40, 0.25),
        shoulderRight = PosePoint(0.60, 0.25),
        hipLeft = PosePoint(0.43, 0.55),
        hipRight = PosePoint(0.57, 0.55),
        elbowLeft = PosePoint(0.38, 0.40),
        elbowRight = PosePoint(0.62, 0.40),
    )

    private val girths = Circumferences(
        neckCm = 39.0, waistCm = 80.0, hipCm = 96.0, chestCm = 100.0,
        thighCm = 56.0, armCm = 34.0, calfCm = 37.0,
    )

    @Test
    fun `a clean standing photo keeps every girth`() {
        val check = PhotoGirthCheck.check(girths, standing, emptyMap(), 178.0, female = false)
        assertEquals(girths, check.circumferences)
        assertTrue(check.withheld.isEmpty())
        assertTrue(check.untrusted.isEmpty())
    }

    @Test
    fun `a raised flexed arm drops chest and arm and skips back width`() {
        val flex = standing.copy(elbowRight = PosePoint(0.75, 0.18))
        val check = PhotoGirthCheck.check(girths, flex, emptyMap(), 178.0, female = false)
        assertNull(check.circumferences.chestCm)
        assertNull(check.circumferences.armCm)
        assertEquals(80.0, check.circumferences.waistCm)
        assertTrue(MuscleGroup.V_TAPER in check.untrusted)
        assertTrue(PhotoIssue.ARM_RAISED in check.issues)
    }

    @Test
    fun `a mirror flex with the elbow just under the shoulder still counts as raised`() {
        // The real scan: upper arm out to the side, elbow a little below shoulder height, fist
        // above the shoulder. The first version only looked at the elbow and missed it.
        val flex = standing.copy(
            elbowRight = PosePoint(0.82, 0.28),
            wristRight = PosePoint(0.70, 0.18),
        )
        val check = PhotoGirthCheck.check(girths, flex, emptyMap(), 178.0, female = false, aspect = 0.56)
        assertTrue(PhotoIssue.ARM_RAISED in check.issues)
        assertTrue(MuscleGroup.ARMS in check.untrusted)
        assertNull(check.circumferences.armCm)
    }

    @Test
    fun `arms held a little clear of the sides are not a flex`() {
        val clear = standing.copy(
            elbowLeft = PosePoint(0.36, 0.40), wristLeft = PosePoint(0.34, 0.52),
            elbowRight = PosePoint(0.64, 0.40), wristRight = PosePoint(0.66, 0.52),
        )
        val check = PhotoGirthCheck.check(girths, clear, emptyMap(), 178.0, female = false, aspect = 0.56)
        assertTrue(check.issues.isEmpty())
    }

    @Test
    fun `an arm across the chest drops the chest only`() {
        val across = standing.copy(elbowLeft = PosePoint(0.49, 0.38))
        val check = PhotoGirthCheck.check(girths, across, emptyMap(), 178.0, female = false)
        assertNull(check.circumferences.chestCm)
        assertEquals(34.0, check.circumferences.armCm)
    }

    @Test
    fun `covered legs drop thigh and calf`() {
        val check = PhotoGirthCheck.check(
            girths, standing, mapOf(MuscleGroup.LEGS to 0.1, MuscleGroup.CHEST to 0.9), 178.0, female = false,
        )
        assertNull(check.circumferences.thighCm)
        assertNull(check.circumferences.calfCm)
        assertEquals(96.0, check.circumferences.hipCm)
        assertTrue(MuscleGroup.LEGS in check.untrusted)
    }

    @Test
    fun `readings no body produces are left out`() {
        // The scan that prompted this: chest 1.6 times the waist, a hip far wider than a man's
        // waist allows, and a calf thinner than an adult's.
        val broken = girths.copy(waistCm = 75.5, chestCm = 122.7, hipCm = 106.0, calfCm = 28.2)
        val check = PhotoGirthCheck.check(broken, null, emptyMap(), 178.0, female = false)
        assertNull(check.circumferences.chestCm)
        assertNull(check.circumferences.hipCm)
        assertNull(check.circumferences.calfCm)
        assertEquals(75.5, check.circumferences.waistCm)
    }
}
