package com.squeeze.core.nutrition

import com.squeeze.core.model.Goal
import com.squeeze.core.model.Sex
import com.squeeze.core.model.TrainingAge
import com.squeeze.core.text.fixed
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Everything the planner knows about the user, gathered from the rest of the app.
 *
 * Nothing here is typed in on the nutrition page. Weight comes from the log, body fat from
 * the trend (which the AI scan feeds), training days from the programme, the goal and its
 * deadline from the profile, and the weight slope from the trend filter — so the plan moves
 * when any of them does, without the user maintaining a second copy.
 *
 * @param weightDaysOld how long ago [weightKg] was recorded
 * @param bodyFatPercent the trend's current level; null when there is no body-fat reading
 * @param bodyFatSource what produced [bodyFatPercent], for the explanation
 * @param weightTrendKgPerWeek the weight trend's slope, only when it is distinguishable from
 *   noise; null otherwise
 * @param trendDays how many days the weight trend spans
 * @param targetWeightKg / [targetBodyFatPercent] / [daysToDeadline] the dated goal, if set
 * @param priorityGroups the groups the AI scan found lagging, for the explanation
 */
data class NutritionInputs(
    val sex: Sex,
    val ageYears: Int,
    val heightCm: Double,
    val weightKg: Double,
    val weightDaysOld: Long? = null,
    val bodyFatPercent: Double? = null,
    val bodyFatSource: String? = null,
    val goal: Goal,
    val trainingAge: TrainingAge = TrainingAge.INTERMEDIATE,
    val trainingDaysPerWeek: Int = 4,
    val trainingDaysFromProgramme: Boolean = false,
    val weightTrendKgPerWeek: Double? = null,
    val trendDays: Long? = null,
    val targetWeightKg: Double? = null,
    val targetBodyFatPercent: Double? = null,
    val daysToDeadline: Long? = null,
    val priorityGroups: List<String> = emptyList(),
    /** Foods the user picked on the nutrition page; the meal programme is built from them. */
    val favouriteFoods: Set<String> = emptySet(),
    /**
     * Exercise calories above resting, per day, averaged over the logged sessions of the last
     * two weeks. Null when too little has been logged to trust over the planned days.
     */
    val loggedExerciseKcalPerDay: Double? = null,
    /** Sessions logged in the last two weeks, across every sport. */
    val loggedSessions: Int = 0,
    /**
     * Exercise calories above rest per day that the user's planned week of sports costs —
     * used until there is enough logged to replace it.
     */
    val plannedExerciseKcalPerDay: Double? = null,
    /** The planned week in a few words, e.g. "Gym ×3, Running ×2", for the explanation. */
    val plannedSummary: String? = null,
)

data class Macros(val calories: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

data class FoodPortion(val food: String, val grams: Int)

/** One meal of the sample day, with the micronutrients its foods supply. */
data class Meal(
    val name: String,
    val items: List<FoodPortion>,
    val macros: Macros,
    val micros: Map<Micronutrient, Double> = emptyMap(),
)

/**
 * A day's eating, derived from the user's own numbers.
 *
 * @param trainingDay / [restDay] the two day types; carbohydrate moves between them, protein
 *   and fat do not, and the week averages to [average]
 * @param intendedKgPerWeek the weight change the plan aims for; negative is loss
 * @param adjustmentKcal how far the trend moved calories from the formula, per day
 * @param reasoning each number traced back to where it came from, in order
 * @param warnings what would make the plan wrong, and how to fix it
 */
data class NutritionPlan(
    val average: Macros,
    val trainingDay: Macros,
    val restDay: Macros,
    val maintenanceCalories: Int,
    val bmr: Int,
    val leanMassKg: Double?,
    val intendedKgPerWeek: Double,
    val adjustmentKcal: Int,
    val fiberG: Int,
    val waterLitres: Double,
    val meals: List<Meal>,
    /** Seven days of meals from the user's favourite foods; [meals] is its first training day. */
    val week: List<DayPlan>,
    val micros: List<MicroCoverage>,
    val microAdvice: List<String>,
    val sodiumMg: Int,
    val reasoning: List<String>,
    val warnings: List<String>,
)

/**
 * Builds a nutrition plan from the user's measurements, goal and training, and keeps it
 * honest against their trend.
 *
 * **Energy.** Resting expenditure from lean mass (Katch–McArdle) whenever a body-fat reading
 * exists, because two people of one weight and different composition burn different amounts
 * and lean mass is what separates them; Mifflin–St Jeor from height, weight and age when it
 * does not. Multiplied by an activity factor that rises with the training days the programme
 * actually schedules.
 *
 * **Goal.** A weekly rate of change, not a fixed deficit: a percentage of bodyweight for fat
 * loss (slower the leaner the user, because lean bodies give up muscle sooner), a lean-gain
 * rate by training age for size. A dated goal replaces the default with the rate the deadline
 * needs, clamped to what is safe — and says so when it clamps.
 *
 * **Feedback.** Formulas are off by a few hundred calories for any one person. Once the weight
 * trend has two weeks behind it and a slope the filter trusts, the plan compares what the
 * scale did with what the plan intended and moves calories by the difference — the same thing
 * a coach does at a weekly check-in.
 *
 * **Macros.** Protein from lean mass, highest in a deficit where it protects muscle; fat at a
 * floor that keeps hormones out of trouble; carbohydrate takes the rest, weighted toward
 * training days.
 */
object NutritionPlanner {

    /** Energy in a kilogram of bodyweight change, averaged over fat and lean tissue. */
    private const val KCAL_PER_KG = 7_700.0

    private const val MIN_CALORIES_MALE = 1_500
    private const val MIN_CALORIES_FEMALE = 1_200

    /** Two weeks of trend before the plan second-guesses its own formula. */
    private const val MIN_TREND_DAYS = 14

    /** Smaller gaps than this are noise in a weekly weight slope. */
    private const val DEAD_BAND_KG_PER_WEEK = 0.15

    /** The most one check-in moves calories, so a bad fortnight cannot swing the plan. */
    private const val MAX_ADJUSTMENT_KCAL = 300

    /** Daily life without exercise, as a multiple of resting burn. */
    private const val BASE_ACTIVITY = 1.35

    /** Fewer logged sessions than this in two weeks, and the plan trusts the programme instead. */
    private const val MIN_LOGGED_SESSIONS = 3

    fun plan(input: NutritionInputs): NutritionPlan {
        val reasoning = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val w = input.weightKg
        val female = input.sex == Sex.FEMALE

        // ── Where the numbers come from ──────────────────────────────────────────────────
        val weightAge = input.weightDaysOld?.let {
            when (it) {
                0L -> "today"
                1L -> "yesterday"
                else -> "$it days ago"
            }
        }
        reasoning += "Weight ${w.fixed(1)} kg" + (weightAge?.let { ", logged $it" } ?: "") + "."
        if ((input.weightDaysOld ?: 0) > 14) {
            warnings += "Your last weight is ${input.weightDaysOld} days old. A scan check-in " +
                "updates it and the plan recalculates."
        }

        val leanMass = input.bodyFatPercent?.takeIf { it in 3.0..60.0 }?.let { w * (1 - it / 100.0) }
        val bmr = if (leanMass != null) {
            reasoning += "Body fat ${input.bodyFatPercent!!.fixed(1)}%" +
                (input.bodyFatSource?.let { " ($it)" } ?: "") +
                " → lean mass ${leanMass.fixed(1)} kg. Resting burn from lean mass " +
                "(Katch–McArdle)."
            370.0 + 21.6 * leanMass
        } else {
            warnings += "No body-fat reading yet, so resting burn is estimated from height, " +
                "weight and age. A scan makes it more personal."
            10 * w + 6.25 * input.heightCm - 5 * input.ageYears + if (female) -161 else 5
        }

        // What the user actually did beats what the programme planned, once there is enough
        // of it: two weeks of logged sessions, across every sport, in place of an activity
        // factor that assumes every training day burns the same.
        val logged = input.loggedExerciseKcalPerDay?.takeIf { input.loggedSessions >= MIN_LOGGED_SESSIONS }
        val days = if (logged != null) {
            ((input.loggedSessions / 2.0).roundToInt()).coerceIn(0, 7)
        } else {
            input.trainingDaysPerWeek.coerceIn(0, 7)
        }
        val maintenance = if (logged != null) {
            reasoning += "You logged ${input.loggedSessions} sessions in the last two weeks, " +
                "burning about ${logged.roundToInt()} kcal a day above rest → maintenance about " +
                "${(bmr * BASE_ACTIVITY + logged).roundToInt()} kcal."
            bmr * BASE_ACTIVITY + logged
        } else if (input.plannedExerciseKcalPerDay != null) {
            val planned = input.plannedExerciseKcalPerDay
            reasoning += "Your week" + (input.plannedSummary?.let { " ($it)" } ?: "") +
                " plans about ${planned.roundToInt()} kcal a day of exercise above rest → maintenance " +
                "about ${(bmr * BASE_ACTIVITY + planned).roundToInt()} kcal. Log your sessions and it " +
                "switches to what you actually did."
            bmr * BASE_ACTIVITY + planned
        } else {
            val activity = BASE_ACTIVITY + 0.05 * days
            reasoning += "$days training days a week" +
                (if (input.trainingDaysFromProgramme) " (from your programme)" else "") +
                " → activity ×${activity.fixed(2)}, maintenance about ${(bmr * activity).roundToInt()} kcal." +
                " Log your workouts and this switches to what you actually burn."
            bmr * activity
        }

        // ── What the goal asks for ───────────────────────────────────────────────────────
        val intended = intendedRate(input, leanMass, reasoning, warnings)

        // ── What the trend says about the formula ────────────────────────────────────────
        var adjustment = 0
        val observed = input.weightTrendKgPerWeek
        if (observed != null && (input.trendDays ?: 0) >= MIN_TREND_DAYS) {
            val gap = observed - intended
            if (abs(gap) > DEAD_BAND_KG_PER_WEEK) {
                adjustment = (-gap * KCAL_PER_KG / 7.0).roundToInt()
                    .coerceIn(-MAX_ADJUSTMENT_KCAL, MAX_ADJUSTMENT_KCAL)
                reasoning += "Your weight trend is ${signed(observed)} kg/week against the " +
                    "plan's ${signed(intended)}, so calories are " +
                    (if (adjustment < 0) "lowered" else "raised") +
                    " by ${abs(adjustment)} kcal. This assumes you've been eating to plan — " +
                    "if not, follow it for two weeks first."
            } else {
                reasoning += "Your weight trend (${signed(observed)} kg/week) matches the plan — " +
                    "no correction needed."
            }
        }

        var calories = (maintenance + intended * KCAL_PER_KG / 7.0 + adjustment).roundToInt()
        val floor = max(if (female) MIN_CALORIES_FEMALE else MIN_CALORIES_MALE, bmr.roundToInt())
        if (calories < floor) {
            warnings += "Calories were held at $floor — going lower costs muscle and " +
                "training quality faster than it costs fat."
            calories = floor
        }

        // ── Macros ───────────────────────────────────────────────────────────────────────
        val proteinPerLean = when (input.goal) {
            Goal.CUT, Goal.MAKE_WEIGHT -> 2.7
            Goal.RECOMP -> 2.5
            Goal.HYPERTROPHY, Goal.STRENGTH -> 2.2
        }
        val protein = if (leanMass != null) {
            proteinPerLean * leanMass
        } else {
            // Without lean mass, bodyweight capped at a BMI of 27 so a heavier user is not
            // prescribed protein for fat tissue.
            val capped = min(w, 27.0 * (input.heightCm / 100.0) * (input.heightCm / 100.0))
            proteinPerLean * 0.85 * capped
        }
        var fat = max((if (input.goal == Goal.CUT) 0.7 else 0.8) * w, 0.22 * calories / 9.0)
        var carbs = (calories - protein * 4 - fat * 9) / 4.0
        if (carbs < 50) {
            fat = max(0.2 * calories / 9.0, fat - (50 - carbs) * 4 / 9.0)
            carbs = max(0.0, (calories - protein * 4 - fat * 9) / 4.0)
        }
        reasoning += "Protein ${protein.roundToInt()} g — " +
            (if (leanMass != null) "${proteinPerLean.fixed(1)} g per kg of lean mass" else "from bodyweight") +
            (if (input.goal == Goal.CUT || input.goal == Goal.MAKE_WEIGHT) {
                ", high because a deficit is when muscle is at risk."
            } else {
                "."
            })

        val average = Macros(calories, protein.roundToInt(), carbs.roundToInt(), fat.roundToInt())

        // Carbohydrate follows training: more on the days it is burned, less on the rest, the
        // same weekly total.
        val (trainingCarbs, restCarbs) = if (days in 1..6) {
            val onDays = carbs * 1.2
            onDays to max(0.0, (carbs * 7 - onDays * days) / (7 - days))
        } else {
            carbs to carbs
        }
        fun day(c: Double) = Macros(
            calories = (protein * 4 + c * 4 + fat * 9).roundToInt(),
            proteinG = protein.roundToInt(),
            carbsG = c.roundToInt(),
            fatG = fat.roundToInt(),
        )
        val trainingDay = day(trainingCarbs)
        val restDay = day(restCarbs)

        if (input.priorityGroups.isNotEmpty() && intended >= 0) {
            reasoning += "Your AI scan's weak points — ${input.priorityGroups.joinToString()} — are " +
                "prioritised in your training block; this surplus and protein are what let " +
                "them grow."
        } else if (input.priorityGroups.isNotEmpty()) {
            reasoning += "Your AI scan's weak points — ${input.priorityGroups.joinToString()} — " +
                "stay prioritised in training so the deficit takes fat, not muscle, from them."
        }

        // The week's meals, from the user's favourite foods, and the micronutrients they
        // supply on an average day of it.
        val week = MealBuilder.week(trainingDay, restDay, days, input.favouriteFoods)
        val meals = week.firstOrNull { "training" in it.name }?.meals ?: week.first().meals
        val microTargets = MicroTargets.targets(input.sex, input.ageYears, days, calories)
        val micros = microTargets.map { (nutrient, target) ->
            MicroCoverage(
                nutrient,
                target,
                week.sumOf { d -> d.meals.sumOf { it.micros[nutrient] ?: 0.0 } } / week.size,
            )
        }
        if (input.favouriteFoods.isNotEmpty()) {
            reasoning += "Meals are built from your ${input.favouriteFoods.size} favourite foods, " +
                "rotated across the week."
        }

        return NutritionPlan(
            average = average,
            trainingDay = trainingDay,
            restDay = restDay,
            maintenanceCalories = maintenance.roundToInt(),
            bmr = bmr.roundToInt(),
            leanMassKg = leanMass,
            intendedKgPerWeek = intended,
            adjustmentKcal = adjustment,
            fiberG = (calories / 1000.0 * 14).roundToInt(),
            waterLitres = ((0.035 * w + 0.5 * days / 7.0) * 10).roundToInt() / 10.0,
            meals = meals,
            week = week,
            micros = micros,
            microAdvice = MicroTargets.advice(micros, input.goal, input.sex, days),
            sodiumMg = MicroTargets.sodiumMg(days),
            reasoning = reasoning,
            warnings = warnings,
        )
    }

    /** Weekly weight change, kg, the goal calls for; negative is loss. */
    private fun intendedRate(
        input: NutritionInputs,
        leanMass: Double?,
        reasoning: MutableList<String>,
        warnings: MutableList<String>,
    ): Double {
        val w = input.weightKg
        val female = input.sex == Sex.FEMALE
        val bf = input.bodyFatPercent
        val leanOffset = if (female) 8.0 else 0.0

        // What a dated goal needs, when one is set: the weight to arrive at (given directly,
        // or implied by a body-fat target at today's lean mass) over the weeks remaining.
        val weeks = input.daysToDeadline?.takeIf { it >= 7 }?.let { it / 7.0 }
        val goalWeight = input.targetWeightKg
            ?: input.targetBodyFatPercent?.let { t -> leanMass?.let { it / (1 - t / 100.0) } }
        val required = if (weeks != null && goalWeight != null) (goalWeight - w) / weeks else null

        val maxLoss = -0.01 * w
        val maxGain = when (input.trainingAge) {
            TrainingAge.NOVICE -> 0.0025 * w
            TrainingAge.INTERMEDIATE -> 0.0015 * w
            TrainingAge.ADVANCED -> 0.0008 * w
        }

        val default = when (input.goal) {
            Goal.CUT -> -w * when {
                bf == null -> 0.007
                bf > 20 + leanOffset -> 0.009
                bf > 12 + leanOffset -> 0.007
                else -> 0.005
            }
            Goal.MAKE_WEIGHT -> -0.005 * w
            Goal.RECOMP -> if (bf != null && bf > 15 + leanOffset) -0.0025 * w else 0.0
            Goal.STRENGTH -> 0.001 * w
            Goal.HYPERTROPHY -> if (bf != null && bf > 20 + leanOffset) {
                warnings += "At ${bf.fixed(1)}% body fat a bulk adds mostly fat. The plan " +
                    "uses a small surplus; a cut or recomposition first would build a better " +
                    "base."
                0.4 * maxGain
            } else {
                maxGain
            }
        }

        val rate = if (required != null && input.goal != Goal.STRENGTH) {
            val clamped = required.coerceIn(maxLoss, maxGain)
            reasoning += "Your goal of ${goalWeight!!.fixed(1)} kg in ${weeks!!.fixed(1)} weeks " +
                "needs ${signed(required)} kg/week."
            if (clamped != required) {
                warnings += "That deadline needs ${signed(required)} kg/week, faster than is " +
                    "safe. The plan uses ${signed(clamped)} kg/week — move the date to keep " +
                    "it honest."
            }
            clamped
        } else {
            default
        }

        reasoning += when {
            rate < -0.01 -> "Target: lose ${abs(rate).fixed(2)} kg a week " +
                "(${(abs(rate) / w * 100).fixed(2)}% of bodyweight)."
            rate > 0.01 -> "Target: gain ${rate.fixed(2)} kg a week — the rate new muscle can " +
                "actually be built at your training age."
            else -> "Target: hold your weight while composition shifts."
        }
        return rate
    }

    private fun signed(v: Double) = (if (v > 0) "+" else "") + v.fixed(2)
}
