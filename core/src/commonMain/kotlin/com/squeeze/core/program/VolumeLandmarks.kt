package com.squeeze.core.program

import com.squeeze.core.model.TrainingAge
import kotlin.math.roundToInt

/** Trainable muscle groups, at the granularity a weekly set count is meaningful for. */
enum class MuscleGroup {
    CHEST, BACK, QUADS, HAMSTRINGS, GLUTES, SHOULDERS, BICEPS, TRICEPS, CALVES, ABS
}

/**
 * Weekly set landmarks for one muscle group, in hard sets taken near failure.
 *
 * @param maintenance the least volume that still holds current muscle. What a cut or a
 *   deload drops to, and what a make-weight block defends.
 * @param minimumEffective the least volume that reliably produces growth
 * @param maximumAdaptive roughly where returns start flattening for most lifters
 * @param maximumRecoverable beyond this, accumulated fatigue outpaces recovery
 *
 * These are population starting points, not laws. [ProgramGenerator] moves a user's
 * working volume between them based on logged performance, and the per-user drift is
 * what makes the programme feel personal over a few blocks.
 */
data class Landmarks(
    val maintenance: Int,
    val minimumEffective: Int,
    val maximumAdaptive: Int,
    val maximumRecoverable: Int,
) {
    init {
        require(maintenance <= minimumEffective) { "maintenance must not exceed MEV" }
        require(minimumEffective <= maximumAdaptive) { "MEV must not exceed MAV" }
        require(maximumAdaptive <= maximumRecoverable) { "MAV must not exceed MRV" }
    }
}

object VolumeLandmarks {

    /**
     * Baseline weekly set landmarks.
     *
     * Larger muscle groups that receive indirect work from compound lifts carry lower
     * direct-set ceilings than small groups trained in isolation.
     */
    private val BASE: Map<MuscleGroup, Landmarks> = mapOf(
        MuscleGroup.CHEST to Landmarks(6, 10, 18, 22),
        MuscleGroup.BACK to Landmarks(8, 12, 20, 25),
        MuscleGroup.QUADS to Landmarks(6, 10, 18, 22),
        MuscleGroup.HAMSTRINGS to Landmarks(4, 8, 14, 18),
        MuscleGroup.GLUTES to Landmarks(4, 8, 14, 18),
        MuscleGroup.SHOULDERS to Landmarks(6, 10, 18, 24),
        MuscleGroup.BICEPS to Landmarks(4, 8, 16, 20),
        MuscleGroup.TRICEPS to Landmarks(4, 8, 16, 20),
        MuscleGroup.CALVES to Landmarks(6, 8, 16, 20),
        MuscleGroup.ABS to Landmarks(0, 6, 16, 20),
    )

    /**
     * Landmarks scaled for training experience.
     *
     * Novices grow on far less volume and recover from far less, so handing them an
     * intermediate's set count buries them in fatigue with no extra adaptation. Advanced
     * lifters need more volume to keep progressing but their recoverable ceiling rises
     * more slowly than their requirement, which is why their blocks get shorter.
     */
    fun forTrainingAge(trainingAge: TrainingAge): Map<MuscleGroup, Landmarks> {
        val scale = when (trainingAge) {
            TrainingAge.NOVICE -> 0.7
            TrainingAge.INTERMEDIATE -> 1.0
            TrainingAge.ADVANCED -> 1.2
        }
        return BASE.mapValues { (_, l) ->
            Landmarks(
                maintenance = scaleSets(l.maintenance, scale),
                minimumEffective = scaleSets(l.minimumEffective, scale),
                maximumAdaptive = scaleSets(l.maximumAdaptive, scale),
                maximumRecoverable = scaleSets(l.maximumRecoverable, scale),
            )
        }
    }

    private fun scaleSets(sets: Int, scale: Double): Int =
        if (sets == 0) 0 else (sets * scale).roundToInt().coerceAtLeast(1)
}
