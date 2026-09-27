package com.squeeze.core.scan

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * One saved physique read: the model's raw per-group scores from one photograph.
 *
 * @param noise how much less to trust a group in this photograph than usual, as a multiple
 *   of the ordinary scan-to-scan spread. 1 is an ordinary read; a chest with a phone held in
 *   front of it is worth less and says so here rather than being thrown away.
 */
data class PhysiqueObservation(
    val epochDay: Long,
    val scores: Map<MuscleGroup, Double>,
    val hidden: Set<MuscleGroup> = emptySet(),
    val noise: Map<MuscleGroup, Double> = emptyMap(),
)

/**
 * One group's best estimate after every read so far.
 *
 * @param score the fused estimate, 0 to 1
 * @param sd its standard deviation — how far the true impression could plausibly be
 * @param reads how many photographs went into it
 * @param latest the newest photograph's own reading of the group, unfused
 * @param change the fused estimate's move since the read before the newest, or null when
 *   there was none. Only worth printing when [confirmed].
 * @param since the day of that earlier read
 * @param confirmed true when the newest photograph disagreed with everything before it by
 *   more than a single scan's noise explains — a real change, not a different photograph
 */
data class FusedScore(
    val score: Double,
    val sd: Double,
    val reads: Int,
    val latest: Double,
    val change: Double?,
    val since: Long?,
    val confirmed: Boolean,
)

/**
 * The physique read from every photograph, not only the last one.
 *
 * **Why.** A single photograph's reading of a muscle group swings far more than the muscle
 * does. Measured on one real photograph with nothing about the body changing: jittering the
 * pose landmarks by the amount the pose model itself jitters moved the chest 23 → 48, dimming
 * the light moved the shoulders 27 → 16, and mirroring the photograph moved the abs 69 → 84.
 * Between two real scans four days apart at the same body weight the arms read 56 and then
 * 15. Muscle does not lose forty points in four days; the photograph changed.
 *
 * So each group is a one-dimensional Kalman filter over the user's own history. Muscle
 * changes slowly ([PROCESS_SD_PER_DAY]), a photograph is noisy ([MEASUREMENT_SD]), and the
 * estimate moves toward a new reading only as far as the two allow. The first read stands
 * alone; the second moves the estimate about half way; by the fifth a single bad photograph
 * moves it a little. A real change still gets through, because it keeps showing up.
 *
 * **What it does not do.** It cannot make one photograph more accurate. It stops one
 * photograph from overturning the last one, which is where most of the apparent inaccuracy
 * was.
 */
object PhysiqueFusion {

    /**
     * How much the impression of a group can genuinely drift per day, as a standard deviation.
     *
     * 0.01/day is about 5 points in a month — the upper end of what a beginner gains in
     * visible size, and far more than a trained lifter does. Set generously so a real change
     * is followed within a few scans rather than argued away.
     */
    const val PROCESS_SD_PER_DAY = 0.01

    /**
     * One photograph's spread around the truth, for a group read in ordinary conditions.
     *
     * From the measurements above: the same body across two sessions differed by 14–41 points
     * per group (a spread of about 0.19 for one read), and multi-view averaging brings the
     * within-photograph part of that down by about half. 0.15 is what remains.
     */
    const val MEASUREMENT_SD = 0.15

    /** A change is confirmed when the new read sits this many standard deviations out. */
    const val CONFIRM_Z = 2.0

    /** Reads further apart than this share nothing: a year-old photograph is a different body. */
    private const val MAX_GAP_DAYS = 365L

    /**
     * @param history every read, in any order; the newest is the one being reported
     * @return per group, the fused estimate as of the newest read. A group the newest read did
     *   not show is left out — the report is about what this photograph could see
     */
    fun fuse(history: List<PhysiqueObservation>): Map<MuscleGroup, FusedScore> {
        val ordered = history.sortedBy { it.epochDay }
        val newest = ordered.lastOrNull() ?: return emptyMap()
        val out = linkedMapOf<MuscleGroup, FusedScore>()

        for ((group, latestRaw) in newest.scores) {
            if (group in newest.hidden || !latestRaw.isFinite()) continue
            var x = Double.NaN
            var p = 0.0
            var lastDay = 0L
            var reads = 0
            var priorX = Double.NaN
            var priorP = 0.0
            var priorDay: Long? = null
            var r = 0.0

            for (obs in ordered) {
                if (group in obs.hidden) continue
                val z = obs.scores[group]?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: continue
                val sd = MEASUREMENT_SD * (obs.noise[group] ?: 1.0).coerceAtLeast(1.0)
                r = sd * sd
                if (x.isNaN() || obs.epochDay - lastDay > MAX_GAP_DAYS) {
                    priorX = Double.NaN
                    priorDay = null
                    x = z
                    p = r
                    reads = 1
                } else {
                    val days = (obs.epochDay - lastDay).coerceAtLeast(0)
                    p += PROCESS_SD_PER_DAY * PROCESS_SD_PER_DAY * days
                    priorX = x
                    priorP = p
                    priorDay = lastDay
                    val gain = p / (p + r)
                    x += gain * (z - x)
                    p *= 1.0 - gain
                    reads++
                }
                lastDay = obs.epochDay
            }
            if (x.isNaN()) continue

            val latest = latestRaw.coerceIn(0.0, 1.0)
            val confirmed = !priorX.isNaN() &&
                abs(latest - priorX) / sqrt(priorP + r) >= CONFIRM_Z
            out[group] = FusedScore(
                score = x.coerceIn(0.0, 1.0),
                sd = sqrt(p),
                reads = reads,
                latest = latest,
                change = if (priorX.isNaN()) null else x - priorX,
                since = priorDay,
                confirmed = confirmed,
            )
        }
        return out
    }
}
