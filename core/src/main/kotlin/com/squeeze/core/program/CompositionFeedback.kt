package com.squeeze.core.program

import com.squeeze.core.model.Goal
import com.squeeze.core.trend.TrendPoint

/**
 * Volume and effort modifiers applied to the next block.
 *
 * @param volumeMultiplier scales every muscle group's prescribed sets
 * @param rationale plain-language explanation shown to the user. An adjustment the user
 *   cannot understand reads as the app being erratic, so this is never null when the
 *   multiplier differs from 1.
 */
data class VolumeAdjustment(
    val volumeMultiplier: Double,
    val rationale: String?,
) {
    companion object {
        val NONE = VolumeAdjustment(1.0, null)
    }
}

/**
 * Turns the body-composition trend into a training decision.
 *
 * This is the loop no competitor closes. Training apps program from bar weight alone, so
 * they cannot tell a lifter whose bench is stalling that the real problem is a four-week
 * deficit. Body-composition apps measure but never prescribe. Reading both together is
 * what makes the measurement worth taking.
 *
 * The rules below are intentionally conservative and only fire on trends the filter has
 * established as statistically significant, because acting on measurement noise produces
 * exactly the thrashing that makes users stop trusting an app.
 */
object CompositionFeedback {

    /** Below this weekly lean-mass loss a cut is proceeding acceptably, in kg/week. */
    private const val ACCEPTABLE_LEAN_LOSS_PER_WEEK = 0.1

    /** A bulk gaining fat faster than this is outrunning its useful surplus, in %/week. */
    private const val EXCESSIVE_FAT_GAIN_PER_WEEK = 0.25

    /** Fat loss faster than this risks lean mass regardless of what the scale says, in %/week. */
    private const val AGGRESSIVE_FAT_LOSS_PER_WEEK = 0.5

    /**
     * @param bodyFatTrend filtered body-fat trend, most recent point last
     * @param leanMassTrend filtered fat-free mass trend in kg, most recent point last
     * @param goal what the user is currently training for
     */
    fun evaluate(
        bodyFatTrend: List<TrendPoint>,
        leanMassTrend: List<TrendPoint>,
        goal: Goal,
    ): VolumeAdjustment {
        val fat = bodyFatTrend.lastOrNull() ?: return VolumeAdjustment.NONE
        val lean = leanMassTrend.lastOrNull()

        // Until the trend separates from noise there is nothing to act on. Saying so is
        // more useful than inventing a correction.
        if (!fat.isChangeSignificant) return VolumeAdjustment.NONE

        return when (goal) {
            Goal.CUT, Goal.MAKE_WEIGHT -> evaluateCut(fat, lean)
            Goal.HYPERTROPHY -> evaluateBulk(fat, lean)
            Goal.RECOMP -> evaluateRecomp(fat, lean)
            Goal.STRENGTH -> VolumeAdjustment.NONE
        }
    }

    private fun evaluateCut(fat: TrendPoint, lean: TrendPoint?): VolumeAdjustment {
        // Losing lean mass on a cut is the failure mode that matters. Training volume is not
        // the cause, but it is the lever this app controls: cutting volume back to
        // maintenance reduces the recovery demand a deficit cannot meet.
        if (lean != null && lean.isChangeSignificant && lean.weeklyChange < -ACCEPTABLE_LEAN_LOSS_PER_WEEK) {
            return VolumeAdjustment(
                volumeMultiplier = 0.8,
                rationale = "You are losing lean mass at %.2f kg/week, faster than a cut should. Volume is reduced to protect muscle. Consider a smaller deficit and more protein."
                    .format(-lean.weeklyChange),
            )
        }

        if (fat.weeklyChange < -AGGRESSIVE_FAT_LOSS_PER_WEEK) {
            return VolumeAdjustment(
                volumeMultiplier = 0.9,
                rationale = "Fat loss is running at %.2f%%/week, which is aggressive. Volume is trimmed so recovery keeps pace."
                    .format(-fat.weeklyChange),
            )
        }

        // Stalled cut with lean mass intact: recovery capacity is clearly there, so the
        // deficit is the problem, not the training. Volume holds.
        if (fat.weeklyChange > -0.05) {
            return VolumeAdjustment(
                volumeMultiplier = 1.0,
                rationale = "Fat loss has stalled while lean mass is holding. Training is working; the deficit needs revisiting before volume changes.",
            )
        }

        return VolumeAdjustment.NONE
    }

    private fun evaluateBulk(fat: TrendPoint, lean: TrendPoint?): VolumeAdjustment {
        if (fat.weeklyChange > EXCESSIVE_FAT_GAIN_PER_WEEK) {
            val leanNote = if (lean != null && lean.isChangeSignificant && lean.weeklyChange > 0.05) {
                " Lean mass is still climbing, so the surplus is working, just too large."
            } else {
                " Lean mass is not climbing with it, so the surplus is mostly becoming fat."
            }
            return VolumeAdjustment(
                volumeMultiplier = 1.0,
                rationale = "Body fat is rising %.2f%%/week.$leanNote Reduce the surplus rather than the training."
                    .format(fat.weeklyChange),
            )
        }

        // Lean mass flat on a surplus with body fat stable means the stimulus is the limit,
        // not the food. This is the one case where more volume is the right answer.
        if (lean != null && !lean.isChangeSignificant && kotlin.math.abs(fat.weeklyChange) < 0.1) {
            return VolumeAdjustment(
                volumeMultiplier = 1.15,
                rationale = "Lean mass has been flat while body fat held steady. Volume is increased to add stimulus.",
            )
        }

        return VolumeAdjustment.NONE
    }

    private fun evaluateRecomp(fat: TrendPoint, lean: TrendPoint?): VolumeAdjustment {
        // Recomp is the case where bodyweight alone tells the user nothing, which is exactly
        // why composition tracking earns its place.
        if (lean != null && lean.isChangeSignificant && lean.weeklyChange > 0.0 && fat.weeklyChange < 0.0) {
            return VolumeAdjustment(
                volumeMultiplier = 1.0,
                rationale = "Lean mass up and fat down at stable bodyweight. Recomposition is working; nothing needs changing.",
            )
        }

        if (fat.weeklyChange > 0.1) {
            return VolumeAdjustment(
                volumeMultiplier = 1.1,
                rationale = "Body fat is drifting up at stable weight. Volume is increased to shift the balance back toward lean mass.",
            )
        }

        return VolumeAdjustment.NONE
    }
}
