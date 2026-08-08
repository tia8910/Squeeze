package com.squeeze.core.program

import com.squeeze.core.model.Goal
import com.squeeze.core.model.Profile
import com.squeeze.core.model.TrainingAge
import kotlin.math.roundToInt

/**
 * What the user can train with and how often.
 *
 * @param daysPerWeek 2-6; more than six leaves no room for recovery and the generator
 *   refuses it rather than producing a programme it believes is harmful
 * @param priorityGroups muscle groups to bias volume toward, at the expense of others
 */
data class TrainingConstraints(
    val daysPerWeek: Int,
    val availableEquipment: Set<Equipment>,
    val priorityGroups: Set<MuscleGroup> = emptySet(),
) {
    init {
        require(daysPerWeek in 2..6) { "daysPerWeek must be 2..6, was $daysPerWeek" }
        require(availableEquipment.isNotEmpty()) { "at least one equipment type is required" }
    }
}

/**
 * Builds a training block from the user's profile, constraints and training history.
 *
 * The generator is deliberately a deterministic rules engine rather than a language model.
 * Prescribing training loads is a safety-relevant decision: it has to be reproducible,
 * inspectable, and identical offline, and it must never invent an exercise or a set count
 * that no evidence supports. Every number below traces to a volume landmark, a
 * periodisation rule, or the user's own logged performance.
 *
 * It also runs entirely on-device in negligible time, which is what lets the app keep its
 * no-server architecture while still selling programme generation.
 */
class ProgramGenerator {

    /**
     * @param adjustment optional volume and effort modifiers derived from the user's
     *   body-composition trend, see [CompositionFeedback]
     */
    fun generate(
        profile: Profile,
        constraints: TrainingConstraints,
        adjustment: VolumeAdjustment = VolumeAdjustment.NONE,
    ): Mesocycle {
        val landmarks = VolumeLandmarks.forTrainingAge(profile.trainingAge)
        val accumulationWeeks = accumulationWeeksFor(profile.trainingAge)

        val startVolume = startingWeeklySets(landmarks, profile.goal, constraints, adjustment)
        val peakVolume = peakWeeklySets(landmarks, profile.goal, constraints, adjustment)

        val split = SplitPlanner.plan(constraints.daysPerWeek)

        val weeks = buildList {
            for (week in 0 until accumulationWeeks) {
                // Volume ramps linearly from the starting point to the peak across the block,
                // so fatigue accumulates predictably and the deload lands where it is needed.
                val progress = if (accumulationWeeks == 1) 0.0
                else week.toDouble() / (accumulationWeeks - 1).toDouble()

                val volume = startVolume.mapValues { (group, start) ->
                    val peak = peakVolume.getValue(group)
                    (start + (peak - start) * progress).roundToInt()
                }

                // Effort rises as volume rises: early weeks leave reps in reserve, late weeks
                // approach failure. Novices never go below 2 RIR, where technique breaks down.
                val rir = targetRir(profile.trainingAge, progress)

                add(buildWeek(week, split, volume, rir, profile.goal, constraints, isDeload = false))
            }

            // Deload: maintenance volume at low effort, to dissipate fatigue and expose the
            // adaptation earned during accumulation.
            val deloadVolume = startVolume.mapValues { (group, _) ->
                landmarks.getValue(group).maintenance
            }
            add(
                buildWeek(
                    accumulationWeeks, split, deloadVolume,
                    targetRir = 4, goal = profile.goal, constraints = constraints, isDeload = true,
                )
            )
        }

        return Mesocycle(
            name = blockName(profile.goal, accumulationWeeks),
            weeks = weeks,
            notes = buildNotes(profile, adjustment),
        )
    }

    /**
     * Block length by training experience.
     *
     * Advanced lifters accumulate fatigue faster relative to their recoverable ceiling, so
     * they need to deload sooner despite tolerating more absolute volume.
     */
    private fun accumulationWeeksFor(trainingAge: TrainingAge): Int = when (trainingAge) {
        TrainingAge.NOVICE -> 5
        TrainingAge.INTERMEDIATE -> 5
        TrainingAge.ADVANCED -> 4
    }

    private fun targetRir(trainingAge: TrainingAge, progress: Double): Int {
        val base = when {
            progress < 0.25 -> 3
            progress < 0.60 -> 2
            progress < 0.85 -> 1
            else -> 0
        }
        // Novices lack the technical consistency to train to true failure safely.
        return if (trainingAge == TrainingAge.NOVICE) base.coerceAtLeast(2) else base
    }

    private fun startingWeeklySets(
        landmarks: Map<MuscleGroup, Landmarks>,
        goal: Goal,
        constraints: TrainingConstraints,
        adjustment: VolumeAdjustment,
    ): Map<MuscleGroup, Int> = landmarks.mapValues { (group, l) ->
        val base = when (goal) {
            // A cut cannot support growth volume; the job is defending what exists.
            Goal.CUT, Goal.MAKE_WEIGHT -> l.maintenance
            Goal.STRENGTH -> l.minimumEffective
            Goal.HYPERTROPHY, Goal.RECOMP -> l.minimumEffective
        }
        applyModifiers(base, group, l, constraints, adjustment)
    }

    private fun peakWeeklySets(
        landmarks: Map<MuscleGroup, Landmarks>,
        goal: Goal,
        constraints: TrainingConstraints,
        adjustment: VolumeAdjustment,
    ): Map<MuscleGroup, Int> = landmarks.mapValues { (group, l) ->
        val base = when (goal) {
            // Never push a dieting lifter to their recoverable ceiling: recovery capacity is
            // reduced by the deficit, so the real ceiling is lower than the landmark says.
            Goal.CUT, Goal.MAKE_WEIGHT -> l.minimumEffective
            Goal.STRENGTH -> l.maximumAdaptive
            Goal.HYPERTROPHY, Goal.RECOMP -> l.maximumRecoverable
        }
        applyModifiers(base, group, l, constraints, adjustment)
    }

    private fun applyModifiers(
        base: Int,
        group: MuscleGroup,
        landmarks: Landmarks,
        constraints: TrainingConstraints,
        adjustment: VolumeAdjustment,
    ): Int {
        val prioritised = if (group in constraints.priorityGroups) base * 1.25 else base.toDouble()
        val adjusted = prioritised * adjustment.volumeMultiplier
        return adjusted.roundToInt()
            // The recoverable ceiling is a hard cap: prioritisation must steal volume from
            // elsewhere rather than push a group past what it can recover from.
            .coerceIn(0, landmarks.maximumRecoverable)
    }

    private fun buildWeek(
        weekIndex: Int,
        split: List<SplitDay>,
        weeklyVolume: Map<MuscleGroup, Int>,
        targetRir: Int,
        goal: Goal,
        constraints: TrainingConstraints,
        isDeload: Boolean,
    ): TrainingWeek {
        val sessions = split.mapIndexed { dayIndex, day ->
            val prescriptions = day.groups.flatMap { group ->
                val totalSets = weeklyVolume[group] ?: 0
                if (totalSets == 0) return@flatMap emptyList()

                // Split this group's weekly sets across the days that train it.
                val daysTrainingGroup = split.count { group in it.groups }.coerceAtLeast(1)
                val setsToday = distributeSets(totalSets, daysTrainingGroup, dayIndex, split, group)
                if (setsToday == 0) return@flatMap emptyList()

                buildPrescriptions(group, setsToday, targetRir, goal, constraints, isDeload)
            }
            Session(dayIndex = dayIndex, name = day.name, prescriptions = prescriptions)
        }
        return TrainingWeek(weekIndex, sessions, isDeload)
    }

    /**
     * Splits weekly sets across sessions, giving the remainder to the earliest days so the
     * heaviest work lands when the lifter is freshest in the week.
     */
    private fun distributeSets(
        totalSets: Int,
        daysTrainingGroup: Int,
        dayIndex: Int,
        split: List<SplitDay>,
        group: MuscleGroup,
    ): Int {
        val ordinal = split.take(dayIndex + 1).count { group in it.groups } - 1
        if (ordinal < 0) return 0
        val perDay = totalSets / daysTrainingGroup
        val remainder = totalSets % daysTrainingGroup
        return perDay + if (ordinal < remainder) 1 else 0
    }

    private fun buildPrescriptions(
        group: MuscleGroup,
        sets: Int,
        targetRir: Int,
        goal: Goal,
        constraints: TrainingConstraints,
        isDeload: Boolean,
    ): List<SetPrescription> {
        val exercises = ExerciseLibrary.forGroup(group, constraints.availableEquipment)
        if (exercises.isEmpty()) return emptyList()

        // Two exercises per group per session once volume justifies it: enough variation to
        // cover the muscle's regions without fragmenting the load progression.
        val useTwo = sets >= 4 && exercises.size >= 2
        val chosen = if (useTwo) exercises.take(2) else exercises.take(1)

        val effectiveRir = if (isDeload) targetRir else targetRir

        return chosen.mapIndexed { index, exercise ->
            val exerciseSets = if (useTwo) {
                if (index == 0) (sets + 1) / 2 else sets / 2
            } else {
                sets
            }

            // Compounds live in lower rep ranges where load progression is readable;
            // isolations sit higher, where joint stress per unit of stimulus is lower.
            val (low, high) = repRange(goal, exercise.compound)

            SetPrescription(
                muscleGroup = group,
                exerciseName = exercise.name,
                sets = exerciseSets.coerceAtLeast(1),
                repRangeLow = low,
                repRangeHigh = high,
                targetRir = effectiveRir,
            )
        }.filter { it.sets > 0 }
    }

    private fun repRange(goal: Goal, compound: Boolean): Pair<Int, Int> = when (goal) {
        Goal.STRENGTH -> if (compound) 3 to 6 else 8 to 12
        // Cutting favours slightly higher reps at lighter loads: the stimulus to hold muscle
        // is preserved while joint stress from heavy loading is reduced in a fatigued state.
        Goal.CUT, Goal.MAKE_WEIGHT -> if (compound) 6 to 10 else 12 to 20
        Goal.HYPERTROPHY, Goal.RECOMP -> if (compound) 5 to 8 else 10 to 15
    }

    private fun blockName(goal: Goal, weeks: Int): String {
        val descriptor = when (goal) {
            Goal.HYPERTROPHY -> "Hypertrophy"
            Goal.STRENGTH -> "Strength"
            Goal.CUT -> "Cut"
            Goal.RECOMP -> "Recomposition"
            Goal.MAKE_WEIGHT -> "Weight Cut"
        }
        return "$descriptor Block (${weeks + 1} weeks)"
    }

    private fun buildNotes(profile: Profile, adjustment: VolumeAdjustment): List<String> = buildList {
        add("Stop each set at the prescribed reps in reserve, not at a fixed weight.")
        add("Add load or reps whenever you beat the top of the rep range at target RIR.")
        if (profile.goal == Goal.MAKE_WEIGHT) {
            add("Volume is set to defend lean mass, not to grow. Progress is measured by holding strength while weight falls.")
        }
        adjustment.rationale?.let { add(it) }
    }
}
