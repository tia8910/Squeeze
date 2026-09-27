package com.squeeze.core.program

import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Goal
import com.squeeze.core.model.Sex
import com.squeeze.core.scan.Development
import com.squeeze.core.workout.DatedSet
import com.squeeze.core.workout.LoggedSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.squeeze.core.scan.MuscleGroup as PhotoGroup

class MuscleProfileTest {

    private val today = 1000L
    private fun set(name: String, kg: Double, reps: Int, rir: Int? = null, daysAgo: Long = 3) =
        DatedSet(today - daysAgo, name, LoggedSet(kg, reps, rir))

    @Test
    fun `estimated max counts reps in reserve, and very high reps are not judged`() {
        assertEquals(100.0 * (1 + 10 / 30.0), StrengthStandards.estimatedMax(100.0, 8, 2)!!, 1e-9)
        assertNull(StrengthStandards.estimatedMax(40.0, 15, 0))
    }

    @Test
    fun `a bench of 1x bodyweight is intermediate, 1_5x advanced`() {
        assertEquals(StrengthStandards.Level.INTERMEDIATE, StrengthStandards.level(1.0, StrengthStandards.Reference.BENCH, Sex.MALE))
        assertEquals(StrengthStandards.Level.ADVANCED, StrengthStandards.level(1.5, StrengthStandards.Reference.BENCH, Sex.MALE))
        assertEquals(0.5, StrengthStandards.score(1.0, StrengthStandards.Reference.BENCH, Sex.MALE), 1e-9)
        // The same lift is judged against women's standards for a woman.
        assertTrue(StrengthStandards.score(0.75, StrengthStandards.Reference.BENCH, Sex.FEMALE) >
            StrengthStandards.score(0.75, StrengthStandards.Reference.BENCH, Sex.MALE))
    }

    @Test
    fun `lifts outweigh a single photo, and the disagreement is explained`() {
        // A 70 kg man benching an estimated 1.5x bodyweight whose one photo reads his chest at 20/100.
        val profile = MuscleProfiler.assess(
            Sex.MALE, Goal.RECOMP, 70.0,
            listOf(set("Barbell Bench Press", 90.0, 5, 1)),
            today, null,
            mapOf(PhotoGroup.CHEST to PhotoReading(0.20, 0.15, 1)),
        )
        val chest = profile.assessments.first { it.group == MuscleGroup.CHEST }
        assertTrue(chest.score > 0.6, "score ${chest.score}")
        assertEquals(EvidenceSource.LIFTS, chest.evidence.first().source)
        assertNotNull(chest.note)
        assertTrue(chest.note!!.contains("not showing"))
    }

    @Test
    fun `every verdict names its evidence and a confidence`() {
        val profile = MuscleProfiler.assess(
            Sex.MALE, Goal.HYPERTROPHY, 80.0,
            listOf(set("Back Squat", 100.0, 5), set("Barbell Row", 50.0, 8)),
            today,
            Circumferences(chestCm = 100.0, waistCm = 82.0, armCm = 33.0, thighCm = 55.0, calfCm = 36.0, neckCm = 38.0),
            mapOf(PhotoGroup.SHOULDERS to PhotoReading(0.4, 0.1, 3)),
        )
        profile.assessments.forEach { a ->
            assertTrue(a.evidence.isNotEmpty())
            assertTrue(a.evidence.all { it.text.isNotBlank() })
        }
        // Size evidence reaches calves, which nothing else could.
        assertTrue(profile.assessments.any { it.group == MuscleGroup.CALVES })
        // Glutes are assessed from the squat even though a front photo cannot see them.
        assertTrue(profile.assessments.first { it.group == MuscleGroup.GLUTES }.evidence.any { it.source == EvidenceSource.LIFTS })
    }

    @Test
    fun `more agreeing evidence means higher confidence`() {
        val photoOnly = MuscleProfiler.assess(Sex.MALE, Goal.RECOMP, 70.0, emptyList(), today, null,
            mapOf(PhotoGroup.SHOULDERS to PhotoReading(0.5, 0.15, 1)))
        val both = MuscleProfiler.assess(Sex.MALE, Goal.RECOMP, 70.0, listOf(set("Overhead Press", 45.0, 6, 1)), today, null,
            mapOf(PhotoGroup.SHOULDERS to PhotoReading(0.5, 0.15, 1)))
        val a = photoOnly.assessments.single { it.group == MuscleGroup.SHOULDERS }
        val b = both.assessments.single { it.group == MuscleGroup.SHOULDERS }
        assertEquals(MuscleConfidence.LOW, a.confidence)
        assertTrue(b.sd < a.sd)
    }

    @Test
    fun `nothing is invented for a muscle with no evidence`() {
        val profile = MuscleProfiler.assess(Sex.MALE, Goal.RECOMP, 70.0, emptyList(), today, null,
            mapOf(PhotoGroup.CHEST to PhotoReading(0.5, 0.12, 2)))
        assertEquals(listOf(MuscleGroup.CHEST), profile.assessments.map { it.group })
        assertTrue(MuscleGroup.GLUTES in profile.unassessed)
    }

    @Test
    fun `a mis-measured chest and hidden legs add nothing`() {
        val profile = MuscleProfiler.assess(Sex.MALE, Goal.RECOMP, 70.0, emptyList(), today,
            Circumferences(chestCm = 121.6, waistCm = 72.8, armCm = 33.0),
            mapOf(PhotoGroup.LEGS to PhotoReading(0.2, 0.1, 1)),
            hiddenInPhoto = setOf(PhotoGroup.LEGS),
        )
        assertFalse(profile.assessments.any { it.group == MuscleGroup.CHEST || it.group == MuscleGroup.QUADS })
    }

    @Test
    fun `old lifts outside the window do not count, stale ones count for less`() {
        val old = MuscleProfiler.assess(Sex.MALE, Goal.RECOMP, 70.0, listOf(set("Barbell Curl", 40.0, 8, daysAgo = 200)), today, null, emptyMap())
        assertTrue(old.assessments.isEmpty())
        val fresh = MuscleProfiler.assess(Sex.MALE, Goal.RECOMP, 70.0, listOf(set("Barbell Curl", 40.0, 8, daysAgo = 5)), today, null, emptyMap())
        val stale = MuscleProfiler.assess(Sex.MALE, Goal.RECOMP, 70.0, listOf(set("Barbell Curl", 40.0, 8, daysAgo = 60)), today, null, emptyMap())
        assertTrue(stale.assessments.single().sd > fresh.assessments.single().sd)
    }

    @Test
    fun `weak points are the low-scoring muscles, each with a specific reason and a plan`() {
        val profile = MuscleProfiler.assess(
            Sex.MALE, Goal.HYPERTROPHY, 80.0,
            listOf(set("Barbell Bench Press", 110.0, 5), set("Barbell Curl", 20.0, 10)),
            today, null, emptyMap(),
        )
        val weak = profile.weaknesses.first()
        assertEquals(MuscleGroup.BICEPS, weak.group)
        assertTrue(weak.why.contains("/100"))
        assertTrue(weak.how.isNotBlank())
        assertTrue(MuscleGroup.CHEST in profile.strengths)
        assertEquals(Development.DEVELOPED, profile.assessments.first { it.group == MuscleGroup.CHEST }.development)
    }
}
