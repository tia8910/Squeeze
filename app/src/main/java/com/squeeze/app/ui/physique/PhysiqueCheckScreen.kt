package com.squeeze.app.ui.physique

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.db.PhysiqueCheckDao
import com.squeeze.app.data.db.PhysiqueCheckEntity
import com.squeeze.app.data.photo.ScanPhotoStore
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.form.CoachGhost
import com.squeeze.app.ui.form.CoachPhoto
import com.squeeze.app.ui.form.CoachView
import com.squeeze.app.ui.form.LivePose
import com.squeeze.app.ui.form.awaitProvider
import com.squeeze.app.ui.form.uprightAndMirrored
import com.squeeze.app.ui.scan.VoiceGuide
import com.squeeze.core.form.Joint
import com.squeeze.core.physique.PhysiquePose
import com.squeeze.core.physique.PoseMatch
import com.squeeze.core.physique.PoseMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import javax.inject.Inject

/** Which coach picture leads each pose, and whether it is turned the other way. */
private fun PhysiquePose.coach(): Pair<CoachView, Boolean> = when (this) {
    PhysiquePose.FRONT -> CoachView.FRONT to false
    PhysiquePose.QUARTER -> CoachView.QUARTER to false
    PhysiquePose.SIDE -> CoachView.QUARTER to true
    PhysiquePose.BACK -> CoachView.BACK to false
}

data class PhysiqueState(
    val today: Map<PhysiquePose, Bitmap> = emptyMap(),
    val before: Map<PhysiquePose, Bitmap> = emptyMap(),
    val beforeDay: LocalDate? = null,
)

@HiltViewModel
class PhysiqueCheckViewModel @Inject constructor(
    private val checks: PhysiqueCheckDao,
    private val photos: ScanPhotoStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PhysiqueState())
    val state: StateFlow<PhysiqueState> = _state.asStateFlow()

    private val today get() = LocalDate.now().toEpochDay()

    /** Saves one pose, replacing today's photo of the same pose if there is one. */
    fun save(pose: PhysiquePose, photo: Bitmap) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val id = photos.save(photo) ?: return@withContext
                checks.forDay(today).filter { it.pose == pose.name }.forEach { photos.delete(it.photoId) }
                checks.remove(today, pose.name)
                checks.insert(PhysiqueCheckEntity(epochDay = today, pose = pose.name, photoId = id))
            }
            _state.value = _state.value.copy(today = _state.value.today + (pose to photo))
        }
    }

    fun load() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val day = today
                val now = decode(checks.forDay(day))
                val beforeDay = checks.dayBefore(day)
                val before = beforeDay?.let { decode(checks.forDay(it)) }.orEmpty()
                PhysiqueState(now, before, beforeDay?.let(LocalDate::ofEpochDay))
            }
            _state.value = loaded
        }
    }

    /** Deletes today's check, photos and all. */
    fun deleteToday() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                checks.forDay(today).forEach {
                    photos.delete(it.photoId)
                    checks.remove(it.epochDay, it.pose)
                }
            }
            _state.value = _state.value.copy(today = emptyMap())
        }
    }

    private fun decode(rows: List<PhysiqueCheckEntity>): Map<PhysiquePose, Bitmap> =
        rows.mapNotNull { row ->
            val pose = runCatching { PhysiquePose.valueOf(row.pose) }.getOrNull() ?: return@mapNotNull null
            photos.load(row.photoId)?.let { pose to it }
        }.toMap()
}

private enum class Stage { INTRO, LIVE, DONE }

/**
 * The weekly physique check: four set poses, each led by the coach figure standing in the camera
 * view. A voice talks the user into the pose, and the photo takes itself after a short hold, so
 * the phone can stay propped up across the room. The same poses at the same distance every week
 * is what makes the photos worth comparing.
 *
 * Photos are encrypted and kept on this phone only.
 */
@Composable
fun PhysiqueCheckScreen(onDone: () -> Unit = {}, viewModel: PhysiqueCheckViewModel = hiltViewModel()) {
    var stage by remember { mutableStateOf(Stage.INTRO) }
    LaunchedEffect(stage) { if (stage != Stage.LIVE) viewModel.load() }
    when (stage) {
        Stage.INTRO -> IntroStep(onStart = { stage = Stage.LIVE })
        Stage.LIVE -> LiveStep(
            onCaptured = viewModel::save,
            onFinish = { stage = Stage.DONE },
        )
        Stage.DONE -> {
            val state by viewModel.state.collectAsStateWithLifecycle()
            DoneStep(
                state = state,
                onRetake = { stage = Stage.LIVE },
                onDelete = viewModel::deleteToday,
                onDone = onDone,
            )
        }
    }
}

@Composable
private fun IntroStep(onStart: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("TRAIN · PHYSIQUE CHECK", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Four poses, hands free", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text("Match the coach and hold. The photo takes itself.", style = MaterialTheme.typography.bodyMedium, color = muted)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhysiquePose.entries.forEach { pose ->
                val (view, mirrored) = pose.coach()
                Box(
                    Modifier.weight(1f).aspectRatio(0.62f).clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    CoachPhoto(view, Modifier.fillMaxSize(), mirrored = mirrored)
                    Text(
                        pose.label,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().background(Color(0x99000000)).padding(vertical = 4.dp),
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("SET UP ONCE, SAME EVERY WEEK", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = muted)
            Text("1. Prop your phone at hip height, 2 to 3 metres away.", style = MaterialTheme.typography.bodyMedium)
            Text("2. Same spot, same light, fitted clothes or shorts.", style = MaterialTheme.typography.bodyMedium)
            Text("3. Turn your sound up or wear earbuds, and follow the voice.", style = MaterialTheme.typography.bodyMedium)
        }

        PrimaryButton(text = "Start", onClick = onStart)
        Text(
            "Photos are encrypted and stay on this phone. They are never uploaded.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LiveStep(onCaptured: (PhysiquePose, Bitmap) -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.CAMERA) }

    var index by remember { mutableIntStateOf(0) }
    val pose = PhysiquePose.entries[index.coerceAtMost(PhysiquePose.entries.lastIndex)]
    var joints by remember { mutableStateOf<Map<Int, Joint>>(emptyMap()) }
    var match by remember { mutableStateOf<PoseMatch?>(null) }
    var frameAspect by remember { mutableStateOf(3f / 4f) }
    var front by remember { mutableStateOf(true) }
    var holdFrom by remember { mutableStateOf<Long?>(null) }
    var count by remember { mutableIntStateOf(0) }
    var captured by remember { mutableStateOf(false) }
    // Matching waits until the instructions for a new pose have been said.
    var listenFrom by remember { mutableStateOf(0L) }

    val voice = remember { VoiceGuide(context) }
    val detector = remember { LivePose(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    DisposableEffect(Unit) {
        onDispose {
            voice.shutdown()
            executor.execute { detector.close() }
            executor.shutdown()
        }
    }

    // Each new pose: say what it is, then start listening.
    LaunchedEffect(index) {
        if (index > PhysiquePose.entries.lastIndex) {
            voice.say("All done. Great work.", force = true)
            delay(1200)
            onFinish()
            return@LaunchedEffect
        }
        captured = false
        holdFrom = null
        count = 0
        delay(if (index == 0) 900 else 1500)
        voice.say("Pose ${index + 1}. ${pose.label}. ${pose.intro}", force = true)
        listenFrom = SystemClock.elapsedRealtime() + 3500
    }

    fun takePhoto() {
        val current = pose
        val mirror = front
        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = runCatching { image.toBitmap() }.getOrNull()
                    val rotation = image.imageInfo.rotationDegrees
                    image.close()
                    val upright = bitmap?.uprightAndMirrored(rotation, mirror) ?: return
                    ContextCompat.getMainExecutor(context).execute { onCaptured(current, upright) }
                }

                override fun onError(exception: ImageCaptureException) = Unit
            },
        )
    }

    // Judge each new set of landmarks: talk the user into the pose, then hold, then capture.
    LaunchedEffect(joints) {
        if (captured || index > PhysiquePose.entries.lastIndex) return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        if (now < listenFrom) return@LaunchedEffect
        val m = PoseMatcher.match(pose, joints)
        match = m
        if (!m.ok) {
            holdFrom = null
            count = 0
            m.cue?.let { voice.say(it) }
            return@LaunchedEffect
        }
        val start = holdFrom ?: now.also {
            holdFrom = it
            voice.say("Perfect. Hold still.", force = true)
        }
        val left = HOLD_SECONDS - ((now - start) / 1000).toInt()
        if (left in 1 until HOLD_SECONDS && left != count) {
            count = left
            voice.say(if (left == 2) "Two" else "One", force = true)
        }
        if (now - start >= HOLD_SECONDS * 1000L) {
            captured = true
            takePhoto()
            voice.say(if (index < PhysiquePose.entries.lastIndex) "Got it." else "Got it. That is all four.", force = true)
        }
    }

    // Move on a moment after the photo, outside the per frame effect so new frames don't cancel it.
    LaunchedEffect(captured) {
        if (captured) {
            delay(700)
            index++
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A1222))) {
        if (allowed) {
            val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            LaunchedEffect(front) {
                val provider = awaitProvider(context)
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val frame = runCatching { proxy.toBitmap() }.getOrNull()
                    val rotation = proxy.imageInfo.rotationDegrees
                    proxy.close()
                    if (frame == null) return@setAnalyzer
                    val upright = frame.uprightAndMirrored(rotation, mirror = front)
                    val found = detector.detect(upright, SystemClock.uptimeMillis())
                    ContextCompat.getMainExecutor(context).execute {
                        frameAspect = upright.width.toFloat() / upright.height
                        joints = found
                    }
                }
                provider.unbindAll()
                runCatching {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                        capture,
                    )
                }
            }

            // The coach figure: the pose to match.
            val (view, mirrored) = pose.coach()
            CoachGhost(
                view = view,
                matched = match?.ok == true,
                mirrored = mirrored,
                modifier = Modifier.align(Alignment.Center).fillMaxHeight(0.62f).aspectRatio(0.67f).padding(top = 40.dp),
            )

            // A pulsing marker on the part of the body to fix.
            val pulse = rememberInfiniteTransition(label = "fix pulse")
            val ring by pulse.animateFloat(1f, 2.2f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "ring")
            val fixJoint = match?.fix?.let { joints[it] }
            Canvas(Modifier.fillMaxSize()) {
                val p = fixJoint ?: return@Canvas
                val viewAspect = size.width / size.height
                val (w, h) = if (frameAspect > viewAspect) size.width to size.width / frameAspect else size.height * frameAspect to size.height
                val at = Offset((size.width - w) / 2 + p.x.toFloat() * w, (size.height - h) / 2 + p.y.toFloat() * h)
                drawCircle(Color(0xFFF59E0B), 14.dp.toPx(), at)
                drawCircle(Color(0xFFFBBF24).copy(alpha = (2.2f - ring) / 1.2f), 14.dp.toPx() * ring, at, style = Stroke(3.dp.toPx()))
            }
        } else {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera access is needed for the physique check.", color = Color.White, textAlign = TextAlign.Center)
                TextButton(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            }
        }

        // Top: progress and the pose.
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "● PHYSIQUE CHECK",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x99050B18)).padding(horizontal = 11.dp, vertical = 7.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Flip camera",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x99050B18))
                        .clickable { front = !front }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                PhysiquePose.entries.forEachIndexed { i, _ ->
                    Box(
                        Modifier.width(30.dp).height(6.dp).clip(RoundedCornerShape(50)).background(
                            when {
                                i < index -> Color(0xFF4ADE80)
                                i == index -> Color.White
                                else -> Color(0x33FFFFFF)
                            },
                        ),
                    )
                }
            }
            Text(
                "Pose ${(index + 1).coerceAtMost(PhysiquePose.entries.size)} of ${PhysiquePose.entries.size} · match the coach",
                color = Color(0xFFC9D6F0),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Text(
                pose.label,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            val m = match
            val text = when {
                m == null -> pose.intro
                m.ok -> "Perfect. Hold still."
                else -> m.cue ?: pose.intro
            }
            Text(
                "“$text”",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (m?.ok == true) Color(0xE016A34A) else Color(0xEBD97706))
                    .padding(12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        // Bottom: hold ring and controls.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEB0A1222), Color(0xFF0A1222))))
                .padding(18.dp),
        ) {
            val start = holdFrom
            val progress = if (captured) 1f else if (start == null) 0f else ((SystemClock.elapsedRealtime() - start) / (HOLD_SECONDS * 1000f)).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color(0x26FFFFFF), style = Stroke(6.dp.toPx()))
                        drawArc(Color(0xFF4ADE80), -90f, 360f * progress, useCenter = false, style = Stroke(6.dp.toPx()))
                    }
                    Text(
                        when {
                            captured -> "✓"
                            start == null -> "–"
                            else -> "${count.takeIf { it > 0 } ?: HOLD_SECONDS}"
                        },
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Text(
                    if (start == null) "Get into the pose and the countdown starts by itself." else "Hold still, the photo takes itself.",
                    color = Color(0xFFC9D6F0),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 14.dp).weight(1f),
                )
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { if (!captured) index++ }, modifier = Modifier.weight(1f)) { Text("Skip pose", color = Color.White) }
                OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f)) { Text("Finish", color = Color.White) }
            }
        }
    }
}

private const val HOLD_SECONDS = 3

@Composable
private fun DoneStep(state: PhysiqueState, onRetake: () -> Unit, onDelete: () -> Unit, onDone: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var comparing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val format = remember { DateTimeFormatter.ofPattern("d MMM") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2F8CFF), Color(0xFF0E4BD8), Color(0xFF081C45))))
                .padding(18.dp),
        ) {
            Text("PHYSIQUE CHECK · TODAY", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (state.today.isEmpty()) "No poses saved" else "${state.today.size} pose${if (state.today.size == 1) "" else "s"} captured",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "Same poses, same distance every week, so the comparison is fair.",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
            )
        }

        if (state.beforeDay != null && state.today.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "Today", true to "Compare with ${state.beforeDay.format(format)}").forEach { (value, label) ->
                    val on = comparing == value
                    Text(
                        label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                            .clickable { comparing = value }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        val shown = PhysiquePose.entries.filter { it in state.today }
        if (comparing) {
            shown.forEach { pose ->
                Text(pose.label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoTile(state.before[pose], state.beforeDay?.format(format) ?: "Before", Modifier.weight(1f))
                    PhotoTile(state.today[pose], "Today", Modifier.weight(1f))
                }
            }
        } else {
            shown.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { pose -> PhotoTile(state.today[pose], pose.label, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("🔒")
            Text("Encrypted and saved on this phone only. Never uploaded. Only you see these photos.", style = MaterialTheme.typography.bodySmall)
        }

        PrimaryButton(text = "Done", onClick = onDone)
        OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) { Text("Retake") }
        if (state.today.isNotEmpty()) {
            TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (confirmDelete) "Tap again to delete today's photos" else "Delete today's photos", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: Bitmap?, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier.aspectRatio(0.75f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0B1A3E)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (photo != null) {
            Image(photo.asImageBitmap(), contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("No photo", color = Color(0xFF8FA6D1), fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
        }
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(6.dp).clip(CircleShape).background(Color(0x66000000)).padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
