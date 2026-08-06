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
import com.squeeze.core.bodycomp.BodyFatCalculator
import com.squeeze.core.bodycomp.NeckEstimate
import com.squeeze.core.bodycomp.NeckEstimator
import com.squeeze.core.bodycomp.VisualAssessment
import com.squeeze.core.model.Circumferences
import com.squeeze.core.model.Profile
import com.squeeze.core.model.Sex
import com.squeeze.core.scan.PostureFinding
import com.squeeze.core.scan.Proportion
import com.squeeze.core.scan.ScanWarning
import com.squeeze.core.scan.WeightScaleCheck
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
                text = "Stand inside the guide, whole body from head to feet, plain background, " +
                    "even light, close-fitting clothing. Loose fabric is measured as body.",
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
                        "Your whole body needs to be visible, head to feet. Step further back."

                    DetectionFailure.PoseImplausible ->
                        "Stand upright and square to the camera, arms slightly away from your sides."

                    DetectionFailure.SegmentationFailed ->
                        "Your outline could not be separated from the background. A plainer " +
                            "background and more even lighting will help."

                    DetectionFailure.BodyCropped ->
                        "Your head or feet are outside the frame. The scan uses your full " +
                            "height to convert the photo into centimetres, so a cropped body " +
                            "makes every measurement too large. Step back until you fit " +
                            "entirely inside the guide."

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

        if (result.depthAssumed) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f),
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Measured from the front photo", style = MaterialTheme.typography.titleSmall)
                    Text(
                        // The honest framing: worse absolute number, nearly the same ability
                        // to detect change, because the assumption is a constant offset.
                        text = "Depth was assumed rather than photographed, so the absolute " +
                            "centimetres are less reliable than a two-photo scan. Because the " +
                            "assumption is the same every time, tracking change is barely " +
                            "affected — add a side photo when you want the numbers themselves " +
                            "to be right.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        ProportionsSection(state.proportions)
        PostureSection(state.posture)

        Text("Measurements", style = MaterialTheme.typography.titleSmall)

        Text(
            // Said plainly, because the previous version presented these as findings and
            // they are not: the silhouette gets sites wrong often enough that a value the
            // user has checked is worth more than one the pipeline is confident about.
            text = "These are the scan's best reading, not a verdict. Correct anything that " +
                "looks wrong before saving — a tape around the site takes ten seconds and is " +
                "more repeatable than any photo method.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val c = result.circumferences

        // First, because it is the figure the app keeps. Everything below it is the
        // circumference route, which is shown so the user can correct it and which is
        // dropped from the estimate entirely when it contradicts this one.
        state.shapeBodyFatPercent?.let { shape ->
            ShapeHeadline(shape)
        }

        var neck by remember(c) { mutableStateOf(c.neckCm.toField()) }
        var chest by remember(c) { mutableStateOf(c.chestCm.toField()) }
        var waist by remember(c) { mutableStateOf(c.waistCm.toField()) }
        var hip by remember(c) { mutableStateOf(c.hipCm.toField()) }
        var thigh by remember(c) { mutableStateOf(c.thighCm.toField()) }
        var weight by remember(c) { mutableStateOf("") }

        val edited = Circumferences(
            neckCm = neck.toCm(),
            chestCm = chest.toCm(),
            waistCm = waist.toCm(),
            hipCm = hip.toCm(),
            thighCm = thigh.toCm(),
        )

        MeasurementField("Neck", neck, { neck = it }, missing = c.neckCm == null)
        MeasurementField("Chest", chest, { chest = it }, missing = c.chestCm == null)
        MeasurementField("Waist", waist, { waist = it }, missing = c.waistCm == null)
        MeasurementField("Hip", hip, { hip = it }, missing = c.hipCm == null)
        MeasurementField("Thigh", thigh, { thigh = it }, missing = c.thighCm == null)
        MeasurementField("Weight (kg)", weight, { weight = it }, missing = false)

        val profile = state.profile
        val weightKg = weight.toCm()

        // Filled in for the user rather than offered behind a button. The neck is the site
        // the silhouette misses most often, and leaving the field blank stopped the entire
        // estimate on a measurement that height and weight predict about as well as anything
        // does. A blank field asks the user to go and find a tape; a filled one they can
        // correct asks nothing and is right more often than not.
        //
        // It is still theirs to overwrite, and the card beneath says plainly that it was
        // worked out rather than measured — an estimate presented as a measurement would be
        // the one dishonesty this screen cannot afford.
        var neckAutoFilled by remember(c) { mutableStateOf(false) }
        val neckEstimate = profile?.let {
            NeckEstimator.estimate(it.heightCm, weightKg, it.sex)
        }

        LaunchedEffect(neckEstimate, c) {
            if (c.neckCm == null && neck.isBlank() && neckEstimate != null) {
                neck = "%.1f".format(neckEstimate.centimetres)
                neckAutoFilled = true
            }
        }

        if (profile != null && neckAutoFilled) {
            NeckEstimateNote(neckEstimate)
        }

        if (profile != null) {
            ScaleCorrectionCard(profile, edited, weightKg) { factor ->
                chest = chest.scaledBy(factor)
                waist = waist.scaledBy(factor)
                hip = hip.scaledBy(factor)
                thigh = thigh.scaledBy(factor)
            }
        }

        // The whole point of making these editable: the estimate updates as the user fixes a
        // value, so a wrong neck stops being a dead end and becomes something they can see
        // themselves correct.
        BodyFatPreview(state.profile, edited)

        // Above the tape-equation preview in importance even though it sits below it on
        // screen, because it is the number that did not come through the circumferences.
        // The escape hatch, and after three measured attempts at reading adiposity off a
        // phone photo, the honest centre of the feature rather than a footnote to it. A
        // number entered here is stored as this scan's reference, which fits the offset for
        // this method immediately — see BodyCompositionRepository.fitCalibration.
        var knownPercent by remember(c) { mutableStateOf("") }
        KnownBodyFatCard(knownPercent) { knownPercent = it }

        state.shapeBodyFatPercent?.let { shape ->
            ShapeDisagreementNote(
                shapePercent = shape,
                tapePercent = profile?.let { BodyFatCalculator.navy(it, edited)?.percent },
            )
        }

        var visualPercent by remember(c) { mutableStateOf<Double?>(null) }

        if (profile != null) {
            VisualMatchSection(
                sex = profile.sex,
                selected = visualPercent,
                onSelect = { visualPercent = if (visualPercent == it) null else it },
                measured = BodyFatCalculator.navy(profile, edited)?.percent,
            )
        }

        if (result.warnings.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Worth checking", style = MaterialTheme.typography.titleSmall)
                    result.warnings.forEach { warning ->
                        Text(
                            text = when (warning) {
                                ScanWarning.FramingTooTight ->
                                    "You filled most of the frame, which stretches the " +
                                        "measurements. Stand further back next time."

                                is ScanWarning.LevelMismatch ->
                                    "Your ${warning.site.name.lowercase()} was found at " +
                                        "different heights in the two photos, so that " +
                                        "measurement is less reliable."

                                is ScanWarning.ImplausibleShape ->
                                    "The ${warning.site.name.lowercase()} shape looks off, " +
                                        "usually meaning the side photo was not square-on."

                                is ScanWarning.MissingRequiredSite ->
                                    "Could not find your ${warning.site.name.lowercase()}."

                                is ScanWarning.ImplausibleMeasurement ->
                                    // Parenthesised deliberately: `.format` binds to the
                                    // last literal of a concatenation, so without these the
                                    // %.0f sitting in an earlier segment reached the user
                                    // verbatim.
                                    ("Your ${warning.site.name.lowercase()} came out at " +
                                        "%.0f cm, which is outside the range plausible for " +
                                        "your height, so it was discarded rather than saved. " +
                                        "Usually this means the outline picked up the " +
                                        "background or your arms — try a plainer background " +
                                        "with arms clear of your sides.")
                                        .format(warning.centimetres)

                                is ScanWarning.ScaleFromLandmarks ->
                                    ("Your outline came out %.0f%% taller or shorter than " +
                                        "your body's landmarks say it should, so the scan " +
                                        "measured you against the landmarks instead. That is " +
                                        "the safer of the two, but slightly less precise — a " +
                                        "plainer background would let it use the sharper one.")
                                        .format(warning.disagreementPercent)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (!result.usableForBodyFat) {
            Text(
                text = "Not enough sites were found for a body fat estimate, but these " +
                    "measurements can still be saved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = { onSave(edited, weight.toCm(), visualPercent, knownPercent.toCm()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save measurements") }
        OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) { Text("Scan again") }
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
            text = "That is enough to measure. The extra views below improve accuracy, but " +
                "neither is required.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(onClick = onMeasureNow, modifier = Modifier.fillMaxWidth()) {
            Text("Measure now")
        }

        ExtraOption(
            title = "Add a side photo",
            captured = hasSide,
            detail = "A front view shows how wide you are but not how deep. Without a side " +
                "view the depth is assumed from population averages — which is a fixed " +
                "offset, so it barely affects tracking change, but it does shift the " +
                "absolute number.",
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

/**
 * Ratios, shown above the raw centimetres.
 *
 * A ratio divides two measurements taken from one photograph at one scale, so the scale
 * error cancels out. That makes these more trustworthy than the numbers they are computed
 * from, and worth more prominence than a footnote.
 */
@Composable
private fun ProportionsSection(proportions: List<Proportion>) {
    if (proportions.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Proportions", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "These divide one measurement by another from the same photo, so they " +
                "stay right even if the scale is slightly off.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        proportions.forEach { proportion ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = if (proportion.flagged) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f),
                    )
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(proportion.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "%.2f".format(proportion.value),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Text(
                        text = proportion.interpretation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Shoulder and hip alignment, read from landmarks the scan produced anyway. */
@Composable
private fun PostureSection(findings: List<PostureFinding>) {
    if (findings.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Alignment", style = MaterialTheme.typography.titleSmall)
        Text(
            // The caveat is not optional. One photograph cannot separate a real asymmetry
            // from how someone happened to stand, and saying so prevents a false alarm.
            text = "From one photo, so treat a single reading lightly — how you stood can " +
                "produce a tilt on its own. What matters is whether it repeats.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        findings.forEach { finding ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(finding.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "%.1f°".format(kotlin.math.abs(finding.degrees)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Text(
                        text = finding.interpretation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun Double?.toField(): String = this?.let { "%.1f".format(it) } ?: ""

private fun String.toCm(): Double? = trim().replace(',', '.').toDoubleOrNull()

/** Rescales a field's contents, leaving anything unparseable alone. */
private fun String.scaledBy(factor: Double): String =
    toCm()?.let { "%.1f".format(it * factor) } ?: this

/**
 * Offers a neck worked out from height and weight when the scan could not find one.
 *
 * The neck is the site a silhouette gets wrong most often and the one the Navy equation
 * leans on hardest, so missing it costs the whole estimate. It is also the site that varies
 * least between people of the same size, which is what makes inferring it defensible at all.
 */
/**
 * Says where the pre-filled neck came from.
 *
 * The field is already populated by the time this is read, so the job here is not to sell
 * the estimate but to stop it being mistaken for a measurement. The error is quoted in
 * points of body fat rather than centimetres, because centimetres of neck sound harmless
 * and two of them are worth about two points.
 */
@Composable
private fun NeckEstimateNote(estimate: NeckEstimate?) {
    if (estimate == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Neck was estimated", style = MaterialTheme.typography.titleSmall)
            Text(
                text = ("The scan could not find your neck, so it was worked out from your " +
                    "height and weight — deliberately not from the scan's own chest, which " +
                    "would inherit whatever the scan got wrong. Two people your size differ " +
                    "by about %.1f cm here, roughly %.1f points of body fat, so overwrite it " +
                    "with a tape measurement when you can.")
                    .format(
                        estimate.standardErrorCm,
                        estimate.standardErrorCm * NeckEstimator.BODY_FAT_POINTS_PER_CM,
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Flags a scan whose measurements describe a body that does not weigh what the user does.
 *
 * The failure this catches is invisible from inside the scan: scale error multiplies every
 * circumference at once, so each one stays individually plausible and every per-site check in
 * the app passes them. Weight is the only number here measured by an instrument, and a body's
 * girths and its mass cannot disagree.
 */
@Composable
private fun ScaleCorrectionCard(
    profile: Profile,
    edited: Circumferences,
    weightKg: Double?,
    onApply: (Double) -> Unit,
) {
    val finding = WeightScaleCheck.evaluate(edited, profile.heightCm, weightKg)
        ?.takeIf { it.significant }
        ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "These measurements do not match your weight",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = ("A body with these girths at your height would weigh about %.0f kg, " +
                    "but you entered %.0f kg. Every measurement in a photo scan is worked " +
                    "out from one height reference, so when that reference is off they are " +
                    "all wrong by the same %.0f%% — which is why each one looks believable " +
                    "on its own.")
                    .format(
                        finding.impliedWeightKg,
                        finding.actualWeightKg,
                        finding.errorPercent,
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { onApply(finding.correctionFactor) }) {
                Text("Rescale to match my weight")
            }
        }
    }
}

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
 * Body fat from whatever is currently in the fields.
 *
 * Recomputed on every keystroke, so correcting a neck shows the percentage appear. When it
 * cannot be computed the reason is named — "no body fat" with no explanation is what sent
 * the user back here in the first place.
 */
@Composable
private fun BodyFatPreview(profile: Profile?, circumferences: Circumferences) {
    if (profile == null) return

    val estimate = BodyFatCalculator.navy(profile, circumferences)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (estimate != null) {
                Text("Body fat", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "%.1f%%".format(estimate.percent),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "From the values above. Save to record it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text("Body fat cannot be calculated yet", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = missingReason(profile, circumferences),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Names the specific thing standing between these values and a percentage. */
private fun missingReason(profile: Profile, c: Circumferences): String {
    val needsHip = profile.sex == Sex.FEMALE

    // Bound to locals rather than smart-cast from the earlier null checks: `Circumferences`
    // lives in `:core`, and Kotlin will not smart-cast a public property across a module
    // boundary because the other module is free to change it under us.
    val neck = c.neckCm
    val waist = c.waistCm

    return when {
        neck == null -> "A neck measurement is needed. Put a tape around the narrowest " +
            "part of your neck, below the Adam's apple."

        waist == null -> "A waist measurement is needed. Measure at the narrowest point, " +
            "level with the navel for most people."

        needsHip && c.hipCm == null ->
            "A hip measurement is needed for the equation used here — around the widest point."

        waist <= neck ->
            "Your waist is not larger than your neck, which the equation cannot use. One of " +
                "the two is wrong — the neck is the usual culprit."

        else -> "The values above are outside the range the equation is defined for. Check " +
            "them against a tape."
    }
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
 * Body fat read from the outline's proportions, and how it compares with the measured route.
 *
 * The comparison is the point. Every other number on this screen is a function of the same
 * converted widths, so when scale recovery fails they all fail together and agreeing with
 * each other proves nothing. This one is computed from ratios between widths in the same
 * image, where the scale divides out exactly — so when the two disagree, the disagreement is
 * evidence rather than noise, and it points at the circumference route as the broken one.
 */
@Composable
private fun ShapeDisagreementNote(shapePercent: Double, tapePercent: Double?) {
    val gap = tapePercent?.let { kotlin.math.abs(it - shapePercent) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (gap != null && gap > 6.0) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "From your shape: %.1f%%".format(shapePercent),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Read from your outline's proportions — how wide your waist is " +
                    "relative to your shoulders and hips. It never converts pixels to " +
                    "centimetres, so nothing about your height in the frame can affect it. " +
                    "That makes it coarser than a good tape measurement and immune to the " +
                    "error that makes a bad scan wrong.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (gap != null && gap > 6.0) {
                Text(
                    text = ("Your measurements give %.1f%% and your shape gives %.1f%%. " +
                        "A gap this size is not noise — the circumferences above have most " +
                        "likely been scaled wrong, and the shape figure is the one that " +
                        "cannot be. Check the weight warning above, or correct a site by " +
                        "tape.").format(tapePercent, shapePercent),
                    style = MaterialTheme.typography.bodySmall,
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
private fun ShapeHeadline(shapePercent: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "From your shape",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "%.1f%%".format(shapePercent),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = "Read from how wide your waist is relative to your shoulders and " +
                    "hips. It never converts pixels to centimetres, so nothing about how " +
                    "you were framed can reach it — which is why the scan keeps this figure " +
                    "and not the one the circumferences give.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
