package com.squeeze.core.bodycomp

import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.Sex

/**
 * One rung on the visual ladder.
 *
 * @param percent the midpoint this band represents
 * @param label what the band is called
 * @param markers the observable features that distinguish it, in the order someone
 *   actually checks them
 */
data class VisualBand(
    val percent: Double,
    val label: String,
    val markers: String,
)

/**
 * Body fat estimated by matching the user against described appearance bands.
 *
 * This exists because it fails in a completely different way from everything else the app
 * does, and that independence is worth more than its accuracy.
 *
 * Every other method here runs on circumferences. A photo scan and a tape reading of the
 * same waist are the same measurement taken two ways, so when the scan's scale is wrong they
 * are wrong together, and [MethodFusion] combining them narrows the interval around a number
 * that is confidently off. Nothing about a waist in centimetres tells the user their abs are
 * visible. Appearance does, it is the one input the scan cannot corrupt, and it is the thing
 * the user can actually see.
 *
 * Its accuracy is real but modest: self-assessment against reference bands lands within
 * about five points, better than a BMI estimate and worse than a careful tape. Its
 * repeatability is better than that figure suggests, because someone who picks a band today
 * picks the same one next month unless something changed — which is exactly the property the
 * trend filter cares about.
 *
 * **Described rather than illustrated, deliberately.** Reference photographs are of specific
 * strangers with specific frames, and a lean-but-narrow user matching themselves against a
 * muscular 10% photo reads high every time. The markers below are what an assessor actually
 * looks at, they apply to any build, and they ship as text — no images, no licensing, no
 * megabytes.
 */
object VisualAssessment {

    /**
     * Male bands.
     *
     * Anchored on abdominal definition, which is the feature that changes most predictably
     * with fat in men and the one people can judge on themselves in a mirror.
     */
    private val MALE = listOf(
        VisualBand(
            percent = 8.0,
            label = "Very lean",
            markers = "Abs clearly separated and visible without flexing. Veins across the " +
                "abdomen and shoulders. Little to nothing to pinch at the waist.",
        ),
        VisualBand(
            percent = 12.0,
            label = "Lean",
            markers = "Abs visible relaxed, though the lower two are softer. Some vascularity " +
                "in the arms. A clear line where the hip meets the waist.",
        ),
        VisualBand(
            percent = 15.0,
            label = "Fit",
            markers = "Upper abs show when flexed but not relaxed. Waist still narrower than " +
                "the ribcage. A soft layer over the lower stomach.",
        ),
        VisualBand(
            percent = 20.0,
            label = "Average",
            markers = "No visible abs. The stomach is flat to slightly rounded standing, and " +
                "there is a definite fold to pinch at the waist.",
        ),
        VisualBand(
            percent = 25.0,
            label = "Above average",
            markers = "Stomach protrudes a little past the chest. The waist is wider than it " +
                "was at the ribs. Some softness at the chest.",
        ),
        VisualBand(
            percent = 30.0,
            label = "High",
            markers = "Clearly rounded stomach, waist noticeably wider than the chest, fat " +
                "visible on the upper back and chest.",
        ),
        VisualBand(
            percent = 35.0,
            label = "Very high",
            markers = "The stomach is the widest part of the body by a clear margin, with " +
                "fat carried on the arms and legs as well.",
        ),
    )

    /**
     * Female bands.
     *
     * Not the male scale relabelled. Women carry essential fat in the breasts, hips and
     * thighs that men do not, so the same appearance corresponds to a percentage roughly ten
     * points higher, and abdominal definition arrives far lower down the scale. A single
     * shared ladder would tell every woman she is obese, which is a modelling error the app
     * has no excuse for making.
     */
    private val FEMALE = listOf(
        VisualBand(
            percent = 14.0,
            label = "Very lean",
            markers = "Abs visible and separated, with striations across the shoulders. Below " +
                "the range most women can hold without their cycle being affected.",
        ),
        VisualBand(
            percent = 18.0,
            label = "Lean",
            markers = "Abs visible relaxed, hips and thighs clearly defined, some vascularity " +
                "in the arms.",
        ),
        VisualBand(
            percent = 22.0,
            label = "Fit",
            markers = "A flat stomach with some definition when flexed. Clear separation " +
                "between waist and hip. Curves at the hip and thigh remain.",
        ),
        VisualBand(
            percent = 25.0,
            label = "Average",
            markers = "Stomach mostly flat with a soft layer, no visible definition. Waist " +
                "clearly narrower than the hips.",
        ),
        VisualBand(
            percent = 30.0,
            label = "Above average",
            markers = "A rounded lower stomach, fuller hips and thighs, softer upper arms.",
        ),
        VisualBand(
            percent = 35.0,
            label = "High",
            markers = "The stomach is rounded standing, and the waist is much less distinct " +
                "from the hips.",
        ),
        VisualBand(
            percent = 42.0,
            label = "Very high",
            markers = "Fat clearly carried across the stomach, hips, thighs and upper arms, " +
                "with the waist no longer defined.",
        ),
    )

    fun bandsFor(sex: Sex): List<VisualBand> = if (sex == Sex.FEMALE) FEMALE else MALE

    /**
     * Turns a chosen band into an estimate the fusion can use.
     *
     * @param percent the band's midpoint, as picked by the user
     * @return null when the value is outside anything the bands describe, rather than
     *   clamping — a figure that far out means something other than a considered choice
     */
    fun estimate(percent: Double, sex: Sex): BodyFatEstimate? {
        val bands = bandsFor(sex)
        val low = bands.first().percent - BAND_MARGIN
        val high = bands.last().percent + BAND_MARGIN
        if (percent !in low..high) return null

        return BodyFatEstimate(
            percent = percent,
            method = EstimationMethod.VISUAL_ASSESSMENT,
            standardErrorPercent = EstimationMethod.VISUAL_ASSESSMENT.standardErrorPercent,
        )
    }

    /**
     * The band a measured percentage falls in, so the app can show the user where a number
     * puts them in terms they can check in a mirror.
     *
     * This is the direction that catches a bad scan. A scan reporting twenty per cent on
     * someone with visible abs is not a small error to be averaged away — the two disagree
     * about something the user can settle by looking down.
     */
    fun bandFor(percent: Double, sex: Sex): VisualBand =
        bandsFor(sex).minBy { kotlin.math.abs(it.percent - percent) }

    /** How far past the end bands a value may sit and still be treated as a choice. */
    private const val BAND_MARGIN = 6.0
}
