package com.squeeze.app.ui.achievements

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.CoachRepository
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.core.coach.Achievement
import com.squeeze.core.coach.Achievements
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val coach: CoachRepository,
) : ViewModel() {

    private val _list = MutableStateFlow<List<Achievement>?>(null)
    val list: StateFlow<List<Achievement>?> = _list.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _list.value = Achievements.all(coach.achievementInputs()) }
    }
}

private val Gold = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEA580C)))

/** Private badges for consistency and measured results, newest first, with the next one in reach. */
@Composable
fun AchievementsScreen(viewModel: AchievementsViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val list by viewModel.list.collectAsStateWithLifecycle()
    val all = list ?: return
    val earned = all.count { it.earned }
    val latest = Achievements.latest(all)
    val next = Achievements.next(all)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Achievements", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text("$earned of ${all.size} earned · private to you", style = MaterialTheme.typography.bodyMedium, color = muted)

        latest?.let { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Gold)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Medal(a.emoji, size = 60, shine = true, background = Color.White.copy(alpha = 0.22f))
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        if (a.earnedOn == LocalDate.now().toEpochDay()) "NEW · TODAY" else "LATEST · ${day(a.earnedOn!!)}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.3.sp,
                    )
                    Text(a.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(a.detail, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        next?.let { a ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Next: ${a.title}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${a.progress} of ${a.target}", style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((a.progress.toFloat() / a.target).coerceIn(0.02f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF36A0FF), Color(0xFF1768FF)))),
                    )
                }
            }
        }

        Text(
            "ALL ACHIEVEMENTS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = muted,
            modifier = Modifier.padding(top = 4.dp),
        )
        // Earned first, newest first; then locked, closest first.
        val ordered = all.filter { it.earned }.sortedByDescending { it.earnedOn } +
            all.filter { !it.earned }.sortedByDescending { it.progress.toDouble() / it.target }
        ordered.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a -> Badge(a, Modifier.weight(1f)) }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Badge(a: Achievement, modifier: Modifier) {
    val dark = LocalIsDarkTheme.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .heightIn(min = 124.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (a.earned) {
            Medal(a.emoji, size = 50, shine = true, background = (if (dark) Color(0xFF1E3A8A) else Color(0xFFE8F0FF)))
        } else {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            a.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = if (a.earned) MaterialTheme.colorScheme.onSurface else muted,
        )
        Text(
            if (a.earned) day(a.earnedOn!!) else "${a.target - a.progress} to go".takeIf { a.target > 1 } ?: "Locked",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            textAlign = TextAlign.Center,
        )
    }
}

/** An emoji on a rounded tile, with a slow light sweep across earned ones. */
@Composable
private fun Medal(emoji: String, size: Int, shine: Boolean, background: Color) {
    val sweep by rememberInfiniteTransition(label = "shine").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size * 0.5f).sp)
        if (shine) {
            Box(
                Modifier
                    .offset(x = (size * sweep - size / 2f).dp)
                    .rotate(20f)
                    .width((size * 0.3f).dp)
                    .height((size * 1.6f).dp)
                    .background(Color.White.copy(alpha = 0.35f)),
            )
        }
    }
}

private fun day(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("MMM d"))
