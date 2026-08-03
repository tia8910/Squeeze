package com.squeeze.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.db.MeasurementDao
import com.squeeze.app.data.db.MeasurementEntity
import com.squeeze.app.data.db.ProfileDao
import com.squeeze.app.scan.BodyDetector
import com.squeeze.app.scan.DetectedBody
import com.squeeze.app.scan.DetectionFailure
import com.squeeze.app.scan.DetectionResult
import com.squeeze.app.scan.PhotoLoader
import com.squeeze.core.model.MeasurementSource
import com.squeeze.core.model.Sex
import com.squeeze.core.scan.AutomaticScanBuilder
import com.squeeze.core.scan.BodyProportions
import com.squeeze.core.scan.BodyScanAnalyser
import com.squeeze.core.scan.PostureAnalysis
import com.squeeze.core.scan.PostureFinding
import com.squeeze.core.scan.Proportion
import com.squeeze.core.scan.ScaleRecovery
import com.squeeze.core.scan.ScanResult
import com.squeeze.core.scan.ScanSite
import com.squeeze.core.scan.ScanWarning
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
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

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
                bodyHeightFraction = front.profile.bodyHeightFraction,
            ),
            imageAspectRatio = frontAspectRatio,
        )

        val result = analyser.analyse(markers)

        // The female Navy equation needs a hip; the male one does not. Reporting a missing
        // hip to a man would be noise, so the warning is filtered by profile here.
        val relevantWarnings = result.warnings.filterNot { warning ->
            warning is ScanWarning.MissingRequiredSite &&
                warning.site == ScanSite.HIP &&
                Sex.valueOf(profile.sex) == Sex.MALE
        }

        _state.value = _state.value.copy(
            step = ScanStep.RESULT,
            result = result.copy(warnings = relevantWarnings),
            // Ratios divide two measurements from the same photograph, so scale error
            // cancels — they are trustworthy even when the centimetres are not.
            proportions = BodyProportions.analyse(result.circumferences, profile.heightCm),
            posture = front.geometry?.let(PostureAnalysis::analyse).orEmpty(),
        )
    }

    /** Stores the scan as a measurement, tagged so the trend engine weights it correctly. */
    fun save() {
        val result = _state.value.result ?: return

        viewModelScope.launch {
            val c = result.circumferences
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
                    weightKg = null,
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
                    referenceBodyFatPercent = null,
                    note = if (result.depthAssumed) "Photo scan (front only)" else "Photo scan",
                ),
            )
            _state.value = _state.value.copy(saved = true)
        }
    }

    fun restart() {
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
