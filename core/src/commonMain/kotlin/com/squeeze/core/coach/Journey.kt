package com.squeeze.core.coach

/** Every step the app can send the user to next. */
enum class StepId {
    WEIGH_IN,
    AI_SCAN,
    CHOOSE_SPORTS,
    PICK_FOODS,
    TODAYS_SESSION,
    WEEKLY_WEIGH_IN,
    RESCAN,
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
 * @param daysSinceWeight / [daysSinceScan] null when there has never been one
 * @param todaysSession the first session planned for today, null on a rest day or with no week
 */
data class JourneyFacts(
    val daysSinceWeight: Long?,
    val daysSinceScan: Long?,
    val hasWeek: Boolean,
    val hasFavourites: Boolean,
    val todaysSession: String?,
    val loggedToday: Boolean,
)

/**
 * @param setup the four steps that switch the whole app on, in order
 * @param next the single thing to do now; null when everything for today is done
 */
data class Journey(val setup: List<JourneyStep>, val next: JourneyStep?) {
    val setupDone: Int get() = setup.count { it.done }
    val setupComplete: Boolean get() = setup.all { it.done }
}

/**
 * The order the app is used in, and the one next step.
 *
 * **Setup, like dominoes.** Each step feeds the next: a weight gives the nutrition plan its
 * size; the AI scan gives body fat (so resting burn is personal) and weak points (so the
 * programme knows what to prioritise); the sports give the week, which gives nutrition its
 * activity; the favourite foods turn the numbers into meals. Asked in any other order, a step
 * would be built on a guess the next step then has to undo.
 *
 * **Then the daily loop.** Today's session if one is planned and not yet logged; a weigh-in
 * once a week, because the weekly trend is what corrects the calories; a rescan every two weeks,
 * because that is about as fast as a body visibly changes.
 */
object JourneyPlanner {

    const val WEIGH_IN_EVERY_DAYS = 7
    const val RESCAN_EVERY_DAYS = 14

    fun plan(f: JourneyFacts): Journey {
        val setup = listOf(
            JourneyStep(
                StepId.WEIGH_IN, "Log your weight", "The one number everything else is sized from.",
                "Log weight", "Your nutrition plan", done = f.daysSinceWeight != null,
            ),
            JourneyStep(
                StepId.AI_SCAN, "AI body scan", "Body fat, lean mass and your strong and weak muscle groups.",
                "Start scan", "Personal calories and your training priorities", done = f.daysSinceScan != null,
            ),
            JourneyStep(
                StepId.CHOOSE_SPORTS, "Choose your sports", "Gym, calisthenics, Pilates, running — one or a mix.",
                "Build my week", "Your weekly programme and its calorie burn", done = f.hasWeek,
            ),
            JourneyStep(
                StepId.PICK_FOODS, "Pick your favourite foods", "Your week of meals is built from them.",
                "Pick foods", "A 7-day meal plan that hits your targets", done = f.hasFavourites,
            ),
        )

        val next = setup.firstOrNull { !it.done } ?: when {
            f.todaysSession != null && !f.loggedToday -> JourneyStep(
                StepId.TODAYS_SESSION, "Today: ${f.todaysSession}", "Log it as you go — your next session and your calories adjust from it.",
                "Start & log", "Progression, weekly volume and nutrition", done = false,
            )
            (f.daysSinceWeight ?: 0) >= WEIGH_IN_EVERY_DAYS -> JourneyStep(
                StepId.WEEKLY_WEIGH_IN, "Weekly weigh-in", "It's been ${f.daysSinceWeight} days. The weekly trend is what corrects your calories.",
                "Log weight", "The nutrition check-in", done = false,
            )
            (f.daysSinceScan ?: 0) >= RESCAN_EVERY_DAYS -> JourneyStep(
                StepId.RESCAN, "Rescan with the AI", "It's been ${f.daysSinceScan} days — see what moved and re-rank your weak points.",
                "Rescan", "Updated body fat, weak points and plan", done = false,
            )
            else -> null
        }
        return Journey(setup, next)
    }
}
