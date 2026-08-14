package com.squeeze.core.model

/**
 * Biological sex, required by every validated body-composition equation.
 *
 * The anthropometric equations this app uses (Navy/Hodgdon-Beckett, Jackson-Pollock,
 * Deurenberg) were derived from sex-separated cohorts and have no defined form outside
 * them. This field selects an equation variant; it is not a statement about identity,
 * and the UI should say so where it is collected.
 */
enum class Sex { MALE, FEMALE }

/** How the user prefers to see and enter numbers. Storage is always metric. */
enum class UnitSystem { METRIC, IMPERIAL }

/**
 * Training experience, used to pick a periodisation model. Novices progress session to
 * session, intermediates need weekly undulation, advanced lifters need block structure.
 */
enum class TrainingAge { NOVICE, INTERMEDIATE, ADVANCED }

enum class Goal {
    /** Maximise lean mass; accepts some fat gain. */
    HYPERTROPHY,

    /** Maximise force output at a fixed bodyweight. */
    STRENGTH,

    /** Lose fat while defending lean mass and performance. */
    CUT,

    /** Hold bodyweight while shifting composition. */
    RECOMP,

    /** Reach a target weight by a fixed date without losing lean mass. */
    MAKE_WEIGHT,
}

/**
 * The user's stable attributes. Everything here lives on-device only.
 *
 * @param heightCm standing height
 * @param birthYear used to derive age for age-dependent equations
 * @param sex selects the equation variant, see [Sex]
 */
data class Profile(
    val heightCm: Double,
    val birthYear: Int,
    val sex: Sex,
    val trainingAge: TrainingAge = TrainingAge.NOVICE,
    val goal: Goal = Goal.HYPERTROPHY,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    /**
     * Where the user wants their body fat to be, and by when.
     *
     * Both or neither. A target without a date cannot be behind schedule, so nothing about
     * it can ever be wrong and the app can never say anything useful about it; a date with
     * no target is a deadline for nothing. Together they give a required rate, which is the
     * only thing a current rate can honestly be compared against.
     */
    val targetBodyFatPercent: Double? = null,
    /**
     * A bodyweight to arrive at, in either direction.
     *
     * Separate from [targetBodyFatPercent] rather than derived from it, because they are
     * different goals and a person can hold both. "Lose ten kilos" and "get to fifteen per
     * cent" are not the same instruction: the first can be met by losing muscle, and the
     * second can be met at any weight. Someone recomposing wants both stated, and the app can
     * only tell them whether it is working if it knows what "it" was.
     */
    val targetWeightKg: Double? = null,
    val targetEpochDay: Long? = null,
) {
    init {
        require(heightCm in 100.0..250.0) { "heightCm out of plausible range: $heightCm" }
    }

    fun ageAt(year: Int): Int = year - birthYear

    /**
     * The dated goal, or null when the user has not set one.
     *
     * Needs a deadline and at least one target. Either target alone is a complete goal: a
     * lifter adding size wants a weight and no body fat figure, someone cutting wants the
     * reverse, and a recomp wants both — a weight that holds while the percentage falls, which
     * is the one goal that cannot be expressed with a single number at all.
     */
    fun targetOrNull(): com.squeeze.core.bodycomp.GoalTarget? {
        val day = targetEpochDay ?: return null
        if (targetBodyFatPercent == null && targetWeightKg == null) return null
        return com.squeeze.core.bodycomp.GoalTarget(
            goal = goal,
            targetBodyFatPercent = targetBodyFatPercent,
            targetWeightKg = targetWeightKg,
            targetEpochDay = day,
        )
    }
}
