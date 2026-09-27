package com.squeeze.core.scan

import com.squeeze.core.bodycomp.MeasuredParts
import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Goal
import com.squeeze.core.model.Sex
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhysiqueFusionTest {

    // The two real scans four days apart at 70 kg that prompted this: same body, and the
    // model's arms went 56 → 15, back width 38 → 4.
    private val sept23 = PhysiqueObservation(
        epochDay = 100,
        scores = mapOf(
            MuscleGroup.ABS to 0.68, MuscleGroup.ARMS to 0.56, MuscleGroup.V_TAPER to 0.38,
            MuscleGroup.SHOULDERS to 0.33, MuscleGroup.CHEST to 0.16,
        ),
        hidden = setOf(MuscleGroup.LEGS),
    )
    private val sept27 = PhysiqueObservation(
        epochDay = 104,
        scores = mapOf(
            MuscleGroup.ABS to 0.47, MuscleGroup.CHEST to 0.29, MuscleGroup.SHOULDERS to 0.19,
            MuscleGroup.ARMS to 0.15, MuscleGroup.V_TAPER to 0.04,
        ),
    )

    @Test
    fun `a first scan stands alone`() {
        val fused = PhysiqueFusion.fuse(listOf(sept23))
        val arms = fused.getValue(MuscleGroup.ARMS)
        assertEquals(0.56, arms.score, 1e-9)
        assertEquals(1, arms.reads)
        assertNull(arms.change)
        assertFalse(arms.confirmed)
    }

    @Test
    fun `a second photograph four days later moves the estimate about half way, not all the way`() {
        val arms = PhysiqueFusion.fuse(listOf(sept27, sept23)).getValue(MuscleGroup.ARMS)
        assertEquals(2, arms.reads)
        assertEquals(0.15, arms.latest, 1e-9)
        assertTrue(arms.score in 0.30..0.40, "fused ${arms.score}")
        assertEquals(100L, arms.since)
    }

    @Test
    fun `the swings between those two scans are within scan noise, so none is reported as change`() {
        val fused = PhysiqueFusion.fuse(listOf(sept23, sept27))
        fused.values.forEach { assertFalse(it.confirmed, "$it") }
    }

    @Test
    fun `a change that keeps showing up is followed and confirmed`() {
        val history = (0 until 6).map { PhysiqueObservation(it * 7L, mapOf(MuscleGroup.ARMS to 0.30)) } +
            PhysiqueObservation(60, mapOf(MuscleGroup.ARMS to 0.80))
        val arms = PhysiqueFusion.fuse(history).getValue(MuscleGroup.ARMS)
        assertTrue(arms.confirmed)
        assertTrue(arms.change!! > 0.0)
    }

    @Test
    fun `many consistent reads narrow the estimate`() {
        val one = PhysiqueFusion.fuse(listOf(sept23)).getValue(MuscleGroup.ABS).sd
        val many = PhysiqueFusion.fuse(
            (0 until 8).map { PhysiqueObservation(it * 3L, mapOf(MuscleGroup.ABS to 0.6)) },
        ).getValue(MuscleGroup.ABS).sd
        assertTrue(many < one / 2)
    }

    @Test
    fun `a read with a phone over the chest moves the estimate less`() {
        val before = PhysiqueObservation(0, mapOf(MuscleGroup.CHEST to 0.5))
        val clear = PhysiqueFusion.fuse(listOf(before, PhysiqueObservation(4, mapOf(MuscleGroup.CHEST to 0.2))))
        val covered = PhysiqueFusion.fuse(
            listOf(before, PhysiqueObservation(4, mapOf(MuscleGroup.CHEST to 0.2), noise = mapOf(MuscleGroup.CHEST to 1.6))),
        )
        assertTrue(
            abs(covered.getValue(MuscleGroup.CHEST).score - 0.5) < abs(clear.getValue(MuscleGroup.CHEST).score - 0.5),
        )
    }

    @Test
    fun `a group hidden in the newest photograph is not reported from old ones`() {
        val newest = PhysiqueObservation(110, mapOf(MuscleGroup.ABS to 0.5), hidden = setOf(MuscleGroup.ARMS))
        assertFalse(MuscleGroup.ARMS in PhysiqueFusion.fuse(listOf(sept23, newest)))
    }

    @Test
    fun `a marked measured taper is never a lagging back width, and the bar agrees with the strength`() {
        val girths = Circumferences(chestCm = 97.5, waistCm = 65.0) // 1.50×
        val measured = MeasuredParts.scores(girths, Sex.MALE)
        val (strong, weak) = MeasuredParts.from(girths, Sex.MALE)
        val report = assertNotNull(
            PhysiqueAnalysis.report(sept27.scores, Goal.RECOMP, measuredStrong = strong, measuredWeak = weak, measuredScores = measured),
        )
        val back = report.scores.first { it.group == MuscleGroup.V_TAPER }
        assertTrue(back.measured)
        assertTrue(back.development != Development.LAGGING)
        assertTrue(MuscleGroup.V_TAPER in report.strengths)
        assertEquals(0.04, report.raw.getValue(MuscleGroup.V_TAPER), 1e-9)
    }

    @Test
    fun `a chest too wide to be real gives back width no measured score and no strength`() {
        // The real 27 Sept scan: 116.7 cm chest, 72 cm waist — 1.62×, the phone arm counted as chest.
        val girths = Circumferences(chestCm = 116.7, waistCm = 72.0)
        assertTrue(MeasuredParts.scores(girths, Sex.MALE).isEmpty())
        assertFalse(MuscleGroup.V_TAPER in MeasuredParts.from(girths, Sex.MALE).first)
    }

    @Test
    fun `a measured strength never shows a lagging label even without a measured score`() {
        val report = assertNotNull(
            PhysiqueAnalysis.report(
                sept27.scores, Goal.RECOMP,
                measuredStrong = mapOf(MuscleGroup.V_TAPER to "Chest 1.62× your waist."),
            ),
        )
        assertTrue(MuscleGroup.V_TAPER in report.strengths)
        assertTrue(report.scores.none { it.group in report.strengths && it.development == Development.LAGGING })
    }

    @Test
    fun `the taper score sits on the AI's own lagging and developed edges`() {
        val flat = MeasuredParts.scores(Circumferences(chestCm = 115.0, waistCm = 100.0), Sex.MALE)
        val marked = MeasuredParts.scores(Circumferences(chestCm = 140.0, waistCm = 100.0), Sex.MALE)
        assertEquals(PhysiqueAnalysis.LAGGING_BELOW, flat.getValue(MuscleGroup.V_TAPER), 1e-9)
        assertEquals(PhysiqueAnalysis.DEVELOPED_AT, marked.getValue(MuscleGroup.V_TAPER), 1e-9)
    }

    // A mirror selfie: the right hand holds the phone up at the chest, the left arm hangs.
    private val selfie = FrontPoseGeometry(
        shoulderLeft = PosePoint(0.40, 0.35),
        shoulderRight = PosePoint(0.85, 0.34),
        hipLeft = PosePoint(0.46, 0.73),
        hipRight = PosePoint(0.77, 0.73),
        elbowLeft = PosePoint(0.35, 0.60),
        wristLeft = PosePoint(0.34, 0.75),
        elbowRight = PosePoint(0.90, 0.60),
        wristRight = PosePoint(0.74, 0.37),
    )

    @Test
    fun `the arm holding the phone is not the arm that gets judged`() {
        val arms = PhysiqueRegions.regions(selfie).getValue(MuscleGroup.ARMS)
        assertEquals(1, arms.size)
        assertTrue(arms.single().right < 0.6, "judged the left, hanging arm")
    }

    @Test
    fun `a hand over the chest marks the chest as a weaker read`() {
        val noise = PhysiqueRegions.noise(selfie)
        assertTrue((noise[MuscleGroup.CHEST] ?: 1.0) > 1.0)
        assertFalse(MuscleGroup.ARMS in noise)
    }
}
