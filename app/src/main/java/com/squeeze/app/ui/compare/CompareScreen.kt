package com.squeeze.app.ui.compare

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.squeeze.app.data.db.MeasurementDao
import com.squeeze.app.data.db.MeasurementEntity
import com.squeeze.app.data.photo.ScanPhotoStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** How far back to compare against. Zero weeks means the first scan with a photo. */
enum class CompareRange(val label: String, val weeks: Long) {
    WEEK("1 week", 1), MONTH("4 weeks", 4), QUARTER("12 weeks", 12), FIRST("First scan", 0),
}

data class CompareState(
    val loading: Boolean = true,
    val range: CompareRange = CompareRange.MONTH,
    val before: MeasurementEntity? = null,
    val after: MeasurementEntity? = null,
    val beforePhoto: Bitmap? = null,
    val afterPhoto: Bitmap? = null,
    val photoScans: Int = 0,
)

@HiltViewModel
class CompareViewModel @Inject constructor(
    private val measurements: MeasurementDao,
    private val photos: ScanPhotoStore,
) : ViewModel() {

    private val _state = MutableStateFlow(CompareState())
    val state: StateFlow<CompareState> = _state.asStateFlow()

    fun load(range: CompareRange = _state.value.range) {
        viewModelScope.launch {
            val withPhotos = measurements.since(Long.MIN_VALUE).filter { it.photoId != null }
            val after = withPhotos.lastOrNull()
            // The scan nearest to the chosen distance back, never the latest itself.
            val before = after?.let { latest ->
                val earlier = withPhotos.filter { it.id != latest.id && it.epochDay <= latest.epochDay }
                if (range == CompareRange.FIRST) {
                    earlier.firstOrNull()
                } else {
                    val wanted = latest.epochDay - range.weeks * 7
                    earlier.minByOrNull { kotlin.math.abs(it.epochDay - wanted) }
                }
            }
            val (b, a) = withContext(Dispatchers.IO) {
                before?.photoId?.let(photos::load) to after?.photoId?.let(photos::load)
            }
            _state.value = CompareState(false, range, before, after, b, a, withPhotos.size)
        }
    }
}

/**
 * Two scan photos, then and now, with a slider between them and the change in figures below.
 *
 * Both photos come from the encrypted store on this phone and are only ever drawn here.
 */
@Composable
fun CompareScreen(onScan: () -> Unit = {}, viewModel: CompareViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var split by remember { mutableFloatStateOf(0.5f) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Compare", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text("Same pose, same light, side by side", style = MaterialTheme.typography.bodyMedium, color = muted)

        if (state.loading) return@Column

        if (state.photoScans < 2) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Two scans needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (state.photoScans == 0) "Take your first scan. Next week, take another in the same spot and you can compare them here."
                    else "You have one scan. Take another in a week, same spot and same light, to see the change.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
                com.squeeze.app.ui.components.PrimaryButton(text = "Start body scan", onClick = onScan)
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompareRange.entries.forEach { r ->
                FilterChip(selected = state.range == r, onClick = { viewModel.load(r) }, label = { Text(r.label) })
            }
        }

        val before = state.before
        val after = state.after
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(440.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0B1B3F))
                .semantics { contentDescription = "Your scan photos, then and today. Drag the slider below to compare." },
        ) {
            state.beforePhoto?.let {
                Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            state.afterPhoto?.let {
                Image(
                    it.asImageBitmap(),
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(left = size.width * split) { this@drawWithContent.drawContent() }
                        },
                )
            }
            Box(
                Modifier
                    .offset(x = maxWidth * split - 1.5.dp)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color.White),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * split - 19.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text("⇆", color = Color(0xFF0B1B3F), fontWeight = FontWeight.Bold)
            }
            Tag(before?.let { day(it.epochDay) } ?: "", Modifier.align(Alignment.TopStart))
            Tag("Today", Modifier.align(Alignment.TopEnd))
        }
        Slider(value = split, onValueChange = { split = it }, valueRange = 0.05f..0.95f)

        if (before != null && after != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Delta("Body fat", bodyFat(before), bodyFat(after), "%", good = -1, Modifier.weight(1f))
                Delta("Waist", before.waistCm, after.waistCm, " cm", good = -1, Modifier.weight(1f))
                Delta("Lean mass", lean(before), lean(after), " kg", good = 1, Modifier.weight(1f))
            }
        }
        Text("Photos stay on this phone.", style = MaterialTheme.typography.bodySmall, color = muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Tag(text: String, modifier: Modifier) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier
            .padding(12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * One figure's change. [good] is the direction that counts as progress: -1 for down, 1 for up,
 * so the colour says whether the change is welcome without the reader working it out.
 */
@Composable
private fun Delta(label: String, before: Double?, after: Double?, unit: String, good: Int, modifier: Modifier) {
    val change = if (before != null && after != null) after - before else null
    val color = when {
        change == null || kotlin.math.abs(change) < 0.05 -> MaterialTheme.colorScheme.onSurface
        change * good > 0 -> Color(0xFF16A34A)
        else -> Color(0xFFD97706)
    }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            change?.let { "%+.1f%s".format(it, unit).replace("-", "−") } ?: "--",
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun bodyFat(m: MeasurementEntity): Double? =
    m.referenceBodyFatPercent ?: m.visualBodyFatPercent ?: m.shapeBodyFatPercent

private fun lean(m: MeasurementEntity): Double? {
    val w = m.weightKg ?: return null
    val bf = bodyFat(m) ?: return null
    return w * (1 - bf / 100.0)
}

private fun day(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("MMM d"))
