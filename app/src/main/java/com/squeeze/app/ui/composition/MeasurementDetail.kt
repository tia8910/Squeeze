package com.squeeze.app.ui.composition

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.squeeze.app.data.db.MeasurementEntity
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.SecondaryButton
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Everything stored for one measurement, including the photograph it came from.
 *
 * A full-screen dialog rather than the small alert this replaced. The alert showed a date
 * and two of the seven circumferences, which made the chevron beside each history row a
 * promise the app did not keep — and it had nowhere to put an image.
 *
 * The photograph is decrypted on demand and only while this is open. It is never held in the
 * list, so scrolling history does not decrypt every image the user has ever taken.
 */
@Composable
fun MeasurementDetailDialog(
    entry: MeasurementEntity,
    loadPhoto: suspend (String) -> Bitmap?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = LocalIsDarkTheme.current
    val muted = if (dark) Brand.DarkMuted else Brand.Muted

    var photo by remember(entry.id) { mutableStateOf<Bitmap?>(null) }
    var photoLoading by remember(entry.id) { mutableStateOf(entry.photoId != null) }

    LaunchedEffect(entry.id) {
        entry.photoId?.let {
            photo = loadPhoto(it)
            photoLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                Text(
                    text = LocalDate.ofEpochDay(entry.epochDay)
                        .format(DateTimeFormatter.ofPattern("d MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete this measurement",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    photoLoading -> Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (dark) Brand.DarkSunken else Brand.Sunken),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    photo != null -> Image(
                        bitmap = photo!!.asImageBitmap(),
                        contentDescription = "The photograph this scan was measured from",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (dark) Brand.DarkSunken else Brand.Sunken),
                    )

                    entry.photoId != null -> Text(
                        text = "The photograph for this scan could not be opened.",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }

                DetailGroup(
                    title = "Source",
                    rows = listOfNotNull(
                        "Method" to sourceLabel(entry.source),
                        entry.note?.let { "Note" to it },
                        if (entry.photoId != null) {
                            "Photograph" to "Stored, encrypted on this device"
                        } else {
                            null
                        },
                    ),
                )

                DetailGroup(
                    title = "Circumferences",
                    rows = listOfNotNull(
                        entry.neckCm?.let { "Neck" to "%.1f cm".format(it) },
                        entry.chestCm?.let { "Chest" to "%.1f cm".format(it) },
                        entry.waistCm?.let { "Waist" to "%.1f cm".format(it) },
                        entry.hipCm?.let { "Hip" to "%.1f cm".format(it) },
                        entry.thighCm?.let { "Thigh" to "%.1f cm".format(it) },
                        entry.armCm?.let { "Arm" to "%.1f cm".format(it) },
                        entry.calfCm?.let { "Calf" to "%.1f cm".format(it) },
                    ),
                    emptyNote = "No circumferences recorded for this entry.",
                )

                DetailGroup(
                    title = "Skinfolds",
                    rows = listOfNotNull(
                        entry.chestMm?.let { "Chest" to "%.1f mm".format(it) },
                        entry.abdomenMm?.let { "Abdomen" to "%.1f mm".format(it) },
                        entry.thighMm?.let { "Thigh" to "%.1f mm".format(it) },
                        entry.tricepsMm?.let { "Triceps" to "%.1f mm".format(it) },
                        entry.suprailiacMm?.let { "Suprailiac" to "%.1f mm".format(it) },
                    ),
                )

                DetailGroup(
                    title = "Other",
                    rows = listOfNotNull(
                        entry.weightKg?.let { "Weight" to "%.1f kg".format(it) },
                        entry.referenceBodyFatPercent?.let {
                            "Reference scan" to "%.1f%%".format(it)
                        },
                    ),
                )

                Spacer(Modifier.height(4.dp))

                SecondaryButton(
                    text = "Close",
                    onClick = onDismiss,
                    leading = {
                        Icon(Icons.Default.Close, contentDescription = null, Modifier.size(18.dp))
                    },
                )
            }
        }
    }
}

/**
 * One labelled block.
 *
 * A group with nothing in it is dropped rather than shown empty, unless [emptyNote] gives a
 * reason worth stating — an entry with no circumferences is unusual enough to explain.
 */
@Composable
private fun DetailGroup(
    title: String,
    rows: List<Pair<String, String>>,
    emptyNote: String? = null,
) {
    if (rows.isEmpty() && emptyNote == null) return

    val muted = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted

    BrandCard(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = muted,
        )

        if (rows.isEmpty()) {
            Text(
                text = emptyNote.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(top = 10.dp),
            )
            return@BrandCard
        }

        rows.forEachIndexed { index, (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (index == 0) 12.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "PHOTO" -> "Photo scan"
    "PHOTO_FRONT_ONLY" -> "Photo scan, front only"
    "REFERENCE_SCAN" -> "Reference scan"
    "BIA_SCALE" -> "Bioimpedance scale"
    else -> "Tape measurement"
}
