package com.squeeze.core.scan

import com.squeeze.core.model.Circumferences
import kotlin.math.abs

/**
 * A proportion worth reporting, with the reading that makes it meaningful.
 *
 * Every ratio here carries its own interpretation because a bare number is useless to
 * anyone who does not already know the literature — "0.48" means nothing; "just above the
 * 0.5 threshold" is actionable.
 */
data class Proportion(
    val name: String,
    val value: Double,
    val interpretation: String,
    /** True when this sits outside the range usually considered healthy or balanced. */
    val flagged: Boolean = false,
)

/**
 * Ratios derived from circumferences.
 *
 * Ratios are the part of a photo scan that survives its own inaccuracy. A scan's absolute
 * centimetres depend on getting scale right, and scale depends on an honestly entered
 * height and a well-framed shot. A *ratio* divides two measurements taken from the same
 * photograph at the same scale, so the scale error cancels: a waist-to-hip ratio is right
 * even when both numbers are five percent large.
 *
 * That makes these the most trustworthy output of the whole pipeline, which is why they are
 * computed and shown rather than left as an exercise for the user.
 */
object BodyProportions {

    /**
     * @param heightCm needed for waist-to-height, the one ratio that reaches outside the
     *   photograph and therefore the one that does depend on scale being right
     */
    fun analyse(circumferences: Circumferences, heightCm: Double?): List<Proportion> = buildList {
        val waist = circumferences.waistCm
        val hip = circumferences.hipCm
        val chest = circumferences.chestCm
        val neck = circumferences.neckCm

        if (waist != null && hip != null && hip > 0) {
            val whr = waist / hip
            add(
                Proportion(
                    name = "Waist-to-hip",
                    value = whr,
                    interpretation = when {
                        whr < 0.80 -> "Low — fat distribution is weighted toward the hips."
                        whr < 0.90 -> "Moderate — a typical adult distribution."
                        whr < 1.00 -> "Elevated — more fat carried around the middle."
                        else -> "High — strongly central fat distribution."
                    },
                    flagged = whr >= 0.95,
                ),
            )
        }

        if (waist != null && heightCm != null && heightCm > 0) {
            val whtr = waist / heightCm
            add(
                Proportion(
                    name = "Waist-to-height",
                    value = whtr,
                    // This ratio predicts cardiometabolic risk better than BMI and needs no
                    // weight at all, which makes it unusually well suited to a photo scan.
                    interpretation = when {
                        whtr < 0.40 -> "Below the usual range — worth checking against a tape."
                        whtr < 0.50 -> "Within the range generally considered healthy."
                        whtr < 0.60 -> "Above the 0.5 guideline often used as a threshold."
                        else -> "Well above the 0.5 guideline."
                    },
                    flagged = whtr >= 0.50,
                ),
            )
        }

        if (chest != null && waist != null && waist > 0) {
            val taper = chest / waist
            add(
                Proportion(
                    name = "Chest-to-waist",
                    value = taper,
                    interpretation = when {
                        taper < 1.10 -> "Narrow taper — chest and waist are close in size."
                        taper < 1.25 -> "Moderate taper."
                        taper < 1.45 -> "Pronounced taper."
                        else -> "Very pronounced taper."
                    },
                ),
            )
        }

        if (neck != null && waist != null && neck > 0) {
            add(
                Proportion(
                    name = "Waist-to-neck",
                    value = waist / neck,
                    // The Navy equation is driven by exactly this gap, so surfacing it shows
                    // the user which of their measurements is moving their body-fat number.
                    interpretation = "The gap the body-fat equation is built on — it moves " +
                        "when your waist changes, not when your neck does.",
                ),
            )
        }
    }
}

/** One side-to-side comparison. */
data class SymmetryFinding(
    val site: ScanSite,
    val leftCm: Double,
    val rightCm: Double,
) {
    val differenceCm: Double get() = abs(leftCm - rightCm)

    val percentDifference: Double
        get() {
            val mean = (leftCm + rightCm) / 2.0
            return if (mean > 0) differenceCm / mean * 100.0 else 0.0
        }

    /**
     * Whether the difference exceeds what measurement noise alone explains.
     *
     * Set generously. Genuine limb asymmetry of a few percent is normal in almost everyone,
     * and a silhouette measured from one photograph cannot resolve small differences —
     * a slight rotation toward the camera produces one on its own. Flagging noise as a
     * finding would send people chasing an imbalance that is not there.
     */
    val notable: Boolean get() = percentDifference >= NOTABLE_PERCENT

    private companion object {
        const val NOTABLE_PERCENT = 5.0
    }
}

/**
 * Left-versus-right comparison.
 *
 * Useful to lifters for a real reason: a persistent limb difference shows up in single-side
 * strength and, left alone, in injury. It is also the measurement most easily faked by
 * standing slightly turned, so the threshold is deliberately loose and the copy says so.
 */
object SymmetryAnalysis {

    fun analyse(findings: Map<ScanSite, Pair<Double, Double>>): List<SymmetryFinding> =
        findings.map { (site, sides) -> SymmetryFinding(site, sides.first, sides.second) }
            .sortedByDescending { it.percentDifference }
}
