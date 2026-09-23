package com.squeeze.core.workout

import kotlin.math.roundToInt

/** One logged set with the day and exercise it belongs to. */
data class DatedSet(val epochDay: Long, val exercise: String, val set: LoggedSet)

/**
 * One exercise on one day.
 *
 * @param volume kilograms moved: weight × reps, summed over the sets
 * @param estimatedMax the best set's estimated one-rep max (Epley) — the one number that lets
 *   a set of 10 at 60 kg and a set of 5 at 70 kg be compared
 */
data class ExerciseSession(
    val epochDay: Long,
    val sets: List<LoggedSet>,
    val volume: Double,
    val bestSet: LoggedSet,
    val estimatedMax: Double,
)

enum class Trend(val label: String) {
    PROGRESSING("Progressing"),
    STALLED("Stalled"),
    REGRESSING("Going backwards"),
    NEW("Just started"),
}

/**
 * Progressive overload on one exercise, across every session it was logged.
 *
 * @param changePercent estimated max now against the first session
 * @param advice what to do about the trend, in one sentence
 */
data class ExerciseProgress(
    val exercise: String,
    val sessions: List<ExerciseSession>,
    val trend: Trend,
    val changePercent: Double?,
    val advice: String,
) {
    val latest: ExerciseSession get() = sessions.last()
    val best: ExerciseSession get() = sessions.maxBy { it.estimatedMax }
}

/** One exercise in today's workout, against the last time. */
data class ExerciseSummary(
    val exercise: String,
    val today: ExerciseSession,
    val previous: ExerciseSession?,
    val personalRecord: Boolean,
) {
    val volumeChangePercent: Double?
        get() = previous?.takeIf { it.volume > 0 }?.let { (today.volume - it.volume) / it.volume * 100 }
    val maxChangePercent: Double?
        get() = previous?.takeIf { it.estimatedMax > 0 }?.let { (today.estimatedMax - it.estimatedMax) / it.estimatedMax * 100 }
}

data class WorkoutSummary(val exercises: List<ExerciseSummary>) {
    val totalSets: Int get() = exercises.sumOf { it.today.sets.size }
    val totalVolume: Double get() = exercises.sumOf { it.today.volume }
    val records: List<String> get() = exercises.filter { it.personalRecord }.map { it.exercise }
}

/**
 * Turns the set log into what a lifter wants to know: did today beat last time, and is each
 * lift still going up.
 *
 * **Estimated max, not heaviest weight.** Double progression adds reps before weight, so the
 * heaviest weight stays flat for weeks while the lifter is plainly getting stronger. Epley's
 * estimated one-rep max moves with either, which is what makes it the right line to watch.
 *
 * **Stalled means three sessions.** One bad day is sleep or food; three sessions without a
 * new best is the programme, and the advice changes from "keep going" to "change something".
 */
object ProgressAnalysis {

    /** Sessions without a new best before an exercise counts as stalled. */
    const val STALL_SESSIONS = 3

    fun sessions(sets: List<DatedSet>): Map<String, List<ExerciseSession>> =
        sets.filter { it.set.reps > 0 }
            .groupBy { it.exercise }
            .mapValues { (_, rows) ->
                rows.groupBy { it.epochDay }.entries.sortedBy { it.key }.map { (day, daySets) ->
                    val s = daySets.map { it.set }
                    val best = s.maxBy { Coaching.estimatedOneRepMax(it) }
                    ExerciseSession(
                        epochDay = day,
                        sets = s,
                        volume = s.sumOf { it.weightKg * it.reps },
                        bestSet = best,
                        estimatedMax = Coaching.estimatedOneRepMax(best),
                    )
                }
            }

    /** Every exercise's progress, the ones needing attention first. */
    fun progress(sets: List<DatedSet>): List<ExerciseProgress> =
        sessions(sets).map { (exercise, list) -> progressOf(exercise, list) }
            // What needs attention first: stalled and regressing lifts above the ones moving.
            .sortedWith(compareBy<ExerciseProgress> { it.trend == Trend.PROGRESSING }.thenByDescending { it.latest.epochDay })

    private fun progressOf(exercise: String, list: List<ExerciseSession>): ExerciseProgress {
        if (list.size < 2) {
            return ExerciseProgress(exercise, list, Trend.NEW, null, "Log it again next session — the trend starts with the second one.")
        }
        val first = list.first().estimatedMax
        val change = if (first > 0) (list.last().estimatedMax - first) / first * 100 else null
        val bestIndex = list.indices.maxBy { list[it].estimatedMax }
        val sinceBest = list.lastIndex - bestIndex
        val recentDrop = list.last().estimatedMax < list[bestIndex].estimatedMax * 0.95

        val (trend, advice) = when {
            sinceBest == 0 -> Trend.PROGRESSING to "New best last session. Keep adding a rep or the smallest plate."
            recentDrop && sinceBest >= 2 -> Trend.REGRESSING to
                "Down ${pct(list.last().estimatedMax, list[bestIndex].estimatedMax)}% from your best. Check sleep and food; " +
                "if it continues, take a lighter week."
            sinceBest >= STALL_SESSIONS -> Trend.STALLED to
                "No new best in $sinceBest sessions. Drop the weight 10% and build back up, or switch rep range for a few weeks."
            else -> Trend.PROGRESSING to "On track — ${sinceBest} session${if (sinceBest == 1) "" else "s"} since your best, which is normal."
        }
        return ExerciseProgress(exercise, list, trend, change, advice)
    }

    /** Today's workout, exercise by exercise, against the previous session of each. */
    fun summary(sets: List<DatedSet>, day: Long): WorkoutSummary {
        val all = sessions(sets)
        val exercises = all.mapNotNull { (exercise, list) ->
            val today = list.lastOrNull { it.epochDay == day } ?: return@mapNotNull null
            val before = list.filter { it.epochDay < day }
            ExerciseSummary(
                exercise = exercise,
                today = today,
                previous = before.lastOrNull(),
                personalRecord = before.isNotEmpty() && today.estimatedMax > before.maxOf { it.estimatedMax },
            )
        }
        return WorkoutSummary(exercises)
    }

    private fun pct(now: Double, then: Double) = ((then - now) / then * 100).roundToInt()
}
