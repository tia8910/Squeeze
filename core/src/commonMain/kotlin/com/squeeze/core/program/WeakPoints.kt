package com.squeeze.core.program

import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Sex
import com.squeeze.core.text.fixed
import kotlin.math.abs

/**
 * A body part the measurements say is lagging, and why.
 *
 * @param group what to train more of
 * @param severity how far below the expected proportion it sits, 0 to 1. Used for ranking
 *   only — it is a distance from a population ratio, not a diagnosis.
 * @param finding the observation, stated as the ratio it came from
 * @param prescription what to do about it
 */
data class WeakPoint(
    val group: MuscleGroup,
    val severity: Double,
    val finding: String,
    val prescription: String,
)

/**
 * Finds lagging body parts from the circumferences a photo scan already produces.
 *
 * The reasoning is proportional, never absolute, and that is what makes it work from a scan
 * whose absolute centimetres may be off. A ratio divides two measurements taken from one
 * photograph at one scale, so the scale error cancels — an arm-to-chest ratio is right even
 * when both numbers are five per cent large. This is the same property [BodyProportions]
 * relies on, applied to training rather than to health.
 *
 * The ratios themselves come from classical physique anthropometry, where they have been
 * used to judge balance for the better part of a century. The best known is that a balanced
 * trainee's neck, upper arm and calf measure close to the same; the others follow from the
 * same tradition. They are coarse, and the copy says so — the point is not to grade someone
 * against an ideal, it is to notice which of their own parts is furthest from where the rest
 * of their body sits, which is a much easier question and the one that matters for choosing
 * what to prioritise.
 *
 * What this deliberately does **not** do is treat a low number as a verdict on the person.
 * Every prescription names sets to add, not a flaw to feel bad about.
 */
object WeakPointAnalysis {

    /** Upper arm as a fraction of chest, for a trained adult. */
    private const val ARM_TO_CHEST = 0.36

    /** Thigh as a fraction of waist. Below this the legs are behind the torso. */
    private const val THIGH_TO_WAIST = 0.68

    /** Calf as a fraction of thigh. Calves are the classic lagging part. */
    private const val CALF_TO_THIGH = 0.63

    /** Chest divided by waist — the taper that back and chest width produce. */
    private const val CHEST_TO_WAIST = 1.25

    /** Upper arm against neck. The classical rule is equality, observed on men. */
    private const val ARM_TO_NECK_MALE = 1.0
    private const val ARM_TO_NECK_FEMALE = 0.88

    /**
     * How far below a ratio counts as lagging at all.
     *
     * Generous, because these ratios vary with frame and with the scan's own noise, and
     * flagging everyone's every part would make the feature worthless. Only a clear shortfall
     * earns a place in the programme.
     */
    private const val THRESHOLD = 0.06

    /** Nobody can prioritise five things. Beyond this it stops being a priority list. */
    const val MAX_PRIORITIES = 3

    /**
     * @param sex used only to soften the arm and neck ratios, which were derived on men and
     *   run high for women
     */
    fun analyse(c: Circumferences, sex: Sex): List<WeakPoint> = buildList {
        val chest = c.chestCm
        val waist = c.waistCm
        val arm = c.armCm
        val thigh = c.thighCm
        val calf = c.calfCm
        val neck = c.neckCm

        val femaleAdjust = if (sex == Sex.FEMALE) 0.92 else 1.0

        if (arm != null && chest != null && chest > 0) {
            shortfall(arm / chest, ARM_TO_CHEST * femaleAdjust)?.let {
                add(
                    WeakPoint(
                        group = MuscleGroup.BICEPS,
                        severity = it,
                        finding = "Your arms measure ${(arm / chest * 100).fixed(0)}% of " +
                            "your chest, where a balanced build sits nearer " +
                            "${(ARM_TO_CHEST * femaleAdjust * 100).fixed(0)}%.",
                        prescription = "Add two to four direct arm sets a week. Arms grow from " +
                            "pressing and pulling, but rarely enough on their own once the " +
                            "big lifts are heavy.",
                    ),
                )
                add(
                    WeakPoint(
                        group = MuscleGroup.TRICEPS,
                        severity = it,
                        finding = "Most of an upper arm's girth is triceps, not biceps.",
                        prescription = "Give triceps at least as many sets as biceps — they " +
                            "are the larger of the two and the one usually under-trained.",
                    ),
                )
            }
        }

        if (thigh != null && waist != null && waist > 0) {
            shortfall(thigh / waist, THIGH_TO_WAIST)?.let {
                add(
                    WeakPoint(
                        group = MuscleGroup.QUADS,
                        severity = it,
                        finding = "Your thighs measure ${(thigh / waist * 100).fixed(0)}% " +
                            "of your waist, where a trained lower body sits nearer " +
                            "${(THIGH_TO_WAIST * 100).fixed(0)}%.",
                        prescription = "Prioritise squats or leg press, and add a set or two " +
                            "per session over the block rather than all at once.",
                    ),
                )
                add(
                    WeakPoint(
                        group = MuscleGroup.HAMSTRINGS,
                        severity = it * 0.8,
                        finding = "Thigh girth is mostly quadriceps, so hamstrings can lag " +
                            "without changing this measurement much.",
                        prescription = "Include a hip hinge and a knee flexion movement — " +
                            "hamstrings need both, and squats train neither well.",
                    ),
                )
            }
        }

        if (calf != null && thigh != null && thigh > 0) {
            shortfall(calf / thigh, CALF_TO_THIGH)?.let {
                add(
                    WeakPoint(
                        group = MuscleGroup.CALVES,
                        severity = it,
                        finding = "Your calves measure ${(calf / thigh * 100).fixed(0)}% " +
                            "of your thighs, against about " +
                            "${(CALF_TO_THIGH * 100).fixed(0)}%.",
                        prescription = "Calves tolerate and need more frequency than anything " +
                            "else — three short sessions a week beats one long one, with a " +
                            "pause at the bottom of each rep.",
                    ),
                )
            }
        }

        if (chest != null && waist != null && waist > 0) {
            shortfall(chest / waist, CHEST_TO_WAIST)?.let {
                add(
                    WeakPoint(
                        group = MuscleGroup.BACK,
                        severity = it,
                        finding = "Your chest measures ${(chest / waist).fixed(2)} times " +
                            "your waist, where a developed torso is nearer " +
                            "${CHEST_TO_WAIST.fixed(2)}.",
                        prescription = "Width comes from lats more than from chest. Put " +
                            "vertical pulling first in the session, while you are fresh.",
                    ),
                )
                add(
                    WeakPoint(
                        group = MuscleGroup.SHOULDERS,
                        severity = it * 0.9,
                        finding = "Side delts set the top of the taper and are missed by " +
                            "pressing alone.",
                        prescription = "Add lateral raises at two or three sessions a week. " +
                            "They recover fast enough to take the frequency.",
                    ),
                )
            }
        }

        // The oldest rule in the book: neck, arm and calf close to equal. It needs no chest
        // or waist, so it still says something when the torso sites were rejected.
        //
        // Sex-adjusted like the others, and for the same reason. Equality was observed on
        // men; women carry proportionally less upper-arm mass against the same neck, so
        // holding them to the male figure reports an arm deficit on almost every woman —
        // which is not a finding, it is the ratio being used outside the population it came
        // from.
        if (neck != null && arm != null && neck > 0) {
            shortfall(arm / neck, if (sex == Sex.FEMALE) ARM_TO_NECK_FEMALE else ARM_TO_NECK_MALE)
                ?.let {
                    add(
                        WeakPoint(
                            group = MuscleGroup.BICEPS,
                            severity = it,
                            finding = "Your arms measure less than your neck. On a balanced " +
                                "build neck, upper arm and calf come out close to equal.",
                            prescription = "Arms are the lagging part relative to the rest " +
                                "of you — add direct work before adding more pressing.",
                        ),
                    )
                }
        }
    }
        .groupBy { it.group }
        // One entry per muscle, keeping the strongest signal. Two findings for the same
        // group would read as two separate problems and double its weight in the ranking.
        .map { (_, found) -> found.maxBy { it.severity } }
        .sortedByDescending { it.severity }

    /**
     * Adds asymmetry to the picture.
     *
     * Kept separate from [analyse] because it answers a different question and needs a
     * different response: a lagging muscle wants more volume, a lopsided one wants the same
     * volume done one side at a time.
     */
    fun asymmetryAdvice(leftCm: Double?, rightCm: Double?, part: String): String? {
        if (leftCm == null || rightCm == null) return null
        val mean = (leftCm + rightCm) / 2.0
        if (mean <= 0) return null

        val difference = abs(leftCm - rightCm) / mean * 100.0
        if (difference < 5.0) return null

        val bigger = if (leftCm > rightCm) "left" else "right"
        return "Your $bigger $part measures ${difference.fixed(0)}% larger. " +
            "Train unilaterally for a block — " +
            "start each set with the smaller side and match its reps on the bigger one."
    }

    /** The groups worth handing to [ProgramGenerator] as priorities. */
    fun priorityGroups(weakPoints: List<WeakPoint>): Set<MuscleGroup> =
        weakPoints.take(MAX_PRIORITIES).map { it.group }.toSet()

    /** How far below [expected] a ratio sits, as a fraction, or null when it is fine. */
    private fun shortfall(actual: Double, expected: Double): Double? {
        if (expected <= 0.0) return null
        val relative = (expected - actual) / expected
        return relative.takeIf { it > THRESHOLD }?.coerceAtMost(1.0)
    }
}
