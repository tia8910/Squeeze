package com.squeeze.core.program

import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeakPointsTest {

    /** A reasonably balanced trained male: nothing here should be flagged. */
    private val balanced = Circumferences(
        neckCm = 38.0,
        chestCm = 100.0,
        waistCm = 80.0,
        hipCm = 95.0,
        thighCm = 58.0,
        armCm = 37.0,
        calfCm = 38.0,
    )

    @Test
    fun `a balanced build is left alone`() {
        val found = WeakPointAnalysis.analyse(balanced, Sex.MALE)

        assertTrue(
            found.isEmpty(),
            "flagging a balanced body would make the feature noise: $found",
        )
    }

    @Test
    fun `small calves are caught`() {
        val found = WeakPointAnalysis.analyse(balanced.copy(calfCm = 32.0), Sex.MALE)

        assertTrue(found.any { it.group == MuscleGroup.CALVES }, "$found")
    }

    @Test
    fun `a narrow torso points at the back rather than the chest`() {
        // The instinct is to prescribe bench press. Width is lats, and the copy has to say so
        // or the user trains the wrong thing harder.
        val found = WeakPointAnalysis.analyse(balanced.copy(chestCm = 90.0), Sex.MALE)

        val back = found.firstOrNull { it.group == MuscleGroup.BACK }
        assertNotNull(back)
        assertTrue(back.prescription.contains("lats"), back.prescription)
    }

    @Test
    fun `small arms surface both heads, not just biceps`() {
        val found = WeakPointAnalysis.analyse(balanced.copy(armCm = 30.0), Sex.MALE)

        assertTrue(found.any { it.group == MuscleGroup.BICEPS }, "$found")
        assertTrue(
            found.any { it.group == MuscleGroup.TRICEPS },
            "most of an arm is triceps; prescribing only curls is the common mistake",
        )
    }

    @Test
    fun `legs behind the torso surface hamstrings as well as quads`() {
        val found = WeakPointAnalysis.analyse(balanced.copy(thighCm = 46.0), Sex.MALE)

        assertTrue(found.any { it.group == MuscleGroup.QUADS }, "$found")
        assertTrue(
            found.any { it.group == MuscleGroup.HAMSTRINGS },
            "thigh girth is mostly quads, so hamstrings hide behind a thigh measurement",
        )
    }

    @Test
    fun `a muscle is never reported twice`() {
        // Small arms trip both the arm-to-chest ratio and the neck-arm-calf rule.
        val found = WeakPointAnalysis.analyse(balanced.copy(armCm = 28.0), Sex.MALE)

        assertEquals(
            found.size,
            found.distinctBy { it.group }.size,
            "one muscle listed twice reads as two problems and doubles its ranking weight",
        )
    }

    @Test
    fun `findings are ranked worst first`() {
        val found = WeakPointAnalysis.analyse(
            balanced.copy(armCm = 30.0, calfCm = 36.0),
            Sex.MALE,
        )

        assertTrue(found.size >= 2)
        assertTrue(
            found.zipWithNext().all { (a, b) -> a.severity >= b.severity },
            "ranking is what makes the priority list mean anything",
        )
    }

    @Test
    fun `priorities are capped so they stay priorities`() {
        val everything = Circumferences(
            neckCm = 40.0,
            chestCm = 88.0,
            waistCm = 85.0,
            thighCm = 44.0,
            armCm = 27.0,
            calfCm = 26.0,
        )
        val priorities = WeakPointAnalysis.priorityGroups(
            WeakPointAnalysis.analyse(everything, Sex.MALE),
        )

        assertTrue(priorities.size <= WeakPointAnalysis.MAX_PRIORITIES, "$priorities")
        assertTrue(priorities.isNotEmpty())
    }

    @Test
    fun `missing sites simply produce fewer findings rather than guesses`() {
        val sparse = Circumferences(waistCm = 80.0, neckCm = 38.0)

        val found = WeakPointAnalysis.analyse(sparse, Sex.MALE)

        assertTrue(found.isEmpty(), "nothing measurable was measured: $found")
    }

    @Test
    fun `the women's arm ratio is not the men's`() {
        val slightArms = balanced.copy(armCm = 32.0)

        val forMen = WeakPointAnalysis.analyse(slightArms, Sex.MALE)
        val forWomen = WeakPointAnalysis.analyse(slightArms, Sex.FEMALE)

        assertTrue(forMen.any { it.group == MuscleGroup.BICEPS })
        assertTrue(
            forWomen.none { it.group == MuscleGroup.BICEPS },
            "the classical ratios were derived on men and read every woman as arm-deficient",
        )
    }

    @Test
    fun `asymmetry is advised on separately from lagging`() {
        // A lagging muscle wants more volume; a lopsided one wants the same volume done one
        // side at a time. Conflating them prescribes the wrong fix.
        val advice = WeakPointAnalysis.asymmetryAdvice(38.0, 34.0, "arm")

        assertNotNull(advice)
        assertTrue(advice.contains("left"))
        assertTrue(advice.contains("unilaterally"))
    }

    @Test
    fun `normal side-to-side difference is not flagged`() {
        assertNull(WeakPointAnalysis.asymmetryAdvice(37.0, 36.5, "arm"))
    }
}
