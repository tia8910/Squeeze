package com.squeeze.app.ui.scan

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squeeze.core.model.Goal
import com.squeeze.core.scan.CropRegion
import com.squeeze.core.scan.PhysiqueReport
import com.squeeze.core.scan.Development
import com.squeeze.core.scan.MuscleGroup
import com.squeeze.core.scan.PhysiqueAnalysis
import com.squeeze.core.scan.FrontPoseGeometry
import com.squeeze.core.scan.PosePoint

/**
 * The scan, shown while it happens.
 *
 * The photograph with what the on-device models are finding drawn over it: a sweep while the
 * pose and body-part models look for the body, the landmarks they placed once they have, and
 * the frame the vision-language model is being shown while it reads. Underneath, the stages
 * in the order they run, and — once the AI has decided — how much it weighed each reference
 * body, so the number arrives with its reasoning instead of out of a spinner.
 *
 * Every stage on screen is the one the view model is actually in. Nothing here is animated
 * to look busy while nothing happens.
 */
@Composable
fun AiScannerView(scanner: AiScanner, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScannerHeader(scanner.stage)
        ScannedPhoto(scanner)
        StageList(scanner)
        if (scanner.aiRuns && scanner.stage >= ScannerStage.AI_READING) AiVerdict(scanner)
        if (scanner.muscleRegions.isNotEmpty() && scanner.stage >= ScannerStage.MUSCLES) {
            MuscleProgress(scanner)
        }
    }
}

@Composable
private fun ScannerHeader(stage: ScannerStage) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PulsingDot(active = stage != ScannerStage.DONE)
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                text = if (stage == ScannerStage.DONE) "AI scan complete" else "AI scanning",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Running on this phone · your photo never leaves it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PulsingDot(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "dot")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        Modifier
            .size(12.dp)
            .alpha(if (active) pulse else 1f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun ScannedPhoto(scanner: AiScanner) {
    val image = remember(scanner.photo) { scanner.photo.asImageBitmap() }
    val ratio = scanner.photo.width.toFloat() / scanner.photo.height.coerceAtLeast(1)
    val accent = MaterialTheme.colorScheme.primary

    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "breathe",
    )
    val landmarksIn by animateFloatAsState(
        targetValue = if (scanner.landmarks != null) 1f else 0f,
        animationSpec = tween(500),
        label = "landmarks",
    )
    val focusIn by animateFloatAsState(
        targetValue = if (scanner.aiRegion != null && scanner.stage == ScannerStage.AI_READING) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "focus",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
        ) {
            Image(
                bitmap = image,
                contentDescription = "The photograph being scanned",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(Modifier.fillMaxSize()) {
                val region = scanner.aiRegion?.takeIf { scanner.stage == ScannerStage.AI_READING }
                val focus = region?.toRect(size)
                val active = scanner.activeGroup
                    ?.takeIf { scanner.stage == ScannerStage.MUSCLES }
                    ?.let { scanner.muscleRegions[it] }
                    ?.map { it.toRect(size) }

                if (focus != null) dimOutside(focus, 0.55f * focusIn)

                // The sweep runs until the last model has answered, then stops: a line still
                // moving over a finished scan would say work is happening that is not.
                if (scanner.stage != ScannerStage.DONE && scanner.stage != ScannerStage.BODY_FOUND) {
                    val bands = active ?: listOf(focus ?: Rect(Offset.Zero, size))
                    bands.forEach { sweepLine(it, sweep, accent) }
                }

                // Groups already judged stay faintly outlined; the one being judged now is
                // bracketed, so the user can watch the model move around the body.
                if (scanner.stage >= ScannerStage.MUSCLES) {
                    scanner.muscleScores.keys.forEach { group ->
                        scanner.muscleRegions[group]?.forEach { box ->
                            val r = box.toRect(size)
                            drawRect(
                                color = Color.White.copy(alpha = 0.35f),
                                topLeft = r.topLeft,
                                size = r.size,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                            )
                        }
                    }
                    active?.forEach { corners(it, accent.copy(alpha = breathe)) }
                }

                scanner.landmarks?.let { skeleton(it, accent, landmarksIn) }

                if (focus != null) {
                    val alpha = if (scanner.stage == ScannerStage.AI_READING) breathe else 1f
                    corners(focus, accent.copy(alpha = alpha * focusIn))
                }
            }
        }
    }
}

private fun CropRegion.toRect(size: Size) = Rect(
    left = (left * size.width).toFloat(),
    top = (top * size.height).toFloat(),
    right = (right * size.width).toFloat(),
    bottom = (bottom * size.height).toFloat(),
)

private fun DrawScope.dimOutside(focus: Rect, alpha: Float) {
    if (alpha <= 0f) return
    val shade = Color.Black.copy(alpha = alpha)
    drawRect(shade, Offset.Zero, Size(size.width, focus.top))
    drawRect(shade, Offset(0f, focus.bottom), Size(size.width, size.height - focus.bottom))
    drawRect(shade, Offset(0f, focus.top), Size(focus.left, focus.height))
    drawRect(shade, Offset(focus.right, focus.top), Size(size.width - focus.right, focus.height))
}

private fun DrawScope.sweepLine(band: Rect, progress: Float, accent: Color) {
    val y = band.top + band.height * progress
    val glow = band.height * 0.12f
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, accent.copy(alpha = 0.35f), Color.Transparent),
            startY = y - glow,
            endY = y + glow,
        ),
        topLeft = Offset(band.left, y - glow),
        size = Size(band.width, glow * 2f),
    )
    drawLine(
        color = accent,
        start = Offset(band.left, y),
        end = Offset(band.right, y),
        strokeWidth = 2.dp.toPx(),
    )
}

private fun DrawScope.skeleton(pose: FrontPoseGeometry, accent: Color, alpha: Float) {
    if (alpha <= 0f) return
    fun PosePoint.at() = Offset((x * size.width).toFloat(), (y * size.height).toFloat())

    val bone = Color.White.copy(alpha = 0.85f * alpha)
    val width = 2.5.dp.toPx()
    val bones = buildList {
        add(pose.shoulderLeft to pose.shoulderRight)
        add(pose.hipLeft to pose.hipRight)
        add(pose.shoulderLeft to pose.hipLeft)
        add(pose.shoulderRight to pose.hipRight)
        pose.ankleLeft?.let { add(pose.hipLeft to it) }
        pose.ankleRight?.let { add(pose.hipRight to it) }
        val neck = PosePoint(
            (pose.shoulderLeft.x + pose.shoulderRight.x) / 2.0,
            (pose.shoulderLeft.y + pose.shoulderRight.y) / 2.0,
        )
        (pose.mouth ?: pose.nose)?.let { add(neck to it) }
    }
    bones.forEach { (a, b) ->
        drawLine(bone, a.at(), b.at(), strokeWidth = width, cap = StrokeCap.Round)
    }

    listOfNotNull(
        pose.shoulderLeft, pose.shoulderRight, pose.hipLeft, pose.hipRight,
        pose.ankleLeft, pose.ankleRight, pose.nose, pose.mouth,
    ).forEach { point ->
        drawCircle(accent.copy(alpha = 0.35f * alpha), radius = 9.dp.toPx(), center = point.at())
        drawCircle(accent.copy(alpha = alpha), radius = 4.dp.toPx(), center = point.at())
        drawCircle(Color.White.copy(alpha = alpha), radius = 1.5.dp.toPx(), center = point.at())
    }
}

private fun DrawScope.corners(rect: Rect, color: Color) {
    val arm = minOf(rect.width, rect.height) * 0.14f
    val stroke = 3.dp.toPx()
    listOf(
        Triple(rect.topLeft, 1f, 1f),
        Triple(rect.topRight, -1f, 1f),
        Triple(rect.bottomLeft, 1f, -1f),
        Triple(rect.bottomRight, -1f, -1f),
    ).forEach { (corner, dx, dy) ->
        drawLine(color, corner, corner + Offset(arm * dx, 0f), stroke, StrokeCap.Round)
        drawLine(color, corner, corner + Offset(0f, arm * dy), stroke, StrokeCap.Round)
    }
}

private class StageLine(val title: String, val detail: String, val stage: ScannerStage)

@Composable
private fun StageList(scanner: AiScanner) {
    // While a single photograph is being checked, only the first stage is happening; the
    // rest belong to the measurement, which the user has not asked for yet.
    val measuring = scanner.stage >= ScannerStage.MEASURING
    val lines = buildList {
        add(
            StageLine(
                "Finding your body",
                "Pose landmarks and body-part segmentation",
                ScannerStage.FINDING_BODY,
            ),
        )
        if (measuring) {
            add(
                StageLine(
                    "Measuring your outline",
                    "Neck, waist and hips from the silhouette",
                    ScannerStage.MEASURING,
                ),
            )
            if (scanner.aiRuns) {
                add(
                    StageLine(
                        "AI reading your physique",
                        "Vision-language model, comparing you with five reference bodies",
                        ScannerStage.AI_READING,
                    ),
                )
                if (scanner.muscleRegions.isNotEmpty()) {
                    add(
                        StageLine(
                            "AI judging each muscle group",
                            scanner.activeGroup
                                ?.takeIf { scanner.stage == ScannerStage.MUSCLES }
                                ?.let { "Looking at your ${it.label.lowercase()}…" }
                                ?: "Shoulders, chest, arms, abs, back width and legs",
                            ScannerStage.MUSCLES,
                        ),
                    )
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            lines.forEach { line ->
                val done = when (line.stage) {
                    ScannerStage.FINDING_BODY -> scanner.stage >= ScannerStage.BODY_FOUND
                    else -> scanner.stage > line.stage
                }
                val active = !done && when (line.stage) {
                    ScannerStage.FINDING_BODY -> scanner.stage == ScannerStage.FINDING_BODY
                    else -> scanner.stage == line.stage
                }
                StageRow(line, done = done, active = active)
            }
        }
    }
}

@Composable
private fun StageRow(line: StageLine, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Done",
                    tint = MaterialTheme.colorScheme.primary,
                )

                active -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)

                else -> Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
        }
        Column(
            Modifier
                .padding(start = 12.dp)
                .alpha(if (done || active) 1f else 0.55f),
        ) {
            Text(line.title, style = MaterialTheme.typography.titleSmall)
            Text(
                line.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What each reference body is, by the body fat it stands for — tools/vlm/prompts.py. */
private fun referenceName(anchor: Double): String = when (anchor) {
    5.0 -> "Stage-lean"
    8.0 -> "Visible six-pack"
    15.0 -> "Soft lower stomach"
    20.0 -> "No visible abs"
    30.0 -> "Rounded belly"
    else -> "%.0f%% body".format(anchor)
}

@Composable
private fun AiVerdict(scanner: AiScanner) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val reading = scanner.reading
            when {
                scanner.stage == ScannerStage.AI_READING -> {
                    Text(
                        "The AI is looking at your torso",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Abdominal definition, how the lower stomach sits, how much muscle " +
                            "shows through — what a coach reads at a glance.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                reading == null -> Text(
                    "The AI could not read this photograph, so the result comes from your " +
                        "measurements alone.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                else -> {
                    val shown by animateFloatAsState(
                        targetValue = reading.percent.toFloat(),
                        animationSpec = tween(900, easing = FastOutSlowInEasing),
                        label = "percent",
                    )
                    Text("The AI reads", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "%.1f%% body fat".format(shown),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    val anchors = reading.anchors
                    val weights = reading.weights
                    if (anchors != null && weights != null) {
                        val top = weights.indices.maxBy { weights[it] }
                        Text(
                            "Looks most like: ${referenceName(anchors[top])}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        anchors.indices.forEach { i ->
                            WeightBar(
                                label = referenceName(anchors[i]),
                                weight = weights[i].toFloat(),
                                strongest = i == top,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightBar(label: String, weight: Float, strongest: Boolean) {
    val grown by animateFloatAsState(
        targetValue = weight.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "bar",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (strongest) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.width(130.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(grown)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (strongest) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    ),
            )
        }
        Text(
            "%.0f%%".format(weight * 100f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 8.dp).width(36.dp),
        )
    }
}

/**
 * Shown over the live camera while auto-capture is on: the models are reading every frame,
 * and this says so, with what they currently make of it.
 */
@Composable
fun AiLiveBadge(hint: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingDot(active = true)
            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    "AI is watching the frame",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    hint ?: "Framing looks good — hold still.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * A scan line over the live preview while auto-capture is on, so the camera screen shows the
 * models at work rather than a still viewfinder.
 */
@Composable
fun LiveScanSweep(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "live")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Reverse),
        label = "liveSweep",
    )
    Canvas(modifier.fillMaxSize()) {
        sweepLine(Rect(Offset.Zero, size), sweep, accent.copy(alpha = 0.8f))
    }
}

/** Each group's score as the model reaches it, with the group being read marked. */
@Composable
private fun MuscleProgress(scanner: AiScanner) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Muscle groups", style = MaterialTheme.typography.titleSmall)
            MuscleGroup.entries.filter { it in scanner.muscleRegions }.forEach { group ->
                val score = scanner.muscleScores[group]
                val reading = group == scanner.activeGroup && scanner.stage == ScannerStage.MUSCLES
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        WeightBar(
                            label = group.label,
                            weight = (score ?: 0.0).toFloat(),
                            strongest = score != null &&
                                PhysiqueAnalysis.development(score) == Development.DEVELOPED,
                        )
                    }
                    Box(Modifier.size(20.dp).padding(start = 4.dp), contentAlignment = Alignment.Center) {
                        if (reading && score == null) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

private fun goalName(goal: Goal): String = when (goal) {
    Goal.HYPERTROPHY -> "Build muscle"
    Goal.STRENGTH -> "Get stronger"
    Goal.CUT -> "Lose fat"
    Goal.RECOMP -> "Recomposition"
    Goal.MAKE_WEIGHT -> "Make weight"
}

/**
 * The AI's physique analysis on the result screen: every group it judged, the strengths and
 * weak points for the user's goal, and what to do about each weak point.
 */
@Composable
fun PhysiqueCard(report: PhysiqueReport, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(active = false)
                Text(
                    "AI physique analysis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                "Goal: ${goalName(report.goal)} · ${report.summary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            report.scores.forEach { score ->
                Column {
                    WeightBar(
                        label = score.group.label,
                        weight = score.score.toFloat(),
                        strongest = score.development == Development.DEVELOPED,
                    )
                    Text(
                        score.development.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (score.development) {
                            Development.DEVELOPED -> MaterialTheme.colorScheme.primary
                            Development.AVERAGE -> MaterialTheme.colorScheme.onSurfaceVariant
                            Development.LAGGING -> MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }

            Text("Strengths", style = MaterialTheme.typography.titleSmall)
            Text(
                report.strengths.takeIf { it.isNotEmpty() }
                    ?.joinToString { it.label }
                    ?: "None stands out yet — that is normal early on, and it is what training fixes.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("Weak points for your goal", style = MaterialTheme.typography.titleSmall)
            if (report.focus.isEmpty()) {
                Text(
                    "Nothing is lagging for this goal. Keep every group progressing.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            report.focus.forEachIndexed { index, advice ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${index + 1}. ${advice.group.label}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(advice.why, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        advice.how,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "The on-device model's impression of one front photograph, the way a coach " +
                    "sizes you up at a glance — not a measurement. Flexing, a pump or harsh " +
                    "light make a group look bigger, and it cannot see your back. Change your " +
                    "goal in Settings to re-prioritise.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
