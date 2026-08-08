package com.squeeze.core.bodycomp

import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The five figures that lead a record.
 *
 * The properties worth holding are about honesty rather than arithmetic: the strip must never
 * show a figure the panel could not compute, must never disagree with the cards below it, and
 * must degrade by getting shorter rather than by leaving holes.
 */
class RecordHeadlineTest {

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

    private fun panel(
        circumferences: Circumferences = full,
        bodyFat: Double? = 18.0,
        weight: Double? = 80.0,
    ) = CompositionAnalyser.analyse(profile, circumferences, bodyFat, weight, year)

    @Test
    fun `a complete record leads with five figures in a fixed order`() {
        val figures = RecordHeadline.from(panel(), weightKg = 80.0)

        assertEquals(
            listOf(
                "Body weight",
                "Body fat",
                "Waist-to-height",
                "Body roundness",
                "Chest-to-waist",
            ),
            figures.map { it.label },
        )
        assertEquals(RecordHeadline.MAX_FIGURES, figures.size)
    }

    @Test
    fun `a sparser record gets a shorter strip, not an emptier one`() {
        // A tape entry with a waist and nothing else. Every figure that survives is real;
        // the ones that cannot be computed are absent rather than blank.
        val waistOnly = Circumferences(waistCm = 84.0)
        val figures = RecordHeadline.from(panel(waistOnly, bodyFat = null, weight = null), null)

        assertTrue(figures.isNotEmpty())
        assertTrue(figures.none { it.value.isBlank() }, "$figures")
        assertTrue(figures.none { it.label == "Body fat" }, "$figures")
        assertTrue(figures.none { it.label == "Chest-to-waist" }, "$figures")
        assertEquals(listOf("Waist-to-height", "Body roundness"), figures.map { it.label })
    }

    @Test
    fun `an empty record leads with nothing at all`() {
        assertEquals(emptyList<HeadlineFigure>(), RecordHeadline.from(null, null))
    }

    @Test
    fun `the weight shown is the record's own, not the panel's idea of one`() {
        val figures = RecordHeadline.from(panel(weight = 80.0), weightKg = 68.4)

        assertEquals("68.4 kg", figures.first { it.label == "Body weight" }.value)
    }

    @Test
    fun `the strip prints what the cards below it print`() {
        // The single property that makes the strip a summary rather than a second opinion.
        val panel = panel()
        val figures = RecordHeadline.from(panel, 80.0)
        val cards = (panel.composition + panel.shape).associateBy { it.name }

        figures.filter { it.label != "Body weight" }.forEach { figure ->
            assertEquals(cards.getValue(figure.label).formatted(), figure.value, figure.label)
        }
    }

    @Test
    fun `precision follows magnitude`() {
        assertEquals("0.49", RecordHeadline.format(0.4899, ""))
        assertEquals("11.6 %", RecordHeadline.format(11.63, "%"))
        assertEquals("2450 kcal/day", RecordHeadline.format(2450.4, "kcal/day"))
        // A resting energy figure is printed whole even below a hundred: the equation has no
        // more precision at 90 kcal than at 900.
        assertEquals("90 kcal/day", RecordHeadline.format(90.4, "kcal/day"))
    }

    @Test
    fun `the strip never outgrows the space it has`() {
        // MAX_FIGURES is what the layout was designed against; ORDER must not quietly grow
        // past it and start wrapping on a phone.
        assertTrue(RecordHeadline.from(panel(), 80.0).size <= RecordHeadline.MAX_FIGURES)
    }
}
