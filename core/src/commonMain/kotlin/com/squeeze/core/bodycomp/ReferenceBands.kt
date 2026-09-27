package com.squeeze.core.bodycomp

import com.squeeze.core.model.Sex

/** Where a reading sits against the population it should be compared with. */
enum class BandPosition { LOW, NORMAL, HIGH }

/**
 * A reading placed against a published reference.
 *
 * @param label the category name, e.g. "Athletic"
 * @param detail what the category means, and where the boundary sits
 */
data class ReferenceBand(
    val position: BandPosition,
    val label: String,
    val detail: String,
    /**
     * Whether the reading deserves attention. Separate from [position] because direction is
     * not the verdict: "Athletic" body fat and "Well trained" FFMI sit off the middle and are
     * good news, and were drawn in the warning colour because the chip read the position.
     */
    val caution: Boolean = position != BandPosition.NORMAL,
)

/**
 * Population norms for the figures the app derives.
 *
 * A number on its own does not tell most people anything — "17.4%" only becomes useful once
 * you know whether that is lean, ordinary or high for someone like you. These are the
 * published categories, named so they can be checked rather than trusted.
 *
 * Two limits worth stating, because they bound what a category can mean:
 *
 *  - **A category is a population statement, not a health verdict.** Where a body fat
 *    percentage sits says nothing on its own about whether a particular person is healthy.
 *  - **[BandPosition] is direction, not judgement.** HIGH on fat-free mass index is a good
 *    outcome; HIGH on waist-to-height is not. The UI colours by what the metric means, not
 *    by the enum.
 */
object ReferenceBands {

    /**
     * Body fat categories from the American Council on Exercise.
     *
     * Age shifts these upward — the same percentage is more ordinary at 55 than at 25 — so
     * the boundaries move with [age] rather than pretending one table fits every decade.
     */
    fun bodyFat(percent: Double, sex: Sex, age: Int): ReferenceBand {
        // ACE publishes its table for younger adults. Body fat rises with age at constant
        // habits, so holding the young-adult boundaries would put most healthy people over
        // forty in the "high" band and tell them nothing useful.
        val drift = ((age - 30).coerceIn(0, 40)) * 0.1

        // ACE: essential fat is 2–5 % in men and 10–13 % in women, so "below essential"
        // starts under the bottom of that range. It was 5 and 13, which called a man at 4 %
        // — inside the essential range — below it.
        val essential = if (sex == Sex.MALE) 2.0 else 10.0
        val athletic = (if (sex == Sex.MALE) 13.0 else 20.0) + drift
        val fitness = (if (sex == Sex.MALE) 17.0 else 24.0) + drift
        val average = (if (sex == Sex.MALE) 24.0 else 31.0) + drift

        return when {
            percent < essential -> ReferenceBand(
                BandPosition.LOW,
                "Below essential",
                "Under the fat the body needs to function. Not a target — check the scan.",
            )

            percent <= (if (sex == Sex.MALE) 5.0 else 13.0) -> ReferenceBand(
                BandPosition.LOW,
                "Essential fat only",
                "Contest-level lean, held only briefly. Check the scan.",
            )

            percent <= athletic -> ReferenceBand(
                BandPosition.LOW,
                "Athletic",
                "Lean, typical of people who train and manage their diet.",
                caution = false,
            )

            percent <= fitness -> ReferenceBand(
                BandPosition.NORMAL,
                "Fitness",
                "Leaner than average, and sustainable.",
            )

            percent <= average -> ReferenceBand(
                BandPosition.NORMAL,
                "Average",
                "The ordinary range for adults.",
            )

            else -> ReferenceBand(
                BandPosition.HIGH,
                "Above average",
                "Above the typical range. Read it with your waist-to-height.",
            )
        }
    }

    /**
     * Fat-free mass index, the standard reference for how much muscle a frame carries.
     *
     * The top band is deliberately blunt. Above roughly 25 in men is very rarely reached
     * without pharmacological help, and a reading there is more often an overestimated lean
     * mass — usually from a body fat figure that is too low — than a remarkable physique.
     */
    fun ffmi(value: Double, sex: Sex): ReferenceBand {
        val belowAverage = if (sex == Sex.MALE) 18.0 else 14.0
        val average = if (sex == Sex.MALE) 20.0 else 16.0
        val aboveAverage = if (sex == Sex.MALE) 22.0 else 18.0
        val excellent = if (sex == Sex.MALE) 25.0 else 21.0

        return when {
            value < belowAverage -> ReferenceBand(
                BandPosition.LOW,
                "Below average",
                "Less lean mass than typical for your height. Strength training raises it.",
            )

            value < average -> ReferenceBand(
                BandPosition.NORMAL,
                "Average",
                "Typical for adults of your sex. Lean people often look muscular here.",
            )

            value < aboveAverage -> ReferenceBand(
                BandPosition.NORMAL,
                "Above average",
                "More lean mass than most untrained adults.",
            )

            value < excellent -> ReferenceBand(
                BandPosition.HIGH,
                "Well trained",
                "Typical after years of consistent training.",
                caution = false,
            )

            else -> ReferenceBand(
                BandPosition.HIGH,
                "Exceptional — check your inputs",
                "Above the usual drug-free limit. A too-low body fat reading inflates it — check both inputs.",
            )
        }
    }

    /**
     * Waist-to-height, the simplest single screen for central fat.
     *
     * "Keep your waist under half your height" holds across sexes, ages and ethnicities
     * better than BMI does, which is why the app leads with it.
     */
    fun waistToHeight(value: Double): ReferenceBand = when {
        value < 0.40 -> ReferenceBand(
            BandPosition.LOW,
            "Slim",
            "Below the usual range, and fine on its own.",
            caution = false,
        )

        value < 0.50 -> ReferenceBand(
            BandPosition.NORMAL,
            "Healthy",
            "The healthy range.",
        )

        value < 0.60 -> ReferenceBand(
            BandPosition.HIGH,
            "Increased",
            "Above the healthy range.",
        )

        else -> ReferenceBand(
            BandPosition.HIGH,
            "High",
            "Well above the healthy range — the measure most worth moving.",
        )
    }

    /** Waist-to-hip, from the WHO's thresholds for central adiposity. */
    fun waistToHip(value: Double, sex: Sex): ReferenceBand {
        // WHO (2008): substantially increased risk from 0.90 in men and 0.85 in women. The
        // male band between 0.90 and 1.00 was labelled "Moderate", which undersold the WHO's
        // own cut-off; it is now "Increased".
        val moderate = if (sex == Sex.MALE) 0.90 else 0.80
        val high = if (sex == Sex.MALE) 1.00 else 0.85

        return when {
            value < moderate -> ReferenceBand(
                BandPosition.NORMAL,
                "Low risk",
                "Fat is not concentrated at the middle.",
            )

            value < high -> ReferenceBand(
                if (sex == Sex.MALE) BandPosition.HIGH else BandPosition.NORMAL,
                if (sex == Sex.MALE) "Increased" else "Moderate",
                if (sex == Sex.MALE) "At or over the WHO's 0.90 cut-off for men." else "Slightly more at the middle than the hips.",
            )

            else -> ReferenceBand(
                BandPosition.HIGH,
                "High",
                "Over the WHO cut-off — the pattern most linked to metabolic risk.",
            )
        }
    }

    /** BMI, included for reference and openly limited. */
    fun bmi(value: Double): ReferenceBand = when {
        value < 18.5 -> ReferenceBand(BandPosition.LOW, "Underweight", "Below the WHO range.")

        value < 25.0 -> ReferenceBand(
            BandPosition.NORMAL,
            "Normal",
            "Inside the WHO range.",
        )

        value < 30.0 -> ReferenceBand(
            BandPosition.HIGH,
            "Overweight by BMI",
            "BMI can't tell muscle from fat; muscular people often land here.",
        )

        else -> ReferenceBand(
            BandPosition.HIGH,
            "Obese by BMI",
            "BMI can't tell muscle from fat. Body fat and waist-to-height are better guides.",
        )
    }
}
