package com.squeeze.core.scan

import kotlin.math.abs
import kotlin.math.max

/**
 * Where the scan's centimetres-per-pixel came from.
 *
 * Recorded rather than inferred, because the two sources fail in completely different ways
 * and a user looking at a suspect number deserves to know which one produced it.
 */
enum class ScaleSource {
    /**
     * The silhouette's own vertical extent: crown of the head to the soles of the feet.
     *
     * The best available reference when the mask is clean, because those two rows *are* the
     * stature the user typed in — no anthropometric constant sits in between.
     */
    MASK,

    /**
     * Pose landmarks, via [LandmarkStature].
     *
     * Less direct, but immune to the failure that makes the mask dangerous: a segmenter can
     * decide a mirror frame, a shadow or a towel on the door is part of the person, and a
     * pose model will not put a nose on a door frame.
     */
    LANDMARK,

    /**
     * Pose landmarks again, but from the trunk alone, via
     * [LandmarkStature.frameFractionFromTrunk].
     *
     * The last resort, for a photograph with no feet in it. There is no cross-check available
     * here — the mask's vertical extent is not stature when the body runs off the frame — so
     * this is a single soft estimate where the others are corroborated ones, and everything
     * built on it has to widen to say so.
     *
     * It exists because refusing to scale is not the safe option it looks like. A scan with no
     * scale has no circumferences, a scan with no circumferences never runs the Navy equation,
     * and what is then left in the fusion is the outline's bound — nine points wide and the
     * same value for every body that reaches it.
     */
    TRUNK_SPAN,
}

/**
 * The scale the scan should use, and how much the two estimates of it disagreed.
 *
 * @param bodyHeightFraction fraction of the frame height the subject's full stature spans
 * @param disagreementPercent how far apart the mask and landmark estimates were, relative to
 *   the larger of the two, or null when only one estimate existed
 */
data class ScaleDecision(
    val bodyHeightFraction: Double,
    val source: ScaleSource,
    val disagreementPercent: Double?,
)

/**
 * Stature inferred from pose landmarks rather than from the silhouette.
 *
 * A pose model reports joints, not the crown of the head, so it cannot measure stature
 * directly. What it can do is measure a span whose proportion to stature is stable across
 * adults, and the nose-to-ankle span is the best one available from the landmark set this
 * app already computes: both ends are landmarks the model locates confidently in a standing
 * front-on photo, and the span covers most of the body, so the relative error in it is small.
 *
 * The constant is an anthropometric average, and it is worth being explicit about what that
 * costs. A person whose head is proportionally larger than average gets a stature estimate a
 * percent or two off — **every time, in the same direction**. That is a systematic offset,
 * not scatter, and this app's entire premise is that a systematic offset cancels when you
 * compare yourself to yourself. What ruins a trend is a number that jumps between scans, and
 * this estimate is stable precisely because it does not depend on what the segmenter thought
 * about the background.
 */
object LandmarkStature {

    /**
     * Nose tip to lateral malleolus, as a fraction of standing height.
     *
     * From the standard segment tables: the nose tip sits near 0.925 of stature and the ankle
     * landmark near 0.039, leaving 0.886 between them. Adult variation around this is roughly
     * two percent — small next to the errors it is being used to catch, which are twenty.
     */
    const val NOSE_TO_ANKLE_FRACTION = 0.886

    /**
     * Nose tip to hip joint, as a fraction of standing height.
     *
     * **Why a second, worse span exists.** The one above needs ankles, and a photograph framed
     * on the trunk does not have them. That framing is not a mistake — it is the one the shape
     * reading actually wants, because it puts far more pixels on the midsection — and the app
     * recommends it. But with no ankles there is no stature, with no stature there is no
     * scale, with no scale there are no circumferences, and with no circumferences the Navy
     * equation never runs. What is then left in the fusion is the outline's bound, which is a
     * constant. A user photographed exactly as instructed was shown 11.6% and told his scan
     * could not resolve his photo; the reason was that his feet were out of shot.
     *
     * From the same segment tables as the constant above: the nose tip sits near 0.925 of
     * stature and the hip joint near 0.530, leaving 0.395 between them.
     *
     * **It is the weaker estimate and the numbers say by how much.** The span is 0.395 of
     * stature against 0.886, so the same landmark error counts for 2.2 times as much, and
     * trunk-to-stature proportion varies more between adults than whole-body proportion does
     * — call the result five per cent rather than the two the ankle span carries.
     *
     * That is worth paying, and it is worth paying *because the cost is computable*. Run five
     * per cent through the density equation and it is 1.8 points of body fat for a man, 3.1
     * for a woman. Even fifteen per cent is 5.2 and 9.0. The figure it replaces carries nine
     * points and is the same value for every body that reaches it — so on the worst plausible
     * scale error this is still no worse, and unlike the constant it moves when the body does.
     * [com.squeeze.core.bodycomp.BodyFatCalculator.navyScaleSensitivityPercent] computes it
     * from the equation's own coefficients rather than from this comment.
     *
     * **It may not print centimetres.** A scale this soft multiplies every girth in the scan
     * by the same wrong number, which is the failure this whole file exists to prevent — two
     * photographs of one body giving waists of 75.4 cm and 92.2 cm. A body-fat percentage
     * survives it because the equation takes a logarithm of one girth difference and the error
     * enters once, bounded, and quantified above. A tape reading does not survive it at all.
     */
    const val NOSE_TO_HIP_FRACTION = 0.395

    /**
     * Stature from the trunk alone, for a photograph with no ankles in it.
     *
     * @return null when the nose or both hips are missing, on the same principle as the ankle
     *   span: a half-present landmark set is where a fabricated scale does the most damage.
     */
    fun frameFractionFromTrunk(
        nose: PosePoint?,
        hipLeft: PosePoint?,
        hipRight: PosePoint?,
    ): Double? {
        if (nose == null) return null

        // The mean of the two, unlike the ankles above. One foot forward lifts an ankle in
        // the image, so the lower one is the one on the floor; the hip joints of a standing
        // adult sit at the same height and the pair averages out landmark noise instead.
        val hips = listOfNotNull(hipLeft?.y, hipRight?.y)
        if (hips.isEmpty()) return null
        val hipY = hips.sum() / hips.size

        val span = hipY - nose.y
        if (span <= 0.0) return null

        val stature = span / NOSE_TO_HIP_FRACTION
        return stature.takeIf { it in 0.05..1.6 }
    }

    /**
     * Full stature as a fraction of the frame height.
     *
     * @return null when the nose or both ankles are missing. Absent rather than guessed: a
     *   half-present landmark set is exactly the case where a fabricated scale would be worst.
     */
    fun frameFraction(
        nose: PosePoint?,
        ankleLeft: PosePoint?,
        ankleRight: PosePoint?,
    ): Double? {
        if (nose == null) return null

        // The lower ankle, not the average. One foot forward is a normal way to stand and it
        // lifts that ankle in the image; the foot bearing weight is the one on the floor.
        val ankleY = listOfNotNull(ankleLeft?.y, ankleRight?.y).maxOrNull() ?: return null

        val span = ankleY - nose.y
        if (span <= 0.0) return null

        val stature = span / NOSE_TO_ANKLE_FRACTION
        // The pose model extrapolates landmarks past the frame edge, so a nonsensical span is
        // reachable. Anything outside this cannot be a standing adult in shot.
        return stature.takeIf { it in 0.05..1.6 }
    }
}

/**
 * Decides which stature estimate to scale by, and when to refuse to scale at all.
 *
 * This exists because of a measured failure, not a hypothetical one: two photographs of the
 * same body on the same day produced waists of 75.4 cm and 92.2 cm. Anatomical sites do not
 * move by 22% in an afternoon, and the level finder was choosing sensible rows in both. The
 * error was upstream of everything — in the conversion from image fractions to centimetres,
 * which multiplies *every* measurement in the scan by the same wrong number.
 *
 * A silhouette's top and bottom row are the whole basis of that conversion, and they are the
 * least robust thing in the pipeline. The segmenter is trained to find people, but a mirror
 * frame, a shadow under the feet, a dark doorway or a towel on a rail all attach themselves
 * to the mask readily. Each one moves `topRow` or `bottomRow`, the body appears to span more
 * of the frame than it does, and every circumference shrinks or grows in proportion.
 *
 * Landmarks do not have that failure mode. So the two are computed independently and
 * compared, and the size of their disagreement decides what happens:
 *
 *  - **Close agreement** — the mask is clean, so use it. It measures the real crown and sole
 *    with no anthropometric constant in the way, which makes it the more accurate of the two.
 *  - **Moderate disagreement** — something is attached to the mask. Fall back to the landmark
 *    estimate and warn: it is the less precise reference but the only one still describing a
 *    person.
 *  - **Severe disagreement** — neither can be trusted. Something is badly wrong with the
 *    photo, and a scan that silently picks one would produce a full set of confident, wrong
 *    numbers. Refuse instead.
 */
object ScaleCrossCheck {

    /**
     * Below this the two estimates are as close as their own methods allow.
     *
     * Sized from what honest disagreement looks like: the landmark constant carries about two
     * percent of person-to-person variation, landmark placement adds a little, and the mask's
     * own edges move by a row or two. Six percent is comfortably above all of that and far
     * below a mask that has swallowed part of the room.
     */
    const val MASK_TRUSTED_DISAGREEMENT = 0.06

    /**
     * Above this, no fallback is honest.
     *
     * A fifth of the subject's height is not a mask edge being slightly generous — it is the
     * mask describing something that is not the person, or the pose model having placed
     * landmarks on the wrong body. Either way the photo needs retaking, and the app should
     * say so rather than quietly measure the more plausible of two bad references.
     */
    const val MAX_DISAGREEMENT = 0.20

    /**
     * @param maskFraction stature as measured by the silhouette's vertical extent
     * @param landmarkFraction the same quantity from [LandmarkStature], or null when the
     *   landmarks needed for it were not found
     * @return the scale to use, or null when the two references disagree so badly that
     *   neither should be trusted
     */
    fun resolve(maskFraction: Double, landmarkFraction: Double?): ScaleDecision? {
        // No second opinion available. The mask is what there is, and refusing every scan
        // whose ankles the model could not place would reject good photos to guard against
        // a fault we have no evidence of here.
        if (landmarkFraction == null) {
            return ScaleDecision(maskFraction, ScaleSource.MASK, disagreementPercent = null)
        }

        // Relative to the larger, so contamination — which only ever inflates the mask — is
        // measured against the inflated figure and the ratio stays bounded by 1.
        val larger = max(maskFraction, landmarkFraction)
        if (larger <= 0.0) return null

        val disagreement = abs(maskFraction - landmarkFraction) / larger

        return when {
            disagreement > MAX_DISAGREEMENT -> null

            disagreement > MASK_TRUSTED_DISAGREEMENT -> ScaleDecision(
                bodyHeightFraction = landmarkFraction,
                source = ScaleSource.LANDMARK,
                disagreementPercent = disagreement * 100.0,
            )

            else -> ScaleDecision(
                bodyHeightFraction = maskFraction,
                source = ScaleSource.MASK,
                disagreementPercent = disagreement * 100.0,
            )
        }
    }
}
