package com.squeeze.core.nutrition

import com.squeeze.core.model.Goal
import com.squeeze.core.model.Sex

/**
 * The micronutrients the plan tracks: the ones lifters most often run short of, and the ones
 * training itself spends.
 *
 * @param unit what the targets are measured in
 * @param fixes foods that close a gap, in the order most people can actually act on
 */
enum class Micronutrient(val label: String, val unit: String, val fixes: String) {
    FIBER("Fibre", "g", "oats, beans, lentils, berries, vegetables at two meals"),
    CALCIUM("Calcium", "mg", "milk or yogurt, cheese, fortified plant milk, tofu set with calcium"),
    IRON("Iron", "mg", "red meat, lentils, spinach — with vitamin C to absorb it"),
    MAGNESIUM("Magnesium", "mg", "pumpkin seeds, almonds, dark chocolate, beans, whole grains"),
    POTASSIUM("Potassium", "mg", "potatoes, bananas, beans, leafy greens, yogurt"),
    ZINC("Zinc", "mg", "red meat, shellfish, pumpkin seeds, chickpeas"),
    VITAMIN_C("Vitamin C", "mg", "citrus, kiwi, peppers, broccoli, berries"),
    FOLATE("Folate", "µg", "leafy greens, lentils, chickpeas, asparagus"),
    VITAMIN_B12("Vitamin B12", "µg", "meat, fish, eggs, dairy — a supplement if you eat none"),
    VITAMIN_D("Vitamin D", "µg", "oily fish, egg yolks, midday sun; most people in winter need a supplement"),
    OMEGA_3("Omega-3 (EPA+DHA)", "mg", "salmon, sardines or mackerel twice a week, or a fish-oil capsule"),
}

/** A daily target, and what the sample day supplies against it. */
data class MicroCoverage(
    val nutrient: Micronutrient,
    val target: Double,
    val supplied: Double,
) {
    val percent: Int get() = if (target <= 0) 100 else (supplied / target * 100).toInt()
    val short: Boolean get() = percent < SHORT_BELOW

    companion object {
        /** Under this share of the target, the plan names it as a gap. */
        const val SHORT_BELOW = 80
    }
}

/**
 * Daily micronutrient targets for this user.
 *
 * Starts from the dietary reference intakes (US National Academies) for age and sex — the
 * amounts that cover nearly everyone — then moves the ones training and dieting move:
 *
 *  - **Sweat.** Hard sessions lose sodium, potassium and magnesium, so those rise with the
 *    training days the programme schedules.
 *  - **Deficit.** Eating less food means less of everything in it, which is why a cut is
 *    where gaps appear; the plan does not raise targets for it, it checks them more closely
 *    and says so.
 *  - **Iron for women** until 51, at more than twice the male amount, because it is the
 *    deficiency most common in female athletes.
 */
object MicroTargets {

    fun targets(sex: Sex, ageYears: Int, trainingDaysPerWeek: Int, calories: Int): Map<Micronutrient, Double> {
        val female = sex == Sex.FEMALE
        val sweat = trainingDaysPerWeek.coerceIn(0, 7) / 7.0
        return mapOf(
            Micronutrient.FIBER to maxOf(25.0, calories / 1000.0 * 14),
            Micronutrient.CALCIUM to when {
                female && ageYears > 50 -> 1_200.0
                !female && ageYears > 70 -> 1_200.0
                else -> 1_000.0
            },
            Micronutrient.IRON to if (female && ageYears <= 50) 18.0 else 8.0,
            Micronutrient.MAGNESIUM to (if (female) 320.0 else 420.0) + 60.0 * sweat,
            Micronutrient.POTASSIUM to (if (female) 2_600.0 else 3_400.0) + 400.0 * sweat,
            Micronutrient.ZINC to if (female) 8.0 else 11.0,
            Micronutrient.VITAMIN_C to if (female) 75.0 else 90.0,
            Micronutrient.FOLATE to 400.0,
            Micronutrient.VITAMIN_B12 to 2.4,
            Micronutrient.VITAMIN_D to if (ageYears > 70) 20.0 else 15.0,
            Micronutrient.OMEGA_3 to 500.0,
        )
    }

    /**
     * Sodium, mg a day: the baseline intake plus what training sweats out. Not tracked
     * against the sample day, because most of it comes from the salt shaker, not the food.
     */
    fun sodiumMg(trainingDaysPerWeek: Int): Int = 1_500 + 1_000 * trainingDaysPerWeek.coerceIn(0, 7) / 7

    /** Plain-language notes that follow from the gaps and from the goal. */
    fun advice(coverage: List<MicroCoverage>, goal: Goal, sex: Sex, trainingDaysPerWeek: Int): List<String> =
        buildList {
            coverage.filter { it.short }.forEach { gap ->
                add("${gap.nutrient.label} is at ${gap.percent}% on this day — add ${gap.nutrient.fixes}.")
            }
            if (goal == Goal.CUT || goal == Goal.MAKE_WEIGHT) {
                add("A deficit means less food and so less of every micronutrient in it. Keep " +
                    "vegetables and fruit at most meals; a basic multivitamin is cheap insurance.")
            }
            if (trainingDaysPerWeek >= 4) {
                add("On ${trainingDaysPerWeek} training days a week you sweat out sodium, " +
                    "potassium and magnesium — salt your food normally and aim for about " +
                    "${sodiumMg(trainingDaysPerWeek)} mg sodium a day.")
            }
            if (sex == Sex.FEMALE) {
                add("Iron needs are more than double a man's. If you tire easily or train hard, " +
                    "ask for a ferritin test before supplementing.")
            }
        }
}
