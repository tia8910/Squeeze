package com.squeeze.core.program

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockOverviewTest {

    // The push day from the user's own block.
    private val push = Session(
        dayIndex = 0,
        name = "Push",
        prescriptions = listOf(
            SetPrescription(MuscleGroup.CHEST, "Barbell Bench Press", 3, 5, 8, 3),
            SetPrescription(MuscleGroup.CHEST, "Incline Dumbbell Press", 2, 5, 8, 3),
            SetPrescription(MuscleGroup.SHOULDERS, "Overhead Press", 4, 5, 8, 3),
            SetPrescription(MuscleGroup.SHOULDERS, "Dumbbell Lateral Raise", 3, 10, 15, 3),
            SetPrescription(MuscleGroup.TRICEPS, "Triceps Pushdown", 5, 10, 15, 3),
            SetPrescription(MuscleGroup.TRICEPS, "Overhead Cable Extension", 5, 10, 15, 3),
        ),
    )

    @Test
    fun `heavy compounds come first as main work, the rest as accessories`() {
        val s = BlockOverview.session(push)
        assertEquals(listOf("Barbell Bench Press", "Incline Dumbbell Press", "Overhead Press"), s.main.map { it.exerciseName })
        assertEquals(3, s.accessory.size)
    }

    @Test
    fun `focus is ordered by sets, and time is rounded to five minutes`() {
        val s = BlockOverview.session(push)
        assertEquals(MuscleGroup.TRICEPS, s.focus.first())
        assertEquals(0, s.minutes % 5)
        // 8 warm-up + 9 heavy sets × 3 + 13 accessory sets × 2 = 61 → 60.
        assertEquals(60, s.minutes)
    }

    @Test
    fun `a week that shares one effort level says it once`() {
        val block = Mesocycle(
            "Block",
            listOf(
                TrainingWeek(0, listOf(push), isDeload = false),
                TrainingWeek(
                    1,
                    listOf(push.copy(prescriptions = push.prescriptions.mapIndexed { i, p -> p.copy(targetRir = if (i == 0) 2 else 3) })),
                    isDeload = false,
                ),
            ),
        )
        val weeks = BlockOverview.weeks(block)
        assertEquals(3, weeks[0].rir)
        assertEquals(22, weeks[0].totalSets)
        assertNull(weeks[1].rir)
        assertTrue(BlockOverview.effortSentence(3).contains("3 reps short of failure"))
    }
}
