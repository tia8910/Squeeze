package com.squeeze.core.program

import com.squeeze.core.model.Goal
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import com.squeeze.core.model.TrainingAge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgramGeneratorTest {

    private val generator = ProgramGenerator()

    private val fullGym = setOf(
        Equipment.BARBELL, Equipment.DUMBBELL, Equipment.MACHINE,
        Equipment.CABLE, Equipment.BODYWEIGHT,
    )

    private fun profile(
        goal: Goal = Goal.HYPERTROPHY,
        trainingAge: TrainingAge = TrainingAge.INTERMEDIATE,
    ) = Profile(
        heightCm = 180.0,
        birthYear = 1995,
        sex = Sex.MALE,
        trainingAge = trainingAge,
        goal = goal,
    )

    private fun constraints(days: Int = 4, equipment: Set<Equipment> = fullGym) =
        TrainingConstraints(daysPerWeek = days, availableEquipment = equipment)

    @Test
    fun `block ends with a deload week`() {
        val meso = generator.generate(profile(), constraints())

        assertTrue(meso.weeks.isNotEmpty())
        assertTrue(meso.weeks.last().isDeload, "every block must end with a deload")
        assertEquals(1, meso.weeks.count { it.isDeload }, "exactly one deload per block")
    }

    @Test
    fun `session count matches the requested training frequency`() {
        for (days in 2..6) {
            val meso = generator.generate(profile(), constraints(days = days))
            meso.weeks.forEach { week ->
                assertEquals(days, week.sessions.size, "week ${week.weekIndex} at $days days/week")
            }
        }
    }

    @Test
    fun `frequency outside the safe range is rejected`() {
        assertFailsWith<IllegalArgumentException> { TrainingConstraints(7, fullGym) }
        assertFailsWith<IllegalArgumentException> { TrainingConstraints(1, fullGym) }
        assertFailsWith<IllegalArgumentException> { TrainingConstraints(4, emptySet()) }
    }

    @Test
    fun `volume ramps up across the accumulation weeks`() {
        val meso = generator.generate(profile(), constraints())
        val accumulation = meso.weeks.filterNot { it.isDeload }

        val first = accumulation.first().weeklySets(MuscleGroup.CHEST)
        val last = accumulation.last().weeklySets(MuscleGroup.CHEST)
        assertTrue(last > first, "volume should climb from $first to $last across the block")
    }

    @Test
    fun `deload drops below the lowest accumulation week`() {
        val meso = generator.generate(profile(), constraints())
        val lowestAccumulation = meso.weeks.filterNot { it.isDeload }
            .minOf { it.weeklySets(MuscleGroup.CHEST) }
        val deload = meso.weeks.last().weeklySets(MuscleGroup.CHEST)

        assertTrue(deload < lowestAccumulation, "deload $deload should undercut $lowestAccumulation")
    }

    @Test
    fun `effort increases as the block progresses`() {
        val meso = generator.generate(profile(), constraints())
        val accumulation = meso.weeks.filterNot { it.isDeload }

        val firstRir = accumulation.first().sessions.flatMap { it.prescriptions }.minOf { it.targetRir }
        val lastRir = accumulation.last().sessions.flatMap { it.prescriptions }.minOf { it.targetRir }
        assertTrue(lastRir < firstRir, "RIR should fall from $firstRir to $lastRir as the block ramps")
    }

    @Test
    fun `novices are never prescribed training to failure`() {
        val meso = generator.generate(profile(trainingAge = TrainingAge.NOVICE), constraints())
        val minimumRir = meso.weeks.flatMap { it.sessions }.flatMap { it.prescriptions }.minOf { it.targetRir }

        assertTrue(minimumRir >= 2, "novices should keep at least 2 RIR, got $minimumRir")
    }

    @Test
    fun `cutting prescribes less volume than bulking`() {
        val bulk = generator.generate(profile(goal = Goal.HYPERTROPHY), constraints())
        val cut = generator.generate(profile(goal = Goal.CUT), constraints())

        val bulkPeak = bulk.weeks.filterNot { it.isDeload }.maxOf { it.weeklySets(MuscleGroup.CHEST) }
        val cutPeak = cut.weeks.filterNot { it.isDeload }.maxOf { it.weeklySets(MuscleGroup.CHEST) }

        assertTrue(cutPeak < bulkPeak, "a deficit cannot support bulk volume: $cutPeak vs $bulkPeak")
    }

    @Test
    fun `cutting uses higher rep ranges on isolation work`() {
        val cut = generator.generate(profile(goal = Goal.CUT), constraints())
        val strength = generator.generate(profile(goal = Goal.STRENGTH), constraints())

        val cutReps = cut.weeks.first().sessions.flatMap { it.prescriptions }.maxOf { it.repRangeHigh }
        val strengthReps = strength.weeks.first().sessions.flatMap { it.prescriptions }.maxOf { it.repRangeHigh }

        assertTrue(cutReps > strengthReps, "cut $cutReps should exceed strength $strengthReps")
    }

    @Test
    fun `strength work uses low reps on compounds`() {
        val meso = generator.generate(profile(goal = Goal.STRENGTH), constraints())
        val compoundNames = ExerciseLibrary.ALL.filter { it.compound }.map { it.name }.toSet()

        val compoundLow = meso.weeks.first().sessions
            .flatMap { it.prescriptions }
            .filter { it.exerciseName in compoundNames }
            .minOf { it.repRangeLow }

        assertTrue(compoundLow <= 3, "strength blocks should reach low reps, got $compoundLow")
    }

    @Test
    fun `only available equipment is prescribed`() {
        val bodyweightOnly = generator.generate(
            profile(),
            constraints(equipment = setOf(Equipment.BODYWEIGHT)),
        )

        val allowed = ExerciseLibrary.ALL
            .filter { it.equipment == Equipment.BODYWEIGHT }
            .map { it.name }
            .toSet()

        val prescribed = bodyweightOnly.weeks.flatMap { it.sessions }
            .flatMap { it.prescriptions }
            .map { it.exerciseName }
            .toSet()

        assertTrue(prescribed.isNotEmpty(), "a bodyweight user must still get a programme")
        assertTrue(allowed.containsAll(prescribed), "prescribed unavailable exercises: ${prescribed - allowed}")
    }

    @Test
    fun `priority groups receive more volume`() {
        val plain = generator.generate(profile(), constraints())
        val prioritised = generator.generate(
            profile(),
            TrainingConstraints(4, fullGym, priorityGroups = setOf(MuscleGroup.BACK)),
        )

        val plainBack = plain.weeks.first().weeklySets(MuscleGroup.BACK)
        val priorityBack = prioritised.weeks.first().weeklySets(MuscleGroup.BACK)
        assertTrue(priorityBack >= plainBack, "prioritised back $priorityBack vs $plainBack")
    }

    @Test
    fun `volume never exceeds the recoverable ceiling`() {
        val landmarks = VolumeLandmarks.forTrainingAge(TrainingAge.INTERMEDIATE)
        val meso = generator.generate(
            profile(),
            TrainingConstraints(6, fullGym, priorityGroups = MuscleGroup.entries.toSet()),
            adjustment = VolumeAdjustment(1.5, "stress test"),
        )

        for (week in meso.weeks) {
            for (group in MuscleGroup.entries) {
                val ceiling = landmarks.getValue(group).maximumRecoverable
                assertTrue(
                    week.weeklySets(group) <= ceiling,
                    "${group} at ${week.weeklySets(group)} sets exceeds MRV of $ceiling in week ${week.weekIndex}",
                )
            }
        }
    }

    @Test
    fun `composition adjustment scales prescribed volume`() {
        val plain = generator.generate(profile(), constraints())
        val reduced = generator.generate(
            profile(),
            constraints(),
            adjustment = VolumeAdjustment(0.8, "protecting lean mass"),
        )

        val plainTotal = plain.weeks.first().sessions.sumOf { it.totalSets }
        val reducedTotal = reduced.weeks.first().sessions.sumOf { it.totalSets }
        assertTrue(reducedTotal < plainTotal, "adjusted $reducedTotal should undercut $plainTotal")
    }

    @Test
    fun `adjustment rationale reaches the user`() {
        val meso = generator.generate(
            profile(),
            constraints(),
            adjustment = VolumeAdjustment(0.8, "Volume reduced to protect lean mass."),
        )
        assertTrue(meso.notes.any { it.contains("protect lean mass") }, "notes were ${meso.notes}")
    }

    @Test
    fun `every prescription is internally valid`() {
        for (days in 2..6) {
            for (goal in Goal.entries) {
                for (age in TrainingAge.entries) {
                    val meso = generator.generate(profile(goal, age), constraints(days))
                    val prescriptions = meso.weeks.flatMap { it.sessions }.flatMap { it.prescriptions }

                    assertTrue(prescriptions.isNotEmpty(), "$goal/$age/$days produced no work")
                    prescriptions.forEach {
                        assertTrue(it.sets > 0, "non-positive sets in $goal/$age/$days")
                        assertTrue(it.repRangeLow <= it.repRangeHigh, "inverted rep range")
                        assertTrue(it.targetRir >= 0, "negative RIR")
                    }
                }
            }
        }
    }

    @Test
    fun `advanced lifters get shorter blocks than intermediates`() {
        val intermediate = generator.generate(profile(trainingAge = TrainingAge.INTERMEDIATE), constraints())
        val advanced = generator.generate(profile(trainingAge = TrainingAge.ADVANCED), constraints())

        assertTrue(
            advanced.durationWeeks < intermediate.durationWeeks,
            "advanced ${advanced.durationWeeks} should deload sooner than ${intermediate.durationWeeks}",
        )
    }

    @Test
    fun `make weight blocks explain their defensive intent`() {
        val meso = generator.generate(profile(goal = Goal.MAKE_WEIGHT), constraints())
        assertTrue(meso.notes.any { it.contains("defend lean mass") }, "notes were ${meso.notes}")
        assertFalse(meso.name.contains("Hypertrophy"))
    }
}
