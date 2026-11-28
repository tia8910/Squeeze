package com.squeeze.app.ui.morning

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.form.CoachGhost
import com.squeeze.app.ui.form.CoachView
import com.squeeze.app.ui.scan.VoiceGuide
import kotlinx.coroutines.delay
import java.time.LocalDate

/** One timed stretch of the routine and what the voice says at its start and halfway. */
private data class Segment(val seconds: Int, val cue: String, val halfway: String? = null, val rest: Boolean = false)

/** One move of the routine, made of timed segments. */
private data class Move(val name: String, val detail: String, val segments: List<Segment>, val vacuum: Boolean = false)

private val Vacuum = listOf(
    Segment(5, "Stand tall. Breathe all the way out.", rest = true),
    Segment(20, "Now pull your belly in, up under your ribs. Hold.", halfway = "Ten seconds. Keep it in."),
    Segment(10, "And release. Breathe normally.", rest = true),
)

private val Routine = listOf(
    Move(
        "Cat cow", "8 slow reps, on hands and knees",
        listOf(Segment(40, "On hands and knees. Breathe in, arch your back and look up. Breathe out, round your back.", halfway = "Keep it slow. Four more.")),
    ),
    Move("Stomach vacuum", "3 holds of 20 seconds", Vacuum + Vacuum + Vacuum, vacuum = true),
    Move(
        "Hip opener", "30 seconds each side",
        listOf(
            Segment(30, "Kneel on your right knee, left foot forward. Push your hips forward gently."),
            Segment(30, "Switch sides. Left knee down, right foot forward."),
        ),
    ),
    Move("Deep squat hold", "45 seconds", listOf(Segment(45, "Sit down into a deep squat. Chest up, heels down.", halfway = "Halfway. Breathe."))),
    Move("Plank", "40 seconds", listOf(Segment(40, "Forearms down, body in one straight line. Squeeze your glutes.", halfway = "Twenty seconds left."))),
)

private val TotalMinutes = (Routine.sumOf { m -> m.segments.sumOf { it.seconds } } + 30) / 60

/** Days the routine was finished, kept in plain preferences: a streak, nothing about the body. */
private class MorningStreak(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("morning_routine", Context.MODE_PRIVATE)

    private fun days(): Set<Long> = prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    /** Days in a row ending today, or ending yesterday when today is not done yet. */
    fun streak(): Int {
        val done = days()
        var day = LocalDate.now().toEpochDay()
        if (day !in done) day--
        var n = 0
        while (day in done) { n++; day-- }
        return n
    }

    fun markToday() {
        val today = LocalDate.now().toEpochDay()
        // Only the last few months matter for a streak.
        val kept = days().filter { it > today - 120 } + today
        prefs.edit().putStringSet(KEY, kept.map { it.toString() }.toSet()).apply()
    }

    private companion object { const val KEY = "days" }
}

private enum class Stage { OVERVIEW, RUNNING, DONE }

/**
 * A short morning routine, talked through by voice, with the stomach vacuum at its centre.
 *
 * Every move is timed and the voice says what to do at each change, so the phone can lie on the
 * floor while the user follows along. Nothing is filmed.
 */
@Composable
fun MorningRoutineScreen(onDone: () -> Unit = {}) {
    val context = LocalContext.current
    val streakStore = remember { MorningStreak(context) }
    var streak by remember { mutableIntStateOf(streakStore.streak()) }
    var stage by remember { mutableStateOf(Stage.OVERVIEW) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B2A4A), Color(0xFF0A1222), Color(0xFF0A1222)))),
    ) {
        // Morning sun in the corner.
        Box(
            Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .padding(start = 110.dp, bottom = 110.dp)
                .background(Brush.radialGradient(listOf(Color(0x73FBBF24), Color.Transparent))),
        )
        when (stage) {
            Stage.OVERVIEW -> Overview(streak, onStart = { stage = Stage.RUNNING })
            Stage.RUNNING -> Running(
                streak = streak,
                onFinish = {
                    streakStore.markToday()
                    streak = streakStore.streak()
                    stage = Stage.DONE
                },
                onStop = onDone,
            )
            Stage.DONE -> Finished(streak, onDone)
        }
    }
}

@Composable
private fun Header(streak: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Pill("☀️ MORNING ROUTINE · $TotalMinutes MIN")
        Spacer(Modifier.weight(1f))
        if (streak > 0) Pill("🔥 Day $streak")
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x80050B18)).padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

@Composable
private fun Overview(streak: Int, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(streak)
        Text("Wake up your core", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Five moves, about $TotalMinutes minutes. Put the phone down and follow the voice.",
            color = Color(0xFFC9D6F0),
            fontSize = 14.sp,
        )
        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            CoachGhost(CoachView.QUARTER, matched = false, modifier = Modifier.height(260.dp).aspectRatio(0.67f))
        }
        Routine.forEachIndexed { i, move -> MoveRow(i, move, current = -1) }
        PrimaryButton(text = "Start", onClick = onStart, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun MoveRow(i: Int, move: Move, current: Int) {
    val done = current > i
    val now = current == i
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        done -> Color(0xFF16A34A)
                        now -> Color(0xFF1768FF)
                        else -> Color(0x1FFFFFFF)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { Text(if (done) "✓" else "${i + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
        Text(
            "${move.name} · ${move.detail}",
            color = if (current < 0 || now || done) Color.White else Color(0xFF8FA6D1),
            fontSize = 14.sp,
            fontWeight = if (now) FontWeight.ExtraBold else FontWeight.Normal,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun Running(streak: Int, onFinish: () -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    val voice = remember { VoiceGuide(context) }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }

    // The routine flattened into one list of timed segments.
    val plan = remember { Routine.flatMapIndexed { i, m -> m.segments.map { i to it } } }
    var at by remember { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(plan[0].second.seconds) }
    var paused by remember { mutableStateOf(false) }
    val (moveIndex, segment) = plan[at]
    val move = Routine[moveIndex]

    LaunchedEffect(at, paused) {
        if (paused) {
            voice.stop()
            return@LaunchedEffect
        }
        if (at == 0 && remaining == segment.seconds) delay(1000)
        if (remaining == segment.seconds) voice.say(segment.cue, force = true)
        while (remaining > 0) {
            delay(1000)
            remaining--
            if (segment.halfway != null && remaining == segment.seconds / 2) voice.say(segment.halfway, force = true)
        }
        if (at == plan.lastIndex) {
            voice.say("Done. Great start to the day.", force = true)
            delay(1500)
            onFinish()
        } else {
            remaining = plan[at + 1].second.seconds
            at++
        }
    }

    // Which hold of the vacuum this is: each hold is three segments, breathe out, hold, release.
    val holdNumber = if (move.vacuum) (at - plan.indexOfFirst { it.first == moveIndex }) / Vacuum.size + 1 else 0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Header(streak)
        Text(
            "Step ${moveIndex + 1} of ${Routine.size}" + if (move.vacuum) " · hold ${holdNumber.coerceAtMost(3)} of 3" else "",
            color = Color(0xFFC9D6F0),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(move.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)

        if (move.vacuum) {
            // The coach side on, with a ring at the waist that tightens while the belly is held in.
            val holding = !segment.rest
            val squeeze by animateFloatAsState(if (holding) 0.62f else 1f, tween(1200), label = "waist")
            Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.height(280.dp).aspectRatio(0.67f)) {
                    CoachGhost(CoachView.QUARTER, matched = holding, modifier = Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width * 0.5f * squeeze
                        val h = size.height * 0.07f
                        val c = Offset(size.width * 0.5f, size.height * 0.5f)
                        drawOval(
                            color = if (holding) Color(0xFF4ADE80) else Color(0xFFF59E0B),
                            topLeft = Offset(c.x - w / 2, c.y - h / 2),
                            size = Size(w, h),
                            style = Stroke(3.dp.toPx()),
                        )
                    }
                }
            }
        }

        Text(
            "“${segment.cue}”",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (segment.rest) Color(0xE6D97706) else Color(0xD916A34A))
                .padding(13.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            val progress = 1f - remaining / segment.seconds.toFloat()
            Box(Modifier.size(if (move.vacuum) 86.dp else 120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color(0x1FFFFFFF), style = Stroke(7.dp.toPx()))
                    drawArc(Color(0xFFFBBF24), -90f, 360f * progress, useCenter = false, style = Stroke(7.dp.toPx()))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$remaining", color = Color.White, fontSize = if (move.vacuum) 24.sp else 34.sp, fontWeight = FontWeight.ExtraBold)
                    Text("seconds", color = Color(0xFFC9D6F0), fontSize = 10.sp)
                }
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Routine.forEachIndexed { i, m -> MoveRow(i, m, current = moveIndex) }
            }
        }

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Stop", color = Color.White) }
            OutlinedButton(
                onClick = {
                    // Skip to the first segment of the next move.
                    val next = plan.indexOfFirst { it.first > moveIndex }
                    if (next < 0) {
                        onFinish()
                    } else {
                        remaining = plan[next].second.seconds
                        at = next
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Skip", color = Color.White) }
            PrimaryButton(text = if (paused) "Resume" else "Pause", onClick = { paused = !paused }, modifier = Modifier.weight(1f))
        }
        Text("Nothing is filmed. Follow the voice with the phone on the floor.", color = Color(0xFF8FA6D1), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Finished(streak: Int, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(120.dp).clip(CircleShape).background(Color(0x3316A34A)).border(3.dp, Color(0xFF4ADE80), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("🔥", fontSize = 48.sp) }
        Text("Day $streak", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            if (streak <= 1) "Routine done. Same time tomorrow starts a streak." else "Routine done, $streak mornings in a row.",
            color = Color(0xFFC9D6F0),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(text = "Done", onClick = onDone)
    }
}
