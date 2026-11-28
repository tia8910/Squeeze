package com.squeeze.app.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.CoachRepository
import com.squeeze.app.data.db.MeasurementDao
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.core.coach.Achievements
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Only counts and a trend line: what the card is allowed to show. No photos, no weight. */
data class ShareStats(
    val weeks: Int,
    val bodyFatChange: Double?,
    val trend: List<Double>,
    val streakWeeks: Int,
    val workouts: Int,
    val awards: Int,
)

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val coach: CoachRepository,
    private val measurements: MeasurementDao,
) : ViewModel() {

    private val _stats = MutableStateFlow<ShareStats?>(null)
    val stats: StateFlow<ShareStats?> = _stats.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val inputs = coach.achievementInputs()
            val readings = measurements.since(Long.MIN_VALUE).mapNotNull { m ->
                (m.referenceBodyFatPercent ?: m.visualBodyFatPercent ?: m.shapeBodyFatPercent)?.let { m.epochDay to it }
            }
            val firstDay = (inputs.scanDays + inputs.workoutDays).minOrNull()
            val today = java.time.LocalDate.now().toEpochDay()
            _stats.value = ShareStats(
                weeks = firstDay?.let { ((today - it) / 7).toInt().coerceAtLeast(1) } ?: 1,
                bodyFatChange = if (readings.size >= 2) readings.last().second - readings.first().second else null,
                trend = readings.takeLast(12).map { it.second },
                streakWeeks = Achievements.weekStreakDays(inputs.workoutDays).second,
                workouts = inputs.workoutDays.size,
                awards = Achievements.all(inputs).count { it.earned },
            )
        }
    }
}

/** The four looks, each a three-stop gradient. */
private val Styles = listOf(
    Triple(0xFF2F8CFF.toInt(), 0xFF0E4BD8.toInt(), 0xFF081C45.toInt()),
    Triple(0xFFF59E0B.toInt(), 0xFFEA580C.toInt(), 0xFFDB2777.toInt()),
    Triple(0xFF22C55E.toInt(), 0xFF15803D.toInt(), 0xFF065F46.toInt()),
    Triple(0xFF334155.toInt(), 0xFF0F172A.toInt(), 0xFF020617.toInt()),
)

/**
 * A branded progress image to post or send, drawn on the phone.
 *
 * It never includes a photo or a weight: only how long, how often, and optionally the body fat
 * change, which the user can switch off before sharing.
 */
@Composable
fun ShareScreen(viewModel: ShareViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var style by remember { mutableIntStateOf(0) }
    var showFat by remember { mutableStateOf(true) }
    val s = stats ?: return
    val card = remember(s, style, showFat) { renderCard(context, s, style, showFat && s.bodyFatChange != null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Share your progress", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)

        Image(
            card.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_W.toFloat() / CARD_H)
                .clip(RoundedCornerShape(24.dp))
                .semantics { contentDescription = "Preview of your progress card" },
        )

        Text("STYLE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Styles.forEachIndexed { i, (a, _, c) ->
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(a), Color(c))))
                        .border(3.dp, if (style == i) MaterialTheme.colorScheme.onSurface else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable(onClickLabel = "Use style ${i + 1}") { style = i },
                )
            }
        }

        if (s.bodyFatChange != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show body fat change", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = showFat, onCheckedChange = { showFat = it })
            }
        }

        PrimaryButton(text = "Share", onClick = { share(context, card) })
        OutlinedButton(onClick = { save(context, card) }, modifier = Modifier.fillMaxWidth()) { Text("Save to Photos") }
        Text(
            "Never includes your photos or your weight.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val CARD_W = 1080
private const val CARD_H = 1350

/** Draws the card at post size with plain Android drawing, so it is the same image everywhere. */
private fun renderCard(context: Context, s: ShareStats, styleIndex: Int, showFat: Boolean): Bitmap {
    val bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val (a, b, d) = Styles[styleIndex]
    val bg = Paint().apply {
        shader = LinearGradient(0f, 0f, CARD_W * 0.6f, CARD_H.toFloat(), intArrayOf(a, b, d), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawRect(0f, 0f, CARD_W.toFloat(), CARD_H.toFloat(), bg)

    fun text(size: Float, bold: Boolean, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        this.alpha = alpha
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    val pad = 84f
    // Header: the mark and name.
    context.getDrawable(com.squeeze.app.R.drawable.logo_squeeze_on_dark)?.let { logo ->
        logo.setBounds(pad.toInt(), 84, pad.toInt() + 72, 156)
        logo.draw(c)
    }
    c.drawText("Squeeze Fit", pad + 96, 136f, text(44f, true))
    val weeks = "${s.weeks} week${if (s.weeks == 1) "" else "s"}"
    c.drawText(weeks, CARD_W - pad - text(36f, false).measureText(weeks), 134f, text(36f, false, 215))

    c.drawText("MY PROGRESS", pad, 300f, text(36f, true, 215))
    if (showFat && s.bodyFatChange != null) {
        val big = "%+.1f%%".format(s.bodyFatChange).replace("-", "−")
        c.drawText(big, pad, 440f, text(150f, true))
        c.drawText("body fat", pad + text(150f, true).measureText(big) + 24f, 440f, text(46f, true, 215))
    } else {
        val big = "${s.workouts}"
        c.drawText(big, pad, 440f, text(150f, true))
        c.drawText("workouts done", pad + text(150f, true).measureText(big) + 24f, 440f, text(46f, true, 215))
    }

    // Trend line, or a gentle rising line before there is a trend to draw.
    val top = 520f
    val bottom = 800f
    val values = s.trend.takeIf { it.size >= 2 }
    val points = if (values != null) {
        val lo = values.min()
        val hi = values.max().coerceAtLeast(lo + 0.1)
        values.mapIndexed { i, v ->
            val x = pad + (CARD_W - 2 * pad) * i / (values.size - 1)
            val y = top + (bottom - top) * ((v - lo) / (hi - lo)).toFloat()
            x to y
        }
    } else {
        listOf(pad to bottom - 40f, CARD_W / 2f to bottom - 140f, CARD_W - pad to top + 40f)
    }
    val line = Path().apply {
        moveTo(points.first().first, points.first().second)
        points.drop(1).forEach { (x, y) -> lineTo(x, y) }
    }
    val fill = Path(line).apply {
        lineTo(points.last().first, bottom + 40f)
        lineTo(points.first().first, bottom + 40f)
        close()
    }
    c.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, top, 0f, bottom + 40f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    })
    c.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    })
    c.drawCircle(points.last().first, points.last().second, 16f, text(1f, false))

    // Three stat tiles.
    val tiles = listOf("🔥 ${s.streakWeeks}" to "week streak", "${s.workouts}" to "workouts", "${s.awards}" to "awards")
    val gap = 24f
    val w = (CARD_W - 2 * pad - 2 * gap) / 3
    tiles.forEachIndexed { i, (value, label) ->
        val left = pad + i * (w + gap)
        val r = RectF(left, 900f, left + w, 1110f)
        c.drawRoundRect(r, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x29FFFFFF })
        val vp = text(64f, true)
        c.drawText(value, r.centerX() - vp.measureText(value) / 2, 1005f, vp)
        val lp = text(32f, false, 215)
        c.drawText(label, r.centerX() - lp.measureText(label) / 2, 1065f, lp)
    }
    c.drawText("squeeze.fit", pad, 1240f, text(34f, false, 190))
    return bmp
}

private fun cardFile(context: Context, bmp: Bitmap): File {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    return File(dir, "squeeze-progress.png").apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
}

private fun share(context: Context, bmp: Bitmap) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", cardFile(context, bmp))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "My progress with Squeeze Fit")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share progress"))
    }.onFailure { Toast.makeText(context, "Could not share the card.", Toast.LENGTH_SHORT).show() }
}

/** Saves to Pictures/Squeeze. Needs no permission on Android 10 and later. */
private fun save(context: Context, bmp: Bitmap) {
    val ok = runCatching {
        if (Build.VERSION.SDK_INT < 29) error("needs Android 10")
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "squeeze-progress-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Squeeze")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
        context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }.isSuccess
    Toast.makeText(context, if (ok) "Saved to Photos" else "Use Share to save it instead.", Toast.LENGTH_SHORT).show()
}
