package com.squeeze.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.db.MeasurementDao
import com.squeeze.app.data.db.MeasurementEntity
import com.squeeze.app.data.db.ProfileDao
import com.squeeze.app.data.db.ProfileEntity
import com.squeeze.app.data.photo.ScanPhotoStore
import com.squeeze.app.scan.BodyDetector
import com.squeeze.app.scan.DetectedBody
import com.squeeze.app.scan.DetectionFailure
import com.squeeze.app.scan.DetectionResult
import com.squeeze.app.scan.PhotoLoader
import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.MeasurementSource
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import com.squeeze.core.scan.AutomaticScanBuilder
import com.squeeze.core.scan.BodyProportions
import com.squeeze.core.scan.BodyScanAnalyser
import com.squeeze.core.scan.PostureAnalysis
import com.squeeze.core.scan.PostureFinding
import com.squeeze.core.scan.Proportion
import com.squeeze.core.scan.ScaleRecovery
import com.squeeze.core.scan.ScaleSource
import com.squeeze.core.scan.ScanResult
import com.squeeze.core.scan.ScanSite
import com.squeeze.core.scan.ScanWarning
import com.squeeze.core.scan.SilhouetteBodyFat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * Where the user is in the scan.
 *
 * Only [FRONT] is required. [OPTIONAL_EXTRAS] is the decision point afterwards: measure now
 * from the front photograph alone, or add a side view for measured depth and a back view
 * for a second width reading.
 */
enum class ScanStep { FRONT, OPTIONAL_EXTRAS, SIDE, BACK, ANALYSING, RESULT }

data class ScanUiState(
    val step: ScanStep = ScanStep.FRONT,
    val result: ScanResult? = null,
    val failure: DetectionFailure? = null,
    val saved: Boolean = false,
    val profileMissing: Boolean = false,
    val hasSide: Boolean = false,
    val hasBack: Boolean = false,
    /** Ratios, which survive scale error and are the scan's most trustworthy output. */
    val proportions: List<Proportion> = emptyList(),
    /** Alignment read from pose landmarks the scan produced anyway. */
    val posture: List<PostureFinding> = emptyList(),
    /** Needed on the result screen to preview body fat as the user corrects a value. */
    val profile: Profile? = null,
    /**
     * Body fat read from the silhouette's proportions, independent of scale recovery.
     *
     * Carried separately from the circumferences because it is the one number on the result
     * screen that did not come through them, and so the only one that can contradict them.
     */
    val shapeBodyFatPercent: Double? = null,
)

/**
 * Drives a body scan from one required photograph and up to two optional ones.
 *
 * Photographs are held in memory for the duration of the scan and never written to disk.
 * Once the circumferences are extracted the bitmaps are dropped.
 *
 * Every path through here ends in a visible state change. An upload that fails to decode,
 * a photo with no person in it, and a crashed inference all land back on a capture step
 * with a named failure — a button that does nothing is indistinguishable from a broken
 * app, because to the user it *is* one.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detector: BodyDetector,
    private val measurementDao: MeasurementDao,
    private val profileDao: ProfileDao,
    private val photoStore: ScanPhotoStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    /**
     * The front photograph, held until the measurement is saved.
     *
     * Kept in memory only for the length of the scan. It is written to encrypted storage at
     * save time and dropped here, so an abandoned scan leaves nothing behind.
     */
    private var frontBitmap: Bitmap? = null

    private var frontBody: DetectedBody? = null
    private var sideBody: DetectedBody? = null
    private var backBody: DetectedBody? = null
    private var frontAspectRatio: Double = 1.0

    /**
     * Which view is being captured right now.
     *
     * Tracked separately from the UI step because the step becomes ANALYSING during
     * inference, at which point it no longer says which photograph is in flight.
     */
    private var capturing: ScanStep = ScanStep.FRONT

    fun addSidePhoto() = moveToCapture(ScanStep.SIDE)

    fun addBackPhoto() = moveToCapture(ScanStep.BACK)

    private fun moveToCapture(step: ScanStep) {
        if (_state.value.step != ScanStep.OPTIONAL_EXTRAS) return
        capturing = step
        _state.value = _state.value.copy(step = step, failure = null)
    }

    /** Measures from whatever has been captured so far; the front photo alone is enough. */
    fun measureNow() {
        val front = frontBody ?: return
        if (_state.value.step != ScanStep.OPTIONAL_EXTRAS) return

        _state.value = _state.value.copy(step = ScanStep.ANALYSING, failure = null)
        viewModelScope.launch { analyse(front, sideBody, backBody) }
    }

    /**
     * Judges a live preview frame for the auto-capture option.
     *
     * Runs the same detector the scan itself runs, which is the whole point: a frame this
     * approves has already passed the cropping, frontality and scale checks, so the photo
     * auto-capture takes cannot then be rejected for framing. A timer cannot promise that —
     * it fires whether or not the user made it into shot.
     *
     * @return advice the user can act on, or null when the frame is worth shooting
     */
    suspend fun checkFraming(frame: Bitmap): String? = withContext(Dispatchers.Default) {
        when (val result = detector.detect(frame)) {
            is DetectionResult.Success -> null
            is DetectionResult.Failure -> when (val reason = result.reason) {
                DetectionFailure.NoPersonDetected -> "Step into frame."
                DetectionFailure.BodyNotFullyVisible,
                DetectionFailure.BodyCropped,
                -> "Step back until your head and feet are both in shot."

                DetectionFailure.PoseImplausible -> "Stand upright, arms clear of your sides."
                DetectionFailure.SegmentationFailed ->
                    "Your outline is hard to separate from the background."

                DetectionFailure.ScaleUnreliable ->
                    "The background is being counted as part of you — try a plainer wall."

                DetectionFailure.PhotoUnreadable -> "Waiting for the camera."
                is DetectionFailure.NotFacingCamera -> reason.advice
            }
        }
    }

    /** Entry point for the upload path: decode off the main thread, then process. */
    fun onPhotoPicked(uri: Uri) {
        if (!canAcceptPhoto()) return
        _state.value = _state.value.copy(step = ScanStep.ANALYSING, failure = null)

        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { PhotoLoader.load(context, uri) }
            if (bitmap == null) {
                // A revoked URI grant or an unsupported format. Without this branch the
                // upload button silently does nothing, which reads as "upload is broken".
                fail(DetectionFailure.PhotoUnreadable)
                return@launch
            }
            process(bitmap)
        }
    }

    /** Entry point for the live-capture path. */
    fun onPhotoCaptured(bitmap: Bitmap) {
        if (!canAcceptPhoto()) return
        _state.value = _state.value.copy(step = ScanStep.ANALYSING, failure = null)
        viewModelScope.launch { process(bitmap) }
    }

    private fun canAcceptPhoto(): Boolean =
        _state.value.step in setOf(ScanStep.FRONT, ScanStep.SIDE, ScanStep.BACK)

    private fun fail(reason: DetectionFailure) {
        _state.value = _state.value.copy(step = capturing, failure = reason)
    }

    private suspend fun process(bitmap: Bitmap) {
        val aspectRatio = bitmap.width.toDouble() / bitmap.height.toDouble()

        // Inference is heavy and synchronous; off the main thread or the UI freezes.
        val detection = withContext(Dispatchers.Default) { detector.detect(bitmap) }

        when (detection) {
            is DetectionResult.Failure -> fail(detection.reason)

            is DetectionResult.Success -> {
                when (capturing) {
                    ScanStep.SIDE -> sideBody = detection.body
                    ScanStep.BACK -> backBody = detection.body
                    else -> {
                        frontBody = detection.body
                        frontAspectRatio = aspectRatio
                        frontBitmap = bitmap
                    }
                }

                // Always return to the decision point. The user chooses when they have
                // given the scan enough; nothing forces a second photograph.
                _state.value = _state.value.copy(
                    step = ScanStep.OPTIONAL_EXTRAS,
                    failure = null,
                    hasSide = sideBody != null,
                    hasBack = backBody != null,
                )
            }
        }
    }

    private suspend fun analyse(
        front: DetectedBody,
        side: DetectedBody?,
        back: DetectedBody?,
    ) {
        val profile = profileDao.get()
        if (profile == null) {
            // Height is the scale reference; without it pixels cannot become centimetres.
            _state.value = _state.value.copy(step = ScanStep.FRONT, profileMissing = true)
            return
        }

        val markers = AutomaticScanBuilder.build(
            frontProfile = front.profile,
            frontAnchors = front.anchors,
            sideProfile = side?.profile,
            sideAnchors = side?.anchors,
            backProfile = back?.profile,
            backAnchors = back?.anchors,
        )

        val analyser = BodyScanAnalyser(
            scale = ScaleRecovery(
                heightCm = profile.heightCm,
                // The cross-checked figure, not the silhouette's own extent. Detection has
                // already compared the outline against the pose landmarks and, where they
                // disagreed, dropped back to the reference that cannot pick up a mirror
                // frame — see ScaleCrossCheck.
                bodyHeightFraction = front.scale.bodyHeightFraction,
            ),
            imageAspectRatio = frontAspectRatio,
        )

        val result = analyser.analyse(markers)

        // Computed from the pixel profile before it is discarded. Nothing here converts to
        // centimetres, so a mask that misjudged the body's height cannot reach it.
        val shape = SilhouetteBodyFat
            .indicesFrom(front.profile, front.anchors)
            ?.let { SilhouetteBodyFat.estimate(it, Sex.valueOf(profile.sex)) }
            ?.percent

        // The female Navy equation needs a hip; the male one does not. Reporting a missing
        // hip to a man would be noise, so the warning is filtered by profile here.
        val relevantWarnings = result.warnings.filterNot { warning ->
            warning is ScanWarning.MissingRequiredSite &&
                warning.site == ScanSite.HIP &&
                Sex.valueOf(profile.sex) == Sex.MALE
        } + listOfNotNull(
            // Placed at the end because it is the widest-reaching of them: it says something
            // about every measurement above it rather than about one site.
            front.scale.takeIf { it.source == ScaleSource.LANDMARK }
                ?.disagreementPercent
                ?.let { ScanWarning.ScaleFromLandmarks(it) },
        )

        _state.value = _state.value.copy(
            step = ScanStep.RESULT,
            profile = profile.toScanProfile(),
            result = result.copy(warnings = relevantWarnings),
            // Ratios divide two measurements from the same photograph, so scale error
            // cancels — they are trustworthy even when the centimetres are not.
            proportions = BodyProportions.analyse(result.circumferences, profile.heightCm),
            shapeBodyFatPercent = shape,
            posture = front.geometry?.let(PostureAnalysis::analyse).orEmpty(),
        )
    }

    /**
     * Stores the measurement the user confirmed.
     *
     * [edited] rather than the scan's own output, because the silhouette gets sites wrong
     * often enough that a value the user has corrected is worth more than one the pipeline
     * is confident about. What the scan produces is a starting point.
     */
    fun save(
        edited: Circumferences,
        weightKg: Double?,
        visualBodyFatPercent: Double? = null,
        knownBodyFatPercent: Double? = null,
    ) {
        val shape = _state.value.shapeBodyFatPercent
        val result = _state.value.result ?: return

        viewModelScope.launch {
            val c = edited

            // Written before the row, so a row never references a file that failed to save.
            val photoId = frontBitmap?.let { bitmap ->
                withContext(Dispatchers.IO) { photoStore.save(bitmap) }
            }

            measurementDao.insert(
                MeasurementEntity(
                    epochDay = LocalDate.now().toEpochDay(),
                    // A front-only scan assumed its depth, so it is stored as a distinct
                    // source and weighted by its own wider error rather than passed off as
                    // a full two-photo measurement.
                    source = if (result.depthAssumed) {
                        MeasurementSource.PHOTO_FRONT_ONLY.name
                    } else {
                        MeasurementSource.PHOTO.name
                    },
                    weightKg = weightKg,
                    neckCm = c.neckCm,
                    waistCm = c.waistCm,
                    hipCm = c.hipCm,
                    chestCm = c.chestCm,
                    thighCm = c.thighCm,
                    armCm = c.armCm,
                    calfCm = c.calfCm,
                    chestMm = null,
                    abdomenMm = null,
                    thighMm = null,
                    tricepsMm = null,
                    suprailiacMm = null,
                    // Stored on the scan's own row so calibration fits from this one action:
                    // the row carries both what the equations said and what the truth is.
                    referenceBodyFatPercent = knownBodyFatPercent?.takeIf { it in 2.0..70.0 },
                    // The one input that did not come from the photograph, and so the one
                    // that can contradict it. See VisualAssessment.
                    visualBodyFatPercent = visualBodyFatPercent,
                    shapeBodyFatPercent = shape,
                    note = if (result.depthAssumed) "Photo scan (front only)" else "Photo scan",
                    photoId = photoId,
                ),
            )

            frontBitmap = null
            _state.value = _state.value.copy(saved = true)
        }
    }

    fun restart() {
        frontBitmap = null
        frontBody = null
        sideBody = null
        backBody = null
        frontAspectRatio = 1.0
        capturing = ScanStep.FRONT
        _state.value = ScanUiState()
    }

    override fun onCleared() {
        super.onCleared()
        detector.close()
    }
}

/**
 * The subset of the profile the result screen needs.
 *
 * Only the three fields the body-fat equations use. Training age and goal are irrelevant to
 * a measurement, and defaulting them here keeps this independent of the settings screen.
 */
private fun ProfileEntity.toScanProfile() = Profile(
    heightCm = heightCm,
    birthYear = birthYear,
    sex = Sex.valueOf(sex),
)
