package com.squeeze.core.program

import com.squeeze.core.model.Sex
import com.squeeze.core.workout.DatedSet

/**
 * How strong a lift is for the person doing it: estimated one-rep max as a multiple of
 * bodyweight, placed on the five levels lifting standards use.
 *
 * **Why lifts count.** Of the three things the app can know about a muscle, how much it can
 * move is the most direct. A photograph judges how a muscle looks, which lighting and body fat
 * change by tens of points; a tape judges its size, which says nothing about what is under the
 * fat. A logged set of 5 at 80 kg is a measurement.
 *
 * **Which lifts.** Only those whose logged weight means the same thing for everyone: barbell
 * lifts, and a few machines at lower confidence because machines differ in leverage. Dumbbell
 * and bodyweight movements are left out, because a logged "20 kg" might be one dumbbell or
 * two, and a pull-up's load is the lifter plus whatever they wrote down.
 *
 * **The thresholds** are the widely used bodyweight-multiple standards (beginner, novice,
 * intermediate, advanced, elite) for adults, men and women separately. They are population
 * guides, not laws, and the assessment carries their uncertainty rather than hiding it.
 */
object StrengthStandards {

    enum class Level(val label: String) {
        BEGINNER("Beginner"),
        NOVICE("Novice"),
        INTERMEDIATE("Intermediate"),
        ADVANCED("Advanced"),
        ELITE("Elite"),
    }

    /**
     * A reference movement and its standards.
     *
     * @param male thresholds for [Level.BEGINNER] to [Level.ELITE], as 1RM ÷ bodyweight
     */
    enum class Reference(val male: DoubleArray, val female: DoubleArray) {
        BENCH(doubleArrayOf(0.50, 0.75, 1.00, 1.50, 2.00), doubleArrayOf(0.25, 0.50, 0.75, 1.00, 1.40)),
        SQUAT(doubleArrayOf(0.75, 1.00, 1.50, 2.00, 2.50), doubleArrayOf(0.50, 0.75, 1.25, 1.50, 1.90)),
        DEADLIFT(doubleArrayOf(1.00, 1.25, 1.75, 2.50, 3.00), doubleArrayOf(0.50, 1.00, 1.25, 1.75, 2.50)),
        OVERHEAD_PRESS(doubleArrayOf(0.35, 0.55, 0.75, 1.00, 1.25), doubleArrayOf(0.20, 0.35, 0.50, 0.75, 1.00)),
        ROW(doubleArrayOf(0.50, 0.75, 1.00, 1.25, 1.75), doubleArrayOf(0.25, 0.40, 0.65, 0.90, 1.20)),
        CURL(doubleArrayOf(0.20, 0.40, 0.60, 0.85, 1.15), doubleArrayOf(0.10, 0.20, 0.40, 0.60, 0.85)),
        PULLDOWN(doubleArrayOf(0.50, 0.75, 1.00, 1.25, 1.50), doubleArrayOf(0.30, 0.45, 0.65, 0.90, 1.20)),
        ROMANIAN_DEADLIFT(doubleArrayOf(0.75, 1.00, 1.50, 2.00, 2.50), doubleArrayOf(0.40, 0.70, 1.00, 1.40, 1.80)),
        HIP_THRUST(doubleArrayOf(0.50, 1.00, 1.50, 2.25, 3.00), doubleArrayOf(0.50, 1.00, 1.50, 2.00, 2.75)),
        LEG_PRESS(doubleArrayOf(1.00, 1.75, 2.50, 3.75, 4.50), doubleArrayOf(0.50, 1.25, 2.00, 3.00, 3.75)),
        PUSHDOWN(doubleArrayOf(0.25, 0.40, 0.60, 0.85, 1.10), doubleArrayOf(0.12, 0.25, 0.40, 0.60, 0.80)),
        ;

        fun thresholds(sex: Sex) = if (sex == Sex.FEMALE) female else male
    }

    /**
     * One loggable exercise, how it relates to its reference, and what it trains.
     *
     * @param factor this exercise's 1RM relative to the reference's; a front squat is about
     *   80 % of a back squat, so its ratio is divided by 0.8 before being judged
     * @param sd how far a score from this exercise can be off, before contribution weighting:
     *   barbell standards are well established; machines vary with the machine
     * @param trains how strongly the exercise reflects each muscle, 0 to 1
     */
    data class Lift(
        val names: Set<String>,
        val reference: Reference,
        val factor: Double,
        val sd: Double,
        val trains: Map<MuscleGroup, Double>,
    )

    private const val BARBELL_SD = 0.08
    private const val MACHINE_SD = 0.13

    val LIFTS: List<Lift> = listOf(
        Lift(setOf("barbell bench press"), Reference.BENCH, 1.0, BARBELL_SD,
            mapOf(MuscleGroup.CHEST to 1.0, MuscleGroup.TRICEPS to 0.5, MuscleGroup.SHOULDERS to 0.3)),
        Lift(setOf("smith machine bench press", "machine chest press"), Reference.BENCH, 1.0, MACHINE_SD,
            mapOf(MuscleGroup.CHEST to 1.0, MuscleGroup.TRICEPS to 0.4)),
        Lift(setOf("back squat"), Reference.SQUAT, 1.0, BARBELL_SD,
            mapOf(MuscleGroup.QUADS to 1.0, MuscleGroup.GLUTES to 0.6)),
        Lift(setOf("front squat"), Reference.SQUAT, 0.8, BARBELL_SD,
            mapOf(MuscleGroup.QUADS to 1.0, MuscleGroup.GLUTES to 0.4)),
        Lift(setOf("smith machine squat", "hack squat"), Reference.SQUAT, 1.0, MACHINE_SD,
            mapOf(MuscleGroup.QUADS to 1.0, MuscleGroup.GLUTES to 0.4)),
        Lift(setOf("barbell deadlift", "deadlift"), Reference.DEADLIFT, 1.0, BARBELL_SD,
            mapOf(MuscleGroup.HAMSTRINGS to 0.6, MuscleGroup.GLUTES to 0.6, MuscleGroup.BACK to 0.6)),
        Lift(setOf("overhead press"), Reference.OVERHEAD_PRESS, 1.0, BARBELL_SD,
            mapOf(MuscleGroup.SHOULDERS to 1.0, MuscleGroup.TRICEPS to 0.4)),
        Lift(setOf("machine shoulder press"), Reference.OVERHEAD_PRESS, 1.0, MACHINE_SD,
            mapOf(MuscleGroup.SHOULDERS to 1.0, MuscleGroup.TRICEPS to 0.3)),
        Lift(setOf("barbell row"), Reference.ROW, 1.0, BARBELL_SD,
            mapOf(MuscleGroup.BACK to 1.0, MuscleGroup.BICEPS to 0.3)),
        Lift(setOf("seated cable row", "smith machine row", "chest-supported row"), Reference.ROW, 1.0, MACHINE_SD,
            mapOf(MuscleGroup.BACK to 1.0, MuscleGroup.BICEPS to 0.3)),
        Lift(setOf("lat pulldown", "close-grip pulldown"), Reference.PULLDOWN, 1.0, MACHINE_SD,
            mapOf(MuscleGroup.BACK to 0.9, MuscleGroup.BICEPS to 0.3)),
        Lift(setOf("barbell curl", "ez bar curl"), Reference.CURL, 1.0, BARBELL_SD,
            mapOf(MuscleGroup.BICEPS to 1.0)),
        Lift(setOf("romanian deadlift"), Reference.ROMANIAN_DEADLIFT, 1.0, BARBELL_SD,
            mapOf(MuscleGroup.HAMSTRINGS to 1.0, MuscleGroup.GLUTES to 0.5)),
        Lift(setOf("hip thrust"), Reference.HIP_THRUST, 1.0, BARBELL_SD,
            mapOf(MuscleGroup.GLUTES to 1.0)),
        Lift(setOf("smith machine hip thrust"), Reference.HIP_THRUST, 1.0, MACHINE_SD,
            mapOf(MuscleGroup.GLUTES to 1.0)),
        Lift(setOf("leg press"), Reference.LEG_PRESS, 1.0, MACHINE_SD,
            mapOf(MuscleGroup.QUADS to 0.7, MuscleGroup.GLUTES to 0.3)),
        Lift(setOf("triceps pushdown"), Reference.PUSHDOWN, 1.0, MACHINE_SD,
            mapOf(MuscleGroup.TRICEPS to 0.8)),
    )

    fun liftFor(exercise: String): Lift? {
        val name = exercise.trim().lowercase()
        return LIFTS.firstOrNull { name in it.names }
    }

    /**
     * Above this many reps, counting reps in reserve, a one-rep max estimate is too loose to
     * judge against a standard: Epley drifts badly past about twelve.
     */
    const val MAX_ESTIMATE_REPS = 12

    /**
     * Epley's estimated one-rep max, counting the reps left in reserve: a set of 8 stopped two
     * short of failure is a set of 10 to failure, and scoring it as 8 undersells the lifter.
     */
    fun estimatedMax(weightKg: Double, reps: Int, rir: Int?): Double? {
        val effective = reps + (rir ?: 0)
        if (weightKg <= 0 || reps <= 0 || effective > MAX_ESTIMATE_REPS) return null
        return weightKg * (1.0 + effective / 30.0)
    }

    /** Scores on the app's 0–1 scale at each level: lagging below novice, developed from advanced. */
    private val LEVEL_SCORES = doubleArrayOf(0.10, 0.30, 0.50, 0.75, 0.95)

    fun score(ratio: Double, reference: Reference, sex: Sex): Double {
        val t = reference.thresholds(sex)
        if (ratio <= t[0]) return (LEVEL_SCORES[0] * ratio / t[0]).coerceAtLeast(0.02)
        for (i in 1 until t.size) {
            if (ratio <= t[i]) {
                val f = (ratio - t[i - 1]) / (t[i] - t[i - 1])
                return LEVEL_SCORES[i - 1] + f * (LEVEL_SCORES[i] - LEVEL_SCORES[i - 1])
            }
        }
        return LEVEL_SCORES.last()
    }

    fun level(ratio: Double, reference: Reference, sex: Sex): Level {
        val t = reference.thresholds(sex)
        val index = t.indexOfLast { ratio >= it }.coerceAtLeast(0)
        return Level.entries[index]
    }

    /** The strongest recent performance on one lift. */
    data class Best(
        val lift: Lift,
        val exercise: String,
        val estimatedMaxKg: Double,
        val ratio: Double,
        val score: Double,
        val level: Level,
        val epochDay: Long,
        val stale: Boolean,
    )

    /** Only the last [WINDOW_DAYS] count: strength from a year ago is not today's. */
    const val WINDOW_DAYS = 90L

    /** Older than this and the best is still used, at lower confidence. */
    const val STALE_DAYS = 45L

    fun bests(sets: List<DatedSet>, bodyweightKg: Double, sex: Sex, today: Long): List<Best> {
        if (bodyweightKg <= 0) return emptyList()
        return sets
            .filter { today - it.epochDay in 0..WINDOW_DAYS }
            .mapNotNull { s ->
                val lift = liftFor(s.exercise) ?: return@mapNotNull null
                val max = estimatedMax(s.set.weightKg, s.set.reps, s.set.rir) ?: return@mapNotNull null
                Triple(lift, s, max)
            }
            .groupBy { it.second.exercise.trim().lowercase() }
            .mapNotNull { (_, entries) ->
                val (lift, set, max) = entries.maxBy { it.third }
                val ratio = max / lift.factor / bodyweightKg
                Best(
                    lift = lift,
                    exercise = set.exercise,
                    estimatedMaxKg = max,
                    ratio = ratio,
                    score = score(ratio, lift.reference, sex),
                    level = level(ratio, lift.reference, sex),
                    epochDay = set.epochDay,
                    stale = today - entries.maxOf { it.second.epochDay } > STALE_DAYS,
                )
            }
    }
}
