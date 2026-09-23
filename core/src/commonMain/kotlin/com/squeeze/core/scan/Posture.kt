package com.squeeze.core.scan

import com.squeeze.core.math.toDegrees
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Normalised landmark positions from a front-facing photograph.
 *
 * Coordinates are 0..1 fractions of the frame, as the pose model reports them, so nothing
 * here depends on resolution or on scale recovery working.
 */
data class PosePoint(val x: Double, val y: Double)

data class FrontPoseGeometry(
    val shoulderLeft: PosePoint,
    val shoulderRight: PosePoint,
    val hipLeft: PosePoint,
    val hipRight: PosePoint,
    val ankleLeft: PosePoint? = null,
    val ankleRight: PosePoint? = null,
    /**
     * The nose tip.
     *
     * Not used for posture — it is here because it is the upper end of the only span this
     * landmark set offers that covers most of the body, which is what [LandmarkStature] needs
     * to check the silhouette's idea of the subject's height against a second opinion.
     */
    val nose: PosePoint? = null,
    /**
     * Midpoint of the mouth landmarks, which is what the neck band is measured down from.
     *
     * Here because a trunk-framed scan had to invent it and invented it wrong. [TorsoFraming]
     * placed the chin a quarter of the trunk span above the shoulders, which on a real
     * photograph landed *below* the jaw and put the neck search on the trapezius. The neck
     * then measured wider than it is, `waist − neck` collapsed, and the Navy equation returned
     * nothing — so a scan that had measured a waist and a chest still reported the outline's
     * constant and told its user the photograph could not resolve him.
     *
     * [AnatomicalLevelFinder]'s own comment predicted it: the trapezius flare "is how a 175 cm
     * man ends up with a 52 cm neck, which drives the Navy equation to a negative body fat and
     * so produces no estimate at all". The full-body path always used this landmark. The trunk
     * path could not reach it.
     */
    val mouth: PosePoint? = null,
    /**
     * Elbows, wrists and knees, for [PhysiqueRegions]: the arms and legs are judged on crops
     * of their own, and a crop of an arm needs to know where the arm is. Null when the pose
     * model did not report them.
     */
    val elbowLeft: PosePoint? = null,
    val elbowRight: PosePoint? = null,
    val wristLeft: PosePoint? = null,
    val wristRight: PosePoint? = null,
    val kneeLeft: PosePoint? = null,
    val kneeRight: PosePoint? = null,
)

/** One postural observation, in degrees, with the reading that makes it usable. */
data class PostureFinding(
    val name: String,
    val degrees: Double,
    val interpretation: String,
    val notable: Boolean,
)

/**
 * Alignment read from the pose landmarks a scan already produces.
 *
 * The pose model runs anyway to locate the search bands, and its output contains shoulder
 * and hip positions that were being discarded. Reading tilt from them costs nothing extra
 * and gives back something a tape measure cannot: whether the two sides sit level.
 *
 * The caveats are real and are stated wherever this is shown. A single photograph cannot
 * separate a genuine postural asymmetry from standing with weight on one leg, and cannot
 * see rotation toward the camera at all. Thresholds are therefore set where a difference is
 * large enough to be worth a second look, not where it is proven.
 */
object PostureAnalysis {

    /** Below this, a tilt is indistinguishable from how someone happened to stand. */
    private const val NOTABLE_DEGREES = 3.0

    /**
     * @param hipsObserved false when the hips are outside the photograph and their landmarks
     *   were inferred rather than seen. Everything downstream of the hip line is then dropped
     *   rather than reported at lower confidence: a pose model asked where the hips are in a
     *   picture that does not contain them returns its prior, and a prior is level by
     *   construction. Printing "hips sit level" from it would be the app inventing a finding
     *   about a part of the body it never looked at — the same fault as reporting a
     *   height-and-weight figure as a scan result.
     */
    fun analyse(
        geometry: FrontPoseGeometry,
        hipsObserved: Boolean = true,
    ): List<PostureFinding> = buildList {
        val shoulderTilt = tiltDegrees(geometry.shoulderLeft, geometry.shoulderRight)
        add(
            PostureFinding(
                name = "Shoulder level",
                degrees = shoulderTilt,
                interpretation = describeTilt(
                    tilt = shoulderTilt,
                    level = "Shoulders sit level.",
                    tilted = "One shoulder sits higher. Common, and often just how you " +
                        "stood — worth a look if it repeats across scans.",
                ),
                notable = abs(shoulderTilt) >= NOTABLE_DEGREES,
            ),
        )

        if (!hipsObserved) return@buildList

        val hipTilt = tiltDegrees(geometry.hipLeft, geometry.hipRight)
        add(
            PostureFinding(
                name = "Hip level",
                degrees = hipTilt,
                interpretation = describeTilt(
                    tilt = hipTilt,
                    level = "Hips sit level.",
                    tilted = "One hip sits higher. Most often weight resting on one leg; " +
                        "persistent tilt across scans is worth attention.",
                ),
                notable = abs(hipTilt) >= NOTABLE_DEGREES,
            ),
        )

        // A shoulder line and hip line tilted opposite ways is a different thing from both
        // tilting together, which is usually just stance.
        if (abs(shoulderTilt) >= NOTABLE_DEGREES &&
            abs(hipTilt) >= NOTABLE_DEGREES &&
            shoulderTilt * hipTilt < 0
        ) {
            add(
                PostureFinding(
                    name = "Counter-rotation",
                    degrees = abs(shoulderTilt) + abs(hipTilt),
                    interpretation = "Shoulders and hips are tilted in opposite directions. " +
                        "If this repeats across scans it is worth raising with a physio.",
                    notable = true,
                ),
            )
        }
    }

    /**
     * Signed angle of the line between two points, in degrees.
     *
     * Positive means the left-hand point sits higher in the image. Image y grows downward,
     * so the sign is flipped to match how a person would describe it.
     */
    fun tiltDegrees(left: PosePoint, right: PosePoint): Double {
        val dx = right.x - left.x
        val dy = right.y - left.y
        if (dx == 0.0 && dy == 0.0) return 0.0
        return atan2(dy, dx).toDegrees()
    }

    private fun describeTilt(tilt: Double, level: String, tilted: String): String =
        if (abs(tilt) < NOTABLE_DEGREES) level else tilted
}
