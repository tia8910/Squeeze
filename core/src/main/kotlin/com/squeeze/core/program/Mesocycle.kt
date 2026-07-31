package com.squeeze.core.program

/**
 * One prescribed exercise slot within a session.
 *
 * @param targetRir reps in reserve to stop at. Autoregulation happens here: the app
 *   prescribes proximity to failure rather than a load, so the lifter self-selects weight
 *   based on the day, which is both safer and more accurate than a percentage table.
 */
data class SetPrescription(
    val muscleGroup: MuscleGroup,
    val exerciseName: String,
    val sets: Int,
    val repRangeLow: Int,
    val repRangeHigh: Int,
    val targetRir: Int,
) {
    init {
        require(sets > 0) { "sets must be positive" }
        require(repRangeLow in 1..repRangeHigh) { "invalid rep range $repRangeLow-$repRangeHigh" }
    }
}

/** One training day. */
data class Session(
    val dayIndex: Int,
    val name: String,
    val prescriptions: List<SetPrescription>,
) {
    val totalSets: Int get() = prescriptions.sumOf { it.sets }
}

/**
 * One week of training.
 *
 * @param isDeload true when this week intentionally drops volume and effort to shed
 *   accumulated fatigue. Deloads are scheduled, not earned, because a lifter who waits
 *   until they feel like they need one has already lost two weeks of progress.
 */
data class TrainingWeek(
    val weekIndex: Int,
    val sessions: List<Session>,
    val isDeload: Boolean,
) {
    fun weeklySets(group: MuscleGroup): Int =
        sessions.sumOf { s -> s.prescriptions.filter { it.muscleGroup == group }.sumOf { it.sets } }
}

/**
 * A complete training block, the unit the user buys.
 *
 * Blocks map onto how lifters already think and onto how the app monetises: a mesocycle
 * is a consumable purchase rather than a subscription, which keeps entitlement checks
 * simple enough to verify entirely offline.
 */
data class Mesocycle(
    val name: String,
    val weeks: List<TrainingWeek>,
    val notes: List<String> = emptyList(),
) {
    val durationWeeks: Int get() = weeks.size
}
