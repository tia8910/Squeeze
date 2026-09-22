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
import com.squeeze.app.scan.ClipAppearance
import com.squeeze.app.scan.DetectedBody
import com.squeeze.app.scan.DetectionFailure
import com.squeeze.app.scan.DetectionResult
import com.squeeze.app.scan.PhotoLoader
import com.squeeze.core.bodycomp.BodyFatCalculator
import com.squeeze.core.bodycomp.LeanMassPlausibility
import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.EstimationMethod
import com.squeeze.core.model.MeasurementSource
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import com.squeeze.core.scan.AbdominalProfile
import com.squeeze.core.scan.AnatomicalLevelFinder
import com.squeeze.core.scan.AppearanceEstimator
import com.squeeze.core.scan.AutomaticScanBuilder
import com.squeeze.core.scan.BodyPartMap
import com.squeeze.core.scan.BodyProportions
import com.squeeze.core.scan.BodyScanAnalyser
import com.squeeze.core.scan.PostureAnalysis
import com.squeeze.core.scan.PostureFinding
import com.squeeze.core.scan.Proportion
import com.squeeze.core.scan.LandmarkStature
import com.squeeze.core.scan.NeckReading
import com.squeeze.core.scan.NeckRefusal
import com.squeeze.core.scan.ScaleRecovery
import com.squeeze.core.scan.ScaleSource
import com.squeeze.core.scan.ScanFraming
import com.squeeze.core.scan.ScanResult
import com.squeeze.core.scan.ScanSite
import com.squeeze.core.scan.ScanWarning
import com.squeeze.app.scan.AbdomenCrop
import com.squeeze.core.scan.ArmClearance
import com.squeeze.core.scan.ShapeIndices
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
enum class ScanStep { WEIGHT, FRONT, OPTIONAL_EXTRAS, SIDE, BACK, ANALYSING, RESULT }

data class ScanUiState(
    val step: ScanStep = ScanStep.WEIGHT,
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
     * to show the difference. It is exactly what the outline produced — on the plateau, a
     * bound carrying ±9 rather than a figure about this body.
     */
    val shape: BodyFatEstimate? = null,
    /**
     * The two ratios [shape] was read from, so the screen can say what the outline actually
     * measured.
     *
     * Here because "not resolved by the photo" is unfalsifiable on its own. Two very
     * different things produce it — a genuinely lean body the method has no power to place,
     * and a denominator with an arm in it — and they call for opposite responses from the
     * person holding the phone. Printing the ratio and which denominator carried it turns one
     * screenshot into a diagnosis instead of a report that something went wrong.
     */
    val shapeIndices: ShapeIndices? = null,
    /**
     * The most recent recorded bodyweight, before the user types one on this screen.
     *
     * Prefills the weight field. A scan with no weight is a scan the plausibility gate and
     * the lean-mass trend cannot use — but it no longer changes the body-fat figure, which
     * comes from the photograph or does not come at all.
     */
    val knownWeightKg: Double? = null,
    /**
     * The weight the user entered when the scan started.
     *
     * Asked for before the camera so the scan is never one forgotten field away from being
     * unusable by the plausibility gate and the lean-mass trend, and so the record carries the
     * weight it was actually taken at.
     *
     * It is **not** an input to the body-fat figure. It was, for one release: on the plateau
     * the reported figure came from height and weight through Deurenberg, which meant the same
     * number for every photograph of one person at one weight. That is gone.
     */
    val enteredWeightKg: Double? = null,
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
    /**
     * True when the scan's centimetres rest on a stature inferred from the trunk, because the
     * feet were outside the frame.
     *
     * Recorded so the saved row can say so and be weighted accordingly. Without it a scan
     * scaled from a soft ruler would be stored as though its girths were measured, and a
     * scale error multiplies every one of them together.
     */
    val scaleFromTrunk: Boolean = false,
    /**
     * What the tape equation made of the girths this photograph produced.
     *
     * **Here because the result card could not report it.** That card has only ever shown
     * [shape] — the outline's own reading — so a scan that measured a waist, a neck and a
     * chest still printed the outline's constant under the words "not resolved by the photo".
     * The photograph had resolved it; the card had nowhere to say so.
     *
     * Null when the scan produced no usable girths, which is the honest case the copy was
     * written for.
     */
    val tape: BodyFatEstimate? = null,
    /**
     * How much abdominal structure the front photograph showed, in arbitrary units.
     *
     * Null when the crop was too dark, too small or too evenly lit. Not a percentage and not
     * comparable between people, which is why nothing on the result screen is decided by it:
     * it is kept so one user's own scans can be compared with each other, and for no other
     * purpose. A release that let it settle a reading is described in the view model.
     */
    val definitionScore: Double? = null,
    /**
     * True when the neck in [tape] came from the part-segmentation model rather than being
     * absent.
     *
     * Surfaced because the difference is the whole scan. Without it there is no `waist − neck`
     * and no tape reading at all, and the result screen has spent several releases printing
     * the outline method's constant while saying "not resolved by the photo" underneath.
     */
    val neckFromModel: Boolean = false,
    /**
     * Exactly what the part model made of the neck, for the result screen to print.
     *
     * Here because two releases were spent inferring the model's behaviour from a single
     * centimetre figure on a screenshot, and guessing wrong both times. The ratio to the face
     * says outright whether it found a neck or the top of a trapezius, and whether the model
     * ran at all is a separate fact from whether it found one.
     */
    val neckReading: NeckReading? = null,
    /** Whether a part mask was produced at all, as distinct from it finding a neck. */
    val partMaskRead: Boolean = false,
    /**
     * The waist as the silhouette measured it, as a fraction of frame width.
     *
     * Printed beside the part model's own waist so the two masks can be compared directly
     * rather than inferred from a centimetre figure. Inferring it is how a diagnosis of "two
     * coordinate spaces, a factor of 1.34" was reached from a waist row measured in the wrong
     * place; the two numbers side by side settle it in one glance.
     */
    val silhouetteWaistFraction: Double? = null,
    /** Why the part model found no neck, when it ran and found none. */
    val neckRefusal: NeckRefusal? = null,
    /**
     * Body fat as the on-device vision-language model reads it from how the body looks.
     *
     * The one reading in this scan taken from inside the outline: whether the abdominal
     * muscles show through the skin, which is what separates a lean body from a very lean one
     * and what every width-based method throws away. See AppearanceEstimator for what it was
     * tested against and how far that goes.
     *
     * Null for women, whose reference descriptions do not exist yet — the ones that do
     * describe men, and the same visible leanness sits eight to ten points higher on a woman.
     * Null too when the model is missing from a build or cannot load.
     */
    val appearance: BodyFatEstimate? = null,
    /**
     * Share of the midsection the part model found to be bare skin, 0.0 to 1.0.
     *
     * Null when no part mask was produced. Below [BodyPartMap.MIN_BARE_ABDOMEN] the definition
     * score is withheld rather than shown, because at that point it is measuring cloth.
     */
    val bareAbdomenFraction: Double? = null,
) {
    /**
     * What the photograph supports, and nothing else.
     *
     * This used to hand a bounded reading to `PlateauPrior.resolve`, which replaced it with a
     * figure from height, weight, age and sex. That figure is a constant for one person at
     * one weight — three photographs of the same man at 70 kg all returned 17.3%, necessarily
     * — and it was stored under the photo method's own name, which is how it slipped past
     * [com.squeeze.core.bodycomp.MethodFusion]'s rule that a BMI figure never outvotes a
     * measurement. A scan's answer must come from the scan.
     *
     * So on the plateau the app now prints the outline's own bound with its own ±9. Weaker,
     * and true. Weight still reaches this function, but only through the plausibility gate
     * below, which can rule a reading out and cannot invent one.
     */
    fun resolvedShape(weightKg: Double?): BodyFatEstimate? {
        val profile = profile ?: return shape
        val weight = weightKg ?: knownWeightKg

        // A gate, not a source. It moves a reading only when height and weight make it
        // physically impossible — the check that caught 36.6% on a normally-built man — and
        // widens the interval to say it was moved. Nothing it does can put a number on a
        // body the photograph failed to read.
        return shape?.let { LeanMassPlausibility.clampToRange(it, profile, weight) }
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
    private val appearanceModel: ClipAppearance,
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

    init {
        // Loaded before anything is on screen so the weight step opens with the last figure
        // already in the field. Most people's weight does not move between scans, so the
        // common case becomes one tap rather than one typing task.
        viewModelScope.launch {
            val known = measurementDao.latestWeightKg()
            _state.value = _state.value.copy(knownWeightKg = known, enteredWeightKg = known)
        }
    }

    /**
     * Records the weight and opens the camera.
     *
     * Null is allowed and moves on: a scan without a weight is worse but still worth taking,
     * and a modal the user cannot get past would cost more scans than it saves figures.
     */
    fun confirmWeight(weightKg: Double?) {
        if (_state.value.step != ScanStep.WEIGHT) return
        _state.value = _state.value.copy(
            step = ScanStep.FRONT,
            enteredWeightKg = weightKg ?: _state.value.knownWeightKg,
        )
    }

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
                -> "Get your shoulders and your waist in shot."

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
            hipsInFrame = front.framing.hipsInShot,
            // **The neck the part model found, and the whole reason the tape path can run.**
            //
            // The silhouette search that used to supply this looked for the narrowest row
            // between the chin and the shoulders in a one-bit mask, where neck, hair, collar
            // and trapezius are all the same colour. On real photographs it returned nothing
            // at all, which meant no `waist − neck`, no Navy estimate, and the outline
            // method's constant printed under the words "not resolved by the photo".
            //
            // Null here is still null downstream — a covered neck is refused rather than
            // guessed — but it is now refused for a reason the app can name.
            neck = front.neck,
            // Separate from the neck itself. When the mask was read and found nothing, the
            // silhouette's neck is dropped rather than substituted — see the parameter's
            // documentation for the 54.3 cm reading that fallback produced.
            partMaskRead = front.partMaskRead,
        )

        // Centimetres only where the photograph can support them. A trunk-framed shot has
        // no stature in it, and the one thing this codebase has learned the hard way is that
        // a fabricated stature does not produce a slightly wrong scan, it produces a
        // confidently wrong one — every girth is multiplied by the same bad number.
        val result = front.scale?.let { scale ->
            // **The trunk-scaled path degrades instead of dying, and only that path.**
            //
            // Enabling it turned a scan that produced no centimetres into one that produces
            // them, which put a photograph through code it had never reached. The first
            // attempt crashed the app on the measure button. A second bug of the same shape
            // would do it again, and a user staring at "Squeeze.fit closed because this app
            // has a bug" has lost the whole scan — including the shape figure, which never
            // needed a scale at all.
            //
            // So a failure here falls back to exactly what this photograph did before the
            // trunk span existed: no centimetres, the shape reading intact. That is a real
            // outcome the app already knows how to present, not a swallowed error.
            //
            // Deliberately not extended to a measured scale. A full-body scan throwing is a
            // bug in code that has always run, and hiding it would cost the evidence.
            val measureInCentimetres: () -> ScanResult = {
                BodyScanAnalyser(
                    scale = ScaleRecovery(
                        heightCm = profile.heightCm,
                        // The cross-checked figure, not the silhouette's own extent.
                        // Detection has already compared the outline against the pose
                        // landmarks and, where they disagreed, dropped back to the reference
                        // that cannot pick up a mirror frame — see ScaleCrossCheck.
                        bodyHeightFraction = scale.bodyHeightFraction,
                        // A trunk-framed scan's stature was worked out from the nose-to-hip
                        // span rather than measured, so it may exceed the frame — which is a
                        // legitimate reading and used to be an uncaught exception.
                        statureInferred = scale.source == ScaleSource.TRUNK_SPAN,
                    ),
                    imageAspectRatio = frontAspectRatio,
                ).analyse(markers)
            }

            if (scale.source == ScaleSource.TRUNK_SPAN) {
                runCatching(measureInCentimetres).getOrNull()
            } else {
                measureInCentimetres()
            }
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

        val shapeIndices = SilhouetteBodyFat.indicesFrom(
            front.profile,
            front.anchors,
            pelvisSpan,
            // False only at UPPER_BODY, where the pelvis is outside the picture and the
            // hip band would otherwise clamp onto the crop line. See the parameter's own
            // documentation — it is the one place this framing could go silently wrong
            // rather than loudly.
            hipInFrame = front.framing.hipsInShot,
        )

        // How much structure the abdomen showed, read from *inside* the outline.
        //
        // The only reading this scan takes from the surface rather than from the border, and
        // the only one that can separate a lean body from a very lean one — an outline knows
        // where the body ends and nothing about what is inside it, which is the entire
        // difference between eight per cent and fifteen.
        val definition = frontBitmap?.let { AbdomenCrop.measure(it, front.geometry) }

        // The outline's reading, and the only one. A previous release let the definition score
        // above corroborate a reading the outline could not resolve — a lean outline over a
        // defined abdomen being two independent signals agreeing — and the argument was sound
        // while the measurement was not. That score had no zero: a patch of blank painted wall
        // in a real scan photograph read 21.3 against a threshold of 20, and the smooth
        // abdomen it was asked about read 37.9, the highest figure this project has recorded.
        // It was reading the camera's noise, so the app printed "Read from your photo" over
        // the plateau's own constant. See AbdominalDefinition, which now has a measured zero
        // and still has no threshold, because having a zero is not the same as being
        // calibrated against other people's bodies.
        val shapeEstimate = shapeIndices
            ?.let { SilhouetteBodyFat.estimate(it, Sex.valueOf(profile.sex)) }

        // Fetched here so the headline has a build to fall back on before the user types
        // anything. Without it a plateau reading has nothing to resolve against and prints
        // the method's own constant, which is where 11.6% came from.
        val knownWeight = _state.value.enteredWeightKg ?: measurementDao.latestWeightKg()

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

        // The on-device vision-language model, off the main thread: a first run copies an 88 MB
        // model out of the APK, and every run is a ViT forward pass on the CPU.
        val appearance = frontBitmap
            ?.takeIf { Sex.valueOf(profile.sex) == Sex.MALE }
            ?.let { bitmap -> withContext(Dispatchers.Default) { appearanceModel.estimate(bitmap) } }
            ?.let {
                BodyFatEstimate(
                    percent = it,
                    method = EstimationMethod.VISUAL_ASSESSMENT,
                    standardErrorPercent = AppearanceEstimator.STANDARD_ERROR_PERCENT,
                )
            }

        _state.value = _state.value.copy(
            step = ScanStep.RESULT,
            appearance = appearance,
            profile = profile.toScanProfile(),
            result = result.copy(warnings = relevantWarnings),
            // Ratios divide two measurements from the same photograph, so scale error
            // cancels — they are trustworthy even when the centimetres are not.
            proportions = BodyProportions.analyse(result.circumferences, profile.heightCm),
            shape = shapeEstimate,
            shapeIndices = shapeIndices,
            knownWeightKg = knownWeight,
            framing = front.framing,
            scaleFromTrunk = front.scale?.source == ScaleSource.TRUNK_SPAN,
            // The tape equation on the girths this photograph produced, carrying the same
            // widened interval the saved row will get, so the card and the record agree
            // rather than quietly differing by a couple of points.
            tape = BodyFatCalculator.navy(profile.toScanProfile(), result.circumferences)
                ?.let { navy ->
                    val fromScale = if (front.scale?.source == ScaleSource.TRUNK_SPAN) {
                        BodyFatCalculator.navyScaleSensitivityPercent(
                            profile.toScanProfile(),
                            result.circumferences,
                            LandmarkStature.TRUNK_SPAN_SCALE_ERROR,
                        ) ?: 0.0
                    } else {
                        0.0
                    }
                    val base = EstimationMethod.PHOTO_FRONT_ONLY.standardErrorPercent
                    navy.copy(
                        method = EstimationMethod.PHOTO_FRONT_ONLY,
                        standardErrorPercent = kotlin.math.sqrt(
                            base * base + fromScale * fromScale,
                        ),
                    )
                },
            abdominalBodyFatPercent = abdominal,
            poseAdvice = ArmClearance.verdict(front.profile, front.anchors),
            lightingAdvice = lighting?.advice,
            // Withheld over clothing. The metric reads shadow contrast across the midsection
            // and a shirt supplies plenty of it — harder-edged than skin, in fact, because a
            // fold casts a line that no amount of body fat does. Until the part model existed
            // there was no way to tell the two apart, so a clothed scan produced a score in
            // the same units and the same range as a bare one and nothing said which it was.
            definitionScore = definition
                ?.takeIf { it.usable }
                ?.score
                ?.takeIf { (front.bareAbdomenFraction ?: 1.0) >= BodyPartMap.MIN_BARE_ABDOMEN },
            neckFromModel = front.neck != null,
            neckReading = front.neck,
            partMaskRead = front.partMaskRead,
            neckRefusal = front.neckRefusal,
            silhouetteWaistFraction = AnatomicalLevelFinder
                .narrowestBetween(front.profile, front.anchors.shoulderRow, front.anchors.hipRow)
                ?.let { front.profile.torsoWidthAt(it) }
                ?.takeIf { it > 0.0 },
            bareAbdomenFraction = front.bareAbdomenFraction,
            // Shoulder level always; hip level only when the hips were in the picture. An
            // inferred hip line is level because the prior is level, not because the body is.
            posture = front.geometry
                ?.let { PostureAnalysis.analyse(it, front.framing.hipsInShot) }
                .orEmpty(),
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
        // Falls back to the weight given at the start of the scan. The result screen's field
        // is prefilled from it, so the two agree in the ordinary case; this only matters when
        // the field was cleared, and losing the weight there would silently drop the figure
        // back to the leanest number the method is allowed to claim.
        val weight = weightKg ?: _state.value.enteredWeightKg

        // The same figure the user was looking at when they pressed save, gated against the
        // weight they entered rather than the one on file.
        val shape = _state.value.resolvedShape(weight)
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
                    // A trunk-scaled scan is named first, because its inference sits further
                    // upstream than the assumed depth: depth affects one axis of each girth,
                    // an inferred scale multiplies all of them at once.
                    source = when {
                        _state.value.scaleFromTrunk ->
                            MeasurementSource.PHOTO_TRUNK_SCALED.name

                        result.depthAssumed -> MeasurementSource.PHOTO_FRONT_ONLY.name
                        else -> MeasurementSource.PHOTO.name
                    },
                    weightKg = weight,
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
        // The weight survives a retake. Nothing about it was wrong with the photograph, and
        // asking again for a figure the user typed a minute ago is the kind of friction that
        // makes people skip it the second time.
        _state.value = ScanUiState(
            step = ScanStep.FRONT,
            knownWeightKg = _state.value.knownWeightKg,
            enteredWeightKg = _state.value.enteredWeightKg,
        )
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
