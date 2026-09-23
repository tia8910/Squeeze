package com.squeeze.core.nutrition

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The groups a user picks favourites from. */
enum class FoodGroup(val label: String) {
    MEAT_POULTRY("Meat & poultry"),
    FISH_SEAFOOD("Fish & seafood"),
    DAIRY_EGGS("Dairy & eggs"),
    PLANT_PROTEIN("Plant protein"),
    GRAINS("Grains & bread"),
    STARCHY_VEG("Potatoes & starchy veg"),
    FRUIT("Fruit"),
    NUTS_SEEDS("Nuts & seeds"),
    FATS("Oils, avocado & cheese"),
}

/** What a food is in a meal: the thing that carries its protein, its carbs or its fat. */
enum class FoodRole { PROTEIN, CARB, FAT }

/** Where in the day a food makes sense. */
enum class MealSlot(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    AROUND_TRAINING("Around training"),
    DINNER("Dinner"),
}

/**
 * One food, per 100 g as eaten. Micronutrients are rounded USDA FoodData Central values:
 * close enough to find a gap, not a food label.
 *
 * @param maxGrams the most of it one meal will prescribe, so a solver chasing a macro target
 *   never asks for 150 g of olive oil or a litre of whey
 */
class Food(
    val name: String,
    val group: FoodGroup,
    val role: FoodRole,
    val slots: Set<MealSlot>,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val micros: Map<Micronutrient, Double> = emptyMap(),
    val maxGrams: Int = 400,
)

/** The foods the planner can build meals from. */
object FoodLibrary {

    private fun m(
        fiber: Double = 0.0, calcium: Double = 0.0, iron: Double = 0.0, magnesium: Double = 0.0,
        potassium: Double = 0.0, zinc: Double = 0.0, vitaminC: Double = 0.0, folate: Double = 0.0,
        b12: Double = 0.0, vitaminD: Double = 0.0, omega3: Double = 0.0,
    ) = mapOf(
        Micronutrient.FIBER to fiber,
        Micronutrient.CALCIUM to calcium,
        Micronutrient.IRON to iron,
        Micronutrient.MAGNESIUM to magnesium,
        Micronutrient.POTASSIUM to potassium,
        Micronutrient.ZINC to zinc,
        Micronutrient.VITAMIN_C to vitaminC,
        Micronutrient.FOLATE to folate,
        Micronutrient.VITAMIN_B12 to b12,
        Micronutrient.VITAMIN_D to vitaminD,
        Micronutrient.OMEGA_3 to omega3,
    )

    private val B = MealSlot.BREAKFAST
    private val L = MealSlot.LUNCH
    private val T = MealSlot.AROUND_TRAINING
    private val D = MealSlot.DINNER
    private val MAIN = setOf(L, D)

    val ALL: List<Food> = listOf(
        // Meat & poultry
        Food("Chicken breast", FoodGroup.MEAT_POULTRY, FoodRole.PROTEIN, MAIN, 31.0, 0.0, 3.6,
            m(calcium = 15.0, iron = 1.0, magnesium = 29.0, potassium = 256.0, zinc = 1.0, folate = 4.0, b12 = 0.3, vitaminD = 0.1, omega3 = 20.0)),
        Food("Turkey breast", FoodGroup.MEAT_POULTRY, FoodRole.PROTEIN, MAIN, 29.0, 0.0, 1.7,
            m(calcium = 12.0, iron = 1.5, magnesium = 32.0, potassium = 300.0, zinc = 2.0, folate = 7.0, b12 = 0.6)),
        Food("Lean beef", FoodGroup.MEAT_POULTRY, FoodRole.PROTEIN, MAIN + T, 26.0, 0.0, 8.0,
            m(calcium = 12.0, iron = 2.6, magnesium = 22.0, potassium = 330.0, zinc = 6.3, folate = 8.0, b12 = 2.5, vitaminD = 0.1, omega3 = 20.0)),
        // Fish & seafood
        Food("Salmon", FoodGroup.FISH_SEAFOOD, FoodRole.PROTEIN, MAIN, 22.0, 0.0, 12.0,
            m(calcium = 12.0, iron = 0.5, magnesium = 30.0, potassium = 490.0, zinc = 0.6, folate = 25.0, b12 = 3.2, vitaminD = 11.0, omega3 = 2_000.0),
            maxGrams = 250),
        Food("Tuna (canned)", FoodGroup.FISH_SEAFOOD, FoodRole.PROTEIN, MAIN + T, 26.0, 0.0, 1.0,
            m(calcium = 11.0, iron = 1.0, magnesium = 27.0, potassium = 240.0, zinc = 0.6, folate = 4.0, b12 = 2.5, vitaminD = 1.7, omega3 = 250.0),
            maxGrams = 200),
        Food("White fish (cod)", FoodGroup.FISH_SEAFOOD, FoodRole.PROTEIN, MAIN, 18.0, 0.0, 0.7,
            m(calcium = 14.0, iron = 0.5, magnesium = 42.0, potassium = 520.0, zinc = 0.6, vitaminC = 1.0, folate = 8.0, b12 = 1.0, vitaminD = 1.0, omega3 = 190.0)),
        Food("Shrimp", FoodGroup.FISH_SEAFOOD, FoodRole.PROTEIN, MAIN, 24.0, 0.2, 0.3,
            m(calcium = 70.0, iron = 0.5, magnesium = 35.0, potassium = 170.0, zinc = 1.6, b12 = 1.1, omega3 = 300.0)),
        // Dairy & eggs
        Food("Eggs", FoodGroup.DAIRY_EGGS, FoodRole.PROTEIN, setOf(B, T), 13.0, 1.1, 10.0,
            m(calcium = 56.0, iron = 1.8, magnesium = 12.0, potassium = 138.0, zinc = 1.3, folate = 47.0, b12 = 1.1, vitaminD = 2.0, omega3 = 50.0),
            maxGrams = 250),
        Food("Greek yogurt (0%)", FoodGroup.DAIRY_EGGS, FoodRole.PROTEIN, setOf(B, T), 10.0, 4.0, 0.4,
            m(calcium = 110.0, iron = 0.1, magnesium = 11.0, potassium = 141.0, zinc = 0.5, folate = 7.0, b12 = 0.75)),
        Food("Cottage cheese", FoodGroup.DAIRY_EGGS, FoodRole.PROTEIN, setOf(B, T), 11.0, 3.4, 4.3,
            m(calcium = 83.0, magnesium = 8.0, potassium = 104.0, zinc = 0.4, folate = 12.0, b12 = 0.4)),
        Food("Whey protein", FoodGroup.DAIRY_EGGS, FoodRole.PROTEIN, setOf(B, T), 80.0, 8.0, 6.0,
            m(calcium = 400.0, magnesium = 60.0, potassium = 500.0, zinc = 2.0, b12 = 1.0),
            maxGrams = 50),
        // Plant protein
        Food("Tofu (firm)", FoodGroup.PLANT_PROTEIN, FoodRole.PROTEIN, MAIN + B, 17.0, 3.0, 9.0,
            m(fiber = 2.3, calcium = 680.0, iron = 2.7, magnesium = 58.0, potassium = 240.0, zinc = 1.6, folate = 29.0)),
        Food("Tempeh", FoodGroup.PLANT_PROTEIN, FoodRole.PROTEIN, MAIN, 20.0, 8.0, 11.0,
            m(fiber = 5.0, calcium = 110.0, iron = 2.7, magnesium = 81.0, potassium = 410.0, zinc = 1.1, folate = 24.0)),
        Food("Lentils (cooked)", FoodGroup.PLANT_PROTEIN, FoodRole.PROTEIN, MAIN, 9.0, 20.0, 0.4,
            m(fiber = 8.0, calcium = 19.0, iron = 3.3, magnesium = 36.0, potassium = 369.0, zinc = 1.3, vitaminC = 1.5, folate = 181.0)),
        Food("Chickpeas (cooked)", FoodGroup.PLANT_PROTEIN, FoodRole.CARB, MAIN, 9.0, 27.0, 2.6,
            m(fiber = 7.6, calcium = 49.0, iron = 2.9, magnesium = 48.0, potassium = 291.0, zinc = 1.5, vitaminC = 1.3, folate = 172.0)),
        // Grains & bread
        Food("Oats", FoodGroup.GRAINS, FoodRole.CARB, setOf(B), 13.0, 60.0, 7.0,
            m(fiber = 10.0, calcium = 54.0, iron = 4.7, magnesium = 177.0, potassium = 429.0, zinc = 4.0, folate = 32.0),
            maxGrams = 150),
        Food("Wholegrain bread", FoodGroup.GRAINS, FoodRole.CARB, setOf(B, L, T), 13.0, 41.0, 3.4,
            m(fiber = 7.0, calcium = 107.0, iron = 2.5, magnesium = 75.0, potassium = 250.0, zinc = 1.8, folate = 42.0),
            maxGrams = 200),
        Food("Rice (cooked)", FoodGroup.GRAINS, FoodRole.CARB, MAIN + T, 2.7, 28.0, 0.3,
            m(fiber = 0.4, calcium = 10.0, iron = 0.2, magnesium = 12.0, potassium = 35.0, zinc = 0.5, folate = 3.0),
            maxGrams = 500),
        Food("Wholewheat pasta (cooked)", FoodGroup.GRAINS, FoodRole.CARB, MAIN, 5.5, 27.0, 0.9,
            m(fiber = 4.0, calcium = 15.0, iron = 1.1, magnesium = 30.0, potassium = 60.0, zinc = 0.8, folate = 5.0),
            maxGrams = 450),
        Food("Quinoa (cooked)", FoodGroup.GRAINS, FoodRole.CARB, MAIN, 4.4, 21.0, 1.9,
            m(fiber = 2.8, calcium = 17.0, iron = 1.5, magnesium = 64.0, potassium = 172.0, zinc = 1.1, folate = 42.0),
            maxGrams = 450),
        // Potatoes & starchy veg
        Food("Potatoes", FoodGroup.STARCHY_VEG, FoodRole.CARB, MAIN, 2.0, 17.0, 0.1,
            m(fiber = 1.8, calcium = 5.0, iron = 0.3, magnesium = 22.0, potassium = 380.0, zinc = 0.3, vitaminC = 13.0, folate = 10.0),
            maxGrams = 600),
        Food("Sweet potato", FoodGroup.STARCHY_VEG, FoodRole.CARB, MAIN, 1.6, 20.0, 0.1,
            m(fiber = 3.0, calcium = 30.0, iron = 0.6, magnesium = 25.0, potassium = 337.0, zinc = 0.3, vitaminC = 2.4, folate = 6.0),
            maxGrams = 500),
        // Fruit
        Food("Banana", FoodGroup.FRUIT, FoodRole.CARB, setOf(B, T), 1.1, 23.0, 0.3,
            m(fiber = 2.6, calcium = 5.0, iron = 0.3, magnesium = 27.0, potassium = 358.0, zinc = 0.15, vitaminC = 8.7, folate = 20.0),
            maxGrams = 300),
        Food("Berries", FoodGroup.FRUIT, FoodRole.CARB, setOf(B, T), 0.7, 12.0, 0.3,
            m(fiber = 2.4, calcium = 12.0, iron = 0.4, magnesium = 12.0, potassium = 120.0, vitaminC = 40.0, folate = 15.0),
            maxGrams = 300),
        Food("Apple", FoodGroup.FRUIT, FoodRole.CARB, setOf(T), 0.3, 14.0, 0.2,
            m(fiber = 2.4, calcium = 6.0, magnesium = 5.0, potassium = 107.0, vitaminC = 4.6, folate = 3.0),
            maxGrams = 300),
        Food("Orange", FoodGroup.FRUIT, FoodRole.CARB, setOf(B, T), 0.9, 12.0, 0.1,
            m(fiber = 2.4, calcium = 40.0, iron = 0.1, magnesium = 10.0, potassium = 181.0, vitaminC = 53.0, folate = 30.0),
            maxGrams = 300),
        // Nuts & seeds
        Food("Almonds", FoodGroup.NUTS_SEEDS, FoodRole.FAT, setOf(B, T), 21.0, 22.0, 50.0,
            m(fiber = 12.5, calcium = 269.0, iron = 3.7, magnesium = 270.0, potassium = 733.0, zinc = 3.1, folate = 44.0),
            maxGrams = 50),
        Food("Walnuts", FoodGroup.NUTS_SEEDS, FoodRole.FAT, setOf(B, T), 15.0, 14.0, 65.0,
            m(fiber = 6.7, calcium = 98.0, iron = 2.9, magnesium = 158.0, potassium = 441.0, zinc = 3.1, folate = 98.0),
            maxGrams = 40),
        Food("Peanut butter", FoodGroup.NUTS_SEEDS, FoodRole.FAT, setOf(B, T), 25.0, 20.0, 50.0,
            m(fiber = 6.0, calcium = 43.0, iron = 1.7, magnesium = 168.0, potassium = 558.0, zinc = 2.5, folate = 87.0),
            maxGrams = 40),
        Food("Chia seeds", FoodGroup.NUTS_SEEDS, FoodRole.FAT, setOf(B), 17.0, 42.0, 31.0,
            m(fiber = 34.0, calcium = 631.0, iron = 7.7, magnesium = 335.0, potassium = 407.0, zinc = 4.6, folate = 49.0),
            maxGrams = 30),
        // Oils, avocado & cheese
        Food("Olive oil", FoodGroup.FATS, FoodRole.FAT, MAIN, 0.0, 0.0, 100.0, maxGrams = 25),
        Food("Avocado", FoodGroup.FATS, FoodRole.FAT, setOf(B, L, D), 2.0, 9.0, 15.0,
            m(fiber = 6.7, calcium = 12.0, iron = 0.6, magnesium = 29.0, potassium = 485.0, zinc = 0.6, vitaminC = 10.0, folate = 81.0),
            maxGrams = 150),
        Food("Cheese (cheddar)", FoodGroup.FATS, FoodRole.FAT, setOf(B, L, D), 25.0, 1.3, 33.0,
            m(calcium = 720.0, iron = 0.7, magnesium = 28.0, potassium = 76.0, zinc = 3.6, folate = 18.0, b12 = 1.1, vitaminD = 0.6),
            maxGrams = 60),
    )

    fun byName(name: String): Food? = ALL.firstOrNull { it.name == name }

    /** Mixed vegetables — broccoli, spinach, peppers — counted for micronutrients only. */
    val VEGETABLES = m(
        fiber = 2.8, calcium = 70.0, iron = 1.5, magnesium = 45.0, potassium = 400.0, zinc = 0.4,
        vitaminC = 60.0, folate = 110.0,
    )
    const val VEGETABLE_GRAMS = 150
}

/** One day of the meal programme. */
data class DayPlan(val name: String, val meals: List<Meal>) {
    val macros: Macros
        get() = Macros(
            calories = meals.sumOf { it.macros.calories },
            proteinG = meals.sumOf { it.macros.proteinG },
            carbsG = meals.sumOf { it.macros.carbsG },
            fatG = meals.sumOf { it.macros.fatG },
        )
}

/**
 * Meals in real food, sized to a day's macros, from the foods the user likes.
 *
 * **Favourites first, never favourites only by accident.** For each meal the planner looks
 * for a liked food that suits that meal and plays the role it needs — the protein, the carb,
 * the fat. If the user liked foods of that role but none suit this meal, it uses a liked one
 * anyway (salmon at breakfast beats eggs for someone who picked no eggs). Only when nothing of
 * that role was liked at all does it fall back to the library, so a vegetarian who ticked tofu
 * and lentils is never served chicken to fill a gap.
 *
 * **Variety.** Across the week each meal rotates through the liked options, with lunch and
 * dinner offset so the same food does not appear twice in one day when there is a choice.
 *
 * **Solving.** Protein and carb portions are solved together — lentils carry both — then the
 * day's remaining fat is spread over the meals with a fat source. Vegetables are added at the
 * two main meals and not counted toward calories, but they are counted toward micronutrients,
 * where they do most of the work.
 */
object MealBuilder {

    private class SlotShare(val slot: MealSlot, val protein: Double, val carbs: Double, val fat: Double, val vegetables: Boolean)

    // No added fat around training, where it slows digestion for no benefit.
    private val SHARES = listOf(
        SlotShare(MealSlot.BREAKFAST, 0.25, 0.25, 0.35, false),
        SlotShare(MealSlot.LUNCH, 0.25, 0.25, 0.30, true),
        SlotShare(MealSlot.AROUND_TRAINING, 0.25, 0.30, 0.0, false),
        SlotShare(MealSlot.DINNER, 0.25, 0.20, 0.35, true),
    )

    /** Carbs never fall below this share of their target to make room for fat. */
    private const val MIN_CARB_SCALE = 0.4

    private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** The defaults when a role has no favourite: the foods most people have to hand. */
    private val DEFAULTS = mapOf(
        MealSlot.BREAKFAST to listOf("Greek yogurt (0%)", "Oats", "Almonds"),
        MealSlot.LUNCH to listOf("Chicken breast", "Rice (cooked)", "Olive oil"),
        MealSlot.AROUND_TRAINING to listOf("Lean beef", "Banana"),
        MealSlot.DINNER to listOf("White fish (cod)", "Potatoes", "Olive oil"),
    )

    /** A single day, day 0 of the week. */
    fun build(day: Macros, favourites: Set<String> = emptySet()): List<Meal> = buildDay(day, favourites, 0)

    /** Seven days rotating through the favourites. */
    fun week(training: Macros, rest: Macros, trainingDays: Int, favourites: Set<String>): List<DayPlan> {
        val onDays = trainingDayIndices(trainingDays)
        return DAY_NAMES.mapIndexed { i, name ->
            val target = if (i in onDays) training else rest
            DayPlan(name + if (i in onDays) " · training" else " · rest", buildDay(target, favourites, i))
        }
    }

    /** Spreads training days across the week rather than stacking them. */
    fun trainingDayIndices(days: Int): Set<Int> = when (days.coerceIn(0, 7)) {
        0 -> emptySet()
        1 -> setOf(2)
        2 -> setOf(1, 4)
        3 -> setOf(0, 2, 4)
        4 -> setOf(0, 1, 3, 4)
        5 -> setOf(0, 1, 2, 4, 5)
        6 -> setOf(0, 1, 2, 3, 4, 5)
        else -> (0..6).toSet()
    }

    private fun pick(slot: MealSlot, role: FoodRole, favourites: Set<String>, rotation: Int): Food? {
        val liked = FoodLibrary.ALL.filter { it.name in favourites && it.role == role }
        val pool = liked.filter { slot in it.slots }.ifEmpty { liked }
        if (pool.isNotEmpty()) return pool[rotation % pool.size]
        // The around-training meal takes no added fat, and that is a choice, not a gap.
        return DEFAULTS[slot].orEmpty()
            .mapNotNull(FoodLibrary::byName)
            .firstOrNull { it.role == role }
    }

    private fun buildDay(day: Macros, favourites: Set<String>, dayIndex: Int): List<Meal> {
        data class Plan(val share: SlotShare, val protein: Food?, val carb: Food?, val fat: Food?, var p: Double, var c: Double)

        val plans = SHARES.mapIndexed { slotIndex, share ->
            // Lunch and dinner offset by one so the same day gets two different mains.
            val rotation = dayIndex + if (share.slot == MealSlot.DINNER) 1 else 0
            val protein = pick(share.slot, FoodRole.PROTEIN, favourites, rotation)
            val carb = pick(share.slot, FoodRole.CARB, favourites, rotation + slotIndex)
            val fat = if (share.fat > 0) pick(share.slot, FoodRole.FAT, favourites, rotation + slotIndex) else null
            val (p, c) = solve(day.proteinG * share.protein, day.carbsG * share.carbs, protein, carb)
            Plan(share, protein, carb, fat, p, c)
        }

        val fatSoFar = plans.sumOf { (it.p * (it.protein?.fat ?: 0.0) + it.c * (it.carb?.fat ?: 0.0)) / 100 }
        val fatLeft = max(0.0, day.fatG - fatSoFar)

        // Favourites like eggs and salmon bring more fat than the day has room for. Rather
        // than overshoot calories, the carbohydrate gives way by the same energy — the day
        // stays on target and simply runs higher-fat, which is what those favourites mean.
        val excessFat = max(0.0, fatSoFar - day.fatG)
        if (excessFat > 0) {
            val carbFromCarbFoods = plans.sumOf { it.c * (it.carb?.carbs ?: 0.0) / 100 }
            if (carbFromCarbFoods > 0) {
                val scale = (1 - excessFat * 9 / 4 / carbFromCarbFoods).coerceIn(MIN_CARB_SCALE, 1.0)
                plans.forEach { it.c *= scale }
            }
        }
        val fatShareTotal = plans.filter { it.fat != null }.sumOf { it.share.fat }.takeIf { it > 0 } ?: 1.0

        return plans.map { plan ->
            val fatGrams = plan.fat?.let { fatLeft * plan.share.fat / fatShareTotal / it.fat * 100 } ?: 0.0
            val portions = listOfNotNull(
                plan.protein?.let { it to plan.p },
                plan.carb?.let { it to plan.c },
                plan.fat?.let { it to fatGrams },
            )
                .groupBy({ it.first }, { it.second })
                .map { (food, grams) -> food to (min(grams.sum(), food.maxGrams.toDouble()) / 5).roundToInt() * 5 }
                .filter { (_, grams) -> grams > 0 }

            val p = portions.sumOf { (f, g) -> f.protein * g / 100 }
            val c = portions.sumOf { (f, g) -> f.carbs * g / 100 }
            val f = portions.sumOf { (food, g) -> food.fat * g / 100 }
            val vegetables = plan.share.vegetables
            val items = portions.map { (food, grams) -> FoodPortion(food.name, grams) } +
                if (vegetables) listOf(FoodPortion("Vegetables (not counted)", FoodLibrary.VEGETABLE_GRAMS)) else emptyList()
            val micros = Micronutrient.entries.associateWith { nutrient ->
                portions.sumOf { (food, grams) -> (food.micros[nutrient] ?: 0.0) * grams / 100 } +
                    if (vegetables) (FoodLibrary.VEGETABLES[nutrient] ?: 0.0) * FoodLibrary.VEGETABLE_GRAMS / 100 else 0.0
            }

            Meal(
                name = plan.share.slot.label,
                items = items,
                macros = Macros(
                    calories = (p * 4 + c * 4 + f * 9).roundToInt(),
                    proteinG = p.roundToInt(),
                    carbsG = c.roundToInt(),
                    fatG = f.roundToInt(),
                ),
                micros = micros,
            )
        }
    }

    /**
     * Grams of the protein food and the carb food that hit both targets at once — two
     * equations, two unknowns — falling back to one at a time when the foods are too alike
     * to separate or the exact answer would need a negative portion.
     */
    private fun solve(protein: Double, carbs: Double, pf: Food?, cf: Food?): Pair<Double, Double> {
        if (pf == null && cf == null) return 0.0 to 0.0
        if (pf == null) return 0.0 to carbs / cf!!.carbs * 100
        if (cf == null) return protein / pf.protein * 100 to 0.0
        val p1 = pf.protein / 100; val c1 = pf.carbs / 100
        val p2 = cf.protein / 100; val c2 = cf.carbs / 100
        val det = p1 * c2 - p2 * c1
        if (abs(det) > 1e-6) {
            val x = (protein * c2 - p2 * carbs) / det
            val y = (p1 * carbs - c1 * protein) / det
            if (x >= 0 && y >= 0) return x to y
        }
        val y = carbs / c2
        val x = max(0.0, (protein - y * p2) / p1)
        return x to y
    }
}
