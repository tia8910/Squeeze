package com.squeeze.core.trend

import kotlin.math.sqrt

/**
 * How precisely the user can reproduce their own measurement.
 *
 * @param withinSessionStdDev pooled standard deviation of repeat measurements taken close
 *   enough together that the body cannot genuinely have changed
 * @param repeatabilityCoefficient the smallest difference between two single measurements
 *   that can be called real at 95% confidence
 * @param replicateCount total repeat measurements contributing to the estimate
 * @param sessionCount how many separate occasions those replicates came from
 */
data class RepeatabilityScore(
    val withinSessionStdDev: Double,
    val repeatabilityCoefficient: Double,
    val replicateCount: Int,
    val sessionCount: Int,
) {
    /**
     * A coarse grade for the UI. The thresholds are in percentage points of body fat and
     * reflect what is achievable with a controlled tape protocol.
     */
    val grade: Grade
        get() = when {
            withinSessionStdDev <= 0.35 -> Grade.EXCELLENT
            withinSessionStdDev <= 0.70 -> Grade.GOOD
            withinSessionStdDev <= 1.20 -> Grade.FAIR
            else -> Grade.POOR
        }

    enum class Grade { EXCELLENT, GOOD, FAIR, POOR }
}

/**
 * Measures the user's own measurement precision, rather than the equation's accuracy.
 *
 * This is the number that decides whether a trend is readable. An equation biased two
 * points high but reproducible to a third of a point tells you far more about whether a
 * cut is working than an unbiased one that scatters by four points, because users track
 * change rather than absolute level.
 *
 * Surfacing it also creates a feedback loop competitors do not offer: a user with a poor
 * score can be coached on protocol — same time of day, same hydration, consistent tape
 * tension — and watch the score improve, which directly improves every later reading.
 */
object Repeatability {

    /** Replicates must fall inside this window to count as the same session. */
    const val DEFAULT_SESSION_WINDOW_DAYS = 1L

    /**
     * Pools within-session variance across sessions.
     *
     * @param observations all measurements; sessions are detected automatically
     * @param sessionWindowDays maximum gap for consecutive measurements to be treated as
     *   replicates of one session
     * @return null when no session has at least two replicates, since precision cannot be
     *   estimated from single measurements
     */
    fun score(
        observations: List<Observation>,
        sessionWindowDays: Long = DEFAULT_SESSION_WINDOW_DAYS,
    ): RepeatabilityScore? {
        if (observations.size < 2) return null

        val sessions = groupIntoSessions(observations, sessionWindowDays)
            .filter { it.size >= 2 }
        if (sessions.isEmpty()) return null

        // Pooled variance: sum of squared deviations over total degrees of freedom. This
        // weights larger sessions more heavily, which is what we want.
        var sumSquaredDeviations = 0.0
        var degreesOfFreedom = 0

        for (session in sessions) {
            val mean = session.map { it.value }.average()
            sumSquaredDeviations += session.sumOf { val d = it.value - mean; d * d }
            degreesOfFreedom += session.size - 1
        }

        if (degreesOfFreedom <= 0) return null
        val stdDev = sqrt(sumSquaredDeviations / degreesOfFreedom)

        return RepeatabilityScore(
            withinSessionStdDev = stdDev,
            // Bland-Altman repeatability coefficient for the difference of two measurements.
            repeatabilityCoefficient = 1.96 * sqrt(2.0) * stdDev,
            replicateCount = sessions.sumOf { it.size },
            sessionCount = sessions.size,
        )
    }

    /** Splits a time-ordered series wherever the gap exceeds the session window. */
    private fun groupIntoSessions(
        observations: List<Observation>,
        windowDays: Long,
    ): List<List<Observation>> {
        val sorted = observations.sortedBy { it.epochDay }
        val sessions = mutableListOf<MutableList<Observation>>()
        var current = mutableListOf(sorted.first())

        for (obs in sorted.drop(1)) {
            if (obs.epochDay - current.last().epochDay <= windowDays) {
                current += obs
            } else {
                sessions += current
                current = mutableListOf(obs)
            }
        }
        sessions += current
        return sessions
    }
}
