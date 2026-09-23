package com.squeeze.core.coach

/** Every step the app can send the user to next. */
enum class StepId {
    AI_SCAN,
    CHOOSE_SPORTS,
    PICK_FOODS,
    TODAYS_SESSION,
    WEEKLY_CHECK_IN,
}

/**
 * One step, as the user sees it.
 *
 * @param unlocks what finishing it switches on elsewhere — the domino it knocks over
 */
data class JourneyStep(
    val id: StepId,
    val title: String,
    val detail: String,
    val action: String,
    val unlocks: String,
    val done: Boolean,
)

/**
 * What the app knows about where the user is. Gathered from every feature; nothing here is
 * asked for.
 *
 * @param daysSinceScan null when there has never been one. The scan is also where weight is
 *   entered, so this is the age of the weight too.
 * @param todaysSession the first session planned for today, null on a rest day or with no week
 */
data class JourneyFacts(
    val daysSinceScan: Long?,
    val hasWeek: Boolean,
    val hasFavourites: Boolean,
    val todaysSession: String?,
    val loggedToday: Boolean,
)

/**
 * @param setup the three steps that switch the whole app on, in order
 * @param next the single thing to do now; null when everything for today is done
 */
data class Journey(val setup: List<JourneyStep>, val next: JourneyStep?) {
    val setupDone: Int get() = setup.count { it.done }
    val setupComplete: Boolean get() = setup.all { it.done }
}

/**
 * The order the app is used in, and the one next step.
 *
 * **Setup, like dominoes.** The AI scan comes first and takes the weight with it — one visit
 * gives body fat, lean mass, weight and weak points, everything the rest is sized from. The
 * sports give the week, which gives nutrition its activity; the favourite foods turn the
 * numbers into meals.
 *
 * **Then the daily loop.** Today's session if one is planned and not yet logged, and a scan
 * check-in once a week: the weekly weight is what corrects the calories, and the photo re-ranks
 * the weak points.
 */
object JourneyPlanner {

    const val CHECK_IN_EVERY_DAYS = 7

    fun plan(f: JourneyFacts): Journey {
        val setup = listOf(
            JourneyStep(
                StepId.AI_SCAN, "AI body scan", "Your weight and one photo — body fat, lean mass and weak points.",
                "Start scan", "Your calories and training priorities", done = f.daysSinceScan != null,
            ),
            JourneyStep(
                StepId.CHOOSE_SPORTS, "Choose your sports", "Gym, calisthenics, Pilates, running — one or a mix.",
                "Build my week", "Your weekly programme", done = f.hasWeek,
            ),
            JourneyStep(
                StepId.PICK_FOODS, "Pick favourite foods", "Your week of meals is built from them.",
                "Pick foods", "Your 7-day meal plan", done = f.hasFavourites,
            ),
        )

        val next = setup.firstOrNull { !it.done } ?: when {
            f.todaysSession != null && !f.loggedToday -> JourneyStep(
                StepId.TODAYS_SESSION, f.todaysSession, "Log it as you go — your next session and calories adjust.",
                "Start workout", "Progression and nutrition", done = false,
            )
            (f.daysSinceScan ?: 0) >= CHECK_IN_EVERY_DAYS -> JourneyStep(
                StepId.WEEKLY_CHECK_IN, "Weekly check-in", "${f.daysSinceScan} days since your last scan — weight and photo.",
                "Start check-in", "Updated calories and weak points", done = false,
            )
            else -> null
        }
        return Journey(setup, next)
    }
}
