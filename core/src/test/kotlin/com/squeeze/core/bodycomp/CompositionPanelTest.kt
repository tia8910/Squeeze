package com.squeeze.core.bodycomp

import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The derived panel.
 *
 * The interesting property is not that the arithmetic is right — it is that nothing is ever
 * invented. A figure that needs a weight must not appear without one, and what cannot be
 * computed has to come back as a specific thing the user could supply.
 */
class CompositionPanelTest {

    private val profile = Profile(heightCm = 178.0, birthYear = 1990, sex = Sex.MALE)
    private val year = 2026

    private val full = Circumferences(
        neckCm = 38.0,
        waistCm = 84.0,
        hipCm = 98.0,
        chestCm = 104.0,
        thighCm = 58.0,
        armCm = 34.0,
        calfCm = 38.0,
    )

    private fun metric(list: List<Metric>, name: String): Metric? =
        list.firstOrNull { it.name == name }

    @Test
    fun `mass figures require a weight and appear once one is given`() {
        val without = CompositionAnalyser.analyse(profile, full, 18.0, null, year)
        assertTrue(
            metric(without.composition, "Fat mass") == null,
            "fat mass was reported with no weight to derive it from",
        )
        assertTrue(
            without.missing.any { it.input.contains("bodyweight", ignoreCase = true) },
            "the missing weight was not reported as something the user could supply",
        )

        val with = CompositionAnalyser.analyse(profile, full, 18.0, 80.0, year)
        val fat = metric(with.composition, "Fat mass")
        val lean = metric(with.composition, "Fat-free mass")

        assertNotNull(fat)
        assertNotNull(lean)
        assertEquals(14.4, fat.value, 1e-9)
        assertEquals(65.6, lean.value, 1e-9)
    }

    @Test
    fun `FFMI and its normalised form are consistent with each other`() {
        val panel = CompositionAnalyser.analyse(profile, full, 18.0, 80.0, year)

        val ffmi = metric(panel.composition, "FFMI")
        val normalised = metric(panel.composition, "Normalised FFMI")
        assertNotNull(ffmi)
        assertNotNull(normalised)

        // 65.6 kg of lean mass at 1.78 m.
        assertEquals(65.6 / (1.78 * 1.78), ffmi.value, 1e-9)

        // Below the 1.8 m reference, so normalisation adjusts upward.
        assertTrue(
            normalised.value > ffmi.value,
            "a person under 1.8 m should normalise upward, got ${normalised.value}",
        )
    }

    @Test
    fun `skeletal muscle needs all three limb girths and is labelled rough without skinfolds`() {
        val noLimbs = full.copy(armCm = null, calfCm = null)
        val without = CompositionAnalyser.analyse(profile, noLimbs, 18.0, 80.0, year)

        assertTrue(
            metric(without.composition, "Skeletal muscle mass") == null,
            "muscle mass was reported without the girths it needs",
        )
        assertTrue(
            without.missing.any { it.unlocks.contains("muscle", ignoreCase = true) },
            "no guidance offered on how to unlock muscle mass",
        )

        val with = CompositionAnalyser.analyse(profile, full, 18.0, 80.0, year)
        val muscle = metric(with.composition, "Skeletal muscle mass")
        assertNotNull(muscle)

        // Uncorrected girths overestimate, and the label has to say so rather than present
        // the figure at the same confidence as a directly measured ratio.
        assertEquals(Confidence.ROUGH, muscle.confidence)
        assertTrue(muscle.value in 20.0..60.0, "implausible muscle mass ${muscle.value}")
    }

    @Test
    fun `waist to height crosses its threshold at exactly half the height`() {
        val under = CompositionAnalyser.analyse(
            profile,
            full.copy(waistCm = 88.0),
            18.0,
            80.0,
            year,
        )
        val over = CompositionAnalyser.analyse(
            profile,
            full.copy(waistCm = 90.0),
            18.0,
            80.0,
            year,
        )

        // 178 cm, so the threshold sits at an 89 cm waist.
        val below = metric(under.shape, "Waist-to-height")
        val above = metric(over.shape, "Waist-to-height")
        assertNotNull(below)
        assertNotNull(above)

        assertTrue(below.value < 0.5 && above.value >= 0.5)
        assertTrue(
            below.detail.contains("Under 0.5"),
            "the reading below the threshold should not warn: ${below.detail}",
        )
        assertTrue(
            above.detail.contains("over it"),
            "the reading above the threshold should say so: ${above.detail}",
        )
    }

    @Test
    fun `ratios come back whenever both of their inputs exist`() {
        val panel = CompositionAnalyser.analyse(profile, full, 18.0, 80.0, year)

        val whr = metric(panel.shape, "Waist-to-hip")
        val taper = metric(panel.shape, "Chest-to-waist")
        assertNotNull(whr)
        assertNotNull(taper)

        assertEquals(84.0 / 98.0, whr.value, 1e-9)
        assertEquals(104.0 / 84.0, taper.value, 1e-9)

        // Both sides of a ratio come from one photo at one scale, so scale error divides out
        // — which is why these are the panel's most trustworthy entries.
        assertEquals(Confidence.DIRECT, whr.confidence)
        assertEquals(Confidence.DIRECT, taper.confidence)
    }

    @Test
    fun `resting energy is driven by lean mass rather than by bodyweight`() {
        val lean = CompositionAnalyser.analyse(profile, full, 10.0, 80.0, year)
        val fat = CompositionAnalyser.analyse(profile, full, 30.0, 80.0, year)

        val leanBmr = metric(lean.energy, "Resting energy")
        val fatBmr = metric(fat.energy, "Resting energy")
        assertNotNull(leanBmr)
        assertNotNull(fatBmr)

        // Same weight, different composition. A weight-driven equation would return the same
        // number for both, which is exactly the failure Katch-McArdle avoids.
        assertTrue(
            leanBmr.value > fatBmr.value,
            "the leaner body should have the higher resting cost at equal weight",
        )
    }

    @Test
    fun `a panel with almost no inputs is empty rather than fabricated`() {
        val panel = CompositionAnalyser.analyse(
            profile,
            Circumferences(),
            bodyFatPercent = null,
            weightKg = null,
            currentYear = year,
        )

        assertTrue(panel.composition.isEmpty(), "composition invented without inputs")
        assertTrue(panel.shape.isEmpty(), "shape invented without inputs")
        assertTrue(panel.energy.isEmpty(), "energy invented without inputs")
        assertTrue(panel.isEmpty)

        // And it must still be actionable rather than blank.
        assertTrue(panel.missing.isNotEmpty(), "no guidance offered for an empty panel")
    }

    @Test
    fun `body roundness stays finite for an extreme waist`() {
        // The index takes a square root of 1 minus a ratio, which goes imaginary once the
        // waist radius exceeds half the height. Guarded rather than producing NaN.
        val panel = CompositionAnalyser.analyse(
            profile,
            full.copy(waistCm = 600.0),
            18.0,
            80.0,
            year,
        )

        val roundness = metric(panel.shape, "Body roundness")
        if (roundness != null) {
            assertTrue(roundness.value.isFinite(), "body roundness returned ${roundness.value}")
        }
    }
}
