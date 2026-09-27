package com.squeeze.core.program

/**
 * What a training block looks like from the outside: the shape of each week and of each
 * session, in the terms a lifter plans a gym visit by.
 *
 * The block itself is a list of prescriptions. Printed as one — twelve rows of
 * "5 × 10–15 @ 3 RIR" under a session name — it answered none of the questions a person
 * opening it has: which day is this, what does it hit, how long will it take, what matters
 * most in it, and how does this week differ from the last. This answers those, so the
 * screen can lead with them and leave the rows as detail.
 */
object BlockOverview {

    /** One week's headline. */
    data class WeekSummary(
        val weekIndex: Int,
        val isDeload: Boolean,
        val sessions: Int,
        val totalSets: Int,
        /** Reps in reserve for the week's work, when every exercise shares one; else null. */
        val rir: Int?,
    )

    /** One session's headline, with its exercises split by role. */
    data class SessionSummary(
        /** Muscles in the order the session trains most of them, most first. */
        val focus: List<MuscleGroup>,
        /** Heavy, low-rep compound work — the part to do first and fresh. */
        val main: List<SetPrescription>,
        /** Higher-rep isolation and pump work that follows. */
        val accessory: List<SetPrescription>,
        val minutes: Int,
    )

    /** At or below this many reps at the bottom of the range, an exercise is main work. */
    const val MAIN_MAX_LOW_REPS = 8

    /**
     * Minutes per working set including rest. Heavy sets need longer rests to be repeated
     * at the same load; accessories recover faster. Plus a warm-up.
     */
    private const val MAIN_SET_MINUTES = 3.0
    private const val ACCESSORY_SET_MINUTES = 2.0
    private const val WARM_UP_MINUTES = 8.0

    fun weeks(block: Mesocycle): List<WeekSummary> = block.weeks.map { week ->
        val rirs = week.sessions.flatMap { s -> s.prescriptions.map { it.targetRir } }.distinct()
        WeekSummary(
            weekIndex = week.weekIndex,
            isDeload = week.isDeload,
            sessions = week.sessions.size,
            totalSets = week.sessions.sumOf { it.totalSets },
            rir = rirs.singleOrNull(),
        )
    }

    fun session(session: Session): SessionSummary {
        val (main, accessory) = session.prescriptions.partition { it.repRangeLow <= MAIN_MAX_LOW_REPS }
        val focus = session.prescriptions
            .groupBy { it.muscleGroup }
            .mapValues { (_, p) -> p.sumOf { it.sets } }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
        val minutes = WARM_UP_MINUTES +
            main.sumOf { it.sets } * MAIN_SET_MINUTES +
            accessory.sumOf { it.sets } * ACCESSORY_SET_MINUTES
        // Rounded to five: "about 65 minutes" is a plan; "63 minutes" is a false promise.
        return SessionSummary(focus, main, accessory, ((minutes / 5.0).let { kotlin.math.round(it) } * 5).toInt())
    }

    /** "Stop 3 reps short of failure", said once per week instead of on every row. */
    fun effortSentence(rir: Int): String = when (rir) {
        0 -> "Take every set to failure."
        1 -> "Stop each set 1 rep short of failure."
        else -> "Stop each set $rir reps short of failure — you could do $rir more with good form."
    }

    fun label(group: MuscleGroup): String = when (group) {
        MuscleGroup.CHEST -> "Chest"
        MuscleGroup.BACK -> "Back"
        MuscleGroup.QUADS -> "Quads"
        MuscleGroup.HAMSTRINGS -> "Hamstrings"
        MuscleGroup.GLUTES -> "Glutes"
        MuscleGroup.SHOULDERS -> "Shoulders"
        MuscleGroup.BICEPS -> "Biceps"
        MuscleGroup.TRICEPS -> "Triceps"
        MuscleGroup.CALVES -> "Calves"
        MuscleGroup.ABS -> "Abs"
    }
}
