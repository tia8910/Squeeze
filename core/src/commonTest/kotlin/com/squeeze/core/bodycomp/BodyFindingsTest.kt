package com.squeeze.core.bodycomp

import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The verdict a record gives about the body in it.
 *
 * Every case here is built by running [CompositionAnalyser] over real inputs rather than by
 * assembling metrics by hand, because the thing worth testing is that the findings agree with
 * the cards printed underneath them. A finding constructed from a metric this app would never
 * emit would pass and prove nothing.
 */
class BodyFindingsTest {

    private val man = Profile(heightCm = 175.0, birthYear = 1993, sex = Sex.MALE)

    private fun panel(
        profile: Profile = man,
        waistCm: Double? = 80.0,
        chestCm: Double? = null,
        hipCm: Double? = null,
        bodyFatPercent: Double? = 16.5,
        weightKg: Double? = 68.0,
    ) = CompositionAnalyser.analyse(
        profile = profile,
        circumferences = Circumferences(waistCm = waistCm, chestCm = chestCm, hipCm = hipCm),
        bodyFatPercent = bodyFatPercent,
        weightKg = weightKg,
        currentYear = 2026,
    )

    private fun titles(findings: List<BodyFinding>) = findings.map { it.title }

    @Test
    fun `the record from the screenshot reads as mostly good news`() {
        // 68 kg, 1.75 m, waist 80 cm, chest 128 cm — waist-to-height 0.457, chest-to-waist
        // 1.60, body fat in the fitness band, FFMI 18.5.
        val findings = BodyFindings.from(panel(chestCm = 128.0), Sex.MALE)

        assertTrue(findings.isNotEmpty())
        assertTrue(
            findings.any { it.title == "Waist under half your height" },
            titles(findings).toString(),
        )
        assertTrue(findings.any { it.title == "Marked V-taper" }, titles(findings).toString())
        // FFMI 18.5 is the untrained adult norm. For someone using a body-composition app
        // that is the honest weak point, and it is the one thing on the screen that moves
        // slowly enough to be worth naming today.
        assertTrue(
            findings.any { it.title == "Lean mass is the lever here" },
            titles(findings).toString(),
        )
    }

    @Test
    fun `strengths come before weak points`() {
        val findings = BodyFindings.from(panel(chestCm = 128.0), Sex.MALE)
        val kinds = findings.map { it.kind }

        assertEquals(kinds.sortedBy { it == FindingKind.WEAKNESS }, kinds)
    }

    @Test
    fun `a high waist is called out as the number most worth moving`() {
        val findings = BodyFindings.from(
            panel(waistCm = 100.0, bodyFatPercent = 28.0, weightKg = 92.0),
            Sex.MALE,
        )

        val waist = findings.single { it.title == "Waist is over half your height" }
        assertEquals(FindingKind.WEAKNESS, waist.kind)
        assertTrue(waist.detail.contains("0.57"), waist.detail)
    }

    @Test
    fun `nothing is invented when the record holds nothing`() {
        // A weight-only entry. There is no waist, no chest and no body fat, so there is
        // nothing to have a view about — and an empty list is the correct output rather than
        // a failure to be padded with generalities.
        val findings = BodyFindings.from(
            panel(waistCm = null, bodyFatPercent = null, weightKg = 70.0),
            Sex.MALE,
        )

        assertTrue(findings.isEmpty(), titles(findings).toString())
    }

    @Test
    fun `a null panel is not a crash`() {
        assertTrue(BodyFindings.from(null, Sex.MALE).isEmpty())
    }

    @Test
    fun `every finding names the figure it came from`() {
        // The anti-drift rule for this file: a finding that cannot be traced back to a number
        // on the same screen is an opinion, and this block is not where the app gets to have
        // opinions. Each detail carries a formatted value.
        val findings = BodyFindings.from(panel(chestCm = 128.0, hipCm = 96.0), Sex.MALE)

        assertTrue(findings.isNotEmpty())
        findings.forEach { finding ->
            assertTrue(
                finding.detail.any { it.isDigit() },
                "no figure in: ${finding.title} — ${finding.detail}",
            )
        }
    }

    @Test
    fun `a below-essential reading is a weak point, not a trophy`() {
        // The failure this whole area of the codebase exists to prevent, arriving one layer
        // later: if a scan ever does produce a single-digit figure again, the summary must not
        // congratulate the user on it.
        val findings = BodyFindings.from(panel(bodyFatPercent = 4.0), Sex.MALE)

        val fat = findings.single { it.title == "Below the fat your body needs" }
        assertEquals(FindingKind.WEAKNESS, fat.kind)
        assertFalse(
            findings.any { it.kind == FindingKind.STRENGTH && it.detail.contains("4.00") },
        )
    }

    @Test
    fun `an FFMI above the drug-free ceiling asks for the inputs to be checked`() {
        // 95 kg at 1.75 m and 6% body fat gives an FFMI near 29. The right response is
        // suspicion of the scan, not praise.
        val findings = BodyFindings.from(
            panel(bodyFatPercent = 6.0, weightKg = 95.0),
            Sex.MALE,
        )

        val ffmi = findings.single { it.title == "This reading needs checking" }
        assertEquals(FindingKind.WEAKNESS, ffmi.kind)
    }

    @Test
    fun `the taper convention is sex-specific`() {
        val woman = Profile(heightCm = 165.0, birthYear = 1993, sex = Sex.FEMALE)
        // Chest 1.35x waist: a marked taper by the female convention, not by the male one.
        val chest = 70.0 * 1.35

        val asWoman = BodyFindings.from(
            panel(profile = woman, waistCm = 70.0, chestCm = chest, bodyFatPercent = 24.0),
            Sex.FEMALE,
        )
        val asMan = BodyFindings.from(
            panel(waistCm = 70.0, chestCm = chest),
            Sex.MALE,
        )

        assertTrue(asWoman.any { it.title == "Marked V-taper" }, titles(asWoman).toString())
        assertFalse(asMan.any { it.title == "Marked V-taper" }, titles(asMan).toString())
    }
}
