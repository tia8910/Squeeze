package com.squeeze.core.bodycomp

import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisualAssessmentTest {

    @Test
    fun `the female ladder is not the male one relabelled`() {
        // Women carry essential fat men do not, so the same appearance is worth roughly ten
        // points more. Sharing a scale would tell every woman she is obese.
        val leanMale = VisualAssessment.bandsFor(Sex.MALE).first().percent
        val leanFemale = VisualAssessment.bandsFor(Sex.FEMALE).first().percent

        assertTrue(
            leanFemale > leanMale + 4.0,
            "male $leanMale vs female $leanFemale — the ladders must not coincide",
        )
    }

    @Test
    fun `bands ascend so a picker can present them in order`() {
        Sex.entries.forEach { sex ->
            val percents = VisualAssessment.bandsFor(sex).map { it.percent }
            assertEquals(percents.sorted(), percents, "$sex bands are out of order")
        }
    }

    @Test
    fun `every band describes something the user can actually check`() {
        Sex.entries.forEach { sex ->
            VisualAssessment.bandsFor(sex).forEach { band ->
                assertTrue(
                    band.markers.length > 40,
                    "${sex}/${band.label} needs observable markers, not a label",
                )
            }
        }
    }

    @Test
    fun `a chosen band becomes an estimate carrying its own error`() {
        val estimate = VisualAssessment.estimate(12.0, Sex.MALE)

        assertNotNull(estimate)
        assertEquals(EstimationMethod.VISUAL_ASSESSMENT, estimate.method)
        assertEquals(12.0, estimate.percent)
        // Wide, and it must stay wide: fusion weights by precision, and overstating this
        // would let a mirror check overrule a tape measurement.
        assertTrue(estimate.standardErrorPercent >= 4.0)
    }

    @Test
    fun `an impossible value is refused rather than clamped`() {
        assertNull(VisualAssessment.estimate(0.5, Sex.MALE))
        assertNull(VisualAssessment.estimate(70.0, Sex.FEMALE))
    }

    @Test
    fun `a measured number maps back to a band the user can verify`() {
        // The direction that catches a bad scan: a number that says 20% on someone with
        // visible abs disagrees about something they can settle by looking down.
        val band = VisualAssessment.bandFor(20.0, Sex.MALE)

        assertTrue(band.markers.contains("No visible abs"), band.markers)
    }

    @Test
    fun `visual assessment is less precise than tape but more than nothing`() {
        // Its place in the ordering is the whole design: it must not outrank a measurement,
        // and it must not be so wide that fusion ignores it.
        val visual = EstimationMethod.VISUAL_ASSESSMENT.standardErrorPercent
        val navy = EstimationMethod.NAVY_CIRCUMFERENCE.standardErrorPercent
        val bmi = EstimationMethod.DEURENBERG_BMI.standardErrorPercent

        assertTrue(visual > navy, "a mirror check must not outrank a tape")
        assertTrue(visual < bmi + 1.0, "it should be competitive with the BMI fallback")
    }

    @Test
    fun `repeatability beats accuracy, which is what the trend needs`() {
        val method = EstimationMethod.VISUAL_ASSESSMENT

        assertTrue(
            method.repeatabilityPercent < method.standardErrorPercent,
            "someone picks the same band twice unless something changed; that stability is " +
                "what makes this usable for tracking even though the absolute number is soft",
        )
    }
}
