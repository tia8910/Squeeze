package com.squeeze.core.trend

/**
 * A quantity the dashboard can plot over time.
 *
 * Three, because these are the three that move independently and mean different things
 * together. Body fat falling while weight falls and muscle holds is a successful cut; body
 * fat falling while muscle falls too is not, and the percentage alone cannot tell those
 * apart. Letting the user switch between them is how the app stops answering half the
 * question.
 *
 * @param label what the chart is titled and what the filter chip reads
 * @param unitSuffix appended to the plotted value, so "%" and " kg" render correctly
 */
enum class TrendFactor(val label: String, val unitSuffix: String) {
    BODY_FAT("Body fat", "%"),
    WEIGHT("Weight", " kg"),
    MUSCLE("Muscle", " kg"),
}

/**
 * Which factors the user's own data actually supports, and which one to show.
 *
 * Availability is a data question, not a preference. Someone who has never entered a
 * bodyweight has no weight series and no muscle series — muscle is derived from weight and
 * body fat together — so offering those chips would promise a chart that cannot be drawn.
 */
object TrendFactors {

    /**
     * The factors with at least one recorded point, in a fixed order.
     *
     * One point is the threshold rather than two. A single reading cannot be drawn as a
     * trend, but the chart already says so in words, and that is a more useful answer than a
     * chip that silently disappears — the user knows they entered a weight, and a missing
     * tab reads as the app having lost it.
     */
    fun available(
        bodyFat: List<TrendPoint>,
        weight: List<TrendPoint>,
        muscle: List<TrendPoint>,
    ): List<TrendFactor> = buildList {
        if (bodyFat.isNotEmpty()) add(TrendFactor.BODY_FAT)
        if (weight.isNotEmpty()) add(TrendFactor.WEIGHT)
        if (muscle.isNotEmpty()) add(TrendFactor.MUSCLE)
    }

    /**
     * The factor to actually plot, given what the user last picked.
     *
     * Selection outlives the data behind it: deleting the only entry that carried a weight
     * while Weight is selected would otherwise leave the chart pointing at an empty series.
     * Falling back to the first available factor keeps the screen showing something true.
     *
     * @return the factor to plot, or null when there is no data at all
     */
    fun resolve(selected: TrendFactor?, available: List<TrendFactor>): TrendFactor? =
        when {
            available.isEmpty() -> null
            selected != null && selected in available -> selected
            else -> available.first()
        }
}
