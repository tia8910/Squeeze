package com.squeeze.app.ui.label

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.NoticePill
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.components.SectionHeader
import com.squeeze.app.ui.components.StatRow
import com.squeeze.app.ui.components.StatTile
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.core.corpus.DefinitionRegion
import com.squeeze.core.corpus.RegionScore

/**
 * Judging your own scan photographs, to build the set a classifier can be trained against.
 *
 * The app has one attempt at a definition signal behind it, and it failed in a way that is
 * worth keeping in view here: an abdominal texture score that ran 5.76 at eight per cent body
 * fat and 6.03 at twenty. Not a weak signal, no signal. It shipped because nothing in the
 * project could score a candidate against photographs a person had judged. This screen is how
 * that stops being true.
 *
 * Nothing here leaves the device. The judgements are stored beside the photographs, and the
 * export produces a text file of answers keyed by image hash — which describes bodies without
 * containing any.
 */
@Composable
fun LabelScreen(viewModel: LabelViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val muted = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(
            eyebrow = "Stays on this device",
            title = "Label your scans",
            caption = "Answer three questions about a photo. Enough of these and the app can " +
                "learn to read definition from a picture instead of guessing at it from an " +
                "outline.",
        )

        StatRow {
            StatTile(
                value = state.labelled.toString(),
                label = "Judged now",
                modifier = Modifier.weight(1f),
                tinted = true,
            )
            StatTile(
                value = state.remaining.toString(),
                label = "In the queue",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = RegionScore.MIN_LABELS.toString(),
                label = "Needed each",
                modifier = Modifier.weight(1f),
            )
        }

        when {
            state.loading -> Row(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }

            state.subject == null -> NoticePill(
                "No scan photographs left to judge. Take a scan and come back — every scan " +
                    "keeps its photo, so the set grows as you use the app.",
            )

            else -> {
                val subject = state.subject!!

                Image(
                    bitmap = subject.bitmap.asImageBitmap(),
                    contentDescription = "The scan photograph being judged",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .clip(RoundedCornerShape(20.dp)),
                )

                Text(
                    text = "Judge what you can see with the body relaxed. Almost anyone shows " +
                        "separation braced, so answering for a braced body teaches the wrong " +
                        "thing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )

                DefinitionRegion.entries.forEach { region ->
                    RegionQuestion(
                        region = region,
                        answer = state.answers[region],
                        unusable = region in state.unusable,
                        onAnswer = { viewModel.answer(region, it) },
                        onCannotTell = { viewModel.cannotTell(region) },
                    )
                }

                PrimaryButton(
                    text = if (state.complete) "Save and next" else "Answer all three",
                    onClick = viewModel::saveAndNext,
                    enabled = state.complete,
                )

                TextButton(onClick = viewModel::skip, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip this photo")
                }
            }
        }

        TextButton(onClick = viewModel::export, modifier = Modifier.fillMaxWidth()) {
            Text("Export labels")
        }

        // Shown in full rather than written to a file. The labels are the user's judgements
        // about their own body, and the app choosing where to put them — a share sheet, a
        // downloads folder — would be deciding on their behalf who gets to see them.
        state.exported?.let { text ->
            BrandCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "Labels, ready to copy",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = text.ifBlank { "Nothing judged yet." },
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * One region, one question, three answers.
 *
 * "Can't tell" is a first-class answer rather than a way of skipping. Which photographs cannot
 * answer the question measures the capture guidance, and a labeller forced to choose yes or no
 * on a shadowed abdomen puts a guess into the set that a classifier then has to fit.
 */
@Composable
private fun RegionQuestion(
    region: DefinitionRegion,
    answer: Boolean?,
    unusable: Boolean,
    onAnswer: (Boolean) -> Unit,
    onCannotTell: () -> Unit,
) {
    val muted = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted

    BrandCard(Modifier.fillMaxWidth()) {
        Text(
            text = question(region),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = hint(region),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = answer == true,
                onClick = { onAnswer(true) },
                label = { Text("Yes") },
            )
            FilterChip(
                selected = answer == false,
                onClick = { onAnswer(false) },
                label = { Text("No") },
            )
            FilterChip(
                selected = unusable,
                onClick = onCannotTell,
                label = { Text("Can't tell") },
            )
        }
    }
}

private fun question(region: DefinitionRegion): String = when (region) {
    DefinitionRegion.ABDOMEN -> "Is abdominal separation visible?"
    DefinitionRegion.CHEST_AND_DELTS -> "Is chest and shoulder separation visible?"
    DefinitionRegion.ARMS -> "Is arm separation or vascularity visible?"
}

private fun hint(region: DefinitionRegion): String = when (region) {
    DefinitionRegion.ABDOMEN ->
        "The line down the middle and the blocks either side of it, with the stomach relaxed."

    DefinitionRegion.CHEST_AND_DELTS ->
        "A visible edge where the chest meets the shoulder, and separation between the " +
            "heads of the deltoid."

    DefinitionRegion.ARMS ->
        "A visible line between biceps and triceps, or veins on the forearm at rest."
}
