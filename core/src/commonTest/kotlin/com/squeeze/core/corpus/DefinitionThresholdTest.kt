package com.squeeze.core.corpus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What it takes for this app to be allowed a threshold again.
 *
 * A constant named `CLEARLY_DEFINED = 20.0` shipped here once. It was fitted by eye to three
 * photographs and it fired on a patch of blank painted wall, which the metric of the day scored
 * at 21.3. The app then told its user the figure had been read from his photo.
 *
 * So the bar for the replacement is not "is it fitted" — it is "can it be refused". These tests
 * are mostly about refusal: too few photographs, all of one answer, a score that does not
 * separate the two groups. Every one of those returns null rather than a number, because a
 * boundary nobody can evaluate is a guess with a decimal point on it.
 */
class DefinitionThresholdTest {

    private fun samples(
        visibleScores: List<Double>,
        smoothScores: List<Double>,
    ): List<DefinitionSample> =
        visibleScores.map { DefinitionSample(it, visible = true) } +
            smoothScores.map { DefinitionSample(it, visible = false) }

    /** Two groups a metric genuinely separates: smooth low, visible high, no overlap. */
    private fun separable() = samples(
        visibleScores = List(12) { 18.0 + it * 0.4 },
        smoothScores = List(12) { 4.0 + it * 0.4 },
    )

    @Test
    fun `a corpus that separates yields a boundary between the groups`() {
        val fit = DefinitionThreshold.fit(separable())

        assertNotNull(fit)
        assertTrue(fit.threshold > 8.8, "boundary ${fit.threshold} below the smooth group")
        assertTrue(fit.threshold < 18.0, "boundary ${fit.threshold} above the visible group")
        assertEquals(24, fit.sampleCount)
        assertEquals(12, fit.visibleCount)
        assertTrue(fit.balancedAccuracy > 0.99, "got ${fit.balancedAccuracy}")
    }

    @Test
    fun `three photographs fit nothing, which is how the last threshold was chosen`() {
        // The whole of the evidence behind CLEARLY_DEFINED = 20.0: two abdomens at 32.5 and
        // 21.9, one at 16.5. Perfectly separable, and meaningless.
        val fit = DefinitionThreshold.fit(
            samples(visibleScores = listOf(32.5, 21.9), smoothScores = listOf(16.5)),
        )

        assertNull(fit, "three photographs must not be allowed to decide a boundary")
    }

    @Test
    fun `a corpus of one answer cannot locate a boundary`() {
        // Thirty photographs, every one judged visible. Every threshold under the lowest score
        // is perfect on this data and tells you nothing about a body that is not.
        val fit = DefinitionThreshold.fit(
            samples(visibleScores = List(30) { 15.0 + it * 0.5 }, smoothScores = emptyList()),
        )

        assertNull(fit)
    }

    @Test
    fun `a lopsided corpus is refused before it can be flattered`() {
        // Twenty-six photographs, three of them smooth. Plain accuracy would reward calling
        // everything visible at about 0.88. Balanced accuracy scores that strategy at 0.5,
        // and MIN_PER_CLASS refuses the fit before it gets that far.
        val fit = DefinitionThreshold.fit(
            samples(
                visibleScores = List(23) { 18.0 + it * 0.3 },
                smoothScores = listOf(4.0, 5.0, 6.0),
            ),
        )

        assertNull(fit)
    }

    @Test
    fun `a score that does not separate is reported as not separating`() {
        // **The case the old threshold could not detect.** Both groups drawn from the same
        // spread, which is what a metric reading the camera rather than the body produces.
        // There is a best boundary here and it is worthless; the fit has to say so.
        val interleaved = (0 until 24).map {
            DefinitionSample(score = 10.0 + it * 0.5, visible = it % 2 == 0)
        }

        val fit = DefinitionThreshold.fit(interleaved)

        assertNull(fit, "an unseparating score must not produce a boundary")
    }

    @Test
    fun `quality is measured by holding each photograph out`() {
        // Scored against its own training data this corpus looks perfect. Held out, one
        // sample near the boundary flips — so the reported figure is under 1.0, and that gap
        // is the entire point: a number that cannot fall is not a measurement of anything.
        val overlapping = samples(
            visibleScores = List(12) { 12.0 + it * 0.5 },
            smoothScores = List(12) { 4.0 + it * 0.7 },
        )

        val fit = DefinitionThreshold.fit(overlapping)

        assertNotNull(fit)
        assertTrue(
            fit.balancedAccuracy <= 1.0,
            "leave-one-out cannot exceed a perfect score: ${fit.balancedAccuracy}",
        )
        assertTrue(fit.balancedAccuracy >= DefinitionThreshold.MIN_BALANCED_ACCURACY)
    }

    @Test
    fun `the shortfall says what is actually missing`() {
        // For the labelling screen. The binding constraint is usually the rarer answer, not
        // the total, and saying "nine more" when nine more of the same kind would not help is
        // worse than saying nothing.
        val onlyVisible = samples(visibleScores = List(18) { 20.0 + it }, smoothScores = emptyList())
        assertEquals(
            DefinitionThreshold.MIN_PER_CLASS,
            DefinitionThreshold.shortfall(onlyVisible),
            "eighteen photographs, none smooth — the shortfall is smooth ones",
        )

        assertEquals(0, DefinitionThreshold.shortfall(separable()))

        assertEquals(
            DefinitionThreshold.MIN_SAMPLES,
            DefinitionThreshold.shortfall(emptyList()),
        )
    }

    @Test
    fun `a non-finite score cannot drag the boundary`() {
        // An unreadable crop reaching the fitter as NaN would otherwise poison every
        // comparison it takes part in, silently.
        val poisoned = separable() + DefinitionSample(Double.NaN, visible = true)

        val fit = DefinitionThreshold.fit(poisoned)

        assertNotNull(fit)
        assertEquals(24, fit.sampleCount, "the NaN sample must be dropped, not counted")
    }
}
