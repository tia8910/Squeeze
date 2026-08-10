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
import com.squeeze.core.bodycomp.PlateauPrior
import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.MeasurementSource
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import com.squeeze.core.scan.AbdominalProfile
import com.squeeze.core.scan.AutomaticScanBuilder
import com.squeeze.core.scan.BodyProportions
import com.squeeze.core.scan.BodyScanAnalyser
import com.squeeze.core.scan.PostureAnalysis
import com.squeeze.core.scan.PostureFinding
import com.squeeze.core.scan.Proportion
import com.squeeze.core.scan.ScaleRecovery
import com.squeeze.core.scan.ScaleSource
import com.squeeze.core.scan.ScanFraming
import com.squeeze.core.scan.ScanResult
import com.squeeze.core.scan.ScanSite
import com.squeeze.core.scan.ScanWarning
import com.squeeze.app.scan.AbdomenCrop
import com.squeeze.core.scan.ArmClearance
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
     *
     * The whole estimate rather than its percentage, because the interval is what says
     * whether the outline measured this body or merely bounded it, and the result screen has
     * to show the difference. It is the *unresolved* reading: [PlateauPrior] is applied at the
     * point of display and of saving, where the user's weight is known.
     */
    val shape: BodyFatEstimate? = null,
    /**
     * The most recent recorded bodyweight, before the user types one on this screen.
     *
     * Needed because what the outline reports on its plateau depends on the body's build. Also
     * prefills the weight field, which is worth doing on its own: a scan with no weight is a
     * scan the plausibility gate and the lean-mass trend cannot use.
     */
    val knownWeightKg: Double? = null,
    /**
     * Body fat read from the abdomen's side-on depth, when a side photograph was taken.
     *
     * Separate from [shape] because they measure perpendicular axes. The front view reads
     * width, which is the axis abdominal fat moves along least; this reads depth, which is
     * the one it moves along most.
     */
    val abdominalBodyFatPercent: Double? = null,
    /**
     * What the light was doing, when it is doing something that matters.
     *
     * Null when the light was fine. The app cannot supply light at scan distance — an LED
     * two metres further away delivers a hundredth as much — so telling the user is the
     * whole of what it can do about it.
     */
    val lightingAdvice: String? = null,
    /**
     * What the arms were doing, when they were doing something that ruins the measurement.
     *
     * Null when they were clear of the body. Ranked above lighting in the result screen
     * because it is a larger error and a cheaper fix: bad light widens the answer, arms
     * against the waist replace it with a number derived from the trunk bound.
     */
    val poseAdvice: String? = null,
    /**
     * How much of the body the photograph held, and so what this result may claim.
     *
     * At [ScanFraming.TORSO] there are no centimetres — the stature they scale from was not
     * in shot. The shape figure and the ratios are unaffected, because none of them ever
     * used it.
     */
    val framing: ScanFraming = ScanFraming.FULL_BODY,
) {
    /**
     * What the app should print, given a weight the user may have typed since the scan ran.
     *
     * Recomputed rather than stored so that entering a weight updates the headline
     * immediately. On the plateau the outline contributes only a bound, and the figure comes
     * from the build — so the weight field is not an afterthought on this screen, it is an
     * input to the number above it.
     */
    fun resolvedShape(weightKg: Double?): BodyFatEstimate? {
        val profile = profile ?: return shape
        return PlateauPrior.resolve(
            estimate = shape,
            profile = profile,
            weightKg = weightKg ?: knownWeightKg,
            age = profile.ageAt(LocalDate.now().year),
        )
    }
}

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
                -> "Get your shoulders and hips both in shot."

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

                        // Stored the same way up as it was measured. The detector stands a
                        // sideways photograph up before reading it; keeping the original
                        // here would put a sideways image in the record beside a correct
                        // number, which reads as a broken scan and cannot be told apart
                        // from one.
                        val upright = detector.orientForStorage(
                            bitmap,
                            detection.body.quarterTurnsApplied,
                        )
                        frontAspectRatio = if (detection.body.quarterTurnsApplied % 2 == 0) {
                            aspectRatio
                        } else {
                            1.0 / aspectRatio
                        }
                        frontBitmap = upright
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

        // Centimetres only where the photograph can support them. A trunk-framed shot has
        // no stature in it, and the one thing this codebase has learned the hard way is that
        // a fabricated stature does not produce a slightly wrong scan, it produces a
        // confidently wrong one — every girth is multiplied by the same bad number.
        val result = front.scale?.let { scale ->
            BodyScanAnalyser(
                scale = ScaleRecovery(
                    heightCm = profile.heightCm,
                    // The cross-checked figure, not the silhouette's own extent. Detection
                    // has already compared the outline against the pose landmarks and, where
                    // they disagreed, dropped back to the reference that cannot pick up a
                    // mirror frame — see ScaleCrossCheck.
                    bodyHeightFraction = scale.bodyHeightFraction,
                ),
                imageAspectRatio = frontAspectRatio,
            ).analyse(markers)
        } ?: ScanResult(
            circumferences = Circumferences(),
            warnings = emptyList(),
            // Not usable for the tape equations, which is what this flag means. The shape
            // figure below does not go through them and is unaffected.
            usableForBodyFat = false,
        )

        // Computed from the pixel profile before it is discarded. Nothing here converts to
        // centimetres, so a mask that misjudged the body's height cannot reach it.
        // The hip landmarks come along so the shape reader can tell the body from the
        // clothes on it. Both they and the width profile are fractions of image width, so
        // they compare directly and the photograph's scale cancels.
        val pelvisSpan = front.geometry
            ?.let { kotlin.math.abs(it.hipLeft.x - it.hipRight.x) }
            ?.takeIf { it > 0.0 }

        val shapeEstimate = SilhouetteBodyFat
            .indicesFrom(front.profile, front.anchors, pelvisSpan)
            ?.let { SilhouetteBodyFat.estimate(it, Sex.valueOf(profile.sex)) }

        // Fetched here so the headline has a build to fall back on before the user types
        // anything. Without it a plateau reading has nothing to resolve against and prints
        // the method's own constant, which is where 11.6% came from.
        val knownWeight = measurementDao.latestWeightKg()

        val lighting = frontBitmap?.let { AbdomenCrop.lighting(it, front.geometry) }

        // Abdominal definition used to narrow a plateau reading to a point inside it. It no
        // longer does, and the reason is measurement rather than taste.
        //
        // Read against a labelled reference set the texture score runs 5.76 at eight per cent,
        // 6.15 at ten, 7.44 at fifteen, 6.03 at twenty, 7.28 at twenty-five, 5.48 at thirty
        // and 4.51 at thirty-five. That is not a weak signal, it is not a signal: it does not
        // even move in one direction. What it tracks is how hard the light was, which is why
        // the lighting gate above it was never enough to save it.
        //
        // Left in the codebase and still shown to the user as a lighting observation, because
        // the lighting half of that work is sound. It simply may not set a number.
        //
        // The concrete damage was visible: a body with no abdominal definition at all came
        // back as exactly 8.00 per cent, because the texture score placed it at the lean end
        // and the lean end is a constant. A figure that is a constant is not a measurement,
        // and printing it to two decimal places made it look like the opposite.

        // The abdomen, measured on the axis it actually moves along. Only a side photograph
        // can supply it: from the front, fat that accumulates in depth is invisible, which is
        // why four separate front-view indices in this project came back flat or out of
        // order. It is also the one measurement here an arm cannot corrupt, because edge-on
        // an arm lies inside the torso's front-to-back extent rather than extending it.
        val abdominal = side?.let { view ->
            AbdominalProfile.depthsFrom(view.profile, view.anchors)
                ?.let { AbdominalProfile.estimate(it, Sex.valueOf(profile.sex)) }
                ?.percent
        }

        // The female Navy equation needs a hip; the male one does not. Reporting a missing
        // hip to a man would be noise, so the warning is filtered by profile here.
        val relevantWarnings = result.warnings.filterNot { warning ->
            warning is ScanWarning.MissingRequiredSite &&
                warning.site == ScanSite.HIP &&
                Sex.valueOf(profile.sex) == Sex.MALE
        } + listOfNotNull(
            // Placed at the end because it is the widest-reaching of them: it says something
            // about every measurement above it rather than about one site.
            front.scale?.takeIf { it.source == ScaleSource.LANDMARK }
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
            shape = shapeEstimate,
            knownWeightKg = knownWeight,
            framing = front.framing,
            abdominalBodyFatPercent = abdominal,
            poseAdvice = ArmClearance.verdict(front.profile, front.anchors),
            lightingAdvice = lighting?.advice,
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
        // Resolved against the weight the user just entered rather than the one on file, so
        // the figure that is stored is the figure they were looking at when they pressed save.
        val shape = _state.value.resolvedShape(weightKg)
        val abdominal = _state.value.abdominalBodyFatPercent
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
                    shapeBodyFatPercent = shape?.percent,
                    // Written alongside the figure because it is what tells the repository
                    // whether that figure measured this body or merely bounded it. Storing
                    // the percentage alone let a ±9 reading re-enter the fusion at ±5.
                    shapeStandardErrorPercent = shape?.standardErrorPercent,
                    abdominalBodyFatPercent = abdominal,
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
