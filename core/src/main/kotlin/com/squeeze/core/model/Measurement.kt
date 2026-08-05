package com.squeeze.core.model

/**
 * How a set of circumferences was obtained. Recorded per measurement because mixing
 * sources silently is the single fastest way to corrupt a trend line: a tape reading and
 * a photo estimate can differ by more than the change the user is trying to detect.
 *
 * The trend engine treats each source as having its own measurement noise, so provenance
 * has to survive into storage.
 */
enum class MeasurementSource {
    /** Hand measurement with a tape. Lowest noise when tension is controlled. */
    TAPE,

    /** Derived from silhouette extraction. Noise depends on scale recovery quality. */
    PHOTO,

    /** Silhouette extraction from a front photograph alone, with depth assumed. */
    PHOTO_FRONT_ONLY,

    /** Bioimpedance scale, imported via Health Connect. Highly hydration sensitive. */
    BIA_SCALE,

    /** DEXA, BodPod or hydrostatic weighing. Treated as ground truth for calibration. */
    REFERENCE_SCAN,
}

/**
 * Body circumferences in centimetres. All are optional because different equations need
 * different subsets, and the app must not force a user to measure sites it will not use.
 */
data class Circumferences(
    val neckCm: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val chestCm: Double? = null,
    val thighCm: Double? = null,
    val armCm: Double? = null,
    val calfCm: Double? = null,
)

/** Skinfold thicknesses in millimetres, for the Jackson-Pollock 3-site equations. */
data class Skinfolds(
    val chestMm: Double? = null,
    val abdomenMm: Double? = null,
    val thighMm: Double? = null,
    val tricepsMm: Double? = null,
    val suprailiacMm: Double? = null,
)

/**
 * One measurement session.
 *
 * @param epochDay days since the Unix epoch; the trend engine needs a monotonic time axis
 *   and this keeps [Measurement] free of platform date types so it stays testable on the JVM.
 * @param weightKg bodyweight at the time of measurement
 * @param referenceBodyFatPercent a scan result the user entered by hand. When present this
 *   measurement can anchor [com.squeeze.core.bodycomp.PersonalCalibration].
 */
data class Measurement(
    val epochDay: Long,
    val source: MeasurementSource,
    val weightKg: Double? = null,
    val circumferences: Circumferences = Circumferences(),
    val skinfolds: Skinfolds = Skinfolds(),
    val referenceBodyFatPercent: Double? = null,
    val note: String? = null,
)

/**
 * A body-fat estimate together with how much to trust it.
 *
 * @param percent estimated body fat, 0-100
 * @param standardErrorPercent the published standard error of estimate for [method],
 *   in absolute percentage points. This is what feeds the trend engine's measurement
 *   noise and what the UI must show alongside the number. Presenting an estimate without
 *   its error is the core dishonesty of this app category.
 * @param calibrated true when a personal reference scan has been applied
 */
data class BodyFatEstimate(
    val percent: Double,
    val method: EstimationMethod,
    val standardErrorPercent: Double,
    val calibrated: Boolean = false,
)

/**
 * Supported estimation equations and their two quite different error figures.
 *
 * Keeping these separate is the central idea of this app, so it is worth stating plainly:
 *
 *  - [standardErrorPercent] is **accuracy**: how far this method's estimate sits from a
 *    criterion measurement like DEXA. It is dominated by a systematic, person-specific
 *    offset, which is why [com.squeeze.core.bodycomp.PersonalCalibration] can remove most
 *    of it from a single reference scan. Use it when displaying an absolute number.
 *
 *  - [repeatabilityPercent] is **precision**: the random scatter between two measurements
 *    of an unchanged body. Use it for anything about *change over time*.
 *
 * A systematic offset is constant, so it cancels out when comparing a user against
 * themselves. Feeding accuracy into the trend filter would make a real 0.3%/week cut
 * statistically undetectable over three months, even though the user can see it in the
 * mirror. Precision is what determines whether a trend is readable, and precision is far
 * better than accuracy for every method here. That gap is the whole product.
 */
enum class EstimationMethod(
    val standardErrorPercent: Double,
    val repeatabilityPercent: Double,
    val displayName: String,
) {
    /** Hodgdon-Beckett circumference equations, as used by the US Navy. */
    NAVY_CIRCUMFERENCE(3.5, 0.5, "Tape (Navy)"),

    /**
     * Jackson-Pollock 3-site skinfolds, converted through the Siri equation.
     * Scatters more than tape because caliper pinch depth is harder to reproduce.
     */
    JACKSON_POLLOCK_3(3.5, 0.8, "Skinfold (JP3)"),

    /**
     * Deurenberg BMI-based estimate. Least accurate method by a wide margin, yet highly
     * repeatable, because a scale reading is precise even when what it implies is wrong.
     * A clean illustration of why the two figures must not be conflated.
     */
    DEURENBERG_BMI(4.5, 0.2, "BMI estimate"),

    /** Circumferences recovered from silhouette extraction, then fed to the Navy equation. */
    PHOTO_SILHOUETTE(4.0, 0.6, "Photo scan"),

    /**
     * A front photograph only, with sagittal depth assumed from population ratios.
     *
     * Accuracy is markedly worse than a two-photo scan, because the assumed depth is wrong
     * for any individual. Repeatability is barely affected, because it is wrong by the same
     * amount every time — which is why this is still worth offering: it tracks change
     * almost as well while being far easier to capture, and personal calibration removes
     * the constant offset entirely.
     */
    PHOTO_FRONT_ONLY(5.5, 0.7, "Photo scan (front only)"),

    /**
     * A directly entered DEXA/BodPod result. The most accurate input available, but
     * repeat scans disagree by more than a careful tape measurement does, so it anchors
     * absolute level without being the best signal for week-to-week change.
     */
    REFERENCE_SCAN(1.5, 1.0, "Reference scan"),

    /**
     * The user matching themselves against described appearance bands.
     *
     * Worth its place despite being the least precise method here, because it is the only
     * one that does not run on circumferences. Every other route to a number shares the
     * scan's scale error, so fusing them narrows the interval around a figure that may be
     * confidently wrong; appearance cannot inherit that error at all. Its repeatability is
     * better than its accuracy suggests, since someone who picks a band today picks the
     * same one next month unless something actually changed.
     */
    VISUAL_ASSESSMENT(5.0, 1.8, "Visual match"),

    /**
     * Body fat read from the silhouette's proportions, without converting to centimetres.
     *
     * The one photo method that scale recovery cannot corrupt. Every other route through a
     * photograph divides a pixel width by the body's pixel height and multiplies by the
     * stated stature, which makes all of them hostage to the mask's top and bottom rows;
     * a ratio of two widths in the same image divides that error out exactly.
     *
     * Coarser than a measured girth, because two people with the same outline can differ by
     * several points depending on the muscle beneath it — hence the wide interval, which has
     * to stay wide or a silhouette would outvote a tape. Its value is not its accuracy but
     * its independence: it is the only estimate that can disagree with the others for a
     * reason, and that disagreement is how the app detects a broken scan at all.
     */
    PHOTO_SHAPE(6.0, 1.2, "Photo shape"),
}
