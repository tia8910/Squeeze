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

        if (bodyFatPercent != null && weightKg != null) {
            val partition = BodyFatCalculator.partition(weightKg, bodyFatPercent)

            composition += Metric(
                name = "Fat mass",
                value = partition.fatMassKg,
                unit = "kg",
                confidence = Confidence.ESTIMATED,
                detail = "Bodyweight multiplied by your body fat estimate, so it carries the " +
                    "same uncertainty as that estimate.",
            )

            composition += Metric(
                name = "Fat-free mass",
                value = partition.leanMassKg,
                unit = "kg",
                confidence = Confidence.ESTIMATED,
                detail = "Everything that is not fat: muscle, bone, organs and water. This is " +
                    "the number to watch during a cut — holding it is the whole point.",
            )

            val ffmi = partition.leanMassKg / (heightM * heightM)
            composition += Metric(
                name = "FFMI",
                value = ffmi,
                unit = "",
                confidence = Confidence.ESTIMATED,
                detail = "Fat-free mass index — lean mass scaled for height, so it can be " +
                    "compared between people. Around 19 is average for an untrained man, " +
                    "22 to 23 is well trained, and the drug-free ceiling sits near 25.",
            )

            // Kouri et al. 1995: normalises FFMI to a 1.8 m reference stature, because the
            // raw index still favours taller lifters.
            composition += Metric(
                name = "Normalised FFMI",
                value = ffmi + 6.1 * (1.8 - heightM),
                unit = "",
                confidence = Confidence.ESTIMATED,
                detail = "FFMI adjusted to a 1.8 m reference height, which removes most of the " +
                    "advantage raw FFMI gives to taller people.",
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
                detail = "From arm, thigh and calf girth (Lee 2000). The equation expects " +
                    "girths corrected for skinfold thickness; without skinfolds these include " +
                    "the fat over the muscle, so the figure runs high. Track its direction " +
                    "rather than its value.",
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
                detail = if (whtr >= 0.5) {
                    "At or above 0.5. Keeping your waist under half your height is the " +
                        "simplest single screen for central fat, and this is over it."
                } else {
                    "Under 0.5, which is the usual target. Waist under half your height is " +
                        "the simplest single screen for central fat."
                },
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
                    detail = "A Body Shape Index. Waist adjusted for height and weight, so it " +
                        "isolates how central your fat is rather than how much there is. " +
                        "Around 0.08 is typical; higher means more centrally carried.",
                )

                shape += Metric(
                    name = "BMI",
                    value = bmi,
                    unit = "",
                    confidence = Confidence.DIRECT,
                    detail = "Included for reference only. BMI cannot tell muscle from fat, " +
                        "which is why a lean, muscular person is routinely classed overweight " +
                        "by it. The figures above are better answers to the same question.",
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
                    detail = "Body Roundness Index, from waist and height. Treats your torso " +
                        "as an ellipse: 1 is a line, higher is rounder. Most adults fall " +
                        "between 2 and 7.",
                )
            }
        } else {
            missing += MissingInput(
                input = "A waist measurement",
                unlocks = "Waist-to-height, body roundness and shape indices",
            )
        }

        val hip = circumferences.hipCm
        if (waist != null && hip != null) {
            shape += Metric(
                name = "Waist-to-hip",
                value = waist / hip,
                unit = "",
                confidence = Confidence.DIRECT,
                detail = "Where fat sits rather than how much there is. Both numbers come " +
                    "from the same photo at the same scale, so scale error cancels — this is " +
                    "one of the most trustworthy things a scan produces.",
            )
        }

        val chest = circumferences.chestCm
        if (waist != null && chest != null) {
            shape += Metric(
                name = "Chest-to-waist",
                value = chest / waist,
                unit = "",
                confidence = Confidence.DIRECT,
                detail = "The V-taper. Rises when you build your upper back and chest, and " +
                    "when you lose from the waist — so it moves for two different good reasons.",
            )
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
                detail = "Katch-McArdle, driven by your lean mass rather than your weight. " +
                    "What your body costs to run at complete rest, before any activity.",
            )

            energy += Metric(
                name = "Maintenance, lightly active",
                value = bmr * 1.375,
                unit = "kcal/day",
                confidence = Confidence.ROUGH,
                detail = "Resting energy times a light-activity factor. Activity multipliers " +
                    "are a broad guess for any individual — treat this as a starting point to " +
                    "adjust from once you see how your weight actually responds.",
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
