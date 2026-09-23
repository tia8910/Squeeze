package com.squeeze.app.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.squeeze.core.scan.FrontPoseGeometry
import com.squeeze.core.scan.FrontalityCheck
import com.squeeze.core.scan.LandmarkStature
import com.squeeze.core.scan.NeckOutcome
import com.squeeze.core.scan.NeckReading
import com.squeeze.core.scan.NeckRefusal
import com.squeeze.core.scan.PoseAnchors
import com.squeeze.core.scan.PosePoint
import com.squeeze.core.scan.ScanFraming
import com.squeeze.core.scan.ScaleCrossCheck
import com.squeeze.core.scan.ScaleDecision
import com.squeeze.core.scan.ScaleSource
import com.squeeze.core.scan.TorsoFraming
import com.squeeze.core.scan.TrunkBounds
import com.squeeze.core.scan.UpperBodyFraming
import com.squeeze.core.scan.Uprightness
import com.squeeze.core.scan.WidthProfile
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

/** What a single photograph yielded, ready for the geometry in `:core`. */
data class DetectedBody(
    val profile: WidthProfile,
    val anchors: PoseAnchors,
    /**
     * Landmark positions for posture analysis.
     *
     * The pose model runs anyway to bound the anatomical searches, and its shoulder and hip
     * coordinates were being discarded. Keeping them costs nothing and yields something a
     * tape cannot give: whether the two sides sit level.
     */
    val geometry: FrontPoseGeometry? = null,
    /**
     * How the photo's pixels become centimetres, and which reference that came from.
     *
     * Carried out of detection rather than recomputed by the caller from
     * [WidthProfile.bodyHeightFraction], because the silhouette's own extent is exactly the
     * value that cannot be trusted on its own — see [ScaleCrossCheck].
     *
     * Null at [ScanFraming.TORSO], where the subject's stature is not in the photograph.
     * Nullable rather than defaulted, so that every path which turns pixels into
     * centimetres has to say out loud what it does when there is no scale to do it with.
     */
    val scale: ScaleDecision?,
    /** How much of the body the photo contains, and therefore what may be claimed from it. */
    val framing: ScanFraming = ScanFraming.FULL_BODY,
    /**
     * Quarter-turns clockwise applied to the photograph before it was measured.
     *
     * Surfaced so the caller can store the image the same way up as the numbers taken from
     * it. A record showing a sideways photograph next to a correct measurement reads as a
     * broken scan, and the user has no way to tell that it is not.
     */
    val quarterTurnsApplied: Int = 0,
    /**
     * The neck, as the part-segmentation model read it — see [com.squeeze.core.scan.BodyPartMap].
     *
     * Null whenever the photograph does not show bare neck skin between the chin and the
     * shoulders: a collar, a hood, a head out of frame. Null is an answer here, not a gap to
     * be filled; the silhouette's neck is not a weaker version of this measurement, it is a
     * different quantity that happened to be stored in the same field.
     */
    val neck: NeckReading? = null,
    /**
     * What share of the midsection is bare skin rather than clothing, 0.0 to 1.0.
     *
     * [com.squeeze.core.scan.AbdominalDefinition] scores shadow gradients across the abdomen
     * and cannot tell a waistband from a linea alba, so it will happily return a number for a
     * man in a t-shirt. This is what lets the caller know that number is fabric.
     */
    val bareAbdomenFraction: Double? = null,
    /**
     * Whether the part model ran and produced a mask at all.
     *
     * A different fact from [neck] being non-null, and the distinction matters: a mask that
     * was read and found no neck is the model's answer, and the silhouette's neck is not a
     * fallback for it. Without this flag the pipeline cannot tell "no model" from "the model
     * says there is no neck in this photograph", and on the second it went on to measure a
     * trapezius.
     */
    val partMaskRead: Boolean = false,
    /**
     * Why the part model found no neck, when it ran and found none.
     *
     * Named because each reason asks something different of the person holding the camera,
     * and the commonest — a chin resting on flexed shoulders, so that no neck is in the
     * picture at all — was being measured as a 54 cm neck for five releases.
     */
    val neckRefusal: NeckRefusal? = null,
)

/** Why a photo could not be measured. Each maps to advice the user can act on. */
sealed interface DetectionFailure {
    /** No person found. Usually framing, lighting, or the subject being out of shot. */
    data object NoPersonDetected : DetectionFailure

    /** A person was found but is cut off, so height cannot anchor the scale. */
    data object BodyNotFullyVisible : DetectionFailure

    /** Landmarks came back in an impossible arrangement, e.g. hips above shoulders. */
    data object PoseImplausible : DetectionFailure

    /** The segmentation mask was too sparse to measure. */
    data object SegmentationFailed : DetectionFailure

    /**
     * Not even the upper body is in the frame.
     *
     * The last of three framings rather than the first thing checked. A cropped photograph
     * is dangerous — it still fills the frame, so the scale step happily maps the user's full
     * height onto whatever fraction of them is showing and inflates every circumference —
     * but the answer to that is to stop converting to centimetres, which
     * [ScanFraming.TORSO] and [ScanFraming.UPPER_BODY] both do. This failure now means what
     * it says: the shoulders or the waist are not in the picture, so there is no width and
     * no denominator, and nothing can be read from it at any confidence.
     */
    data object BodyCropped : DetectionFailure

    /** The picked file could not be decoded into an image at all. */
    data object PhotoUnreadable : DetectionFailure

    /**
     * The silhouette and the pose landmarks disagree about how tall the subject appears.
     *
     * The most damaging failure there is, and the reason [ScaleCrossCheck] exists: scale
     * multiplies every measurement in the scan, so an outline that has absorbed a mirror
     * frame or a shadow does not produce one bad number, it produces a complete set of
     * plausible ones that are all wrong by the same factor. Refused rather than measured.
     */
    data object ScaleUnreliable : DetectionFailure

    /**
     * The subject is not square to the camera.
     *
     * A front-on width is only a width if the body faces the lens. Rotation foreshortens
     * every horizontal measurement while the assumed depth stays put, so the result is
     * wrong by an amount nothing downstream can detect. Mirror selfies are the usual cause
     * and the worst one: the phone is held out to one side and the body turns with it.
     *
     * @param advice what to change, phrased for the person holding the camera
     */
    data class NotFacingCamera(val advice: String) : DetectionFailure
}

sealed interface DetectionResult {
    data class Success(val body: DetectedBody) : DetectionResult
    data class Failure(val reason: DetectionFailure) : DetectionResult
}

/**
 * Finds a body in a photograph, entirely on-device.
 *
 * Three models run per image, each answering the question the other two cannot.
 *
 * The **pose landmarker** locates joints, which is what it is good at. The **selfie
 * segmenter** produces a body outline, which is reduced to a per-row width profile. The
 * division of labour between those two matters: a pose model cannot see soft tissue, so it
 * cannot find a natural waist, while an outline has no idea which part of a silhouette is a
 * waist. Together the joints bound the search and the silhouette decides the exact level.
 *
 * The **part segmenter** is the third, and it was added because those two together still
 * could not find a neck. An outline cannot separate a neck from the hair beside it or the
 * collar beneath it, and a pose model has no neck landmark at all — so `waist − neck` could
 * not be formed, the Navy equation returned null, and the app fell back to printing the
 * silhouette method's constant to every user who reached it. The part model labels each
 * pixel as background, hair, body-skin, face-skin, clothes or accessory, which turns that
 * unreadable band into bare skin below a face and above a pair of shoulders. See
 * [com.squeeze.core.scan.BodyPartMap].
 *
 * Nothing here touches the network — all three models ship inside the APK and the app holds
 * no INTERNET permission, so a body photo cannot leave the device. That is what makes
 * on-device inference the only form of AI this app is allowed: there is no endpoint to call
 * and no permission with which to call one, and the guarantee is enforced by the operating
 * system rather than by this comment.
 *
 * Not thread-safe: MediaPipe tasks hold native state. Callers should serialise access, and
 * must [close] to release the native handles.
 */
@Singleton
class BodyDetector @Inject constructor(
    private val context: Context,
) : Closeable {

    private var poseLandmarker: PoseLandmarker? = null
    private var segmenter: ImageSegmenter? = null

    /**
     * The part segmenter: six classes rather than one, and the only model here that can see
     * anatomy instead of outline. See [com.squeeze.core.scan.BodyPartMap].
     *
     * Separate from [segmenter] rather than replacing it. The binary selfie model traces the
     * body's outer edge at the photograph's own resolution and is what every width in the
     * scan is measured on; this one emits 256 square and is used for the questions an outline
     * cannot answer — where the face stops, which pixels are hair, which are cloth.
     */
    private var partSegmenter: ImageSegmenter? = null

    private fun ensureLoaded() {
        if (poseLandmarker == null) {
            poseLandmarker = PoseLandmarker.createFromOptions(
                context,
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(POSE_MODEL_ASSET)
                            .build(),
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(MIN_POSE_CONFIDENCE)
                    .build(),
            )
        }

        if (segmenter == null) {
            segmenter = ImageSegmenter.createFromOptions(
                context,
                ImageSegmenter.ImageSegmenterOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(SEGMENTER_MODEL_ASSET)
                            .build(),
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setOutputCategoryMask(true)
                    .setOutputConfidenceMasks(false)
                    .build(),
            )
        }

        if (partSegmenter == null) {
            partSegmenter = ImageSegmenter.createFromOptions(
                context,
                ImageSegmenter.ImageSegmenterOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(PART_SEGMENTER_MODEL_ASSET)
                            .build(),
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setOutputCategoryMask(true)
                    .setOutputConfidenceMasks(false)
                    .build(),
            )
        }
    }

    /**
     * Never throws. MediaPipe surfaces problems as native exceptions, and a scan that
     * crashes the app teaches the user nothing; a named failure tells them what to change.
     */
    fun detect(bitmap: Bitmap): DetectionResult = runCatching { detectUpright(bitmap) }
        .getOrElse { DetectionResult.Failure(DetectionFailure.SegmentationFailed) }

    /**
     * Detects, standing the photograph up first if it arrived lying down.
     *
     * The rotation is worked out from the body rather than from the file, because the file
     * frequently does not know: EXIF orientation is stripped by screenshots, by most share
     * sheets and by anything that re-encodes, so a photograph of a standing person can reach
     * here running left to right with nothing in its metadata admitting it. Every row index
     * downstream is a horizontal slice, so on such a frame the waist band lands on a thigh
     * and the shoulder band on a forearm. One scan of a sideways photograph reported 5.0%.
     *
     * The user is never asked to fix this. [Uprightness] reads the shoulder-to-hip vector,
     * which points down the image whenever the image is upright — a fact about anatomy rather
     * than about any file format, so it survives every pipeline that discards metadata.
     *
     * Costs nothing on a correctly-oriented photograph: the pose model runs once, the trunk
     * is found to be vertical, and no pixels are copied.
     */
    private fun detectUpright(bitmap: Bitmap): DetectionResult {
        ensureLoaded()

        val turns = quarterTurnsFor(bitmap)
        if (turns == 0) return detectOrThrow(bitmap)

        val turned = rotate(bitmap, turns)
        return try {
            when (val result = detectOrThrow(turned)) {
                is DetectionResult.Success ->
                    DetectionResult.Success(result.body.copy(quarterTurnsApplied = turns))

                else -> result
            }
        } finally {
            if (turned != bitmap) turned.recycle()
        }
    }

    /** Turns a photograph the way [detect] turned it, for callers that keep the image. */
    fun orientForStorage(bitmap: Bitmap, quarterTurnsClockwise: Int): Bitmap =
        rotate(bitmap, quarterTurnsClockwise)

    /**
     * How far to turn the photograph, from a first look at the pose.
     *
     * When the model finds a body, the body says which way is up. When it finds nothing, the
     * rotations are tried in turn — a pose model is trained on upright people and a sideways
     * one is exactly the case it fails on, so "no person detected" on a photograph that
     * plainly contains a person is usually this and not the subject.
     */
    private fun quarterTurnsFor(bitmap: Bitmap): Int {
        geometryOf(bitmap)?.let { return Uprightness.quarterTurnsClockwise(it) }

        Uprightness.SEARCH_ORDER.filter { it != 0 }.forEach { turns ->
            val turned = rotate(bitmap, turns)
            try {
                val geometry = geometryOf(turned)
                if (geometry != null) {
                    // Compose the two: this rotation found the body, and the body may still
                    // be lying down within it.
                    return (turns + Uprightness.quarterTurnsClockwise(geometry)) % 4
                }
            } finally {
                if (turned != bitmap) turned.recycle()
            }
        }

        // Nothing found at any rotation. Left alone so the failure reported is the real one.
        return 0
    }

    /** Pose geometry for one frame, or null when no usable body is in it. */
    private fun geometryOf(bitmap: Bitmap): FrontPoseGeometry? {
        val result = poseLandmarker?.detect(BitmapImageBuilder(bitmap).build()) ?: return null
        if (result.landmarks().isEmpty()) return null
        return buildGeometry(result)
    }

    /**
     * The photograph at a size worth handing a 256-square network.
     *
     * Returns the original when it is already small enough, so the common path copies nothing
     * and the caller can compare by identity to know whether to recycle.
     *
     * [PART_MASK_MAX_EDGE] is twice the model's own input, which leaves the mask able to
     * resolve a neck to a couple of pixels either way after upsampling while keeping the
     * buffer under a megabyte. The neck on the photograph this was tuned against measured 23
     * pixels at 256 and the reading was identical at every size from 256 to 3000, so there is
     * nothing above this to buy.
     */
    private fun downscaleForParts(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= PART_MASK_MAX_EDGE) return bitmap

        val factor = PART_MASK_MAX_EDGE.toDouble() / longest.toDouble()
        val width = (bitmap.width * factor).toInt().coerceAtLeast(1)
        val height = (bitmap.height * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun rotate(bitmap: Bitmap, quarterTurnsClockwise: Int): Bitmap {
        if (quarterTurnsClockwise % 4 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(90f * (quarterTurnsClockwise % 4)) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun detectOrThrow(bitmap: Bitmap): DetectionResult {
        ensureLoaded()

        val image = BitmapImageBuilder(bitmap).build()

        val poseResult = poseLandmarker?.detect(image)
            ?: return DetectionResult.Failure(DetectionFailure.NoPersonDetected)
        if (poseResult.landmarks().isEmpty()) {
            return DetectionResult.Failure(DetectionFailure.NoPersonDetected)
        }

        val segmentation = segmenter?.segment(image)
            ?: return DetectionResult.Failure(DetectionFailure.SegmentationFailed)

        val mask = segmentation.categoryMask().orElse(null)
            ?: return DetectionResult.Failure(DetectionFailure.SegmentationFailed)

        // Every row index from here on lives in the MASK's coordinate space, not the
        // photo's. A segmenter is free to emit its mask at the model's own resolution
        // rather than the input's; assuming they match walks the buffer at the wrong
        // stride and reads garbage widths from a perfectly good mask. Pose landmarks are
        // normalised 0..1, so they project into the same space by scaling with the mask
        // height — which keeps the anchors and the profile consistent by construction.
        val maskWidth = mask.width
        val maskHeight = mask.height

        val geometry = buildGeometry(poseResult)

        // Built before extraction, because it changes what extraction measures: it is what
        // lets a torso run be cut back when an arm is touching the body.
        val trunk = geometry?.let { TrunkBounds.from(it, maskHeight) }

        // **The third inference, and the only one that can see which part of a person a
        // pixel belongs to.**
        //
        // It answers two questions the silhouette above cannot. Where is the neck — bare skin
        // below the face and above the shoulders, with the hair that used to be measured as
        // part of it now a class of its own. And how much of the midsection is skin rather
        // than cloth, which is what tells the definition metric whether it is scoring a body.
        //
        // Wrapped, and every downstream use is nullable, because this is an addition to a
        // pipeline that already worked without it. A model that fails to load, or a mask that
        // comes back in an unexpected shape, must cost the neck and nothing else — not the
        // widths, not the shape reading, and certainly not the scan.
        val parts = geometry?.let { pose ->
            runCatching {
                // **Downscaled first, and this is not an optimisation.**
                //
                // MediaPipe returns a category mask at the *input image's* resolution, so a
                // twelve-megapixel photograph produces a twelve-megabyte mask — which is then
                // copied into a ByteArray and walked twice by [BodyPartMap], in a Kotlin loop,
                // on the main scan path. The model itself works at 256 square whatever it is
                // handed, so every one of those pixels is an upsample of a pixel the network
                // never saw: the cost is real and the information is not.
                //
                // Left unbounded it is a plausible way for this inference to die on a phone
                // with a good camera and be swallowed by the catch below, which would present
                // as the model silently not running. Everything [BodyPartMap] returns is a
                // fraction, so a smaller mask changes no answer.
                val small = downscaleForParts(bitmap)
                try {
                    val partImage =
                        if (small === bitmap) image else BitmapImageBuilder(small).build()
                    val partMask =
                        partSegmenter?.segment(partImage)?.categoryMask()?.orElse(null)
                    partMask?.let {
                        PartMaskReader.readNeck(it, pose) to
                            PartMaskReader.bareAbdomenFraction(it, pose)
                    }
                } finally {
                    if (small !== bitmap) small.recycle()
                }
            }.getOrNull()
        }

        val profile = MaskWidthExtractor.extract(mask, maskWidth, maskHeight, trunk)
            ?: return DetectionResult.Failure(DetectionFailure.SegmentationFailed)

        // Scale, when the photograph can support one. Every step here is a reason the
        // subject's stature is not reliably in the picture, and each is a veto rather than a
        // warning: a wrong scale multiplies every centimetre in the scan at once.
        val scale = when {
            // A cropped photograph has no stature in it and cannot take the full-body path.
            // The trunk span below is how it gets a scale — on the branch built for it, not
            // this one. Putting it here instead shipped a crash and would have printed
            // centimetres from an inferred ruler; see the trunk branch for both.
            isCropped(poseResult) -> null

            else -> ScaleCrossCheck.resolve(
                maskFraction = profile.bodyHeightFraction,
                landmarkFraction = geometry?.let {
                    LandmarkStature.frameFraction(it.nose, it.ankleLeft, it.ankleRight)
                },
            )?.takeIf { it.bodyHeightFraction >= MIN_BODY_HEIGHT_FRACTION }
        }

        if (scale != null) {
            val anchors = buildAnchors(poseResult, maskHeight)
                ?: return DetectionResult.Failure(DetectionFailure.PoseImplausible)

            // Frontality is checked last, because it needs the measured body height and is
            // the most specific advice available — telling someone to stand square is only
            // useful once we know the photo was otherwise good enough to measure. It is
            // given the resolved height rather than the raw mask one for the same reason the
            // measurements are: a contaminated mask would make square shoulders look narrow.
            if (geometry != null) {
                val aspectRatio = bitmap.width.toDouble() / bitmap.height.toDouble()
                FrontalityCheck
                    .evaluate(geometry, scale.bodyHeightFraction, aspectRatio)
                    ?.let { return DetectionResult.Failure(DetectionFailure.NotFacingCamera(it)) }
            }

            return DetectionResult.Success(
                DetectedBody(
                    profile = profile,
                    anchors = anchors,
                    geometry = geometry,
                    scale = scale,
                    framing = ScanFraming.FULL_BODY,
                    neck = (parts?.first as? NeckOutcome.Found)?.reading,
                    neckRefusal = (parts?.first as? NeckOutcome.Refused)?.reason,
                    bareAbdomenFraction = parts?.second,
                    partMaskRead = parts != null,
                ),
            )
        }

        // No stature in shot. That used to end the scan, and it no longer does: the shape
        // figure and every ratio the app reports divide one width by another in the same
        // image, so none of them ever needed a stature. ScanFraming.TORSO is how the rest of
        // the pipeline is told which frame it is looking at — and, since the trunk span
        // below, centimetres are no longer necessarily gone with it, only softer.
        if (geometry != null && TorsoFraming.supports(geometry)) {
            val anchors = TorsoFraming.anchorsFor(geometry, maskHeight)
                ?: return DetectionResult.Failure(DetectionFailure.PoseImplausible)

            FrontalityCheck.evaluateTorso(geometry)
                ?.let { return DetectionResult.Failure(DetectionFailure.NotFacingCamera(it)) }

            // **A trunk photograph can have a scale after all, and refusing it cost a whole
            // measurement.**
            //
            // A user photographed exactly as this app recommends — waist, shoulders and hips
            // in shot, far more pixels on the midsection than a full-body frame gives — and
            // his ankles were outside the picture. The only stature span the app knew was
            // nose to ankle, so it returned nothing, so there was no scale, so there were no
            // circumferences, so the Navy equation never ran, so the fusion was left with
            // the outline's bound. That bound is a constant. He was shown 11.6% under the
            // words "not resolved by the photo", on a photograph with his waist and his neck
            // plainly in it.
            //
            // The trunk span is the weaker ruler and everything downstream is told so: it
            // carries no cross-check, the row is stored as its own measurement source, and
            // the interval is widened by the sensitivity the equation itself reports. See
            // LandmarkStature.NOSE_TO_HIP_FRACTION, which costs it out rather than asserting
            // it — five per cent of scale is 1.8 points of body fat for a man, against the
            // nine-point constant it replaces.
            //
            // **It belongs on this branch and not on the full-body one.** The first attempt
            // attached it up there, which labelled a cropped photograph FULL_BODY, handed it
            // to the anchor builder that reads knee and ankle landmarks the pose model
            // extrapolates outside the image, and crashed. TorsoFraming.anchorsFor is built
            // for exactly this frame and says so: it lets the knee row fall past the bottom
            // of the picture because the searches clamp to the silhouette's own extent.
            val trunkScale = LandmarkStature
                .frameFractionFromTrunk(geometry.nose, geometry.hipLeft, geometry.hipRight)
                ?.takeIf { it >= MIN_BODY_HEIGHT_FRACTION }
                ?.let { ScaleDecision(it, ScaleSource.TRUNK_SPAN, disagreementPercent = null) }

            return DetectionResult.Success(
                DetectedBody(
                    profile = profile,
                    anchors = anchors,
                    geometry = geometry,
                    scale = trunkScale,
                    framing = ScanFraming.TORSO,
                    neck = (parts?.first as? NeckOutcome.Found)?.reading,
                    neckRefusal = (parts?.first as? NeckOutcome.Refused)?.reason,
                    bareAbdomenFraction = parts?.second,
                    partMaskRead = parts != null,
                ),
            )
        }

        // The hips are not in shot. That used to end the scan here, and it told the user
        // "there is no waist to measure" about a photograph with their waist plainly in it.
        //
        // What is missing is the hip, and the hip is a denominator rather than the
        // measurement. The shoulder denominator has been in SilhouetteBodyFat from the start,
        // carrying its own wider interval for the reason it documents — the arms attach at
        // the shoulder line, so that run is not a torso width. Weaker, and weaker is what
        // this framing gets. It is not nothing, which is what it got before.
        if (geometry != null && UpperBodyFraming.supports(geometry)) {
            val anchors = UpperBodyFraming.anchorsFor(geometry, maskHeight)
                ?: return DetectionResult.Failure(DetectionFailure.PoseImplausible)

            FrontalityCheck.evaluateUpperBody(geometry)
                ?.let { return DetectionResult.Failure(DetectionFailure.NotFacingCamera(it)) }

            return DetectionResult.Success(
                DetectedBody(
                    profile = profile,
                    anchors = anchors,
                    geometry = geometry,
                    scale = null,
                    framing = ScanFraming.UPPER_BODY,
                    neck = (parts?.first as? NeckOutcome.Found)?.reading,
                    neckRefusal = (parts?.first as? NeckOutcome.Refused)?.reason,
                    bareAbdomenFraction = parts?.second,
                    partMaskRead = parts != null,
                ),
            )
        }

        // No framing at all. The waist is out of shot too, so there is nothing to measure.
        return DetectionResult.Failure(DetectionFailure.BodyCropped)
    }

    /** Normalised landmark coordinates, or null when the pose is too incomplete to use. */
    private fun buildGeometry(result: PoseLandmarkerResult): FrontPoseGeometry? {
        val landmarks = result.landmarks().firstOrNull() ?: return null

        fun point(index: Int): PosePoint? = landmarks.getOrNull(index)
            ?.let { PosePoint(it.x().toDouble(), it.y().toDouble()) }

        return FrontPoseGeometry(
            shoulderLeft = point(LANDMARK_SHOULDER_LEFT) ?: return null,
            shoulderRight = point(LANDMARK_SHOULDER_RIGHT) ?: return null,
            hipLeft = point(LANDMARK_HIP_LEFT) ?: return null,
            hipRight = point(LANDMARK_HIP_RIGHT) ?: return null,
            ankleLeft = point(LANDMARK_ANKLE_LEFT),
            ankleRight = point(LANDMARK_ANKLE_RIGHT),
            nose = point(LANDMARK_NOSE),
            elbowLeft = point(LANDMARK_ELBOW_LEFT),
            elbowRight = point(LANDMARK_ELBOW_RIGHT),
            wristLeft = point(LANDMARK_WRIST_LEFT),
            wristRight = point(LANDMARK_WRIST_RIGHT),
            kneeLeft = point(LANDMARK_KNEE_LEFT),
            kneeRight = point(LANDMARK_KNEE_RIGHT),
            // The same pair buildAnchors uses for the full-body chin row. Carried on the
            // geometry so a trunk-framed scan can reach it too, instead of synthesising a
            // chin from the trunk span and landing the neck search on the trapezius.
            mouth = listOfNotNull(point(LANDMARK_MOUTH_LEFT), point(LANDMARK_MOUTH_RIGHT))
                .takeIf { it.isNotEmpty() }
                ?.let { points ->
                    PosePoint(
                        x = points.sumOf { it.x } / points.size,
                        y = points.sumOf { it.y } / points.size,
                    )
                },
        )
    }

    /**
     * True when the head or the feet run past the edge of the frame.
     *
     * Pose landmarks are normalised, and the model happily extrapolates joints beyond 0..1
     * when a limb leaves the picture. Those out-of-range values are the signal: if the
     * ankles sit at or past the bottom edge, the feet were never in shot, and the height
     * the scale depends on is not the height that was photographed.
     */
    private fun isCropped(result: PoseLandmarkerResult): Boolean {
        val landmarks = result.landmarks().firstOrNull() ?: return true

        fun y(index: Int): Float? = landmarks.getOrNull(index)?.y()

        val head = y(LANDMARK_NOSE) ?: return true
        val ankles = listOfNotNull(y(LANDMARK_ANKLE_LEFT), y(LANDMARK_ANKLE_RIGHT))
        if (ankles.isEmpty()) return true

        val lowest = ankles.max()
        return head < EDGE_MARGIN || lowest > 1f - EDGE_MARGIN
    }

    /**
     * Converts normalised pose landmarks into mask rows.
     *
     * Left and right landmarks are averaged, which both stabilises the estimate and
     * tolerates one side being slightly occluded in a side-on photo.
     */
    private fun buildAnchors(result: PoseLandmarkerResult, maskHeight: Int): PoseAnchors? {
        val landmarks = result.landmarks().firstOrNull() ?: return null

        fun row(index: Int): Int? =
            landmarks.getOrNull(index)?.let { (it.y() * maskHeight).toInt() }

        fun midRow(left: Int, right: Int): Int? {
            val l = row(left)
            val r = row(right)
            return when {
                l != null && r != null -> (l + r) / 2
                else -> l ?: r
            }
        }

        val chin = midRow(LANDMARK_MOUTH_LEFT, LANDMARK_MOUTH_RIGHT) ?: return null
        val shoulder = midRow(LANDMARK_SHOULDER_LEFT, LANDMARK_SHOULDER_RIGHT) ?: return null
        val hip = midRow(LANDMARK_HIP_LEFT, LANDMARK_HIP_RIGHT) ?: return null
        val knee = midRow(LANDMARK_KNEE_LEFT, LANDMARK_KNEE_RIGHT) ?: return null

        // PoseAnchors enforces anatomical ordering and throws otherwise. A person lying
        // down, or a badly misdetected pose, lands here; it is a detection failure rather
        // than something to measure.
        return runCatching {
            PoseAnchors(shoulderRow = shoulder, hipRow = hip, kneeRow = knee, chinRow = chin)
        }.getOrNull()
    }

    override fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
        segmenter?.close()
        segmenter = null
        partSegmenter?.close()
        partSegmenter = null
    }

    private companion object {
        const val POSE_MODEL_ASSET = "pose_landmarker_lite.task"
        const val SEGMENTER_MODEL_ASSET = "selfie_segmenter.tflite"
        const val PART_SEGMENTER_MODEL_ASSET = "selfie_multiclass_256x256.tflite"

        /**
         * Longest edge the part segmenter is handed, in pixels.
         *
         * Twice the model's own 256-square input. See [downscaleForParts] for why more is
         * only cost: the mask comes back at whatever size it was given, and every pixel above
         * the model's own resolution is an upsample of one the network never saw.
         */
        const val PART_MASK_MAX_EDGE = 512

        const val MIN_POSE_CONFIDENCE = 0.5f

        /** Below this the subject is too small or cut off for height to anchor the scale. */
        const val MIN_BODY_HEIGHT_FRACTION = 0.4

        // MediaPipe pose landmark indices.
        /** Fraction of the frame that must remain clear above the head and below the feet. */
        const val EDGE_MARGIN = 0.02f

        const val LANDMARK_NOSE = 0
        const val LANDMARK_MOUTH_LEFT = 9
        const val LANDMARK_MOUTH_RIGHT = 10
        const val LANDMARK_SHOULDER_LEFT = 11
        const val LANDMARK_SHOULDER_RIGHT = 12
        const val LANDMARK_ELBOW_LEFT = 13
        const val LANDMARK_ELBOW_RIGHT = 14
        const val LANDMARK_WRIST_LEFT = 15
        const val LANDMARK_WRIST_RIGHT = 16
        const val LANDMARK_HIP_LEFT = 23
        const val LANDMARK_HIP_RIGHT = 24
        const val LANDMARK_KNEE_LEFT = 25
        const val LANDMARK_KNEE_RIGHT = 26
        const val LANDMARK_ANKLE_LEFT = 27
        const val LANDMARK_ANKLE_RIGHT = 28
    }
}
