package com.squeeze.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.squeeze.app.scan.DetectionFailure
import com.squeeze.core.scan.ScanWarning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        ScanStep.FRONT, ScanStep.SIDE -> CaptureStep(
            step = state.step,
            failure = state.failure,
            profileMissing = state.profileMissing,
            hasCameraPermission = hasCameraPermission,
            onRequestCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onCapture = viewModel::onPhotoCaptured,
            onPhotoPicked = viewModel::onPhotoPicked,
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
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    val scope = rememberCoroutineScope()

    var useFrontCamera by remember { mutableStateOf(false) }
    var timerSeconds by remember { mutableIntStateOf(DEFAULT_TIMER_SECONDS) }
    var countdown by remember { mutableIntStateOf(0) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(onPhotoPicked) }

    fun shoot() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    image.decodeToBitmap()?.let(onCapture)
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    // The step is unchanged, so the user simply taps again.
                    exception.printStackTrace()
                }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                // Keyed on the lens so flipping rebinds the provider rather than leaving
                // the old camera attached.
                factory = { ctx -> PreviewView(ctx) },
                update = { previewView ->
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = CameraPreview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
                            else CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                    }, ContextCompat.getMainExecutor(context))
                },
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
                            if (countdown > 0) "Get into position…"
                            else if (step == ScanStep.FRONT) "Capture front" else "Capture side",
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
                    text = if (step == ScanStep.FRONT) "Upload front photo" else "Upload side photo",
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
            LinearProgressIndicator(
                progress = { if (step == ScanStep.FRONT) 0.5f else 1f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (step == ScanStep.FRONT) "Step 1 of 2 — face the camera"
                else "Step 2 of 2 — turn side-on",
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

                    DetectionFailure.PhotoUnreadable ->
                        "That photo could not be opened. Try picking it again, or choose a " +
                            "JPEG or PNG from your gallery."
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
private fun ResultStep(state: ScanUiState, onSave: () -> Unit, onRetake: () -> Unit) {
    val result = state.result ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Scan results", style = MaterialTheme.typography.headlineSmall)

        val c = result.circumferences
        listOfNotNull(
            c.neckCm?.let { "Neck" to it },
            c.chestCm?.let { "Chest" to it },
            c.waistCm?.let { "Waist" to it },
            c.hipCm?.let { "Hip" to it },
            c.thighCm?.let { "Thigh" to it },
        ).forEach { (label, value) ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text("%.1f cm".format(value), style = MaterialTheme.typography.bodyLarge)
                }
            }
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

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save measurements") }
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
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
}

private val TIMER_OPTIONS = listOf(0, 5, 10, 15)
private const val DEFAULT_TIMER_SECONDS = 10
