package com.squeeze.core.coach

/**
 * One badge: earned on a day, or not yet, with how far along it is.
 *
 * @param earnedOn the epoch day it was earned, or null while locked
 * @param progress how much of [target] is done, for the "next achievement" bar
 */
data class Achievement(
    val id: String,
    val title: String,
    val detail: String,
    val emoji: String,
    val earnedOn: Long?,
    val progress: Int,
    val target: Int,
) {
    val earned: Boolean get() = earnedOn != null
}

/**
 * Everything the badges are worked out from: dates and counts the app already stores.
 *
 * @param scanDays the day of every saved scan or measurement
 * @param sideScanDays the days of scans that included a side photo
 * @param workoutDays the day of every saved workout or sport session, one entry per session
 * @param recordDays the days a lift set a new best
 * @param mealDays the days with at least one meal ticked as eaten
 * @param waistByDay the waist on each day it was measured, oldest first
 * @param goalReachedOn the first day the goal body fat was reached, if it has been
 */
data class AchievementInputs(
    val scanDays: List<Long> = emptyList(),
    val sideScanDays: List<Long> = emptyList(),
    val workoutDays: List<Long> = emptyList(),
    val recordDays: List<Long> = emptyList(),
    val mealDays: List<Long> = emptyList(),
    val waistByDay: List<Pair<Long, Double>> = emptyList(),
    val goalReachedOn: Long? = null,
)

/**
 * Private badges for showing up, worked out fresh from the record every time.
 *
 * Nothing is stored about them, so they can never disagree with the history they describe,
 * and deleting a scan takes away the badge it earned. Every badge rewards consistency or a
 * measured result, never a body shape.
 */
object Achievements {

    fun all(input: AchievementInputs): List<Achievement> {
        val scans = input.scanDays.sorted()
        val workouts = input.workoutDays.sorted()
        val mealDays = input.mealDays.distinct().sorted()

        fun nth(days: List<Long>, n: Int) = days.getOrNull(n - 1)

        val waistDrop = waistDropDays(input.waistByDay)
        val weeks = weekStreakDays(workouts)

        return listOf(
            count("first_scan", "First scan", "Your starting point is set.", "📸", scans, 1),
            count("scans_5", "5 scans", "A real trend needs repeat readings.", "📈", scans, 5),
            count("scans_10", "10 scans", "Ten check ins on the record.", "🏅", scans, 10),
            count("side_photo", "Side photo pro", "Added a side photo for the sharpest read.", "⭐", input.sideScanDays.sorted(), 1),
            count("first_workout", "First workout", "The first session is the hardest.", "💪", workouts, 1),
            count("workouts_10", "10 workouts", "Ten sessions done.", "🏋️", workouts, 10),
            count("workouts_25", "25 workouts", "Twenty five sessions done.", "🔥", workouts, 25),
            count("workouts_50", "50 workouts", "Fifty sessions. This is a habit now.", "🚀", workouts, 50),
            count("first_record", "New personal record", "Beat your best on a lift.", "🥇", input.recordDays.sorted(), 1),
            streak("streak_4", "4 week streak", "Trained every week for a month.", "🗓️", weeks, 4),
            streak("streak_12", "12 week streak", "Trained every week for three months.", "👑", weeks, 12),
            Achievement("meals_7", "A week of meals", "Logged meals on seven days.", "🥗", nth(mealDays, 7), mealDays.size.coerceAtMost(7), 7),
            Achievement("waist_2", "Waist down 2 cm", "Measured, not guessed.", "📉", waistDrop.first, waistDrop.third.coerceIn(0, 2), 2),
            Achievement("waist_5", "Waist down 5 cm", "A clear change on the tape.", "🎯", waistDrop.second, waistDrop.third.coerceIn(0, 5), 5),
            Achievement("goal", "Goal reached", "You hit your target body fat.", "🏆", input.goalReachedOn, if (input.goalReachedOn != null) 1 else 0, 1),
        )
    }

    /** The newest earned badge, for the banner at the top. */
    fun latest(list: List<Achievement>): Achievement? = list.filter { it.earned }.maxByOrNull { it.earnedOn!! }

    /** The locked badge closest to done, for the "next" progress bar. */
    fun next(list: List<Achievement>): Achievement? =
        list.filter { !it.earned && it.progress > 0 }.maxByOrNull { it.progress.toDouble() / it.target }
            ?: list.firstOrNull { !it.earned }

    private fun count(id: String, title: String, detail: String, emoji: String, days: List<Long>, n: Int) =
        Achievement(id, title, detail, emoji, days.getOrNull(n - 1), days.size.coerceAtMost(n), n)

    private fun streak(id: String, title: String, detail: String, emoji: String, weeks: Pair<List<Long>, Int>, n: Int) =
        Achievement(id, title, detail, emoji, weeks.first.getOrNull(n - 1), weeks.second.coerceAtMost(n), n)

    /**
     * Consecutive training weeks, counted in 7-day blocks from the epoch.
     *
     * @return the day each streak length was first reached (index 0 for 1 week), and the
     *   current run of consecutive weeks including this one or the last
     */
    internal fun weekStreakDays(workoutDays: List<Long>): Pair<List<Long>, Int> {
        if (workoutDays.isEmpty()) return emptyList<Long>() to 0
        val firstDayOfWeek = workoutDays.groupBy { it.floorDiv(7L) }.mapValues { (_, d) -> d.min() }
        val weeks = firstDayOfWeek.keys.sorted()
        val reachedOn = mutableListOf<Long>()
        var run = 0
        var best = 0
        var previous: Long? = null
        for (w in weeks) {
            run = if (previous != null && w == previous + 1) run + 1 else 1
            if (run > best) {
                best = run
                reachedOn += firstDayOfWeek.getValue(w)
            }
            previous = w
        }
        return reachedOn to run
    }

    /** Days the waist first measured 2 cm and 5 cm under the first reading, and the cm lost now. */
    private fun waistDropDays(waist: List<Pair<Long, Double>>): Triple<Long?, Long?, Int> {
        val sorted = waist.sortedBy { it.first }
        val start = sorted.firstOrNull()?.second ?: return Triple(null, null, 0)
        val two = sorted.firstOrNull { start - it.second >= 2.0 }?.first
        val five = sorted.firstOrNull { start - it.second >= 5.0 }?.first
        val now = (start - sorted.last().second).toInt()
        return Triple(two, five, now)
    }
}
