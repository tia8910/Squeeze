package com.squeeze.core.workout

import com.squeeze.core.model.Goal
import com.squeeze.core.model.TrainingAge
import kotlin.math.roundToInt

enum class Intensity(val label: String) { EASY("Easy"), MODERATE("Moderate"), HARD("Hard") }

/**
 * Every sport the log accepts, with its energy cost at three intensities.
 *
 * METs — multiples of resting burn — from the Compendium of Physical Activities (Ainsworth
 * et al., 2011 update), the reference every activity tracker's estimate traces back to.
 *
 * @param hasDistance whether the log asks for a distance
 */
enum class Sport(
    val label: String,
    private val easy: Double,
    private val moderate: Double,
    private val hard: Double,
    val hasDistance: Boolean = false,
) {
    STRENGTH("Strength training", 3.5, 5.0, 6.0),
    RUNNING("Running", 7.0, 9.8, 11.5, hasDistance = true),
    CYCLING("Cycling", 5.8, 8.0, 10.0, hasDistance = true),
    SWIMMING("Swimming", 6.0, 8.3, 10.0, hasDistance = true),
    WALKING("Walking", 3.0, 3.8, 5.0, hasDistance = true),
    HIKING("Hiking", 5.3, 6.0, 7.8, hasDistance = true),
    ROWING("Rowing", 4.8, 7.0, 8.5, hasDistance = true),
    FOOTBALL("Football", 7.0, 8.0, 10.0),
    BASKETBALL("Basketball", 6.0, 6.5, 8.0),
    TENNIS("Tennis", 5.0, 7.3, 8.0),
    PADEL("Padel / squash", 5.0, 6.0, 7.3),
    VOLLEYBALL("Volleyball", 3.0, 4.0, 6.0),
    MARTIAL_ARTS("Boxing / martial arts", 5.5, 7.8, 10.3),
    HIIT("HIIT / circuits", 6.0, 8.0, 10.0),
    CROSSFIT("CrossFit", 5.5, 8.0, 10.0),
    YOGA("Yoga / Pilates", 2.5, 3.0, 4.0),
    CLIMBING("Climbing", 5.8, 7.5, 8.0),
    DANCE("Dance", 4.5, 5.5, 7.8),
    SKIING("Skiing / snowboarding", 5.3, 7.0, 9.0),
    OTHER("Other sport", 4.0, 6.0, 8.0),
    ;

    fun met(intensity: Intensity): Double = when (intensity) {
        Intensity.EASY -> easy
        Intensity.MODERATE -> moderate
        Intensity.HARD -> hard
    }
}

object ActivityCalories {

    /**
     * Calories burned above rest — the extra a session costs, which is what a nutrition plan
     * that already counts resting burn must add. (MET − 1) × kg × hours.
     */
    fun netKcal(sport: Sport, intensity: Intensity, minutes: Int, weightKg: Double): Int =
        ((sport.met(intensity) - 1.0) * weightKg * minutes / 60.0).roundToInt().coerceAtLeast(0)
}

/** One logged strength set. */
data class LoggedSet(val weightKg: Double, val reps: Int, val rir: Int? = null)

/**
 * How to train one exercise for this user.
 *
 * @param restSeconds between sets; longer for heavy compounds, where it is strength that
 *   recovers slowly, not breath
 */
data class Prescription(
    val sets: Int,
    val reps: IntRange,
    val rir: Int,
    val restSeconds: Int,
) {
    val summary: String
        get() = "$sets × ${reps.first}–${reps.last} reps · leave $rir in the tank · rest ${formatRest(restSeconds)}"

    private fun formatRest(seconds: Int) = if (seconds % 60 == 0) "${seconds / 60} min" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')} min"
}

/**
 * What to do on the very next set.
 *
 * @param weightKg the weight to load; null when the user should pick (first set)
 * @param finished true once the planned sets are done
 */
data class SetAdvice(val weightKg: Double?, val reps: Int, val text: String, val finished: Boolean)

/** What to do on the next set or session, and why. */
data class Suggestion(val weightKg: Double?, val reps: IntRange, val reason: String)

/**
 * Sets, reps and loading from the user's goal, experience and what they have logged.
 *
 * **Double progression.** Work inside a rep range; once every set reaches the top of it, add
 * the smallest load the equipment allows and start again at the bottom. It needs nothing but
 * the log, it never asks for a max test, and it is how most lifters who keep progressing
 * for years actually train.
 */
object Coaching {

    fun prescribe(
        goal: Goal,
        trainingAge: TrainingAge,
        compound: Boolean,
        weakPoint: Boolean,
    ): Prescription {
        val base = when (goal) {
            Goal.STRENGTH -> if (compound) Prescription(4, 3..6, 2, 180) else Prescription(3, 8..10, 2, 90)
            Goal.HYPERTROPHY, Goal.RECOMP ->
                if (compound) Prescription(3, 6..10, 2, 120) else Prescription(3, 10..15, 1, 75)
            // In a deficit the job is keeping the load on the bar: fewer, heavier sets.
            Goal.CUT, Goal.MAKE_WEIGHT ->
                if (compound) Prescription(3, 5..8, 2, 150) else Prescription(2, 8..12, 1, 75)
        }
        val experience = when (trainingAge) {
            TrainingAge.NOVICE -> -1
            TrainingAge.INTERMEDIATE -> 0
            TrainingAge.ADVANCED -> 1
        }
        // A weak point the AI scan found gets one more set: the cheapest way to add volume
        // exactly where it is needed.
        val sets = (base.sets + experience + if (weakPoint) 1 else 0).coerceIn(2, 6)
        return base.copy(sets = sets, rir = (base.rir + if (trainingAge == TrainingAge.NOVICE) 1 else 0))
    }

    /** Load step for the next jump: bigger for lower-body compounds, the smallest plate otherwise. */
    fun increment(compound: Boolean, lowerBody: Boolean): Double = when {
        compound && lowerBody -> 5.0
        compound -> 2.5
        else -> 1.0
    }

    /**
     * The next session from the last one.
     *
     * @param last the sets of this exercise from the most recent session it was logged in
     */
    fun next(last: List<LoggedSet>, prescription: Prescription, compound: Boolean, lowerBody: Boolean): Suggestion {
        val working = last.filter { it.reps > 0 }
        if (working.isEmpty()) {
            return Suggestion(
                weightKg = null,
                reps = prescription.reps,
                reason = "First time: pick a weight you could lift ${prescription.reps.last + prescription.rir} " +
                    "times, do ${prescription.reps.last}, and log it — the app takes it from there.",
            )
        }
        val top = working.maxOf { it.weightKg }
        val atTop = working.filter { it.weightKg == top }
        val allAtTop = atTop.size >= prescription.sets.coerceAtMost(working.size) &&
            atTop.all { it.reps >= prescription.reps.last }
        val anyBelow = atTop.any { it.reps < prescription.reps.first }

        return when {
            allAtTop -> {
                val next = top + increment(compound, lowerBody)
                Suggestion(
                    next,
                    prescription.reps.first..prescription.reps.last,
                    "Every set hit ${prescription.reps.last} at ${fmt(top)} kg — go up to ${fmt(next)} kg " +
                        "and aim for ${prescription.reps.first}+ reps.",
                )
            }
            anyBelow && top > 0 -> {
                val back = (top * 0.9 / 2.5).roundToInt() * 2.5
                Suggestion(
                    back,
                    prescription.reps,
                    "Last time fell below ${prescription.reps.first} reps at ${fmt(top)} kg. Drop to " +
                        "${fmt(back)} kg and build back up through the range.",
                )
            }
            else -> {
                val best = atTop.maxOf { it.reps }
                Suggestion(
                    top,
                    (best + 1).coerceAtMost(prescription.reps.last)..prescription.reps.last,
                    "Stay at ${fmt(top)} kg and beat last time: ${atTop.joinToString(", ") { it.reps.toString() }} " +
                        "reps → aim for ${(best + 1).coerceAtMost(prescription.reps.last)}+ on each set.",
                )
            }
        }
    }

    /**
     * What to do on the next set, from the sets already done today — the call a coach makes
     * standing next to you.
     *
     * Reads the last set against the prescription: reps against the range, and — when the
     * user logged it — reps in reserve against the target. Too easy (the top of the range with
     * more than a rep to spare) means more weight now, not next week; too hard (below the
     * range, or failure on an early set) means less, so the remaining sets stay productive;
     * in range means same weight, one more rep. After the last planned set it says so, and
     * what next session's target is.
     */
    fun nextSet(
        done: List<LoggedSet>,
        prescription: Prescription,
        compound: Boolean,
        lowerBody: Boolean,
    ): SetAdvice {
        val last = done.lastOrNull { it.reps > 0 }
            ?: return SetAdvice(null, prescription.reps.first, "Set 1: warm up with a light set first, then aim for " +
                "${prescription.reps.first}–${prescription.reps.last} reps, leaving ${prescription.rir} in the tank.", finished = false)
        val setNumber = done.size + 1
        val range = prescription.reps
        val seconds = prescription.restSeconds
        val rest = if (seconds % 60 == 0) {
            "Rest ${seconds / 60} min"
        } else {
            "Rest ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')} min"
        }
        val step = increment(compound, lowerBody)

        if (done.size >= prescription.sets) {
            val nextSession = next(done, prescription, compound, lowerBody)
            return SetAdvice(
                null, range.first,
                "That's your ${prescription.sets} sets — done. Next session: ${nextSession.reason}",
                finished = true,
            )
        }

        val spare = last.rir
        return when {
            last.reps > range.last || (last.reps >= range.last && spare != null && spare > prescription.rir + 1) -> {
                val up = last.weightKg + step
                SetAdvice(up, range.first, "Too light — ${last.reps} reps${spare?.let { " with $it to spare" }.orEmpty()}. " +
                    "Set $setNumber: go up to ${fmt(up)} kg for ${range.first}–${range.last}. $rest.", finished = false)
            }
            last.reps < range.first || (spare == 0 && done.size < prescription.sets - 1) -> {
                val down = kotlin.math.max(0.0, ((last.weightKg * 0.9) / step).roundToInt() * step)
                SetAdvice(down, range.first, "Too heavy — ${last.reps} reps${if (spare == 0) " to failure" else ""}. " +
                    "Set $setNumber: drop to ${fmt(down)} kg so the rest of your sets count. $rest.", finished = false)
            }
            else -> {
                val target = (last.reps + 1).coerceAtMost(range.last)
                SetAdvice(last.weightKg, target, "Good set. Set $setNumber: stay at ${fmt(last.weightKg)} kg and aim for " +
                    "$target reps. $rest.", finished = false)
            }
        }
    }

    /** Epley: an estimate of the one-rep max, for tracking strength across rep ranges. */
    fun estimatedOneRepMax(set: LoggedSet): Double =
        if (set.reps <= 1) set.weightKg else set.weightKg * (1 + set.reps / 30.0)

    private fun fmt(kg: Double) = if (kg % 1.0 == 0.0) kg.toInt().toString() else ((kg * 10).roundToInt() / 10.0).toString()
}
