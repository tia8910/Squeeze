package com.squeeze.core.coach

/** The two moments of the day the app may speak up. */
enum class ReminderSlot { MORNING, EVENING }

/** The kinds of notification, each one switchable on its own in Settings. */
enum class ReminderKind { WORKOUT, CHECK_IN, WEEK_SUMMARY }

/**
 * One notification, ready to post.
 *
 * @param publicTitle what shows on a locked screen. Never a number or a body part: the phone
 *   may be on a desk, and body measurements are the data this app goes furthest to protect.
 * @param opens the step a tap should open, the same one the dashboard's "next" would
 */
data class Reminder(
    val kind: ReminderKind,
    val title: String,
    val body: String,
    val publicTitle: String,
    val opens: StepId?,
)

/**
 * What the reminder needs to know at the moment it fires. Read fresh each time, so a
 * notification never describes a plan the user has since changed.
 *
 * @param isoDayOfWeek 1 = Monday … 7 = Sunday
 * @param todaysSession the first session planned for today, null on a rest day
 * @param sessionsPlanned sessions in this week's plan, Monday to Sunday
 * @param sessionsLogged of those, the days with anything logged so far this week
 */
data class ReminderFacts(
    val isoDayOfWeek: Int,
    val setupComplete: Boolean,
    val daysSinceScan: Long?,
    val todaysSession: String?,
    val todaysMinutes: Int?,
    val loggedToday: Boolean,
    val sessionsPlanned: Int,
    val sessionsLogged: Int,
    val setsPlanned: Int,
    val setsLogged: Int,
)

/**
 * Which notification, if any, each slot should post.
 *
 * **At most one per slot, and nothing that is not actionable.** A fitness app that pings
 * three times a day gets its notifications turned off in a week, and then cannot reach the
 * user on the one day it matters. So the morning says what today holds, the evening follows
 * up only on something still open, and a quiet day is silent.
 *
 * **The check-in carries the accuracy advice.** A body scan compared with the last one is
 * only as good as the match between the two photographs: the physique read swings by tens of
 * points with light and pose alone. The reminder is the one moment the app can ask for the
 * same spot, light and pose before the photo is taken, rather than explain afterwards why a
 * number jumped.
 */
object ReminderPlanner {

    /** Sunday evening: the week is over, and next week has not started. */
    const val SUMMARY_DAY = 7

    fun plan(slot: ReminderSlot, f: ReminderFacts, enabled: Set<ReminderKind>): Reminder? {
        // Before setup is done the plan has nothing to remind about; the app's own first-run
        // flow is the place for that, not a notification.
        if (!f.setupComplete) return null
        val candidates = when (slot) {
            ReminderSlot.MORNING -> listOfNotNull(workoutToday(f), checkIn(f))
            ReminderSlot.EVENING -> listOfNotNull(
                weekSummary(f).takeIf { f.isoDayOfWeek == SUMMARY_DAY },
                unloggedSession(f),
            )
        }
        return candidates.firstOrNull { it.kind in enabled }
    }

    private fun workoutToday(f: ReminderFacts): Reminder? {
        val session = f.todaysSession ?: return null
        if (f.loggedToday) return null
        val length = f.todaysMinutes?.let { " · about $it min" }.orEmpty()
        return Reminder(
            kind = ReminderKind.WORKOUT,
            title = "Today: $session$length",
            body = "Log it as you go — your next session and your calories adjust to what you did.",
            publicTitle = "Today's session is ready",
            opens = StepId.TODAYS_SESSION,
        )
    }

    private fun unloggedSession(f: ReminderFacts): Reminder? {
        val session = f.todaysSession ?: return null
        if (f.loggedToday) return null
        return Reminder(
            kind = ReminderKind.WORKOUT,
            title = "$session is still open",
            body = "Done it? Log it so the plan knows. Skipped it? Nothing to do — the week " +
                "absorbs one missed session.",
            publicTitle = "A session is still open",
            opens = StepId.TODAYS_SESSION,
        )
    }

    private fun checkIn(f: ReminderFacts): Reminder? {
        val days = f.daysSinceScan ?: return null
        if (days < JourneyPlanner.CHECK_IN_EVERY_DAYS) return null
        return Reminder(
            kind = ReminderKind.CHECK_IN,
            title = "Weekly check-in — $days days since your last scan",
            body = "Same spot, same light, same pose as last time, arms relaxed, phone away " +
                "from your chest. Matching photos are what make the comparison accurate.",
            publicTitle = "Time for your weekly check-in",
            opens = StepId.WEEKLY_CHECK_IN,
        )
    }

    private fun weekSummary(f: ReminderFacts): Reminder? {
        if (f.sessionsPlanned == 0) return null
        val sets = if (f.setsPlanned > 0) " · ${f.setsLogged} of ${f.setsPlanned} sets" else ""
        val verdict = when {
            f.sessionsLogged >= f.sessionsPlanned -> "Every planned session done. Next week builds on it."
            f.sessionsLogged == 0 -> "Nothing logged this week. A fresh week starts tomorrow — one session is enough to restart."
            else -> "Consistency beats perfection. Next week's plan is ready."
        }
        return Reminder(
            kind = ReminderKind.WEEK_SUMMARY,
            title = "Your week: ${f.sessionsLogged} of ${f.sessionsPlanned} sessions$sets",
            body = verdict,
            publicTitle = "Your weekly summary is ready",
            opens = null,
        )
    }
}
