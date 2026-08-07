package com.squeeze.core.bodycomp

import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import com.squeeze.core.model.Skinfolds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BodyFatCalculatorTest {

    private val male = Profile(heightCm = 180.0, birthYear = 1995, sex = Sex.MALE)
    private val female = Profile(heightCm = 165.0, birthYear = 1995, sex = Sex.FEMALE)

    @Test
    fun `navy male matches the Hodgdon-Beckett equation`() {
        // 180 cm, 38 cm neck, 85 cm waist.
        // 495 / (1.0324 - 0.19077*log10(47) + 0.15456*log10(180)) - 450 = 16.107
        val result = BodyFatCalculator.navy(male, Circumferences(neckCm = 38.0, waistCm = 85.0))
        assertNotNull(result)
        assertEquals(16.107, result.percent, 0.01)
        assertEquals(EstimationMethod.NAVY_CIRCUMFERENCE, result.method)
    }

    @Test
    fun `navy female matches the Hodgdon-Beckett equation`() {
        // 165 cm, 32 cm neck, 70 cm waist, 95 cm hip.
        // 495 / (1.29579 - 0.35004*log10(133) + 0.22100*log10(165)) - 450 = 24.856
        val result = BodyFatCalculator.navy(
            female,
            Circumferences(neckCm = 32.0, waistCm = 70.0, hipCm = 95.0),
        )
        assertNotNull(result)
        assertEquals(24.856, result.percent, 0.01)
    }

    @Test
    fun `navy returns null when a required site is missing`() {
        assertNull(BodyFatCalculator.navy(male, Circumferences(neckCm = 38.0)))
        // Women need hip; the same inputs that suffice for a man are not enough.
        assertNull(BodyFatCalculator.navy(female, Circumferences(neckCm = 32.0, waistCm = 70.0)))
    }

    @Test
    fun `navy rejects anatomically impossible girth rather than returning NaN`() {
        // Waist below neck would make the log argument non-positive.
        val result = BodyFatCalculator.navy(male, Circumferences(neckCm = 45.0, waistCm = 40.0))
        assertNull(result)
    }

    @Test
    fun `navy body fat rises with waist and falls with neck`() {
        val lean = BodyFatCalculator.navy(male, Circumferences(neckCm = 38.0, waistCm = 78.0))!!
        val heavier = BodyFatCalculator.navy(male, Circumferences(neckCm = 38.0, waistCm = 95.0))!!
        assertTrue(heavier.percent > lean.percent, "larger waist must estimate higher body fat")

        val thickerNeck = BodyFatCalculator.navy(male, Circumferences(neckCm = 42.0, waistCm = 85.0))!!
        val thinnerNeck = BodyFatCalculator.navy(male, Circumferences(neckCm = 38.0, waistCm = 85.0))!!
        assertTrue(thickerNeck.percent < thinnerNeck.percent, "larger neck must estimate lower body fat")
    }

    @Test
    fun `jackson pollock three site male is in the expected range`() {
        // Sum of 45 mm across chest, abdomen and thigh at age 30 -> roughly 13%.
        val result = BodyFatCalculator.jacksonPollock3(
            male,
            Skinfolds(chestMm = 12.0, abdomenMm = 20.0, thighMm = 13.0),
            age = 30,
        )
        assertNotNull(result)
        assertEquals(13.0, result.percent, 1.5)
    }

    @Test
    fun `jackson pollock uses sex specific sites`() {
        val maleSites = Skinfolds(chestMm = 12.0, abdomenMm = 20.0, thighMm = 13.0)
        // Female equation needs triceps and suprailiac, which the male site set lacks.
        assertNull(BodyFatCalculator.jacksonPollock3(female, maleSites, age = 30))

        val femaleSites = Skinfolds(tricepsMm = 18.0, suprailiacMm = 14.0, thighMm = 22.0)
        assertNotNull(BodyFatCalculator.jacksonPollock3(female, femaleSites, age = 30))
    }

    @Test
    fun `siri converts body density to fat percentage`() {
        // A density of 1.05 g per cubic cm corresponds to about 21.4% fat.
        assertEquals(21.4, BodyFatCalculator.siri(1.05), 0.1)
    }

    @Test
    fun `deurenberg overestimates for a muscular subject as documented`() {
        // 95 kg at 180 cm is BMI 29.3. A trained lifter at that weight may be near 12% fat,
        // but BMI cannot see muscle, so the estimate lands far higher. The test pins this
        // known weakness so the UI keeps labelling the method as a placeholder.
        val result = BodyFatCalculator.deurenbergBmi(male, weightKg = 95.0, age = 30)
        assertNotNull(result)
        assertTrue(result.percent > 25.0, "expected BMI method to overestimate, got ${result.percent}")
        assertEquals(4.5, result.standardErrorPercent)
    }

    @Test
    fun `partition splits weight into fat and lean mass`() {
        val partition = BodyFatCalculator.partition(weightKg = 80.0, bodyFatPercent = 20.0)
        assertEquals(16.0, partition.fatMassKg, 1e-9)
        assertEquals(64.0, partition.leanMassKg, 1e-9)
    }
}
