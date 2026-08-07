package com.squeeze.core.bodycomp

import com.squeeze.core.model.Goal
import com.squeeze.core.model.Sex
import com.squeeze.core.text.fixed
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the user is aiming at, and by when.
 *
 * A goal without a date is a wish: it cannot be behind schedule, so nothing about it can
 * ever be wrong, and the app can never tell the user anything useful. A date turns it into
 * something with a required rate, which is a number that can be compared against the rate
 * actually being achieved — and that comparison is the only honest basis for advice.
 *
 * @param targetBodyFatPercent where the user wants to be. Null when the goal is not about
 *   body fat, e.g. a lifter adding size at a fixed waist.
 * @param targetWeightKg an alternative or additional target.
 * @param targetEpochDay the deadline, days since the Unix epoch.
 */
data class GoalTarget(
    val goal: Goal,
    val targetBodyFatPercent: Double? = null,
    val targetWeightKg: Double? = null,
    val targetEpochDay: Long,
)

/** Whether the current rate of change gets there in time. */
enum class GoalVerdict {
    /** On or ahead of the rate the deadline needs. */
    ON_TRACK,

    /** Moving the right way, but not fast enough to arrive on time. */
    BEHIND,

    /** Moving the wrong way. */
    WRONG_DIRECTION,

    /** Not enough measurements yet to say anything. */
    TOO_EARLY,

    /**
     * The deadline demands a rate that cannot be reached safely.
     *
     * Reported as its own verdict rather than folded into BEHIND, because the honest advice
     * is completely different: BEHIND means push harder, this means move the date.
     */
    UNREALISTIC,
}

/**
 * @param verdict the headline judgement
 * @param requiredRatePerWeek what the remaining gap and time demand
 * @param actualRatePerWeek what the trend is currently doing, or null when unknown
 * @param projectedValue where the current rate lands on the deadline
 * @param daysRemaining days left, negative once the deadline has passed
 * @param headline one sentence stating the position
 * @param actions concrete things to change, most important first. Empty when on track.
 */
data class GoalProgress(
    val verdict: GoalVerdict,
    val requiredRatePerWeek: Double,
    val actualRatePerWeek: Double?,
    val projectedValue: Double?,
    val daysRemaining: Long,
    val headline: String,
    val actions: List<String>,
)

/**
 * Judges progress against a dated goal and says what to change.
 *
 * The advice is deliberately arithmetic rather than motivational. "Keep going" is worth
 * nothing to someone eight weeks from a deadline they will miss by four points; the useful
 * output is the size of the gap between the rate they need and the rate they have, and the
 * one lever that closes it.
 *
 * Rate limits below are the load-bearing part. It is easy to build something that divides a
 * gap by a number of weeks and reports whatever falls out, and that is how apps end up
 * instructing people to lose two kilos a week. A rate that cannot be sustained without
 * shedding lean mass is not a plan, and this says so instead.
 */
object GoalPlanner {

    /**
     * The fastest fat loss that reliably spares muscle, as a fraction of bodyweight per week.
     *
     * Around 0.7% of bodyweight per week is the widely used ceiling for a lifter in a
     * deficit; past roughly 1% the proportion of the loss coming from lean tissue climbs
     * sharply, which defeats the point for anyone measuring body composition rather than
     * scale weight.
     */
    const val MAX_SAFE_LOSS_FRACTION_PER_WEEK = 0.01

    /** Sustainable target for someone who wants to keep their training quality. */
    const val COMFORTABLE_LOSS_FRACTION_PER_WEEK = 0.007

    /**
     * The fastest useful lean gain, in kilograms per week, for an intermediate lifter.
     *
     * Muscle accrues far more slowly than fat is lost, which is why a bulking deadline is
     * so much easier to set unrealistically than a cutting one.
     */
    const val MAX_LEAN_GAIN_KG_PER_WEEK = 0.25

    /** Below this many days there is not enough runway for any rate to mean much. */
    private const val MIN_MEANINGFUL_DAYS = 7

    /**
     * @param currentBodyFatPercent the trend's current level, not a single reading
     * @param actualRatePerWeek change per week from the trend, negative when falling. Null
     *   when there are too few measurements for a slope.
     * @param currentWeightKg needed to turn a percentage-point rate into a safe-rate check
     */
    fun evaluate(
        target: GoalTarget,
        currentBodyFatPercent: Double?,
        currentWeightKg: Double?,
        actualRatePerWeek: Double?,
        todayEpochDay: Long,
        sex: Sex,
    ): GoalProgress? {
        val goalValue = target.targetBodyFatPercent ?: return null
        val current = currentBodyFatPercent ?: return tooEarly(target, todayEpochDay)

        val daysRemaining = target.targetEpochDay - todayEpochDay
        val weeksRemaining = daysRemaining / 7.0

        if (daysRemaining < MIN_MEANINGFUL_DAYS) {
            return GoalProgress(
                verdict = if (abs(current - goalValue) < 1.0) {
                    GoalVerdict.ON_TRACK
                } else {
                    GoalVerdict.BEHIND
                },
                requiredRatePerWeek = 0.0,
                actualRatePerWeek = actualRatePerWeek,
                projectedValue = current,
                daysRemaining = daysRemaining,
                headline = if (daysRemaining < 0) {
                    "Your deadline has passed. You are at ${current.fixed(1)}% against " +
                        "a target of ${goalValue.fixed(1)}%."
                } else {
                    "$daysRemaining days left. You are at ${current.fixed(1)}% against " +
                        "a target of ${goalValue.fixed(1)}%."
                },
                actions = if (abs(current - goalValue) < 1.0) {
                    emptyList()
                } else {
                    listOf("Set a new date — there is not enough time left to change this much.")
                },
            )
        }

        val gap = goalValue - current
        val requiredRate = gap / weeksRemaining

        val projected = actualRatePerWeek?.let { current + it * weeksRemaining }

        val safeRate = safeWeeklyRate(current, currentWeightKg, losing = gap < 0)
        if (safeRate != null && abs(requiredRate) > safeRate) {
            val weeksNeeded = (abs(gap) / safeRate).roundToInt()
            return GoalProgress(
                verdict = GoalVerdict.UNREALISTIC,
                requiredRatePerWeek = requiredRate,
                actualRatePerWeek = actualRatePerWeek,
                projectedValue = projected,
                daysRemaining = daysRemaining,
                headline = "That date needs ${abs(requiredRate).fixed(2)} points a week, " +
                    "which is faster than is safe. About $weeksNeeded weeks is realistic " +
                    "for this change.",
                actions = listOf(
                    "Move the date out by about ${weeksNeeded - weeksRemaining.roundToInt()} " +
                        "weeks, or set a less ambitious target.",
                    if (gap < 0) {
                        "Going faster than this costs muscle, which raises your body fat " +
                            "percentage even as the scale falls — the opposite of the goal."
                    } else {
                        "Gaining faster than this adds fat rather than muscle."
                    },
                ),
            )
        }

        if (actualRatePerWeek == null) return tooEarly(target, todayEpochDay)

        // Moving away from the target, or not moving while a move is needed.
        val movingRightWay = actualRatePerWeek * gap > 0
        if (!movingRightWay && abs(gap) > 0.5) {
            return GoalProgress(
                verdict = GoalVerdict.WRONG_DIRECTION,
                requiredRatePerWeek = requiredRate,
                actualRatePerWeek = actualRatePerWeek,
                projectedValue = projected,
                daysRemaining = daysRemaining,
                headline = "You are moving away from your target, not toward it — " +
                    "${abs(actualRatePerWeek).fixed(2)} points a week in the wrong direction.",
                actions = advice(gap, currentWeightKg, requiredRate, actualRatePerWeek, sex),
            )
        }

        // Enough of the required rate to arrive on time, with a little tolerance: demanding
        // the exact rate would report BEHIND on noise alone.
        val onTrack = abs(actualRatePerWeek) >= abs(requiredRate) * ON_TRACK_TOLERANCE

        return GoalProgress(
            verdict = if (onTrack) GoalVerdict.ON_TRACK else GoalVerdict.BEHIND,
            requiredRatePerWeek = requiredRate,
            actualRatePerWeek = actualRatePerWeek,
            projectedValue = projected,
            daysRemaining = daysRemaining,
            headline = if (onTrack) {
                "On track. At ${abs(actualRatePerWeek).fixed(2)} points a week you reach " +
                    "${(projected ?: goalValue).fixed(1)}% around your deadline."
            } else {
                "Behind. You need ${abs(requiredRate).fixed(2)} points a week and you are " +
                    "doing ${abs(actualRatePerWeek).fixed(2)}, which lands at about " +
                    "${(projected ?: current).fixed(1)}% on the day."
            },
            actions = if (onTrack) emptyList() else {
                advice(gap, currentWeightKg, requiredRate, actualRatePerWeek, sex)
            },
        )
    }

    /**
     * Tolerance on the required rate before calling someone behind.
     *
     * A trend slope built from a handful of readings carries real noise, and reporting
     * "behind" every time it dips would train the user to ignore the verdict.
     */
    private const val ON_TRACK_TOLERANCE = 0.85

    private fun tooEarly(target: GoalTarget, todayEpochDay: Long) = GoalProgress(
        verdict = GoalVerdict.TOO_EARLY,
        requiredRatePerWeek = 0.0,
        actualRatePerWeek = null,
        projectedValue = null,
        daysRemaining = target.targetEpochDay - todayEpochDay,
        headline = "Not enough measurements yet to say whether you are on track.",
        actions = listOf(
            "Measure two or three more times over the next fortnight. A rate needs a run of " +
                "readings — a single scan cannot have a direction.",
        ),
    )

    /**
     * The fastest weekly change in body-fat percentage that does not cost lean mass.
     *
     * Converted from a bodyweight fraction, because the safe limit is a mass rate and the
     * goal is stated in percentage points. Losing `f` of bodyweight as pure fat moves the
     * percentage by roughly `f * (100 - bodyFat) / 100` points, which is why the same
     * kilogram is worth more percentage points to a lean person than to a heavy one.
     */
    private fun safeWeeklyRate(
        currentBodyFat: Double,
        weightKg: Double?,
        losing: Boolean,
    ): Double? {
        if (!losing) {
            // Gains are limited by how fast muscle can be built, not by bodyweight.
            return weightKg?.let { MAX_LEAN_GAIN_KG_PER_WEEK / it * 100.0 }
        }
        if (weightKg == null) return null

        val fatMassLostFraction = MAX_SAFE_LOSS_FRACTION_PER_WEEK
        return fatMassLostFraction * (100.0 - currentBodyFat)
    }

    /**
     * Concrete levers, ordered by how much they move the number.
     *
     * Energy balance first because it dominates, training second because it decides what the
     * loss is made of, and measurement discipline last because a trend that is not really
     * behind is the cheapest problem on the list to fix.
     */
    private fun advice(
        gap: Double,
        weightKg: Double?,
        requiredRate: Double,
        actualRate: Double,
        sex: Sex,
    ): List<String> = buildList {
        val shortfall = abs(requiredRate) - abs(actualRate)

        if (gap < 0) {
            // Roughly 7,700 kcal per kilogram of fat. Turning a percentage-point shortfall
            // into a daily calorie figure is the single most actionable thing here, because
            // it is the number the user can act on tonight.
            weightKg?.let { weight ->
                val kgPerWeek = shortfall / 100.0 * weight
                val dailyDeficit = (kgPerWeek * 7700.0 / 7.0).roundToInt()
                if (dailyDeficit > 0) {
                    add(
                        "Take about $dailyDeficit kcal a day out of your intake — that is " +
                            "the size of the gap between the rate you need and the rate you " +
                            "have.",
                    )
                }
            }
            add(
                "Keep lifting heavy and keep protein around " +
                    "${proteinTarget(weightKg, sex)} — in a deficit that is what decides " +
                    "whether the weight you lose is fat or muscle.",
            )
        } else {
            weightKg?.let { weight ->
                val kgPerWeek = shortfall / 100.0 * weight
                val dailySurplus = (kgPerWeek * 7700.0 / 7.0).roundToInt()
                if (dailySurplus > 0) {
                    add("Add about $dailySurplus kcal a day.")
                }
            }
            add("Add sets to the lifts you are trying to grow before you add food.")
        }

        add(
            "Measure at the same time of day, in the same state — a trend built from mixed " +
                "conditions can read as behind when nothing has actually changed.",
        )
    }

    private fun proteinTarget(weightKg: Double?, sex: Sex): String {
        if (weightKg == null) return "1.6 to 2.2 g per kg of bodyweight"
        val low = (weightKg * 1.6).roundToInt()
        val high = (weightKg * 2.2).roundToInt()
        return "$low to ${high}g a day"
    }
}
