package com.squeeze.core.scan

import com.squeeze.core.model.Circumferences
import kotlin.math.abs

/** Something about the photograph that makes some of its measurements unreliable. */
enum class PhotoIssue(val label: String) {
    ARM_RAISED("Arm raised or flexed"),
    ARM_ACROSS_BODY("Arm in front of the body"),
    LEGS_COVERED("Legs covered by clothing"),
}

/**
 * The girths a photograph can stand behind, and the ones it cannot.
 *
 * @param circumferences the input with every unreliable site set to null
 * @param withheld site name to the reason it was dropped, in the order sites are listed
 * @param issues what was wrong with the pose or the clothing
 * @param untrusted muscle groups the physique read must skip for the same reasons
 */
data class GirthCheck(
    val circumferences: Circumferences,
    val withheld: Map<String, String>,
    val issues: Set<PhotoIssue>,
    val untrusted: Set<MuscleGroup>,
)

/**
 * Drops measurements the pose or clothing has corrupted, instead of letting them reach a verdict.
 *
 * Every girth a front photograph gives is a width across the outline at one height. That width
 * is only the body when nothing else crosses that row. A raised, flexed arm puts the upper arm
 * beside the chest, so the "chest" includes an arm, and a chest 1.6 times the waist then reads
 * as an exceptional back. Loose trousers do the same to hip, thigh and calf. Each was seen on a
 * real scan. A missing number is honest; a wrong one becomes a strength or a weak point.
 *
 * Three checks, each conservative:
 * 1. Pose. An arm is raised or flexed when its elbow is above the shoulder, its fist is above
 *    the shoulder, or the upper arm points more sideways than down. A double-biceps or
 *    single-arm flex in a mirror does all three, and only the first was checked at first, so a
 *    flex with the elbow just under shoulder height got through. Chest and arm are withheld,
 *    and arms, chest and back width are not judged: a flexed arm is not the arm at rest. An elbow inside the shoulder line is an arm in
 *    front of the body: chest is withheld.
 * 2. Clothing. Legs that show less skin than the physique read needs: thigh and calf are
 *    withheld, since the outline there is the fabric.
 * 3. Proportion. A girth outside what human bodies measure, relative to height and waist, is
 *    withheld. The bounds are wide on purpose; they catch broken readings, not unusual bodies.
 */
object PhotoGirthCheck {

    /** An elbow this far (in torso lengths) above the shoulder line counts as raised. */
    private const val RAISED_MARGIN = 0.05

    /** An elbow closer to the midline than this share of half the shoulder width is across the body. */
    private const val ACROSS_SHARE = 0.6

    /** A typical phone photo held upright, for callers that do not know the real shape. */
    const val PORTRAIT_ASPECT = 0.6

    /**
     * @param aspect the photo's width over its height, so sideways and downward distances on the
     *   upper arm compare in pixels rather than in the two different normalised units
     */
    fun armRaised(pose: FrontPoseGeometry, aspect: Double = PORTRAIT_ASPECT): Boolean {
        val span = torsoSpan(pose) ?: return false
        val sides = listOf(
            Triple(pose.shoulderLeft, pose.elbowLeft, pose.wristLeft),
            Triple(pose.shoulderRight, pose.elbowRight, pose.wristRight),
        )
        return sides.any { (shoulder, elbow, wrist) ->
            val elbowUp = elbow != null && elbow.y < shoulder.y - RAISED_MARGIN * span
            val fistUp = wrist != null && wrist.y < shoulder.y
            // Upper arm out to the side: more horizontal travel than vertical, in pixels.
            val outward = elbow != null &&
                abs(elbow.x - shoulder.x) * aspect > (elbow.y - shoulder.y).coerceAtLeast(0.0)
            elbowUp || fistUp || outward
        }
    }

    fun armAcrossBody(pose: FrontPoseGeometry): Boolean {
        val centre = (pose.shoulderLeft.x + pose.shoulderRight.x) / 2.0
        val half = abs(pose.shoulderLeft.x - pose.shoulderRight.x) / 2.0
        if (half <= 0.0) return false
        val shoulderY = (pose.shoulderLeft.y + pose.shoulderRight.y) / 2.0
        val hipY = (pose.hipLeft.y + pose.hipRight.y) / 2.0
        return listOfNotNull(pose.elbowLeft, pose.elbowRight).any { elbow ->
            elbow.y in shoulderY..hipY && abs(elbow.x - centre) < ACROSS_SHARE * half
        }
    }

    /**
     * @param pose the front photograph's landmarks; null skips the pose check
     * @param visibility the part mask's share of skin per group; empty skips the clothing check
     * @param heightCm the user's height, the reference for the proportion bounds
     */
    fun check(
        c: Circumferences,
        pose: FrontPoseGeometry?,
        visibility: Map<MuscleGroup, Double>,
        heightCm: Double,
        female: Boolean,
        aspect: Double = PORTRAIT_ASPECT,
    ): GirthCheck {
        val issues = linkedSetOf<PhotoIssue>()
        val untrusted = linkedSetOf<MuscleGroup>()
        val withheld = linkedMapOf<String, String>()
        var out = c

        if (pose != null) {
            if (armRaised(pose, aspect)) {
                issues += PhotoIssue.ARM_RAISED
                untrusted += setOf(MuscleGroup.ARMS, MuscleGroup.CHEST, MuscleGroup.V_TAPER)
                if (out.chestCm != null) withheld["Chest"] = "Arm in the way"
                if (out.armCm != null) withheld["Arm"] = "Arm flexed or raised"
                out = out.copy(chestCm = null, armCm = null)
            }
            if (armAcrossBody(pose)) {
                issues += PhotoIssue.ARM_ACROSS_BODY
                untrusted += setOf(MuscleGroup.CHEST, MuscleGroup.V_TAPER)
                if (out.chestCm != null) withheld["Chest"] = "Arm in the way"
                out = out.copy(chestCm = null)
            }
        }

        val legs = visibility[MuscleGroup.LEGS]
        if (visibility.isNotEmpty() && legs != null && legs < PhysiqueAnalysis.MIN_VISIBLE) {
            issues += PhotoIssue.LEGS_COVERED
            untrusted += MuscleGroup.LEGS
            if (out.thighCm != null) withheld["Thigh"] = "Clothing"
            if (out.calfCm != null) withheld["Calf"] = "Clothing"
            out = out.copy(thighCm = null, calfCm = null)
        }

        if (heightCm > 0) {
            val waist = out.waistCm
            fun bad(value: Double?, low: Double, high: Double) =
                value != null && (value / heightCm !in low..high)

            if (bad(out.chestCm, 0.42, 0.78) ||
                (waist != null && out.chestCm != null && out.chestCm!! / waist > (if (female) 1.5 else 1.55))
            ) {
                withheld["Chest"] = withheld["Chest"] ?: "Out of range"
                out = out.copy(chestCm = null)
            }
            if (bad(out.armCm, 0.13, 0.28)) {
                withheld["Arm"] = withheld["Arm"] ?: "Out of range"
                out = out.copy(armCm = null)
            }
            if (bad(out.thighCm, 0.24, 0.44)) {
                withheld["Thigh"] = withheld["Thigh"] ?: "Out of range"
                out = out.copy(thighCm = null)
            }
            if (bad(out.calfCm, 0.17, 0.27)) {
                withheld["Calf"] = withheld["Calf"] ?: "Out of range"
                out = out.copy(calfCm = null)
            }
            val hipOverWaist = if (female) 1.65 else 1.35
            if (bad(out.hipCm, 0.42, 0.75) ||
                (waist != null && out.hipCm != null && out.hipCm!! / waist > hipOverWaist)
            ) {
                withheld["Hip"] = if (PhotoIssue.LEGS_COVERED in issues) "Clothing" else "Out of range"
                out = out.copy(hipCm = null)
            }
        }

        return GirthCheck(out, withheld, issues, untrusted)
    }

    private fun torsoSpan(pose: FrontPoseGeometry): Double? {
        val shoulderY = (pose.shoulderLeft.y + pose.shoulderRight.y) / 2.0
        val hipY = (pose.hipLeft.y + pose.hipRight.y) / 2.0
        return (hipY - shoulderY).takeIf { it > 0.0 }
    }
}
