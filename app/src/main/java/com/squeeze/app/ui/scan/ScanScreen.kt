package com.squeeze.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/**
 * Guided two-photograph body scan.
 *
 * The screen deliberately never displays a captured photo back to the user, and never
 * writes one to disk. Photographs exist only long enough for inference to run; what
 * survives is a set of circumferences.
 */
@Composable
fun ScanScreen(
    onFinished: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onFinished()
    }

    if (!hasCameraPermission) {
        CameraPermissionRequired(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        return
    }

    when (state.step) {
        ScanStep.FRONT, ScanStep.SIDE -> CaptureStep(
            step = state.step,
            failure = state.failure,
            profileMissing = state.profileMissing,
            onCapture = viewModel::onPhotoCaptured,
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
private fun CameraPermissionRequired(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera access needed", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "The scan runs entirely on this device. Photos are never saved and never " +
                "leave your phone — only the measurements are kept.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest) { Text("Allow camera") }
    }
}

@Composable
private fun CaptureStep(
    step: ScanStep,
    failure: DetectionFailure?,
    profileMissing: Boolean,
    onCapture: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = CameraPreview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
        )

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (step == ScanStep.FRONT) "Step 1 of 2 — facing the camera"
                        else "Step 2 of 2 — turn to your side",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        // Framing advice is measurement advice: the whole body must be in
                        // shot because height is what converts pixels into centimetres,
                        // and standing back reduces perspective distortion.
                        text = "Stand a few steps back so your whole body from head to feet " +
                            "is in frame, against a plain background, in even light. Wear " +
                            "close-fitting clothing — loose fabric is measured as body.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            failure?.let { FailureCard(it) }

            if (profileMissing) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        text = "Set your height in Settings first — the scan uses it to " +
                            "convert the photo into real measurements.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Button(
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            onClick = {
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            image.decodeToBitmap()?.let(onCapture)
                            image.close()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            // A failed shutter leaves the step unchanged, so the user simply
                            // taps again; there is no partial state to unwind.
                            exception.printStackTrace()
                        }
                    },
                )
            },
        ) {
            Text(if (step == ScanStep.FRONT) "Capture front" else "Capture side")
        }
    }
}

@Composable
private fun FailureCard(failure: DetectionFailure) {
    Card(Modifier.fillMaxWidth()) {
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
                        "Stand upright and face the camera squarely, with arms slightly away " +
                            "from your sides."

                    DetectionFailure.SegmentationFailed ->
                        "Your outline could not be separated from the background. A plainer " +
                            "background and more even lighting will help."
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
            text = "Running on this device. Nothing is uploaded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultStep(state: ScanUiState, onSave: () -> Unit, onRetake: () -> Unit) {
    val result = state.result ?: return

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Scan results", style = MaterialTheme.typography.titleLarge)

        val c = result.circumferences
        listOfNotNull(
            c.neckCm?.let { "Neck" to it },
            c.chestCm?.let { "Chest" to it },
            c.waistCm?.let { "Waist" to it },
            c.hipCm?.let { "Hip" to it },
            c.thighCm?.let { "Thigh" to it },
        ).forEach { (label, value) ->
            Card(Modifier.fillMaxWidth()) {
                Text(
                    text = "%s   %.1f cm".format(label, value),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        if (result.warnings.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
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
                                        "which usually means the side photo was not square-on."

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
 * Decodes a captured frame into a bitmap.
 *
 * CameraX hands back JPEG bytes for [ImageCapture]; decoding here keeps the bitmap in
 * memory and off the filesystem, which is the point — a captured body photo is never
 * written to disk.
 */
private fun ImageProxy.decodeToBitmap(): Bitmap? {
    val plane = planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }

    return runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
