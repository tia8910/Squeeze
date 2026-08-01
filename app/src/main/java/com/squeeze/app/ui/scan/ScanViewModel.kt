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
import com.squeeze.core.scan.BodyScanAnalyser
import com.squeeze.core.scan.ScaleRecovery
import com.squeeze.core.scan.ScanResult
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

/** Which photograph the user is being asked for. */
enum class ScanStep { FRONT, SIDE, ANALYSING, RESULT }

data class ScanUiState(
    val step: ScanStep = ScanStep.FRONT,
    val result: ScanResult? = null,
    val failure: DetectionFailure? = null,
    val saved: Boolean = false,
    val profileMissing: Boolean = false,
)

/**
 * Drives a two-photograph body scan.
 *
 * Photographs are held in memory for the duration of the scan and never written to disk.
 * Once the circumferences are extracted the bitmaps are dropped.
 *
 * Every path through here ends in a visible state change. An upload that fails to decode,
 * a photo with no person in it, and a crashed inference all land back on the capture step
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
    private var frontAspectRatio: Double = 1.0

    /** Entry point for the upload path: decode off the main thread, then process. */
    fun onPhotoPicked(uri: Uri) {
        val step = _state.value.step
        if (step != ScanStep.FRONT && step != ScanStep.SIDE) return

        _state.value = _state.value.copy(step = ScanStep.ANALYSING, failure = null)

        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { PhotoLoader.load(context, uri) }
            if (bitmap == null) {
                // A revoked URI grant or an unsupported format. Without this branch the
                // upload button silently does nothing, which reads as "upload is broken".
                _state.value = _state.value.copy(
                    step = if (frontBody == null) ScanStep.FRONT else ScanStep.SIDE,
                    failure = DetectionFailure.PhotoUnreadable,
                )
                return@launch
            }
            process(bitmap)
        }
    }

    /** Entry point for the live-capture path. */
    fun onPhotoCaptured(bitmap: Bitmap) {
        val step = _state.value.step
        if (step != ScanStep.FRONT && step != ScanStep.SIDE) return

        _state.value = _state.value.copy(step = ScanStep.ANALYSING, failure = null)
        viewModelScope.launch { process(bitmap) }
    }

    private suspend fun process(bitmap: Bitmap) {
        val aspectRatio = bitmap.width.toDouble() / bitmap.height.toDouble()

        // Inference is heavy and synchronous; off the main thread or the UI freezes.
        val detection = withContext(Dispatchers.Default) { detector.detect(bitmap) }

        when (detection) {
            is DetectionResult.Failure -> {
                _state.value = _state.value.copy(
                    step = if (frontBody == null) ScanStep.FRONT else ScanStep.SIDE,
                    failure = detection.reason,
                )
            }

            is DetectionResult.Success -> {
                if (frontBody == null) {
                    frontBody = detection.body
                    frontAspectRatio = aspectRatio
                    _state.value = _state.value.copy(step = ScanStep.SIDE, failure = null)
                } else {
                    analyse(frontBody!!, detection.body)
                }
            }
        }
    }

    private suspend fun analyse(front: DetectedBody, side: DetectedBody) {
        val profile = profileDao.get()
        if (profile == null) {
            // Height is the scale reference; without it pixels cannot become centimetres.
            _state.value = _state.value.copy(step = ScanStep.FRONT, profileMissing = true)
            return
        }

        val markers = AutomaticScanBuilder.build(
            frontProfile = front.profile,
            frontAnchors = front.anchors,
            sideProfile = side.profile,
            sideAnchors = side.anchors,
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
                warning.site == com.squeeze.core.scan.ScanSite.HIP &&
                Sex.valueOf(profile.sex) == Sex.MALE
        }

        _state.value = _state.value.copy(
            step = ScanStep.RESULT,
            result = result.copy(warnings = relevantWarnings),
        )
    }

    /** Stores the scan as a measurement, tagged so the trend engine weights it correctly. */
    fun save() {
        val result = _state.value.result ?: return

        viewModelScope.launch {
            val circumferences = result.circumferences
            measurementDao.insert(
                MeasurementEntity(
                    epochDay = LocalDate.now().toEpochDay(),
                    source = MeasurementSource.PHOTO.name,
                    weightKg = null,
                    neckCm = circumferences.neckCm,
                    waistCm = circumferences.waistCm,
                    hipCm = circumferences.hipCm,
                    chestCm = circumferences.chestCm,
                    thighCm = circumferences.thighCm,
                    armCm = circumferences.armCm,
                    calfCm = circumferences.calfCm,
                    chestMm = null,
                    abdomenMm = null,
                    thighMm = null,
                    tricepsMm = null,
                    suprailiacMm = null,
                    referenceBodyFatPercent = null,
                    note = "Photo scan",
                ),
            )
            _state.value = _state.value.copy(saved = true)
        }
    }

    fun restart() {
        frontBody = null
        frontAspectRatio = 1.0
        _state.value = ScanUiState()
    }

    override fun onCleared() {
        super.onCleared()
        detector.close()
    }
}
