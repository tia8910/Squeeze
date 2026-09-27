package com.squeeze.core.bodycomp

import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * How much to trust a single number.
 *
 * Shown next to every figure, because this panel mixes things that are almost directly
 * measured with things that are three inferences deep, and presenting them at the same
 * visual weight would be the same lie as reporting a body fat percentage with no interval.
 */
enum class Confidence {
    /** Computed from measurements with no population model in between. */
    DIRECT,

    /** A validated equation applied to measurements, with published error. */
    ESTIMATED,

    /** Directionally useful, but the equation is being pushed past its inputs. */
    ROUGH,
}

/**
 * One derived figure.
 *
 * @param detail what the number means and, where it matters, what would make it better.
 */
data class Metric(
    val name: String,
    val value: Double,
    val unit: String,
    val confidence: Confidence,
    val detail: String,
    /**
     * Where this reading sits against the population, when a published reference exists.
     *
     * Null where none does. A number without a comparison tells most people nothing —
     * "17.4%" only becomes useful once you know whether that is lean or ordinary for
     * someone like you.
     */
    val band: ReferenceBand? = null,
)

/** Something the user could supply to unlock figures that cannot be computed without it. */
data class MissingInput(val input: String, val unlocks: String)

/**
 * Everything derivable about a body from a set of circumferences, a profile and a weight.
 *
 * Grouped rather than flat because the groups answer different questions: composition is
 * "what am I made of", shape is "how is it distributed", and energy is "what does it cost to
 * run". A single list of fourteen numbers is a data dump; these are three answers.
 */
data class CompositionPanel(
    val composition: List<Metric>,
    val shape: List<Metric>,
    val energy: List<Metric>,
    val missing: List<MissingInput>,
) {
    val isEmpty: Boolean get() = composition.isEmpty() && shape.isEmpty() && energy.isEmpty()
}

/**
 * Derives the full panel.
 *
 * Nothing here invents an input. Every figure is emitted only when the measurements it needs
 * are present, and the ones that cannot be computed become entries in [CompositionPanel.missing]
 * with the specific thing that would unlock them. That is the difference between a screen
 * that looks empty and one that tells the user what to do next.
 *
 * The equations are named in the comments so a reader can check them rather than trust them.
 */
object CompositionAnalyser {

    fun analyse(
        profile: Profile,
        circumferences: Circumferences,
        bodyFatPercent: Double?,
        weightKg: Double?,
        currentYear: Int,
    ): CompositionPanel {
        val heightM = profile.heightCm / 100.0
        val age = profile.ageAt(currentYear)

        val composition = mutableListOf<Metric>()
        val shape = mutableListOf<Metric>()
        val energy = mutableListOf<Metric>()
        val missing = mutableListOf<MissingInput>()

        // --- Composition -----------------------------------------------------------------

        if (bodyFatPercent != null) {
            composition += Metric(
                name = "Body fat",
                value = bodyFatPercent,
                unit = "%",
                confidence = Confidence.ESTIMATED,
                detail = "Combined from every method this entry supports, weighted by precision.",
                band = ReferenceBands.bodyFat(bodyFatPercent, profile.sex, age),
            )
        }

        if (bodyFatPercent != null && weightKg != null) {
            val partition = BodyFatCalculator.partition(weightKg, bodyFatPercent)

            composition += Metric(
                name = "Fat mass",
                value = partition.fatMassKg,
                unit = "kg",
                confidence = Confidence.ESTIMATED,
                detail = "Weight × body fat. As uncertain as the body fat figure.",
            )

            composition += Metric(
                name = "Fat-free mass",
                value = partition.leanMassKg,
                unit = "kg",
                confidence = Confidence.ESTIMATED,
                detail = "Muscle, bone, organs and water. Hold this steady on a cut.",
            )

            val ffmi = partition.leanMassKg / (heightM * heightM)
            composition += Metric(
                name = "FFMI",
                value = ffmi,
                unit = "",
                confidence = Confidence.ESTIMATED,
                detail = "Fat-free mass ÷ height². Comparable between people.",
                band = ReferenceBands.ffmi(ffmi, profile.sex),
            )

            // Kouri et al. 1995: normalises FFMI to a 1.8 m reference stature, because the
            // raw index still favours taller lifters.
            composition += Metric(
                name = "Normalised FFMI",
                value = ffmi + 6.1 * (1.8 - heightM),
                unit = "",
                confidence = Confidence.ESTIMATED,
                detail = "FFMI adjusted to a 1.8 m height (Kouri 1995), so tall and short compare fairly.",
            )
        }

        // Lee et al. 2000, the anthropometric skeletal muscle equation. It wants girths
        // corrected for skinfold thickness; without skinfolds the raw girths include
        // subcutaneous fat, so the result runs high. Emitted anyway because the trend in it
        // is informative even when the absolute value is not, and labelled ROUGH for exactly
        // that reason.
        val arm = circumferences.armCm
        val thigh = circumferences.thighCm
        val calf = circumferences.calfCm
        if (arm != null && thigh != null && calf != null) {
            val sexTerm = if (profile.sex == Sex.MALE) 2.4 else 0.0
            val skeletalMuscle = heightM *
                (0.00744 * arm * arm + 0.00088 * thigh * thigh + 0.00441 * calf * calf) +
                sexTerm - 0.048 * age + 7.8

            composition += Metric(
                name = "Skeletal muscle mass",
                value = skeletalMuscle,
                unit = "kg",
                confidence = Confidence.ROUGH,
                detail = "Lee 2000, from arm, thigh and calf girths. Runs high without " +
                    "skinfolds — track the direction, not the value.",
            )
        } else {
            missing += MissingInput(
                input = "Arm, thigh and calf measurements",
                unlocks = "Skeletal muscle mass",
            )
        }

        if (weightKg == null) {
            missing += MissingInput(
                input = "Your bodyweight",
                unlocks = "Fat mass, fat-free mass, FFMI and daily energy needs",
            )
        }

        // --- Shape -----------------------------------------------------------------------

        val waist = circumferences.waistCm

        if (waist != null) {
            val whtr = waist / profile.heightCm
            shape += Metric(
                name = "Waist-to-height",
                value = whtr,
                unit = "",
                confidence = Confidence.DIRECT,
                // The band says where the value sits; this says what the measure is, so the
                // two lines never repeat each other.
                detail = if (whtr >= 0.5) {
                    "Waist ÷ height. Over 0.5 — aim to bring it under."
                } else {
                    "Waist ÷ height. Under 0.5, the target for belly fat."
                },
                band = ReferenceBands.waistToHeight(whtr),
            )

            // A Body Shape Index (Krakauer 2012). Designed so that what remains after
            // removing height and weight is waist distribution alone, which is why it
            // predicts risk where waist circumference by itself does not.
            if (weightKg != null) {
                val bmi = weightKg / (heightM * heightM)
                val absi = (waist / 100.0) / (bmi.pow(2.0 / 3.0) * sqrt(heightM))
                shape += Metric(
                    name = "ABSI",
                    value = absi,
                    unit = "",
                    confidence = Confidence.ESTIMATED,
                    detail = "A Body Shape Index (Krakauer 2012): waist corrected for height and " +
                        "weight. Adults average about 0.080; higher means more fat at the middle.",
                )

                shape += Metric(
                    name = "BMI",
                    value = bmi,
                    unit = "",
                    confidence = Confidence.DIRECT,
                    detail = "For reference only — BMI cannot tell muscle from fat.",
                    band = ReferenceBands.bmi(bmi),
                )
            }

            // Body Roundness Index (Thomas 2013): models the torso as an ellipse whose
            // eccentricity comes from waist and height, so it responds to shape rather than
            // to mass.
            val waistRadius = (waist / 100.0) / (2.0 * PI)
            val halfHeight = 0.5 * heightM
            val ratio = waistRadius / halfHeight
            if (ratio < 1.0) {
                val bri = 364.2 - 365.5 * sqrt(1.0 - ratio * ratio)
                shape += Metric(
                    name = "Body roundness",
                    value = bri,
                    unit = "",
                    confidence = Confidence.ESTIMATED,
                    // Not "1 is a line": the formula gives a negative value for a line.
                    // US adults (NHANES) average about 5.
                    detail = "Body Roundness Index (Thomas 2013), from waist and height. " +
                        "Adults average about 5; lower is slimmer.",
                )
            }
        } else {
            missing += MissingInput(
                input = "A waist measurement",
                unlocks = "Waist-to-height, body roundness and shape indices",
            )
        }

        val hip = circumferences.hipCm
        val hipSuspect = waist != null && hip != null &&
            !com.squeeze.core.scan.PlausibleRanges.plausibleHip(waist, hip, profile.sex == Sex.FEMALE)
        if (hipSuspect) {
            // A hip this much wider than the waist is loose clothing or hands measured as hip;
            // printing its ratio as "Measured" would present an error as a fact.
            missing += MissingInput(
                input = "A retake in fitted clothing, hands away from your hips",
                unlocks = "Waist-to-hip (this photo's hip read too wide to be real)",
            )
        } else if (waist != null && hip != null) {
            shape += Metric(
                name = "Waist-to-hip",
                value = waist / hip,
                unit = "",
                confidence = Confidence.DIRECT,
                detail = "Where fat sits, not how much. Both from one photo, so scale error cancels.",
                band = ReferenceBands.waistToHip(waist / hip, profile.sex),
            )
        }

        val chest = circumferences.chestCm
        if (waist != null && chest != null) {
            if (com.squeeze.core.scan.PlausibleRanges.plausibleTaper(chest, waist)) {
                shape += Metric(
                    name = "Chest-to-waist",
                    value = chest / waist,
                    unit = "",
                    confidence = Confidence.DIRECT,
                    detail = "The V-taper. Rises as your upper body grows or your waist shrinks.",
                )
            } else {
                // Not shown as a figure: a chest this far beyond the waist is an arm counted as
                // chest, and printing it as "Measured" would present an error as a fact.
                missing += MissingInput(
                    input = "A retake with arms slightly away from your sides",
                    unlocks = "Chest-to-waist (this photo's chest read too wide to be real)",
                )
            }
        }

        // --- Energy ----------------------------------------------------------------------

        if (bodyFatPercent != null && weightKg != null) {
            val lean = BodyFatCalculator.partition(weightKg, bodyFatPercent).leanMassKg

            // Katch-McArdle. Preferred over Mifflin-St Jeor here because it is driven by lean
            // mass, which the app already estimates — and lean mass is what actually burns.
            val bmr = 370.0 + 21.6 * lean
            energy += Metric(
                name = "Resting energy",
                value = bmr,
                unit = "kcal/day",
                confidence = Confidence.ESTIMATED,
                detail = "Calories at complete rest (Katch-McArdle, from lean mass).",
            )

            // Named as the generic figure it is. The Fuel tab's maintenance uses the user's
            // actual training week and weight trend, so the two differ, and an unlabelled
            // second "maintenance" read as the app contradicting itself.
            energy += Metric(
                name = "Maintenance at light activity",
                value = bmr * 1.375,
                unit = "kcal/day",
                confidence = Confidence.ROUGH,
                detail = "Resting × 1.375. A generic guide — your Fuel plan uses your real week.",
            )
        }

        return CompositionPanel(
            composition = composition,
            shape = shape,
            energy = energy,
            missing = missing,
        )
    }
}
