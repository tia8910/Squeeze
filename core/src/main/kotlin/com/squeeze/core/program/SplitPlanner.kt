package com.squeeze.core.program

/** One day of a weekly split. */
data class SplitDay(val name: String, val groups: List<MuscleGroup>)

/**
 * Chooses a weekly split for a given training frequency.
 *
 * Frequency drives structure, not preference: at two days a week only full-body training
 * hits each muscle often enough to matter, while at five or six days a body-part split
 * is the only way to fit the volume without stacking overlapping fatigue on consecutive days.
 */
object SplitPlanner {

    fun plan(daysPerWeek: Int): List<SplitDay> = when (daysPerWeek) {
        2 -> listOf(
            SplitDay("Full Body A", listOf(MuscleGroup.QUADS, MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS, MuscleGroup.ABS)),
            SplitDay("Full Body B", listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.BACK, MuscleGroup.CHEST, MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.CALVES)),
        )

        3 -> listOf(
            SplitDay("Push", listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)),
            SplitDay("Pull", listOf(MuscleGroup.BACK, MuscleGroup.BICEPS)),
            SplitDay("Legs", listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES, MuscleGroup.ABS)),
        )

        4 -> listOf(
            SplitDay("Upper A", listOf(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS)),
            SplitDay("Lower A", listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES)),
            SplitDay("Upper B", listOf(MuscleGroup.BACK, MuscleGroup.CHEST, MuscleGroup.BICEPS, MuscleGroup.TRICEPS)),
            SplitDay("Lower B", listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.QUADS, MuscleGroup.ABS)),
        )

        5 -> listOf(
            SplitDay("Push", listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)),
            SplitDay("Pull", listOf(MuscleGroup.BACK, MuscleGroup.BICEPS)),
            SplitDay("Legs", listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES)),
            SplitDay("Upper", listOf(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS)),
            SplitDay("Lower", listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.QUADS, MuscleGroup.ABS)),
        )

        else -> listOf(
            SplitDay("Push A", listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)),
            SplitDay("Pull A", listOf(MuscleGroup.BACK, MuscleGroup.BICEPS)),
            SplitDay("Legs A", listOf(MuscleGroup.QUADS, MuscleGroup.CALVES)),
            SplitDay("Push B", listOf(MuscleGroup.SHOULDERS, MuscleGroup.CHEST, MuscleGroup.TRICEPS)),
            SplitDay("Pull B", listOf(MuscleGroup.BACK, MuscleGroup.BICEPS, MuscleGroup.ABS)),
            SplitDay("Legs B", listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES)),
        )
    }
}
