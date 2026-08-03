package com.squeeze.app.scan

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.squeeze.core.scan.FrontPoseGeometry
import com.squeeze.core.scan.PoseAnchors
import com.squeeze.core.scan.PosePoint
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
     * Head or feet are outside the frame.
     *
     * Distinct from [BodyNotFullyVisible], which is about the subject being too small.
     * This is the more dangerous case: a cropped body still fills the frame, so the scale
     * step happily maps the user's full height onto whatever fraction of them is showing
     * and inflates every circumference. It has to be caught before measurement, not after.
     */
    data object BodyCropped : DetectionFailure

    /** The picked file could not be decoded into an image at all. */
    data object PhotoUnreadable : DetectionFailure
}

sealed interface DetectionResult {
    data class Success(val body: DetectedBody) : DetectionResult
    data class Failure(val reason: DetectionFailure) : DetectionResult
}

/**
 * Finds a body in a photograph, entirely on-device.
 *
 * Two models run per image. The pose landmarker locates joints, which is what it is good
 * at; the segmenter produces a body mask, which is reduced to a per-row width profile. The
 * division of labour matters: a pose model cannot see soft tissue, so it cannot find a
 * natural waist, while a mask has no idea which part of a silhouette is a waist. Together
 * the joints bound the search and the silhouette decides the exact level.
 *
 * Nothing here touches the network — the models ship inside the APK and the app holds no
 * INTERNET permission, so a body photo cannot leave the device.
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
    }

    /**
     * Never throws. MediaPipe surfaces problems as native exceptions, and a scan that
     * crashes the app teaches the user nothing; a named failure tells them what to change.
     */
    fun detect(bitmap: Bitmap): DetectionResult = runCatching { detectOrThrow(bitmap) }
        .getOrElse { DetectionResult.Failure(DetectionFailure.SegmentationFailed) }

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

        val profile = MaskWidthExtractor.extract(mask, maskWidth, maskHeight)
            ?: return DetectionResult.Failure(DetectionFailure.SegmentationFailed)

        if (isCropped(poseResult)) {
            return DetectionResult.Failure(DetectionFailure.BodyCropped)
        }

        val anchors = buildAnchors(poseResult, maskHeight)
            ?: return DetectionResult.Failure(DetectionFailure.PoseImplausible)

        // Scale comes from the subject's full height, so a cropped body would silently
        // scale every circumference wrong rather than simply measuring less.
        if (profile.bodyHeightFraction < MIN_BODY_HEIGHT_FRACTION) {
            return DetectionResult.Failure(DetectionFailure.BodyNotFullyVisible)
        }

        return DetectionResult.Success(
            DetectedBody(profile, anchors, buildGeometry(poseResult)),
        )
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
    }

    private companion object {
        const val POSE_MODEL_ASSET = "pose_landmarker_lite.task"
        const val SEGMENTER_MODEL_ASSET = "selfie_segmenter.tflite"

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
        const val LANDMARK_HIP_LEFT = 23
        const val LANDMARK_HIP_RIGHT = 24
        const val LANDMARK_KNEE_LEFT = 25
        const val LANDMARK_KNEE_RIGHT = 26
        const val LANDMARK_ANKLE_LEFT = 27
        const val LANDMARK_ANKLE_RIGHT = 28
    }
}
