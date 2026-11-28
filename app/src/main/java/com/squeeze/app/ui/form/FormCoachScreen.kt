package com.squeeze.app.ui.form

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.squeeze.app.data.CoachRepository
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.scan.VoiceGuide
import com.squeeze.core.form.FormAnalyzer
import com.squeeze.core.form.FormExercise
import com.squeeze.core.form.FormUpdate
import com.squeeze.core.form.Joint
import com.squeeze.core.form.RepResult
import com.squeeze.core.workout.LoggedSet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@HiltViewModel
class FormCoachViewModel @Inject constructor(private val coach: CoachRepository) : ViewModel() {
    /** Saves the set to today's workout log. Bodyweight moves are logged at 0 kg. */
    fun save(exercise: FormExercise, reps: Int, weightKg: Double) {
        if (reps <= 0) return
        viewModelScope.launch {
            val group = runCatching { com.squeeze.core.program.MuscleGroup.valueOf(exercise.muscleGroup) }.getOrNull()
            coach.logSet(exercise.logName, group, LoggedSet(weightKg, reps, null))
        }
    }
}

private enum class Stage { PICK, LIVE, DONE }

/**
 * Live form coaching: the camera tracks the body on this phone, counts reps and a voice gives
 * one short cue per rep. Nothing is recorded; frames are read and dropped.
 */
@Composable
fun FormCoachScreen(onDone: () -> Unit = {}, viewModel: FormCoachViewModel = hiltViewModel()) {
    var stage by remember { mutableStateOf(Stage.PICK) }
    var exercise by remember { mutableStateOf(FormExercise.SQUAT) }
    var analyzer by remember { mutableStateOf(FormAnalyzer(FormExercise.SQUAT)) }
    var set by remember { mutableIntStateOf(1) }

    when (stage) {
        Stage.PICK -> PickStep(exercise, onPick = { exercise = it }, onStart = {
            analyzer = FormAnalyzer(exercise)
            stage = Stage.LIVE
        })
        Stage.LIVE -> LiveStep(exercise, analyzer, set, onFinish = { stage = Stage.DONE })
        Stage.DONE -> DoneStep(
            exercise = exercise,
            set = set,
            results = analyzer.history,
            onNextSet = { weight ->
                viewModel.save(exercise, analyzer.history.size, weight)
                analyzer = FormAnalyzer(exercise)
                set++
                stage = Stage.LIVE
            },
            onFinish = { weight ->
                viewModel.save(exercise, analyzer.history.size, weight)
                onDone()
            },
        )
    }
}

@Composable
private fun PickStep(selected: FormExercise, onPick: (FormExercise) -> Unit, onStart: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("TRAIN · FORM COACH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("What are you doing?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text("The camera counts reps and a voice coaches your form.", style = MaterialTheme.typography.bodyMedium, color = muted)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormExercise.entries.forEach { ex ->
                val on = ex == selected
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 104.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
                        .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .clickable { onPick(ex) }
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                ) {
                    CoachPhoto(
                        coachFor(ex),
                        Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(14.dp)),
                    )
                    Text(ex.label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                    Text(if (ex.sideOn) "Side on" else "Facing", style = MaterialTheme.typography.labelSmall, color = muted)
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
            Text("SET UP FOR ${selected.label.uppercase()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = muted)
            Text("1. Prop your phone at hip height, 2 to 3 metres away.", style = MaterialTheme.typography.bodyMedium)
            Text("2. ${if (selected.sideOn) "Stand side on to the camera" else "Face the camera"}, whole body in the picture.", style = MaterialTheme.typography.bodyMedium)
            Text("3. Start when you hear \"Ready\". Earbuds work best.", style = MaterialTheme.typography.bodyMedium)
        }

        PrimaryButton(text = "Start camera", onClick = onStart)
        Text(
            "Runs on this phone. No video is saved or sent.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The coach picture that shows how to stand for each exercise. */
internal fun coachFor(ex: FormExercise) = if (ex.sideOn) CoachView.QUARTER else CoachView.FRONT

/** Lines between landmarks that make the skeleton. */
private val Bones = listOf(
    11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16, 11 to 23, 12 to 24, 23 to 24,
    23 to 25, 25 to 27, 24 to 26, 26 to 28,
)

@Composable
private fun LiveStep(exercise: FormExercise, analyzer: FormAnalyzer, set: Int, onFinish: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.CAMERA) }

    var joints by remember { mutableStateOf<Map<Int, Joint>>(emptyMap()) }
    var update by remember { mutableStateOf<FormUpdate?>(null) }
    var banner by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var frameAspect by remember { mutableStateOf(3f / 4f) }
    var front by remember { mutableStateOf(false) }

    val voice = remember { VoiceGuide(context) }
    val pose = remember { LivePose(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            voice.shutdown()
            executor.execute { pose.close() }
            executor.shutdown()
        }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        voice.say("Ready. Start your first rep when you are in position.", force = true)
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
                    val found = pose.detect(upright, SystemClock.uptimeMillis())
                    val result = analyzer.update(found)
                    ContextCompat.getMainExecutor(context).execute {
                        frameAspect = upright.width.toFloat() / upright.height
                        joints = found
                        update = result
                        result.cue?.let { cue ->
                            voice.say(cue, force = result.completed != null)
                            banner = cue to (result.completed?.clean ?: false)
                        }
                    }
                }
                provider.unbindAll()
                runCatching {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }
            }

            // The skeleton, drawn where the fitted preview sits on screen.
            Box(Modifier.fillMaxSize()) {
                val slipping = update?.slipping.orEmpty()
                Canvas(Modifier.fillMaxSize()) {
                    val viewAspect = size.width / size.height
                    val (w, h) = if (frameAspect > viewAspect) size.width to size.width / frameAspect else size.height * frameAspect to size.height
                    val left = (size.width - w) / 2
                    val top = (size.height - h) / 2
                    fun at(j: Joint) = Offset(left + j.x.toFloat() * w, top + j.y.toFloat() * h)
                    Bones.forEach { (a, b) ->
                        val ja = joints[a] ?: return@forEach
                        val jb = joints[b] ?: return@forEach
                        if (ja.visibility < 0.5 || jb.visibility < 0.5) return@forEach
                        val bad = a in slipping || b in slipping
                        drawLine(if (bad) Color(0xFFF59E0B) else Color(0xFF4ADE80), at(ja), at(jb), strokeWidth = 7f, cap = StrokeCap.Round)
                    }
                    joints.filterKeys { it in 11..28 }.forEach { (i, j) ->
                        if (j.visibility >= 0.5) drawCircle(if (i in slipping) Color(0xFFF59E0B) else Color.White, 9f, at(j))
                    }
                }
            }
            // Until the camera finds a body, the coach shows how to stand.
            if (update?.visible != true) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CoachGhost(coachFor(exercise), matched = false, modifier = Modifier.fillMaxHeight(0.5f).aspectRatio(0.67f))
                    Text(
                        if (exercise.sideOn) "Stand side on, like this" else "Face the camera, like this",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).background(Color(0x99050B18)).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        } else {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera access is needed for the form coach.", color = Color.White, textAlign = TextAlign.Center)
                TextButton(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            }
        }

        // Top bar and rep counter.
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "● LIVE · ${exercise.label.uppercase()}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x99050B18)).padding(horizontal = 11.dp, vertical = 7.dp),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
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
        Column(Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 16.dp), horizontalAlignment = Alignment.End) {
            Text("${update?.reps ?: 0}", color = Color.White, fontSize = 80.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 80.sp)
            Text("reps · set $set", color = Color(0xFFC9D6F0), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        banner?.let { (text, good) ->
            Text(
                "“$text”",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 190.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (good) Color(0xD916A34A) else Color(0xE6D97706))
                    .padding(14.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        // Score and finish.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEB0A1222), Color(0xFF0A1222))))
                .padding(18.dp),
        ) {
            val score = update?.score ?: 100
            Row(Modifier.fillMaxWidth()) {
                Text("FORM SCORE", color = Color(0xFF8DB3FF), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("$score%", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Color(0x24FFFFFF))) {
                Box(Modifier.fillMaxWidth(score / 100f).height(10.dp).clip(RoundedCornerShape(50)).background(Color(0xFF4ADE80)))
            }
            if (update?.visible == false) {
                Text("Step back so your whole body is in the picture.", color = Color(0xFFF8B84A), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
            PrimaryButton(text = "Finish set", onClick = onFinish, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun DoneStep(
    exercise: FormExercise,
    set: Int,
    results: List<RepResult>,
    onNextSet: (Double) -> Unit,
    onFinish: (Double) -> Unit,
) {
    val reps = results.size
    val clean = results.count { it.clean }
    val score = if (reps == 0) 0 else clean * 100 / reps
    // The most common slip is the one tip for the next set.
    val tip = results.flatMap { it.issues }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    var weight by remember { mutableStateOf("") }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF15803D), Color(0xFF065F46))))
                .padding(18.dp),
        ) {
            Text("SET $set DONE · ${exercise.label.uppercase()}", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$reps", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
                Text(" reps", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp).weight(1f))
                Text("$score%", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(" form", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(
                if (reps == 0) "No full reps counted. Make sure your whole body is in the picture."
                else "$clean clean rep${if (clean == 1) "" else "s"}${if (reps > clean) ", ${reps - clean} with a slip." else "."}",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
            )
        }

        if (reps > 0) {
            Text("EVERY REP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = muted)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                results.forEach { r ->
                    Box(
                        Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (r.clean) Color(0xFF16A34A) else Color(0xFFF59E0B)),
                        contentAlignment = Alignment.BottomCenter,
                    ) { Text("${r.number}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }

        Text("FOR THE NEXT SET", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = muted)
        Text(
            tip ?: "Clean set. Keep the same tempo next time.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(14.dp),
        )

        if (exercise == FormExercise.BICEP_CURL) {
            androidx.compose.material3.OutlinedTextField(
                value = weight,
                onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text("Dumbbell weight (kg), optional") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val kg = weight.toDoubleOrNull() ?: 0.0
        PrimaryButton(text = "Start set ${set + 1}", onClick = { onNextSet(kg) })
        OutlinedButton(onClick = { onFinish(kg) }, modifier = Modifier.fillMaxWidth()) { Text("Save and finish") }
        Text("Each set is saved to your workout log.", style = MaterialTheme.typography.bodySmall, color = muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** The pose model in video mode: one frame at a time, in order, on the caller's thread. */
internal class LivePose(context: Context) {
    private val landmarker: PoseLandmarker? = runCatching {
        PoseLandmarker.createFromOptions(
            context,
            PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build())
                .setRunningMode(RunningMode.VIDEO)
                .setNumPoses(1)
                .build(),
        )
    }.getOrNull()
    private var lastTs = 0L

    fun detect(bitmap: Bitmap, timestampMs: Long): Map<Int, Joint> {
        val lm = landmarker ?: return emptyMap()
        val ts = maxOf(timestampMs, lastTs + 1).also { lastTs = it }
        val result = runCatching { lm.detectForVideo(BitmapImageBuilder(bitmap).build(), ts) }.getOrNull() ?: return emptyMap()
        val points = result.landmarks().firstOrNull() ?: return emptyMap()
        return points.mapIndexed { i, p -> i to Joint(p.x().toDouble(), p.y().toDouble(), p.visibility().orElse(0f).toDouble()) }.toMap()
    }

    fun close() = runCatching { landmarker?.close() }
}

internal fun Bitmap.uprightAndMirrored(degrees: Int, mirror: Boolean): Bitmap {
    if (degrees == 0 && !mirror) return this
    val m = Matrix().apply {
        postRotate(degrees.toFloat())
        if (mirror) postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}

internal suspend fun awaitProvider(context: Context): ProcessCameraProvider = suspendCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
}
