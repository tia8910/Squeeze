package com.squeeze.core.coach

import com.squeeze.core.model.Goal

/** When the user usually trains — it moves the pre/post-workout meal and the advice. */
enum class TrainingTime(val label: String, val window: String) {
    EARLY_MORNING("Early morning", "5–8 am"),
    MORNING("Morning", "8 am–12 pm"),
    AFTERNOON("Afternoon", "12–5 pm"),
    EVENING("Evening", "5–9 pm"),
    NIGHT("Night", "after 9 pm"),
}

/**
 * What the coach knows today, for picking a tip.
 *
 * @param weakPoints the AI scan's focus groups, by name
 * @param microGaps micronutrients the meal plan runs short of, by name
 */
data class TipContext(
    val goal: Goal,
    val trainingTime: TrainingTime?,
    val trainingToday: Boolean,
    val weakPoints: List<String> = emptyList(),
    val microGaps: List<String> = emptyList(),
    val proteinG: Int? = null,
    val epochDay: Long,
)

/**
 * One coaching tip for today, chosen for the user's goal and what today holds.
 *
 * Today-specific tips come first — what to eat before today's session given when it is, what a
 * night session means for caffeine — then a rotating tip for the goal, so the dashboard says
 * something new each day without saying something irrelevant.
 */
object CoachTips {

    fun today(c: TipContext): List<String> {
        val specific = buildList {
            if (c.trainingToday) {
                when (c.trainingTime) {
                    TrainingTime.EARLY_MORNING ->
                        add("Training early: a banana or a small yogurt 30 minutes before is enough, save the big breakfast for after.")
                    TrainingTime.MORNING, TrainingTime.AFTERNOON ->
                        add("Eat your carb-and-protein meal 1–2 hours before training, and protein within 2 hours after.")
                    TrainingTime.EVENING ->
                        add("Evening session: a carb-and-protein snack around 4 pm keeps you fuelled; dinner after is your recovery meal.")
                    TrainingTime.NIGHT ->
                        add("Training late: skip caffeine after about 4 pm so you can still sleep, sleep is when the session pays off.")
                    null -> add("Set your usual training time in Train and your meals will be timed around it.")
                }
            } else {
                add("Rest day: keep protein the same, carbs a little lower, and walk, recovery is part of the plan.")
            }
            c.weakPoints.firstOrNull()?.let {
                if (c.trainingToday) add("Train your ${it.lowercase()} first today, while you're fresh, it's your focus area.")
            }
            c.microGaps.firstOrNull()?.let { add("Your meals run short on ${it.lowercase()} this week, see Fuel → Details for the foods that fix it.") }
        }
        val pool = goalTips(c)
        val rotating = pool[(c.epochDay % pool.size).toInt().let { if (it < 0) it + pool.size else it }]
        return (specific.take(2) + rotating).distinct()
    }

    private fun goalTips(c: TipContext): List<String> {
        val protein = c.proteinG?.let { "$it g" } ?: "your protein target"
        return when (c.goal) {
            Goal.CUT -> listOf(
                "Spread $protein over 3–4 meals, it protects muscle in a deficit and keeps hunger down.",
                "Keep lifting heavy while you cut. The weight on the bar is the signal to keep your muscle.",
                "Aim for 8–10k steps a day: the easiest calories to burn without eating into recovery.",
                "Judge the cut by the weekly trend, not the daily scale, water swings 1–2 kg day to day.",
                "Fill half your plate with vegetables: volume for the stomach, micronutrients for the body.",
            )
            Goal.HYPERTROPHY -> listOf(
                "Muscle grows from progressive overload: beat last time by a rep or the smallest plate.",
                "Sleep 7–9 hours. It's the cheapest muscle-building supplement there is.",
                "A small surplus builds muscle; a big one mostly builds fat. Trust the plan's numbers.",
                "Get $protein across 4 meals, each meal is a chance to start building.",
                "Take most sets within 1–2 reps of failure. Easy sets don't send the growth signal.",
            )
            Goal.STRENGTH -> listOf(
                "Rest 3–5 minutes between heavy sets, strength recovers slower than breath.",
                "Practise the big lifts often with clean reps; technique is strength you already have.",
                "Carbs before training power heavy sets. Don't train strength on an empty tank.",
                "Log every top set. A stalled lift is fixed by changing something, and the log shows what.",
            )
            Goal.RECOMP -> listOf(
                "Recomposition is slow and the scale may not move, trust the scan and the tape.",
                "Hit $protein every day; it's what lets you build muscle at maintenance calories.",
                "Train hard and progressively, recomp works when the training gives the body a reason.",
                "Keep calories steady day to day; recomp rewards consistency over intensity.",
            )
            Goal.MAKE_WEIGHT -> listOf(
                "Lose weight gradually toward the date, rapid water cuts cost strength and are risky.",
                "Keep $protein high and training heavy so the weight that comes off is not muscle.",
                "Weigh at the same time each morning; the trend is what tells you you're on track.",
            )
        }
    }
}
