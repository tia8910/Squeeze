package com.squeeze.core.program

/**
 * @param compound compound lifts are placed first in a session, while the lifter is fresh,
 *   and are the ones whose load progression drives the strength picture
 */
data class Exercise(
    val name: String,
    val primary: MuscleGroup,
    val compound: Boolean,
    val equipment: Equipment,
)

enum class Equipment { BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT }

/**
 * A deliberately small, high-confidence exercise set.
 *
 * Breadth is not the differentiator here and a large library dilutes programme quality,
 * because every extra option is another way for the generator to pick something the user
 * cannot load progressively. Users can add their own; these are the defaults.
 */
object ExerciseLibrary {

    val ALL: List<Exercise> = listOf(
        // Chest
        Exercise("Barbell Bench Press", MuscleGroup.CHEST, true, Equipment.BARBELL),
        Exercise("Incline Dumbbell Press", MuscleGroup.CHEST, true, Equipment.DUMBBELL),
        Exercise("Cable Fly", MuscleGroup.CHEST, false, Equipment.CABLE),
        Exercise("Push-Up", MuscleGroup.CHEST, true, Equipment.BODYWEIGHT),
        // Back
        Exercise("Barbell Row", MuscleGroup.BACK, true, Equipment.BARBELL),
        Exercise("Pull-Up", MuscleGroup.BACK, true, Equipment.BODYWEIGHT),
        Exercise("Lat Pulldown", MuscleGroup.BACK, true, Equipment.MACHINE),
        Exercise("Seated Cable Row", MuscleGroup.BACK, true, Equipment.CABLE),
        // Legs
        Exercise("Back Squat", MuscleGroup.QUADS, true, Equipment.BARBELL),
        Exercise("Leg Press", MuscleGroup.QUADS, true, Equipment.MACHINE),
        Exercise("Bulgarian Split Squat", MuscleGroup.QUADS, true, Equipment.DUMBBELL),
        Exercise("Romanian Deadlift", MuscleGroup.HAMSTRINGS, true, Equipment.BARBELL),
        Exercise("Lying Leg Curl", MuscleGroup.HAMSTRINGS, false, Equipment.MACHINE),
        Exercise("Hip Thrust", MuscleGroup.GLUTES, true, Equipment.BARBELL),
        Exercise("Standing Calf Raise", MuscleGroup.CALVES, false, Equipment.MACHINE),
        // Shoulders and arms
        Exercise("Overhead Press", MuscleGroup.SHOULDERS, true, Equipment.BARBELL),
        Exercise("Dumbbell Lateral Raise", MuscleGroup.SHOULDERS, false, Equipment.DUMBBELL),
        Exercise("Barbell Curl", MuscleGroup.BICEPS, false, Equipment.BARBELL),
        Exercise("Incline Dumbbell Curl", MuscleGroup.BICEPS, false, Equipment.DUMBBELL),
        Exercise("Triceps Pushdown", MuscleGroup.TRICEPS, false, Equipment.CABLE),
        Exercise("Overhead Cable Extension", MuscleGroup.TRICEPS, false, Equipment.CABLE),
        // Core
        Exercise("Cable Crunch", MuscleGroup.ABS, false, Equipment.CABLE),
        Exercise("Hanging Leg Raise", MuscleGroup.ABS, false, Equipment.BODYWEIGHT),
    )

    /** Exercises for [group] that the user can actually perform with [available] equipment. */
    fun forGroup(group: MuscleGroup, available: Set<Equipment>): List<Exercise> =
        ALL.filter { it.primary == group && it.equipment in available }
            // Compounds first so session ordering falls out of selection order.
            .sortedByDescending { it.compound }
}
