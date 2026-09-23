package com.squeeze.core.scan

import com.squeeze.core.model.Goal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhysiqueTest {

    private val dim = 4
    private fun axis(i: Int) = DoubleArray(dim) { if (it == i) 1.0 else 0.0 }
    private val pair = MusclePromptPair(developed = axis(0), undeveloped = axis(1))

    @Test
    fun `a crop that matches the developed wording scores near one`() {
        val score = assertNotNull(MuscleScorer.score(listOf(axis(0)), listOf(pair)))
        assertTrue(score > 0.99)
    }

    @Test
    fun `a crop halfway between the wordings scores a half, and both arms are averaged`() {
        val left = assertNotNull(MuscleScorer.score(listOf(axis(0)), listOf(pair)))
        val right = assertNotNull(MuscleScorer.score(listOf(axis(1)), listOf(pair)))
        val both = assertNotNull(MuscleScorer.score(listOf(axis(0), axis(1)), listOf(pair)))
        assertEquals((left + right) / 2.0, both, 1e-9)
        assertEquals(0.5, both, 1e-9)
    }

    @Test
    fun `mismatched embeddings give no score`() {
        assertNull(MuscleScorer.score(listOf(DoubleArray(dim + 1) { 1.0 }), listOf(pair)))
        assertNull(MuscleScorer.score(emptyList(), listOf(pair)))
        assertNull(MuscleScorer.score(listOf(DoubleArray(dim)), listOf(pair)))
    }

    private val untrained = mapOf(
        MuscleGroup.SHOULDERS to 0.27,
        MuscleGroup.CHEST to 0.22,
        MuscleGroup.ARMS to 0.32,
        MuscleGroup.ABS to 0.47,
        MuscleGroup.V_TAPER to 0.30,
        MuscleGroup.LEGS to 0.20,
    )

    @Test
    fun `best of a weak set is not called a strength`() {
        val report = assertNotNull(PhysiqueAnalysis.report(untrained, Goal.HYPERTROPHY))
        assertTrue(report.strengths.isEmpty())
        assertEquals(2, report.weaknesses.size)
        assertFalse(MuscleGroup.ABS in report.weaknesses)
    }

    @Test
    fun `the goal decides which gap comes first`() {
        val cut = assertNotNull(PhysiqueAnalysis.report(untrained, Goal.CUT))
        assertEquals(MuscleGroup.ABS, cut.weaknesses.first())

        val strength = assertNotNull(PhysiqueAnalysis.report(untrained, Goal.STRENGTH))
        assertEquals(MuscleGroup.LEGS, strength.weaknesses.first())
    }

    @Test
    fun `developed groups are strengths and never weaknesses`() {
        val lean = mapOf(
            MuscleGroup.ABS to 0.98,
            MuscleGroup.ARMS to 0.75,
            MuscleGroup.CHEST to 0.55,
            MuscleGroup.SHOULDERS to 0.38,
        )
        val report = assertNotNull(PhysiqueAnalysis.report(lean, Goal.HYPERTROPHY))
        assertEquals(listOf(MuscleGroup.ABS, MuscleGroup.ARMS), report.strengths)
        assertEquals(listOf(MuscleGroup.SHOULDERS, MuscleGroup.CHEST), report.weaknesses)
        assertEquals(report.weaknesses, report.focus.map { it.group })
    }

    @Test
    fun `nothing read gives no report`() {
        assertNull(PhysiqueAnalysis.report(emptyMap(), Goal.HYPERTROPHY))
    }

    private fun pose(withLimbs: Boolean) = FrontPoseGeometry(
        shoulderLeft = PosePoint(0.62, 0.30),
        shoulderRight = PosePoint(0.38, 0.30),
        hipLeft = PosePoint(0.56, 0.60),
        hipRight = PosePoint(0.44, 0.60),
        elbowLeft = if (withLimbs) PosePoint(0.70, 0.45) else null,
        elbowRight = if (withLimbs) PosePoint(0.30, 0.45) else null,
        wristLeft = if (withLimbs) PosePoint(0.72, 0.60) else null,
        wristRight = if (withLimbs) PosePoint(0.28, 0.60) else null,
        kneeLeft = if (withLimbs) PosePoint(0.55, 0.80) else null,
        kneeRight = if (withLimbs) PosePoint(0.45, 0.80) else null,
    )

    @Test
    fun `every group gets a crop when the whole body is in frame`() {
        val regions = PhysiqueRegions.regions(pose(withLimbs = true))
        assertEquals(MuscleGroup.entries.toSet(), regions.keys)
        assertEquals(2, regions.getValue(MuscleGroup.ARMS).size)
        regions.values.flatten().forEach { r ->
            assertTrue(r.left < r.right && r.top < r.bottom)
            assertTrue(r.left >= 0.0 && r.right <= 1.0 && r.top >= 0.0 && r.bottom <= 1.0)
        }
    }

    @Test
    fun `limbs the pose model did not place are not judged`() {
        val regions = PhysiqueRegions.regions(pose(withLimbs = false))
        assertFalse(MuscleGroup.ARMS in regions)
        assertFalse(MuscleGroup.LEGS in regions)
        assertTrue(MuscleGroup.CHEST in regions)
    }

    // The user's own scan: abs defined, the rest average, thighs in shorts.
    private val shorts = mapOf(
        MuscleGroup.ABS to 0.87,
        MuscleGroup.V_TAPER to 0.42,
        MuscleGroup.CHEST to 0.42,
        MuscleGroup.SHOULDERS to 0.41,
        MuscleGroup.ARMS to 0.37,
        MuscleGroup.LEGS to 0.17,
    )

    @Test
    fun `a group under clothing is neither scored nor called weak`() {
        val report = assertNotNull(
            PhysiqueAnalysis.report(shorts, Goal.RECOMP, hidden = setOf(MuscleGroup.LEGS)),
        )
        assertFalse(MuscleGroup.LEGS in report.weaknesses)
        assertFalse(report.scores.any { it.group == MuscleGroup.LEGS })
        assertEquals(listOf(MuscleGroup.LEGS), report.hidden)
    }

    @Test
    fun `measured strengths join the AI's, and win over an average AI read`() {
        val report = assertNotNull(
            PhysiqueAnalysis.report(
                shorts, Goal.RECOMP,
                hidden = setOf(MuscleGroup.LEGS),
                measuredStrong = mapOf(MuscleGroup.V_TAPER to "Chest 1.65× your waist.", MuscleGroup.LEGS to "Quads ahead."),
            ),
        )
        assertTrue(MuscleGroup.ABS in report.strengths)
        assertTrue(MuscleGroup.V_TAPER in report.strengths)
        assertFalse(MuscleGroup.V_TAPER in report.weaknesses)
        // A girth taken through shorts is the shorts' girth.
        assertFalse(MuscleGroup.LEGS in report.strengths)
        assertTrue(report.strengthEvidence.getValue(MuscleGroup.V_TAPER).contains("1.65"))
    }

    @Test
    fun `a group clearly ahead of the rest is a strength even when average`() {
        val scores = mapOf(
            MuscleGroup.V_TAPER to 0.55,
            MuscleGroup.CHEST to 0.40,
            MuscleGroup.SHOULDERS to 0.38,
            MuscleGroup.ARMS to 0.36,
        )
        val report = assertNotNull(PhysiqueAnalysis.report(scores, Goal.HYPERTROPHY))
        assertEquals(listOf(MuscleGroup.V_TAPER), report.strengths)
    }

    @Test
    fun `nothing is hidden without a part mask, and bare regions are kept`() {
        val all = MuscleGroup.entries.toSet()
        assertTrue(PhysiqueAnalysis.hiddenGroups(emptyMap(), all).isEmpty())
        val hidden = PhysiqueAnalysis.hiddenGroups(mapOf(MuscleGroup.LEGS to 0.2, MuscleGroup.CHEST to 0.9), setOf(MuscleGroup.LEGS, MuscleGroup.CHEST))
        assertEquals(setOf(MuscleGroup.LEGS), hidden)
    }
}
