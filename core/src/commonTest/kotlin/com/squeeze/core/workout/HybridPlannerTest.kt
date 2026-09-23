package com.squeeze.core.workout

import com.squeeze.core.model.Goal
import com.squeeze.core.model.TrainingAge
import com.squeeze.core.program.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HybridPlannerTest {

    @Test
    fun `every chosen sport gets at least one session`() {
        val chosen = setOf(Discipline.GYM, Discipline.PILATES, Discipline.RUNNING)
        val week = HybridPlanner.plan(chosen, 4, Goal.HYPERTROPHY, TrainingAge.INTERMEDIATE)
        val present = week.days.flatMap { d -> d.sessions.map { it.discipline } }.toSet()
        assertEquals(chosen, present)
        assertEquals(4, week.trainingDays)
    }

    @Test
    fun `a build gives strength most of the days, a cut gives endurance more`() {
        val chosen = listOf(Discipline.GYM, Discipline.RUNNING)
        val build = HybridPlanner.allocate(chosen, 5, Goal.HYPERTROPHY)
        val cut = HybridPlanner.allocate(chosen, 5, Goal.CUT)
        assertTrue(build.getValue(Discipline.GYM) > build.getValue(Discipline.RUNNING))
        assertTrue(cut.getValue(Discipline.RUNNING) >= build.getValue(Discipline.RUNNING))
        assertEquals(5, build.values.sum())
    }

    @Test
    fun `more sports than days makes the lightest ones add-ons, not casualties`() {
        val chosen = setOf(Discipline.GYM, Discipline.YOGA, Discipline.RUNNING, Discipline.PILATES)
        val week = HybridPlanner.plan(chosen, 2, Goal.RECOMP, TrainingAge.NOVICE)
        val sessions = week.days.flatMap { it.sessions }
        assertEquals(chosen, sessions.map { it.discipline }.toSet())
        assertTrue(sessions.filter { it.addOn }.all { it.discipline.kind == DisciplineKind.MOBILITY })
        assertEquals(2, week.trainingDays)
    }

    @Test
    fun `weak points come first and get an extra set`() {
        val week = HybridPlanner.plan(
            setOf(Discipline.CALISTHENICS), 3, Goal.HYPERTROPHY, TrainingAge.INTERMEDIATE,
            weakGroups = listOf(MuscleGroup.SHOULDERS),
        )
        val first = week.days.first { !it.rest }.sessions.first()
        assertEquals(MuscleGroup.SHOULDERS, first.items.first().group)
        assertTrue(first.items.first().weakPoint)
    }

    @Test
    fun `calisthenics progressions follow experience`() {
        val novice = HybridPlanner.plan(setOf(Discipline.CALISTHENICS), 2, Goal.HYPERTROPHY, TrainingAge.NOVICE)
        val advanced = HybridPlanner.plan(setOf(Discipline.CALISTHENICS), 2, Goal.HYPERTROPHY, TrainingAge.ADVANCED)
        fun push(w: HybridWeek) = w.days.flatMap { d -> d.sessions.flatMap { it.items } }
            .first { it.group == MuscleGroup.CHEST }.name
        assertEquals("Incline push-up", push(novice))
        assertEquals("Archer push-up", push(advanced))
    }

    @Test
    fun `planned calories rise with the sessions`() {
        val light = HybridPlanner.plan(setOf(Discipline.YOGA), 2, Goal.RECOMP, TrainingAge.INTERMEDIATE)
        val heavy = HybridPlanner.plan(setOf(Discipline.RUNNING, Discipline.GYM), 5, Goal.CUT, TrainingAge.INTERMEDIATE)
        assertTrue(heavy.netKcalPerDay(75.0) > light.netKcalPerDay(75.0))
    }

    @Test
    fun `double progression moves the weight once every set tops the range`() {
        val p = Prescription(3, 8..12, 2, 90)
        val topped = Coaching.next(List(3) { LoggedSet(60.0, 12) }, p, compound = true, lowerBody = false)
        assertEquals(62.5, topped.weightKg)
        val building = Coaching.next(listOf(LoggedSet(60.0, 10), LoggedSet(60.0, 9)), p, compound = true, lowerBody = false)
        assertEquals(60.0, building.weightKg)
    }

    @Test
    fun `strength goals prescribe fewer reps than size goals`() {
        val strength = Coaching.prescribe(Goal.STRENGTH, TrainingAge.INTERMEDIATE, compound = true, weakPoint = false)
        val size = Coaching.prescribe(Goal.HYPERTROPHY, TrainingAge.INTERMEDIATE, compound = true, weakPoint = false)
        assertTrue(strength.reps.last < size.reps.last)
    }

    @Test
    fun `the matcher ranks the closest machine first`() {
        fun axis(i: Int) = DoubleArray(4) { if (it == i) 1.0 else 0.0 }
        val ranked = EquipmentMatcher.rank(axis(1), mapOf("a" to listOf(axis(0)), "b" to listOf(axis(1), axis(2))))
        assertEquals("b", ranked.first().first)
        assertTrue(ranked.first().second > EquipmentMatcher.CONFIDENT)
    }

    @Test
    fun `every machine guide has instructions`() {
        EquipmentCatalog.ALL.forEach { assertTrue(it.steps.size >= 3, it.id) }
        assertEquals(EquipmentCatalog.ALL.size, EquipmentCatalog.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `a machine scores the average of its descriptions, so one lucky phrase does not win`() {
        fun axis(i: Int) = DoubleArray(4) { if (it == i) 1.0 else 0.0 }
        // "a" matches one of its two descriptions perfectly and the other not at all; "b"
        // matches both of its descriptions well. The average picks "b".
        val image = doubleArrayOf(0.6, 0.8, 0.0, 0.0)
        val ranked = EquipmentMatcher.rank(
            image,
            mapOf(
                "a" to listOf(doubleArrayOf(0.6, 0.8, 0.0, 0.0), axis(3)),
                "b" to listOf(doubleArrayOf(0.8, 0.6, 0.0, 0.0), doubleArrayOf(0.6, 0.8, 0.0, 0.0)),
            ),
        )
        assertEquals("b", ranked.first().first)
    }
}
