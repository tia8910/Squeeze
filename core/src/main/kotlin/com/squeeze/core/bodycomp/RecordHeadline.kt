package com.squeeze.core.bodycomp

import kotlin.math.abs

/**
 * One figure in a record's headline strip: a label and an already-formatted value.
 *
 * Formatted here rather than in the composable so the strip and the cards below it can never
 * print the same quantity to different precision — the strip is a summary of the panel, and a
 * summary that disagrees with its source is worse than no summary.
 */
data class HeadlineFigure(val label: String, val value: String)

/**
 * The five figures that lead a record.
 *
 * A record holds upward of twenty numbers. Read top to bottom they answer no question at all;
 * the reader has to already know which of them they came for. So a small fixed set goes across
 * the top, and everything else stays where it was.
 *
 * The five are not the five most accurate. They are the five that between them describe a
 * body: a mass, a composition, and three shapes measured on different axes. [ORDER] runs from
 * the most direct to the most derived, which is also the order they stop being available in as
 * a record gets sparser — an entry with only a weight shows one figure, not a gap-toothed row.
 *
 * Deliberately absent: FFMI, resting energy, ABSI, BMI. Each is a real number the panel still
 * prints, and each is a second opinion on a quantity already in the strip. A headline that
 * says the same thing twice has spent a slot for nothing.
 */
object RecordHeadline {

    /**
     * Panel metrics that may lead a record, in the order they appear.
     *
     * Matched by name against [CompositionPanel.composition] and [CompositionPanel.shape],
     * because the panel emits a metric only when its inputs are present and the strip wants
     * exactly that behaviour: no placeholder, no dash, no row of blanks.
     */
    private val ORDER = listOf(
        "Body fat",
        "Waist-to-height",
        "Body roundness",
        "Chest-to-waist",
    )

    /** The most figures a strip can hold, weight included. */
    const val MAX_FIGURES = 5

    /**
     * @param panel the analysis for this one record, or null when there is none
     * @param weightKg the record's own weight, which is not a panel metric
     * @return between zero and [MAX_FIGURES] figures, in a fixed order, with absent ones
     *   dropped rather than shown empty
     */
    fun from(panel: CompositionPanel?, weightKg: Double?): List<HeadlineFigure> {
        val figures = mutableListOf<HeadlineFigure>()

        // First because it is the only one that was measured rather than worked out.
        weightKg?.let { figures += HeadlineFigure("Body weight", format(it, "kg")) }

        val byName = ((panel?.composition ?: emptyList()) + (panel?.shape ?: emptyList()))
            .associateBy { it.name }

        ORDER.forEach { name ->
            byName[name]?.let { figures += HeadlineFigure(name, format(it.value, it.unit)) }
        }

        return figures
    }

    /**
     * Formats a value at a precision its accuracy can support.
     *
     * A resting energy figure printed to one decimal would imply a precision the equation
     * does not have; a ratio printed as a whole number would hide the change the user is
     * looking for. The thresholds are on magnitude rather than on the quantity, so a new
     * metric gets sensible digits without being listed anywhere.
     */
    fun format(value: Double, unit: String): String {
        val text = when {
            unit == "kcal/day" -> "%.0f".format(value)
            abs(value) >= 100 -> "%.0f".format(value)
            abs(value) >= 10 -> "%.1f".format(value)
            else -> "%.2f".format(value)
        }
        return if (unit.isEmpty()) text else "$text $unit"
    }
}

/** This metric as it should be printed anywhere in the app; see [RecordHeadline.format]. */
fun Metric.formatted(): String = RecordHeadline.format(value, unit)
