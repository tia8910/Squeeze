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

        val essential = if (sex == Sex.MALE) 5.0 else 13.0
        val athletic = (if (sex == Sex.MALE) 13.0 else 20.0) + drift
        val fitness = (if (sex == Sex.MALE) 17.0 else 24.0) + drift
        val average = (if (sex == Sex.MALE) 24.0 else 31.0) + drift

        return when {
            percent < essential -> ReferenceBand(
                BandPosition.LOW,
                "Below essential",
                "Under the fat the body needs for normal function. Sustained, this is not a " +
                    "target — it is a level competitors hold briefly and deliberately.",
            )

            percent <= athletic -> ReferenceBand(
                BandPosition.LOW,
                "Athletic",
                "Lean. Typical of people who train seriously and manage intake.",
            )

            percent <= fitness -> ReferenceBand(
                BandPosition.NORMAL,
                "Fitness",
                "Leaner than average and comfortably sustainable.",
            )

            percent <= average -> ReferenceBand(
                BandPosition.NORMAL,
                "Average",
                "Within the ordinary range for the population.",
            )

            else -> ReferenceBand(
                BandPosition.HIGH,
                "Above average",
                "Above the population's typical range. Worth reading alongside your " +
                    "waist-to-height, which tracks where the fat sits rather than how much.",
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
                "Less lean mass than typical for your height. A population comparison, not a " +
                    "judgement of your training: resistance work moves this more reliably " +
                    "than anything else, and it moves slowly.",
            )

            value < average -> ReferenceBand(
                BandPosition.NORMAL,
                "Average",
                "Around the adult average for your sex. This says how much lean mass you " +
                    "carry, not how hard you train — someone lean enough to look muscular " +
                    "often sits here, because visible muscle comes from low body fat as much " +
                    "as from mass.",
            )

            value < aboveAverage -> ReferenceBand(
                BandPosition.NORMAL,
                "Above average",
                "More lean mass than most untrained adults carry.",
            )

            value < excellent -> ReferenceBand(
                BandPosition.HIGH,
                "Well trained",
                "The range reached by people who have trained consistently for years.",
            )

            else -> ReferenceBand(
                BandPosition.HIGH,
                "Exceptional — check your inputs",
                "Above the level usually considered attainable drug-free. Before celebrating, " +
                    "check your weight and body fat: this figure is derived from both, and an " +
                    "underestimated body fat inflates it directly.",
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
            "Below the usual range. Not a concern on its own.",
        )

        value < 0.50 -> ReferenceBand(
            BandPosition.NORMAL,
            "Healthy",
            "Under the 0.5 boundary, which is the target this measure is built around.",
        )

        value < 0.60 -> ReferenceBand(
            BandPosition.HIGH,
            "Increased",
            "Over 0.5. The usual advice is to bring the waist under half your height.",
        )

        else -> ReferenceBand(
            BandPosition.HIGH,
            "High",
            "Well over the 0.5 boundary. This is the measure most worth moving.",
        )
    }

    /** Waist-to-hip, from the WHO's thresholds for central adiposity. */
    fun waistToHip(value: Double, sex: Sex): ReferenceBand {
        val moderate = if (sex == Sex.MALE) 0.90 else 0.80
        val high = if (sex == Sex.MALE) 1.00 else 0.85

        return when {
            value < moderate -> ReferenceBand(
                BandPosition.NORMAL,
                "Low risk",
                "Fat is not disproportionately carried around the middle.",
            )

            value < high -> ReferenceBand(
                BandPosition.NORMAL,
                "Moderate",
                "Somewhat more carried centrally than at the hips.",
            )

            else -> ReferenceBand(
                BandPosition.HIGH,
                "High",
                "Substantially more around the middle than the hips, which is the pattern " +
                    "most associated with metabolic risk.",
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
            "BMI cannot separate muscle from fat, so a lean, muscular person lands here " +
                "routinely. Read your body fat and waist-to-height before drawing anything " +
                "from this.",
        )

        else -> ReferenceBand(
            BandPosition.HIGH,
            "Obese by BMI",
            "Same caveat: BMI is blind to composition. The figures above are better answers " +
                "to the same question.",
        )
    }
}
