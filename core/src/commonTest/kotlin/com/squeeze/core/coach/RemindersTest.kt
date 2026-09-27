package com.squeeze.core.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemindersTest {

    private val all = ReminderKind.entries.toSet()

    private val facts = ReminderFacts(
        isoDayOfWeek = 1,
        setupComplete = true,
        daysSinceScan = 2,
        todaysSession = "Push",
        todaysMinutes = 60,
        loggedToday = false,
        sessionsPlanned = 4,
        sessionsLogged = 1,
        setsPlanned = 55,
        setsLogged = 12,
    )

    @Test
    fun `the morning of a training day names the session and its length`() {
        val r = assertNotNull(ReminderPlanner.plan(ReminderSlot.MORNING, facts, all))
        assertEquals(ReminderKind.WORKOUT, r.kind)
        assertEquals("Today: Push · about 60 min", r.title)
        assertEquals(StepId.TODAYS_SESSION, r.opens)
    }

    @Test
    fun `a quiet day is silent`() {
        val rest = facts.copy(todaysSession = null)
        assertNull(ReminderPlanner.plan(ReminderSlot.MORNING, rest, all))
        assertNull(ReminderPlanner.plan(ReminderSlot.EVENING, rest, all))
    }

    @Test
    fun `a logged session gets no evening nudge`() {
        assertNotNull(ReminderPlanner.plan(ReminderSlot.EVENING, facts, all))
        assertNull(ReminderPlanner.plan(ReminderSlot.EVENING, facts.copy(loggedToday = true), all))
    }

    @Test
    fun `a check-in that is due asks for a matching photograph`() {
        val due = facts.copy(todaysSession = null, daysSinceScan = 8)
        val r = assertNotNull(ReminderPlanner.plan(ReminderSlot.MORNING, due, all))
        assertEquals(ReminderKind.CHECK_IN, r.kind)
        assertTrue(r.body.contains("same light"))
        assertEquals(StepId.WEEKLY_CHECK_IN, r.opens)
    }

    @Test
    fun `sunday evening sums up the week`() {
        val r = assertNotNull(ReminderPlanner.plan(ReminderSlot.EVENING, facts.copy(isoDayOfWeek = 7, loggedToday = true), all))
        assertEquals(ReminderKind.WEEK_SUMMARY, r.kind)
        assertEquals("Your week: 1 of 4 sessions · 12 of 55 sets", r.title)
    }

    @Test
    fun `a kind switched off is skipped, and the next one in line is used`() {
        val due = facts.copy(daysSinceScan = 8)
        val r = assertNotNull(ReminderPlanner.plan(ReminderSlot.MORNING, due, setOf(ReminderKind.CHECK_IN)))
        assertEquals(ReminderKind.CHECK_IN, r.kind)
        assertNull(ReminderPlanner.plan(ReminderSlot.MORNING, due, emptySet()))
    }

    @Test
    fun `nothing is sent before setup is finished`() {
        assertNull(ReminderPlanner.plan(ReminderSlot.MORNING, facts.copy(setupComplete = false), all))
    }

    @Test
    fun `the lock-screen version carries no numbers`() {
        val slots = listOf(
            ReminderPlanner.plan(ReminderSlot.MORNING, facts, all),
            ReminderPlanner.plan(ReminderSlot.MORNING, facts.copy(todaysSession = null, daysSinceScan = 9), all),
            ReminderPlanner.plan(ReminderSlot.EVENING, facts.copy(isoDayOfWeek = 7), all),
        )
        slots.forEach { r -> assertFalse(assertNotNull(r).publicTitle.any { it.isDigit() }, r.publicTitle) }
    }

    @Test
    fun `an evening steps nudge only when the walk is short enough to take`() {
        val done = facts.copy(loggedToday = true)
        val r = assertNotNull(ReminderPlanner.plan(ReminderSlot.EVENING, done.copy(steps = 5_600), all))
        assertEquals(ReminderKind.STEPS, r.kind)
        assertTrue(r.title.contains("25-minute walk"))
        assertNull(ReminderPlanner.plan(ReminderSlot.EVENING, done.copy(steps = 1_000), all))
        assertNull(ReminderPlanner.plan(ReminderSlot.EVENING, done.copy(steps = 9_000), all))
        assertNull(ReminderPlanner.plan(ReminderSlot.EVENING, done.copy(steps = null), all))
    }
}
