package com.squeeze.core.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressTest {

    private fun day(d: Long, vararg sets: Pair<Double, Int>) =
        sets.map { (kg, reps) -> DatedSet(d, "Bench", LoggedSet(kg, reps)) }

    @Test
    fun `adding reps at the same weight counts as progress`() {
        val log = day(1, 60.0 to 8, 60.0 to 8) + day(4, 60.0 to 10, 60.0 to 9)
        val p = ProgressAnalysis.progress(log).single()
        assertEquals(Trend.PROGRESSING, p.trend)
        assertTrue(p.changePercent!! > 0)
    }

    @Test
    fun `three sessions without a new best is a stall`() {
        val log = day(1, 70.0 to 8) + day(3, 67.5 to 8) + day(5, 67.5 to 8) + day(7, 67.5 to 9)
        assertEquals(Trend.STALLED, ProgressAnalysis.progress(log).single().trend)
    }

    @Test
    fun `today's summary compares with last time and flags a record`() {
        val log = day(1, 60.0 to 8, 60.0 to 8) + day(4, 62.5 to 8, 62.5 to 7)
        val summary = ProgressAnalysis.summary(log, 4)
        val bench = summary.exercises.single()
        assertEquals(2, summary.totalSets)
        assertEquals(62.5 * 15, summary.totalVolume, 1e-9)
        assertTrue(bench.personalRecord)
        assertTrue(bench.maxChangePercent!! > 0)
        assertEquals(listOf("Bench"), summary.records)
    }

    @Test
    fun `a first session is a start, not a record`() {
        val summary = ProgressAnalysis.summary(day(1, 50.0 to 10), 1)
        assertTrue(summary.records.isEmpty())
        assertEquals(Trend.NEW, ProgressAnalysis.progress(day(1, 50.0 to 10)).single().trend)
    }

    private val p = Prescription(3, 8..12, 2, 90)

    @Test
    fun `a set that was too easy moves the weight up now`() {
        val advice = Coaching.nextSet(listOf(LoggedSet(60.0, 13, 3)), p, compound = true, lowerBody = false)
        assertEquals(62.5, advice.weightKg)
        assertTrue("Too light" in advice.text)
    }

    @Test
    fun `a set below the range drops the weight`() {
        val advice = Coaching.nextSet(listOf(LoggedSet(80.0, 5, 0)), p, compound = true, lowerBody = false)
        assertTrue(advice.weightKg!! < 80.0)
        assertTrue("Too heavy" in advice.text)
    }

    @Test
    fun `a good set keeps the weight and asks for one more rep`() {
        val advice = Coaching.nextSet(listOf(LoggedSet(60.0, 9, 2)), p, compound = true, lowerBody = false)
        assertEquals(60.0, advice.weightKg)
        assertEquals(10, advice.reps)
    }

    @Test
    fun `after the planned sets it says done and sets next session`() {
        val advice = Coaching.nextSet(List(3) { LoggedSet(60.0, 12, 2) }, p, compound = true, lowerBody = false)
        assertTrue(advice.finished)
        assertTrue("62.5" in advice.text)
    }
}
