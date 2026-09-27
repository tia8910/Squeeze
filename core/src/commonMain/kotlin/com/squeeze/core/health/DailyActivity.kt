package com.squeeze.core.health

import com.squeeze.core.text.grouped

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * One day as the user's other apps and devices recorded it — a watch, a phone's step counter,
 * a nutrition app — read from the phone's shared health store.
 *
 * Every field is nullable because each comes from a different source that may not exist: a
 * user with a watch but no food log has steps and no intake, and "no data" must never be
 * shown as zero. Zero steps is a claim; null is an absence.
 */
data class DailyActivity(
    val epochDay: Long,
    val steps: Long? = null,
    val activeKcal: Double? = null,
    val exerciseMinutes: Int? = null,
    val restingHeartRate: Int? = null,
    val sleepMinutes: Int? = null,
    val eatenKcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
) {
    val hasActivity: Boolean get() = steps != null || activeKcal != null || exerciseMinutes != null
    val hasIntake: Boolean get() = eatenKcal != null
}

/** Food logged elsewhere against today's plan. */
data class IntakeProgress(
    val eatenKcal: Int,
    val targetKcal: Int,
    val proteinG: Int?,
    val targetProteinG: Int,
) {
    val remainingKcal: Int get() = targetKcal - eatenKcal
    val remainingProteinG: Int? get() = proteinG?.let { targetProteinG - it }
    val fraction: Float get() = if (targetKcal <= 0) 0f else (eatenKcal.toFloat() / targetKcal).coerceIn(0f, 1.5f)
}

/**
 * What the synced data means for today, said the way the rest of the app says things: a
 * number, and what to do about it.
 */
object ActivityInsights {

    /**
     * 8,000 steps: where the large cohort studies see most of the mortality benefit level off
     * for adults under 60. Ten thousand is a pedometer advert from 1965, not a finding.
     */
    const val DEFAULT_STEP_GOAL = 8_000L

    /** Roughly 100 steps a minute at an ordinary walking pace. */
    private const val STEPS_PER_MINUTE = 100.0

    /**
     * A watch-recorded workout this long counts as today's session being done. Shorter than
     * this is a walk to the shop that the watch auto-detected, not a training session.
     */
    const val WORKOUT_MINUTES = 15

    fun watchWorkoutDone(day: DailyActivity?): Boolean = (day?.exerciseMinutes ?: 0) >= WORKOUT_MINUTES

    fun stepFraction(steps: Long, goal: Long = DEFAULT_STEP_GOAL): Float =
        if (goal <= 0) 0f else (steps.toFloat() / goal).coerceIn(0f, 1f)

    /** Minutes of walking that close the gap to the goal, or 0 when it is met. */
    fun walkMinutesToGoal(steps: Long, goal: Long = DEFAULT_STEP_GOAL): Int =
        if (steps >= goal) 0 else ceil((goal - steps) / STEPS_PER_MINUTE / 5.0).toInt() * 5

    fun stepsLine(steps: Long, goal: Long = DEFAULT_STEP_GOAL): String {
        val walk = walkMinutesToGoal(steps, goal)
        return if (walk == 0) {
            "Goal of ${(goal).grouped()} reached"
        } else {
            "${(goal - steps).grouped()} to go — about a $walk-minute walk"
        }
    }

    fun intake(day: DailyActivity?, targetKcal: Int, targetProteinG: Int): IntakeProgress? {
        val eaten = day?.eatenKcal ?: return null
        return IntakeProgress(
            eatenKcal = eaten.roundToInt(),
            targetKcal = targetKcal,
            proteinG = day.proteinG?.roundToInt(),
            targetProteinG = targetProteinG,
        )
    }

    fun intakeLine(p: IntakeProgress): String {
        val kcal = when {
            p.remainingKcal > 50 -> "${(p.remainingKcal).grouped()} kcal left"
            p.remainingKcal < -150 -> "${(-p.remainingKcal).grouped()} kcal over"
            else -> "On target"
        }
        val protein = p.remainingProteinG?.let {
            if (it > 5) " · $it g protein to go" else " · protein done"
        }.orEmpty()
        return kcal + protein
    }

    /**
     * A note on recovery from last night's sleep and resting heart rate, or null when
     * nothing stands out.
     *
     * Deliberately modest. A resting pulse a few beats above the user's own norm, after a
     * short night, is a reasonable signal to hold back one rep — not a diagnosis, and never a
     * reason to skip a session.
     *
     * @param baselineRestingHr the user's usual resting heart rate, e.g. a two-week median
     */
    fun recoveryNote(day: DailyActivity?, baselineRestingHr: Int?): String? {
        val sleep = day?.sleepMinutes
        val hr = day?.restingHeartRate
        val shortSleep = sleep != null && sleep < SHORT_SLEEP_MINUTES
        val elevated = hr != null && baselineRestingHr != null && hr - baselineRestingHr >= ELEVATED_BPM
        return when {
            shortSleep && elevated ->
                "Short night (${hoursMinutes(sleep!!)}) and resting pulse ${hr!! - baselineRestingHr!!} bpm above " +
                    "your usual. Train, but stop each set one rep earlier today."
            shortSleep ->
                "Short night (${hoursMinutes(sleep!!)}). Keep today's session, and keep the last set honest rather than heroic."
            elevated ->
                "Resting pulse ${hr!! - baselineRestingHr!!} bpm above your usual — often a sign of fatigue " +
                    "or a cold coming. Warm up longer and see how the first sets feel."
            else -> null
        }
    }

    fun hoursMinutes(minutes: Int): String = "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"

    /** The middle value of recent resting heart rates, ignoring days with none. */
    fun baseline(values: List<Int?>): Int? {
        val known = values.filterNotNull().sorted()
        if (known.size < MIN_BASELINE_DAYS) return null
        return known[known.size / 2]
    }

    private const val SHORT_SLEEP_MINUTES = 6 * 60
    private const val ELEVATED_BPM = 5
    private const val MIN_BASELINE_DAYS = 5
}
