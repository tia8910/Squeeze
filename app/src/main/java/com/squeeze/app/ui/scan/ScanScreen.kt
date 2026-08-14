package com.squeeze.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.SystemClock
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.squeeze.app.audio.LocalSoundEngine
import com.squeeze.app.scan.DetectionFailure
import com.squeeze.core.audio.Cue
import com.squeeze.core.bodycomp.VisualAssessment
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.HeroMetric
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Sex
import com.squeeze.core.scan.ScanFraming
import com.squeeze.core.scan.SilhouetteBodyFat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Guided two-photograph body scan.
 *
 * No captured photo is ever displayed back or written to disk. Images exist only long
 * enough for inference; what survives is a set of circumferences.
 */
@Composable
fun ScanScreen(
    onFinished: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Kept for permission checks only; photo decoding lives in the ViewModel now so the
    // upload path has visible states end to end.

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    // Deliberately not requested on entry: uploading needs no camera, and a reflexive denial
    // would block the capture path permanently.

    LaunchedEffect(state.saved) { if (state.saved) onFinished() }

    when (state.step) {
        ScanStep.WEIGHT -> WeightStep(
            knownWeightKg = state.knownWeightKg,
            onConfirm = viewModel::confirmWeight,
        )

        ScanStep.OPTIONAL_EXTRAS -> OptionalExtrasStep(
            hasSide = state.hasSide,
            hasBack = state.hasBack,
            onMeasureNow = viewModel::measureNow,
            onAddSide = viewModel::addSidePhoto,
            onAddBack = viewModel::addBackPhoto,
        )

        ScanStep.FRONT, ScanStep.SIDE, ScanStep.BACK -> CaptureStep(
            step = state.step,
            failure = state.failure,
            profileMissing = state.profileMissing,
            hasCameraPermission = hasCameraPermission,
            onRequestCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onCapture = viewModel::onPhotoCaptured,
            onPhotoPicked = viewModel::onPhotoPicked,
            onCheckFraming = viewModel::checkFraming,
        )

        ScanStep.ANALYSING -> AnalysingStep()

        ScanStep.RESULT -> ResultStep(
            state = state,
            onSave = viewModel::save,
            onRetake = viewModel::restart,
        )
    }
}

/**
 * The first thing a scan asks for, before the camera.
 *
 * A weight used to be an optional field on the result screen, collected after the photograph
 * and easy to skip. That made it look like a footnote, and it is not one: when the outline
 * cannot resolve the body — which is most photographs of most people, because the ratio the
 * method reads is flat across the lean range — the reported figure comes from height, weight
 * and sex. A scan taken without a weight cannot produce a figure about the person at all. It
 * falls back to the leanest number the method is allowed to claim, which is the same for
 * everybody who lands there.
 *
 * Prefilled with the last recorded weight, so the usual case is one tap. Skippable, because a
 * scan without a weight is worse than one with it and much better than one not taken.
 */
@Composable
private fun WeightStep(knownWeightKg: Double?, onConfirm: (Double?) -> Unit) {
    var weight by remember(knownWeightKg) {
        mutableStateOf(knownWeightKg?.let { "%.1f".format(it) }.orEmpty())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Before we start", style = MaterialTheme.typography.headlineSmall)

        Text(
            text = "What do you weigh today?",
            style = MaterialTheme.typography.bodyLarge,
        )

        MeasurementField("Weight (kg)", weight, { weight = it }, missing = false)

        InfoCard(
            "A photograph shows your outline, and an outline cannot tell a lean body from a " +
                "very lean one — what separates them is definition, which a silhouette " +
                "throws away. Your weight is what turns the scan into a figure about you " +
                "rather than the leanest figure the method is allowed to claim. It is also " +
                "what the plausibility check and the lean-mass trend run on.",
        )

        PrimaryButton(
            text = "Continue",
            onClick = { onConfirm(weight.toCm()) },
            modifier = Modifier.fillMaxWidth(),
        )

        TextButton(
            onClick = { onConfirm(null) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Skip — scan without it")
        }
    }
}

@Composable
private fun CaptureStep(
    step: ScanStep,
    failure: DetectionFailure?,
    profileMissing: Boolean,
    hasCameraPermission: Boolean,
    onRequestCamera: () -> Unit,
    onCapture: (Bitmap) -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    /**
     * Judges a live preview frame, returning advice to fix or null when it is worth shooting.
     *
     * Suspending and supplied from outside because it runs the real detector: the point of
     * auto-capture is that the frame it fires on has already passed the same checks the scan
     * will apply afterwards, so a photo it takes cannot be rejected for framing.
     */
    onCheckFraming: suspend (Bitmap) -> String?,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    val scope = rememberCoroutineScope()
    val sound = LocalSoundEngine.current

    var useFrontCamera by remember { mutableStateOf(false) }
    var timerSeconds by remember { mutableIntStateOf(DEFAULT_TIMER_SECONDS) }
    var countdown by remember { mutableIntStateOf(0) }

    var autoDetect by remember { mutableStateOf(false) }
    var framingHint by remember { mutableStateOf<String?>(null) }

    // Held so the zoom control has something to talk to. CameraControl only exists once a
    // camera is bound, so this is null until the provider callback has run.
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            // Only the newest frame matters. Queueing them would mean the hint describes a
            // pose the user has already moved out of.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(onPhotoPicked) }

    fun shoot() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // Only on a frame that actually decoded. A shutter sound for a capture
                    // that produced nothing would tell the user the opposite of the truth.
                    image.decodeToBitmap()?.let { bitmap ->
                        sound?.play(Cue.CAPTURE)
                        onCapture(bitmap)
                    }
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    // The step is unchanged, so the user simply taps again.
                    exception.printStackTrace()
                }
            },
        )
    }

    // Auto-detect: watch the preview and take the photo when the framing is actually right,
    // rather than making the user guess when to start a timer. The two failure modes this
    // removes are the two the scan cannot recover from — a body cut off at the feet, and a
    // body turned away from the lens — because both are decided before the shutter and no
    // amount of later arithmetic can undo either.
    LaunchedEffect(autoDetect, step) {
        if (!autoDetect) {
            imageAnalysis.clearAnalyzer()
            framingHint = null
            return@LaunchedEffect
        }

        var busy = false
        var lastCheck = 0L
        var goodStreak = 0

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
            val now = SystemClock.elapsedRealtime()
            // Two models per frame at preview rate would heat the phone and drop the
            // preview. A person getting into position does not move faster than this.
            if (busy || now - lastCheck < AUTO_DETECT_INTERVAL_MS) {
                proxy.close()
                return@setAnalyzer
            }
            lastCheck = now
            busy = true

            val frame = runCatching { proxy.toBitmap() }.getOrNull()
            val degrees = proxy.imageInfo.rotationDegrees
            proxy.close()

            if (frame == null) {
                busy = false
                return@setAnalyzer
            }

            scope.launch {
                val hint = onCheckFraming(frame.rotated(degrees))
                framingHint = hint

                if (hint == null) {
                    goodStreak++
                    // Two consecutive good frames, not one. A single frame catches the
                    // moment an arm happens to swing clear and fires while the user is
                    // still walking into place.
                    if (goodStreak >= AUTO_DETECT_CONFIRMATIONS && countdown == 0) {
                        goodStreak = 0
                        countdown = AUTO_DETECT_COUNTDOWN_SECONDS
                        while (countdown > 0) {
                            delay(1000)
                            countdown--
                        }
                        shoot()
                    }
                } else {
                    goodStreak = 0
                }
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            val previewView = remember { PreviewView(context) }

            // Binding belongs in an effect keyed on what it depends on, not in AndroidView's
            // update lambda. That lambda runs on every recomposition — every countdown tick,
            // every zoom nudge — and each run called unbindAll() and rebound the camera,
            // which is why the preview stuttered and the lens sometimes came back detached.
            LaunchedEffect(useFrontCamera, autoDetect) {
                val provider = ProcessCameraProvider.getInstance(context).awaitProvider()
                val preview = CameraPreview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                provider.unbindAll()
                camera = runCatching {
                    val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
                    else CameraSelector.DEFAULT_BACK_CAMERA

                    // The analyser is only bound when the user asked for it. It runs the
                    // same pose and segmentation models the scan itself uses, which is not
                    // something to have running against the battery on every capture screen.
                    if (autoDetect) {
                        provider.bindToLifecycle(
                            lifecycleOwner, selector, preview, imageCapture, imageAnalysis,
                        )
                    } else {
                        provider.bindToLifecycle(
                            lifecycleOwner, selector, preview, imageCapture,
                        )
                    }
                }.getOrNull()
                zoomRatio = 1f

                // Focus and expose for the body, not the scene. Left to its own devices the
                // camera meters the whole frame, so a bright wall behind the subject pulls
                // the exposure down and flattens exactly the abdominal contrast the scan
                // needs to read. Metering on the centre — where the capture guide puts the
                // torso — costs nothing and sharpens the mask edge as well.
                camera?.let { bound ->
                    runCatching {
                        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                        val torso = factory.createPoint(0.5f, TORSO_METERING_Y)
                        bound.cameraControl.startFocusAndMetering(
                            FocusMeteringAction.Builder(torso, FocusMeteringAction.FLAG_AF)
                                .addPoint(torso, FocusMeteringAction.FLAG_AE)
                                .build(),
                        )
                    }
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    // Pinch to zoom, because the framing this method wants puts the phone
                    // several metres away and the body then occupies a fraction of a wide
                    // lens. Zooming in is also the least distorting way to fill the frame:
                    // it trades resolution for a longer effective focal length, where
                    // stepping closer instead adds the perspective error the scan warns
                    // about.
                    .pointerInput(camera) {
                        detectTransformGestures { _, _, gestureZoom, _ ->
                            val control = camera?.cameraControl ?: return@detectTransformGestures
                            val state = camera?.cameraInfo?.zoomState?.value
                                ?: return@detectTransformGestures
                            val next = (state.zoomRatio * gestureZoom)
                                .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                            control.setZoomRatio(next)
                            zoomRatio = next
                        }
                    },
                factory = { previewView },
            )

            CaptureGuideOverlay()
            if (countdown > 0) CountdownOverlay(countdown)
        } else {
            CameraPermissionRequired(onRequest = onRequestCamera)
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepCard(step)
            failure?.let { FailureCard(it) }
            if (profileMissing) {
                InfoCard(
                    "Set your height in Settings first — the scan uses it to convert the " +
                        "photo into real measurements.",
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (hasCameraPermission) {
                // What the detector currently makes of the frame. Shown only while
                // auto-capture is armed, because otherwise it is a running commentary on a
                // photo the user has not asked to take.
                if (autoDetect) {
                    InfoCard(framingHint ?: "Framing looks good — hold still.")
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = autoDetect,
                        onClick = { autoDetect = !autoDetect },
                        leadingIcon = if (autoDetect) {
                            { Icon(Icons.Default.Visibility, null, Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                        label = { Text("Auto") },
                    )

                    // Two fixed steps rather than a slider. The useful zooms here are "as
                    // wide as possible" and "framed on the body from across the room", and a
                    // slider invites fiddling with a setting that has one right answer per
                    // room.
                    ZOOM_STEPS.forEach { ratio ->
                        val control = camera?.cameraControl
                        val supported = camera?.cameraInfo?.zoomState?.value
                            ?.let { ratio <= it.maxZoomRatio } ?: false
                        if (!supported) return@forEach

                        FilterChip(
                            selected = zoomRatio in (ratio - 0.05f)..(ratio + 0.05f),
                            onClick = {
                                control?.setZoomRatio(ratio)
                                zoomRatio = ratio
                            },
                            label = { Text("%.0f×".format(ratio)) },
                        )
                    }
                }

                // A self-timer is not a nicety here: the framing that produces a good
                // measurement puts the user metres from the phone, where the shutter is out
                // of reach. Without it, every self-captured scan is taken at arm's length,
                // which is exactly the framing the method handles worst.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TIMER_OPTIONS.forEach { seconds ->
                        FilterChip(
                            selected = timerSeconds == seconds,
                            onClick = { timerSeconds = seconds },
                            leadingIcon = if (timerSeconds == seconds) {
                                { Icon(Icons.Default.Timer, null, Modifier.size(16.dp)) }
                            } else {
                                null
                            },
                            label = { Text(if (seconds == 0) "Off" else "${seconds}s") },
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = countdown == 0,
                        onClick = {
                            if (timerSeconds == 0) {
                                shoot()
                            } else {
                                scope.launch {
                                    countdown = timerSeconds
                                    while (countdown > 0) {
                                        delay(1000)
                                        countdown--
                                    }
                                    shoot()
                                }
                            }
                        },
                    ) {
                        Text(
                            if (countdown > 0) {
                            "Get into position…"
                        } else {
                            when (step) {
                                ScanStep.SIDE -> "Capture side"
                                ScanStep.BACK -> "Capture back"
                                else -> "Capture front"
                            }
                        },
                        )
                    }

                    FilledTonalButton(onClick = { useFrontCamera = !useFrontCamera }) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera")
                    }
                }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, Modifier.size(18.dp))
                Text(
                    text = when (step) {
                        ScanStep.SIDE -> "Upload side photo"
                        ScanStep.BACK -> "Upload back photo"
                        else -> "Upload front photo"
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StepCard(step: ScanStep) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Only the front photo is required, so the bar shows the required step as
            // complete rather than implying two more are owed.
            LinearProgressIndicator(
                progress = { if (step == ScanStep.FRONT) 0.34f else 1f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = when (step) {
                    ScanStep.SIDE -> "Side view — turn side-on"
                    ScanStep.BACK -> "Back view — turn around"
                    else -> "Front view — face the camera"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Shoulders and hips both in shot, arms held clear of your sides, " +
                    "plain background, even light, close-fitting clothing. Loose fabric is " +
                    "measured as body. Head to feet is optional — it adds tape measurements " +
                    "in centimetres, and framing closer on your trunk reads your shape " +
                    "better.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CameraPermissionRequired(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera access", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Allow the camera to take the scan photos here, or upload two photos you " +
                "already have. Either way the scan runs entirely on this device — this app " +
                "has no internet permission at all, so nothing can be uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest) { Text("Allow camera") }
    }
}

@Composable
private fun InfoCard(body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Text(body, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FailureCard(failure: DetectionFailure) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Could not measure that photo", style = MaterialTheme.typography.titleSmall)
            Text(
                // Every message names something the user can change. "Detection failed" is
                // useless; "step back so your feet are in frame" is actionable.
                text = when (failure) {
                    DetectionFailure.NoPersonDetected ->
                        "No person found. Check the lighting and that you are fully in frame."

                    DetectionFailure.BodyNotFullyVisible ->
                        "Too little of you is in shot to measure. Frame at least from your " +
                            "shoulders to below your hips."

                    DetectionFailure.PoseImplausible ->
                        "Stand upright and square to the camera, arms slightly away from your sides."

                    DetectionFailure.SegmentationFailed ->
                        "Your outline could not be separated from the background. A plainer " +
                            "background and more even lighting will help."

                    DetectionFailure.BodyCropped ->
                        "Your shoulders and hips were not both in the picture, so there is " +
                            "no waist to measure. Your head and feet do not have to be in " +
                            "shot — frame from your shoulders to below your hips and the " +
                            "scan will read your shape from that."

                    DetectionFailure.PhotoUnreadable ->
                        "That photo could not be opened. Try picking it again, or choose a " +
                            "JPEG or PNG from your gallery."

                    DetectionFailure.ScaleUnreliable ->
                        "Your outline and your body's landmarks disagree about how tall you " +
                            "appear, which means the outline picked up something that is not " +
                            "you — a mirror frame, a doorway, or a strong shadow. Every " +
                            "measurement is worked out from your height in the photo, so " +
                            "rather than give you numbers that are all wrong by the same " +
                            "amount, the scan stopped. Try a plainer background."

                    // Carries its own advice: the check knows which way the pose was off,
                    // and "stand square" alone would not say what to correct.
                    is DetectionFailure.NotFacingCamera -> failure.advice
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AnalysingStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("Measuring", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Running on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultStep(
    state: ScanUiState,
    onSave: (Circumferences, Double?, Double?, Double?) -> Unit,
    onRetake: () -> Unit,
) {
    val result = state.result ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Scan results", style = MaterialTheme.typography.headlineSmall)

        // Four things, and nothing else.
        //
        // The screen used to show the circumference route in full — seven editable girths, a
        // body-fat figure derived from them, a warning when they disagreed with the weight,
        // and another when they disagreed with the outline. All of that described a
        // measurement the app no longer uses: a contradicted circumference set is dropped
        // from the estimate outright, so showing it invited the user to correct numbers that
        // were not going to be believed either way. Editing a value that feeds nothing is
        // worse than not offering the edit.
        //
        // What is left is what the answer is actually made of: the figure read from the
        // outline, the weight, an optional true value to calibrate against, and the
        // appearance match. The girths are still measured and still saved for history — they
        // are simply no longer presented as something to argue with.
        val c = result.circumferences

        // Declared above the headline because the headline depends on it. When the outline
        // lands on its plateau it has no figure of its own to give — it contributes a bound —
        // and what gets printed comes from the body's build, which needs a weight. Prefilled
        // from the last recorded one so the number is right before anything is typed, and
        // recomputed on every keystroke so correcting the weight corrects the answer.
        var weight by remember(c) {
            mutableStateOf(state.knownWeightKg?.let { "%.1f".format(it) }.orEmpty())
        }
        var knownPercent by remember(c) { mutableStateOf("") }
        var visualPercent by remember(c) { mutableStateOf<Double?>(null) }

        val shape = state.resolvedShape(weight.toCm())

        shape?.let { ShapeHeadline(it, hasWeight = weight.toCm() != null) }

        // Said before the advice, because it changes what the advice is for. A trunk scan is
        // not a degraded full-body scan — it is the framing the shape figure actually wants,
        // and the only thing it gives up is a set of centimetres the figure never used.
        if (state.framing == ScanFraming.TORSO) {
            InfoCard(
                "Measured from your trunk. Your waist, shoulders and hips were all in " +
                    "shot, which is everything the shape reading needs — and closer " +
                    "framing puts far more detail on your midsection. Tape measurements " +
                    "in centimetres need your full height in the picture, so this scan " +
                    "does not produce them.",
            )
        }

        // The abdominal reading gets its own line rather than being folded silently into the
        // headline, because it answers a different question from the outline above it and
        // was measured on a different axis. Where they disagree, that disagreement is
        // information: the front view reads width, this reads depth, and a body can be
        // narrow and deep.
        state.abdominalBodyFatPercent?.let { percent ->
            InfoCard(
                "From your side profile: about %.0f%%. This is your abdomen measured " +
                    "against your own ribcage — the axis abdominal fat actually moves " +
                    "along, and the one a front photo cannot see."
                    .format(percent),
            )
        } ?: InfoCard(
            "No side photo, so your abdomen was not measured — only your outline from the " +
                "front. Fat accumulates on the abdomen far more in depth than in width, so " +
                "a side photo is the single biggest improvement available to this scan.",
        )

        // Above the lighting note, because it is the bigger error and the easier fix. An arm
        // resting against the waist does not blur the reading, it replaces it: the app cuts
        // the arm off using a skeleton-derived bound, and what it then measures is that
        // bound. Two different bodies come back with nearly the same number.
        state.poseAdvice?.let { InfoCard(it) }

        // Only ever present when something is wrong with the light, and when it is, it is
        // the reason to distrust the figure directly above it.
        state.lightingAdvice?.let { InfoCard(it) }

        MeasurementField("Weight (kg)", weight, { weight = it }, missing = false)

        KnownBodyFatCard(knownPercent) { knownPercent = it }

        state.profile?.let { profile ->
            VisualMatchSection(
                sex = profile.sex,
                selected = visualPercent,
                onSelect = { visualPercent = if (visualPercent == it) null else it },
                measured = shape?.percent,
            )
        }

        AccuracyDisclaimer()

        Button(
            onClick = { onSave(c, weight.toCm(), visualPercent, knownPercent.toCm()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }

        OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
            Text("Scan again")
        }
    }
}

/**
 * Decodes a captured frame in memory.
 *
 * Named to avoid colliding with CameraX's own `ImageProxy.toBitmap()` extension.
 */
private fun ImageProxy.decodeToBitmap(): Bitmap? {
    val plane = planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val decoded = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        .getOrNull() ?: return null

    // The sensor is mounted sideways in almost every phone, so the JPEG that comes back is
    // in sensor orientation and the upright angle lives in imageInfo.rotationDegrees.
    // Ignoring it handed the pipeline a body lying on its side, which is not a subtle
    // failure: the pose model's ordering check requires chin above shoulders above hips,
    // and a rotated frame satisfies none of it. Every camera capture was being measured
    // sideways or refused outright, while gallery uploads worked because the loader there
    // reads EXIF.
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return decoded

    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return runCatching {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }.getOrDefault(decoded)
}

private val TIMER_OPTIONS = listOf(0, 5, 10, 15)
private const val DEFAULT_TIMER_SECONDS = 10

/**
 * Where down the frame the torso sits when the subject fills the capture guide.
 *
 * Slightly above centre: the guide centres the whole body, so the midsection lands above
 * the middle of the frame rather than on it.
 */
private const val TORSO_METERING_Y = 0.45f

/** Wide, and framed from across a room. See the chip row for why these two. */
private val ZOOM_STEPS = listOf(1f, 2f)

/** How often a preview frame is put through the detector while auto-capture is armed. */
private const val AUTO_DETECT_INTERVAL_MS = 700L

/** Consecutive good frames required before the countdown starts. */
private const val AUTO_DETECT_CONFIRMATIONS = 2

/** Long enough to drop the arm that was holding the phone, short enough not to drift. */
private const val AUTO_DETECT_COUNTDOWN_SECONDS = 3

/** Rotates a preview frame upright, matching what [decodeToBitmap] does for a capture. */
private fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return runCatching {
        Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }.getOrDefault(this)
}

/** Suspends until CameraX hands over the provider, instead of nesting a listener callback. */
private suspend fun ListenableFuture<ProcessCameraProvider>.awaitProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        addListener(
            { runCatching { get() }.fold(continuation::resume) { continuation.cancel(it) } },
            Runnable::run,
        )
    }

/**
 * The decision point after the required photograph.
 *
 * A front view alone is enough to measure. The extras are offered with a plain statement of
 * what each buys, because a user who does not know why a side photo helps will either skip
 * it and wonder why the number is off, or take it and resent the extra step. Neither is
 * necessary when the trade-off fits in a sentence.
 */
@Composable
private fun OptionalExtrasStep(
    hasSide: Boolean,
    hasBack: Boolean,
    onMeasureNow: () -> Unit,
    onAddSide: () -> Unit,
    onAddBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Front photo captured", style = MaterialTheme.typography.headlineSmall)

        Text(
            text = "Enough to measure — but the side photo is the one worth taking. Fat on " +
                "the abdomen accumulates far more in depth than in width, and a front view " +
                "cannot see depth at all. It is the difference between guessing at your " +
                "midsection and measuring it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(onClick = onMeasureNow, modifier = Modifier.fillMaxWidth()) {
            Text("Measure now")
        }

        ExtraOption(
            title = "Add a side photo — recommended",
            captured = hasSide,
            detail = "The one measurement a front photo cannot make. Your abdomen is " +
                "measured against your own ribcage, in the same picture, so nothing about " +
                "your height or the framing can affect it — and unlike the front view, an " +
                "arm at your side cannot get in the way of it.",
            onClick = onAddSide,
        )

        ExtraOption(
            title = "Add a back photo",
            captured = hasBack,
            detail = "Measures the same width as the front from the other side, and the two " +
                "are averaged. A small precision gain — worth it if you are chasing a " +
                "reliable trend, skippable otherwise.",
            onClick = onAddBack,
        )
    }
}

@Composable
private fun ExtraOption(
    title: String,
    captured: Boolean,
    detail: String,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (captured) {
                    Text(
                        text = "Captured",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(if (captured) "Retake" else "Add photo")
            }
        }
    }
}

private fun Double?.toField(): String = this?.let { "%.1f".format(it) } ?: ""

private fun String.toCm(): Double? = trim().replace(',', '.').toDoubleOrNull()

/**
 * Offers a neck worked out from height and weight when the scan could not find one.
 *
 * The neck is the site a silhouette gets wrong most often and the one the Navy equation
 * leans on hardest, so missing it costs the whole estimate. It is also the site that varies
 * least between people of the same size, which is what makes inferring it defensible at all.
 */
/**
 * One editable measurement.
 *
 * A site the scan could not measure is shown empty and labelled, rather than hidden. Hiding
 * it loses the most useful thing on the screen: that the app knows it is missing and the
 * user can supply it in ten seconds with a tape.
 */
@Composable
private fun MeasurementField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    missing: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(5)) },
        label = { Text(label) },
        supportingText = if (missing) {
            { Text("The scan could not measure this") }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Lets the user place themselves on the appearance ladder.
 *
 * This is the only input on the screen that does not come from a circumference, and that is
 * exactly why it is here. Every other route to a percentage runs through the same waist, so
 * when the scan's scale is off they are all off together and merging them narrows the
 * interval around a number that is confidently wrong. What someone looks like cannot inherit
 * a scale error.
 *
 * Described rather than illustrated. Reference photographs are of particular strangers with
 * particular frames, and a lean-but-narrow person comparing themselves against a muscular
 * ten-per-cent photo reads high every time. The markers below are what an assessor actually
 * checks, they apply to any build, and they cost nothing to ship.
 *
 * Optional throughout. A user who does not want to judge their own appearance skips it and
 * loses nothing they had before.
 */
@Composable
private fun VisualMatchSection(
    sex: Sex,
    selected: Double?,
    onSelect: (Double) -> Unit,
    measured: Double?,
) {
    val bands = VisualAssessment.bandsFor(sex)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Which describes you? — optional", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Everything above was worked out from your measurements. This is the " +
                    "one thing the tape cannot see, so it checks the rest rather than " +
                    "repeating it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            bands.forEach { band ->
                val chosen = selected == band.percent
                Card(
                    onClick = { onSelect(band.percent) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (chosen) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "${band.label} · about %.0f%%".format(band.percent),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = band.markers,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // The disagreement is the useful part. A scan saying twenty per cent to someone
            // who can see their abs is not a small error to be averaged quietly away - the
            // two disagree about something the user can settle by looking down, and saying
            // so is more honest than blending them and reporting the midpoint.
            if (selected != null && measured != null && kotlin.math.abs(selected - measured) > 6.0) {
                Text(
                    text = ("Your measurements give %.0f%% and what you picked is nearer " +
                        "%.0f%%. That gap is too large to be noise — the scan has most " +
                        "likely mis-measured a site. Both are saved, and the estimate will " +
                        "sit between them, but a tape reading would settle it.")
                        .format(measured, selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Lets the user tell the app what they actually are, once.
 *
 * Three separate photo-native methods were built and measured against labelled reference
 * charts, and all three failed in the same place: below about twenty per cent, what
 * distinguishes one body from another is abdominal definition, and that is swamped by
 * lighting, pose and camera before it ever reaches a number. A phone photograph analysed
 * on-device cannot resolve the lean range, and no amount of further arithmetic changes that.
 *
 * What it can do is be told once. The app's founding claim is that a method's error is a
 * systematic, person-specific offset — the same size in the same direction every time — and
 * a single honest anchor removes it permanently. Everything after this is corrected, and the
 * trend, which was always the point, was never affected by the offset anyway.
 *
 * This was previously reachable only by adding a separate manual entry, and even then the
 * fit accepted tape measurements alone, so a photo scan could never be calibrated at all.
 * That is why the number stayed wrong however many methods were added to the pool.
 */
@Composable
private fun KnownBodyFatCard(value: String, onValueChange: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Know your real number?", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "If you have a DEXA, a BodPod, or you simply know roughly where you " +
                    "are, put it here once. The app will work out how far this method sits " +
                    "from the truth for your body and correct every future scan by the same " +
                    "amount. It is the fastest way to make the number mean something.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.filter { ch -> ch.isDigit() || ch == '.' }.take(4)) },
                label = { Text("Actual body fat (%)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The figure the scan actually keeps.
 *
 * Promoted above the measurements because it is what the estimate is built from, not a
 * second opinion on it. Everything below reaches a percentage through
 * `widthCm = widthPixels / bodyHeightPixels × height`, and that division is where a scan
 * goes wrong — one bad mask edge moves every girth together while each stays individually
 * plausible. This number never performs that division, so the failure cannot reach it.
 *
 * It is not presented as precise, and the copy says so. It is presented as the one reading
 * on this screen whose errors are its own.
 */
@Composable
private fun ShapeHeadline(estimate: BodyFatEstimate, hasWeight: Boolean) {
    // A reading carrying the plateau's interval is not a measurement of this body's fat — the
    // outline could not separate lean from very lean, and said so by widening to ±9. The card
    // has to say the same thing, because a number in display type reads as certain no matter
    // what is printed under it.
    val bounded = estimate.standardErrorPercent >= SilhouetteBodyFat.PLATEAU_ERROR_PERCENT
    val low = (estimate.percent - estimate.standardErrorPercent).coerceAtLeast(3.0)
    val high = estimate.percent + estimate.standardErrorPercent
    val dark = LocalIsDarkTheme.current

    BrandCard(Modifier.fillMaxWidth()) {
        HeroMetric(
            value = "%.1f".format(estimate.percent),
            unit = "%",
            label = if (bounded) "Best estimate" else "From your shape",
            // Shown for every reading, not only the uncertain ones. Every figure in this app
            // has an interval; hiding it is the core dishonesty of this app category, and a
            // single number to one decimal place claims a precision no method here has.
            interval = "most likely %.0f–%.0f%%".format(low, high),
            band = if (bounded) "Not resolved by the photo" else null,
        )

        Text(
            text = when {
                bounded && hasWeight ->
                    "Your outline could not settle this one. What separates a lean body " +
                        "from a very lean one is abdominal definition, and a silhouette " +
                        "throws that away — it knows your edge and nothing inside it. So " +
                        "this figure comes from your height, weight and age instead, " +
                        "which are measured rather than inferred. A side photo, a tape " +
                        "measurement at your navel, or matching yourself to the pictures " +
                        "below will all beat it."

                bounded ->
                    "Your outline could not settle this one, and without a weight there " +
                        "is nothing left to settle it with — so this is the leanest " +
                        "figure the shape reading is allowed to claim, not a reading of " +
                        "you. Enter your weight above and it becomes an estimate of your " +
                        "body."

                else ->
                    "Read from how wide your waist is relative to your shoulders and " +
                        "hips. It never converts pixels to centimetres, so nothing about " +
                        "how you were framed can reach it — which is why the scan keeps " +
                        "this figure and not the one the circumferences give."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (dark) Brand.DarkMuted else Brand.Muted,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * What this number is and is not.
 *
 * Placed immediately above Save, where it is read at the moment the figure is about to be
 * committed rather than skimmed past on the way in.
 *
 * Written after three photo-native methods were built and measured against labelled
 * reference photographs, and all three failed the same way below twenty per cent: what
 * separates a lean body from a very lean one is abdominal definition, and in an uncontrolled
 * phone photograph that signal sits underneath lighting, pose and camera. So this is not a
 * disclaimer in the legal sense of covering the app — it is the honest description of a
 * measurement whose limits are known and measured, and it names them specifically enough to
 * be acted on rather than hedging in general.
 *
 * The last line is the one that matters. Absolute accuracy is a systematic offset that
 * cancels when you compare yourself against yourself, and the direction of travel survives
 * every error listed above it. Someone who reads only that sentence has taken the right
 * thing from the screen.
 */
@Composable
private fun AccuracyDisclaimer() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Treat this as an estimate",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "A single percentage read from one photograph is not a measurement in " +
                    "the way a DEXA scan is, and it can be several points out. What it " +
                    "depends on:",
                style = MaterialTheme.typography.bodySmall,
            )
            listOf(
                "Lighting, most of all. A lamp off to one side carves shadows that look " +
                    "like muscle definition; flat overhead light hides it. Even light from " +
                    "in front of you is best.",
                "Photo quality — focus, distance, and whether your outline separates " +
                    "cleanly from the background.",
                "Pose and clothing. Standing square, arms clear of your sides, and the same " +
                    "clothing each time.",
                "Your build. Two people with the same outline can differ by several points " +
                    "depending on the muscle underneath it.",
            ).forEach { line ->
                Text(
                    text = "· $line",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "So watch the direction, not the number. Whatever this method gets " +
                    "wrong, it gets wrong the same way every time — which means the trend " +
                    "across scans is trustworthy even when a single reading is not. Keep " +
                    "your conditions consistent and the line will tell you the truth long " +
                    "before any one figure does.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
